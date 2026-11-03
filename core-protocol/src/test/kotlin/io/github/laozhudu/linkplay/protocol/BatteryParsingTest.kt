package io.github.laozhudu.linkplay.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 电量解析。
 *
 * 真机两种来源（2026-11-03 实测）：
 * - `getStatusEx` → `"battery":"0", "battery_percent":"100"`（独立设备/主机）
 * - `multiroom:getSlaveList` → `"battery":60`（组内从机，从机在 10.10.10.x 我们直连不到）
 *
 * 原厂的设备卡片上有电量，我们之前完全没读这两个字段。
 */
class BatteryParsingTest {

    private val statusEx = """
        { "ssid": "DOSS DS-1831_400C", "uuid": "FF310096A5760623673F81F1",
          "internet": "1", "battery": "0", "battery_percent": "100", "WifiChannel": "11" }
    """.trimIndent()

    @Test
    fun `battery percent comes from getStatusEx`() {
        assertEquals(100, LinkPlayStatus.parse(statusEx).batteryPercent)
    }

    @Test
    fun `missing battery field is null not zero`() {
        // 不能把"设备没报"当成 0% —— 那会显示成空电
        assertNull(
            LinkPlayStatus.parse("""{ "uuid": "X", "internet": "1" }""").batteryPercent,
        )
    }

    @Test
    fun `slave list battery and channel are parsed`() {
        val json = """{ "slaves": 1, "slave_list": [ {
            "name": "doss3", "ssid": "DOSS DS-1831_ADB4", "mask": 0,
            "volume": 62, "mute": 0, "channel": 1, "battery": 60,
            "ip": "10.10.10.92", "version": "3.6.5310",
            "uuid": "uuid:FF31F017-4FE5-C9B2-A2AC-EAE3FF31F017" } ] }"""
        val slave = SlaveDevice.parseList(json).single()
        assertEquals(60, slave.battery)
        assertEquals(1, slave.channel)
        assertEquals(62, slave.volume)
    }
}
