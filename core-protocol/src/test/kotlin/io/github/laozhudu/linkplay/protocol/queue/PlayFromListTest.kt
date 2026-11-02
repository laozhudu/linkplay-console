package io.github.laozhudu.linkplay.protocol.queue

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 「点一首 → 播整个列表」的起播位置。
 *
 * 用户反馈「媒体库里点一首歌应该直接播放，并推送所在整个列表」——
 * 这条逻辑最容易出错的地方是**下标算错**（点了第 3 首却从第 1 首开始），
 * 而那在真机上要听一会儿才发现，所以这里钉死。
 */
class PlayFromListTest {

    private val tracks = listOf("a.mp3", "b.mp3", "c.mp3", "d.mp3")

    private fun idx(key: String, list: List<String> = tracks) =
        PlayFromList.startIndex(list, key) { it }

    @Test
    fun 点中间那首就从中间起播() {
        assertEquals(2, idx("c.mp3"))
    }

    @Test
    fun 点第一首从零起播() {
        assertEquals(0, idx("a.mp3"))
    }

    @Test
    fun 点最后一首从最后一个起播() {
        assertEquals(3, idx("d.mp3"))
    }

    @Test
    fun 不在列表里时退回从头播而不是不动() {
        assertEquals(0, idx("zzz.mp3"))
    }

    @Test
    fun 空列表不崩() {
        assertEquals(0, idx("a.mp3", emptyList()))
    }

    @Test
    fun 有重复项时取第一个出现位置() {
        assertEquals(1, idx("b.mp3", listOf("a.mp3", "b.mp3", "b.mp3")))
    }

    @Test
    fun 起播下标不会把前面的曲目丢掉_列表保持全量() {
        val kept = PlayFromList.reorderFrom(tracks, 2)
        assertEquals(tracks, kept)
        assertEquals(4, kept.size)
    }
}
