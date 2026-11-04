package io.github.laozhudu.linkplay.lx

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * 洛雪脚本和 musicSdk 需要的加密原语。
 *
 * ## 为什么放在这里而不是抄 JS
 *
 * 官方 preload 里 `utils.crypto.*` 全部是转调原生（`__lx_native_call__utils_*`），
 * 因为 JS 里做 RSA 既慢又要额外库。Android 的 `javax.crypto` 现成，且**纯 JVM 可测**。
 *
 * ## 参数约定（照抄官方 `AES.java` / `RSA.java`）
 *
 * - 输入输出**全部是 Base64 字符串**，不是原始字节。
 * - AES 的 key/iv 都是 Base64 解码后取**前 16 字节**（官方 `Math.min(iv.length, 16)`）。
 * - RSA 的公钥是**去掉 PEM 头尾的裸 Base64**，补回 `-----BEGIN PUBLIC KEY-----` 再用。
 */
object LxCrypto {

    private val json = Json { ignoreUnknownKeys = true }

    // 官方 Android 版用的是 "AES/CBC/PKCS7Padding"（Conscrypt 认这个名字）。
// 但标准 JDK 没有 PKCS7Padding 这个 transformation —— 对 16 字节块来说
// PKCS5Padding 与 PKCS7Padding 完全等价，改用后者，这样单测能在 JVM 上跑。
    private const val AES_CBC_PKCS5 = "AES/CBC/PKCS5Padding"
    private const val AES_ECB_NOPAD = "AES/ECB/NoPadding"
    private const val AES_ECB_PKCS5 = "AES/ECB/PKCS5Padding"
    private const val RSA_OAEP = "RSA/ECB/OAEPWithSHA1AndMGF1Padding"
    private const val RSA_NOPAD = "RSA/ECB/NoPadding"

    /** `utils.crypto.md5(str)` —— 返回 hex 字符串。 */
    fun md5(text: String): String = hex(MessageDigest.getInstance("MD5").digest(text.toByteArray()))

    /** musicSdk 的 `hashSHA1`。 */
    fun sha1(text: String): String = hex(MessageDigest.getInstance("SHA-1").digest(text.toByteArray()))

    /** `utils.buffer` 的 str2b64。 */
    fun strToBase64(text: String): String = b64(text.toByteArray())

    /**
     * `utils.buffer.from(base64, 'base64')` 的字节数组表示。
     *
     * 官方返回给 JS 的是 `[12,34,...]` 形式的 JSON 数组（见 `QuickJS.java` 的
     * `utils_b642buf`），JS 侧再 `new Uint8Array(...)`。这里保持一致。
     */
    fun base64ToByteArrayJson(b64Text: String): String {
        val bytes = Base64.getDecoder().decode(b64Text.trim())
        return buildJsonArray { bytes.forEach { add(JsonPrimitive(it.toInt() and 0xFF)) } }.toString()
    }

    /**
     * AES 加解密。
     *
     * @param mode 官方两种取值：`AES/CBC/PKCS7Padding` 与 `AES`（即 ECB/NoPadding）
     * @param ivBase64 CBC 必须给；ECB 可传空串
     */
    fun aesEncrypt(dataBase64: String, keyBase64: String, ivBase64: String, mode: String): String =
        aes(dataBase64, keyBase64, ivBase64, mode, encrypt = true)

    fun aesDecrypt(dataBase64: String, keyBase64: String, ivBase64: String, mode: String): String =
        aes(dataBase64, keyBase64, ivBase64, mode, encrypt = false)

    private fun aes(
        dataBase64: String,
        keyBase64: String,
        ivBase64: String,
        mode: String,
        encrypt: Boolean,
    ): String {
        val isCbc = mode.contains("CBC", ignoreCase = true)
        val noPadding = mode.contains("NoPadding", ignoreCase = true)
        val transformation = when {
            isCbc -> AES_CBC_PKCS5
            noPadding -> AES_ECB_NOPAD
            else -> AES_ECB_PKCS5
        }
        val key = Base64.getDecoder().decode(keyBase64.trim()).copyOf(16)
        val keySpec = SecretKeySpec(key, "AES")
        val cipher = Cipher.getInstance(transformation)
        val opmode = if (encrypt) Cipher.ENCRYPT_MODE else Cipher.DECRYPT_MODE
        if (isCbc) {
            val iv = ByteArray(16)
            val raw = if (ivBase64.isBlank()) ByteArray(0) else Base64.getDecoder().decode(ivBase64.trim())
            System.arraycopy(raw, 0, iv, 0, minOf(raw.size, 16))
            cipher.init(opmode, keySpec, IvParameterSpec(iv))
        } else {
            cipher.init(opmode, keySpec)
        }
        return b64(cipher.doFinal(Base64.getDecoder().decode(dataBase64.trim())))
    }

    /**
     * RSA 加密。公钥是**去掉 PEM 头尾的裸 Base64**。
     *
     * @param padding `RSA/ECB/OAEPWithSHA1AndMGF1Padding` 或 `RSA/ECB/NoPadding`
     */
    fun rsaEncrypt(dataBase64: String, publicKeyBody: String, padding: String): String {
        val cipher = Cipher.getInstance(
            if (padding.contains("NoPadding", ignoreCase = true)) RSA_NOPAD else RSA_OAEP,
        )
        val der = Base64.getDecoder().decode(stripPem(publicKeyBody))
        val key = java.security.KeyFactory.getInstance("RSA")
            .generatePublic(java.security.spec.X509EncodedKeySpec(der))
        cipher.init(Cipher.ENCRYPT_MODE, key)
        return b64(cipher.doFinal(Base64.getDecoder().decode(dataBase64.trim())))
    }

    fun rsaDecrypt(dataBase64: String, privateKeyBody: String, padding: String): String {
        val cipher = Cipher.getInstance(
            if (padding.contains("NoPadding", ignoreCase = true)) RSA_NOPAD else RSA_OAEP,
        )
        val der = Base64.getDecoder().decode(stripPem(privateKeyBody))
        val key = java.security.KeyFactory.getInstance("RSA")
            .generatePrivate(java.security.spec.PKCS8EncodedKeySpec(der))
        cipher.init(Cipher.DECRYPT_MODE, key)
        return b64(cipher.doFinal(Base64.getDecoder().decode(dataBase64.trim())))
    }

    private fun stripPem(key: String): String = key
        .replace("-----BEGIN PUBLIC KEY-----", "")
        .replace("-----END PUBLIC KEY-----", "")
        .replace("-----BEGIN PRIVATE KEY-----", "")
        .replace("-----END PRIVATE KEY-----", "")
        .filterNot { it.isWhitespace() }

    private fun b64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    private fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

    /**
     * 统一入口：给 JS 的同步桥 `__lx_crypto__(op, argsJson)` 用。
     *
     * 返回的永远是**字符串**（JS 侧直接用）；失败抛异常，由调用方翻译成人话。
     */
    fun call(op: String, argsJson: String): String {
        val args = json.parseToJsonElement(argsJson).jsonArray
        fun str(i: Int): String = args[i].jsonPrimitive.content
        fun optStr(i: Int): String = args.getOrNull(i)?.jsonPrimitive?.content.orEmpty()
        return when (op) {
            "md5" -> md5(str(0))
            "sha1" -> sha1(str(0))
            "aesEncrypt" -> aesEncrypt(str(0), str(1), optStr(2), str(3))
            "aesDecrypt" -> aesDecrypt(str(0), str(1), optStr(2), str(3))
            "rsaEncrypt" -> rsaEncrypt(str(0), str(1), str(2))
            "rsaDecrypt" -> rsaDecrypt(str(0), str(1), str(2))
            else -> throw IllegalArgumentException("不支持的加密操作: $op")
        }
    }
}
