package io.github.laozhudu.linkplay.protocol.playqueue

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayListContextTest {

    @Test
    fun `produces the shape the real device accepted`() {
        val xml = PlayListContext.build("Douban", PlayListInfo(searchUrl = "http://www.douban.com"))
        assertTrue(xml.startsWith("<?xml version=\"1.0\"?><PlayList>"))
        assertTrue(xml.endsWith("</PlayList>"))
        assertTrue(xml.contains("<ListName>Douban</ListName>"))
        assertTrue(xml.contains("<SearchUrl>http://www.douban.com</SearchUrl>"))
        // ListInfo 是必需的：实机上「只有 ListName」被拒（404 ParseXmltoPlayList）
        assertTrue(xml.contains("<ListInfo>"))
        assertTrue(xml.contains("</ListInfo>"))
        assertTrue(xml.contains("<Tracks></Tracks>"))
    }

    @Test
    fun `all thirteen list info fields are emitted in device order`() {
        val xml = PlayListContext.build("q")
        val order = listOf(
            "SearchUrl", "MarkSearch", "TrackNumber", "Quality", "UpdateTime", "LastPlayIndex",
            "RealIndex", "SwitchPageMode", "CurrentPage", "TotalPages", "searching", "PressType", "Volume",
        )
        var last = -1
        order.forEach {
            val at = xml.indexOf("<$it>")
            assertTrue("missing field $it", at > last)
            last = at
        }
    }

    @Test
    fun `values are escaped`() {
        val xml = PlayListContext.build("a<b&c", PlayListInfo(searchUrl = "x&y"))
        assertTrue(xml.contains("<ListName>a&lt;b&amp;c</ListName>"))
        assertTrue(xml.contains("<SearchUrl>x&amp;y</SearchUrl>"))
        assertTrue(!xml.contains("a<b"))
    }

    @Test
    fun `track payload is pass through for future firmware`() {
        val xml = PlayListContext.build("q", tracksXml = "<Track/>")
        assertTrue(xml.contains("<Tracks><Track/></Tracks>"))
    }
}
