package io.github.laozhudu.linkplay.protocol.multiroom

import io.github.laozhudu.linkplay.protocol.DeviceHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 组网命令逐字锁死（真机验证过的那一条）。
 *
 * 2026-11-03 真机：`ConnectMasterAp:…:ch=11:auth=OPEN:encry=NONE:pwd=:chext=0`
 * 发给 `.153`，第 8 秒 `.164` 的 `getSlaveList` 出现 `slaves:1`，从机落到 `10.10.10.92`；
 * `SlaveKickout` 3 秒后复原。
 *
 * 下面每一条断言都对应一个**曾经让我们组不上**的错误：
 * 1. 加了 `multiroom:` 前缀（那是"搬迁已有从机"的命令）；
 * 2. 用了小写 hex（`chext=0` 必须大写）；
 * 3. 把 `getNetwork` 的整段 JSON 当密码（`securemode=0` 必须是空串）。
 */
class JoinGroupCommandTest {

    /** 假客户端：不发网络请求，只记下 (目标 IP, 命令)。 */
    private class CapturingClient : DeviceHttpClient() {
        val sent = mutableListOf<Pair<String, String>>()
        override fun get(deviceIp: String, command: String, path: String, readTimeoutMs: Int): String {
            sent += deviceIp to command
            return "OK"
        }
    }

    private fun hexU(s: String) = s.toByteArray(Charsets.UTF_8).joinToString("") { "%02X".format(it) }
    private fun hexL(s: String) = s.toByteArray(Charsets.UTF_8).joinToString("") { "%02x".format(it) }

    @Test
    fun `grouping command matches the real device verified string`() {
        val client = CapturingClient()
        MultiroomClient(client).joinGroup(
            masterIp = "192.168.99.164",
            masterSsid = "DOSS DS-1831_400C",
            slaveIp = "192.168.99.153",
            channel = 11,
            password = "",
        )
        val (targetIp, command) = client.sent.single()
        assertEquals("命令发给从机", "192.168.99.153", targetIp)
        assertEquals(
            "ConnectMasterAp:ssid=" + hexU("DOSS DS-1831_400C") +
                ":ch=11:auth=OPEN:encry=NONE:pwd=:chext=0",
            command,
        )
    }

    @Test
    fun `grouping command has no multiroom prefix`() {
        val client = CapturingClient()
        MultiroomClient(client).joinGroup("192.168.99.164", "DOSS DS-1831_400C", "192.168.99.153", 11, "")
        val command = client.sent.single().second
        assertFalse("多一个 multiroom: 前缀，设备就当成'搬迁从机'而不组网：$command", command.startsWith("multiroom:"))
        assertTrue(command.startsWith("ConnectMasterAp:"))
    }

    @Test
    fun `grouping hex is uppercase`() {
        val client = CapturingClient()
        MultiroomClient(client).joinGroup("192.168.99.164", "DOSS DS-1831_400C", "192.168.99.153", 11, "")
        val command = client.sent.single().second
        val ssidHex = command.substringAfter("ssid=").substringBefore(":ch=")
        assertEquals("chext=0 必须大写 hex", ssidHex, ssidHex.uppercase())
        assertEquals(hexU("DOSS DS-1831_400C"), ssidHex)
    }

    @Test
    fun `grouping uses chext 0`() {
        val client = CapturingClient()
        MultiroomClient(client).joinGroup("192.168.99.164", "X", "192.168.99.153", 11, "")
        assertTrue(client.sent.single().second.endsWith(":chext=0"))
    }

    @Test
    fun `password present switches to WPA2 PSK with uppercase hex`() {
        val client = CapturingClient()
        MultiroomClient(client).joinGroup("192.168.99.164", "X", "192.168.99.153", 11, "abcd1234")
        val command = client.sent.single().second
        assertTrue(command.contains(":auth=WPA2PSK"))
        assertTrue(command.contains(":encry=AES"))
        assertTrue(command.contains(":pwd=" + hexU("abcd1234")))
    }

    @Test
    fun `moving an existing slave uses the other dialect`() {
        // 原厂三参重载：带 multiroom 前缀、chext=1、小写 hex。留着做协议对照。
        val client = CapturingClient()
        MultiroomClient(client).moveExistingSlave(
            masterIp = "192.168.99.164",
            masterSsid = "DOSS DS-1831_400C",
            existingSlaveIp = "10.10.10.92",
            channel = 11,
            password = "p",
        )
        val command = client.sent.single().second
        assertTrue(command.startsWith("multiroom:ConnnectAP:192.168.99.164:ConnectMasterAp:ssid="))
        assertTrue(command.contains("ssid=" + hexL("DOSS DS-1831_400C")))
        assertTrue(command.endsWith(":chext=1"))
    }
}
