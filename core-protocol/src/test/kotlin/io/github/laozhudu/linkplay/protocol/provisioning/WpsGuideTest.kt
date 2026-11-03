package io.github.laozhudu.linkplay.protocol.provisioning

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * WPS 配网的判定与进度。
 *
 * 重点锁两件事：
 * 1. **找到优先于超时** —— 扫描是 2 秒一次，最后一轮的设备常常在超时判定之后才回来，
 *    先判超时就会误报「60 秒内没发现设备」，紧接着界面里设备就出现了；
 * 2. 进度必须夹在 0..1 —— Compose 的进度条拿到越界值会抛异常。
 */
class WpsGuideTest {

    @Test
    fun `just started waits`() {
        assertEquals(WpsStep.Waiting, WpsGuide.tick(elapsedMs = 0, foundCount = 0))
    }

    @Test
    fun `one millisecond before the timeout still waits`() {
        assertEquals(
            WpsStep.Waiting,
            WpsGuide.tick(elapsedMs = WpsGuide.DEFAULT_TIMEOUT_MS - 1, foundCount = 0),
        )
    }

    @Test
    fun `exactly at the timeout it times out`() {
        assertEquals(
            WpsStep.Timeout,
            WpsGuide.tick(elapsedMs = WpsGuide.DEFAULT_TIMEOUT_MS, foundCount = 0),
        )
    }

    @Test
    fun `found wins over timeout`() {
        // 超时那一刻这一轮扫描刚好返回了设备：必须报 Found，不能报 Timeout
        assertEquals(
            WpsStep.Found,
            WpsGuide.tick(elapsedMs = WpsGuide.DEFAULT_TIMEOUT_MS + 5_000, foundCount = 1),
        )
    }

    @Test
    fun `found early reports found`() {
        assertEquals(WpsStep.Found, WpsGuide.tick(elapsedMs = 6_000, foundCount = 2))
    }

    @Test
    fun `custom timeout is respected`() {
        assertEquals(WpsStep.Timeout, WpsGuide.tick(elapsedMs = 5_000, foundCount = 0, timeoutMs = 5_000))
        assertEquals(WpsStep.Waiting, WpsGuide.tick(elapsedMs = 4_999, foundCount = 0, timeoutMs = 5_000))
    }

    @Test
    fun `progress starts at zero and reaches one at the deadline`() {
        assertEquals(0f, WpsGuide.progress(0), 0.0001f)
        assertEquals(0.5f, WpsGuide.progress(WpsGuide.DEFAULT_TIMEOUT_MS / 2), 0.0001f)
        assertEquals(1f, WpsGuide.progress(WpsGuide.DEFAULT_TIMEOUT_MS), 0.0001f)
    }

    @Test
    fun `progress is clamped for values outside the window`() {
        assertEquals("超出截止时间也不能 >1", 1f, WpsGuide.progress(WpsGuide.DEFAULT_TIMEOUT_MS * 3), 0.0001f)
        assertEquals("负耗时（时钟回拨）夹到 0", 0f, WpsGuide.progress(-1_000), 0.0001f)
    }

    @Test
    fun `zero timeout means done immediately`() {
        // 除零会得到 NaN，进度条拿到 NaN 一样会崩
        assertEquals(1f, WpsGuide.progress(elapsedMs = 0, timeoutMs = 0), 0.0001f)
        assertEquals(WpsStep.Timeout, WpsGuide.tick(elapsedMs = 0, foundCount = 0, timeoutMs = 0))
    }

    @Test
    fun `cancel is a distinct state from timeout`() {
        // 用户点返回不是失败，界面上不能显示「60 秒内没发现设备」这种报错口吻
        assertEquals(4, WpsStep.entries.size)
        assertEquals(WpsStep.Cancelled, WpsStep.valueOf("Cancelled"))
    }
}
