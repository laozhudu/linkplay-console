// 验证提交进仓库的 bundle 是否真的可用：用假原生桥驱动它，跑五个平台的搜索。
// 这不是单测，是「产物自检」—— QuickJS 在 JVM 上跑不了，只能这样证明 bundle 没坏。
const fs = require('fs')
const vm = require('vm')
const crypto = require('crypto')

const BUNDLE = process.argv[2]
const KEY = 'test-key'
const src = fs.readFileSync(BUNDLE, 'utf-8')

const responses = new Map()
let seq = 0

function cryptoCall(op, argsJson) {
  const a = JSON.parse(argsJson)
  const strip = (k) => String(k).replace(/-----[A-Z ]+-----/g, '').replace(/\s/g, '')
  const toPem = (k) => '-----BEGIN PUBLIC KEY-----\n' + strip(k).replace(/(.{64})/g, '$1\n').trim() + '\n-----END PUBLIC KEY-----\n'
  switch (op) {
    case 'md5': return crypto.createHash('md5').update(String(a[0]), 'utf-8').digest('hex')
    case 'sha1': return crypto.createHash('sha1').update(String(a[0]), 'utf-8').digest('hex')
    case 'aesEncrypt':
    case 'aesDecrypt': {
      const [data, key, iv, mode] = a
      const alg = String(mode).includes('CBC') ? 'aes-128-cbc' : 'aes-128-ecb'
      const k = Buffer.from(key, 'base64').subarray(0, 16)
      const ivb = String(mode).includes('CBC') ? Buffer.from(iv, 'base64').subarray(0, 16) : null
      const c = op === 'aesEncrypt' ? crypto.createCipheriv(alg, k, ivb) : crypto.createDecipheriv(alg, k, ivb)
      if (String(mode).includes('NoPadding')) c.setAutoPadding(false)
      const out = op === 'aesEncrypt'
        ? Buffer.concat([c.update(Buffer.from(data, 'base64')), c.final()])
        : Buffer.concat([c.update(Buffer.from(data, 'base64')), c.final()])
      return out.toString('base64')
    }
    case 'rsaEncrypt': {
      const [text, key, padding] = a
      const pad = String(padding).includes('NoPadding')
        ? crypto.constants.RSA_NO_PADDING
        : crypto.constants.RSA_PKCS1_OAEP_PADDING
      return crypto.publicEncrypt({ key: toPem(key), padding: pad }, Buffer.from(text, 'base64')).toString('base64')
    }
    default: throw new Error('unsupported crypto op: ' + op)
  }
}

function httpFetchNative({ id, url, options }) {
  const u = new URL(url)
  const lib = u.protocol === 'https:' ? require('node:https') : require('node:http')
  const method = String((options && options.method) || 'GET').toUpperCase()
  const headers = Object.assign({}, (options && options.headers) || {})
  let body
  if (options && options.form) {
    body = new URLSearchParams(options.form).toString()
    headers['Content-Type'] = headers['Content-Type'] || 'application/x-www-form-urlencoded'
  } else if (options && typeof options.body === 'string') {
    body = options.body
  } else if (options && options.body) {
    body = JSON.stringify(options.body)
    headers['Content-Type'] = headers['Content-Type'] || 'application/json'
  }
  const req = lib.request(url, { method, headers, timeout: 20000 }, (res) => {
    const chunks = []
    res.on('data', (c) => chunks.push(c))
    res.on('end', () => {
      const text = Buffer.concat(chunks).toString('utf-8')
      let parsed = text
      try { parsed = JSON.parse(text) } catch (e) { /* 保持字符串 */ }
      settle(id, { statusCode: res.statusCode, statusMessage: res.statusMessage, headers: res.headers, body: parsed })
    })
  })
  req.on('error', (e) => settle(id, { error: String(e.message || e) }))
  req.on('timeout', () => req.destroy(new Error('timeout')))
  if (body) req.write(body)
  req.end()
}

function settle(id, payload) {
  if (typeof sandbox.__lx_http_settle__ === 'function') {
    sandbox.__lx_http_settle__(id, JSON.stringify(payload))
  }
}

const sandbox = {
  console: { log: () => {}, warn: () => {}, error: () => {} },
  __lx_key__: KEY,
  __lx_crypto__: cryptoCall,
  __lx_native_call__: (key, action, dataJson) => {
    if (key !== KEY) return
    const data = JSON.parse(dataJson)
    if (action === 'http') return httpFetchNative(data)
    if (action === 'sdkResponse') {
      const p = responses.get(data.reqId)
      if (p) { responses.delete(data.reqId); p(data) }
    }
  },
  setTimeout, clearTimeout, setInterval, clearInterval,
  Promise, JSON, Math, Date, RegExp, String, Number, Boolean, Array, Object,
  Uint8Array, ArrayBuffer, Map, Set, WeakMap, parseInt, parseFloat, isNaN,
  encodeURIComponent, decodeURIComponent, encodeURI, decodeURI, escape, unescape,
  TextDecoder, TextEncoder, URL, URLSearchParams, Buffer,
}
sandbox.globalThis = sandbox
sandbox.window = undefined

const ctx = vm.createContext(sandbox)
vm.runInContext(src, ctx, { filename: 'lx-music-sdk.bundle.js' })

function call(action, payload) {
  const reqId = `r${seq++}`
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error('超时 30s')), 30000)
    responses.set(reqId, (d) => { clearTimeout(timer); d.ok ? resolve(d.data) : reject(new Error(d.error)) })
    ctx.lxSdkCall(reqId, action, JSON.stringify(payload))
  })
}

;(async () => {
  console.log('=== bundle 自检 ===')
  const srcs = await call('sources', {})
  console.log('支持的平台:', srcs.sources.join(', '))

  for (const s of srcs.sources) {
    try {
      const r = await call('search', { source: s, name: '周杰伦', page: 1, limit: 3 })
      const first = r.list[0]
      console.log(`  ${s}: ${r.list.length} 条 / total ${r.total}` +
        (first ? `  例: ${first.name} - ${first.singer}` : ''))
    } catch (e) {
      console.log(`  ${s}: ✗ ${String(e.message).slice(0, 80)}`)
    }
  }

  // 再验一个非搜索能力
  try {
    const h = await call('hotSearch', { source: 'wy' })
    console.log('  热搜(wy):', h.list.length, '条')
  } catch (e) { console.log('  热搜(wy): ✗', e.message.slice(0, 60)) }

  try {
    const l = await call('songListSearch', { source: 'wy', name: '华语', page: 1, limit: 2 })
    console.log('  歌单(wy):', l.list.length, '个')
  } catch (e) { console.log('  歌单(wy): ✗', e.message.slice(0, 60)) }

  process.exit(0)
})()
