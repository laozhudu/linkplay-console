package io.github.laozhudu.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimingLedgerTest {

    private fun sample(name: String, ms: Long, cmds: Int = 0, at: Long = 1_000L, ok: Boolean = true) =
        ActionSample(name = name, atMs = at, durationMs = ms, commands = cmds, ok = ok)

    @Test
    fun `empty ledger says so instead of rendering nothing`() {
        val text = TimingLedger().render()
        assertTrue(text.contains("还没触发过"))
    }

    @Test
    fun `recent is newest first and carries commands`() {
        val ledger = TimingLedger()
        ledger.record(sample("refreshDevices", 240, cmds = 3, at = 1_000))
        ledger.record(sample("setVolume", 87, cmds = 1, at = 2_000))

        val recent = ledger.recent()
        assertEquals(listOf("setVolume", "refreshDevices"), recent.map { it.name })
        assertTrue(ledger.render().contains("setVolume 87ms 1 条指令"))
    }

    @Test
    fun `summaries put the slowest first and average the cost`() {
        val ledger = TimingLedger()
        ledger.record(sample("setVolume", 400, cmds = 1, at = 1_000))
        ledger.record(sample("setVolume", 600, cmds = 1, at = 2_000))
        ledger.record(sample("playPause", 40, cmds = 1, at = 3_000))

        val summaries = ledger.summaries()
        assertEquals("setVolume", summaries.first().name)
        assertEquals(500, summaries.first().avgMs)
        assertEquals(600, summaries.first().maxMs)
        assertEquals(2, summaries.first().count)
        assertEquals(1.0, summaries.first().avgCommands, 0.001)
    }

    @Test
    fun `same action twice inside the window is flagged as repeated`() {
        val ledger = TimingLedger(repeatWindowMs = 2_000)
        ledger.record(sample("refreshDevices", 200, at = 10_000))
        ledger.record(sample("refreshDevices", 210, at = 10_800))

        val repeats = ledger.repeats()
        assertEquals(1, repeats.size)
        assertEquals("refreshDevices", repeats.first().name)
        assertEquals(2, repeats.first().count)
        assertTrue(ledger.render().contains("可能有两处在刷同一件事"))
    }

    @Test
    fun `same action far apart is not repeat`() {
        val ledger = TimingLedger(repeatWindowMs = 2_000)
        ledger.record(sample("refreshDevices", 200, at = 10_000))
        ledger.record(sample("refreshDevices", 200, at = 15_000))

        assertTrue(ledger.repeats().isEmpty())
    }

    @Test
    fun `detour is reported only when more commands than the shortest path`() {
        val ledger = TimingLedger()
        ledger.record(sample("refreshDevices", 500, cmds = 6, at = 1_000))
        ledger.record(sample("refreshDevices", 400, cmds = 4, at = 5_000))

        val detour = ledger.detourOf("refreshDevices", expectCommands = 3)
        assertEquals(3, detour!!.expected)
        assertEquals(5.0, detour.actualAvg, 0.001)
        assertEquals(6, detour.worst)
        assertTrue(ledger.render(expectations = mapOf("refreshDevices" to 3)).contains("绕路"))

        // 期望值够宽时不该报（避免把"多一台设备"误判成绕路）
        assertNull(ledger.detourOf("refreshDevices", expectCommands = 6))
    }

    @Test
    fun `verdict follows the interaction thresholds`() {
        val ledger = TimingLedger()
        assertEquals("跟手", ledger.verdictOf(99))
        assertEquals("偏慢", ledger.verdictOf(100))
        assertEquals("偏慢", ledger.verdictOf(999))
        assertEquals("慢", ledger.verdictOf(1_000))
    }

    @Test
    fun `overflow drops the oldest sample`() {
        val ledger = TimingLedger(capacity = 3)
        repeat(5) { i -> ledger.record(sample("setVolume", 10L * (i + 1), at = 1_000L + i)) }

        assertEquals(3, ledger.size)
        val names = ledger.recent().map { it.durationMs }
        assertEquals(listOf(50L, 40L, 30L), names)
    }

    @Test
    fun `failure is counted in the summary`() {
        val ledger = TimingLedger()
        ledger.record(sample("radioPlay", 300, cmds = 1, at = 1_000, ok = false))
        assertEquals(1, ledger.summaries().first().failures)
        assertTrue(ledger.render().contains("失败"))
    }
}
