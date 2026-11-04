package io.github.laozhudu.linkplay.console.diagnostics

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 界面位置快照 —— 覆盖「点了没反应」最常见的那类误报。
 *
 * 判据：**只看这一段文本，能不能判断"用户是不是被浮层挡住了"**。
 * 这个判断错了，我会去查按钮绑定、查 ViewModel，查半天发现是对话框没关。
 */
class NavTraceTest {

    @After
    fun tearDown() = NavTrace.reset()

    @Test
    fun `default state says no sub page and no overlay`() {
        val text = NavTrace.render()
        assertTrue(text.contains("一级页=设备"))
        assertTrue(text.contains("二级页=无"))
        assertTrue(text.contains("浮层=无"))
    }

    @Test
    fun `open overlay is called out with its consequence`() {
        // 只写"浮层=设备菜单"不够 —— 要让人知道这会吃掉点击
        NavTrace.overlay = "设备菜单"
        val text = NavTrace.render()
        assertTrue(text.contains("浮层=设备菜单"))
        assertTrue(text.contains("浮层开着时点背景不会响应"))
    }

    @Test
    fun `volume osd is flagged as a full screen mask`() {
        // 音量浮层和对话框不同：它是全屏遮罩，用户完全看不出有问题
        NavTrace.volumeOsdVisible = true
        val text = NavTrace.render()
        assertTrue(text.contains("音量浮层=显示中"))
        assertTrue(text.contains("全屏遮罩"))
    }

    @Test
    fun `no osd means no osd line`() {
        assertFalse(NavTrace.render().contains("音量浮层"))
    }

    @Test
    fun `sub page is shown when pushed`() {
        NavTrace.tab = "播放"
        NavTrace.subPage = "Diagnostics"
        val text = NavTrace.render()
        assertTrue(text.contains("一级页=播放"))
        assertTrue(text.contains("二级页=Diagnostics"))
    }

    @Test
    fun `reset clears everything`() {
        NavTrace.tab = "媒体库"
        NavTrace.subPage = "Nas"
        NavTrace.overlay = "改名"
        NavTrace.volumeOsdVisible = true
        NavTrace.reset()
        val text = NavTrace.render()
        assertTrue(text.contains("一级页=设备"))
        assertTrue(text.contains("二级页=无"))
        assertTrue(text.contains("浮层=无"))
    }
}
