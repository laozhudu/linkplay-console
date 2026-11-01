package io.github.laozhudu.linkplay.protocol

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * 设备 HTTP 客户端。
 *
 * 原厂 App 用 AsyncHttpClient，默认超时 15s（配网命令 8s），并固定送
 * `Accept-Language: zh-cn|en-us`。这里保持同一语义，避免设备端行为差异：
 * 单连接（设备是嵌入式 HTTP 服务，并发容易被拒）、短超时、不跟随重定向。
 */
class DeviceHttpClient(
    private val connectTimeoutMs: Int = 5_000,
    private val readTimeoutMs: Int = 15_000,
    private val language: String = "zh-cn",
) {
    @Throws(IOException::class)
    fun get(deviceIp: String, command: String, path: String = LinkPlayCommands.HTTP_API_PATH): String {
        val url = URL("http://$deviceIp$path$command")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            instanceFollowRedirects = false
            useCaches = false
            setRequestProperty("Accept-Language", language)
            setRequestProperty("Accept", "*/*")
            setRequestProperty("Connection", "close")
        }
        try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw IOException("HTTP $code from $deviceIp: ${body.take(200)}")
            return body
        } finally {
            conn.disconnect()
        }
    }

    fun status(deviceIp: String): LinkPlayStatus =
        LinkPlayStatus.parse(get(deviceIp, LinkPlayCommands.statusEx()))

    fun slaveList(deviceIp: String): List<SlaveDevice> =
        SlaveDevice.parseList(get(deviceIp, LinkPlayCommands.slaveList()))

    fun send(deviceIp: String, command: String): String = get(deviceIp, command)

    companion object {
        /** 把 & 等保留字符编码进 command（设备对未编码的 & 会截断）。 */
        fun encodeCommandParam(value: String): String = URLEncoder.encode(value, "UTF-8")
    }
}
