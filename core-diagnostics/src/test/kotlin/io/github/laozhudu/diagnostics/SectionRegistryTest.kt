package io.github.laozhudu.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SectionRegistryTest {

    @Test
    fun `sections render in registration order`() {
        val r = SectionRegistry()
        r.register("a", "第一") { "  内容A" }
        r.register("b", "第二") { "  内容B" }
        val text = r.render()
        assertTrue(text.indexOf("第一") < text.indexOf("第二"))
    }

    @Test
    fun `re-register replaces body but keeps position`() {
        // 切设备时播放模块会重建并重注册：位置不能跳，否则截图前后对不上
        val r = SectionRegistry()
        r.register("x", "设备") { "  旧" }
        r.register("y", "队列") { "  队列" }
        r.register("x", "设备") { "  新" }
        val text = r.render()
        assertTrue(text.contains("新"))
        assertFalse(text.contains("旧"))
        assertTrue(text.indexOf("设备") < text.indexOf("队列"))
    }

    @Test
    fun `unregister removes it`() {
        val r = SectionRegistry()
        r.register("playback", "播放") { "  在放" }
        r.unregister("playback")
        assertFalse(r.render().contains("播放"))
    }

    @Test
    fun `one broken section does not kill the report`() {
        val r = SectionRegistry()
        r.register("bad", "坏的") { error("boom") }
        r.register("good", "好的") { "  正常" }
        val text = r.render()
        assertTrue(text.contains("渲染失败"))
        assertTrue(text.contains("正常"))
    }

    @Test
    fun `broken section is reported to the sink`() {
        // 诊断自己崩了必须留痕，否则"报告少了一段"没人知道为什么
        val seen = mutableListOf<DiagnosticEntry>()
        val r = SectionRegistry { seen.add(it) }
        r.register("bad", "坏的") { error("boom") }
        r.render()
        assertEquals(Severity.ERROR, seen.single().severity)
        assertTrue(seen.single().message.contains("boom"))
    }

    @Test
    fun `blank section renders as none`() {
        val r = SectionRegistry()
        r.register("empty", "空的") { "" }
        assertTrue(r.render().contains("无"))
    }

    @Test
    fun `worst aggregates section severity`() {
        val r = SectionRegistry()
        r.register("a", "A", Severity.INFO) { "x" }
        r.register("b", "B", Severity.WARN) { "y" }
        assertEquals(Severity.WARN, r.worst())
    }

    @Test
    fun `two registries are independent`() {
        // 第一版是 object，两个用例互相污染。这条锁死"实例隔离"
        val a = SectionRegistry()
        val b = SectionRegistry()
        a.register("only-a", "A") { "x" }
        assertEquals(0, b.sections().size)
    }

    @Test
    fun `clear resets order too`() {
        val r = SectionRegistry()
        r.register("a", "A") { "x" }
        r.clear()
        assertEquals(0, r.sections().size)
    }
}
