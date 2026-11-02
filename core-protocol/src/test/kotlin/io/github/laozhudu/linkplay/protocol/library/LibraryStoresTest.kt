package io.github.laozhudu.linkplay.protocol.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryStoresTest {

    private fun track(id: Long, title: String = "T$id") = TrackRecord(
        playUri = "http://h/t/$id", title = title, songId = id,
    )

    @Test
    fun `favorites dedupe by song id for local tracks`() {
        val store = InMemoryFavoriteStore()
        assertTrue(store.add(track(1)))
        // 同一首歌换了 IP，URL 变了但 songId 一样
        assertFalse(store.add(track(1).copy(playUri = "http://10.0.0.2:9000/t/1")))
        assertEquals(1, store.all().size)
    }

    @Test
    fun `radio tracks dedupe by url because they have no song id`() {
        val store = InMemoryFavoriteStore()
        val radio = TrackRecord(playUri = "http://stream/1", title = "R", sourceType = TrackRecord.SOURCE_RADIO)
        assertTrue(store.add(radio))
        assertFalse(store.add(radio))
    }

    @Test
    fun `favorite remove and clear`() {
        val store = InMemoryFavoriteStore()
        store.add(track(1)); store.add(track(2))
        assertTrue(store.remove(track(1).dedupeKey))
        assertFalse(store.remove("nope"))
        assertEquals(1, store.all().size)
        store.clear()
        assertTrue(store.all().isEmpty())
    }

    @Test
    fun `recent moves a repeated track to the front instead of duplicating`() {
        val store = InMemoryRecentStore()
        store.record(track(1), 100)
        store.record(track(2), 200)
        store.record(track(1), 300) // 重播第一首

        val recent = store.recent()
        assertEquals(2, recent.size)
        assertEquals("T1", recent.first().title)
        assertEquals(300, recent.first().firstPlayedAt)
    }

    @Test
    fun `recent respects limit`() {
        val store = InMemoryRecentStore()
        (1..10).forEach { store.record(track(it.toLong()), it * 10L) }
        assertEquals(3, store.recent(limit = 3).size)
    }

    @Test
    fun `display name prefers artist then creator`() {
        val a = TrackRecord(playUri = "u", title = "Song", artist = "Artist")
        assertEquals("Song · Artist", a.displayName)
        val b = TrackRecord(playUri = "u", title = "Song", creator = "Creator")
        assertEquals("Song · Creator", b.displayName)
        val c = TrackRecord(playUri = "u", title = "Song", artist = "  ")
        assertEquals("Song", c.displayName)
    }
}
