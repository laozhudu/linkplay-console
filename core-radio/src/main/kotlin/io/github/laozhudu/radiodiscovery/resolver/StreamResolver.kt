package io.github.laozhudu.radiodiscovery.resolver

import io.github.laozhudu.radiodiscovery.client.HttpClient
import io.github.laozhudu.radiodiscovery.client.HttpResponse
import io.github.laozhudu.radiodiscovery.client.JdkHttpClient
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

/**
 * 解析电台流地址的真实终点。
 *
 * 很多电台目录里的 URL 是中间页（如 .pls、.m3u、带 token 的跳转页），
 * 直接给播放器会失败。本解析器会跟随 HTTP 重定向，返回最终可播放的流地址。
 */
open class StreamResolver(
    private val httpClient: HttpClient = JdkHttpClient(),
    private val maxRedirects: Int = 5,
) {

    private val streamExtensions = listOf("mp3", "m3u8", "m3u", "aac", "ogg", "flac", "pls", "wma")

    /**
     * 解析给定 URL，返回最终可播放的流地址及元信息。
     */
    open suspend fun resolve(url: String): ResolvedStream {
        return withContext(Dispatchers.IO) {
            var currentUrl = url
            var redirects = 0
            var finalResponse: HttpResponse? = null

            while (redirects <= maxRedirects) {
                // readBody = false：只取响应头就断开。
                // 直播流没有 EOF，读 body 会一直等到 readTimeout（8 秒）再抛异常 ——
                // 那会让「本来就能直接播的台」反而卡 8 秒然后失败。
                // 我们只需要 Location 和 Content-Type。
                finalResponse = httpClient.get(currentUrl, mapOf(
                    "User-Agent" to "radiostation-discovery/1.0",
                    "Icy-MetaData" to "1",
                    "Accept" to "*/*"
                ), readBody = false)

                // 检查重定向头
                val location = finalResponse?.headers?.let { h ->
                    h["Location"]?.firstOrNull() ?: h["location"]?.firstOrNull()
                }

                if (location != null && redirects < maxRedirects) {
                    currentUrl = resolveRelativeUrl(currentUrl, location)
                    redirects++
                    continue
                }
                break
            }

            val finalUrl = if (finalResponse != null) currentUrl else url
            val mimeType = finalResponse?.headers?.let { h ->
                h["Content-Type"]?.firstOrNull() ?: h["content-type"]?.firstOrNull()
            }?.takeUnless { it.isBlank() }
            val isHls =
                finalUrl.lowercase().endsWith(".m3u8") ||
                mimeType?.lowercase()?.contains("mpegurl") == true ||
                mimeType?.lowercase()?.contains("vlm") == true

            ResolvedStream(
                originalUrl = url,
                finalUrl = finalUrl,
                mimeType = mimeType,
                isHls = isHls,
                isDirectStream = streamExtensions.any { finalUrl.lowercase().endsWith(".$it") } || isHls,
            )
        }
    }

    private fun resolveRelativeUrl(base: String, location: String): String {
        if (location.startsWith("http://") || location.startsWith("https://")) return location
        if (location.startsWith("//")) {
            val uri = java.net.URI(base)
            return "${uri.scheme}:$location"
        }
        val uri = java.net.URI(base)
        val scheme = uri.scheme ?: "https"
        val host = uri.host ?: ""
        val port = if (uri.port != -1) ":${uri.port}" else ""
        // 如果 location 以 / 开头，是绝对路径（相对于域名根），不应拼接当前目录
        val pathPrefix = if (location.startsWith("/")) "" else {
            uri.path?.let { p ->
                val lastSlash = p.lastIndexOf('/')
                if (lastSlash >= 0) p.substring(0, lastSlash + 1) else "/"
            } ?: "/"
        }
        return "$scheme://$host$port$pathPrefix$location"
    }
}
