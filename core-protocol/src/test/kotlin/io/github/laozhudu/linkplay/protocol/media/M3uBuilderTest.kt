package io.github.laozhudu.linkplay.protocol.media

import io.github.laozhudu.linkplay.protocol.queue.QueueItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 整列表交给设备（m3u）。
 *
 * 这套东西是**真机验证过**的（2026-11-03）：
 * `setPlayerCmd:playlist:<m3u>:0` 之后 `plicount` 等于 m3u 条数、`next/prev` 正常、
 * 并且把 m3u 服务停掉之后设备仍能自己切歌。所以格式必须严格。
 */
class M3uBuilderTest {

    private fun item(url: String, title: String, sec: Long = 0) =
        QueueItem(url = url, title = title, durationSec = sec)

    @Test
    fun 第一行必须是_EXTM3U() {
        val text = M3uBuilder.build(listOf(item("http://nas/a.mp3", "A")))
        assertTrue(text.startsWith("#EXTM3U\n"))
    }

    @Test
    fun 每首是_EXTINF_加地址两行() {
        val text = M3uBuilder.build(
            listOf(item("http://nas/a.mp3", "A", 215), item("http://nas/b.mp3", "B", 0)),
        )
        val lines = text.trim().split("\n")
        assertEquals(listOf("#EXTM3U", "#EXTINF:215,A", "http://nas/a.mp3", "#EXTINF:-1,B", "http://nas/b.mp3"), lines)
    }

    @Test
    fun 时长未知写负一而不是零() {
        // 直播/未知时长写 0 会被部分播放器当成"立刻结束"
        assertTrue(M3uBuilder.build(listOf(item("http://nas/a.mp3", "A", 0))).contains("#EXTINF:-1,A"))
    }

    @Test
    fun 标题里的换行被抹平_否则会截断列表() {
        val text = M3uBuilder.build(listOf(item("http://nas/a.mp3", "第一行\n第二行")))
        assertEquals(3, text.trim().split("\n").size)
        assertTrue(text.contains("#EXTINF:-1,第一行 第二行"))
    }

    @Test
    fun 中文与空格标题原样保留() {
        val text = M3uBuilder.build(listOf(item("http://nas/五百年 桑田.mp3", "五百年 桑田")))
        assertTrue(text.contains("#EXTINF:-1,五百年 桑田"))
        assertTrue(text.contains("http://nas/五百年 桑田.mp3"))
    }

    @Test
    fun 旋转后目标曲排第一_设备只认_index0() {
        val list = listOf(item("http://nas/a.mp3", "A"), item("http://nas/b.mp3", "B"), item("http://nas/c.mp3", "C"))
        val rotated = M3uBuilder.rotateFrom(list, 1)
        assertEquals(listOf("B", "C", "A"), rotated.map { it.title })
        // 设备侧 plicurr=1 现在对应原列表第 2 首
        assertEquals("B", rotated.first().title)
    }

    @Test
    fun 旋转起点为零时保持原序() {
        val list = listOf(item("http://nas/a.mp3", "A"), item("http://nas/b.mp3", "B"))
        assertEquals(list.map { it.title }, M3uBuilder.rotateFrom(list, 0).map { it.title })
    }

    @Test
    fun 空列表只给出头部() {
        assertEquals("#EXTM3U\n", M3uBuilder.build(emptyList()))
    }

    @Test
    fun 只有_http_直链才算可交给设备() {
        assertTrue(M3uBuilder.allStreamable(listOf(item("http://a/1.mp3", "1"), item("https://a/2.mp3", "2"))))
        // 本机文件如果不经 LocalMediaServer 暴露就是 file:// 或裸路径，设备取不到
        assertFalse(M3uBuilder.allStreamable(listOf(item("/sdcard/a.mp3", "1"))))
        assertFalse(M3uBuilder.allStreamable(emptyList()))
    }
}
