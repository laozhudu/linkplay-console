package io.github.laozhudu.linkplay.console.playback.endpoint

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 统一状态视图的边界。
 *
 * ## 为什么要测这个（不是凑数）
 *
 * `progressFraction` 和 `hasProgress` 现在被**三种后端**共用。
 * 一个除零或越界，三种后端会同时出错 —— 而这类错在界面上表现为
 * "进度条卡住"或"进度条超出头"，很难反推是哪一层算的。
 */
class EndpointStateTest {

    @Test
    fun `直播流没有总时长时不画进度条`() {
        // 直播流的 durationSec 是 0（协议里就没有这个值，不是"还没加载完"）
        val live = EndpointState(positionSec = 120, durationSec = 0)

        assertFalse("durationSec 为 0 时不该有进度条", live.hasProgress)
        assertEquals("不能除零", 0f, live.progressFraction, 0.0001f)
    }

    @Test
    fun `位置超过总时长时进度条钉在 100%`() {
        // 设备回报的 position 暂时大于 duration 是真会发生的
        // （换曲瞬间设备先报旧位置、再报新时长）
        val over = EndpointState(positionSec = 300, durationSec = 240)

        assertEquals(1f, over.progressFraction, 0.0001f)
    }

    @Test
    fun `正常进度按比例算`() {
        val half = EndpointState(positionSec = 120, durationSec = 240)

        assertTrue(half.hasProgress)
        assertEquals(0.5f, half.progressFraction, 0.0001f)
    }

    @Test
    fun `负数位置当作 0，不返回负进度`() {
        // 有些设备在换曲瞬间报 -1
        val neg = EndpointState(positionSec = -1, durationSec = 240)

        assertEquals(0f, neg.progressFraction, 0.0001f)
    }
}
