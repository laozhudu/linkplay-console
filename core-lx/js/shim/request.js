/**
 * 替换洛雪的 `src/utils/request.js`。
 *
 * 原版用 RN 的网络栈 + BackgroundTimer 做超时；这里把请求丢给 Kotlin，
 * 拿到回包后再 resolve 对应的 Promise。
 *
 * 保持原版的返回形状 `{ promise, cancelHttp }` —— 洛雪 musicSdk 里所有调用点
 * 都是 `httpFetch(url, opts).promise.then(...)`。
 */
import { nativeCall } from '@/bridge'

let seq = 0
const pending = new Map()

/** 原生拿到回包后调这个（由 bridge 注册到 globalThis） */
globalThis.__lx_http_settle__ = (id, payloadJson) => {
  const entry = pending.get(id)
  if (!entry) return
  pending.delete(id)
  let payload
  try {
    payload = JSON.parse(payloadJson)
  } catch (e) {
    entry.reject(new Error('bad response payload'))
    return
  }
  if (payload.error) {
    entry.reject(new Error(payload.error))
    return
  }
  entry.resolve({
    statusCode: payload.statusCode,
    statusMessage: payload.statusMessage || '',
    headers: payload.headers || {},
    body: payload.body,
    raw: payload.body,
  })
}

export const httpFetch = (url, options = { method: 'get' }) => {
  const id = `h${seq++}`
  const promise = new Promise((resolve, reject) => {
    pending.set(id, { resolve, reject })
    nativeCall('http', { id, url, options })
  })
  return {
    promise,
    cancelHttp() {
      pending.delete(id)
    },
  }
}

export const httpGet = (url, options, callback) => {
  const obj = httpFetch(url, options)
  if (typeof callback === 'function') {
    obj.promise.then((resp) => callback(null, resp, resp.body)).catch((err) => callback(err))
  }
  return obj
}

export const checkUrl = async(url, options = {}) => {
  const resp = await httpFetch(url, { method: 'head', ...options }).promise
  return resp.statusCode >= 200 && resp.statusCode < 400
}
