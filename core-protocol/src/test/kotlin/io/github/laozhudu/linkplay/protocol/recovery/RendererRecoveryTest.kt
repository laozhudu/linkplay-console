package io.github.laozhudu.linkplay.protocol.recovery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 假探针：按脚本逐个返回状态，并记录是否被要求重启。 */
private class FakeProbe(
    private val upnp: MutableList<Boolean>,
    private val http: MutableList<Boolean>,
    private val rebootOk: Boolean = true,
) : RendererProbe {
    var rebootCount = 0
        private set

    override fun httpApiOk(): Boolean = if (http.size > 1) http.removeAt(0) else http.first()
    override fun upnpOk(): Boolean = if (upnp.size > 1) upnp.removeAt(0) else upnp.first()
    override fun reboot(): Boolean {
        rebootCount++
        return rebootOk
    }
}

/** 假时钟：sleep 直接推进时间，不真的等。 */
private class FakeClock {
    var t = 0L
    fun sleep(ms: Long) { t += ms }
    fun now() = t
}

class RendererRecoveryTest {

    @Test
    fun `两条通道都通时判定健康`() {
        val probe = FakeProbe(mutableListOf(true), mutableListOf(true))
        val r = RendererRecovery(probe)
        assertEquals(RendererHealth.HEALTHY, r.diagnose())
    }

    @Test
    fun `httpapi 通但不看 UPnP 就返回健康`() {
        // UPnP 通时不应再去问 httpapi：老设备 httpapi 有超时风险，能省一次请求就省
        val probe = FakeProbe(mutableListOf(true), mutableListOf(false))
        assertEquals(RendererHealth.HEALTHY, RendererRecovery(probe).diagnose())
    }

    @Test
    fun `UPnP 挂而 httpapi 通是控制面掉线`() {
        val probe = FakeProbe(mutableListOf(false), mutableListOf(true))
        assertEquals(RendererHealth.CONTROL_LOST, RendererRecovery(probe).diagnose())
    }

    @Test
    fun `两条都不通是真离线`() {
        val probe = FakeProbe(mutableListOf(false), mutableListOf(false))
        assertEquals(RendererHealth.OFFLINE, RendererRecovery(probe).diagnose())
    }

    @Test
    fun `健康时 heal 不重启设备`() {
        val probe = FakeProbe(mutableListOf(true), mutableListOf(true))
        val clock = FakeClock()
        val r = RendererRecovery(probe, clock::sleep, clock::now)
        assertTrue(r.heal())
        assertEquals("健康设备不应被重启", 0, probe.rebootCount)
    }

    @Test
    fun `离线时不下发重启命令`() {
        // 发不出去的命令只会白等 60 秒，用户还得以为在恢复
        val probe = FakeProbe(mutableListOf(false), mutableListOf(false))
        val clock = FakeClock()
        val r = RendererRecovery(probe, clock::sleep, clock::now)
        assertFalse(r.heal())
        assertEquals("离线不该发重启", 0, probe.rebootCount)
    }

    @Test
    fun `控制面掉线时重启并等到 UPnP 恢复`() {
        // 前两次探测：UPnP false / httpapi true → CONTROL_LOST
        // 之后轮询：重启还没起来（false），然后恢复 true
        val probe = FakeProbe(
            upnp = mutableListOf(false, false, false, true),
            http = mutableListOf(true),
        )
        val clock = FakeClock()
        val r = RendererRecovery(probe, clock::sleep, clock::now)
        assertTrue(r.heal())
        assertEquals(1, probe.rebootCount)
        assertTrue("应在预算内恢复，实际用时 ${clock.t}ms", clock.t <= 60_000)
    }

    @Test
    fun `重启后一直不恢复则超时返回 false`() {
        val probe = FakeProbe(upnp = mutableListOf(false), http = mutableListOf(true))
        val clock = FakeClock()
        val r = RendererRecovery(probe, clock::sleep, clock::now)
        assertFalse(r.heal(timeoutMs = 10_000, pollMs = 2_000))
        assertEquals(1, probe.rebootCount)
        assertTrue("应等满预算，实际 ${clock.t}ms", clock.t >= 10_000)
    }

    @Test
    fun `重启命令下发失败直接返回 false`() {
        val probe = FakeProbe(
            upnp = mutableListOf(false),
            http = mutableListOf(true),
            rebootOk = false,
        )
        val clock = FakeClock()
        val r = RendererRecovery(probe, clock::sleep, clock::now)
        assertFalse(r.heal())
        assertEquals(0L, clock.t)
    }
}
