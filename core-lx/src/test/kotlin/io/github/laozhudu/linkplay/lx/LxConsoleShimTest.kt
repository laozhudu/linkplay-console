package io.github.laozhudu.linkplay.lx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * console 垫片：真机 0.9.110 报的 "you should be set a stdout of platform to console.stdout"
 * 就是 preload 最后那一句 `console.log('Preload finished.')` 打出来的。
 */
class LxConsoleShimTest {

    @Test
    fun `shim replaces every console method the engine may call`() {
        listOf("log", "info", "warn", "error", "debug", "trace").forEach { name ->
            assertTrue("垫片必须顶掉 console.$name", LxConsoleShim.SCRIPT.contains("$name:"))
        }
    }

    @Test
    fun `shim keeps a tail buffer so logs are not lost`() {
        assertTrue(LxConsoleShim.SCRIPT.contains("__lx_console_tail"))
        assertTrue(LxConsoleShim.SCRIPT.contains("lines.shift()"))
    }

    @Test
    fun `shim touches globalThis and nothing else`() {
        // preload 会冻结 globalThis，所以垫片只往 g 上挂东西，不定义全局变量
        assertTrue(LxConsoleShim.SCRIPT.contains("typeof globalThis !== 'undefined' ? globalThis : this"))
        assertTrue(LxConsoleShim.SCRIPT.contains("g.console ="))
    }

    @Test
    fun `sdk loads console shim before the bundle`() {
        val engine = FakeEngine()
        val sdk = LxMusicSdk(engine, "// bundle")
        sdk.load()

        val order = engine.evaluated.map { it.first }
        assertEquals("lx-console-shim.js", order.first())
        assertTrue(order.indexOf("lx-console-shim.js") < order.indexOf("lx-music-sdk.bundle.js"))
    }
}
