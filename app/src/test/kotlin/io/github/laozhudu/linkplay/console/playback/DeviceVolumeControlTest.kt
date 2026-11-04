package io.github.laozhudu.linkplay.console.playback

import io.github.laozhudu.linkplay.protocol.upnp.DeviceDescription
import io.github.laozhudu.linkplay.protocol.upnp.UpnpRendererClient
import io.github.laozhudu.linkplay.protocol.upnp.UpnpService
import io.github.laozhudu.linkplay.protocol.upnp.UpnpServices
import io.github.laozhudu.linkplay.protocol.upnp.UpnpTransport
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 音量读取的**取数来源**。
 *
 * 锁的是 `docs/65` 批次 2 点名的那件事：同一个音量值如果有两个来源
 * （卡片走 `GetControlDeviceInfo`、音量键走 `GetVolume`），迟早会打架 ——
 * 界面显示 60、读回 40，用户看到的就是"音量键不管用"。
 *
 * 所以断言的不是"读到了多少"，而是**发的到底是哪个动作**。
 *
 * 只覆盖 UPnP 成功这一条：其余分支会落到真实 `DeviceHttpClient`，
 * 那需要设备在网里，属于真机验证的范围。
 */
class DeviceVolumeControlTest {

    private class RecordingTransport(private val response: String) : UpnpTransport {
        val actions = ArrayList<String>()

        override fun get(url: String): String = error("不该发 GET")

        override fun postSoap(url: String, soapAction: String, body: String): String {
            actions += soapAction
            return response
        }
    }

    private val description = DeviceDescription(
        location = "http://192.168.99.164:49152/description.xml",
        friendlyName = "DOSS DS-1831",
        manufacturer = "WiiMu",
        modelName = "WiiMu Media player",
        udn = "uuid:test",
        services = listOf(
            UpnpService(
                UpnpServices.AV_TRANSPORT, "id1",
                "http://192.168.99.164:49152/upnp/control/rendertransport1",
                null, null,
            ),
            UpnpService(
                UpnpServices.RENDERING_CONTROL, "id2",
                "http://192.168.99.164:49152/upnp/control/rendercontrol1",
                null, null,
            ),
        ),
    )

    private fun soapResponse(volume: Int): String = """
        <s:Envelope><s:Body><u:GetControlDeviceInfoResponse>
          <CurrentVolume>$volume</CurrentVolume>
          <CurrentMute>0</CurrentMute>
          <CurrentChannel>0</CurrentChannel>
          <SlaveMask>0</SlaveMask>
        </u:GetControlDeviceInfoResponse></s:Body></s:Envelope>
    """.trimIndent()

    @Before
    fun setUp() {
        DeviceVolumeControl.forget(IP)
    }

    @After
    fun tearDown() {
        DeviceVolumeControl.clientFactory = null
        DeviceVolumeControl.forget(IP)
    }

    @Test
    fun `readVolume asks GetControlDeviceInfo not GetVolume`() {
        val transport = RecordingTransport(soapResponse(42))
        DeviceVolumeControl.clientFactory = { UpnpRendererClient(description, transport) }

        assertEquals(42, DeviceVolumeControl.readVolume(IP))

        val actions = transport.actions.joinToString(" ")
        assertTrue("必须走控制信息这条（与卡片同源）", actions.contains("GetControlDeviceInfo"))
        assertTrue("不能再用 RenderingControl.GetVolume 另开一个来源", !actions.contains("#GetVolume"))
        assertEquals("一次读取只该发一条 SOAP", 1, transport.actions.size)
    }

    private companion object {
        const val IP = "192.168.99.164"
    }
}
