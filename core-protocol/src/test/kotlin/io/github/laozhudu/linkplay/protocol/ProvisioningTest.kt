package io.github.laozhudu.linkplay.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProvisioningTest {

    // ---- 预校验 ----

    @Test
    fun `valid wpa2 credentials pass`() {
        assertNull(ProvisioningRules.validateCredentials("Home", "password123"))
    }

    @Test
    fun `empty password means open network`() {
        assertNull(ProvisioningRules.validateCredentials("FreeWiFi", ""))
        assertEquals("OPEN", ProvisioningRules.authFor(""))
        assertEquals("NONE", ProvisioningRules.encryFor(""))
    }

    @Test
    fun `short wpa2 password is rejected`() {
        val reason = ProvisioningRules.validateCredentials("Home", "1234567")
        assertTrue(reason!!.contains("8-63"))
    }

    @Test
    fun `ssid over 32 utf8 bytes is rejected`() {
        // 11 个汉字 = 33 字节
        val reason = ProvisioningRules.validateCredentials("一二三四五六七八九十壹", "")
        assertTrue(reason!!.contains("32"))
    }

    @Test
    fun `empty ssid is rejected`() {
        assertEquals("SSID 不能为空", ProvisioningRules.validateCredentials("", ""))
    }

    // ---- 轮询状态机（假时钟，不真等待）----

    private class FakeClock(var t: Long = 0) {
        val sleeps = ArrayList<Long>()
        fun now() = t
        fun sleep(ms: Long) { sleeps.add(ms); t += ms }
    }

    private fun status(essid: String?, internet: Int?) = LinkPlayStatus(
        ssid = "LinkPlay_32C8", hardware = "A31", firmware = "4.2", uuid = "U",
        ethernetMac = null, wifiMac = null, essid = essid, expired = 0,
        internet = internet, netstat = 1, language = "zh_CN", release = null,
        build = null, secureMode = "2",
    )

    @Test
    fun `connected when essid matches and internet is up`() {
        val clock = FakeClock()
        val poller = ProvisioningPoller(intervalMs = 1_000)
        val result = poller.poll(
            targetSsid = "Home",
            timeoutMs = 10_000,
            probe = { status("Home", 1) },
            sleep = clock::sleep,
            now = clock::now,
        )
        assertTrue(result is ProvisioningResult.Connected)
        assertEquals(0, clock.sleeps.size) // 第一次探测就成功，不该 sleep
    }

    @Test
    fun `joined but offline is reported when ip never arrives`() {
        val clock = FakeClock()
        val poller = ProvisioningPoller(intervalMs = 1_000)
        val result = poller.poll(
            targetSsid = "Home",
            timeoutMs = 5_000,
            probe = { status("Home", 0) },
            sleep = clock::sleep,
            now = clock::now,
        )
        assertTrue(result is ProvisioningResult.JoinedButOffline)
    }

    @Test
    fun `waits through offline then succeeds`() {
        val clock = FakeClock()
        var calls = 0
        val poller = ProvisioningPoller(intervalMs = 1_000)
        val result = poller.poll(
            targetSsid = "Home",
            timeoutMs = 30_000,
            probe = { calls++; if (calls < 4) status("Old", 1) else status("Home", 1) },
            sleep = clock::sleep,
            now = clock::now,
        )
        assertTrue(result is ProvisioningResult.Connected)
        assertEquals(4, calls)
    }

    @Test
    fun `timeout carries last observed status`() {
        val clock = FakeClock()
        val poller = ProvisioningPoller(intervalMs = 1_000)
        val result = poller.poll(
            targetSsid = "Home",
            timeoutMs = 3_000,
            probe = { status("Old", 1) },
            sleep = clock::sleep,
            now = clock::now,
        )
        assertTrue(result is ProvisioningResult.Timeout)
        assertEquals("Old", (result as ProvisioningResult.Timeout).lastSeen!!.essid)
    }

    @Test
    fun `transient failures are tolerated while device restarts its network stack`() {
        val clock = FakeClock()
        var calls = 0
        val poller = ProvisioningPoller(intervalMs = 1_000, maxConsecutiveFailures = 3)
        val result = poller.poll(
            targetSsid = "Home",
            timeoutMs = 30_000,
            probe = {
                calls++
                when (calls) {
                    1 -> throw java.io.IOException("connection refused")
                    2 -> throw java.io.IOException("connection refused")
                    else -> status("Home", 1)
                }
            },
            sleep = clock::sleep,
            now = clock::now,
        )
        assertTrue(result is ProvisioningResult.Connected)
    }

    @Test
    fun `permanent disappearance becomes unreachable`() {
        val clock = FakeClock()
        val poller = ProvisioningPoller(intervalMs = 1_000, maxConsecutiveFailures = 2)
        val result = poller.poll(
            targetSsid = "Home",
            timeoutMs = 60_000,
            probe = { throw java.io.IOException("unreachable") },
            sleep = clock::sleep,
            now = clock::now,
        )
        assertTrue(result is ProvisioningResult.Unreachable)
        assertEquals("unreachable", (result as ProvisioningResult.Unreachable).lastError)
    }
}
