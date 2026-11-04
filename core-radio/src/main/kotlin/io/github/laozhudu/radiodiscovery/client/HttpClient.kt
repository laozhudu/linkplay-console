package io.github.laozhudu.radiodiscovery.client

import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

/**
 * 抽象 HTTP 客户端，模块本身不绑定 OkHttp / Ktor，默认提供 JDK 实现。
 * 集成方可替换为自己的 HttpClient 实现（如 OkHttp、Ktor Client 等）。
 */
interface HttpClient {
    /**
     * @param readBody 是否读取响应体。
     *
     * **为什么需要这个开关**：电台地址分两种 ——
     * 一种是一次性返回的 JSON（Radio Browser 的搜索接口，必须读 body）；
     * 另一种是**永不结束的直播流**（`.mp3` / `.m3u8`）。
     *
     * 对直播流调 `readText()` 会一直等到 readTimeout 再抛异常。实测
     * `stream.radioparadise.com` 与 `lhttp.qtfm.cn` 都是无限流，
     * 读到 8 秒超时才失败 —— 也就是说「本来就能直接播的台」反而卡 8 秒然后报错。
     *
     * [io.github.laozhudu.radiodiscovery.resolver.StreamResolver] 只关心 `Location` 和
     * `Content-Type` 两个响应头，所以它传 `readBody = false`，读完头立刻断开。
     */
    suspend fun get(
        url: String,
        headers: Map<String, String>,
        readBody: Boolean = true,
    ): HttpResponse
}

data class HttpResponse(
    val statusCode: Int,
    val headers: Map<String, List<String>>,
    val body: String,
)

/**
 * 基于 java.net.HttpURLConnection 的默认实现，零额外依赖。
 */
class JdkHttpClient(
    private val connectTimeoutMs: Int = 8000,
    private val readTimeoutMs: Int = 8000,
) : HttpClient {

    override suspend fun get(
        url: String,
        headers: Map<String, String>,
        readBody: Boolean,
    ): HttpResponse = withContext(Dispatchers.IO) {
        val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        connection.apply {
            requestMethod = "GET"
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            instanceFollowRedirects = false // 我们手动处理重定向，拿到最终地址
            headers.forEach { (k, v) -> addRequestProperty(k, v) }
        }
        try {
            val headerFields = connection.headerFields
            // headerFields 的 key 可能含 null（HTTP 状态行那一项），要滤掉
            val allHeaders = if (headerFields != null) {
                headerFields
                    .filterKeys { it != null }
                    .mapValues { (_, v) -> v.orEmpty() }
            } else {
                emptyMap()
            }
            // 关键：不读 body 时立刻返回，绝不碰 inputStream。
            // 直播流没有 EOF，一旦开始读就只能等 readTimeout 抛异常。
            val body = if (!readBody) {
                ""
            } else {
                try {
                    connection.inputStream?.bufferedReader()?.use { it.readText() } ?: ""
                } catch (e: java.io.IOException) {
                    // 4xx/5xx 时 getInputStream() 会抛，改读 errorStream 拿错误正文
                    connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                }
            }
            HttpResponse(connection.responseCode, allHeaders, body)
        } finally {
            connection.disconnect()
        }
    }
}
