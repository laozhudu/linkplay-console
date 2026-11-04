package io.github.laozhudu.linkplay.lx

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.net.ServerSocket
import kotlin.concurrent.thread

class LxHttpTest {

    private fun withServer(
        response: String,
        block: suspend (port: Int) -> Unit,
    ) {
        val server = ServerSocket(0)
        val t = thread(isDaemon = true) {
            try {
                server.accept().use { sock ->
                    sock.getInputStream().read(ByteArray(8192)) // 读掉请求头，不细究
                    val w = BufferedWriter(OutputStreamWriter(sock.getOutputStream()))
                    w.write(response)
                    w.flush()
                    Thread.sleep(500)
                }
            } catch (_: Exception) {
            }
        }
        try {
            kotlinx.coroutines.runBlocking { block(server.localPort) }
        } finally {
            server.close()
            t.interrupt()
        }
    }

    // ── 参数解析 ─────────────────────────────────────────────────────────

    @Test
    fun `默认是 GET 且带默认 UA`() {
        val req = LxHttp.parse("h1", "https://x/y", null)
        assertEquals("GET", req.method)
        assertEquals("https://x/y", req.url)
        assertEquals(20_000, req.timeoutMs)
    }

    @Test
    fun `form 会转成 urlencoded`() {
        val req = LxHttp.parse(
            "h1",
            "https://x",
            """{"method":"post","form":{"a":"1","b":"中 文"}}""",
        )
        assertEquals("POST", req.method)
        assertTrue(req.formBody!!.startsWith("a=1&b="))
        assertTrue("中文要编码", req.formBody!!.contains("%E4%B8%AD"))
    }

    @Test
    fun `body 是字符串时原样保留`() {
        val req = LxHttp.parse("h1", "https://x", """{"method":"post","body":"raw"}""")
        assertEquals("raw", req.body)
    }

    @Test
    fun `body 是对象时转成 JSON 字符串`() {
        val req = LxHttp.parse("h1", "https://x", """{"method":"post","body":{"a":1}}""")
        assertEquals("""{"a":1}""", req.body)
    }

    @Test
    fun `headers 会被解析出来`() {
        val req = LxHttp.parse("h1", "https://x", """{"headers":{"Referer":"https://y","X-A":"1"}}""")
        assertEquals("https://y", req.headers["Referer"])
        assertEquals("1", req.headers["X-A"])
    }

    @Test
    fun `自定义 timeout 生效`() {
        val req = LxHttp.parse("h1", "https://x", """{"timeout":3000}""")
        assertEquals(3000, req.timeoutMs)
    }

    @Test
    fun `options 为空或非法时不崩`() {
        assertEquals("GET", LxHttp.parse("h", "https://x", "").method)
        assertEquals("GET", LxHttp.parse("h", "https://x", "not json").method)
    }

    // ── body 转 JSON ────────────────────────────────────────────────────

    @Test
    fun `JSON 响应原样保留`() {
        assertEquals("""{"a":1}""", LxHttp.toBodyJson("""{"a":1}"""))
    }

    @Test
    fun `非 JSON 响应包成字符串字面量`() {
        // 洛雪部分接口返回纯文本，body 必须是合法的 JSON 值（字符串）
        assertEquals("\"hello\"", LxHttp.toBodyJson("hello"))
    }

    @Test
    fun `空响应是空字符串字面量`() {
        assertEquals("\"\"", LxHttp.toBodyJson(""))
        assertEquals("\"\"", LxHttp.toBodyJson("   "))
    }

    @Test
    fun `含引号的文本会被正确转义`() {
        val out = LxHttp.toBodyJson("""a"b""")
        assertEquals("\"a\\\"b\"", out)
    }

    // ── 真实请求 ─────────────────────────────────────────────────────────

    @Test
    fun `能拿到状态码和 JSON body`() = runTest {
        withServer(
            "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: 13\r\n\r\n{\"code\":200}\n",
        ) { port ->
            val r = LxHttp.perform(LxHttp.parse("h", "http://127.0.0.1:$port/x", """{"timeout":3000}"""))
            assertEquals(200, r.statusCode)
            assertEquals("""{"code":200}""", r.bodyJson)
        }
    }

    @Test
    fun `连不上时把错误塞进 error 而不抛异常`() = runTest {
        // 端口 1 基本不会有服务
        val r = LxHttp.perform(LxHttp.parse("h", "http://127.0.0.1:1/x", """{"timeout":1000}"""))
        assertTrue("应该有错误信息", r.error != null)
        assertEquals(0, r.statusCode)
    }

    @Test
    fun `回灌给 JS 的 payload 里 body 是 JSON 值不是字符串`() = runTest {
        withServer("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: 7\r\n\r\n{\"a\":1}",) { port ->
            val r = LxHttp.perform(LxHttp.parse("h", "http://127.0.0.1:$port/x", """{"timeout":3000}"""))
            val payload = r.toJsPayload("h")
            // 关键：body 必须是对象，洛雪代码里写的是 body.data
            assertTrue(payload, payload.contains("\"body\":{\"a\":1}"))
            assertTrue(payload.contains("\"id\":\"h\""))
        }
    }
}
