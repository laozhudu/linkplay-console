package io.github.laozhudu.radiodiscovery.resolver

import io.github.laozhudu.radiodiscovery.FakeHttpClient
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamResolverTest {

    @Test
    fun `解析时不读 body —— 这是缺陷 2 的回归测试`() = runTest {
        // 直播流永不 EOF，读 body 会阻塞到 readTimeout（实测 8 秒）再抛异常。
        // 所以解析器必须传 readBody = false。
        val fake = FakeHttpClient {
            FakeHttpClient.headersOnly(200, mapOf("Content-Type" to listOf("audio/mpeg")))
        }
        StreamResolver(httpClient = fake).resolve("http://a/live.mp3")

        assertFalse("解析器绝不能读 body", fake.calls.single().readBody)
    }

    @Test
    fun `跟随 302 拿到最终地址`() = runTest {
        val fake = FakeHttpClient { url ->
            if (url.contains("short.link")) {
                FakeHttpClient.headersOnly(302, mapOf("Location" to listOf("http://real.host/live.mp3")))
            } else {
                FakeHttpClient.headersOnly(200, mapOf("Content-Type" to listOf("audio/mpeg")))
            }
        }
        val r = StreamResolver(httpClient = fake).resolve("http://short.link/x")

        assertEquals("http://real.host/live.mp3", r.finalUrl)
        assertTrue(r.isDirectStream)
        assertFalse(r.isHls)
        assertEquals(2, fake.calls.size)
    }

    @Test
    fun `相对 Location 按当前目录补全`() = runTest {
        val fake = FakeHttpClient { url ->
            if (url.endsWith("/a/b")) {
                FakeHttpClient.headersOnly(302, mapOf("Location" to listOf("c/live.mp3")))
            } else {
                FakeHttpClient.headersOnly(200, mapOf("Content-Type" to listOf("audio/mpeg")))
            }
        }
        val r = StreamResolver(httpClient = fake).resolve("http://h/a/b")

        assertEquals("http://h/a/c/live.mp3", r.finalUrl)
    }

    @Test
    fun `绝对路径 Location 不拼当前目录`() = runTest {
        val fake = FakeHttpClient { url ->
            if (url == "http://h/a/b") {
                FakeHttpClient.headersOnly(302, mapOf("Location" to listOf("/x/live.mp3")))
            } else {
                FakeHttpClient.headersOnly(200, mapOf("Content-Type" to listOf("audio/mpeg")))
            }
        }
        val r = StreamResolver(httpClient = fake).resolve("http://h/a/b")

        assertEquals("http://h/x/live.mp3", r.finalUrl)
    }

    @Test
    fun `协议相对 Location 继承 scheme`() = runTest {
        val fake = FakeHttpClient { url ->
            if (url == "https://h/a") {
                FakeHttpClient.headersOnly(302, mapOf("Location" to listOf("//cdn.h/live.mp3")))
            } else {
                FakeHttpClient.headersOnly(200, mapOf("Content-Type" to listOf("audio/mpeg")))
            }
        }
        val r = StreamResolver(httpClient = fake).resolve("https://h/a")

        assertEquals("https://cdn.h/live.mp3", r.finalUrl)
    }

    @Test
    fun `超过最大跳转次数就停`() = runTest {
        // 永远返回 302，解析器必须靠 maxRedirects 收手，不能死循环
        val fake = FakeHttpClient { i ->
            FakeHttpClient.headersOnly(302, mapOf("Location" to listOf("http://h/again.mp3")))
        }
        val r = StreamResolver(httpClient = fake, maxRedirects = 3).resolve("http://h/start")

        assertEquals(4, fake.calls.size) // 1 次原始 + 3 次跟随
        assertEquals("http://h/again.mp3", r.finalUrl)
    }

    @Test
    fun `m3u8 被识别成 HLS`() = runTest {
        val fake = FakeHttpClient {
            FakeHttpClient.headersOnly(200, mapOf("Content-Type" to listOf("application/vnd.apple.mpegurl")))
        }
        val r = StreamResolver(httpClient = fake).resolve("http://h/live.m3u8")

        assertTrue(r.isHls)
        assertTrue(r.isDirectStream)
    }

    @Test
    fun `content-type 里的 mpegurl 也认成 HLS`() = runTest {
        val fake = FakeHttpClient {
            FakeHttpClient.headersOnly(200, mapOf("Content-Type" to listOf("application/x-mpegURL")))
        }
        val r = StreamResolver(httpClient = fake).resolve("http://h/stream")

        assertTrue(r.isHls)
    }

    @Test
    fun `没有后缀也没有 content-type 时不算直连流`() = runTest {
        val fake = FakeHttpClient { FakeHttpClient.headersOnly(200, emptyMap()) }
        val r = StreamResolver(httpClient = fake).resolve("http://h/streams")

        assertFalse(r.isDirectStream)
    }

    @Test
    fun `content-type 大小写不敏感`() = runTest {
        val fake = FakeHttpClient {
            FakeHttpClient.headersOnly(200, mapOf("content-type" to listOf("AUDIO/MPEG")))
        }
        val r = StreamResolver(httpClient = fake).resolve("http://h/live")

        assertEquals("AUDIO/MPEG", r.mimeType)
    }
}
