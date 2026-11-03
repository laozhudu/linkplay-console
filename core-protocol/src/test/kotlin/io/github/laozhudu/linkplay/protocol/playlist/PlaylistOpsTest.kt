package io.github.laozhudu.linkplay.protocol.playlist

import io.github.laozhudu.linkplay.protocol.queue.QueueItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistOpsTest {

    private fun t(url: String, title: String = url) =
        QueueItem(url = url, title = title)

    @Test
    fun `name normalization trims and collapses whitespace`() {
        assertEquals("我的歌单", PlaylistOps.normalizeName("  我的歌单  "))
        assertEquals("a b", PlaylistOps.normalizeName("a   b"))
        assertNull("空名必须被拒绝，否则会建出一个没有名字的歌单", PlaylistOps.normalizeName("   "))
    }

    @Test
    fun `name is truncated to 60 chars`() {
        val long = "字".repeat(200)
        assertEquals(60, PlaylistOps.normalizeName(long)!!.length)
    }

    @Test
    fun `duplicate track is ignored silently`() {
        val a = t("http://nas/1.mp3")
        val before = listOf(a)
        val after = PlaylistOps.addTracks(before, listOf(a))
        assertSame("重复加同一首不能改变列表（同一个引用，避免无谓重组）", before, after)
    }

    @Test
    fun `same url with different title counts as one track`() {
        val before = listOf(t("http://nas/1.mp3", "A"))
        val after = PlaylistOps.addTracks(before, listOf(t("http://nas/1.mp3", "B")))
        assertEquals(1, after.size)
        assertEquals("A", after[0].title)
    }

    @Test
    fun `insert at position`() {
        val list = listOf(t("u1"), t("u2"), t("u3"))
        val out = PlaylistOps.addTracks(list, listOf(t("u9")), at = 1)
        assertEquals(listOf("u1", "u9", "u2", "u3"), out.map { it.url })
    }

    @Test
    fun `insert at out of range appends`() {
        val list = listOf(t("u1"), t("u2"))
        val out = PlaylistOps.addTracks(list, listOf(t("u9")), at = 99)
        assertEquals(listOf("u1", "u2", "u9"), out.map { it.url })
    }

    @Test
    fun `adding respects the track cap`() {
        val many = (0 until PlaylistOps.MAX_TRACKS).map { t("u$it") }
        val before = PlaylistOps.addTracks(emptyList(), many)
        assertEquals(PlaylistOps.MAX_TRACKS, before.size)
        val after = PlaylistOps.addTracks(before, listOf(t("overflow")))
        assertEquals("满员后不能再加", PlaylistOps.MAX_TRACKS, after.size)
    }

    @Test
    fun `remove only touches a valid index`() {
        val list = listOf(t("u1"), t("u2"))
        assertEquals(listOf("u2"), PlaylistOps.removeAt(list, 0).map { it.url })
        assertSame(list, PlaylistOps.removeAt(list, 5))
        assertSame(list, PlaylistOps.removeAt(list, -1))
    }

    @Test
    fun `move reorders and keeps size`() {
        val list = listOf(t("u1"), t("u2"), t("u3"))
        val out = PlaylistOps.move(list, 0, 2)
        assertEquals(listOf("u2", "u3", "u1"), out.map { it.url })
        assertEquals("移动不能丢歌", list.size, out.size)
    }

    @Test
    fun `move clamps the target and is a no-op when nothing changes`() {
        val list = listOf(t("u1"), t("u2"), t("u3"))
        assertEquals(listOf("u2", "u3", "u1"), PlaylistOps.move(list, 0, 99).map { it.url })
        assertSame(list, PlaylistOps.move(list, 1, 1))
        assertSame(list, PlaylistOps.move(list, -3, 1))
    }

    @Test
    fun `find playlist by name ignores case and extra spaces`() {
        val lists = listOf(
            PlaylistOps.Playlist(id = 1, name = "Road Trip"),
            PlaylistOps.Playlist(id = 2, name = "工作"),
        )
        assertEquals(0, PlaylistOps.indexOfName(lists, "  road   trip "))
        assertEquals(1, PlaylistOps.indexOfName(lists, "工作"))
        assertEquals(-1, PlaylistOps.indexOfName(lists, "不存在"))
        assertEquals(-1, PlaylistOps.indexOfName(lists, "   "))
    }

    @Test
    fun `key prefers url and falls back to title plus artist`() {
        assertEquals("u:http://nas/1.mp3", PlaylistOps.keyOf(t("http://nas/1.mp3")))
        val noUrl = QueueItem(url = "", title = "歌", artist = "人")
        assertEquals("t:歌|人", PlaylistOps.keyOf(noUrl))
    }

    @Test
    fun `adding a batch keeps the original order of existing tracks`() {
        val before = listOf(t("u1"), t("u2"))
        val out = PlaylistOps.addTracks(before, listOf(t("u3"), t("u4")))
        assertEquals(listOf("u1", "u2", "u3", "u4"), out.map { it.url })
        assertTrue(out.all { it.url.startsWith("u") })
    }
}
