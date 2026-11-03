package io.github.laozhudu.linkplay.protocol.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 设备切换代际号的回归测试。
 *
 * 锁死的是这条真实故障：换设备后上一台设备的在飞请求仍然写状态，
 * 界面出现「先报错串台、一会儿又自己好」。
 */
class DeviceGenerationTest {

    @Test
    fun `token taken before a switch is stale afterwards`() {
        val gen = DeviceGeneration()
        val token = gen.current()          // 在 .164 上发起请求
        gen.bump()                         // 用户切到 .153
        assertTrue("切设备后旧请求必须作废", gen.isStale(token))
    }

    @Test
    fun `token stays valid without a switch`() {
        val gen = DeviceGeneration()
        val token = gen.current()
        assertFalse(gen.isStale(token))
    }

    @Test
    fun `token taken after the switch is valid`() {
        val gen = DeviceGeneration()
        gen.bump()
        val token = gen.current()
        assertFalse("切换之后发出的请求属于新设备，不能用旧票判死", gen.isStale(token))
    }

    @Test
    fun `every bump invalidates the previous token`() {
        val gen = DeviceGeneration()
        val first = gen.current()
        gen.bump()
        val second = gen.current()
        gen.bump()
        assertTrue(gen.isStale(first))
        assertTrue(gen.isStale(second))
        assertEquals(2, gen.current())
    }

    @Test
    fun `instances are independent`() {
        val a = DeviceGeneration()
        val b = DeviceGeneration()
        val tokenA = a.current()
        b.bump()
        assertFalse("两台设备各自一个代际号，互不影响", a.isStale(tokenA))
    }
}
