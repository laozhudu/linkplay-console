package io.github.laozhudu.linkplay.radio

import io.github.laozhudu.radiodiscovery.model.RadioStation as DiscoveryStation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RadioStationMappingTest {

    private fun station(
        url: String,
        resolved: String? = null,
        name: String = "某台",
    ) = DiscoveryStation(id = "id", name = name, url = url, resolvedUrl = resolved)

    // ── streamKindOf ────────────────────────────────────────────────────

    @Test
    fun `mp3 与 aac 算直连流`() {
        assertEquals(StreamKind.DIRECT, streamKindOf("http://a/1.mp3"))
        assertEquals(StreamKind.DIRECT, streamKindOf("http://a/1.aac"))
        assertEquals(StreamKind.DIRECT, streamKindOf("https://a/1.ogg"))
        assertEquals(StreamKind.DIRECT, streamKindOf("http://a/1.flac"))
    }

    @Test
    fun `m3u8 算 HLS`() {
        assertEquals(StreamKind.HLS, streamKindOf("http://a/live.m3u8"))
        assertEquals(StreamKind.HLS, streamKindOf("https://a/live.m3u8?token=1"))
    }

    @Test
    fun `后缀判断不受大小写和查询串影响`() {
        assertEquals(StreamKind.DIRECT, streamKindOf("http://a/1.MP3"))
        assertEquals(StreamKind.HLS, streamKindOf("http://a/LIVE.M3U8?x=1#y"))
    }

    @Test
    fun `播放列表文件算未知 —— 还要再解析一层`() {
        assertEquals(StreamKind.UNKNOWN, streamKindOf("http://a/list.pls"))
        assertEquals(StreamKind.UNKNOWN, streamKindOf("http://a/list.m3u"))
    }

    @Test
    fun `无后缀算未知`() {
        // 实测 stream.zeno.fm/xxxx 这类无后缀地址 6-6 全都能播，
        // 但只看地址判断不出格式，所以标 UNKNOWN 而不是打包票
        assertEquals(StreamKind.UNKNOWN, streamKindOf("https://stream.zeno.fm/mss1yzbbbm8uv"))
    }

    // ── toFavourite ─────────────────────────────────────────────────────

    @Test
    fun `转收藏时优先用解析后的地址`() {
        val s = station("http://a/redirect", resolved = "http://a/real.mp3")
        assertEquals("http://a/real.mp3", s.toFavourite().url)
    }

    @Test
    fun `没有解析地址就用原始地址`() {
        assertEquals("http://a/x.mp3", station("http://a/x.mp3").toFavourite().url)
    }

    @Test
    fun `空白的解析地址不算数`() {
        val s = station("http://a/x.mp3", resolved = "   ")
        assertEquals("http://a/x.mp3", s.toFavourite().url)
    }

    @Test
    fun `名字为空白时给个兜底名`() {
        assertEquals("网络电台", station("http://a/x.mp3", name = "   ").toFavourite().name)
    }

    @Test
    fun `名字两端空白会被去掉`() {
        assertEquals("中国之声", station("http://a/x.mp3", name = "  中国之声 ").toFavourite().name)
    }

    @Test
    fun `设备归属会带过去`() {
        assertEquals("doss1", station("http://a/x.mp3").toFavourite("doss1").deviceName)
    }

    @Test
    fun `转出来的收藏默认是常驻且未失效`() {
        val fav = station("http://a/x.mp3").toFavourite()
        assertTrue(fav.kept)
        assertEquals(false, fav.invalid)
        assertEquals(false, fav.playing)
    }

    // ── playbackHint ────────────────────────────────────────────────────

    @Test
    fun `HLS 给提示`() {
        assertEquals("HLS 流，部分音箱播不了", station("http://a/live.m3u8").playbackHint())
    }

    @Test
    fun `直连流不给提示`() {
        assertNull(station("http://a/1.mp3").playbackHint())
    }

    @Test
    fun `提示看的是解析后的地址`() {
        // 原始地址看不出格式，解析出来是 m3u8 就该提示
        val s = station("http://a/stream", resolved = "http://a/live.m3u8")
        assertEquals("HLS 流，部分音箱播不了", s.playbackHint())
    }

    @Test
    fun `不把没探测过的格式说成不能播`() {
        // 我们没做网络探测，只按格式给概率性提示，不能写成"不可播放"
        val hint = station("http://a/stream").playbackHint()
        assertNull("未知格式不该下断言", hint)
    }
}
