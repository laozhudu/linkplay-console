package io.github.laozhudu.linkplay.protocol.multiroom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 主机热点参数与「自动补信道」。
 *
 * 这两条都是真机踩出来的：
 * 1. 主机热点信道必须等于 `WifiChannel`（单 Wi-Fi 模块的 AP 与 STA 同信道），传 0 试过不行；
 * 2. 密码不能从 `getNetwork` 取 —— 真机只返回 `{"securemode":"0",}`。
 */
class MultiroomChannelTest {

    /** 记录所有下发的命令，按 command 前缀返回预设响应。 */
    private class FakeHttp(private val responses: Map<String, String>) :
        io.github.laozhudu.linkplay.protocol.DeviceHttpClient() {
        val sent = mutableListOf<Pair<String, String>>()   // deviceIp to command
        override fun get(
            deviceIp: String,
            command: String,
            path: String,
            readTimeoutMs: Int,
        ): String {
            sent += deviceIp to command
            return responses.entries.firstOrNull { command.startsWith(it.key) }?.value
                ?: "<ERR unknown>"
        }
    }

    // 真机 getStatusEx 的精简版（字段与真实返回一致）
    private val statusEx = """{ "ssid": "DOSS DS-1831_400C", "hideSSID": "1", "essid": "4D594444",
        "WifiChannel": "11", "securemode": "0", "uuid": "FF310096A5760623673F81F1" }"""

    @Test
    fun readApInfo_读ssid信道与加密方式() {
        val http = FakeHttp(mapOf("getStatusEx" to statusEx))
        val info = MultiroomClient(http).readApInfo("192.168.99.164")!!
        assertEquals("DOSS DS-1831_400C", info.ssid)
        assertEquals(11, info.channel)
        assertTrue(info.isOpen)
    }

    @Test
    fun readApInfo_读不到设备时返回null() {
        val http = FakeHttp(emptyMap())
        assertEquals(null, MultiroomClient(http).readApInfo("192.168.99.164"))
    }

    @Test
    fun joinWithPolling_自动补主机信道() {
        val http = FakeHttp(
            mapOf(
                "getStatusEx" to statusEx,
                "multiroom:ConnnectAP" to "OK",
                "multiroom:getSlaveList" to """{ "slaves": 0 }""",
            ),
        )
        var clock = 0L
        MultiroomClient(http).joinWithPolling(
            masterIp = "192.168.99.164",
            masterSsid = "DOSS DS-1831_400C",
            slaveIp = "192.168.99.153",
            channel = 0,                       // 不传 → 应自动补成 11
            sleep = { clock += it },
            now = { clock },
        )
        val cmd = http.sent.first { it.second.startsWith("multiroom:ConnnectAP") }
        // 发给从机，参数里带主机 IP 与 ch=11
        assertEquals("192.168.99.153", cmd.first)
        assertTrue(cmd.second.contains(":ch=11:"))
        assertTrue(cmd.second.contains("ch=11"))
        assertTrue(cmd.second.endsWith(":chext=1"))
    }

    @Test
    fun joinWithPolling_密码留空时照原厂用getNetwork返回值走WPA2PSK() {
        // 真机 getNetwork 的真实返回就是这个 JSON，原厂直接把它当密码用，且能组上
        val http = FakeHttp(
            mapOf(
                "getStatusEx" to statusEx,
                "getNetwork" to """{"securemode":"0",}""",
                "multiroom:ConnnectAP" to "OK",
                "multiroom:getSlaveList" to """{ "slaves": 0 }""",
            ),
        )
        var clock = 0L
        MultiroomClient(http).joinWithPolling(
            masterIp = "192.168.99.164",
            masterSsid = "DOSS DS-1831_400C",
            slaveIp = "192.168.99.153",
            sleep = { clock += it },
            now = { clock },
        )
        val cmd = http.sent.first { it.second.startsWith("multiroom:ConnnectAP") }.second
        assertTrue(cmd.contains("auth=WPA2PSK"))
        assertTrue(cmd.contains("encry=AES"))
        // pwd 是那段 JSON 的 hex
        assertTrue(cmd.contains("pwd=7b227365637572656d6f646522"))
    }

    @Test
    fun 显式信道优先() {
        val http = FakeHttp(
            mapOf(
                "getStatusEx" to statusEx,
                "multiroom:ConnnectAP" to "OK",
                "multiroom:getSlaveList" to """{ "slaves": 0 }""",
            ),
        )
        var clock = 0L
        MultiroomClient(http).joinWithPolling(
            masterIp = "192.168.99.164",
            masterSsid = "DOSS DS-1831_400C",
            slaveIp = "192.168.99.153",
            channel = 6,
            sleep = { clock += it },
            now = { clock },
        )
        val cmd = http.sent.first { it.second.startsWith("multiroom:ConnnectAP") }.second
        assertTrue(cmd.contains(":ch=6:"))
    }
}
