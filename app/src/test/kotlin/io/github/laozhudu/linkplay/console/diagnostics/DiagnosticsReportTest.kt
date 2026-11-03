package io.github.laozhudu.linkplay.console.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 诊断报告格式 —— 锁死"截图能截到关键信息"这件事。
 *
 * 这些用例的判据不是"好不好看"，而是：**只看这段文本能不能定位问题**。
 * 所以每条都断言"该出现的信息出现了、不该出现的（比如 null 堆栈）被替换成人话"。
 */
class DiagnosticsReportTest {

    private fun line(
        name: String = "doss3",
        ip: String = "192.168.99.153",
        online: Boolean? = true,
        groupedUnder: String? = null,
        battery: Int? = 40,
        charging: Boolean = false,
        channel: Int? = 0,
        volume: Int = 39,
        playing: Boolean = false,
        answered: Set<String> = setOf("CONTROL_INFO", "PLAYER_STATUS"),
        profile: String = "LINKPLAY",
    ) = DiagnosticsReport.DeviceLine(
        name, ip, online, groupedUnder, battery, charging, channel, volume, playing, answered, profile,
    )

    @Test
    fun `no crash says so instead of printing null`() {
        val text = DiagnosticsReport.build("0.9.52（52）", "2026-11-04 00:10:00", null, emptyList(), emptyList())
        assertTrue(text.contains("无（本次启动未捕获到崩溃）"))
        assertTrue(!text.contains("null"))
    }

    @Test
    fun `crash stack is trimmed to the first lines so it fits a screenshot`() {
        val stack = (1..40).joinToString("\n") { "  at frame$it(Foo.kt:$it)" }
        val text = DiagnosticsReport.build("v", "t", "异常：java.lang.NullPointerException\n消息：null\n$stack", emptyList(), emptyList())
        assertTrue(text.contains("NullPointerException"))
        // 只留前 16 行：再多截图就截不全了
        assertTrue(text.contains("frame1"))
        assertTrue(!text.contains("frame30"))
    }

    @Test
    fun `device line carries battery channel and channels that answered`() {
        val text = DiagnosticsReport.build("v", "t", null, listOf(line()), emptyList())
        assertTrue(text.contains("doss3  192.168.99.153"))
        assertTrue(text.contains("状态=在线"))
        assertTrue(text.contains("能力=LINKPLAY"))
        assertTrue(text.contains("音量=39"))
        assertTrue(text.contains("声道=立体声"))
        assertTrue(text.contains("电量=40%"))
        assertTrue(text.contains("取数通道=CONTROL_INFO+PLAYER_STATUS"))
    }

    @Test
    fun `missing battery and channel are spelled out not silently dropped`() {
        // 这两个字段缺失正是之前用户报的问题，报告必须显式说"无 / 未报"
        val text = DiagnosticsReport.build("v", "t", null, listOf(line(battery = null, channel = null)), emptyList())
        assertTrue(text.contains("电量=无"))
        assertTrue(text.contains("声道=未报"))
    }

    @Test
    fun `device that answered nothing is called out`() {
        // 全通道未应答 = 这台设备基本没救，报告里必须一眼看到
        val text = DiagnosticsReport.build("v", "t", null, listOf(line(answered = emptySet())), emptyList())
        assertTrue(text.contains("取数通道=全部未应答"))
    }

    @Test
    fun `charging and grouped device are shown`() {
        val text = DiagnosticsReport.build(
            "v", "t", null,
            listOf(line(charging = true, groupedUnder = "doss1", online = null)), emptyList(),
        )
        assertTrue(text.contains("（充电）"))
        assertTrue(text.contains("属=doss1"))
        assertTrue(text.contains("状态=未探测"))
    }

    @Test
    fun `issues are newest first and capped with a remainder note`() {
        val issues = (1..35).map { "issue-$it" }
        val text = DiagnosticsReport.build("v", "t", null, emptyList(), issues)
        assertTrue(text.contains("[运行期问题 35 条（新→旧）]"))
        assertTrue(text.contains("issue-1"))
        assertTrue(text.contains("… 另有 5 条"))
    }

    /**
     * 打印一份样例（`./gradlew :app:testDebugUnitTest --tests '*DiagnosticsReport*' -i` 可见）。
     *
     * 留这个用例是为了让人**看见**用户手机上会看到什么 —— 格式类代码光靠断言容易写出
     * "测试全绿但排版一塌糊涂"的东西。
     */
    @Test
    fun `sample report is readable`() {
        val text = DiagnosticsReport.build(
            version = "0.9.52（52）",
            now = "2026-11-04 00:10:48",
            crash = "异常：java.lang.NullPointerException\n消息：Attempt to invoke virtual method on a null object reference\n" +
                "  at io.github.laozhudu.linkplay.console.ConsoleViewModel.applySnapshot(ConsoleViewModel.kt:620)\n" +
                "  at io.github.laozhudu.linkplay.console.ConsoleViewModel.access\$applySnapshot(ConsoleViewModel.kt:1)",
            devices = listOf(
                line(name = "doss1", ip = "192.168.99.164", battery = 100, charging = true, channel = 2, volume = 34),
                line(
                    name = "doss3", ip = "192.168.99.153", groupedUnder = "doss1",
                    battery = 50, channel = 1, volume = 39, playing = true,
                    answered = setOf("PLAYER_STATUS"),
                ),
                line(name = "E900V22C-175[DLNA]", ip = "192.168.99.175", battery = null, channel = null, profile = "GENERIC_DMR", answered = emptySet()),
            ),
            issues = listOf(
                "00:09:12 [ConsoleViewModel] IOException: HTTP 500 from 192.168.99.153: 401 invalid action",
                "00:09:40 [PlaybackViewModel] IllegalStateException: 设备没有 RenderingControl 服务，无法调音量",
            ),
        )
        println("\n----- 样例报告 -----\n$text----- 样例结束 -----\n")
        assertTrue(text.contains("doss3"))
    }

    @Test
    fun `channel text covers all four states`() {
        assertEquals("未报", DiagnosticsReport.channelText(null))
        assertEquals("立体声", DiagnosticsReport.channelText(0))
        assertEquals("仅左", DiagnosticsReport.channelText(1))
        assertEquals("仅右", DiagnosticsReport.channelText(2))
        assertEquals("未知(9)", DiagnosticsReport.channelText(9))
    }
}
