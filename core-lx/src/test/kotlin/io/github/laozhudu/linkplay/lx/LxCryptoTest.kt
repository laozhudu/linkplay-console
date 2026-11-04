package io.github.laozhudu.linkplay.lx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class LxCryptoTest {

    private fun b64(s: String) = Base64.getEncoder().encodeToString(s.toByteArray())
    private fun bytesB64(b: ByteArray) = Base64.getEncoder().encodeToString(b)

    // ── md5 / sha1 ──────────────────────────────────────────────────────

    @Test
    fun `md5 是标准 hex`() {
        assertEquals("e10adc3949ba59abbe56e057f20f883e", LxCrypto.md5("123456"))
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", LxCrypto.md5(""))
    }

    @Test
    fun `sha1 是标准 hex`() {
        // 官方 hashSHA1 用的是 hex，不是 base64
        assertEquals("7c4a8d09ca3762af61e59520943dc26494f8941b", LxCrypto.sha1("123456"))
    }

    // ── base64 / buffer ─────────────────────────────────────────────────

    @Test
    fun `字符串转 base64`() {
        assertEquals("5L2g5aW9", LxCrypto.strToBase64("你好"))
    }

    @Test
    fun `base64 转字节数组 JSON`() {
        // 官方 utils_b642buf 返回的是 [..] 形式的 JSON 数组，不是 ByteArray
        assertEquals("[1,2,3]", LxCrypto.base64ToByteArrayJson(bytesB64(byteArrayOf(1, 2, 3))))
    }

    @Test
    fun `字节数组 JSON 里高位字节按无符号处理`() {
        // 0xFF 在 Java 里是 -1，JSON 里必须是 255，否则 JS 的 Uint8Array 会出错
        assertEquals("[255,0]", LxCrypto.base64ToByteArrayJson(bytesB64(byteArrayOf(0xFF.toByte(), 0))))
    }

    // ── AES ─────────────────────────────────────────────────────────────

    @Test
    fun `AES CBC PKCS7 加解密可逆`() {
        val key = bytesB64(ByteArray(16) { it.toByte() })
        val iv = bytesB64(ByteArray(16) { (it * 2).toByte() })
        val plain = b64("""{"a":1}""")

        val enc = LxCrypto.aesEncrypt(plain, key, iv, "AES/CBC/PKCS7Padding")
        val dec = LxCrypto.aesDecrypt(enc, key, iv, "AES/CBC/PKCS7Padding")
        assertEquals(plain, dec)
    }

    @Test
    fun `AES CBC 与 JDK 标准实现一致`() {
        // 用 JDK 直接算一遍做交叉验证，确保不是"自己加密自己解密"的假通过
        val keyBytes = ByteArray(16) { (it + 1).toByte() }
        val ivBytes = ByteArray(16) { (it + 3).toByte() }
        val plainText = "0123456789abcdef" // 16 字节，正好一个块
        val plain = b64(plainText)

        // JDK 没有 PKCS7Padding（那是 BouncyCastle 的名字），对 16 字节块 PKCS5 等价
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyBytes, "AES"), IvParameterSpec(ivBytes))
        val expected = bytesB64(cipher.doFinal(plainText.toByteArray()))

        assertEquals(expected, LxCrypto.aesEncrypt(plain, bytesB64(keyBytes), bytesB64(ivBytes), "AES/CBC/PKCS7Padding"))
    }

    @Test
    fun `AES ECB NoPadding 能处理整块数据`() {
        val key = bytesB64(ByteArray(16) { it.toByte() })
        val plainText = "0123456789abcdef"
        val plain = b64(plainText)

        val enc = LxCrypto.aesEncrypt(plain, key, "", "AES")
        val dec = LxCrypto.aesDecrypt(enc, key, "", "AES")
        assertEquals(plain, dec)
    }

    @Test
    fun `key 超过 16 字节时只取前 16`() {
        // 官方 Math.min 的逻辑：多出来的部分要丢掉
        val longKey = bytesB64(ByteArray(32) { it.toByte() })
        val shortKey = bytesB64(ByteArray(16) { it.toByte() })
        val iv = bytesB64(ByteArray(16))
        val plain = b64("hello")

        assertEquals(
            LxCrypto.aesEncrypt(plain, shortKey, iv, "AES/CBC/PKCS7Padding"),
            LxCrypto.aesEncrypt(plain, longKey, iv, "AES/CBC/PKCS7Padding"),
        )
    }

    @Test
    fun `iv 不足 16 字节时右侧补零`() {
        val key = bytesB64(ByteArray(16) { it.toByte() })
        val shortIv = bytesB64(byteArrayOf(1, 2, 3))
        val paddedIv = bytesB64(byteArrayOf(1, 2, 3) + ByteArray(13))
        val plain = b64("hello")

        assertEquals(
            LxCrypto.aesEncrypt(plain, key, paddedIv, "AES/CBC/PKCS7Padding"),
            LxCrypto.aesEncrypt(plain, key, shortIv, "AES/CBC/PKCS7Padding"),
        )
    }

    // ── 统一入口 ────────────────────────────────────────────────────────

    @Test
    fun `call 能分发 md5 与 sha1`() {
        assertEquals(LxCrypto.md5("abc"), LxCrypto.call("md5", """["abc"]"""))
        assertEquals(LxCrypto.sha1("abc"), LxCrypto.call("sha1", """["abc"]"""))
    }

    @Test
    fun `call 对未知操作抛异常`() {
        val e = runCatching { LxCrypto.call("nope", "[]") }.exceptionOrNull()
        assertTrue(e is IllegalArgumentException)
        assertTrue(e!!.message!!.contains("不支持的加密操作"))
    }

    @Test
    fun `call 的 aes 参数顺序与官方一致`() {
        val key = bytesB64(ByteArray(16) { it.toByte() })
        val iv = bytesB64(ByteArray(16) { (it * 2).toByte() })
        val plain = b64("data")
        val viaCall = LxCrypto.call(
            "aesEncrypt",
            """["$plain","$key","$iv","AES/CBC/PKCS7Padding"]""",
        )
        assertEquals(LxCrypto.aesEncrypt(plain, key, iv, "AES/CBC/PKCS7Padding"), viaCall)
    }
}
