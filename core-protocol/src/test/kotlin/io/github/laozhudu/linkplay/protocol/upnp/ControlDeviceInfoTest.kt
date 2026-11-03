package io.github.laozhudu.linkplay.protocol.upnp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 用**真机原样响应**做回归。
 *
 * 样本取自 192.168.99.153（固件 3.6.5310），2026-11-03 实测，HTTP 200。
 * 刻意保留 `&quot;` 转义、`&quot;slaves&quot;: 0` 的空格 —— 这些细节曾经害过我们。
 */
class ControlDeviceInfoTest {

    private val realResponse = """
        <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/"><s:Body>
        <u:GetControlDeviceInfoResponse xmlns:u="urn:schemas-upnp-org:service:RenderingControl:1">
        <MultiType>1</MultiType>
        <Router>4D594444</Router>
        <Ssid>DOSS DS-1831_ADB4</Ssid>
        <SlaveMask>0</SlaveMask>
        <CurrentVolume>41</CurrentVolume>
        <CurrentMute>0</CurrentMute>
        <CurrentChannel>0</CurrentChannel>
        <SlaveList>{ &quot;slaves&quot;: 0 }</SlaveList>
        <Status>{ &quot;language&quot;: &quot;en_us&quot;, &quot;ssid&quot;: &quot;DOSS DS-1831_ADB4&quot;,
        &quot;uuid&quot;: &quot;FF31F0174FE5C9B2A2ACEAE3&quot;, &quot;WifiChannel&quot;: &quot;11&quot;,
        &quot;battery&quot;: &quot;1&quot;, &quot;battery_percent&quot;: &quot;50&quot;,
        &quot;upnp_uuid&quot;: &quot;uuid:FF31F017-4FE5-C9B2-A2AC-EAE3FF31F017&quot;,
        &quot;DeviceName&quot;: &quot;doss3&quot;, &quot;GroupName&quot;: &quot;doss3&quot; }</Status>
        </u:GetControlDeviceInfoResponse>
        </s:Body> </s:Envelope>
    """.trimIndent()

    @Test
    fun `parses volume mute channel from real response`() {
        val info = ControlDeviceInfo.parse(realResponse)
        assertEquals(41, info.volume)
        assertFalse(info.mute)
        assertEquals(0, info.channel)
        assertFalse(info.slaveMask)
        assertTrue(info.slaves.isEmpty())
    }

    @Test
    fun `status block is decoded once and re-parsed as getStatusEx`() {
        val status = ControlDeviceInfo.parse(realResponse).status
        assertEquals("FF31F0174FE5C9B2A2ACEAE3", status?.uuid)
        // 电量在 Status 里是字符串 "50"，必须解析出来（界面「电量有时没有」的一半原因在这）
        assertEquals(50, status?.batteryPercent)
        // 改名真值来源
        assertEquals("doss3", status?.deviceName)
    }

    @Test
    fun `channel right only is preserved`() {
        // .164 真机长期停在 2（仅右）：这正是我们之前一直显示 LR 的那台
        val info = ControlDeviceInfo.parse(realResponse.replace("<CurrentChannel>0", "<CurrentChannel>2"))
        assertEquals(2, info.channel)
    }

    @Test
    fun `missing fields degrade to null instead of throwing`() {
        val info = ControlDeviceInfo.parse("<u:GetControlDeviceInfoResponse></u:GetControlDeviceInfoResponse>")
        assertNull(info.volume)
        assertNull(info.channel)
        assertNull(info.status)
        assertFalse(info.mute)
    }

    @Test
    fun `slave list with one member is parsed`() {
        val xml = realResponse.replace(
            "<SlaveList>{ &quot;slaves&quot;: 0 }</SlaveList>",
            "<SlaveList>{ &quot;slaves&quot;: 1, &quot;slave_list&quot;: [ { &quot;name&quot;: &quot;doss3&quot;, " +
                "&quot;volume&quot;: 48, &quot;channel&quot;: 1, &quot;battery&quot;: 50, " +
                "&quot;ip&quot;: &quot;10.10.10.92&quot;, " +
                "&quot;uuid&quot;: &quot;uuid:FF31F017-4FE5-C9B2-A2AC-EAE3FF31F017&quot; } ] }</SlaveList>",
        )
        val slaves = ControlDeviceInfo.parse(xml).slaves
        assertEquals(1, slaves.size)
        assertEquals("10.10.10.92", slaves[0].ip)
        assertEquals(1, slaves[0].channel)
        assertEquals(50, slaves[0].battery)
    }
}
