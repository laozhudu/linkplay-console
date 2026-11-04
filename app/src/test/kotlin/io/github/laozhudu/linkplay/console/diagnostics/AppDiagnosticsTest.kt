package io.github.laozhudu.linkplay.console.diagnostics

import io.github.laozhudu.diagnostics.Severity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 装配测试 —— 只验"零件接对了"，语义本身由 `core-diagnostics` 的测试覆盖。
 *
 * 分开的理由：模块测试要能在没有 Android 的环境跑（它证明可复用）；
 * 这里只验"本 App 把 sink 接到了 log、把段接到了注册表"这类接线。
 */
class AppDiagnosticsTest {

    @After
    fun tearDown() {
        AppDiagnostics.log.clear()
        AppDiagnostics.sections.clear()
    }

    @Test
    fun `section render failure lands in the log`() {
        // 诊断自己崩了必须留痕，否则"报告少了一段"没人知道为什么
        AppDiagnostics.sections.register("bad", "坏的") { error("boom") }
        AppDiagnostics.sections.render()
        val e = AppDiagnostics.recent().single()
        assertEquals(Severity.ERROR, e.severity)
        assertTrue(e.message.contains("boom"))
    }

    @Test
    fun `register replaces body but keeps position`() {
        AppDiagnostics.sections.register("x", "设备") { "  旧" }
        AppDiagnostics.sections.register("y", "队列") { "  队列" }
        AppDiagnostics.sections.register("x", "设备") { "  新" }
        val text = AppDiagnostics.sections.render()
        assertTrue(text.contains("新"))
        assertTrue(text.indexOf("设备") < text.indexOf("队列"))
    }

    @Test
    fun `unregister removes the section`() {
        AppDiagnostics.sections.register("playback", "播放") { "  在放" }
        AppDiagnostics.sections.unregister("playback")
        assertTrue(!AppDiagnostics.sections.render().contains("播放"))
    }
}
