package io.github.laozhudu.linkplay.console.ir

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 锁 NEC 帧的时序。写错一个数，设备就是"没反应"，
 * 而红外没有回执 —— 真机上永远分不清是码错了还是设备不支持。
 */
class NecEncoderTest {

    @Test
    fun `one frame has the expected number of segments`() {
        val pattern = NecEncoder.encode(address = 0x04, command = 0x08)
        assertEquals(NecEncoder.BUFFER_SIZE, pattern.size)
        assertEquals(68, pattern.size) // 头 2 + 32 位 × 2 + 尾 2
    }

    @Test
    fun `frame starts with the NEC header and ends with a space`() {
        val pattern = NecEncoder.encode(0x04, 0x08)
        assertEquals(9_000, pattern[0])
        assertEquals(4_500, pattern[1])
        // Android 的语义：偶数是 mark、奇数是 space；必须以 space 结尾，
        // 否则接收端会把下一帧的开头吞进上一次
        assertTrue("数组必须以 space 结尾（长度是偶数）", pattern.size % 2 == 0)
        assertEquals(40_000, pattern.last())
    }

    @Test
    fun `zero bits use the short space, one bits use the long one`() {
        // 只看第一个字节（地址）的 8 位：下标 2 起，每 2 段一位，space 在奇数位
        val zeros = NecEncoder.encode(address = 0x00, command = 0x00)
        for (i in 3..17 step 2) assertEquals("地址 0x00 第 $i 段", 560, zeros[i])

        val ones = NecEncoder.encode(address = 0xFF, command = 0x00)
        for (i in 3..17 step 2) assertEquals("地址 0xFF 第 $i 段", 1_690, ones[i])
    }

    @Test
    fun `inverted bytes are included`() {
        // 地址 0x04 → 反码 0xFB；命令 0x08 → 0xF7。反码错一位，设备就不认
        val pattern = NecEncoder.encode(0x04, 0x08)
        // 第 8~15 段是反地址的第一位区（LSB 先出），0xFB 的最低位是 1 → 长间隔
        // 位区从下标 2 开始：每 2 段一位
        val bitIndex = 8 // 第 9 位（反地址的第 0 位）
        val spaceIndex = 2 + bitIndex * 2 + 1
        assertEquals(1_690, pattern[spaceIndex])
    }

    @Test
    fun `repeats produce whole frames with a gap between them`() {
        val pattern = NecEncoder.encode(0x04, 0x08, repeats = 2)
        assertEquals(NecEncoder.BUFFER_SIZE * 2, pattern.size)
        // 第二帧重新以 9000 开头
        assertEquals(9_000, pattern[NecEncoder.BUFFER_SIZE])
    }

    @Test
    fun `probe code is a valid two frame NEC burst`() {
        val probe = NecEncoder.probePower()
        assertEquals(NecEncoder.BUFFER_SIZE * 2, probe.size)
        assertEquals(9_000, probe[0])
    }
}
