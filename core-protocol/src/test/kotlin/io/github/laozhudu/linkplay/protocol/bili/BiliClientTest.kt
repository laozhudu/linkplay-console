package io.github.laozhudu.linkplay.protocol.bili

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * B 站取流的两条**最容易写错、又不需要网络就能验**的规则。
 *
 * 锁的是：① 用户粘什么都能认出 BV 号；② 音轨永远选带宽最大那条。
 *
 * 第 ② 条尤其重要：选错了拿到 43kbps 那条，听感会"像收音机信号不好"，
 * 而这种问题在真机上只会被当成"网络差"，极难归因。
 */
class BiliClientTest {

    private fun client(json: String) = BiliClient(fetchJson = { json })

    @Test
    fun `三种写法都能认出 BV 号`() {
        val c = client("{}")
        assertEquals("BV1GJ411x7h7", c.parseBvid("BV1GJ411x7h7"))
        assertEquals(
            "BV1GJ411x7h7",
            c.parseBvid("https://www.bilibili.com/video/BV1GJ411x7h7?p=1&t=10"),
        )
        assertEquals("BV1GJ411x7h7", c.parseBvid("   BV1GJ411x7h7   "))
    }

    @Test
    fun `认不出就返回 null 而不是瞎猜`() {
        val c = client("{}")
        assertNull(c.parseBvid(""))
        assertNull(c.parseBvid("周杰伦"))
        assertNull(c.parseBvid("https://b23.tv/abcd"))   // 短链要展开，这里不猜
    }

    @Test
    fun `音轨选带宽最大那条`() = runBlocking {
        val view = """{"data":{"cid":137649199,"title":"测试","duration":213}}"""
        val playurl = """
            {"data":{"dash":{"audio":[
              {"id":30216,"bandwidth":43000,"baseUrl":"https://a/low.m4s"},
              {"id":30280,"bandwidth":203000,"baseUrl":"https://a/high.m4s"},
              {"id":30232,"bandwidth":102000,"baseUrl":"https://a/mid.m4s"}
            ]}}}
        """.trimIndent()

        var call = 0
        val c = BiliClient(fetchJson = { if (call++ == 0) view else playurl })

        val s = c.fetchStream("BV1GJ411x7h7")
        assertEquals(137649199L, s.cid)
        assertEquals(213L, s.durationSec)
        assertEquals("https://a/high.m4s", s.audioUrl)
        assertEquals(30280, s.audioQualityId)
    }

    @Test
    fun `没有 dash 时给出能看懂的错误`() = runBlocking {
        val view = """{"data":{"cid":1,"title":"x","duration":10}}"""
        val playurl = """{"data":{}}"""
        var call = 0
        val c = BiliClient(fetchJson = { if (call++ == 0) view else playurl })
        val err = runCatching { c.fetchStream("BV1GJ411x7h7") }.exceptionOrNull()
        assertEquals("没有 dash（可能该视频无音轨）", err?.message)
    }
}
