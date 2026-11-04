package io.github.laozhudu.linkplay.console

import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 锁"搜到了点不动"时报告里那几句话 —— 因为这三步（搜 / 取流 / 投送）
 * 走的是**三条不同的链路**，混成一句"失败"就定位不了。
 */
class LxSourceStatusResolveTest {

    @Before
    fun setUp() = LxSourceStatus.reset()

    @Test
    fun `resolve success shows the track, the source and the cost`() {
        LxSourceStatus.searched("wy", "周杰伦", 5, 400)
        LxSourceStatus.resolveOk("wy", "七里香", 620)

        val text = LxSourceStatus.render()
        assertTrue(text.contains("取流=「七里香」@wy"))
        assertTrue(text.contains("620ms"))
    }

    @Test
    fun `resolve failure is reported as the script's problem`() {
        LxSourceStatus.resolveFailed("七里香", "脚本还没初始化成功")

        val text = LxSourceStatus.render()
        assertTrue(text.contains("取流=失败"))
        assertTrue(text.contains("脚本还没初始化成功"))
    }

    @Test
    fun `cast failure is attributed to the device, not the script`() {
        LxSourceStatus.resolveOk("wy", "七里香", 500)
        LxSourceStatus.cast(false, "七里香")

        val text = LxSourceStatus.render()
        // 地址拿到了但设备不播 —— 这句归因很重要，否则用户会去反复换脚本
        assertTrue(text.contains("设备没起播"))
        assertTrue(text.contains("设备侧问题，不是脚本"))
    }

    @Test
    fun `cast success is recorded too`() {
        LxSourceStatus.cast(true, "七里香")
        assertTrue(LxSourceStatus.render().contains("已投送「七里香」"))
    }
}
