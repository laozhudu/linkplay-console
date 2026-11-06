package io.github.laozhudu.linkplay.protocol.media

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** 路径 → 本地文件 的解析器。做成接口是因为 App 侧要按 MediaStore 的 id 映射，测试只需临时文件。 */
fun interface MediaResolver {
    /** 返回 null 表示 404。 */
    fun resolve(path: String): File?
}

/**
 * 手机侧本地媒体 HTTP 服务。
 *
 * 为什么必须有它：音箱是「渲染器」，它只会自己发起 HTTP 拉流。手机要播本地文件，
 * 就得把文件通过 HTTP 暴露出来——原厂 App 用的是 NanoHTTPD（字节码里 `serveFile(): given
 * homeDir is not a directory.` 是它的经典报错），我们重写成一个不依赖 Android、
 * 因此可以在纯 JVM 单测里跑的服务。
 *
 * 实现要点（都是 renderer 兼容性关键）：
 * - 支持 `HEAD`：不少 DLNA 设备先 HEAD 再决定是否 GET；
 * - 支持 `Range: bytes=a-b`，回 `206 Partial Content` + `Content-Range`：
 *   没有 Range 支持，拽进度条会失败、部分固件直接不播；
 * - 回 `Accept-Ranges: bytes`；
 * - 每个连接 `Connection: close`（嵌入式设备不会复用连接，长连接反而挂住）；
 * - 只做单层路径校验，防止 `..` 逃逸出解析器允许的范围。
 */
class LocalMediaServer(
    private val resolver: MediaResolver,
    private val bindAddress: String = "0.0.0.0",
    /** 0 表示由系统分配空闲端口。 */
    private val preferredPort: Int = 0,
    private val advertiseHost: String? = null,
) {
    /**
     * 直接由内存提供的文本资源（目前只有播放列表 m3u）。
     *
     * 为什么要这个：把整个列表交给设备时，设备需要一个**可访问的 m3u 地址**
     * （实测 `setPlayerCmd:playlist:<url>:<idx>` 会真的来 GET 它，plircount 随之变成列表长度）。
     * 而列表内容是动态生成的，不该落到临时文件里再靠 resolver 解析。
     */
    private val virtuals = java.util.concurrent.ConcurrentHashMap<String, VirtualFile>()

    /**
     * 直接对外提供**磁盘上的文件**（路径 → 文件）。
     *
     * 和 [virtuals] 的区别：那一个把内容整个放内存（适合几百字节的 m3u），
     * 而 B 站转出来的音频有 5MB 上下 —— 整份塞内存既浪费又可能 OOM。
     * 这里只记路径，响应时走下面那条**带 Range 的文件流式分支**，
     * 顺带白拿了断点续传能力（音箱请求时会带 `Range`）。
     */
    private val extraFiles = java.util.concurrent.ConcurrentHashMap<String, java.io.File>()

    private data class VirtualFile(val body: ByteArray, val contentType: String)

    private var serverSocket: ServerSocket? = null
    private val running = AtomicBoolean(false)
    private val pool = Executors.newFixedThreadPool(4)

    val port: Int get() = serverSocket?.localPort ?: -1

    /**
     * 推流统计。诊断页要回答的是「音乐没声音，是根本没请求、请求了没发出去、
     * 还是发到一半断了」—— 三种情况的修法完全不同，而界面看起来一模一样。
     */
    @Volatile
    private var statsSnapshot = MediaServerStats()

    fun stats(): MediaServerStats = statsSnapshot

    private fun bump(transform: (MediaServerStats) -> MediaServerStats) {
        synchronized(this) { statsSnapshot = transform(statsSnapshot) }
    }

    /** 对外宣告的基地址，例如 `http://192.168.1.5:8123`。 */
    fun baseUrl(host: String? = null): String {
        val h = host ?: advertiseHost ?: "127.0.0.1"
        return "http://$h:$port"
    }

    /** 登记一段内存文本并返回**相对路径**（调用方自己拼 baseUrl）。 */
    fun publishText(path: String, body: String, contentType: String = "text/plain; charset=utf-8"): String {
        val normalized = if (path.startsWith("/")) path else "/$path"
        virtuals[normalized] = VirtualFile(body.toByteArray(Charsets.UTF_8), contentType)
        return normalized
    }

    /**
     * 登记一个磁盘文件，返回**相对路径**（调用方自己拼 [baseUrl]）。
     *
     * ⚠️ **`path` 的后缀决定 `Content-Type`**（`.mp4` → `audio/mp4`），
     * 所以对外的路径要带上正确扩展名，哪怕真实文件名不是那样。
     */
    fun publishFile(path: String, file: java.io.File): String {
        val normalized = if (path.startsWith("/")) path else "/$path"
        require(file.isFile) { "publishFile: 不是文件 ${file.absolutePath}" }
        extraFiles[normalized] = file
        return normalized
    }

    fun unpublishFile(path: String) {
        extraFiles.remove(if (path.startsWith("/")) path else "/$path")
    }

    fun unpublish(path: String) {
        extraFiles.remove(if (path.startsWith("/")) path else "/$path")
        virtuals.remove(if (path.startsWith("/")) path else "/$path")
    }

    fun start(): Int {
        check(!running.get()) { "server already started" }
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
                pool.execute { serve(client) }
            }
        }, "local-media-server").apply { isDaemon = true }.start()
        return socket.localPort
    }

    fun stop() {
        running.set(false)
        runCatching { serverSocket?.close() }
        serverSocket = null
        pool.shutdownNow()
    }

    // ---- HTTP 处理 ----

    private fun handle(socket: Socket, progress: Progress) {
        socket.use { s ->
            s.soTimeout = 10_000
            val input = s.getInputStream()
            val output = CountingOutputStream(s.getOutputStream(), progress)
            val requestLine = readLine(input) ?: return
            val parts = requestLine.split(' ')
            if (parts.size < 2) return respond(output, 400, "Bad Request", null)
            val method = parts[0].uppercase()
            val target = parts[1]

            // 读掉头部（我们只用 Range）
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
                return respond(output, 405, "Method Not Allowed", null)
            }

            val path = target.substringBefore('?')
            if (path.contains("..")) return respond(output, 403, "Forbidden", null)

            virtuals[path]?.let { v ->
                val headers = buildString {
                    append("HTTP/1.1 200 OK\r\n")
                    append("Content-Type: ").append(v.contentType).append("\r\n")
                    append("Content-Length: ").append(v.body.size).append("\r\n")
                    append("Accept-Ranges: none\r\n")
                    append("Connection: close\r\n\r\n")
                }
                output.expect(v.body.size.toLong())
                output.write(headers.toByteArray(Charsets.ISO_8859_1))
                if (method == "GET") output.write(v.body)
                output.flush()
                return
            }

            val file = extraFiles[path] ?: resolver.resolve(path)
            if (file == null || !file.isFile) {
                return respond(output, 404, "Not Found", null)
            }

            val total = file.length()
            val contentType = contentTypeOf(file.name)
            val range = parseRange(rangeHeader, total)

            if (range == null) {
                val headers = buildString {
                    append("HTTP/1.1 200 OK\r\n")
                    append("Content-Type: ").append(contentType).append("\r\n")
                    append("Content-Length: ").append(total).append("\r\n")
                    append("Accept-Ranges: bytes\r\n")
                    append("Connection: close\r\n\r\n")
                }
                output.write(headers.toByteArray(Charsets.ISO_8859_1))
                output.expect(total)
                if (method == "GET") stream(file, 0, total, output)
                output.flush()
                return
            }

            val (start, endInclusive) = range
            val length = endInclusive - start + 1
            val headers = buildString {
                append("HTTP/1.1 206 Partial Content\r\n")
                append("Content-Type: ").append(contentType).append("\r\n")
                append("Content-Length: ").append(length).append("\r\n")
                append("Accept-Ranges: bytes\r\n")
                append("Content-Range: bytes ").append(start).append('-').append(endInclusive)
                append('/').append(total).append("\r\n")
                append("Connection: close\r\n\r\n")
            }
            output.write(headers.toByteArray(Charsets.ISO_8859_1))
            output.expect(length)
            if (method == "GET") stream(file, start, length, output)
            output.flush()
        }
    }

    private fun stream(file: File, offset: Long, length: Long, output: OutputStream) {
        file.inputStream().use { instr ->
            var skipped = 0L
            while (skipped < offset) {
                val s = instr.skip(offset - skipped)
                if (s <= 0) break
                skipped += s
            }
            val buffer = ByteArray(16 * 1024)
            var remaining = length
            while (remaining > 0) {
                val toRead = minOf(buffer.size.toLong(), remaining).toInt()
                val read = instr.read(buffer, 0, toRead)
                if (read <= 0) break
                output.write(buffer, 0, read)
                remaining -= read
            }
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

    /**
     * 一条连接的处理入口 —— **异常绝不外抛**。
     *
     * ## 为什么这层必须有（2026-11-04 的崩溃记录）
     *
     * 原来这里是 `pool.execute { handle(client) }`，`handle` 里没有任何 catch。
     * 音箱拉到一半主动断开（切歌、缓冲满、换 Wi-Fi、播放器 close）时，
     * `output.write` 抛 `SocketException: Software caused connection abort`，
     * 从线程池线程冒出去 → `CrashGuard` 装的默认处理器接住 → **记录并杀进程**。
     *
     * 用户看到的是「听本地音乐，切首歌 App 就闪退了」。
     * 这不是 App 有缺陷，是**把正常网络事件当成了未捕获异常**。
     *
     * 所以：IO 断开只计数（诊断页可见），其它异常也只计数不外抛。
     * 真正的缺陷会以「失败」计数出现在诊断里，而不是把 App 打死。
     */
    private fun serve(client: Socket) {
        val progress = Progress()
        bump { it.copy(requests = it.requests + 1) }
        try {
            handle(client, progress)
            bump { it.copy(completed = it.completed + 1) }
        } catch (e: IOException) {
            // stop() 会 shutdownNow 并关 socket，那条路径上的中断是预期的
            if (!running.get()) return
            bump {
                it.copy(
                    aborted = it.aborted + 1,
                    lastAbortMs = System.currentTimeMillis(),
                    lastAbortNote = "客户端中途断开：${e.message}（已发 ${human(progress.written)} / 共 ${human(progress.total)}）",
                )
            }
        } catch (e: Throwable) {
            bump {
                it.copy(
                    failed = it.failed + 1,
                    lastFailedNote = "${e.javaClass.simpleName}: ${e.message}（已发 ${human(progress.written)}）",
                )
            }
        } finally {
            runCatching { client.close() }
        }
    }

    private fun human(bytes: Long): String = when {
        bytes >= 1024L * 1024 -> String.format(java.util.Locale.US, "%.1fMB", bytes / 1024.0 / 1024.0)
        bytes >= 1024 -> "${bytes / 1024}KB"
        else -> "${bytes}B"
    }

    private fun respond(output: OutputStream, code: Int, reason: String, body: String?) {
        val payload = (body ?: reason).toByteArray(Charsets.UTF_8)
        val headers = "HTTP/1.1 $code $reason\r\n" +
            "Content-Type: text/plain; charset=utf-8\r\n" +
            "Content-Length: ${payload.size}\r\n" +
            "Connection: close\r\n\r\n"
        output.write(headers.toByteArray(Charsets.ISO_8859_1))
        output.write(payload)
        output.flush()
    }

    companion object {
        /** 解析 `Range: bytes=a-b` / `bytes=a-` / `bytes=-n`，非法或不可满足返回 null（按整文件处理）。 */
        fun parseRange(header: String?, total: Long): Pair<Long, Long>? {
            if (header.isNullOrBlank() || total <= 0) return null
            val spec = header.substringAfter("bytes=", "").trim()
            if (spec.isEmpty()) return null
            val first = spec.split(',').first().trim()
            val dash = first.indexOf('-')
            if (dash < 0) return null
            val startRaw = first.substring(0, dash).trim()
            val endRaw = first.substring(dash + 1).trim()
            return try {
                if (startRaw.isEmpty()) {
                    // 后缀形式：最后 n 字节
                    val n = endRaw.toLong()
                    if (n <= 0) return null
                    val start = (total - n).coerceAtLeast(0)
                    start to (total - 1)
                } else {
                    val start = startRaw.toLong()
                    if (start >= total) return null
                    val end = if (endRaw.isEmpty()) total - 1 else minOf(endRaw.toLong(), total - 1)
                    if (end < start) return null
                    start to end
                }
            } catch (e: NumberFormatException) {
                null
            }
        }

        fun contentTypeOf(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
            "mp3" -> "audio/mpeg"
            "m4a", "aac", "mp4" -> "audio/mp4"
            "flac" -> "audio/flac"
            "ogg", "oga" -> "audio/ogg"
            "wav" -> "audio/wav"
            "wma" -> "audio/x-ms-wma"
            "ape" -> "audio/x-ape"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            // 播放列表：设备取 m3u 时按这个类型认（实测 Content-Type 不影响，但按规范给对）
            "m3u", "m3u8" -> "audio/x-mpegurl"
            else -> "application/octet-stream"
        }
    }
}

/**
 * 推流统计快照。诊断页直接读它，回答"声音没了是哪一段断的"。
 *
 * [aborted] 是**正常事件**（音箱切歌/缓冲满/换网），不是缺陷；
 * [failed] 才可能是我们自己的问题（读文件失败等）。
 * 两者分开计数，就是为了不再把前者当崩溃。
 */
data class MediaServerStats(
    val requests: Int = 0,
    val completed: Int = 0,
    val aborted: Int = 0,
    val failed: Int = 0,
    val lastAbortMs: Long = 0L,
    val lastAbortNote: String = "",
    val lastFailedNote: String = "",
)
