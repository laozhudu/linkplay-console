package io.github.laozhudu.radiodiscovery.client

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.net.ServerSocket
import kotlin.concurrent.thread

/**
 * 用本地假服务器验证「不读 body 就不会被直播流卡住」。
 *
 * 这是缺陷 2 的端到端回归：真实直播流永不 EOF，`readText()` 会一直等到
 * readTimeout 再抛异常。这里用一个"发完头就一直挂住不发结尾"的 socket 复现。
 */
class JdkHttpClientTest {

    /** 起一个只发响应头、之后挂住不关的服务器，模拟直播流。 */
    private suspend fun withHangingServer(
        headers: String,
        block: suspend (port: Int) -> Unit,
    ) {
        val server = ServerSocket(0)
        val t = thread(isDaemon = true) {
            try {
                server.accept().use { sock ->
                    val w = BufferedWriter(OutputStreamWriter(sock.getOutputStream()))
                    w.write(headers)
                    w.flush()
                    // 关键：不发结束、不关连接 —— 直播流就是这样
                    Thread.sleep(5000)
                }
            } catch (_: Exception) {
            }
        }
        try {
            block(server.localPort)
        } finally {
            server.close()
            t.interrupt()
        }
    }

    @Test
    fun `readBody 为 false 时立刻返回不被直播流卡住`() = runTest {
        withHangingServer(
            "HTTP/1.1 200 OK\r\nContent-Type: audio/mpeg\r\nConnection: close\r\n\r\n",
        ) { port ->
            val client = JdkHttpClient(connectTimeoutMs = 2000, readTimeoutMs = 2000)
            val start = System.currentTimeMillis()
            val resp = client.get("http://127.0.0.1:$port/live.mp3", emptyMap(), readBody = false)
            val elapsed = System.currentTimeMillis() - start

            assertEquals(200, resp.statusCode)
            assertEquals("", resp.body)
            assertEquals(
                "不该等到 readTimeout（2000ms），实测耗时 ${elapsed}ms",
                true,
                elapsed < 1000,
            )
            // 头还是要能读到
            val ct = resp.headers.entries
                .firstOrNull { it.key.equals("Content-Type", ignoreCase = true) }
                ?.value?.firstOrNull()
            assertEquals("audio/mpeg", ct)
        }
    }

    @Test
    fun `readBody 为 true 时会读到正常响应的 body`() = runTest {
        withHangingServer(
            "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: 2\r\n\r\n[]",
        ) { port ->
            val client = JdkHttpClient(connectTimeoutMs = 2000, readTimeoutMs = 2000)
            val resp = client.get("http://127.0.0.1:$port/json", emptyMap(), readBody = true)

            assertEquals(200, resp.statusCode)
            assertEquals("[]", resp.body)
        }
    }

    @Test
    fun `302 的 Location 头能读到`() = runTest {
        withHangingServer(
            "HTTP/1.1 302 Found\r\nLocation: http://real.host/live.mp3\r\nContent-Length: 0\r\n\r\n",
        ) { port ->
            val client = JdkHttpClient(connectTimeoutMs = 2000, readTimeoutMs = 2000)
            val resp = client.get("http://127.0.0.1:$port/x", emptyMap(), readBody = false)

            assertEquals(302, resp.statusCode)
            val loc = resp.headers.entries
                .firstOrNull { it.key.equals("Location", ignoreCase = true) }
                ?.value?.firstOrNull()
            assertEquals("http://real.host/live.mp3", loc)
        }
    }
}
