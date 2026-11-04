package io.github.laozhudu.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 报告格式 —— 锁死"截图能截到关键信息"这件事。
 *
 * 判据不是"好不好看"，而是：**只看这段文本能不能定位问题**。
 */
class DiagnosticReportTest {

    private val header = DiagnosticReport.Header(
        appName = "声控台",
        version = "0.9.60（60）",
        atText = "2026-11-04 02:30:00",
    )

    private fun entry(
        atMs: Long = 1_000L,
        source: String = "playQueueItem",
        severity: Severity = Severity.ERROR,
        message: String = "IOException: HTTP 500",
    ) = DiagnosticEntry(atMs, source, severity, message)

    @Test
    fun `no crash says so instead of printing null`() {
        val text = DiagnosticReport.build(header, null, "", emptyList())
        assertTrue(text.contains("无（本次启动未捕获到崩溃）"))
        assertFalse(text.contains("null"))
    }

    @Test
    fun `crash stack is trimmed so it fits a screenshot`() {
        val stack = (1..40).joinToString("\n") { "  at frame$it(Foo.kt:$it)" }
        val text = DiagnosticReport.build(header, "异常：java.lang.NullPointerException\n$stack", "", emptyList())
        assertTrue(text.contains("NullPointerException"))
        assertTrue(text.contains("frame1"))
        assertFalse(text.contains("frame30"))
    }

    @Test
    fun `severity counts appear in the issues header`() {
        // 一眼看出"是硬错误还是噪声"，不用逐行读 200 条
        val text = DiagnosticReport.build(
            header, null, "",
            listOf(
                entry(severity = Severity.ERROR),
                entry(severity = Severity.WARN),
                entry(severity = Severity.WARN),
            ),
        )
        assertTrue(text.contains("运行期问题 3 条（新→旧）｜E1 W2 I0"))
    }

    @Test
    fun `entry line carries time severity and source`() {
        val text = DiagnosticReport.build(header, null, "", listOf(entry(source = "playQueueItem")))
        assertTrue(text.contains("[E] [playQueueItem] IOException: HTTP 500"))
    }

    @Test
    fun `entries are capped with a remainder note`() {
        val entries = (1..35).map { entry(message = "issue-$it") }
        val text = DiagnosticReport.build(header, null, "", entries)
        assertTrue(text.contains("issue-1"))
        assertTrue(text.contains("… 另有 5 条"))
    }

    @Test
    fun `empty entries still print the section`() {
        val text = DiagnosticReport.build(header, null, "", emptyList())
        assertTrue(text.contains("运行期问题 0 条"))
    }

    @Test
    fun `body is inserted between crash and entries`() {
        val text = DiagnosticReport.build(header, null, "[设备]\n  doss1  192.168.99.164", emptyList())
        assertTrue(text.indexOf("崩溃记录") < text.indexOf("[设备]"))
        assertTrue(text.indexOf("[设备]") < text.indexOf("运行期问题"))
    }

    @Test
    fun `blank body is omitted entirely`() {
        val text = DiagnosticReport.build(header, null, "   ", emptyList())
        assertFalse(text.contains("\n\n\n\n"))
    }

    @Test
    fun `extra header lines are printed`() {
        val text = DiagnosticReport.build(
            header.copy(extra = listOf("运行时长：2h13m", "设备：3 台")), null, "", emptyList(),
        )
        assertTrue(text.contains("运行时长：2h13m"))
        assertTrue(text.contains("设备：3 台"))
    }

    @Test
    fun `labels can be swapped for another language`() {
        // 通用模块：别的项目/别的语言不该改代码
        val text = DiagnosticReport.build(
            header, null, "", emptyList(),
            DiagnosticReport.Options(labels = DiagnosticReport.Labels(title = "Report", issues = "Issues")),
        )
        assertTrue(text.contains("=== 声控台 Report ==="))
        assertTrue(text.contains("Issues 0 条"))
    }

    @Test
    fun `clock text is zero padded`() {
        // 1_000 ms = 00:00:01 UTC；时区差异不影响"至少有时分秒三段"
        val text = DiagnosticReport.clockText(1_000L)
        assertEquals(3, text.split(":").size)
        assertEquals(2, text.split(":")[1].length)
    }

    /** 打印一份样例，让人看见用户手机上会看到什么。 */
    @Test
    fun `sample report is readable`() {
        val text = DiagnosticReport.build(
            header.copy(extra = listOf("运行时长：2h13m")),
            crashText = "异常：java.lang.NullPointerException\n" +
                "  at io.github.laozhudu.linkplay.console.ConsoleViewModel.applySnapshot(ConsoleViewModel.kt:620)",
            body = "[设备]\n  doss1  192.168.99.164\n    状态=在线  能力=LINKPLAY\n    取数通道=CONTROL_INFO+PLAYER_STATUS\n",
            entries = listOf(
                entry(1_000L, "playQueueItem", Severity.ERROR, "IOException: HTTP 500 from 192.168.99.153: 401 invalid action"),
                entry(2_000L, "probeSaved", Severity.WARN, "设备未应答，保留上次状态"),
                entry(3_000L, "getStatusEx", Severity.INFO, "完成 69ms"),
            ),
        )
        println("\n----- 样例报告 -----\n$text----- 样例结束 -----\n")
        assertTrue(text.contains("doss1"))
    }
}
