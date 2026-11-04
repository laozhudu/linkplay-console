package io.github.laozhudu.linkplay.console

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 洛雪引擎自检段的排版。
 *
 * 这一段存在的理由（`docs/74` 第六节）：`core-lx` 的单测全跑在假引擎上，
 * 「QuickJS 能不能跑」是唯一没验证过的环节。所以第一件事是让 App 自己说出结果。
 *
 * 三种结果必须能分开：
 *
 * | 报告里 | 含义 | 下一步 |
 * |---|---|---|
 * | `引擎=可用  自检=…5 条` | 能跑 | 可以做界面 |
 * | `自检=…0 条` | 引擎活了但没结果 | 查网络或平台接口 |
 * | `自检失败=…` | 引擎/bundle 挂了 | 先修引擎，别碰界面 |
 */
class LxSourceStatusTest {

    @Before
    fun setUp() = LxSourceStatus.reset()

    @After
    fun tearDown() = LxSourceStatus.reset()

    @Test
    fun `before the self check says it has not run`() {
        assertTrue(LxSourceStatus.render().contains("还没跑过自检"))
    }

    @Test
    fun `successful self check shows load time count and elapsed`() {
        LxSourceStatus.started()
        LxSourceStatus.loaded(820)
        LxSourceStatus.searched("wy", "周杰伦", 5, 1_200)

        val text = LxSourceStatus.render()
        assertTrue(text.contains("引擎=可用"))
        assertTrue(text.contains("加载 820ms"))
        assertTrue(text.contains("搜「周杰伦」@wy"))
        assertTrue(text.contains("5 条"))
        assertTrue(text.contains("1200ms"))
        assertEquals(true, LxSourceStatus.snapshot().ok)
    }

    @Test
    fun `zero results is not the same as engine failure`() {
        LxSourceStatus.loaded(700)
        LxSourceStatus.searched("wy", "周杰伦", 0, 900)

        val s = LxSourceStatus.snapshot()
        // 引擎是活的：ok=true、没有 error
        assertEquals(true, s.ok)
        assertEquals(null, s.error)
        val text = LxSourceStatus.render()
        assertTrue(text.contains("0 条"))
        assertTrue("要说清可能是网络或接口变了", text.contains("网络或平台接口变了"))
    }

    @Test
    fun `engine failure carries the stage and the message`() {
        LxSourceStatus.failed("引擎或 bundle", "UnsatisfiedLinkError: libquickjs")

        val text = LxSourceStatus.render()
        assertTrue(text.contains("引擎=不可用"))
        assertTrue(text.contains("UnsatisfiedLinkError"))
        assertEquals(false, LxSourceStatus.snapshot().ok)
    }

    @Test
    fun `failure without a message still names the stage`() {
        LxSourceStatus.failed("搜索", null)
        assertTrue(LxSourceStatus.render().contains("自检失败=搜索"))
    }
}
