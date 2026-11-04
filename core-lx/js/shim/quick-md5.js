// react-native-quick-md5 的替身：转调 Kotlin 的同步加密桥
import { cryptoCall } from '@/bridge'

export const stringMd5 = (s) => cryptoCall('md5', [String(s)])
export default { stringMd5 }
