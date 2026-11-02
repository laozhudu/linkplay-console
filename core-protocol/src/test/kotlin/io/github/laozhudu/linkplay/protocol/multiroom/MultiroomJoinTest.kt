package io.github.laozhudu.linkplay.protocol.multiroom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MultiroomJoinTest {

    /** 假上游：记录收到的命令，可按次返回不同的从机列表。 */
    private class FakeHttp(
        private val slaveListResponses: MutableList<String> = mutableListOf(),
    ) : io.github.laozhudu.linkplay.protocol.DeviceHttpClient() {
        val sent = mutableListOf<Pair<String, String>>()

        override fun get(
            deviceIp: String,
            command: String,
            path: String,
            readTimeoutMs: Int,
        ): String {
            sent += deviceIp to command
            if (command == "multiroom:getSlaveList") {
                return if (slaveListResponses.size > 1) slaveListResponses.removeAt(0)
                else slaveListResponses.firstOrNull() ?: """{"slaves":0}"""
            }
            return "OK"
        }
    }

    @Test
    fun `无密码主机生成 OPEN 命令，ssid 用小写 hex`() {
        val http = FakeHttp()
        val c = MultiroomClient(http)
        c.joinGroup(
            masterIp = "192.168.99.164",
            masterSsid = "DOSS DS-1831_400C",
            slaveIp = "192.168.99.153",
            channel = 6,
        )
        // 命令要发给从机
        assertEquals("192.168.99.153", http.sent.last().first)
        val cmd = http.sent.last().second
        val expected = "multiroom:ConnnectAP:192.168.99.164:ConnectMasterAp:" +
            "ssid=${MultiroomClient.hexLower("DOSS DS-1831_400C")}" +
            ":ch=6:auth=OPEN:encry=NONE:pwd=:chext=1"
        assertEquals(expected, cmd)
        // 小写 hex，不能是大写（普通配网才用大写）
        assertTrue(cmd.contains(MultiroomClient.hexLower("DOSS DS-1831_400C")))
        assertFalse(cmd.contains(MultiroomClient.hexLower("DOSS DS-1831_400C").uppercase()))
    }

    @Test
    fun `有密码时用 WPA2PSK-AES 并把密码转 hex`() {
        val http = FakeHttp()
        MultiroomClient(http).joinGroup(
            masterIp = "10.0.0.5",
            masterSsid = "SPK",
            slaveIp = "10.0.0.6",
            password = "12345678",
        )
        val cmd = http.sent.last().second
        assertTrue(cmd.contains(":auth=WPA2PSK:"))
        assertTrue(cmd.contains(":encry=AES:"))
        assertTrue(cmd.contains(":pwd=${MultiroomClient.hexLower("12345678")}"))
    }

    @Test
    fun `hex 编码是小写的两字节对齐`() {
        assertEquals("444f5353", MultiroomClient.hexLower("DOSS"))
        assertEquals("e4b8ad", MultiroomClient.hexLower("中"))
    }

    @Test
    fun `轮询看到从机出现即成功`() {
        val http = FakeHttp(
            mutableListOf(
                """{"slaves":0}""",
                """{"slaves":0}""",
                """{"slaves":[{"ip":"192.168.4.2","name":"doss3"}]}""",
            ),
        )
        val clock = longArrayOf(0L)
        var ticks = 0
        val ok = MultiroomClient(http).joinWithPolling(
            masterIp = "192.168.99.164",
            masterSsid = "DOSS DS-1831_400C",
            slaveIp = "192.168.4.2",     // 从机接入后会换 IP，按列表内容判断
            intervalMs = 2_000,
            timeoutMs = 40_000,
            sleep = { clock[0] += it },
            now = { clock[0] },
            onTick = { ticks++ },
        )
        assertTrue(ok)
        assertTrue("应该至少轮询两次，实际 $ticks", ticks >= 2)
    }

    @Test
    fun `超时返回失败`() {
        val http = FakeHttp(mutableListOf("""{"slaves":0}"""))
        val clock = longArrayOf(0L)
        val ok = MultiroomClient(http).joinWithPolling(
            masterIp = "192.168.99.164",
            masterSsid = "X",
            slaveIp = "192.168.4.2",
            intervalMs = 2_000,
            timeoutMs = 6_000,
            sleep = { clock[0] += it },
            now = { clock[0] },
        )
        assertFalse(ok)
        // 发过成组命令
        assertTrue(http.sent.any { it.second.contains("multiroom:ConnnectAP:") })
    }

    @Test
    fun `中途要求停止时立即返回`() {
        val http = FakeHttp(mutableListOf("""{"slaves":0}"""))
        val clock = longArrayOf(0L)
        val ok = MultiroomClient(http).joinWithPolling(
            masterIp = "m", masterSsid = "X", slaveIp = "s",
            intervalMs = 1_000, timeoutMs = 60_000,
            sleep = { clock[0] += it }, now = { clock[0] },
            shouldStop = { true },
        )
        assertFalse(ok)
    }
}
