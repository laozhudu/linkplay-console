package io.github.laozhudu.linkplay.console.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 报告送进 logcat 时的切块规则。
 *
 * 为什么要测：`logcat` 单条消息有长度上限（各版本 4KB 上下），
 * 一份十五段的报告远超这个数。整条丢进去只会被截断 ——
 * 表现是"报告只有前半截"，而看的人会以为是诊断自己少了段。
 *
 * 切块逻辑本身不碰 `android.util.Log`，所以能在纯 JVM 单测里验证。
 */
class AppDiagnosticsLogSplitTest {

    @Test
    fun `short report stays one chunk`() {
        val chunks = AppDiagnostics.splitForLog("第一行\n第二行\n", maxChars = 100)
        assertEquals(1, chunks.size)
        assertTrue(chunks[0].contains("第二行"))
    }

    @Test
    fun `long report is split and every chunk fits the limit`() {
        val text = (1..50).joinToString("") { "第 $it 行，内容占一点长度\n" }
        val chunks = AppDiagnostics.splitForLog(text, maxChars = 120)

        assertTrue("50 行不可能塞进一块", chunks.size > 1)
        chunks.forEach { assertTrue("块超长会被 logcat 截断：${it.length}", it.length <= 120) }
    }

    @Test
    fun `no line is cut in half`() {
        val text = "AAAA\nBBBB\nCCCC\nDDDD\n"
        val chunks = AppDiagnostics.splitForLog(text, maxChars = 12)

        // 每块都应以换行结尾（= 不在行中间断开），否则 `adb logcat` 读出来是碎句
        chunks.forEach { assertTrue(it.endsWith("\n")) }
        assertEquals(text, chunks.joinToString(""))
    }

    @Test
    fun `a single overlong line gets its own chunk instead of looping forever`() {
        val long = "X".repeat(500)
        val chunks = AppDiagnostics.splitForLog("短行\n$long\n短行\n", maxChars = 100)

        assertTrue("超长行必须独占一块", chunks.any { it.contains(long) })
        assertEquals("拆开再拼回去要和原文一致", "短行\n$long\n短行\n", chunks.joinToString(""))
    }

    @Test
    fun `empty text produces no chunks`() {
        assertTrue(AppDiagnostics.splitForLog("").isEmpty())
    }
}
