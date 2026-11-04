package io.github.laozhudu.linkplay.protocol.media

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.net.HttpURLConnection
import java.net.Socket
import java.net.URL
import java.nio.file.Files

/** 真起一个 socket 服务来测，不用 mock —— HTTP 语义（206/Range/HEAD）只有真连才能验证。 */
class LocalMediaServerTest {

    private lateinit var server: LocalMediaServer
    private lateinit var file: File

    @Before
    fun setUp() {
        file = Files.createTempFile("track", ".mp3").toFile()
        file.writeBytes(ByteArray(1000) { (it % 251).toByte() })
        val resolver = MediaResolver { path ->
            when {
                path == "/t/1" -> file
                path == "/t/missing" -> null
                else -> null
            }
        }
        server = LocalMediaServer(resolver)
        server.start()
    }

    @After
    fun tearDown() {
        server.stop()
        file.delete()
    }

    private fun open(path: String, method: String = "GET", range: String? = null): HttpURLConnection {
        val conn = URL("http://127.0.0.1:${server.port}$path").openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = 3_000
        conn.readTimeout = 3_000
        range?.let { conn.setRequestProperty("Range", it) }
        return conn
    }

    @Test
    fun `full get returns the whole file with audio mime and accept-ranges`() {
        val conn = open("/t/1")
        assertEquals(200, conn.responseCode)
        assertEquals("audio/mpeg", conn.getHeaderField("Content-Type"))
        assertEquals("bytes", conn.getHeaderField("Accept-Ranges"))
        assertEquals(1000, conn.getHeaderField("Content-Length").toInt())
        val body = conn.inputStream.readBytes()
        assertEquals(1000, body.size)
        assertEquals(file.readBytes().toList(), body.toList())
        conn.disconnect()
    }

    @Test
    fun `head returns headers without body`() {
        val conn = open("/t/1", method = "HEAD")
        assertEquals(200, conn.responseCode)
        assertEquals(1000, conn.getHeaderField("Content-Length").toInt())
        assertEquals(0, conn.inputStream.available())
        conn.disconnect()
    }

    @Test
    fun `range request returns 206 with content-range and exact bytes`() {
        val conn = open("/t/1", range = "bytes=100-199")
        assertEquals(206, conn.responseCode)
        assertEquals("bytes 100-199/1000", conn.getHeaderField("Content-Range"))
        assertEquals(100, conn.getHeaderField("Content-Length").toInt())
        val body = conn.inputStream.readBytes()
        assertEquals(file.readBytes().copyOfRange(100, 200).toList(), body.toList())
        conn.disconnect()
    }

    @Test
    fun `open ended range runs to the end`() {
        val conn = open("/t/1", range = "bytes=900-")
        assertEquals(206, conn.responseCode)
        assertEquals(100, conn.inputStream.readBytes().size)
        conn.disconnect()
    }

    @Test
    fun `suffix range returns the tail`() {
        val conn = open("/t/1", range = "bytes=-50")
        assertEquals(206, conn.responseCode)
        assertEquals("bytes 950-999/1000", conn.getHeaderField("Content-Range"))
        conn.disconnect()
    }

    @Test
    fun `unknown path is 404`() {
        val conn = open("/t/missing")
        assertEquals(404, conn.responseCode)
        conn.disconnect()
    }

    @Test
    fun `path traversal is rejected`() {
        val conn = open("/../etc/passwd")
        assertTrue(conn.responseCode == 403 || conn.responseCode == 404)
        conn.disconnect()
    }

    @Test
    fun `unsupported method is 405`() {
        val conn = open("/t/1", method = "POST")
        assertEquals(405, conn.responseCode)
        conn.disconnect()
    }

    @Test
    fun `baseUrl includes the actual port`() {
        assertNotNull(server.port)
        assertEquals("http://10.0.0.9:${server.port}", server.baseUrl("10.0.0.9"))
    }

    @Test
    fun `range parser handles the documented forms`() {
        assertEquals(100L to 199L, LocalMediaServer.parseRange("bytes=100-199", 1000))
        assertEquals(900L to 999L, LocalMediaServer.parseRange("bytes=900-", 1000))
        assertEquals(950L to 999L, LocalMediaServer.parseRange("bytes=-50", 1000))
        assertNull(LocalMediaServer.parseRange("bytes=1000-", 1000)) // 起点越界
        assertNull(LocalMediaServer.parseRange("bytes=200-100", 1000)) // 反向
        assertNull(LocalMediaServer.parseRange("garbage", 1000))
        assertNull(LocalMediaServer.parseRange(null, 1000))
        assertNull(LocalMediaServer.parseRange("bytes=0-", 0))
        // 越界结束值被夹到文件末尾
        assertEquals(990L to 999L, LocalMediaServer.parseRange("bytes=990-99999", 1000))
    }

    @Test
    fun `content types cover the audio formats we scan`() {
        assertEquals("audio/mpeg", LocalMediaServer.contentTypeOf("a.mp3"))
        assertEquals("audio/flac", LocalMediaServer.contentTypeOf("a.FLAC"))
        assertEquals("audio/mp4", LocalMediaServer.contentTypeOf("a.m4a"))
        assertEquals("audio/ogg", LocalMediaServer.contentTypeOf("a.ogg"))
        assertEquals("application/octet-stream", LocalMediaServer.contentTypeOf("a.unknown"))
    }
    // ---- 断流不再冒充崩溃（2026-11-04 真机崩溃记录）----

    /**
     * 音箱拉到一半断开时，服务端**不能**把异常抛到线程池外。
     *
     * 原来没有这层兜底，`SocketException` 从 `pool.execute` 里的线程冒出去，
     * 被 `CrashGuard` 当成未捕获异常 → 记录并杀进程。
     * 用户看到的是"听本地音乐，切首歌 App 就闪退"。
     *
     * 这里用真 socket：发请求头 → 读一点 → `SO_LINGER=0` 强制 RST → 关连接，
     * 让服务端在写的时候真的撞上断开的连接。
     */
    @Test
    fun `client aborting mid-stream is counted as abort and never thrown`() {
        val big = Files.createTempFile("big", ".mp3").toFile()
        big.writeBytes(ByteArray(8 * 1024 * 1024))
        val srv = LocalMediaServer(MediaResolver { path -> if (path == "/t/big") big else null })
        srv.start()
        try {
            val raw = Socket("127.0.0.1", srv.port)
            raw.getOutputStream().write("GET /t/big HTTP/1.1\r\nHost: x\r\n\r\n".toByteArray())
            raw.getOutputStream().flush()
            // 只读一点点，确保服务端已经开始往回写
            raw.getInputStream().read(ByteArray(64))
            raw.setSoLinger(true, 0) // 直接 RST，不用等四次挥手
            raw.close()

            val st = waitFor(srv) { it.aborted > 0 }
            assertEquals(1, st.requests)
            assertEquals(0, st.completed)
            assertEquals(1, st.aborted)
            assertTrue("中断原因要写清楚", st.lastAbortNote.startsWith("客户端中途断开"))
        } finally {
            srv.stop()
            big.delete()
        }
    }

    /** 正常读完的请求算"完成"，不算"中断"——两个计数不能互相污染。 */
    @Test
    fun `completed request is counted as completed`() {
        val conn = open("/t/1")
        assertEquals(200, conn.responseCode)
        conn.inputStream.readBytes()
        conn.disconnect()

        val st = waitFor(server) { it.completed > 0 }
        assertEquals(1, st.requests)
        assertEquals(1, st.completed)
        assertEquals(0, st.aborted)
    }

    /** 轮询等计数落定：服务端是异步写 socket，直接断言会偶发读到旧值。 */
    private fun waitFor(
        srv: LocalMediaServer,
        predicate: (MediaServerStats) -> Boolean,
    ): MediaServerStats {
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline) {
            val st = srv.stats()
            if (predicate(st)) return st
            Thread.sleep(25)
        }
        return srv.stats()
    }

}
