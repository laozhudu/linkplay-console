/**
 * 替换洛雪的 `src/utils/nativeModules/crypto.ts`。
 *
 * 原版转调 RN 原生模块 CryptoModule；这里转调 Kotlin 的同步桥 `__lx_crypto__`。
 * 接口保持原样（同名同签名），否则 musicSdk 里各平台的 eapi/weapi 会编译不过。
 */
import { cryptoCall } from '@/bridge'

export enum RSA_PADDING {
  OAEPWithSHA1AndMGF1Padding = 'RSA/ECB/OAEPWithSHA1AndMGF1Padding',
  NoPadding = 'RSA/ECB/NoPadding',
}

export enum AES_MODE {
  CBC_128_PKCS7Padding = 'AES/CBC/PKCS7Padding',
  ECB_128_NoPadding = 'AES',
}

const strip = (key: string) => String(key)
  .replace('-----BEGIN PUBLIC KEY-----', '')
  .replace('-----END PUBLIC KEY-----', '')
  .replace('-----BEGIN PRIVATE KEY-----', '')
  .replace('-----END PRIVATE KEY-----', '')
  .replace(/\s/g, '')

export const aesEncryptSync = (text: string, key: string, iv: string, mode: AES_MODE): string =>
  cryptoCall('aesEncrypt', [text, key, iv, mode])

export const aesDecryptSync = (text: string, key: string, iv: string, mode: AES_MODE): string =>
  cryptoCall('aesDecrypt', [text, key, iv, mode])

export const aesEncrypt = async(...args: [string, string, string, AES_MODE]) => aesEncryptSync(...args)
export const aesDecrypt = async(...args: [string, string, string, AES_MODE]) => aesDecryptSync(...args)

export const rsaEncryptSync = (text: string, key: string, padding: RSA_PADDING): string =>
  cryptoCall('rsaEncrypt', [text, strip(key), padding])

export const rsaDecryptSync = (text: string, key: string, padding: RSA_PADDING): string =>
  cryptoCall('rsaDecrypt', [text, strip(key), padding])

export const rsaEncrypt = async(...args: [string, string, RSA_PADDING]) => rsaEncryptSync(...args)
export const rsaDecrypt = async(...args: [string, string, RSA_PADDING]) => rsaDecryptSync(...args)

export const hashSHA1 = async(text: any): Promise<string> => cryptoCall('sha1', [String(text)])

export const generateRsaKey = async() => {
  throw new Error('generateRsaKey 未实现：musicSdk 不用它')
}

export const testRsa = () => true
