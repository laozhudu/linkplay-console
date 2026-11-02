package io.github.laozhudu.linkplay.protocol.playqueue

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DidlLiteBuilderTest {

    private fun track(n: Int) = PlaylistTrack(
        url = "http://192.168.1.5:8123/t/$n",
        title = "Track $n",
        artist = "Artist & Co",
        album = "Album",
        durationSec = 201,
        sizeBytes = 5_000_000,
        mimeType = "audio/mpeg",
    )

    @Test
    fun `single builds one item with required fields`() {
        val didl = DidlLiteBuilder.single(track(1))
        assertTrue(didl.startsWith("<DIDL-Lite"))
        assertTrue(didl.endsWith("</DIDL-Lite>"))
        assertTrue(didl.contains("<dc:title>Track 1</dc:title>"))
        assertTrue(didl.contains("<upnp:class>object.item.audioItem.musicTrack</upnp:class>"))
        assertTrue(didl.contains("http-get:*:audio/mpeg:*"))
        assertEquals(1, Regex("<item ").findAll(didl).count())
    }

    @Test
    fun `playlist emits sequential ids starting at one`() {
        val didl = DidlLiteBuilder.build(listOf(track(1), track(2), track(3)))
        assertEquals(3, Regex("<item ").findAll(didl).count())
        assertTrue(didl.contains("<item id=\"1\" parentID=\"-1\" restricted=\"1\">"))
        assertTrue(didl.contains("<item id=\"2\" parentID=\"-1\" restricted=\"1\">"))
        assertTrue(didl.contains("<item id=\"3\" parentID=\"-1\" restricted=\"1\">"))
    }

    @Test
    fun `special characters are escaped in titles and urls`() {
        val didl = DidlLiteBuilder.build(
            listOf(track(1).copy(title = "A & B <live>", url = "http://h/a.mp3?x=1&y=2")),
        )
        assertTrue(didl.contains("<dc:title>A &amp; B &lt;live&gt;</dc:title>"))
        assertTrue(didl.contains("x=1&amp;y=2"))
        // 不能出现裸 & （会破坏 XML）
        assertTrue(!Regex("&(?!amp;|lt;|gt;|quot;|apos;)").containsMatchIn(didl))
    }

    @Test
    fun `size and duration attributes appear only when known`() {
        val withMeta = DidlLiteBuilder.single(track(1))
        assertTrue(withMeta.contains("size=\"5000000\""))
        assertTrue(withMeta.contains("duration=\"0:03:21.000\""))

        val bare = DidlLiteBuilder.single(track(1).copy(sizeBytes = 0, durationSec = 0))
        assertTrue(!bare.contains("size="))
        assertTrue(!bare.contains("duration="))
    }

    @Test
    fun `duration formatting matches didl expectations`() {
        assertEquals("0:00:00.000", DidlLiteBuilder.formatDuration(0))
        assertEquals("0:03:21.000", DidlLiteBuilder.formatDuration(201))
        assertEquals("1:00:05.000", DidlLiteBuilder.formatDuration(3605))
    }

    @Test
    fun `queue context formats are switchable and documented`() {
        val didl = "<DIDL-Lite/>"
        assertEquals(didl, QueueContextFormat.DIDL_ONLY.render("q", didl))
        assertEquals("q\n<DIDL-Lite/>", QueueContextFormat.NAME_THEN_DIDL.render("q", didl))
        assertEquals("q", QueueContextFormat.NAME_ONLY.render("q", didl))
    }
}
