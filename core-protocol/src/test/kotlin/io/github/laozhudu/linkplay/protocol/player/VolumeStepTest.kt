package io.github.laozhudu.linkplay.protocol.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 音量键换算。
 *
 * 用户要求「音量与手机音量键绑定」。这里钉住三件容易写错的事：
 * 步长、上下界、到界之后还要不要继续吞事件。
 */
class VolumeStepTest {

    @Test
    fun 加一档减一档的步长() {
        assertEquals(55, VolumeStep.next(50, 1))
        assertEquals(45, VolumeStep.next(50, -1))
    }

    @Test
    fun 上限封顶到一百() {
        assertEquals(100, VolumeStep.next(98, 1))
        assertEquals(100, VolumeStep.next(100, 1))
    }

    @Test
    fun 下限封到零() {
        assertEquals(0, VolumeStep.next(3, -1))
        assertEquals(0, VolumeStep.next(0, -1))
    }

    @Test
    fun 当前值未知时返回_null_交给系统处理() {
        assertNull(VolumeStep.next(null, 1))
        assertFalse(VolumeStep.shouldConsume(null, 1))
    }

    @Test
    fun 越界的当前值先被夹住() {
        assertEquals(100, VolumeStep.next(999, 0))
        assertEquals(0, VolumeStep.next(-20, 0))
    }

    @Test
    fun 到顶或到底后继续按仍然吞事件_避免突然改手机音量() {
        assertTrue(VolumeStep.shouldConsume(100, 1))    // 到顶了也吞：否则手机音量突然开始动
        assertTrue(VolumeStep.shouldConsume(100, -1))
        assertTrue(VolumeStep.shouldConsume(0, -1))
        assertTrue(VolumeStep.shouldConsume(0, 1))
    }

    @Test
    fun 中间值一律吞事件() {
        assertTrue(VolumeStep.shouldConsume(50, 1))
        assertTrue(VolumeStep.shouldConsume(50, -1))
    }
}
