package io.github.laozhudu.linkplay.protocol.media

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 内存文本资源（播放列表）。
 *
 * 为什么要测：设备来取 m3u 时只发一个极简 GET，如果响应头不对（缺 Content-Length、
 * Content-Type 写成 audio/mpeg），设备会当成音频流去解，结果是"命令回 OK 但没声音"。
 */
class LocalMediaServerTextTest {

    private fun withServer(block: (LocalMediaServer) -> Unit) {
        val server = LocalMediaServer(resolver = { null }, preferredPort = 0)
        server.start()
        try {
            block(server)
        } finally {
            server.stop()
        }
    }

    private fun httpGet(port: Int, path: String, method: String = "GET"): String {
        java.net.Socket("127.0.0.1", port).use { s ->
            s.soTimeout = 3000
            val out = s.getOutputStream()
            out.write("$method $path HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n".toByteArray())
            out.flush()
            return s.getInputStream().readBytes().toString(Charsets.UTF_8)
        }
    }

    @Test
    fun 登记的文本能按原样取回() {
        withServer { server ->
            val body = "#EXTM3U\n#EXTINF:-1,A\nhttp://nas/a.mp3\n"
            val path = server.publishText("/pl/t1.m3u", body, "audio/x-mpegurl")
            val resp = httpGet(server.port, path)
            assertTrue(resp, resp.startsWith("HTTP/1.1 200 OK"))
            assertTrue(resp, resp.contains("Content-Type: audio/x-mpegurl"))
            assertTrue(resp, resp.contains("Content-Length: ${body.toByteArray().size}"))
            assertTrue(resp, resp.endsWith(body))
        }
    }

    @Test
    fun HEAD_回同样的头但不回正文() {
        withServer { server ->
            val path = server.publishText("/pl/t2.m3u", "#EXTM3U\n")
            val resp = httpGet(server.port, path, method = "HEAD")
            assertTrue(resp, resp.contains("Content-Length: 8"))
            assertTrue("HEAD 不该带正文", !resp.endsWith("#EXTM3U\n"))
        }
    }

    @Test
    fun 撤销后回到_404() {
        withServer { server ->
            val path = server.publishText("/pl/t3.m3u", "#EXTM3U\n")
            server.unpublish(path)
            assertTrue(httpGet(server.port, path).startsWith("HTTP/1.1 404"))
        }
    }

    @Test
    fun 没有登记的路径仍然走_resolver_并_404() {
        withServer { server ->
            assertTrue(httpGet(server.port, "/nope.mp3").startsWith("HTTP/1.1 404"))
        }
    }

    @Test
    fun 路径带反斜杠穿越被拒() {
        withServer { server ->
            assertTrue(httpGet(server.port, "/../etc/passwd").startsWith("HTTP/1.1 403"))
        }
    }
}
