package io.github.laozhudu.upnp

import io.github.laozhudu.linkplay.protocol.upnp.DeviceDescription
import io.github.laozhudu.linkplay.protocol.upnp.UpnpTransport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 记录发出去的 SOAP 调用，断言"动作名和参数集"——参数名写错设备会直接回 402。 */
private class FakeTransport(
    private val reply: String = "<Envelope><CurrentTransportState>PLAYING</CurrentTransportState></Envelope>",
) : UpnpTransport {
    data class Sent(val url: String, val action: String, val body: String)
    val sent = mutableListOf<Sent>()

    override fun get(url: String): String = DESCRIPTION
    override fun postSoap(url: String, soapAction: String, body: String): String {
        sent += Sent(url, soapAction, body)
        return reply
    }
}

private const val DESCRIPTION = """
    <root><device>
      <friendlyName>客厅电视盒子</friendlyName>
      <serviceList>
        <service>
          <serviceType>urn:schemas-upnp-org:service:AVTransport:1</serviceType>
          <controlURL>/upnp/control/avt</controlURL>
        </service>
      </serviceList>
    </device></root>
"""

class GenericRendererTest {

    private fun renderer(
        xml: String = DESCRIPTION,
        transport: FakeTransport = FakeTransport(),
    ): Pair<GenericRenderer, FakeTransport> {
        val desc = DeviceDescription.parse("http://192.168.1.9/desc.xml", xml)
        return GenericRenderer(desc, transport) to transport
    }

    @Test
    fun `capabilities come from declared services not from brand`() {
        val (r, _) = renderer()
        assertTrue("声明了 AVTransport 就能控播放", r.capabilities.transport)
        assertFalse("没声明 RenderingControl 就不能调音量", r.capabilities.volume)
        assertFalse(r.capabilities.contentDirectory)
        assertTrue(r.capabilities.controllable)
    }

    @Test
    fun `a device with no controllable service is not controllable`() {
        val xml = """
            <root><device><friendlyName>X</friendlyName><serviceList></serviceList></device></root>
        """
        val (r, _) = renderer(xml)
        assertFalse(r.capabilities.controllable)
    }

    @Test
    fun `set av transport uri is not play — one action each`() {
        val (r, t) = renderer()
        r.setAvTransportUri("http://192.168.1.2:8080/a.mp3", "七里香")

        assertEquals(1, t.sent.size)
        val call = t.sent.single()
        assertEquals("\"urn:schemas-upnp-org:service:AVTransport:1#SetAVTransportURI\"", call.action)
        assertTrue(call.body.contains("<CurrentURI>http://192.168.1.2:8080/a.mp3</CurrentURI>"))
        assertTrue(call.body.contains("七里香"))
        // UPnP 规定只设置不播，播必须再发 Play
        assertFalse(call.action.contains("Play\""))
    }

    @Test
    fun `play pause stop use instance zero`() {
        val (r, t) = renderer()
        r.play()
        r.pause()
        r.stop()

        assertEquals(3, t.sent.size)
        assertTrue(t.sent[0].action.endsWith("#Play\""))
        assertTrue(t.sent[1].action.endsWith("#Pause\""))
        assertTrue(t.sent[2].action.endsWith("#Stop\""))
        t.sent.forEach { assertTrue(it.body.contains("<InstanceID>0</InstanceID>")) }
    }

    @Test
    fun `seek formats REL_TIME as H MM SS`() {
        val (r, t) = renderer()
        r.seek(202)
        assertTrue(t.sent.single().body.contains("<Target>0:03:22</Target>"))
    }

    @Test
    fun `set volume outside 0 to 100 is rejected before sending`() {
        val (r, t) = renderer()
        runCatching { r.setVolume(150) }
        assertTrue("越界不该发出去", t.sent.isEmpty())
    }

    @Test
    fun `volume on a device without rendering control says unsupported`() {
        val (r, _) = renderer()
        assertNull(r.volume())
    }

    @Test
    fun `missing service on the device is reported as unsupported, not as a network error`() {
        val (r, t) = renderer()
        val error = runCatching { r.setVolume(30) }.exceptionOrNull()
        // 设备没声明 RenderingControl：必须说"它没有这个服务"，不能说"连不上/无响应"
        assertTrue("应抛 Unsupported，实际=$error", error is RendererException.Unsupported)
        assertTrue("不该白发一条请求", t.sent.isEmpty())
    }
}

class RendererStateTest {

    @Test
    fun `time parsing handles the standard format`() {
        assertEquals(202_000L, RendererState.parseTime("0:03:22"))
        assertEquals(3_723_000L, RendererState.parseTime("1:02:03"))
        assertEquals(202_500L, RendererState.parseTime("0:03:22.500"))
    }

    @Test
    fun `garbage time is null not zero`() {
        // 真机上见过暂停时回 28242665ms —— 照抄到界面就是"位置 7.8 小时"
        assertNull(RendererState.parseTime(null))
        assertNull(RendererState.parseTime(""))
        assertNull(RendererState.parseTime("NOT_IMPLEMENTED"))
        assertNull(RendererState.parseTime("abc"))
        assertNull(RendererState.parseTime("99:03:22"))
    }

    @Test
    fun `transport state decides playing and has media`() {
        assertTrue(RendererState(transportState = "PLAYING").playing)
        assertFalse(RendererState(transportState = "PAUSED_PLAYBACK").playing)
        assertFalse(RendererState(transportState = "NO_MEDIA_PRESENT").hasMedia)
    }
}
