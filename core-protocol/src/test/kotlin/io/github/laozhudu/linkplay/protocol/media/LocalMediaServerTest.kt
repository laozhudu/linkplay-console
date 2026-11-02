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
}
