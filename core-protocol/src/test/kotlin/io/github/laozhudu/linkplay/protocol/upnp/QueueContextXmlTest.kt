package io.github.laozhudu.linkplay.protocol.upnp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 设备侧队列 XML（QueueContext）。
 *
 * 样本是**真机返回的原文**（2026-11-03，`.153`，`BrowseQueue("LocalPlayList")`），
 * 只把 4 首裁成 1 首以缩小体积，其余字符未改。它的形状（`<Track1>` 带序号、
 * `<Metadata>` 里二次转义的 DIDL）就是原厂 `org.teleal.cling.support.c.a.l` 造出来的结构。
 */
class QueueContextXmlTest {

    private val realSample = """
<?xml version="1.0"?>
<PlayList>
<ListName>LocalPlayList</ListName>
<ListInfo>
<SourceName>LocalPlayList</SourceName>
<MarkSearch>0</MarkSearch>
<TrackNumber>4</TrackNumber>
<Quality>0</Quality>
<UpdateTime>53579</UpdateTime>
<LastPlayIndex>1</LastPlayIndex>
<RealIndex>0</RealIndex>
<SwitchPageMode>0</SwitchPageMode>
<CurrentPage>0</CurrentPage>
<TotalPages>0</TotalPages>
<searching>0</searching>
<PressType>0</PressType>
<Volume>0</Volume>
</ListInfo>
<Tracks><Track1>
<URL>http://192.168.99.223:8200/MediaItems/515.mp3</URL>
<Metadata>&lt;DIDL-Lite xmlns:dc=&quot;http://purl.org/dc/elements/1.1/&quot; xmlns:upnp=&quot;urn:schemas-upnp-org:metadata-1-0/upnp/&quot; xmlns:song=&quot;www.wiimu.com/song/&quot; xmlns=&quot;urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/&quot;&gt; &lt;upnp:class&gt;object.item.audioItem.musicTrack&lt;/upnp:class&gt; &lt;item&gt; &lt;res protocolInfo=&quot;http-get:*:audio/mpeg:DLNA.ORG_PN=MP3;DLNA.ORG_OP=01;&quot; duration=&quot;0&quot;&gt;http://192.168.99.223:8200/MediaItems/515.mp3&lt;/res&gt;&lt;dc:title&gt;曲目515&lt;/dc:title&gt; &lt;upnp:artist&gt;未知&lt;/upnp:artist&gt; &lt;upnp:album&gt;&lt;/upnp:album&gt; &lt;upnp:albumArtURI&gt;&lt;/upnp:albumArtURI&gt; &lt;/item&gt; &lt;/DIDL-Lite&gt; </Metadata>
<Id>0</Id>
<Source>LocalPlayList</Source>
<ChapterNumber>0</ChapterNumber>
<Chapters></Chapters>
</Track1></Tracks>
</PlayList>
""".trimIndent()

    private fun track(url: String, title: String, artist: String = "未知", dur: Long = 0) =
        QueueTrack(url = url, title = title, artist = artist, durationSec = dur)

    @Test
    fun 解析真机_BrowseQueue_返回的队列() {
        val p = QueueContextXml.parse(realSample)
        assertNotNull("真机样本必须能解析", p)
        assertEquals("LocalPlayList", p!!.name)
        assertEquals(1, p.tracks.size)
        val t = p.tracks.first()
        assertEquals("http://192.168.99.223:8200/MediaItems/515.mp3", t.url)
        assertEquals("曲目515", t.title)
        assertEquals("未知", t.artist)
    }

    @Test
    fun 生成的结构与真机一致_标签名带序号且_Metadata_二次转义() {
        val xml = QueueContextXml.build(
            "LinkPlayConsole",
            listOf(track("http://nas/1.mp3", "第一首"), track("http://nas/2.mp3", "第二首")),
        )
        assertTrue(xml.startsWith("<?xml version=\"1.0\" ?>"))
        assertTrue(xml.contains("<ListName>LinkPlayConsole</ListName>"))
        assertTrue(xml.contains("<TrackNumber>2</TrackNumber>"))
        // 标签名必须带序号：<Track1>/<Track2>，否则设备当成空列表
        assertTrue(xml.contains("<Track1>") && xml.contains("</Track1>"))
        assertTrue(xml.contains("<Track2>") && xml.contains("</Track2>"))
        // Metadata 内的 DIDL 必须是转义形态（设备端二次解析）
        assertTrue(xml.contains("<Metadata>&lt;DIDL-Lite"))
        assertTrue(xml.contains("object.item.audioItem.musicTrack"))
    }

    @Test
    fun 生成的队列能被自己解析回来() {
        val tracks = listOf(track("http://nas/a.mp3", "A & B", "歌手<1>"), track("http://nas/b.mp3", "第二", dur = 215))
        val p = QueueContextXml.parse(QueueContextXml.build("Q", tracks))!!
        assertEquals("Q", p.name)
        assertEquals(2, p.tracks.size)
        // 特殊字符要能原样往返（& 与 <> 都经过两次转义）
        assertEquals("A & B", p.tracks[0].title)
        assertEquals("歌手<1>", p.tracks[0].artist)
        assertEquals(215, p.tracks[1].durationSec)
    }

    @Test
    fun 控制字符被清洗_否则设备会拒绝整份列表() {
        val xml = QueueContextXml.build("Q", listOf(track("http://nas/a.mp3", "带\u0001控制符")))
        assertTrue("控制字符应被换成空格", !xml.contains("\u0001"))
    }

    @Test
    fun 空列表只给骨架不给_Tracks() {
        val xml = QueueContextXml.build("Q", emptyList())
        assertTrue(xml.contains("<TrackNumber>0</TrackNumber>"))
        assertTrue(!xml.contains("<Tracks>"))
    }

    @Test
    fun 当前曲目取_LastPlayIndex_而不是_RealIndex() {
        // 真机回包：LastPlayIndex=3（第 3 首），RealIndex=0（分页下标，不是曲目序号）
        val xml = "<PlayList><ListName>Q</ListName><ListInfo><LastPlayIndex>3</LastPlayIndex>" +
            "<RealIndex>0</RealIndex></ListInfo><Tracks></Tracks></PlayList>"
        assertEquals(2, QueueContextXml.parse(xml)!!.currentIndex)
    }

    @Test
    fun 没有_LastPlayIndex_时退回_RealIndex() {
        val xml = "<PlayList><ListName>Q</ListName><ListInfo><RealIndex>2</RealIndex></ListInfo>" +
            "<Tracks></Tracks></PlayList>"
        assertEquals(1, QueueContextXml.parse(xml)!!.currentIndex)
    }

    @Test
    fun 坏数据返回_null_不抛异常() {
        assertNull(QueueContextXml.parse(""))
        assertNull(QueueContextXml.parse("<html>not a playlist</html>"))
    }

    @Test
    fun 没有标题时用文件名兜底() {
        val xml = """<PlayList><ListName>Q</ListName><Tracks><Track1><URL>http://nas/x.mp3</URL>
            <Metadata></Metadata></Track1></Tracks></PlayList>"""
        val p = QueueContextXml.parse(xml)!!
        assertEquals("x.mp3", p.tracks.first().title)
    }

    @Test
    fun 整份被再转义一层也能解析() {
        val inner = QueueContextXml.build("Q", listOf(track("http://nas/a.mp3", "A")))
        val escaped = inner.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        val p = QueueContextXml.parse(escaped)!!
        assertEquals("Q", p.name)
        assertEquals(1, p.tracks.size)
    }
}
