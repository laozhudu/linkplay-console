package io.github.laozhudu.linkplay.protocol.media

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** 一条需要转发给设备的音频源。 */
data class RelayTarget(
    val url: String,
    /**
     * 转发时要替设备补上的请求头。
     *
     * 为什么必须由我们补：实测这台 DOSS 拉流时的请求头只有
     * `Host` / `User-Agent: curl/7.36.0` / `Icy-MetaData` / `Connection`（docs/16 §2），
     * 没有 Referer、没有 Cookie。凡是做了防盗链的直链，设备自己去拉必然 403。
     */
    val headers: Map<String, String> = emptyMap(),
    /** 该条目的存活时间；到点后自动失效，避免长期留一堆可被猜到的入口。 */
    val ttlMs: Long = 6 * 60 * 60 * 1000L,
)

/** 上游响应。把 `HttpURLConnection` 抽出来，单测才能注入假上游。 */
data class UpstreamResponse(
    val status: Int,
    val headers: Map<String, String>,
    val body: InputStream?,
) {
    fun header(name: String): String? = headers.entries
        .firstOrNull { it.key.equals(name, ignoreCase = true) }?.value
}

/** 上游访问的抽象。 */
fun interface Upstream {
    /** @param rangeHeader 设备发来的 `Range`，原样透传；没有则 null */
    fun open(url: String, headers: Map<String, String>, rangeHeader: String?): UpstreamResponse
}

/** 默认实现：`HttpURLConnection`，够用且无第三方依赖。 */
class HttpUrlConnectionUpstream(private val timeoutMs: Int = 15_000) : Upstream {
    override fun open(url: String, headers: Map<String, String>, rangeHeader: String?): UpstreamResponse {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            instanceFollowRedirects = true
            requestMethod = "GET"
            // 上游多半会校验 UA；用设备自己的 curl UA 往往直接被拒，所以默认伪装成常见播放器
            setRequestProperty("User-Agent", headers["User-Agent"] ?: DEFAULT_UA)
            headers.forEach { (k, v) ->
                if (!k.equals("User-Agent", ignoreCase = true)) setRequestProperty(k, v)
            }
            rangeHeader?.let { setRequestProperty("Range", it) }
        }
        val status = conn.responseCode
        val map = buildMap {
            conn.headerFields.forEach { (k, vs) ->
                if (k != null && vs != null && vs.isNotEmpty()) put(k, vs.first())
            }
        }
        val body = runCatching {
            if (status in 200..299) conn.inputStream else conn.errorStream
        }.getOrNull()
        return UpstreamResponse(status, map, body)
    }

    companion object {
        /** 与常见播放器一致的 UA：多数 CDN 不会拦它。 */
        const val DEFAULT_UA =
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"
    }
}

/**
 * 转发媒体服务：把「设备拉不了的上游链接」变成「设备能拉的本机链接」。
 *
 * 它解决的是 docs/16 里实测到的硬限制：设备只发 `curl/7.36.0` 的裸 GET，
 * 不带 Referer/Cookie，防盗链直链必然 403。这里替它补头、并把 Range 语义接住。
 *
 * 三个设计约束：
 * 1. **不是开放代理**：只有 [publish] 登记过的 token 才可访问，token 随机且带 TTL。
 *    否则局域网里任何设备都能借它访问任意地址，等于把内网当跳板。
 * 2. **上游忽略 Range 时自己切**：不少 CDN 对 Range 返回 200 + 全长，直接透传会让设备
 *    拖进度条时从头播。所以这里跳过 `start` 字节并自己回 206 + 正确的 `Content-Range`。
 * 3. **每个连接 `Connection: close`**：嵌入式设备不复用连接，长连接反而会挂住（与
 *    [LocalMediaServer] 同一结论）。
 */
class RelayMediaServer(
    private val bindAddress: String = "0.0.0.0",
    private val preferredPort: Int = 0,
    private val upstream: Upstream = HttpUrlConnectionUpstream(),
    private val now: () -> Long = { System.currentTimeMillis() },
    private val advertiseHost: String? = null,
) {
    private data class Entry(val target: RelayTarget, val createdAt: Long)

    private var serverSocket: ServerSocket? = null
    private val running = AtomicBoolean(false)
    private val pool = Executors.newFixedThreadPool(4)
    private val entries = ConcurrentHashMap<String, Entry>()
    private val random = SecureRandom()

    val port: Int get() = serverSocket?.localPort ?: -1

    fun baseUrl(host: String? = null): String =
        "http://${host ?: advertiseHost ?: "127.0.0.1"}:$port"

    fun start(): Int {
        check(!running.get()) { "relay already started" }
        val socket = ServerSocket(preferredPort, 16, InetAddress.getByName(bindAddress))
        serverSocket = socket
        running.set(true)
        Thread({
            while (running.get()) {
                val client = try {
                    socket.accept()
                } catch (e: IOException) {
                    if (running.get()) continue else break
                }
                pool.execute { handle(client) }
            }
        }, "relay-media-server").apply { isDaemon = true }.start()
        return socket.localPort
    }

    fun stop() {
        running.set(false)
        runCatching { serverSocket?.close() }
        serverSocket = null
        entries.clear()
        pool.shutdownNow()
    }

    /**
     * 登记一条上游，返回设备可直接使用的**相对路径**（形如 `/s/ab12cd34.mp3`）。
     *
     * 带上扩展名是为了让按扩展名嗅探类型的固件也能认（有些设备不看 Content-Type）。
     */
    fun publish(target: RelayTarget, extension: String = "mp3"): String {
        cleanupExpired()
        val token = newToken()
        entries[token] = Entry(target, now())
        return "/s/$token.$extension"
    }

    /** 设备实际使用的完整地址。 */
    fun urlFor(target: RelayTarget, deviceVisibleHost: String, extension: String = "mp3"): String =
        "http://$deviceVisibleHost:$port" + publish(target, extension)

    fun revoke(path: String) {
        entries.remove(tokenOf(path))
    }

    private fun newToken(): String {
        val bytes = ByteArray(8).also { random.nextBytes(it) }
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun tokenOf(path: String): String =
        path.substringBefore('?').substringAfterLast('/').substringBefore('.')

    private fun cleanupExpired() {
        val t = now()
        entries.entries.removeIf { t - it.value.createdAt > it.value.target.ttlMs }
    }

    // ---- HTTP ----

    private fun handle(socket: Socket) {
        socket.use { s ->
            s.soTimeout = 15_000
            val input = s.getInputStream()
            val output = s.getOutputStream()
            val requestLine = readLine(input) ?: return
            val parts = requestLine.split(' ')
            if (parts.size < 2) return respond(output, 400, "Bad Request", "bad request")

            val method = parts[0].uppercase()
            val target = parts[1]
            var rangeHeader: String? = null
            while (true) {
                val line = readLine(input) ?: break
                if (line.isEmpty()) break
                val idx = line.indexOf(':')
                if (idx > 0 && line.substring(0, idx).trim().equals("Range", ignoreCase = true)) {
                    rangeHeader = line.substring(idx + 1).trim()
                }
            }
            if (method != "GET" && method != "HEAD") {
                return respond(output, 405, "Method Not Allowed", "method not allowed")
            }

            cleanupExpired()
            val entry = entries[tokenOf(target)]
                ?: return respond(output, 404, "Not Found", "unknown or expired relay token")

            val resp = try {
                upstream.open(entry.target.url, entry.target.headers, rangeHeader)
            } catch (e: IOException) {
                return respond(output, 502, "Bad Gateway", "upstream error: ${e.message}")
            }

            resp.use { up ->
                if (up.status !in 200..299) {
                    return respond(
                        output, 502, "Bad Gateway",
                        "upstream ${up.status}（多半是防盗链：该链接需要 Referer/Cookie）",
                    )
                }
                val contentType = up.header("Content-Type")?.substringBefore(';')
                    ?: LocalMediaServer.contentTypeOf("x." + target.substringAfterLast('.', "mp3"))
                val upLength = up.header("Content-Length")?.toLongOrNull()
                val wanted = LocalMediaServer.parseRange(rangeHeader, upLength ?: Long.MAX_VALUE)

                if (wanted == null || up.status == 206) {
                    // 上游自己就给了 206，或设备没要 Range：直接透传
                    val length = up.header("Content-Length")?.toLongOrNull()
                    val extra = up.header("Content-Range")?.let { "Content-Range: $it\r\n" } ?: ""
                    val head = buildString {
                        append("HTTP/1.1 ").append(if (up.status == 206) "206 Partial Content" else "200 OK").append("\r\n")
                        append("Content-Type: ").append(contentType).append("\r\n")
                        length?.let { append("Content-Length: ").append(it).append("\r\n") }
                        append(extra)
                        append("Accept-Ranges: bytes\r\n")
                        append("Connection: close\r\n\r\n")
                    }
                    output.write(head.toByteArray(Charsets.ISO_8859_1))
                    if (method == "GET") up.body?.copyTo(output, 16 * 1024)
                    output.flush()
                    return
                }

                // 上游无视 Range 回了 200：自己跳字节并回 206，否则设备拖进度条会从头播
                val (start, endInclusive) = wanted
                val total = upLength ?: (endInclusive + 1)
                val length = (endInclusive - start + 1).coerceAtLeast(0)
                val head = buildString {
                    append("HTTP/1.1 206 Partial Content\r\n")
                    append("Content-Type: ").append(contentType).append("\r\n")
                    append("Content-Length: ").append(length).append("\r\n")
                    append("Content-Range: bytes ").append(start).append('-').append(endInclusive)
                    append('/').append(total).append("\r\n")
                    append("Accept-Ranges: bytes\r\n")
                    append("Connection: close\r\n\r\n")
                }
                output.write(head.toByteArray(Charsets.ISO_8859_1))
                if (method == "GET") {
                    up.body?.let { skipAndCopy(it, start, length, output) }
                }
                output.flush()
            }
        }
    }

    private fun skipAndCopy(input: InputStream, skip: Long, length: Long, output: OutputStream) {
        var skipped = 0L
        while (skipped < skip) {
            val s = input.skip(skip - skipped)
            if (s <= 0) {
                // skip 可能返回 0，退回逐字节读，避免死循环
                if (input.read() < 0) return
                skipped += 1
            } else {
                skipped += s
            }
        }
        val buffer = ByteArray(16 * 1024)
        var remaining = length
        while (remaining > 0) {
            val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            if (read <= 0) break
            output.write(buffer, 0, read)
            remaining -= read
        }
    }

    private fun readLine(input: InputStream): String? {
        val sb = StringBuilder()
        while (true) {
            val b = input.read()
            if (b == -1) return if (sb.isEmpty()) null else sb.toString()
            if (b == '\n'.code) return sb.toString().trimEnd('\r')
            sb.append(b.toChar())
            if (sb.length > 8192) return null
        }
    }

    private fun respond(output: OutputStream, code: Int, reason: String, message: String?) {
        val body = (message ?: "").toByteArray(Charsets.UTF_8)
        val head = buildString {
            append("HTTP/1.1 ").append(code).append(' ').append(reason).append("\r\n")
            append("Content-Type: text/plain; charset=utf-8\r\n")
            append("Content-Length: ").append(body.size).append("\r\n")
            append("Connection: close\r\n\r\n")
        }
        output.write(head.toByteArray(Charsets.ISO_8859_1))
        output.write(body)
        output.flush()
    }
}

/** 便于在 `use` 里关闭上游流。 */
private inline fun UpstreamResponse.use(block: (UpstreamResponse) -> Unit) {
    try {
        block(this)
    } finally {
        runCatching { body?.close() }
    }
}
