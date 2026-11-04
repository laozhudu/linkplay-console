package io.github.laozhudu.linkplay.console.online

import java.net.SocketTimeoutException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 在线曲库诊断段的排版与"库空"判据。
 *
 * 这一组锁的是 `docs/65` 批次 1 的验收判据：
 * 用户说「搜不到歌」时，报告要能分开「这个词没命中」和「服务端曲库是空的」。
 *
 * 关键约束：**单次 0 条不能下"库空"的结论**。那是在猜。
 * 只有连续多次都是 0 条，才允许写"大概率是空的"。
 */
class SubsonicSourceStatusTest {

    @Before
    fun setUp() = SubsonicSourceStatus.reset()

    @After
    fun tearDown() = SubsonicSourceStatus.reset()

    @Test
    fun `never configured says so`() {
        assertTrue(SubsonicSourceStatus.render().contains("还没配置过"))
    }

    @Test
    fun `successful ping shows address user and elapsed time`() {
        SubsonicSourceStatus.configured("http://192.168.99.124:9527", "tt01")
        SubsonicSourceStatus.pinged(true, 38)

        val text = SubsonicSourceStatus.render()
        assertTrue(text.contains("http://192.168.99.124:9527"))
        assertTrue(text.contains("tt01"))
        assertTrue(text.contains("已通过 ping"))
        assertTrue(text.contains("38ms"))
    }

    @Test
    fun `ping returning false is auth failure not unreachable`() {
        SubsonicSourceStatus.configured("http://x:9527", "u")
        SubsonicSourceStatus.pinged(false, 40)

        val s = SubsonicSourceStatus.snapshot()
        assertEquals(false, s.verified)
        assertTrue(s.failure!!.startsWith("认证失败"))
    }

    @Test
    fun `single empty search must not claim the library is empty`() {
        SubsonicSourceStatus.configured("http://x:9527", "u")
        SubsonicSourceStatus.searched("周杰伦", 0, 38)

        val text = SubsonicSourceStatus.render()
        assertTrue("单次 0 条只能说这个词没命中", text.contains("这个词没命中"))
        assertTrue("不能说库空", !text.contains("大概率是空的"))
    }

    @Test
    fun `three empty searches in a row may conclude library is empty`() {
        SubsonicSourceStatus.configured("http://x:9527", "u")
        SubsonicSourceStatus.searched("周杰伦", 0, 30)
        SubsonicSourceStatus.searched("陈奕迅", 0, 31)
        SubsonicSourceStatus.searched("林俊杰", 0, 33)

        val text = SubsonicSourceStatus.render()
        assertTrue(text.contains("连续 3 次都是 0 条"))
        assertTrue(text.contains("大概率是空的"))
        assertTrue(text.contains("EMPTY"))
    }

    @Test
    fun `a hit resets the streak`() {
        SubsonicSourceStatus.configured("http://x:9527", "u")
        SubsonicSourceStatus.searched("a", 0, 10)
        SubsonicSourceStatus.searched("b", 0, 10)
        SubsonicSourceStatus.searched("c", 5, 20)
        SubsonicSourceStatus.searched("d", 0, 10)

        assertEquals(1, SubsonicSourceStatus.snapshot().zeroStreak)
        assertTrue(!SubsonicSourceStatus.render().contains("大概率是空的"))
    }

    @Test
    fun `search failure keeps classification and query`() {
        SubsonicSourceStatus.configured("http://x:9527", "u")
        SubsonicSourceStatus.searchFailed("晴天", SocketTimeoutException("timeout"), 12000)

        val text = SubsonicSourceStatus.render()
        assertTrue(text.contains("晴天"))
        assertTrue(text.contains("超时"))
        assertEquals(-1, SubsonicSourceStatus.snapshot().lastCount)
    }

    @Test
    fun `changing address resets previous verdict`() {
        SubsonicSourceStatus.configured("http://x:9527", "u")
        SubsonicSourceStatus.pinged(true, 20)
        SubsonicSourceStatus.searched("a", 0, 10)
        SubsonicSourceStatus.searched("b", 0, 10)

        SubsonicSourceStatus.configured("http://y:9527", "u2")
        val s = SubsonicSourceStatus.snapshot()
        assertEquals(null, s.verified)
        assertEquals(0, s.zeroStreak)
        assertEquals(-1, s.lastCount)
    }

    @Test
    fun `play result is recorded both ways`() {
        SubsonicSourceStatus.played("晴天", true, null)
        assertTrue(SubsonicSourceStatus.render().contains("成功（晴天）"))

        SubsonicSourceStatus.played("雨天", false, "拿不到直链")
        assertTrue(SubsonicSourceStatus.render().contains("设备不支持"))
    }
}
