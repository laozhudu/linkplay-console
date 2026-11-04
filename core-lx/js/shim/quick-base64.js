// react-native-quick-base64 的替身（纯 JS，不依赖 RN）
const btoa = (s) => {
  const bytes = unescape(encodeURIComponent(String(s)))
  let out = ''
  const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/'
  for (let i = 0; i < bytes.length; i += 3) {
    const c1 = bytes.charCodeAt(i)
    const c2 = bytes.charCodeAt(i + 1)
    const c3 = bytes.charCodeAt(i + 2)
    out += chars[c1 >> 2]
    out += chars[((c1 & 3) << 4) | (isNaN(c2) ? 0 : c2 >> 4)]
    out += isNaN(c2) ? '=' : chars[((c2 & 15) << 2) | (isNaN(c3) ? 0 : c3 >> 6)]
    out += isNaN(c3) ? '=' : chars[c3 & 63]
  }
  return out
}

const atob = (s) => {
  const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/'
  let out = ''
  const clean = String(s).replace(/[^A-Za-z0-9+/=]/g, '')
  for (let i = 0; i < clean.length; i += 4) {
    const n1 = chars.indexOf(clean[i])
    const n2 = chars.indexOf(clean[i + 1])
    const n3 = chars.indexOf(clean[i + 2])
    const n4 = chars.indexOf(clean[i + 3])
    out += String.fromCharCode((n1 << 2) | (n2 >> 4))
    if (n3 !== -1 && clean[i + 2] !== '=') out += String.fromCharCode(((n2 & 15) << 4) | (n3 >> 2))
    if (n4 !== -1 && clean[i + 3] !== '=') out += String.fromCharCode(((n3 & 3) << 6) | n4)
  }
  return out
}

const toByteArray = (s) => Array.from(unescape(encodeURIComponent(atob(s))), (c) => c.charCodeAt(0))
const fromByteArray = (a) => btoa(String.fromCharCode(...a))

export { btoa, atob, toByteArray, fromByteArray }
export const toByteArrayAsync = async(s) => toByteArray(s)
export const fromByteArrayAsync = async(a) => fromByteArray(a)
export default { btoa, atob, toByteArray, fromByteArray }
