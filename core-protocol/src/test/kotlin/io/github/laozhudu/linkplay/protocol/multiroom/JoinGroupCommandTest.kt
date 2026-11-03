package io.github.laozhudu.linkplay.protocol.multiroom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import io.github.laozhudu.linkplay.protocol.DeviceHttpClient

/**
 * 拖拽组网最终会发出什么命令 —— 逐字锁死。
 *
 * 这条命令是反编译 `com.wifiaudio.a.b.a(master, slave, apInfo)` 拿到的原厂格式，
 * 其中有两个**看起来可以"优化"、实际一改就组不上**的地方：
 *
 * 1. `auth` 只看「password 是否非空」，不看它是真密码还是脏数据
 *    （原厂把 `getNetwork` 的整段返回 `{"securemode":"0",}` 当密码，于是发 WPA2PSK）；
 * 2. 热点名 `ssid` 用**小写** hex（普通配网 `chext=0` 用的是大写 hex）。
 *
 * 拖拽组网只是换了"谁是主机"的入口，命令本身必须与一键组网完全一致，
 * 所以这里用假 HTTP 客户端把命令截下来断言。
 */
class JoinGroupCommandTest {

    /** 原厂的 `com.wifiaudio.utils.a.b(byte[])`：`Integer.toHexString` 补零，**小写**、**不转输入大小写**。 */
    private fun hex(text: String): String =
        text.toByteArray(Charsets.UTF_8).joinToString("") { "%02x".format(it) }

    /** 假客户端：不发网络请求，只记下 (目标 IP, 命令)。 */
    private class CapturingClient : DeviceHttpClient() {
        val sent = mutableListOf<Pair<String, String>>()
        override fun get(deviceIp: String, command: String, path: String, readTimeoutMs: Int): String {
            sent += deviceIp to command
            return "OK"
        }
    }

    @Test
    fun `command matches the original app byte for byte`() {
        val client = CapturingClient()
        MultiroomClient(client).joinGroup(
            masterIp = "192.168.99.164",
            masterSsid = "DOSS DS-1831_400C",
            slaveIp = "192.168.99.153",
            channel = 11,
            password = "{\"securemode\":\"0\",}",
        )
        val (targetIp, command) = client.sent.single()
        assertEquals("命令发给**从机**，不是主机", "192.168.99.153", targetIp)
        assertEquals(
            "multiroom:ConnnectAP:192.168.99.164:ConnectMasterAp" +
                ":ssid=" + hex("DOSS DS-1831_400C") +
                ":ch=11" +
                ":auth=WPA2PSK" +
                ":encry=AES" +
                ":pwd=" + hex("{\"securemode\":\"0\",}") +
                ":chext=1",
            command,
        )
        assertTrue("组网命令必须带 chext=1（普通配网是 chext=0）", command.endsWith(":chext=1"))
    }

    /**
     * 组网的 ssid **不**转大写，而普通配网要转 —— 这是两个不同的入口，抄错哪个都组不上。
     *
     * 原厂证据（jadx `com/wifiaudio/a/b.java`）：
     * - 组网：`ssid=" + utils.a.b(str3.getBytes())`，`chext=1`，**没有** toUpperCase；
     * - 配网：`ssid=" + utils.a.b(...).toUpperCase()`，`chext=0`。
     */
    @Test
    fun `grouping ssid keeps lowercase hex unlike provisioning`() {
        val client = CapturingClient()
        MultiroomClient(client).joinGroup("10.0.0.1", "DOSS", "10.0.0.2", 11, "p")
        val ssidHex = client.sent.single().second.substringAfter("ssid=").substringBefore(":ch=")
        assertEquals("444f5353", ssidHex)
        assertEquals("组网用的是小写 hex 字母", ssidHex, ssidHex.lowercase())
    }

    @Test
    fun `empty password still uses the WPA2 branch when it is not empty`() {
        // 反向用例：锁死"不要按 securemode 自作聪明改 OPEN"这条教训
        val client = CapturingClient()
        MultiroomClient(client).joinGroup(
            masterIp = "10.0.0.1",
            masterSsid = "X",
            slaveIp = "10.0.0.2",
            channel = 6,
            password = "{\"securemode\":\"0\",}",
        )
        val command = client.sent.single().second
        assertTrue("密码串非空就必须走 WPA2PSK（原厂就是这么发的）", command.contains(":auth=WPA2PSK"))
        assertTrue(command.contains(":encry=AES"))
    }

    @Test
    fun `a truly empty password falls back to OPEN`() {
        val client = CapturingClient()
        MultiroomClient(client).joinGroup("10.0.0.1", "X", "10.0.0.2", 6, "")
        val command = client.sent.single().second
        assertTrue(command.contains(":auth=OPEN"))
        assertTrue(command.contains(":encry=NONE"))
        assertTrue("OPEN 分支不带密码", command.contains(":pwd=:"))
    }
}
