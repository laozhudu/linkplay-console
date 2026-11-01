package io.github.laozhudu.linkplay.protocol

/**
 * 设备侧的十六进制编码规则（逆向自原厂 App `com.wifiaudio.utils.a.b`）。
 *
 * 原厂实现：对 byte[] 逐字节取 `Integer.toHexString(b & 0xFF)`，不足两位左补 0。
 * 因此 SSID / Wi-Fi 密码在 URL 里一律是「小写、无分隔符」的 hex 串（不是 Base64）。
 * 设备侧的 setDeviceName / SlaveDeviceName 反而用 URL 编码，两者不要混用。
 */
object HexCodec {
    private val HEX = "0123456789abcdef".toCharArray()

    fun encode(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            sb.append(HEX[v ushr 4]).append(HEX[v and 0x0F])
        }
        return sb.toString()
    }

    /** 与 [encode] 相同但输出大写，用于 `ConnectMasterAp` 的 ssid/pwd 字段。 */
    fun encodeUpper(bytes: ByteArray): String = encode(bytes).uppercase()

    fun encodeUtf8(text: String): String = encode(text.toByteArray(Charsets.UTF_8))

    fun encodeUtf8Upper(text: String): String = encodeUpper(text.toByteArray(Charsets.UTF_8))

    /** 严格解码；输入非十六进制或长度为奇数返回 null。 */
    fun decodeOrNull(hex: String): ByteArray? {
        if (hex.length % 2 != 0) return null
        val out = ByteArray(hex.length / 2)
        for (i in out.indices) {
            val hi = Character.digit(hex[i * 2], 16)
            val lo = Character.digit(hex[i * 2 + 1], 16)
            if (hi < 0 || lo < 0) return null
            out[i] = ((hi shl 4) or lo).toByte()
        }
        return out
    }

    /**
     * 设备字段里既有明文又有 hex 的情况（如 `getStatusEx.essid`）。
     * 能解成合法 UTF-8 就返回解码结果，否则原样返回。
     */
    fun decodeIfHex(text: String): String {
        val bytes = decodeOrNull(text) ?: return text
        if (bytes.isEmpty()) return text
        val decoded = runCatching { bytes.toString(Charsets.UTF_8) }.getOrNull() ?: return text
        if (decoded.isEmpty() || decoded.any { it.code == 0xFFFD }) return text
        if (decoded.any { it.code < 0x20 && it != '\t' }) return text
        return decoded
    }
}
