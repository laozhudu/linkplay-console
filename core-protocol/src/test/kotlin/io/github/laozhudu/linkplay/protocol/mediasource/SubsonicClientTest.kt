package io.github.laozhudu.linkplay.protocol.mediasource

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Subsonic 音源（`.124` 上的 tt-music）。
 *
 * 断言用的 JSON 形状取自真机（`/rest/ping.view` 与 OpenSubsonic 的 `search3.view`）：
 * ```
 * {"subsonic-response":{"status":"ok","version":"1.16.1","type":"lxserver","openSubsonic":true}}
 * ```
 */
class SubsonicClientTest {

    private var lastUrl: String = ""

    private fun client(
        baseUrl: String = "192.168.99.124:9527",
        user: String = "tt01",
        pass: String = "123456",
        body: String = """{"subsonic-response":{"status":"ok","version":"1.16.1"}}""",
    ) = SubsonicClient(
        SubsonicConfig(baseUrl = baseUrl, user = user, password = pass),
    ) { url -> lastUrl = url; body }

    @Test
    fun `base url without scheme is normalised`() {
        val c = client()
        assertEquals("http://192.168.99.124:9527/rest", c.restBase())
    }

    @Test
    fun `trailing slash and explicit rest path are handled`() {
        assertEquals("http://a/rest", client("http://a/").restBase())
        assertEquals("https://a/rest", client("https://a").restBase())
    }

    @Test
    fun `auth uses token not plaintext password`() {
        val c = client()
        val auth = c.auth("ab12")
        assertTrue("必须带 t=（token）：$auth", auth.contains("t="))
        assertTrue("必须带 s=（salt）：$auth", auth.contains("s=ab12"))
        assertFalse("不能把明文密码放进 URL：$auth", auth.contains("123456"))
        assertFalse(auth.contains("p="))
        assertEquals(SubsonicClient.md5("123456" + "ab12"), SubsonicClient.md5("123456ab12"))
    }

    @Test
    fun `ping returns true on ok status`() {
        assertTrue(client().ping())
    }

    @Test
    fun `ping returns false when server reports failed`() {
        val c = client(body = """{"subsonic-response":{"status":"failed","error":{"code":40,"message":"Wrong username or password"}}}""")
        assertFalse(c.ping())
    }

    @Test
    fun `search builds search3 with songCount and no album results`() = runBlocking {
        val body = """{"subsonic-response":{"status":"ok","searchResult3":{
            "song":[
              {"id":"101","title":"晴天","artist":"周杰伦","album":"叶惠美","duration":269,"suffix":"mp3","size":8000000,"coverArt":"al-9"},
              {"id":"102","title":"七里香","artist":"周杰伦","duration":299}
            ]}}}"""
        val list = client(body = body).search("周杰伦", limit = 20)
        assertTrue(lastUrl.contains("/rest/search3.view"))
        assertTrue("要带 songCount", lastUrl.contains("songCount=20"))
        assertTrue("不取专辑，减少无关结果", lastUrl.contains("albumCount=0"))
        assertEquals(2, list.size)
        assertEquals("晴天", list[0].title)
        assertEquals(269L, list[0].durationSec)
        assertEquals("al-9", list[0].coverId)
        assertEquals(SubsonicClient.ID, list[0].providerId)
        // 缺字段不能崩
        assertEquals(299L, list[1].durationSec)
        assertNull(list[1].coverId)
    }

    @Test
    fun `search throws a readable error on failed status`() {
        val c = client(body = """{"subsonic-response":{"status":"failed","error":{"code":40,"message":"Wrong username or password"}}}""")
        val e = runCatching { runBlocking { c.search("x") } }.exceptionOrNull()
        assertTrue(e is MediaSourceException)
        assertTrue("要把服务端的原话带出来：${e?.message}", e!!.message!!.contains("Wrong username or password"))
    }

    @Test
    fun `empty query short-circuits without a request`() = runBlocking {
        lastUrl = ""
        assertEquals(0, client().search("   ").size)
        assertEquals("空查询不该打服务器", "", lastUrl)
    }

    @Test
    fun `stream url carries credentials so the speaker can fetch it bare`() = runBlocking {
        val c = client()
        val url = c.resolve(MediaTrack(id = "101", title = "晴天", providerId = SubsonicClient.ID))
        assertTrue(url.startsWith("http://192.168.99.124:9527/rest/stream.view?"))
        assertTrue("凭据必须在 query 里（音箱裸 GET 才能拉）", url.contains("&id=101"))
        assertTrue(url.contains("t="))
    }

    @Test
    fun `cover url uses coverArt id when present`() {
        val c = client()
        val withCover = c.coverUrl(MediaTrack(id = "1", title = "t", providerId = "subsonic", coverId = "al-9"))
        assertTrue(withCover!!.contains("getCoverArt.view"))
        assertTrue(withCover.contains("id=al-9"))
        assertTrue(withCover.contains("size=300"))
    }

    @Test
    fun `config round-trips through json`() {
        val cfg = SubsonicConfig(name = "家里曲库", baseUrl = "192.168.99.124:9527", user = "tt01", password = "p w", enabled = false)
        val back = SubsonicConfig.decode(cfg.encode())
        assertEquals(cfg, back)
        assertNull(SubsonicConfig.decode(null))
        assertNull(SubsonicConfig.decode("not json"))
    }

    @Test
    fun `unconfigured provider is not offered`() {
        assertFalse(SubsonicClient(SubsonicConfig(baseUrl = "", user = "")) { "" }.configured)
        assertTrue(SubsonicClient(SubsonicConfig(baseUrl = "a", user = "u")) { "" }.configured)
    }

    @Test
    fun `malformed response gives an actionable message instead of a null`() {
        // 代理错误页 / 地址填错时会拿到 HTML 而不是 JSON：必须说清"检查地址"，
        // 而不是显示 "返回 null：未知原因"（写这个用例时实现正好犯了这个错）
        val e = runCatching { runBlocking { client(body = "<html>502 Bad Gateway</html>").search("x") } }
            .exceptionOrNull()
        assertTrue(e is MediaSourceException)
        val msg = e!!.message!!
        assertTrue("要提示检查地址：$msg", msg.contains("检查地址"))
        assertFalse("不能出现 null 这种内部词：$msg", msg.contains("null"))
    }
}
