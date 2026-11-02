package io.github.laozhudu.linkplay.protocol.media

import java.io.File
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * 真机联调：验证「防盗链直链 → 本机转发 → 音箱能播」。
 *
 * 为什么只在本地跑：它需要一台真实的音箱和一个本机媒体文件，CI 上没有。
 * 用环境变量开关，CI 保持绿：
 *
 * ```
 * LIVE_DEVICE=192.168.99.153 \
 * LIVE_HOST=192.168.99.226 \
 * LIVE_MEDIA=/root/.hermes/cache/scratch/media/tone1.mp3 \
 * ./gradlew :core-protocol:test --tests '*RelayLiveIT*'
 * ```
 */
class RelayLiveIT {

    /** 只认 Referer 的极简上游：模拟音乐平台的防盗链。 */
    private class GatedUpstream(private val body: ByteArray) {
        private val socket = ServerSocket(0, 8, InetAddress.getByName("0.0.0.0"))
        val port: Int get() = socket.localPort
        @Volatile var sawReferer = false
        @Volatile var hits = 0

        fun start() {
            Thread({
                while (!socket.isClosed) {
                    val client = runCatching { socket.accept() }.getOrNull() ?: break
                    Thread({ serve(client) }).apply { isDaemon = true }.start()
                }
            }, "gated-upstream").apply { isDaemon = true }.start()
        }

        private fun serve(client: Socket) = client.use { s ->
            hits++
            val inp = s.getInputStream()
            val out = s.getOutputStream()
            val requestLine = readLine(inp) ?: return
            var referer: String? = null
            var range: String? = null
            while (true) {
                val line = readLine(inp) ?: break
                if (line.isEmpty()) break
                val i = line.indexOf(':')
                if (i > 0) {
                    val k = line.substring(0, i).trim().lowercase()
                    val v = line.substring(i + 1).trim()
                    if (k == "referer") referer = v
                    if (k == "range") range = v
                }
            }
            if (referer == null || !referer.contains("only-with-referer")) {
                out.write("HTTP/1.1 403 Forbidden\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
                out.flush()
                return
            }
            sawReferer = true
            val total = body.size
            var start = 0
            var end = total - 1
            var code = "200 OK"
            if (range != null && range.startsWith("bytes=")) {
                val spec = range.removePrefix("bytes=")
                spec.substringBefore('-').toIntOrNull()?.let { start = it }
                spec.substringAfter('-').toIntOrNull()?.let { end = minOf(it, total - 1) }
                code = "206 Partial Content"
            }
            val len = end - start + 1
            val head = buildString {
                append("HTTP/1.1 ").append(code).append("\r\n")
                append("Content-Type: audio/mpeg\r\n")
                append("Content-Length: ").append(len).append("\r\n")
                append("Accept-Ranges: bytes\r\n")
                if (code.startsWith("206")) append("Content-Range: bytes $start-$end/$total\r\n")
                append("Connection: close\r\n\r\n")
            }
            out.write(head.toByteArray())
            out.write(body, start, len)
            out.flush()
        }

        private fun readLine(inp: java.io.InputStream): String? {
            val sb = StringBuilder()
            while (true) {
                val b = inp.read()
                if (b == -1) return if (sb.isEmpty()) null else sb.toString()
                if (b == '\n'.code) return sb.toString().trimEnd('\r')
                sb.append(b.toChar())
            }
        }

        fun stop() = runCatching { socket.close() }.let { }
    }

    private fun deviceApi(ip: String, command: String): String =
        URL("http://$ip/httpapi.asp?command=$command").openStream().use {
            it.readBytes().toString(Charsets.UTF_8).trim()
        }

    @Test
    fun `防盗链直链经转发后音箱能播`() {
        val device = System.getenv("LIVE_DEVICE")
        val host = System.getenv("LIVE_HOST")
        val media = System.getenv("LIVE_MEDIA")
        assumeTrue(
            "需要 LIVE_DEVICE / LIVE_HOST / LIVE_MEDIA 才跑（CI 上跳过）",
            !device.isNullOrBlank() && !host.isNullOrBlank() && !media.isNullOrBlank() &&
                File(media!!).isFile,
        )

        val body = File(media!!).readBytes()
        val upstream = GatedUpstream(body).also { it.start() }
        val relay = RelayMediaServer(bindAddress = "0.0.0.0", advertiseHost = host).also { it.start() }
        try {
            val gated = "http://$host:${upstream.port}/secret.mp3"

            // 先证明「不补头就播不了」：设备自己拉这条链必然失败
            deviceApi(device!!, "setPlayerCmd:play:$gated")
            Thread.sleep(3_000)
            val directStatus = deviceApi(device, "getPlayerStatus")
            assertTrue("直连防盗链链接应当播不了，实际：$directStatus", !directStatus.contains("\"status\":\"play\""))

            // 再用转发：补上 Referer，设备拉本机地址
            val path = relay.publish(
                RelayTarget(gated, mapOf("Referer" to "https://only-with-referer.example/")),
            )
            val relayUrl = "http://$host:${relay.port}$path"
            deviceApi(device, "setPlayerCmd:play:$relayUrl")

            var playing = false
            var lastPos = -1L
            repeat(6) {
                Thread.sleep(2_000)
                val status = deviceApi(device, "getPlayerStatus")
                val pos = Regex("\"curpos\":\"(\\d+)\"").find(status)?.groupValues?.get(1)?.toLongOrNull() ?: -1
                println("relay status=$status")
                if (status.contains("\"status\":\"play\"") && pos > lastPos) playing = true
                lastPos = pos
            }
            deviceApi(device, "setPlayerCmd:stop")

            assertTrue("上游应收到带 Referer 的请求", upstream.sawReferer)
            assertTrue("音箱应能播放转发后的地址", playing)
        } finally {
            relay.stop()
            upstream.stop()
        }
    }
}
