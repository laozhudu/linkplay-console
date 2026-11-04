// 用途：探测洛雪自定义源「真能不能取到可播链接」。
//       仓库自带的 probe.py 只做静态主机探测、明确不执行脚本 —— 所以它报「可用」
//       只代表上游主机活着。六音那种「返回 success 但没有 url」的坏法它抓不到。
//       这里真的跑脚本、真的取 URL、真的去拉一下。
// 来源：docs/80-lx-source-integration.md
// 调度：手动 / 批量（scripts/lx-source-sync.sh 会循环调用）
// 影响范围：只读网络 + 只读脚本文件，不写任何东西。
// 依赖：node（用 /opt/node-26.13.0）、core-lx 的 lx-preload.js 与 lx-music-sdk.bundle.js
// 风险：低。会向第三方音源接口发请求（这是它本来的用途）。
//
// 用法：
//   node scripts/lx-source-probe.js --script /tmp/qdy.js --name 周杰伦
//   node scripts/lx-source-probe.js --script /tmp/qdy.js --source wy --songmid 186016
//   node scripts/lx-source-probe.js --all-scripts /tmp/lxsrc --json
'use strict'

const fs = require('fs')
const vm = require('vm')
const crypto = require('crypto')
const path = require('path')
const http = require('node:http')
const https = require('node:https')

// 音源脚本会在异步回调里抛异常（比如 flower 的「服务器异常」），
// 不接住的话一个坏脚本就能把整批探测搞崩 —— 那还怎么比较 15 支。
let lastAsyncError = null
process.on('unhandledRejection', (e) => { lastAsyncError = String((e && e.message) || e) })
process.on('uncaughtException', (e) => { lastAsyncError = String((e && e.message) || e) })

const ROOT = path.resolve(__dirname, '..')
const PRELOAD = path.join(ROOT, 'core-lx/src/main/assets/lx-preload.js')
const BUNDLE = path.join(ROOT, 'core-lx/src/main/assets/lx-music-sdk.bundle.js')
const KEY = 'probe-key'

// ───────────────────────── 参数 ─────────────────────────

function parseArgs(argv) {
  const a = { name: '周杰伦', quality: '128k', timeout: 25000 }
  for (let i = 2; i < argv.length; i++) {
    const k = argv[i]
    const v = argv[i + 1]
    if (k === '--script') { a.script = v; i++ }
    else if (k === '--all-scripts') { a.allScripts = v; i++ }
    else if (k === '--source') { a.source = v; i++ }
    else if (k === '--songmid') { a.songmid = v; i++ }
    else if (k === '--name') { a.name = v; i++ }
    else if (k === '--quality') { a.quality = v; i++ }
    else if (k === '--json') { a.json = true }
  }
  return a
}

// ───────────────────────── 原生桥（照 lx-preload.js 的契约） ─────────────────────────

function aesEncrypt(dataB64, keyB64, ivB64, mode) {
  const m = String(mode || '')
  const alg = m.includes('CBC') ? 'aes-128-cbc' : 'aes-128-ecb'
  const key = Buffer.from(keyB64, 'base64').subarray(0, 16)
  const iv = m.includes('CBC') ? Buffer.from(ivB64 || '', 'base64').subarray(0, 16) : null
  const c = crypto.createCipheriv(alg, key, iv)
  // 'AES'（ECB NoPadding）时不能自动补位，否则密文长度对不上上游
  if (!m.includes('PKCS7')) c.setAutoPadding(false)
  return Buffer.concat([c.update(Buffer.from(dataB64, 'base64')), c.final()]).toString('base64')
}

function rsaEncrypt(dataB64, keyBody, padding) {
  const body = String(keyBody).replace(/\s/g, '')
  const pem = '-----BEGIN PUBLIC KEY-----\n' + body.replace(/(.{64})/g, '$1\n').trim() +
    '\n-----END PUBLIC KEY-----\n'
  const pad = String(padding).includes('NoPadding')
    ? crypto.constants.RSA_NO_PADDING
    : crypto.constants.RSA_PKCS1_OAEP_PADDING
  return crypto.publicEncrypt({ key: pem, padding: pad },
    Buffer.from(dataB64, 'base64')).toString('base64')
}

function httpRequest(url, options) {
  return new Promise((resolve) => {
    let u
    try { u = new URL(url) } catch (e) { return resolve({ error: 'URL 非法: ' + url }) }
    const lib = u.protocol === 'https:' ? https : http
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
    const req = lib.request(url, { method, headers, timeout: (options && options.timeout) || 20000 }, (res) => {
      const chunks = []
      res.on('data', (c) => chunks.push(c))
      res.on('end', () => {
        const text = Buffer.concat(chunks).toString('utf-8')
        let parsed = text
        try { parsed = JSON.parse(text) } catch (e) { /* 保持字符串 */ }
        resolve({ statusCode: res.statusCode, statusMessage: res.statusMessage, headers: res.headers, body: parsed })
      })
    })
    req.on('error', (e) => resolve({ error: String((e && e.message) || e) }))
    req.on('timeout', () => req.destroy(new Error('timeout')))
    if (body) req.write(body)
    req.end()
  })
}

// ───────────────────────── 跑一支音源脚本 ─────────────────────────

async function loadScript(scriptText, scriptPath) {
  const sandbox = {
    console: { log() {}, warn() {}, error() {}, info() {}, debug() {} },
    setTimeout, clearTimeout, setInterval, clearInterval,
    Promise, JSON, Math, Date, RegExp, String, Number, Boolean, Array, Object,
    Uint8Array, Int8Array, Uint8ClampedArray, ArrayBuffer, DataView, Map, Set, WeakMap,
    parseInt, parseFloat, isNaN, isFinite,
    encodeURIComponent, decodeURIComponent, encodeURI, decodeURI, escape, unescape,
    TextDecoder, TextEncoder, URL, URLSearchParams, Buffer,
  }
  sandbox.globalThis = sandbox

  const out = {
    initInfo: null,       // lx.send('inited', …) 报上来的平台与音质
    initStatus: null,
    runError: null,
    responses: new Map(), // requestKey → {status, result, errorMessage}
    httpCount: 0,
    lastHttp: null,
  }

  const deliver = (action, data) => {
    if (typeof sandbox.__lx_native__ !== 'function') return
    sandbox.__lx_native__(KEY, action, JSON.stringify(data))
  }

  sandbox.__lx_native_call__ = (key, action, dataJson) => {
    if (key !== KEY) return
    let data
    try { data = dataJson ? JSON.parse(dataJson) : null } catch (e) { return }
    switch (action) {
      case 'init':
        out.initStatus = data && data.status
        out.initInfo = data && data.info
        break
      case 'showUpdateAlert':
        break
      case 'request': {
        out.httpCount++
        httpRequest(data.url, data.options).then((resp) => {
          out.lastHttp = { url: data.url, status: resp.statusCode, error: resp.error }
          deliver('response', { requestKey: data.requestKey, error: resp.error || null, response: resp })
        })
        break
      }
      case 'cancelRequest':
        break
      case 'response':
        out.responses.set(data.requestKey, data)
        break
    }
  }
  sandbox.__lx_native_call__set_timeout = (id, ms) => {
    setTimeout(() => deliver('__set_timeout__', id), Math.min(Number(ms) || 0, 60000))
  }
  sandbox.__lx_native_call__utils_str2b64 = (s) => Buffer.from(String(s), 'utf-8').toString('base64')
  sandbox.__lx_native_call__utils_b642buf = (b64) => new Uint8Array(Buffer.from(String(b64), 'base64'))
  sandbox.__lx_native_call__utils_str2md5 = (s) => crypto.createHash('md5').update(String(s), 'utf-8').digest('hex')
  sandbox.__lx_native_call__utils_aes_encrypt = aesEncrypt
  sandbox.__lx_native_call__utils_rsa_encrypt = rsaEncrypt

  const ctx = vm.createContext(sandbox)
  vm.runInContext(fs.readFileSync(PRELOAD, 'utf-8'), ctx, { filename: 'lx-preload.js' })

  // 脚本头部元信息（洛雪靠这些展示名字/版本/作者）
  const header = {}
  for (const k of ['name', 'description', 'version', 'author', 'homepage']) {
    const m = new RegExp('@' + k + '\\s+([^\\n*]+)').exec(scriptText)
    if (m) header[k] = m[1].trim()
  }

  sandbox.lx_setup(KEY, 'probe', header.name || path.basename(scriptPath || 'script.js'),
    header.description || '', header.version || '', header.author || '',
    header.homepage || '', scriptText)

  try {
    vm.runInContext(scriptText, ctx, { filename: path.basename(scriptPath || 'script.js'), timeout: 15000 })
  } catch (e) {
    out.runError = String((e && e.message) || e)
  }
  return { sandbox, out, ctx, header }
}

async function getMusicUrl(env, source, quality, musicInfo) {
  const requestKey = 'rk-' + Math.random().toString(36).slice(2)
  const done = new Promise((resolve) => {
    const t0 = Date.now()
    const timer = setInterval(() => {
      const r = env.out.responses.get(requestKey)
      if (r) { clearInterval(timer); resolve({ ...r, ms: Date.now() - t0 }) }
      else if (Date.now() - t0 > 25000) { clearInterval(timer); resolve({ status: false, errorMessage: '超时 25s' }) }
    }, 100)
  })
  env.sandbox.__lx_native__(KEY, 'request', JSON.stringify({
    requestKey,
    data: { source, action: 'musicUrl', info: { type: quality, musicInfo } },
  }))
  return done
}

// ───────────────────────── 用 SDK 搜一首歌拿 musicInfo ─────────────────────────

async function sdkSearch(source, name) {
  const responses = new Map()
  let seq = 0
  const sandbox = {
    console: { log() {}, warn() {}, error() {} },
    __lx_key__: KEY,
    __lx_crypto__: (op, argsJson) => {
      const a = JSON.parse(argsJson)
      const strip = (k) => String(k).replace(/-----[A-Z ]+-----/g, '').replace(/\s/g, '')
      const toPem = (k) => '-----BEGIN PUBLIC KEY-----\n' +
        strip(k).replace(/(.{64})/g, '$1\n').trim() + '\n-----END PUBLIC KEY-----\n'
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
          return Buffer.concat([c.update(Buffer.from(data, 'base64')), c.final()]).toString('base64')
        }
        case 'rsaEncrypt': {
          const [text, key, padding] = a
          const pad = String(padding).includes('NoPadding')
            ? crypto.constants.RSA_NO_PADDING : crypto.constants.RSA_PKCS1_OAEP_PADDING
          return crypto.publicEncrypt({ key: toPem(key), padding: pad },
            Buffer.from(text, 'base64')).toString('base64')
        }
        default: throw new Error('unsupported crypto op: ' + op)
      }
    },
    __lx_native_call__: (key, action, dataJson) => {
      if (key !== KEY) return
      const data = JSON.parse(dataJson)
      if (action === 'http') {
        httpRequest(data.url, data.options).then((resp) => {
          if (typeof sandbox.__lx_http_settle__ === 'function') {
            sandbox.__lx_http_settle__(data.id, JSON.stringify(resp))
          }
        })
        return
      }
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
  const ctx = vm.createContext(sandbox)
  vm.runInContext(fs.readFileSync(BUNDLE, 'utf-8'), ctx, { filename: 'lx-music-sdk.bundle.js' })

  const call = (action, payload) => {
    const reqId = 's' + (seq++)
    return new Promise((resolve, reject) => {
      const timer = setTimeout(() => reject(new Error('SDK 超时 30s')), 30000)
      responses.set(reqId, (d) => { clearTimeout(timer); d.ok ? resolve(d.data) : reject(new Error(d.error)) })
      ctx.lxSdkCall(reqId, action, JSON.stringify(payload))
    })
  }
  const r = await call('search', { source, name, page: 1, limit: 1 })
  return r && r.list && r.list[0] ? r.list[0] : null
}

// ───────────────────────── 验证 URL 真能拉 ─────────────────────────

function probeUrl(url, hop = 0) {
  return new Promise((resolve) => {
    let u
    try { u = new URL(url) } catch (e) { return resolve({ ok: false, why: 'URL 非法' }) }
    const lib = u.protocol === 'https:' ? https : http
    const req = lib.request(url, {
      method: 'GET',
      headers: { Range: 'bytes=0-1', 'User-Agent': 'Mozilla/5.0' },
      timeout: 12000,
    }, (res) => {
      const ct = String(res.headers['content-type'] || '').toLowerCase()
      const code = res.statusCode
      // 3xx 要跟：音源经常给一个中转地址
      if (code >= 300 && code < 400 && res.headers.location && hop < 3) {
        res.resume()
        const next = new URL(res.headers.location, url).toString()
        return probeUrl(next, hop + 1).then((r) => resolve({ ...r, hops: hop + 1 }))
      }
      res.resume()
      // 判据要严：**只看 2xx 不够** —— 酷狗会返回 200 + application/json 的错误体，
      // 那种"成功"喂给音箱只会静音。必须是音频类型，或者 206（Range 成立说明是真流）。
      const isAudio = /^(audio\/|video\/|application\/octet-stream)/.test(ct)
      const ok = (code === 200 || code === 206) && (isAudio || code === 206)
      let why = null
      if (!ok) {
        if (code === 200 && /json/.test(ct)) why = '返回 JSON 而非音频（多半是错误体）'
        else if (/text\/html/.test(ct)) why = '返回 HTML（不是音频）'
        else why = '状态码 ' + code + ' / ' + (ct || '无类型')
      }
      resolve({
        ok,
        code,
        type: ct || null,
        len: res.headers['content-length'] || null,
        hops: hop || 0,
        why,
      })
    })
    req.on('error', (e) => resolve({ ok: false, why: String((e && e.message) || e) }))
    req.on('timeout', () => req.destroy(new Error('timeout')))
    req.end()
  })
}

// ───────────────────────── 主流程 ─────────────────────────

async function probeOne(scriptPath, args) {
  const scriptText = fs.readFileSync(scriptPath, 'utf-8')
  const env = await loadScript(scriptText, scriptPath)
  // 很多脚本要先请求服务端配置才 lx.send('inited')，读完脚本立刻看 initStatus 会误判
  // 「init 失败」。这里等它最多 15 秒 —— 冤枉一支好音源比多等 15 秒糟得多。
  if (env.out.initStatus === null) {
    const deadline = Date.now() + 15000
    while (env.out.initStatus === null && Date.now() < deadline) {
      await new Promise((r) => setTimeout(r, 200))
    }
  }
  const res = {
    script: path.basename(path.dirname(scriptPath)) + '/' + path.basename(scriptPath),
    name: env.header.name || null,
    version: env.header.version || null,
    runError: env.out.runError,
    initOk: env.out.initStatus === true,
    sources: {},
  }
  if (!res.initOk) {
    res.error = env.out.runError || '脚本没有成功 init（lx.send("inited") 没回来）'
    return res
  }
  const info = env.out.initInfo || {}
  const srcs = Object.keys(info.sources || {})
  res.declared = srcs

  const targets = args.source ? [args.source] : srcs
  for (const s of targets) {
    const entry = { qualitys: (info.sources[s] && info.sources[s].qualitys) || [] }
    try {
      let musicInfo
      if (args.songmid && (!args.source || args.source === s)) {
        musicInfo = { songmid: args.songmid, source: s, name: args.name, singer: '' }
      } else {
        const hit = await sdkSearch(s, args.name)
        if (!hit) { entry.result = 'no-search-hit'; res.sources[s] = entry; continue }
        musicInfo = hit
        entry.hit = `${hit.name} - ${hit.singer}`
      }
      const q = entry.qualitys.includes(args.quality) ? args.quality : (entry.qualitys[0] || '128k')
      const r = await getMusicUrl(env, s, q, musicInfo)
      if (!r.status) {
        entry.result = 'fail'
        entry.error = r.errorMessage || '未知'
      } else {
        const url = r.result && r.result.data && r.result.data.url
        entry.result = url ? 'url' : 'empty'
        entry.url = url || null
        entry.ms = r.ms
        if (url) {
          const v = await probeUrl(url)
          entry.playable = v.ok
          entry.http = { code: v.code, type: v.type, why: v.why }
          if (!v.ok) entry.result = 'unplayable'
          if (v.hops) entry.hops = v.hops
        }
      }
    } catch (e) {
      entry.result = 'error'
      entry.error = String((e && e.message) || e)
    }
    res.sources[s] = entry
  }
  res.httpCount = env.out.httpCount
  if (lastAsyncError) { res.asyncError = lastAsyncError; lastAsyncError = null }
  return res
}

;(async () => {
  const args = parseArgs(process.argv)
  const files = []
  if (args.script) files.push(args.script)
  if (args.allScripts) {
    for (const d of fs.readdirSync(args.allScripts)) {
      const f = path.join(args.allScripts, d, 'latest.js')
      if (fs.existsSync(f)) files.push(f)
      else if (fs.existsSync(path.join(args.allScripts, d))) files.push(path.join(args.allScripts, d))
    }
  }
  if (!files.length) {
    console.error('用法: node scripts/lx-source-probe.js --script <path> [--source wy] [--name 周杰伦] [--json]')
    process.exit(1)
  }

  const all = []
  for (const f of files) {
    try {
      all.push(await probeOne(f, args))
    } catch (e) {
      all.push({ script: path.basename(f), error: String((e && e.message) || e) })
    }
  }

  if (args.json) {
    console.log(JSON.stringify(all, null, 2))
  } else {
    for (const r of all) {
      console.log(`\n=== ${r.script} ${r.name ? '「' + r.name + '」' : ''} ${r.version || ''}`)
      if (r.error) { console.log('  ✗ ' + r.error); continue }
      console.log('  声明的平台: ' + (r.declared || []).join(', '))
      for (const [s, e] of Object.entries(r.sources)) {
        const icon = e.result === 'url' ? (e.playable ? '✅' : '⚠️') : '❌'
        let line = `  ${icon} ${s}: ${e.result}`
        if (e.hit) line += `  《${e.hit}》`
        if (e.ms) line += `  ${e.ms}ms`
        if (e.http) line += `  HTTP ${e.http.code} ${e.http.type || ''} ${e.http.why || ''}`
        if (e.error) line += `  ← ${String(e.error).slice(0, 60)}`
        console.log(line)
      }
    }
  }
  process.exit(0)
})()
