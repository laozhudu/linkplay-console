package io.github.laozhudu.linkplay.protocol.upnp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentDirectoryTest {

    // 逐字取自 fnOS(MiniDLNA) 实机返回的 Result 内容
    private val realResult = """
        <DIDL-Lite xmlns:dc="http://purl.org/dc/elements/1.1/"
                   xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/"
                   xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/">
        <container id="64" parentID="0" restricted="1" searchable="0">
          <dc:title>Browse Folders</dc:title><upnp:class>object.container.storageFolder</upnp:class>
        </container>
        <container id="1" parentID="0" restricted="1" searchable="1">
          <dc:title>Music</dc:title><upnp:class>object.container.storageFolder</upnp:class>
        </container>
        <item id="1110" parentID="1${'$'}4" restricted="1">
          <dc:title>00 凯叔给家长的一封信</dc:title>
          <upnp:class>object.item.audioItem.musicTrack</upnp:class>
          <res protocolInfo="http-get:*:audio/mpeg:*" size="6888383" duration="0:02:51.058"
               bitrate="320000" sampleFrequency="44100" nrAudioChannels="2">
            http://192.168.99.223:8200/MediaItems/1386.mp3
          </res>
        </item>
        <item id="1111" parentID="1${'$'}4" restricted="1">
          <dc:title>000 凯叔水浒传·魔君降世</dc:title>
          <upnp:artist>凯叔</upnp:artist>
          <res protocolInfo="http-get:*:audio/mpeg:*" size="13823610" duration="0:14:23.972">
            http://192.168.99.223:8200/MediaItems/515.mp3
          </res>
        </item>
        </DIDL-Lite>
    """.trimIndent()

    @Test
    fun `containers and items are told apart`() {
        val entries = DidlParser.parseEntries(realResult)
        assertEquals(4, entries.size)
        assertEquals(2, entries.count { it.isContainer })
        assertEquals(2, entries.count { !it.isContainer })
        assertEquals("Browse Folders", entries[0].title)
        assertEquals("Music", entries[1].title)
    }

    @Test
    fun `item carries url protocol info duration and size`() {
        val item = DidlParser.parseEntries(realResult).first { !it.isContainer }
        assertEquals("http://192.168.99.223:8200/MediaItems/1386.mp3", item.url)
        assertEquals("http-get:*:audio/mpeg:*", item.protocolInfo)
        assertEquals(171, item.durationSec) // 0:02:51.058
        assertEquals(6888383, item.sizeBytes)
        assertEquals("00 凯叔给家长的一封信", item.title)
    }

    @Test
    fun `artist is parsed and used in display name`() {
        val withArtist = DidlParser.parseEntries(realResult).first { it.artist != null }
        assertEquals("凯叔", withArtist.artist)
        assertEquals("000 凯叔水浒传·魔君降世 · 凯叔", withArtist.displayName)
    }

    @Test
    fun `didl duration handles all the shapes devices emit`() {
        assertEquals(171, DidlParser.parseDidlDuration("0:02:51.058"))
        assertEquals(863, DidlParser.parseDidlDuration("0:14:23.972"))
        assertEquals(3661, DidlParser.parseDidlDuration("1:01:01"))
        assertEquals(90, DidlParser.parseDidlDuration("1:30"))
        assertEquals(0, DidlParser.parseDidlDuration(null))
        assertEquals(0, DidlParser.parseDidlDuration("garbage"))
    }

    @Test
    fun `escaped result is unescaped before parsing`() {
        // 设备把 DIDL 整体转义后放进 <Result>，客户端必须先反转义
        val escaped = realResult.replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
        assertEquals(realResult, UpnpXml.unescape(escaped))
    }

    @Test
    fun `browse result pagination flag`() {
        assertTrue(BrowseResult(listOf(BrowseEntry("1", "0", "a", false, null)), 10, 0).hasMore)
        assertFalse(BrowseResult(listOf(BrowseEntry("1", "0", "a", false, null)), 1, 0).hasMore)
    }
}

class ContentDirectoryClientTest {

    private class FakeTransport(var response: String) : UpnpTransport {
        val bodies = ArrayList<String>()
        override fun get(url: String): String = error("no GET")
        override fun postSoap(url: String, soapAction: String, body: String): String {
            bodies += body
            return response
        }
    }

    private fun browseResponse(didl: String, total: Int) = """
        <u:BrowseResponse xmlns:u="urn:schemas-upnp-org:service:ContentDirectory:1">
          <Result>${UpnpXml.escape(didl)}</Result>
          <NumberReturned>1</NumberReturned>
          <TotalMatches>$total</TotalMatches>
          <UpdateID>1</UpdateID>
        </u:BrowseResponse>
    """.trimIndent()

    @Test
    fun `browse sends the six documented arguments in order`() {
        val t = FakeTransport(browseResponse("<DIDL-Lite/>", 0))
        ContentDirectoryClient("http://nas:8200/ctl/ContentDir", t).browse("1\$4")

        val body = t.bodies.single()
        listOf("ObjectID", "BrowseFlag", "Filter", "StartingIndex", "RequestedCount", "SortCriteria").let { names ->
            var last = -1
            names.forEach {
                val at = body.indexOf("<$it>")
                assertTrue("missing $it", at > last)
                last = at
            }
        }
        assertTrue(body.contains("<ObjectID>1\$4</ObjectID>"))
        assertTrue(body.contains("<BrowseFlag>BrowseDirectChildren</BrowseFlag>"))
    }

    @Test
    fun `total matches drives hasMore`() {
        val t = FakeTransport(browseResponse("<DIDL-Lite><item id=\"1\" parentID=\"0\"><dc:title>A</dc:title></item></DIDL-Lite>", 500))
        val r = ContentDirectoryClient("http://nas/c", t, pageSize = 100).browse("0")
        assertEquals(500, r.totalMatches)
        assertTrue(r.hasMore)
    }

    @Test
    fun `browseAll stops when a short page comes back`() {
        val t = FakeTransport(browseResponse("<DIDL-Lite><item id=\"1\" parentID=\"0\"><dc:title>A</dc:title></item></DIDL-Lite>", 1))
        val all = ContentDirectoryClient("http://nas/c", t, pageSize = 100).browseAll("0")
        assertEquals(1, all.size)
        assertEquals(1, t.bodies.size) // 首页就取完，不该再发第二页
    }

    @Test
    fun `empty page terminates paging`() {
        val t = FakeTransport(browseResponse("<DIDL-Lite/>", 999))
        val all = ContentDirectoryClient("http://nas/c", t, pageSize = 10).browseAll("0")
        assertTrue(all.isEmpty())
    }

    @Test
    fun `control url is taken from the device description`() {
        val desc = DeviceDescription.parse(
            "http://nas:8200/rootDesc.xml",
            "<root><device><serviceList><service>" +
                "<serviceType>${ContentDirectory.SERVICE_TYPE}</serviceType>" +
                "<controlURL>/ctl/ContentDir</controlURL></service></serviceList></device></root>",
        )
        assertEquals("http://nas:8200/ctl/ContentDir", ContentDirectoryClient.controlUrlOf(desc))
        assertNull(ContentDirectoryClient.controlUrlOf(DeviceDescription.parse("http://h/d.xml", "<root/>")))
    }
}
