package io.github.laozhudu.linkplay.protocol.player

import org.junit.Assert.assertEquals
import org.junit.Test

class LoopModeTest {

    @Test
    fun `枚举值就是设备取值`() {
        // 一旦有人给枚举加了 @JvmField 或改顺序，这条会拦住
        assertEquals(0, LoopMode.OFF.wire)
        assertEquals(1, LoopMode.ONE.wire)
        assertEquals(2, LoopMode.SHUFFLE.wire)
        // 负数是「顺序循环」，不是「关闭」——最容易写反的一格
        assertEquals(-1, LoopMode.SEQUENCE.wire)
    }

    @Test
    fun `未知取值退回不循环而不是抛异常`() {
        assertEquals(LoopMode.OFF, LoopMode.from(null))
        assertEquals(LoopMode.OFF, LoopMode.from(99))
        assertEquals(LoopMode.SEQUENCE, LoopMode.from(-1))
    }

    @Test
    fun `切换顺序走完一圈回到起点`() {
        var m = LoopMode.OFF
        val seen = mutableListOf(m)
        repeat(3) {
            m = LoopMode.next(m)
            seen += m
        }
        assertEquals(listOf(LoopMode.OFF, LoopMode.SEQUENCE, LoopMode.ONE, LoopMode.SHUFFLE), seen)
        assertEquals(LoopMode.OFF, LoopMode.next(m))
    }
}
