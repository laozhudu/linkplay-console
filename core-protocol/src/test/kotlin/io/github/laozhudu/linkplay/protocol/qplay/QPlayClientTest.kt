package io.github.laozhudu.linkplay.protocol.qplay

import io.github.laozhudu.linkplay.protocol.upnp.DeviceDescription
import io.github.laozhudu.linkplay.protocol.upnp.UpnpService
import io.github.laozhudu.linkplay.protocol.upnp.UpnpServices
import io.github.laozhudu.linkplay.protocol.upnp.UpnpTransport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QPlayClientTest {

    private class FakeTransport(var response: String = "<ok/>") : UpnpTransport {
        val calls = ArrayList<Pair<String, String>>()
        override fun get(url: String): String = error("no GET")
        override fun postSoap(url: String, soapAction: String, body: String): String {
            calls += soapAction to body
            return response
        }
    }

    private fun description(withQPlay: Boolean = true): DeviceDescription {
        val services = mutableListOf(UpnpService(UpnpServices.AV_TRANSPORT, "1", "http://h/av", null, null))
        if (withQPlay) services += UpnpService(QPLAY_SERVICE_TYPE, "2", "http://h/qplay", null, null)
        return DeviceDescription("http://h/d.xml", "doss3", "DOSS", "DOSS Player", "uuid:x", services)
    }

    @Test
    fun `missing service is reported not thrown at construction`() {
        val client = QPlayClient(description(withQPlay = false), FakeTransport())
        assertFalse(client.isSupported)
        assertTrue(runCatching { client.maxTracks() }.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun `max tracks is parsed from the real response shape`() {
        val t = FakeTransport("<MaxTracks>200</MaxTracks>")
        assertEquals(200, QPlayClient(description(), t).maxTracks())
        assertEquals("\"urn:schemas-tencent-com:service:QPlay:1#GetMaxTracks\"", t.calls.single().first)
    }

    @Test
    fun `insertTracks sends the three documented arguments`() {
        val t = FakeTransport("<NumberOfSuccess>2</NumberOfSuccess>")
        val n = QPlayClient(description(), t).insertTracks("0", 0, "<DIDL-Lite/>")
        assertEquals(2, n)
        val body = t.calls.single().second
        assertTrue(body.contains("<QueueID>0</QueueID>"))
        assertTrue(body.contains("<StartingIndex>0</StartingIndex>"))
        assertTrue(body.contains("<TracksMetaData>&lt;DIDL-Lite/&gt;</TracksMetaData>"))
    }

    @Test
    fun `empty response body means the device silently ignored the write`() {
        // 实机行为：未认证时 InsertTracks 返回 200 但响应体为空
        val t = FakeTransport("")
        assertNull(QPlayClient(description(), t).insertTracks("0", 0, "<x/>"))
        assertNull(QPlayClient(description(), t).tracksCount())
    }

    @Test
    fun `auth returns null when no code is issued`() {
        assertNull(QPlayClient(description(), FakeTransport("")).auth("seed"))
        val ok = QPlayClient(description(), FakeTransport("<Code>C</Code><MID>M</MID><DID>D</DID>")).auth("seed")
        assertEquals("C", ok?.code)
        assertEquals("M", ok?.mid)
    }
}
