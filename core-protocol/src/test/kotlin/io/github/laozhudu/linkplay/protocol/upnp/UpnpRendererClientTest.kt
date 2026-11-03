package io.github.laozhudu.linkplay.protocol.upnp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 用假传输层驱动整个控制点，验证「发出去的 SOAP 到底长什么样」。 */
class UpnpRendererClientTest {

    private class RecordingTransport(
        private val responses: MutableMap<String, String> = mutableMapOf(),
    ) : UpnpTransport {
        data class Call(val url: String, val soapAction: String, val body: String)

        val calls = ArrayList<Call>()

        var nextResponse: String = "<ok/>"

        override fun get(url: String): String = responses[url] ?: error("unexpected GET $url")

        override fun postSoap(url: String, soapAction: String, body: String): String {
            calls.add(Call(url, soapAction, body))
            return nextResponse
        }
    }

    private val description = DeviceDescription(
        location = "http://192.168.1.20:49152/description.xml",
        friendlyName = "LinkPlay_32C8",
        manufacturer = "WiiMu",
        modelName = "WiiMu Media player",
        udn = "uuid:x",
        services = listOf(
            UpnpService(
                UpnpServices.AV_TRANSPORT, "id1",
                "http://192.168.1.20:49152/upnp/control/rendertransport1",
                null, null,
            ),
            UpnpService(
                UpnpServices.RENDERING_CONTROL, "id2",
                "http://192.168.1.20:49152/upnp/control/rendercontrol1",
                null, null,
            ),
        ),
    )

    @Test
    fun `setAvTransportUri posts metadata and uri`() {
        val transport = RecordingTransport()
        val client = UpnpRendererClient(description, transport)

        client.setAvTransportUri("http://192.168.1.5:8080/t.mp3", "Test Track")

        val call = transport.calls.single()
        assertEquals("http://192.168.1.20:49152/upnp/control/rendertransport1", call.url)
        assertEquals("\"urn:schemas-upnp-org:service:AVTransport:1#SetAVTransportURI\"", call.soapAction)
        assertTrue(call.body.contains("<CurrentURI>http://192.168.1.5:8080/t.mp3</CurrentURI>"))
        // DIDL 被整体转义进 CurrentURIMetaData
        assertTrue(call.body.contains("&lt;dc:title&gt;Test Track&lt;/dc:title&gt;"))
        assertTrue(call.body.contains("protocolInfo=&quot;http-get:*:audio/mpeg:*&quot;"))
    }

    @Test
    fun `play pause stop target avtransport with instance id`() {
        val transport = RecordingTransport()
        val client = UpnpRendererClient(description, transport)
        client.play(); client.pause(); client.stop()

        assertEquals(3, transport.calls.size)
        assertTrue(transport.calls[0].body.contains("<InstanceID>0</InstanceID><Speed>1</Speed>"))
        assertTrue(transport.calls[1].body.contains("<u:Pause"))
        assertTrue(transport.calls[2].body.contains("<u:Stop"))
    }

    @Test
    fun `seek formats target as hmmss`() {
        val transport = RecordingTransport()
        val client = UpnpRendererClient(description, transport)
        client.seek(201)

        val body = transport.calls.single().body
        assertTrue(body.contains("<Unit>REL_TIME</Unit><Target>3:21</Target>"))
    }

    @Test
    fun `volume goes to rendering control with master channel`() {
        val transport = RecordingTransport()
        val client = UpnpRendererClient(description, transport)
        client.setVolume(35)

        val call = transport.calls.single()
        assertEquals("http://192.168.1.20:49152/upnp/control/rendercontrol1", call.url)
        assertTrue(call.soapAction.contains("RenderingControl:1#SetVolume"))
        assertTrue(call.body.contains("<Channel>Master</Channel><DesiredVolume>35</DesiredVolume>"))
    }

    @Test
    fun `volume clamps to 0-100`() {
        val transport = RecordingTransport()
        val client = UpnpRendererClient(description, transport)
        client.setVolume(250)
        client.setVolume(-5)
        assertTrue(transport.calls[0].body.contains("<DesiredVolume>100</DesiredVolume>"))
        assertTrue(transport.calls[1].body.contains("<DesiredVolume>0</DesiredVolume>"))
    }

    @Test
    fun `transportInfo parses the transport response`() {
        val transport = RecordingTransport().apply {
            nextResponse = "<CurrentTransportState>PAUSED_PLAYBACK</CurrentTransportState>"
        }
        val client = UpnpRendererClient(description, transport)
        assertEquals(TransportState.PAUSED_PLAYBACK, client.transportInfo().state)
    }

    @Test
    fun `description without avtransport refuses playback but device is still describable`() {
        val bare = description.copy(services = description.services.filter { it.serviceType == UpnpServices.RENDERING_CONTROL })
        val transport = RecordingTransport()
        val error = runCatching { UpnpRendererClient(bare, transport) }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
        assertTrue(error!!.message!!.contains("AVTransport"))
    }

    @Test
    fun `didl lite is well formed and escaped`() {
        val didl = UpnpRendererClient.buildDidlLite("http://h/a.mp3?a=1&b=2", "A & B", "audio/flac")
        assertTrue(didl.startsWith("<DIDL-Lite"))
        assertTrue(didl.endsWith("</DIDL-Lite>"))
        assertTrue(didl.contains("<dc:title>A &amp; B</dc:title>"))
        assertTrue(didl.contains("a=1&amp;b=2"))
        assertTrue(didl.contains("http-get:*:audio/flac:*"))
        assertTrue(didl.contains("object.item.audioItem.musicTrack"))
    }

    @Test
    fun `next and previous use AVTransport actions like the original app`() {
        // 原厂是 AVTransport.Next/Previous，不是 httpapi 的 setPlayerCmd:next —— 这条要锁住
        val transport = RecordingTransport()
        val client = UpnpRendererClient(description, transport)

        client.next()
        client.previous()

        assertEquals(2, transport.calls.size)
        assertEquals("\"urn:schemas-upnp-org:service:AVTransport:1#Next\"", transport.calls[0].soapAction)
        assertEquals("\"urn:schemas-upnp-org:service:AVTransport:1#Previous\"", transport.calls[1].soapAction)
        assertEquals("http://192.168.1.20:49152/upnp/control/rendertransport1", transport.calls[0].url)
        assertTrue(transport.calls[0].body.contains("<InstanceID>0</InstanceID>"))
        // 绝不能退回到 httpapi 的命令名
        assertTrue(!transport.calls[0].body.contains("setPlayerCmd"))
    }

    @Test
    fun `mute reads RenderingControl GetMute with Master channel`() {
        val transport = RecordingTransport().apply {
            nextResponse = "<CurrentMute>1</CurrentMute>"
        }
        val client = UpnpRendererClient(description, transport)

        assertTrue(client.mute())

        val call = transport.calls.single()
        assertEquals("\"urn:schemas-upnp-org:service:RenderingControl:1#GetMute\"", call.soapAction)
        assertTrue(call.body.contains("<Channel>Master</Channel>"))
    }

    @Test
    fun `volume set goes to RenderingControl not httpapi`() {
        // 原厂音量同样走 UPnP；这条防止以后又改回 httpapi
        val transport = RecordingTransport()
        val client = UpnpRendererClient(description, transport)

        client.setVolume(42)

        val call = transport.calls.single()
        assertEquals("\"urn:schemas-upnp-org:service:RenderingControl:1#SetVolume\"", call.soapAction)
        assertTrue(call.body.contains("<DesiredVolume>42</DesiredVolume>"))
        assertTrue(call.body.contains("<Channel>Master</Channel>"))
    }

    @Test
    fun `setChannel posts SetChannel with Master channel and DesiredChannel`() {
        // 原厂 com/wifiaudio/b/bl.java 的 master 分支走的就是这条（独立设备也能改声道）
        val transport = RecordingTransport()
        val client = UpnpRendererClient(description, transport)

        client.setChannel(1)

        val call = transport.calls.single()
        assertEquals("\"urn:schemas-upnp-org:service:RenderingControl:1#SetChannel\"", call.soapAction)
        assertTrue(call.body.contains("<Channel>Master</Channel>"))
        assertTrue(call.body.contains("<DesiredChannel>1</DesiredChannel>"))
    }

    @Test
    fun `setChannel clamps out of range values`() {
        val transport = RecordingTransport()
        UpnpRendererClient(description, transport).setChannel(9)
        assertTrue(transport.calls.single().body.contains("<DesiredChannel>2</DesiredChannel>"))
    }

    @Test
    fun `controlDeviceInfo posts GetControlDeviceInfo and parses body`() {
        val transport = RecordingTransport().apply {
            nextResponse = "<CurrentVolume>41</CurrentVolume><CurrentChannel>2</CurrentChannel>"
        }
        val info = UpnpRendererClient(description, transport).controlDeviceInfo()

        assertEquals("\"urn:schemas-upnp-org:service:RenderingControl:1#GetControlDeviceInfo\"", transport.calls.single().soapAction)
        assertEquals(41, info.volume)
        assertEquals(2, info.channel)
    }
}
