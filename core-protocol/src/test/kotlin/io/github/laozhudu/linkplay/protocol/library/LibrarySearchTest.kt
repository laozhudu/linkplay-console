package io.github.laozhudu.linkplay.protocol.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibrarySearchTest {

    private fun t(title: String, artist: String? = null, album: String? = null, folder: String? = null, id: Long = 0) =
        TrackRecord(
            playUri = "http://h/t/$id",
            title = title,
            artist = artist,
            album = album,
            folderName = folder,
            songId = id,
        )

    private val tracks = listOf(
        t("夜曲", "周杰伦", "十一月的萧邦", id = 1),
        t("Nocturne Op.9", "Chopin", "Nocturnes", id = 2),
        t("青花瓷", "周杰伦", "我很忙", id = 3),
        t("夜空中最亮的星", "逃跑计划", "世界", id = 4),
    )

    @Test
    fun `title prefix wins`() {
        val hits = LibrarySearch.search(tracks, "夜曲")
        assertEquals("夜曲", hits.first().track.title)
        assertEquals(MatchKind.TITLE_PREFIX, hits.first().kind)
    }

    @Test
    fun `pinyin initials find chinese titles`() {
        // yq -> 夜曲
        val hits = LibrarySearch.search(tracks, "yq")
        assertTrue(hits.any { it.track.title == "夜曲" && it.kind == MatchKind.INITIALS_PREFIX })
    }

    @Test
    fun `artist initials find the whole artist catalog`() {
        // zjl -> 周杰伦
        val hits = LibrarySearch.search(tracks, "zjl")
        assertEquals(listOf("夜曲", "青花瓷").sorted(), hits.map { it.track.title }.sorted())
    }

    @Test
    fun `artist initials are reported as contains not prefix`() {
        val hits = LibrarySearch.search(tracks, "zjl")
        assertTrue(hits.all { it.kind == MatchKind.INITIALS_CONTAINS })
    }

    @Test
    fun `latin query matches artist and album case insensitively`() {
        assertTrue(LibrarySearch.search(tracks, "chopin").any { it.track.title == "Nocturne Op.9" })
        assertTrue(LibrarySearch.search(tracks, "nocturnes").isNotEmpty())
    }

    @Test
    fun `album and folder are searched too`() {
        assertTrue(LibrarySearch.search(tracks, "十一月的萧邦").any { it.track.title == "夜曲" })
        assertTrue(LibrarySearch.search(listOf(t("x", folder = "MySongs")), "mysongs").isNotEmpty())
    }

    @Test
    fun `blank query returns nothing`() {
        assertTrue(LibrarySearch.search(tracks, "   ").isEmpty())
        assertTrue(LibrarySearch.search(tracks, "").isEmpty())
    }

    @Test
    fun `results are ranked by match kind then title length`() {
        val hits = LibrarySearch.search(tracks, "夜")
        assertTrue(hits.first().track.title == "夜曲") // 前缀匹配且更短
        assertEquals(MatchKind.TITLE_PREFIX, hits.first().kind)
    }

    @Test
    fun `limit is honored`() {
        val many = (1..50).map { t("Song $it", id = it.toLong()) }
        assertEquals(10, LibrarySearch.search(many, "song", limit = 10).size)
    }
}
