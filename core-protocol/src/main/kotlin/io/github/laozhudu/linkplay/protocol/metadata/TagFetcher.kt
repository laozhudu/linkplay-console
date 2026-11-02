package io.github.laozhudu.linkplay.protocol.metadata

import java.net.HttpURLConnection
import java.net.URL

/**
 * 从网络地址上读**内嵌标签**（封面 / 歌词 / 标题）。
 *
 * ## 为什么要分「读头」和「读尾」
 *
 * 实测本项目的 NAS（fnOS/MiniDLNA）：
 * - MP3 的 ID3v2 在文件**开头** → 读头 512KB 就够；
 * - M4A 的 `moov`/`ilst` 在文件**末尾**（不是 faststart）→ 必须读尾。
 *
 * 所以流程是：先读头判断容器类型 → ID3/FLAC 就地解析 → MP4 则再读尾部。
 * 全程用 HTTP Range，不整首下载（一首 13MB 的歌只取几百 KB）。
 *
 * 为什么手机能读：投给音箱的 URL 手机也在同一局域网，直接取得到。
 */
object TagFetcher {

    private const val HEAD_BYTES = 256 * 1024
    private const val TAIL_BYTES = 1024 * 1024

    fun fetch(
        url: String,
        connectTimeoutMs: Int = 4_000,
        readTimeoutMs: Int = 8_000,
        extraHeaders: Map<String, String> = emptyMap(),
    ): EmbeddedTags? {
        val head = readRange(url, 0L, (HEAD_BYTES - 1).toLong(), connectTimeoutMs, readTimeoutMs, extraHeaders)
            ?: return null
        // 1) ID3 / FLAC：开头就能解析
        TagParser.parse(head)?.let { if (it.hasCover || it.lyrics != null || it.title != null) return it }
        // 2) MP4：moov 在末尾
        if (Mp4Tags.looksLikeMp4(head)) {
            val total = contentLength(url, connectTimeoutMs, readTimeoutMs, extraHeaders)
            if (total != null && total > 8) {
                val from = (total - TAIL_BYTES).coerceAtLeast(0)
                val tail = readRange(url, from, total - 1, connectTimeoutMs, readTimeoutMs, extraHeaders)
                if (tail != null) Mp4Tags.parse(tail)?.let { return it }
            }
        }
        return TagParser.parse(head)
    }

    private fun contentLength(
        url: String,
        connectTimeoutMs: Int,
        readTimeoutMs: Int,
        extraHeaders: Map<String, String>,
    ): Long? {
        var conn: HttpURLConnection? = null
        return try {
            conn = open(url, connectTimeoutMs, readTimeoutMs, extraHeaders)
            conn.requestMethod = "HEAD"
            conn.connect()
            // 有的 DLNA 服务器不认真回 Content-Length，退回 -1 时用 Range 探测
            val len = conn.getHeaderField("Content-Length")?.toLongOrNull()
            if (len != null && len > 0) return len
            // Content-Range: bytes 0-0/13632582
            conn.getHeaderField("Content-Range")?.substringAfter('/', "")?.toLongOrNull()
        } catch (_: Throwable) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    internal fun readRange(
        url: String,
        from: Long,
        to: Long,
        connectTimeoutMs: Int,
        readTimeoutMs: Int,
        extraHeaders: Map<String, String>,
    ): ByteArray? {
        var conn: HttpURLConnection? = null
        return try {
            conn = open(url, connectTimeoutMs, readTimeoutMs, extraHeaders)
            conn.setRequestProperty("Range", "bytes=$from-$to")
            val code = conn.responseCode
            if (code !in 200..299) return null
            val cap = (to - from + 1).toInt().coerceAtMost(8 * 1024 * 1024)
            // 服务器忽略 Range（回 200 全量）时也只会读到 cap 就停，不会把整首歌拖下来
            conn.inputStream.use { it.readCapped(cap) }
        } catch (_: Throwable) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    private fun open(
        url: String,
        connectTimeoutMs: Int,
        readTimeoutMs: Int,
        extraHeaders: Map<String, String>,
    ): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        this.connectTimeout = connectTimeoutMs
        this.readTimeout = readTimeoutMs
        instanceFollowRedirects = true
        useCaches = false
        setRequestProperty("Connection", "close")
        // 部分 NAS 对无 UA 的请求直接 403
        setRequestProperty("User-Agent", "linkplay-console/1.0")
        extraHeaders.forEach { (k, v) -> setRequestProperty(k, v) }
    }

    private fun java.io.InputStream.readCapped(cap: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream(minOf(cap, 64 * 1024))
        val buf = ByteArray(16 * 1024)
        var total = 0
        while (total < cap) {
            val n = read(buf, 0, minOf(buf.size, cap - total))
            if (n <= 0) break
            out.write(buf, 0, n)
            total += n
        }
        return out.toByteArray()
    }
}
