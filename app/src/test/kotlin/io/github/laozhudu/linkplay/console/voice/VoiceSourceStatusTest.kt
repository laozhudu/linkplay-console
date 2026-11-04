package io.github.laozhudu.linkplay.console.voice

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 语音诊断段的排版与三段区分。
 *
 * 这一组锁的是 `docs/65` 批次 1 的验收判据：
 * 用户说「语音不管用」时，报告里要能看出断在哪一段 ——
 * **识别**（麦克风权限 / 有没有引擎 / 听没听清）、**意图匹配**、**执行**。
 *
 * 三种情况的修法完全不同：去授权 / 换个说法 / 这个口令还没实现。
 */
class VoiceSourceStatusTest {

    @Before
    fun setUp() = VoiceSourceStatus.reset()

    @After
    fun tearDown() = VoiceSourceStatus.reset()

    @Test
    fun `before any use the section says so`() {
        assertTrue(VoiceSourceStatus.render().contains("还没用过语音"))
    }

    @Test
    fun `environment shows engine and mic permission separately`() {
        VoiceSourceStatus.environment(available = true, micGranted = false)
        val text = VoiceSourceStatus.render()
        assertTrue(text.contains("识别服务=可用"))
        assertTrue(text.contains("麦克风=未授权"))
    }

    @Test
    fun `matched intent renders recognition intent and result on one line`() {
        VoiceSourceStatus.environment(available = true, micGranted = true)
        VoiceSourceStatus.heard("下一首")
        VoiceSourceStatus.routed("Next", "下一首", matched = true)

        val text = VoiceSourceStatus.render()
        assertTrue(text.contains("最近识别「下一首」"))
        assertTrue(text.contains("意图=Next"))
        assertTrue(text.contains("执行=下一首"))
        assertEquals(true, VoiceSourceStatus.snapshot().lastOk)
    }

    @Test
    fun `unmatched intent is unsupported not a crash`() {
        VoiceSourceStatus.heard("放一首周杰伦")
        VoiceSourceStatus.routed("Unknown", "没听清（识别为「放一首周杰伦」）", matched = false)

        val s = VoiceSourceStatus.snapshot()
        assertEquals(false, s.lastOk)
        assertTrue(s.failure!!.startsWith("设备不支持"))
        assertTrue("要写明只认播放控制类口令", s.failure!!.contains("只认播放控制类"))
        // 但识别是本身上成功的，原文必须留着 —— 否则没法判断是不是说法问题
        assertTrue(VoiceSourceStatus.render().contains("放一首周杰伦"))
    }

    @Test
    fun `mic permission failure is permission denied`() {
        VoiceSourceStatus.failed("没有麦克风权限，语音用不了")
        assertTrue(VoiceSourceStatus.snapshot().failure!!.startsWith("没权限"))
    }

    @Test
    fun `missing engine is not configured not a failure of ours`() {
        VoiceSourceStatus.environment(available = false, micGranted = false)
        VoiceSourceStatus.failed("本机没有可用的语音识别")

        assertTrue(VoiceSourceStatus.snapshot().failure!!.startsWith("未配置"))
        assertTrue(VoiceSourceStatus.render().contains("识别服务=不可用"))
    }

    @Test
    fun `not heard maps to empty`() {
        VoiceSourceStatus.failed("没听清，再说一次")
        assertTrue(VoiceSourceStatus.snapshot().failure!!.startsWith("连上了但是空的"))
    }

    @Test
    fun `a later success clears the previous failure`() {
        VoiceSourceStatus.failed("没听清，再说一次")
        assertTrue(VoiceSourceStatus.snapshot().failure != null)

        VoiceSourceStatus.heard("播放")
        VoiceSourceStatus.routed("Play", "播放", matched = true)
        assertEquals(null, VoiceSourceStatus.snapshot().failure)
    }

    @Test
    fun `hearing text without routing says it did not reach intent matching`() {
        VoiceSourceStatus.heard("嗯…")
        assertTrue(VoiceSourceStatus.render().contains("还没走到意图匹配"))
    }
}
