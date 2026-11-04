package io.github.laozhudu.linkplay.protocol.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 口令流水账 —— 覆盖「点了没反应」这类最常报的问题。
 *
 * 判据是：**只看这一段文本，能不能分清三种情况**
 * 1. 发了，设备没照做
 * 2. 根本没发（设备睡了/地址变了）
 * 3. 发了，回包看不懂
 *
 * 这三种在界面上表现完全一样，只有流水账能分开。
 */
class CommandTraceTest {

    private fun entry(
        command: String = "Play",
        ip: String = "192.168.99.164",
        acked: Boolean = true,
        failure: String? = null,
        elapsed: Long = 60,
        at: Long = 1_000L,
    ) = CommandTrace.Entry(at, command, ip, acked, failure, elapsed)

    @Test
    fun `empty trace says so instead of printing zeros`() {
        val text = CommandTrace().render()
        assertTrue(text.contains("还没有下发过指令"))
        // 0 条时说"平均 0ms"是误导，不如说清楚
        assertFalse(text.contains("平均"))
    }

    @Test
    fun `successes and failures are counted separately`() {
        val t = CommandTrace()
        t.record(entry(acked = true))
        t.record(entry(acked = true))
        t.record(entry(acked = false, failure = "设备没有响应"))
        val text = t.render()
        assertTrue(text.contains("共 3 条 · 成功 2 · 失败 1"))
    }

    @Test
    fun `failure reason is carried into the report`() {
        val t = CommandTrace()
        t.record(entry(command = "Pause", acked = false, failure = "设备里没有可播放的内容"))
        assertTrue(t.render().contains("失败：设备里没有可播放的内容"))
    }

    @Test
    fun `successful entries are kept too`() {
        // 只记失败的话，「没反应」和「根本没触发」分不开
        val t = CommandTrace()
        t.record(entry(command = "SetVolume(45)", acked = true))
        val text = t.render()
        assertTrue(text.contains("SetVolume(45)"))
        assertTrue(text.contains("→ OK"))
    }

    @Test
    fun `device ip is shown so multi device issues are locatable`() {
        val t = CommandTrace()
        t.record(entry(ip = "192.168.99.181"))
        assertTrue(t.render().contains("@192.168.99.181"))
    }

    @Test
    fun `consecutive failures are called out as a broken link`() {
        // 连续失败 = 整条链路断了（设备离线/地址变了），和偶发一条失败是两回事
        val t = CommandTrace()
        repeat(3) { t.record(entry(acked = false, failure = "设备没有响应")) }
        assertTrue(t.render().contains("连续失败 3 次"))
    }

    @Test
    fun `a success breaks the failure streak`() {
        val t = CommandTrace()
        t.record(entry(acked = false))
        t.record(entry(acked = false))
        t.record(entry(acked = true))
        assertEquals(0, t.consecutiveFailures())
        assertFalse(t.render().contains("连续失败"))
    }

    @Test
    fun `two failures are not yet a broken link`() {
        // 阈值 3：两次可能是偶发（比如正好在切输入源）
        val t = CommandTrace()
        repeat(2) { t.record(entry(acked = false)) }
        assertEquals(2, t.consecutiveFailures())
        assertFalse(t.render().contains("连续失败"))
    }

    @Test
    fun `ring keeps only the newest entries`() {
        val t = CommandTrace(capacity = 3)
        (1..5).forEach { t.record(entry(command = "Cmd$it")) }
        val cmds = t.recent().map { it.command }
        assertEquals(listOf("Cmd5", "Cmd4", "Cmd3"), cmds)
    }

    @Test
    fun `total count keeps counting after the ring wraps`() {
        // 统计要反映真实下发量，不能被环形容量截断
        val t = CommandTrace(capacity = 3)
        repeat(10) { t.record(entry()) }
        assertEquals(10, t.totalCount)
    }

    @Test
    fun `average elapsed is over the retained window`() {
        val t = CommandTrace()
        t.record(entry(elapsed = 40))
        t.record(entry(elapsed = 80))
        assertEquals(60L, t.averageElapsedMs())
    }

    @Test
    fun `recent is newest first`() {
        val t = CommandTrace()
        t.record(entry(command = "first", at = 1))
        t.record(entry(command = "second", at = 2))
        assertEquals("second", t.recent().first().command)
    }

    @Test
    fun `command labels are readable not class names`() {
        // data class 的 toString 带全限定名，太长且看不懂
        assertEquals("Play", CommandTrace.labelOf(RemoteCommand.Play))
        assertEquals("SetVolume(45)", CommandTrace.labelOf(RemoteCommand.SetVolume(45)))
        assertEquals("Seek(125s)", CommandTrace.labelOf(RemoteCommand.Seek(125)))
        assertEquals("SelectInput(wifi)", CommandTrace.labelOf(RemoteCommand.SelectInput("wifi")))
        assertEquals("Preset(2)", CommandTrace.labelOf(RemoteCommand.Preset(2)))
        assertFalse(CommandTrace.labelOf(RemoteCommand.Play).contains("RemoteCommand"))
    }

    @Test
    fun `clear resets counts too`() {
        val t = CommandTrace()
        t.record(entry(acked = false))
        t.clear()
        assertEquals(0, t.totalCount)
        assertEquals(0, t.failedCount)
        assertTrue(t.render().contains("还没有下发过指令"))
    }
}
