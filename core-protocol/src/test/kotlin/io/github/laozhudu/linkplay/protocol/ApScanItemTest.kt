package io.github.laozhudu.linkplay.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApScanItemTest {

    private val payload = """
        {"aplist":[
          {"ssid":"Home-5G","bssid":"aa:bb:cc:dd:ee:01","rssi":-44,"channel":149,"auth":"WPA2PSK","encry":"AES","extch":0},
          {"ssid":"FreeWiFi","bssid":"aa:bb:cc:dd:ee:02","rssi":-81,"channel":6,"auth":"OPEN","encry":"NONE","extch":0},
          {"ssid":"客厅","bssid":"aa:bb:cc:dd:ee:03","rssi":-60,"channel":11,"auth":"WPA2PSK","encry":"AES","extch":1}
        ]}
    """.trimIndent()

    @Test
    fun `parses every documented field`() {
        val aps = ApScanItem.parseList(payload)
        assertEquals(3, aps.size)
        val first = aps[0]
        assertEquals("Home-5G", first.ssid)
        assertEquals("aa:bb:cc:dd:ee:01", first.bssid)
        assertEquals(-44, first.rssi)
        assertEquals(149, first.channel)
        assertEquals("WPA2PSK", first.auth)
        assertEquals("AES", first.encry)
        assertEquals(0, first.extch)
    }

    @Test
    fun `open network is detected and secured one is not`() {
        val aps = ApScanItem.parseList(payload)
        assertTrue(aps[1].isOpen)
        assertFalse(aps[0].isOpen)
    }

    @Test
    fun `non ascii ssid survives`() {
        assertEquals("客厅", ApScanItem.parseList(payload)[2].ssid)
    }

    @Test
    fun `signal level buckets follow the documented thresholds`() {
        assertEquals(4, ApScanItem.parseList(payload)[0].signalLevel) // -44
        assertEquals(3, ApScanItem.parseList(payload)[2].signalLevel) // -60（>= -67 档）
        assertEquals(1, ApScanItem.parseList(payload)[1].signalLevel) // -81
    }

    @Test
    fun `missing optional numbers do not throw`() {
        val aps = ApScanItem.parseList("""{"aplist":[{"ssid":"X","auth":"OPEN","encry":"NONE","channel":1}]}""")
        assertEquals(1, aps.size)
        assertEquals(null, aps[0].rssi)
        assertEquals(0, aps[0].signalLevel)
    }

    @Test
    fun `malformed payloads degrade to empty list`() {
        assertTrue(ApScanItem.parseList("not json").isEmpty())
        assertTrue(ApScanItem.parseList("{}").isEmpty())
        assertTrue(ApScanItem.parseList("""{"aplist":[]}""").isEmpty())
        // 缺 ssid 的条目被跳过，其余保留
        assertEquals(1, ApScanItem.parseList("""{"aplist":[{"bssid":"x","channel":1},{"ssid":"Y","channel":2}]}""").size)
    }
}
