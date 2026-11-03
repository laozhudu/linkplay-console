package io.github.laozhudu.linkplay.protocol.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 设备侧队列现状。
 *
 * 真机样本（`.153`，2026-11-03，整列表推给设备之后）：
 * `mode=20 status=play plicount=3 plicurr=1`，播到第 3 首时 `plicurr=3`。
 * 这里用真实数值钉住**1 基与 0 基的换算** —— 这是最容易差一位的地方。
 */
class DeviceQueueStateTest {

    private fun status(count: Int?, current: Int?, state: PlayState = PlayState.PLAY) =
        PlayerStatus(
            type = 0, channel = 0, mode = 20, loopMode = 0, eq = 0,
            playState = state, positionMs = 0, durationMs = 0, volume = 50, mute = false,
            playListCount = count, playListCurrent = current,
        )

    @Test
    fun 真机样本_三首列表的第一首() {
        val q = DeviceQueueState.from(status(3, 1))
        assertEquals(3, q.count)
        assertEquals(0, q.index)          // plicurr 是 1 基，换算是 -1
        assertTrue(q.hasQueue)
        assertTrue(q.playing)
    }

    @Test
    fun 播到最后一首时索引是_二() {
        assertEquals(2, DeviceQueueState.from(status(3, 3)).index)
    }

    @Test
    fun plicurr_为零表示没有当前项_给负一() {
        assertEquals(-1, DeviceQueueState.from(status(3, 0)).index)
    }

    @Test
    fun plicurr_超出范围不乱算() {
        // 固件在切歌瞬间可能报 plicurr=4 而 plicount=3
        assertEquals(-1, DeviceQueueState.from(status(3, 4)).index)
    }

    @Test
    fun 没有列表时_hasQueue_为假() {
        val q = DeviceQueueState.from(status(0, 0, PlayState.STOP))
        assertFalse(q.hasQueue)
        assertFalse(q.playing)
    }

    @Test
    fun 空值不崩() {
        val q = DeviceQueueState.from(status(null, null))
        assertEquals(0, q.count)
        assertEquals(-1, q.index)
    }

    @Test
    fun 长度一致才算同一份列表() {
        assertTrue(DeviceQueueState.from(status(5, 1)).matches(5))
        assertFalse(DeviceQueueState.from(status(5, 1)).matches(3))
        // 设备没队列时永远不算匹配，否则会空列表冒充
        assertFalse(DeviceQueueState.from(status(0, 0)).matches(0))
    }
}
