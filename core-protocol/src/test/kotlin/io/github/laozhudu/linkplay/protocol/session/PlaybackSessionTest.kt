package io.github.laozhudu.linkplay.protocol.session

import io.github.laozhudu.linkplay.protocol.queue.QueueItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 播放快照的编解码。
 *
 * 重点在 [PlaybackSession.deviceOffset]：设备只认 `playlist` 命令的 index=0，
 * 所以下发前列表被旋转过；设备报的 `plicurr` 要靠这个偏移映射回本地列表。
 * 偏移丢了，重开 App 的列表高亮就会错位一条。
 */
class PlaybackSessionTest {

    private fun items(n: Int) = (1..n).map { QueueItem(url = "http://nas/$it.mp3", title = "曲目$it") }

    @Test
    fun 编解码往返保留全部字段() {
        val s = PlaybackSession("uuid-1", "10.0.0.5", items(3), index = 2, loopMode = 1, deviceOffset = 1)
        val back = PlaybackSession.decode(s.encode())!!
        assertEquals(s.deviceUuid, back.deviceUuid)
        assertEquals(s.deviceIp, back.deviceIp)
        assertEquals(s.index, back.index)
        assertEquals(s.loopMode, back.loopMode)
        assertEquals(s.deviceOffset, back.deviceOffset)
        assertEquals(s.items.map { it.title }, back.items.map { it.title })
    }

    @Test
    fun 老快照没有_offset_字段时默认为零() {
        // 兼容升级前存下的数据：不能因为缺字段就整条读不出来
        val legacy = """{"uuid":"u","ip":"1.2.3.4","index":1,"loop":2,
            "items":[{"url":"http://a/1.mp3","title":"A","dur":0,"mime":"audio/mpeg"}]}"""
        val s = PlaybackSession.decode(legacy)!!
        assertEquals(0, s.deviceOffset)
        assertEquals(1, s.items.size)
    }

    @Test
    fun 索引越界被夹回范围内() {
        val s = PlaybackSession("u", "1.2.3.4", items(2), index = 9, loopMode = 0)
        assertEquals(1, PlaybackSession.decode(s.encode())!!.index)
    }

    @Test
    fun 空串与坏数据返回_null() {
        assertNull(PlaybackSession.decode(null))
        assertNull(PlaybackSession.decode(""))
        assertNull(PlaybackSession.decode("{not json"))
        assertNull(PlaybackSession.decode("""{"uuid":"u","ip":"1.2.3.4","items":[]}"""))
    }

    @Test
    fun remaining_从当前首开始() {
        val s = PlaybackSession("u", "1.2.3.4", items(4), index = 2, loopMode = 0)
        assertEquals(listOf("曲目3", "曲目4"), s.remaining.map { it.title })
    }
}
