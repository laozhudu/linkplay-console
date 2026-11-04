package io.github.laozhudu.linkplay.console.library

import java.net.SocketTimeoutException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 本地曲库诊断段的排版与状态转移。
 *
 * 断言的是**报告里那句话**，不是内部字段 —— 报告是唯一给用户看的东西。
 *
 * 这一组用例锁的是 `docs/65` 批次 1 的验收判据：
 * 用户说「本地没音乐」时，报告里必须能分清
 * 「没权限」/「真没歌」/「查到但全被跳过」/「查询抛异常」。
 */
class LocalSourceStatusTest {

    @Before
    fun setUp() = LocalSourceStatus.reset()

    @After
    fun tearDown() = LocalSourceStatus.reset()

    @Test
    fun `before any scan the section says so instead of showing zeros`() {
        val text = LocalSourceStatus.render()
        assertTrue(text.contains("权限=还没检查过"))
        assertTrue(text.contains("扫描=还没扫过"))
    }

    @Test
    fun `missing permission is stated with its consequence`() {
        LocalSourceStatus.permission("READ_MEDIA_AUDIO", false)
        val text = LocalSourceStatus.render()
        assertTrue(text.contains("权限=未授予（READ_MEDIA_AUDIO）"))
        // 只说"未授予"会被当成无关提示；必须点明它会让界面显示 0 首
        assertTrue(text.contains("界面只会显示 0 首"))
    }

    @Test
    fun `granted but zero tracks is EMPTY not a permission problem`() {
        LocalSourceStatus.permission("READ_MEDIA_AUDIO", true)
        LocalSourceStatus.scanned(0, 0, 88)

        val s = LocalSourceStatus.snapshot()
        assertTrue(s.failure!!.startsWith("连上了但是空的"))
        assertTrue(LocalSourceStatus.render().contains("MediaStore 里没有音频"))
    }

    @Test
    fun `zero kept but some skipped names the real reason`() {
        LocalSourceStatus.permission("READ_MEDIA_AUDIO", true)
        LocalSourceStatus.scanned(0, 12, 120)

        val text = LocalSourceStatus.render()
        assertTrue("必须说出「查到 N 条全被跳过」，否则会让人以为手机没歌", text.contains("查到 12 条但全被跳过"))
        assertTrue(LocalSourceStatus.snapshot().failure!!.startsWith("回包看不懂"))
    }

    @Test
    fun `a healthy scan clears the previous failure`() {
        LocalSourceStatus.permission("READ_MEDIA_AUDIO", true)
        LocalSourceStatus.scanned(0, 0, 90)
        assertNotNull(LocalSourceStatus.snapshot().failure)

        LocalSourceStatus.scanned(1931, 3, 120)
        assertNull(LocalSourceStatus.snapshot().failure)
        val text = LocalSourceStatus.render()
        assertTrue(text.contains("1931 首"))
        assertTrue(text.contains("跳过 3 条"))
    }

    @Test
    fun `scan failure keeps the classification and elapsed time`() {
        LocalSourceStatus.permission("READ_MEDIA_AUDIO", true)
        LocalSourceStatus.scanFailed(SocketTimeoutException("timeout"), 5000)

        val s = LocalSourceStatus.snapshot()
        assertEquals(0, s.count)
        assertEquals(5000L, s.ms)
        assertTrue(s.failure!!.startsWith("超时"))
    }

    @Test
    fun `permission denied is not overwritten by a later empty scan`() {
        // 没权限时扫出来 0 首是意料之中的，不该把"未授予"这条结论盖成"库里没歌"
        LocalSourceStatus.permission("READ_MEDIA_AUDIO", false)
        LocalSourceStatus.scanned(0, 0, 30)

        assertNull("没权限时不该再报一条 EMPTY，否则报告自相矛盾", LocalSourceStatus.snapshot().failure)
        assertTrue(LocalSourceStatus.render().contains("权限=未授予"))
    }
}
