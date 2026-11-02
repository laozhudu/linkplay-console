package io.github.laozhudu.linkplay.protocol.media

import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 假上游：按预设返回状态、头与定长内容，并记录被要求过的 Range。 */
private class FakeUpstream(
    private val status: Int = 200,
    private val body: ByteArray = ByteArray(1000) { (it % 256).toByte() },
    private val honorRange: Boolean = false,
    private val headers: Map<String, String> = mapOf("Content-Type" to "audio/mpeg"),
) : Upstream {
    var lastHeaders: Map<String, String> = emptyMap()
    var lastRange: String? = null
    var calls = 0

    override fun open(url: String, requestHeaders: Map<String, String>, rangeHeader: String?): UpstreamResponse {
        calls++
        lastHeaders = requestHeaders
        lastRange = rangeHeader
        val total = body.size
        if (!honorRange || rangeHeader == null) {
            return UpstreamResponse(
                status,
                headers + ("Content-Length" to total.toString()),
                ByteArrayInputStream(body),
            )
        }
        val spec = rangeHeader.removePrefix("bytes=")
        val a = spec.substringBefore('-').toIntOrNull() ?: 0
        val b = spec.substringAfter('-').toIntOrNull() ?: (total - 1)
        val end = minOf(b, total - 1)
        val slice = body.copyOfRange(a, end + 1)
        return UpstreamResponse(
            206,
            headers + mapOf(
                "Content-Length" to slice.size.toString(),
                "Content-Range" to "bytes $a-$end/$total",
            ),
            ByteArrayInputStream(slice),
        )
    }
}

/** 直接对服务发起一次请求，返回状态行 + 头 + 正文。 */
private fun fetch(port: Int, path: String, range: String? = null, method: String = "GET"): Triple<Int, Map<String, String>, ByteArray> {
    val conn = (URL("http://127.0.0.1:$port$path").openConnection() as HttpURLConnection).apply {
        requestMethod = method
        range?.let { setRequestProperty("Range", it) }
        connectTimeout = 3000
        readTimeout = 3000
    }
    val status = conn.responseCode
    val headers = buildMap {
        conn.headerFields.forEach { (k, v) -> if (k != null && v != null && v.isNotEmpty()) put(k.lowercase(), v.first()) }
    }
    val body = runCatching { (if (status in 200..299) conn.inputStream else conn.errorStream)?.readBytes() ?: ByteArray(0) }
        .getOrDefault(ByteArray(0))
    return Triple(status, headers, body)
}

class RelayMediaServerTest {

    @Test
    fun `只有登记过的 token 可访问，其余 404`() {
        val upstream = FakeUpstream()
        val relay = RelayMediaServer(bindAddress = "127.0.0.1", upstream = upstream).also { it.start() }
        try {
            val (code, _, _) = fetch(relay.port, "/s/deadbeef.mp3")
            assertEquals("未登记的 token 必须 404，否则就成了开放代理", 404, code)
            assertEquals("不该为未知 token 去访问上游", 0, upstream.calls)
        } finally {
            relay.stop()
        }
    }

    @Test
    fun `转发时带上补的请求头`() {
        val upstream = FakeUpstream()
        val relay = RelayMediaServer(bindAddress = "127.0.0.1", upstream = upstream).also { it.start() }
        try {
            val path = relay.publish(
                RelayTarget("http://example/a.mp3", mapOf("Referer" to "https://music.example/", "Cookie" to "k=v")),
            )
            val (code, _, body) = fetch(relay.port, path)
            assertEquals(200, code)
            assertEquals(1000, body.size)
            assertEquals("https://music.example/", upstream.lastHeaders["Referer"])
            assertEquals("k=v", upstream.lastHeaders["Cookie"])
        } finally {
            relay.stop()
        }
    }

    @Test
    fun `Range 原样透传给上游`() {
        val upstream = FakeUpstream(honorRange = true)
        val relay = RelayMediaServer(bindAddress = "127.0.0.1", upstream = upstream).also { it.start() }
        try {
            val path = relay.publish(RelayTarget("http://example/a.mp3"))
            fetch(relay.port, path, range = "bytes=100-199")
            assertEquals("bytes=100-199", upstream.lastRange)
        } finally {
            relay.stop()
        }
    }

    @Test
    fun `上游忽略 Range 时由本机切片并回 206`() {
        // 很多 CDN 对 Range 仍回 200 全长；直接透传会让设备拖进度条时从头播
        val upstream = FakeUpstream(honorRange = false)
        val relay = RelayMediaServer(bindAddress = "127.0.0.1", upstream = upstream).also { it.start() }
        try {
            val path = relay.publish(RelayTarget("http://example/a.mp3"))
            val (code, headers, body) = fetch(relay.port, path, range = "bytes=100-199")
            assertEquals(206, code)
            assertEquals("bytes 100-199/1000", headers["content-range"])
            assertEquals(100, body.size)
            // 切片内容必须是原字节的第 100..199 个，而不是从头发一段
            assertEquals((100 % 256).toByte(), body.first())
            assertEquals((199 % 256).toByte(), body.last())
        } finally {
            relay.stop()
        }
    }

    @Test
    fun `上游防盗链 403 时回 502 并说明原因`() {
        val upstream = FakeUpstream(status = 403)
        val relay = RelayMediaServer(bindAddress = "127.0.0.1", upstream = upstream).also { it.start() }
        try {
            val path = relay.publish(RelayTarget("http://example/a.mp3"))
            val (code, _, body) = fetch(relay.port, path)
            assertEquals(502, code)
            assertTrue(String(body, Charsets.UTF_8).contains("Referer"))
        } finally {
            relay.stop()
        }
    }

    @Test
    fun `HEAD 只回头不留正文`() {
        val upstream = FakeUpstream()
        val relay = RelayMediaServer(bindAddress = "127.0.0.1", upstream = upstream).also { it.start() }
        try {
            val path = relay.publish(RelayTarget("http://example/a.mp3"))
            val (code, headers, body) = fetch(relay.port, path, method = "HEAD")
            assertEquals(200, code)
            assertEquals("1000", headers["content-length"])
            assertEquals(0, body.size)
        } finally {
            relay.stop()
        }
    }

    @Test
    fun `每次登记都是新 token`() {
        val relay = RelayMediaServer(bindAddress = "127.0.0.1", upstream = FakeUpstream())
        val a = relay.publish(RelayTarget("http://example/a.mp3"))
        val b = relay.publish(RelayTarget("http://example/a.mp3"))
        assertNotEquals(a, b, "同一上游两次登记应得到不同 token，避免可预测")
    }

    @Test
    fun `撤销后立刻 404`() {
        val relay = RelayMediaServer(bindAddress = "127.0.0.1", upstream = FakeUpstream()).also { it.start() }
        try {
            val path = relay.publish(RelayTarget("http://example/a.mp3"))
            relay.revoke(path)
            assertEquals(404, fetch(relay.port, path).first)
        } finally {
            relay.stop()
        }
    }

    @Test
    fun `过期条目被清理`() {
        var clock = 0L
        val relay = RelayMediaServer(
            bindAddress = "127.0.0.1",
            upstream = FakeUpstream(),
            now = { clock },
        ).also { it.start() }
        try {
            val path = relay.publish(RelayTarget("http://example/a.mp3", ttlMs = 1_000))
            assertEquals(200, fetch(relay.port, path).first)
            clock = 5_000
            assertEquals("TTL 到期必须失效", 404, fetch(relay.port, path).first)
        } finally {
            relay.stop()
        }
    }
}
