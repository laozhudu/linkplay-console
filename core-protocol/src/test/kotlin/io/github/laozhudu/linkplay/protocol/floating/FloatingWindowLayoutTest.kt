package io.github.laozhudu.linkplay.protocol.floating

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 悬浮窗位置策略。
 *
 * 这些边界（拖出屏幕、转屏后旧坐标越界、窗口比屏幕还宽）在真机上要复现得靠运气，
 * 而每一条出错的表现都很致命：窗口跑到屏幕外 = 功能看起来完全坏了，且用户没法点回来。
 */
class FloatingWindowLayoutTest {

    /** 常见竖屏尺寸，窗宽 200×56。 */
    private val screenW = 1080
    private val screenH = 1920
    private val size = 200 to 56

    private fun spec(x: Int, y: Int) = FloatSpec(x, y, size.first, size.second)

    @Test
    fun `position already inside the screen is untouched`() {
        val out = FloatingLayout.clamp(spec(300, 800), screenW, screenH)
        assertEquals(spec(300, 800), out)
    }

    @Test
    fun `dragging past the left and top edge keeps an 8px gutter`() {
        val out = FloatingLayout.clamp(spec(-500, -500), screenW, screenH)
        assertEquals("左边留白", FloatingLayout.EDGE, out.x)
        assertEquals("上边留白", FloatingLayout.EDGE, out.y)
    }

    @Test
    fun `dragging past the bottom right edge is clamped back inside`() {
        val out = FloatingLayout.clamp(spec(5_000, 5_000), screenW, screenH)
        assertEquals("右边只留 8px", screenW - size.first - FloatingLayout.EDGE, out.x)
        assertEquals("下边只留 8px", screenH - size.second - FloatingLayout.EDGE, out.y)
    }

    @Test
    fun `window wider than the screen is shrunk to fit`() {
        val out = FloatingLayout.clamp(FloatSpec(0, 0, 4_000, 3_000), screenW, screenH)
        assertEquals(screenW - 2 * FloatingLayout.EDGE, out.width)
        assertEquals(screenH - 2 * FloatingLayout.EDGE, out.height)
        assertTrue("缩小后仍然完整在屏内", out.x + out.width + FloatingLayout.EDGE <= screenW)
    }

    @Test
    fun `snap pulls a window on the left half to the left edge`() {
        val out = FloatingLayout.snap(spec(100, 600), screenW, screenH)
        assertEquals(FloatingLayout.EDGE, out.x)
        assertEquals("竖直位置不吸附", 600, out.y)
    }

    @Test
    fun `snap pulls a window on the right half to the right edge`() {
        val out = FloatingLayout.snap(spec(700, 600), screenW, screenH)
        assertEquals(screenW - size.first - FloatingLayout.EDGE, out.x)
        assertEquals(600, out.y)
    }

    @Test
    fun `snap leaves an already docked window where it is`() {
        val left = FloatSpec(FloatingLayout.EDGE, 400, size.first, size.second)
        assertEquals(left, FloatingLayout.snap(left, screenW, screenH))
        val right = FloatSpec(screenW - size.first - FloatingLayout.EDGE, 400, size.first, size.second)
        assertEquals(right, FloatingLayout.snap(right, screenW, screenH))
    }

    @Test
    fun `snap of an off-screen window uses the clamped centre not the raw one`() {
        // 手指把窗口一路拖到屏幕外（x=3000）：不先夹回的话中心点算在右边但其实已在屏外，
        // 更糟的是负坐标会被判成左半边 —— 用户往右拖、窗口却跳到左边。
        assertEquals(
            "往右拖出屏幕 → 吸右边缘",
            screenW - size.first - FloatingLayout.EDGE,
            FloatingLayout.snap(spec(3_000, 600), screenW, screenH).x,
        )
        assertEquals(
            "往左拖出屏幕 → 吸左边缘",
            FloatingLayout.EDGE,
            FloatingLayout.snap(spec(-3_000, 600), screenW, screenH).x,
        )
    }

    @Test
    fun `rotating the screen clamps the stale coordinates`() {
        // 竖屏右下角：x=900,y=1800
        val before = spec(900, 1800)
        val rotated = FloatingLayout.clamp(before, 1920, 1080)
        assertEquals("横屏宽够，x 不动", 900, rotated.x)
        assertEquals("横屏高只有 1080，y 必须夹回", 1080 - size.second - FloatingLayout.EDGE, rotated.y)
        assertTrue(rotated.y + rotated.height <= 1080)
    }

    @Test
    fun `drag accumulates offsets and stays inside`() {
        val start = spec(400, 400)
        val moved = FloatingLayout.onDrag(start, 120, -90, screenW, screenH)
        assertEquals(spec(520, 310), moved)

        val pushed = FloatingLayout.onDrag(spec(1_000, 1_900), 500, 500, screenW, screenH)
        assertEquals(screenW - size.first - FloatingLayout.EDGE, pushed.x)
        assertEquals(screenH - size.second - FloatingLayout.EDGE, pushed.y)
    }

    @Test
    fun `drag does not snap mid gesture`() {
        // 松手吸附，但拖动中窗口必须跟手：停在左半边中间就是中间
        val out = FloatingLayout.onDrag(spec(300, 600), 40, 0, screenW, screenH)
        assertEquals(340, out.x)
    }

    @Test
    fun `tiny screen does not throw and keeps the window reachable`() {
        // 分屏/极端小窗：可用空间为 0 时 coerceIn(min > max) 会抛异常，必须提前兜住
        val out = FloatingLayout.clamp(FloatSpec(-10, -10, 200, 56), 10, 10)
        assertTrue("坐标不能为负", out.x >= 0 && out.y >= 0)
        assertTrue("尺寸不能超过屏幕", out.width <= 10 && out.height <= 10)
    }

    @Test
    fun `custom edge is honoured`() {
        // App 层传的是「8dp 换算后的 px」，所以留白必须可配置
        val out = FloatingLayout.clamp(spec(-100, -100), screenW, screenH, edge = 24)
        assertEquals(24, out.x)
        assertEquals(24, out.y)
        assertEquals(screenW - size.first - 24, FloatingLayout.clamp(spec(9_999, 0), screenW, screenH, 24).x)
    }
}
