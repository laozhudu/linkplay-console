package io.github.laozhudu.linkplay.protocol.mediasource

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * 对**真实** Subsonic 服务端的联调用例（默认跳过，CI 上没有服务器）。
 *
 * 目标服务是 `.124` 上的 tt-music（`penhaijun/lx-music-server`，Navidrome 分支，
 * `"subsonic.enable": true`, path `/rest`）。
 *
 * ```
 * LIVE_SUBSONIC=192.168.99.124:9527 LIVE_SUBSONIC_USER=tt01 LIVE_SUBSONIC_PASS=123456 \
 *   ./gradlew :core-protocol:test --tests "*SubsonicLiveIT*"
 * ```
 *
 * 只做**只读**操作（ping / search3 / 取直链 URL），不写任何数据。
 */
class SubsonicLiveIT {

    private fun cfg(): SubsonicConfig = SubsonicConfig(
        baseUrl = System.getenv("LIVE_SUBSONIC").orEmpty(),
        user = System.getenv("LIVE_SUBSONIC_USER").orEmpty(),
        password = System.getenv("LIVE_SUBSONIC_PASS").orEmpty(),
    )

    private fun httpGet(url: String): String {
        val conn = (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 6_000
            readTimeout = 12_000
            instanceFollowRedirects = true
        }
        return try {
            val code = conn.responseCode
            val s = if (code in 200..299) conn.inputStream else conn.errorStream
            s?.bufferedReader()?.use { it.readText() }.orEmpty()
        } finally {
            conn.disconnect()
        }
    }

    @Test
    fun `ping a real subsonic server`() {
        val c = cfg()
        assumeTrue("未提供 LIVE_SUBSONIC，跳过", c.baseUrl.isNotBlank() && c.user.isNotBlank())
        val client = SubsonicClient(c, ::httpGet)
        assertTrue("ping 应该成功：${client.restBase()}", client.ping())
    }

    @Test
    fun `search returns a parseable result even for an empty library`() = runBlocking {
        val c = cfg()
        assumeTrue("未提供 LIVE_SUBSONIC，跳过", c.baseUrl.isNotBlank() && c.user.isNotBlank())
        val client = SubsonicClient(c, ::httpGet)
        // 空库也要能正常返回 0 条（不能抛"不是 Subsonic 响应"）
        val list = client.search("周杰伦", limit = 5)
        assertTrue("结果数不该为负：${list.size}", list.size >= 0)
        if (list.isNotEmpty()) {
            val url = client.resolve(list.first())
            assertTrue("直链要能取到：$url", url.contains("/rest/stream.view"))
            assertTrue("直链必须带凭据：$url", url.contains("t="))
        }
    }
}
