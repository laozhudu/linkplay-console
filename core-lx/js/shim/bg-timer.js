// react-native-background-timer 的替身：QuickJS 里没有后台线程概念，
// 用普通定时器顶上（洛雪只用它做请求超时兜底）。
const BackgroundTimer = {
  setTimeout: (fn, ms) => setTimeout(fn, ms),
  clearTimeout: (id) => clearTimeout(id),
  setInterval: (fn, ms) => setInterval(fn, ms),
  clearInterval: (id) => clearInterval(id),
}
export default BackgroundTimer
export const setTimeout = (fn, ms) => globalThis.setTimeout(fn, ms)
export const clearTimeout = (id) => globalThis.clearTimeout(id)
export const setInterval = (fn, ms) => globalThis.setInterval(fn, ms)
export const clearInterval = (id) => globalThis.clearInterval(id)
