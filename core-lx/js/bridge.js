/**
 * 原生桥的 JS 侧。
 *
 * 这里定义的是「洛雪 musicSdk 依赖的那几个 RN 模块」的替身。原版跑在 React Native 里，
 * 这些能力由 RN 原生模块提供；我们跑在 QuickJS 里，改由本模块转调 Kotlin。
 *
 * 三条通道：
 *   __lx_crypto__(op, argsJson) -> string      同步，AES/RSA/MD5/SHA1
 *   __lx_native_call__(key, action, json)      异步，发出去就完（http 请求 / 日志 / 回包）
 *   __lx_native__(key, action, json)           原生 -> JS，由本文件注册
 */

const KEY = globalThis.__lx_key__

/** 同步加密调用：洛雪的 aesEncryptSync 等是同步 API，不能走异步桥 */
export function cryptoCall(op, args) {
  if (typeof globalThis.__lx_crypto__ !== 'function') throw new Error('crypto bridge missing')
  return globalThis.__lx_crypto__(op, JSON.stringify(args))
}

/** 异步发一条消息给原生（不等回包） */
export function nativeCall(action, data) {
  if (typeof globalThis.__lx_native_call__ !== 'function') throw new Error('native bridge missing')
  globalThis.__lx_native_call__(KEY, action, JSON.stringify(data))
}
