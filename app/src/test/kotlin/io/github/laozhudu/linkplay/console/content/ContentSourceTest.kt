package io.github.laozhudu.linkplay.console.content

import io.github.laozhudu.linkplay.console.library.LocalTrack
import io.github.laozhudu.radiodiscovery.model.RadioStation
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 两个源的「统一契约」。
 *
 * 这些用例不测各源的业务（那在各自的测试里），只测**接口约定**：
 * 空查询、无权限、取不到时分别给什么，界面据此画什么。
 */
class ContentSourceTest {

    private fun track(id: Long, title: String, artist: String? = null) = LocalTrack(
        id = id, title = title, artist = artist, album = null,
        durationSec = 200, sizeBytes = 1024, mimeType = "audio/mpeg", path = "/music/$id.mp3",
    )

    private fun local(
        tracks: List<LocalTrack> = listOf(track(1, "晴天", "周杰伦"), track(2, "稻香")),
        permission: Boolean = true,
    ) = LocalContentSource(queryAll = { tracks }, hasPermission = { permission })

    // ---------- 本地 ----------

    @Test
    fun `local without permission is not ready and searches nothing`() {
        val src = local(permission = false)
        assertFalse(src.readiness().usable)
        assertTrue(src.readiness() is Readiness.Missing)
        assertTrue(runBlocking { src.search("晴天") }.isEmpty())
    }

    @Test
    fun `local search matches title or artist`() {
        val src = local()
        assertEquals(1, runBlocking { src.search("晴天") }.size)
        // 按歌手也能搜到
        assertEquals(1, runBlocking { src.search("周杰伦") }.size)
    }

    @Test
    fun `blank query returns nothing`() {
        // 空查询不该把整个库倒出来 —— 那会让"打开就搜"变慢且没意义
        assertTrue(runBlocking { local().search("  ") }.isEmpty())
    }

    @Test
    fun `local resolve asks the orchestrator to serve the file`() {
        val src = local()
        val item = ContentItem(SourceId.Local, "1", "晴天")
        val r = runBlocking { src.resolve(item) }
        // **不能**直接给 http 地址：转流服务那时还没起，端口是编的
        assertTrue(r is Resolved.NeedsLocalServer)
        assertEquals("/music/1.mp3", (r as Resolved.NeedsLocalServer).path)
    }

    @Test
    fun `local resolve fails politely when the library was rebuilt`() {
        val src = local()
        val r = runBlocking { src.resolve(ContentItem(SourceId.Local, "999", "老记录")) }
        assertTrue(r is Resolved.Failed)
    }

    // ---------- 电台 ----------

    private fun station(name: String, url: String, resolved: String? = null, id: String = "uuid-1") =
        RadioStation(id = id, name = name, url = url, resolvedUrl = resolved)

    private fun radio(
        results: List<RadioStation> = listOf(station("中国之声", "http://a/1.mp3")),
    ) = RadioContentSource(
        searchStations = { _, _ -> results },
        resolveStation = { it },
    )

    @Test
    fun `radio is always ready`() {
        // 目录是公开服务，不需要任何配置
        assertTrue(radio().readiness().usable)
    }

    @Test
    fun `radio uses resolved url when available`() {
        val s = station("中国之声", "http://old/1.mp3", resolved = "http://new/1.mp3")
        val src = RadioContentSource(searchStations = { _, _ -> listOf(s) }, resolveStation = { it })
        val item = runBlocking { src.search("中国") }.first()
        val r = runBlocking { src.resolve(item) }
        assertEquals(Resolved.Ok("http://new/1.mp3"), r)
    }

    @Test
    fun `radio falls back to plain url`() {
        val src = radio()
        val item = runBlocking { src.search("中国") }.first()
        assertEquals(Resolved.Ok("http://a/1.mp3"), runBlocking { src.resolve(item) })
    }

    @Test
    fun `radio search failure does not throw`() {
        // 一个源连不上不该让"内容"整屏报错
        val src = RadioContentSource(
            searchStations = { _, _ -> error("网络断了") },
            resolveStation = { it },
        )
        assertTrue(runBlocking { src.search("中国") }.isEmpty())
    }

    // ---------- 两条契约 ----------

    @Test
    fun `every source reports a non blank diagnose line`() {
        assertTrue(local().diagnoseLine().isNotBlank())
        assertTrue(radio().diagnoseLine().isNotBlank())
    }

    @Test
    fun `items carry their own source id`() {
        // 界面上「这条来自哪个源」全靠它，错了会把电台标的歌标成 NAS
        assertEquals(SourceId.Local, runBlocking { local().search("晴天") }.first().source)
        assertEquals(SourceId.Radio, runBlocking { radio().search("中国") }.first().source)
    }
}
