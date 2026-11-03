package io.github.laozhudu.linkplay.protocol.multiroom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 主机热点参数与「自动补信道」。
 *
 * 这两条都是真机踩出来的：
 * 1. 主机热点信道必须等于 `WifiChannel`（单 Wi-Fi 模块的 AP 与 STA 同信道），传 0 试过不行；
 * 2. 密码来自 `getNetwork` 的**解析结果**：`securemode != 1` 时是空串（见 PairingSecretTest）。
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
                "ConnectMasterAp" to "OK",
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
        val cmd = http.sent.first { it.second.startsWith("ConnectMasterAp") }
        // 发给从机，参数里带主机信道 ch=11
        assertEquals("192.168.99.153", cmd.first)
        assertTrue(cmd.second.contains(":ch=11:"))
        assertTrue(cmd.second.contains("ch=11"))
        assertTrue("组网命令是 chext=0（chext=1 是搬迁从机）", cmd.second.endsWith(":chext=0"))
    }

    @Test
    fun joinWithPolling_密码留空时按原厂解析得到空密码走OPEN() {
        // 真机 getNetwork 的真实返回就是这个 JSON。原厂 `ax.a(String)` 会把它解析成空串
        // （securemode != 1 → pwd = ""），于是 auth=OPEN / encry=NONE / pwd 留空。
        // 上一版这里把整段 JSON 当密码（WPA2PSK + JSON 的 hex），是推断错误 —— 见 PairingSecretTest。
        val http = FakeHttp(
            mapOf(
                "getStatusEx" to statusEx,
                "getNetwork" to """{"securemode":"0",}""",
                "ConnectMasterAp" to "OK",
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
        val cmd = http.sent.first { it.second.startsWith("ConnectMasterAp") }.second
        assertTrue("无密码时必须 OPEN：$cmd", cmd.contains(":auth=OPEN"))
        assertTrue(cmd.contains(":encry=NONE"))
        assertTrue("pwd 必须留空，不能把 JSON 塞进去：$cmd", cmd.contains(":pwd=:"))
    }

    @Test
    fun 显式信道优先() {
        val http = FakeHttp(
            mapOf(
                "getStatusEx" to statusEx,
                "ConnectMasterAp" to "OK",
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
        val cmd = http.sent.first { it.second.startsWith("ConnectMasterAp") }.second
        assertTrue(cmd.contains(":ch=6:"))
    }
}
