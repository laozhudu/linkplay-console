package io.github.laozhudu.linkplay.protocol

import io.github.laozhudu.linkplay.protocol.upnp.UpnpServices
import io.github.laozhudu.linkplay.protocol.upnp.UpnpTransport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkPlayDeviceTest {

    /** 假设备：控制 httpapi 的应答，并记录收到的命令。 */
    private class FakeHttpClient(
        var reachable: Boolean = true,
        var playerStatusAvailable: Boolean = true,
    ) : DeviceHttpClient() {
        val commands = ArrayList<String>()

        override fun get(deviceIp: String, command: String, path: String, readTimeoutMs: Int): String {
            if (!reachable) throw java.io.IOException("unreachable")
            commands += command
            return when {
                command.startsWith("getStatusEx") ->
                    """{"ssid":"LinkPlay_1","internet":1,"firmware":"4.2","uuid":"U"}"""
                command.startsWith("getPlayerStatus") ->
                    if (playerStatusAvailable) """{"status":"stop","curpos":"0","totlen":"0","vol":"10","mute":"0"}"""
                    else throw java.io.IOException("404")
                command.startsWith("multiroom:getSlaveList") -> """{"slave_list":[]}"""
                else -> "OK"
            }
        }
    }

    private class FakeUpnp(private val description: String?) : UpnpTransport {
        val posts = ArrayList<String>()

        override fun get(url: String): String = description ?: throw java.io.IOException("no description")

        override fun postSoap(url: String, soapAction: String, body: String): String {
            posts += soapAction
            return "<ok/>"
        }
    }

    private val fullDescription = """
        <root><device>
          <friendlyName>LinkPlay_32C8</friendlyName>
          <serviceList>
            <service><serviceType>${UpnpServices.AV_TRANSPORT}</serviceType>
              <controlURL>/av</controlURL></service>
            <service><serviceType>${UpnpServices.RENDERING_CONTROL}</serviceType>
              <controlURL>/rc</controlURL></service>
            <service><serviceType>${UpnpServices.WIIMU_PLAY_QUEUE}</serviceType>
              <controlURL>/pq</controlURL></service>
          </serviceList>
        </device></root>
    """.trimIndent()

    private val avOnlyDescription = """
        <root><device><serviceList>
          <service><serviceType>${UpnpServices.AV_TRANSPORT}</serviceType><controlURL>/av</controlURL></service>
        </serviceList></device></root>
    """.trimIndent()

    @Test
    fun `open probes reachability and throws when unreachable`() {
        val ok = LinkPlayDevice.open("192.168.1.9", null, FakeHttpClient(), FakeUpnp(null))
        assertEquals("192.168.1.9", ok.ip)

        val error = runCatching { LinkPlayDevice.open("192.168.1.9", null, FakeHttpClient(reachable = false), FakeUpnp(null)) }
            .exceptionOrNull()
        assertTrue(error is java.io.IOException)
    }

    @Test
    fun `capabilities reflect a fully featured device`() {
        val device = LinkPlayDevice.open("h", "http://h/d.xml", FakeHttpClient(), FakeUpnp(fullDescription))
        val caps = device.detectCapabilities()
        assertTrue(caps.httpApi)
        assertTrue(caps.upnpPlayback)
        assertTrue(caps.playQueue)
        assertTrue(caps.httpPlayer)
        assertTrue(caps.canPushQueue)
        assertTrue(caps.canControlPlayback)
        assertTrue(caps.summary().contains("playqueue=✓"))
    }

    @Test
    fun `device without location still reports httpapi only`() {
        val device = LinkPlayDevice.open("h", null, FakeHttpClient(), FakeUpnp(fullDescription))
        val caps = device.detectCapabilities()
        assertTrue(caps.httpApi)
        assertFalse(caps.upnpPlayback)
        assertFalse(caps.playQueue)
        assertNull(device.queue())
        assertNotNull(device.playback()) // 退到 httpapi 播放通道
    }

    @Test
    fun `old firmware without httpPlayer still has upnp playback`() {
        val device = LinkPlayDevice.open("h", "http://h/d.xml", FakeHttpClient(playerStatusAvailable = false), FakeUpnp(avOnlyDescription))
        val caps = device.detectCapabilities()
        assertTrue(caps.upnpPlayback)
        assertFalse(caps.playQueue)
        assertFalse(caps.httpPlayer)
        assertTrue(caps.canControlPlayback)
        assertFalse(caps.canPushQueue)
    }

    @Test
    fun `probe helper never throws`() {
        assertNull(LinkPlayDevice.probe("h", null, FakeHttpClient(reachable = false), FakeUpnp(null)))
        val caps = LinkPlayDevice.probe("h", "http://h/d.xml", FakeHttpClient(), FakeUpnp(fullDescription))
        assertNotNull(caps)
        assertTrue(caps!!.playQueue)
    }

    @Test
    fun `queue client is only returned when the service is declared`() {
        val withQueue = LinkPlayDevice.open("h", "http://h/d.xml", FakeHttpClient(), FakeUpnp(fullDescription))
        val client = withQueue.queue()
        assertNotNull(client)
        assertEquals(1, client!!.let { 1 })

        val without = LinkPlayDevice.open("h", "http://h/d.xml", FakeHttpClient(), FakeUpnp(avOnlyDescription))
        assertNull(without.queue())
    }

    @Test
    fun `preset and input commands go through httpapi`() {
        val http = FakeHttpClient()
        val device = LinkPlayDevice.open("h", null, http, FakeUpnp(null))
        device.pressPreset(3)
        device.switchInput("bluetooth")
        device.rename("客厅音箱")

        assertTrue(http.commands.contains("MCUKeyShortClick:3"))
        assertTrue(http.commands.contains("setPlayerCmd:switchmode:bluetooth"))
        assertTrue(http.commands.any { it.startsWith("setDeviceName:") })
    }

    @Test
    fun `preset index must start at one`() {
        val device = LinkPlayDevice.open("h", null, FakeHttpClient(), FakeUpnp(null))
        assertTrue(runCatching { device.pressPreset(0) }.exceptionOrNull() is IllegalArgumentException)
    }
}
