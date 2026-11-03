package io.github.laozhudu.linkplay.console.diagnostics

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 全 App 诊断注册表。
 *
 * 判据不是"能不能注册"，而是三条会真实发生的坑：
 * 1. 模块顺序要稳定（截图每次位置都变就没法对照）
 * 2. 某个 provider 抛异常不能拖垮整份报告（诊断工具自己崩比被诊断的功能崩更难查）
 * 3. 注销要真生效（PlaybackViewModel 切设备会重建，旧实例残留会显示上一台的状态）
 */
class DiagnosticsHubTest {

    @After
    fun tearDown() = DiagnosticsHub.clear()

    @Test
    fun `sections render in registration order`() {
        DiagnosticsHub.register("b", "第二", { "  内容B" })
        DiagnosticsHub.register("a", "第一", { "  内容A" })

        val text = DiagnosticsHub.render()

        assertTrue(text.indexOf("[第二]") < text.indexOf("[第一]"))
        assertTrue(text.contains("内容B"))
    }

    @Test
    fun `re-registering same id replaces content and keeps position`() {
        DiagnosticsHub.register("x", "设备", { "  旧" })
        DiagnosticsHub.register("y", "队列", { "  队列" })
        DiagnosticsHub.register("x", "设备", { "  新" })

        val text = DiagnosticsHub.render()

        assertTrue(text.contains("新"))
        assertTrue(!text.contains("旧"))
        // 位置不变：设备仍在队列之前
        assertTrue(text.indexOf("[设备]") < text.indexOf("[队列]"))
    }

    @Test
    fun `unregister removes the section`() {
        DiagnosticsHub.register("playback", "播放", { "  在放" })
        DiagnosticsHub.unregister("playback")
        assertTrue(!DiagnosticsHub.render().contains("播放"))
    }

    @Test
    fun `a throwing provider does not break the whole report`() {
        DiagnosticsHub.register("bad", "坏的", { error("boom") })
        DiagnosticsHub.register("good", "好的", { "  正常" })

        val text = DiagnosticsHub.render()

        assertTrue(text.contains("渲染失败"))
        assertTrue(text.contains("boom"))
        // 后面的段照常渲染 —— 这是这个用例的全部意义
        assertTrue(text.contains("正常"))
    }

    @Test
    fun `blank section says 无 instead of printing nothing`() {
        DiagnosticsHub.register("empty", "空的", { "" })
        assertTrue(DiagnosticsHub.render().contains("无"))
    }

    @Test
    fun `clear resets order so tests do not leak into each other`() {
        DiagnosticsHub.register("a", "A", { "x" })
        DiagnosticsHub.clear()
        assertEquals(0, DiagnosticsHub.sections().size)
    }

    @Test
    fun `report uses hub sections and skips the legacy device table`() {
        DiagnosticsHub.register("devices", "设备", { "  doss3  192.168.99.153\n    电量=50%" })

        val text = DiagnosticsReport.build(
            version = "0.9.53（53）",
            now = "t",
            crash = null,
            devices = emptyList(),
            issues = emptyList(),
            sections = DiagnosticsHub.render(),
        )

        assertTrue(text.contains("声控台 诊断报告"))
        assertTrue(text.contains("[设备]"))
        assertTrue(text.contains("电量=50%"))
        // 不再重复画"[设备 0 台]"那张老表
        assertTrue(!text.contains("[设备 0 台]"))
    }
}
