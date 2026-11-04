package io.github.laozhudu.linkplay.console.floating

import io.github.laozhudu.linkplay.console.diagnostics.SourceFailure
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 悬浮窗诊断段的排版。
 *
 * 这一组锁的是 `docs/65` 批次 1 的最后一条验收判据：
 * 用户说「悬浮窗不显示」时，报告里要能分开
 * **没权限**（`权限=已授予  服务=未运行` 这种组合也要看得出）、
 * **有权限但加窗被拒**、**窗口 0 尺寸**。
 */
class FloatingSourceStatusTest {

    @Before
    fun setUp() = FloatingSourceStatus.reset()

    @After
    fun tearDown() = FloatingSourceStatus.reset()

    @Test
    fun `before any use says so`() {
        assertTrue(FloatingSourceStatus.render().contains("还没开过悬浮窗"))
    }

    @Test
    fun `missing permission explains the switch snapping back`() {
        FloatingSourceStatus.permission(false)
        val text = FloatingSourceStatus.render()
        assertTrue(text.contains("权限=未授予（SYSTEM_ALERT_WINDOW）"))
        // 用户能看到的现象，报告里要能对上
        assertTrue(text.contains("开关会自己弹回"))
    }

    @Test
    fun `granted permission and running service show geometry`() {
        FloatingSourceStatus.permission(true)
        FloatingSourceStatus.started("192.168.99.164")
        FloatingSourceStatus.added(600, 168, 720, 400, "192.168.99.164")

        val text = FloatingSourceStatus.render()
        assertTrue(text.contains("权限=已授予"))
        assertTrue(text.contains("服务=运行中"))
        assertTrue(text.contains("窗口=600×168px @ (720, 400)"))
        assertTrue(text.contains("目标设备=192.168.99.164"))
        assertEquals(true, FloatingSourceStatus.snapshot().added)
    }

    @Test
    fun `zero size window is called out even though addView succeeded`() {
        FloatingSourceStatus.permission(true)
        FloatingSourceStatus.started("192.168.99.164")
        FloatingSourceStatus.added(0, 0, 0, 0, "192.168.99.164")

        val s = FloatingSourceStatus.snapshot()
        assertEquals("加了，但没东西可看", true, s.added)
        assertTrue(s.failure!!.contains("0 尺寸"))
    }

    @Test
    fun `permission true but addView rejected is a separate failure`() {
        FloatingSourceStatus.permission(true)
        // 用普通异常代替 BadTokenException：纯 JVM 单测里 android 异常类不可构造
        FloatingSourceStatus.addFailed(IllegalStateException("BadTokenException: permission denied"))

        val text = FloatingSourceStatus.render()
        assertTrue("权限显示已授予，所以失败必须单独记", text.contains("权限=已授予"))
        assertTrue(text.contains("加窗被拒"))
    }

    @Test
    fun `user stop is not a failure`() {
        FloatingSourceStatus.permission(true)
        FloatingSourceStatus.started("192.168.99.164")
        FloatingSourceStatus.added(600, 168, 720, 400, "192.168.99.164")
        FloatingSourceStatus.stopped("用户关闭")

        val s = FloatingSourceStatus.snapshot()
        assertEquals(false, s.started)
        assertEquals("用户主动关掉不该报成问题", null, s.failure)
        assertTrue(FloatingSourceStatus.render().contains("关（用户关闭）"))
    }

    @Test
    fun `permission denied is recorded as a classified failure`() {
        FloatingSourceStatus.failed(SourceFailure.PERMISSION_DENIED, "没有 SYSTEM_ALERT_WINDOW 权限")
        assertTrue(FloatingSourceStatus.snapshot().failure!!.startsWith("没权限"))
    }
}
