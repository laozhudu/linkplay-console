package io.github.laozhudu.linkplay.console.radio

import io.github.laozhudu.linkplay.console.diagnostics.SourceFailure
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 电台诊断段的排版与 https 判据。
 *
 * 这一组锁的是 `docs/65` 批次 1 的验收判据，以及项目里踩过四次的
 * 「命令回 OK ≠ 功能生效」：`https` 地址在老固件上命令回 OK 但 status 不动。
 *
 * 报告必须**直接说出"音箱不认 https"**，不能让看报告的人自己从 URL 前面
 * 那五个字母去推断。
 */
class RadioSourceStatusTest {

    @Before
    fun setUp() = RadioSourceStatus.reset()

    @After
    fun tearDown() = RadioSourceStatus.reset()

    @Test
    fun `before anything the section says so`() {
        val text = RadioSourceStatus.render()
        assertTrue(text.contains("库内=还没读过"))
        assertTrue(text.contains("最近播放=还没试过"))
    }

    @Test
    fun `library count is shown once loaded`() {
        RadioSourceStatus.library(24)
        assertTrue(RadioSourceStatus.render().contains("库内=24 台"))
    }

    @Test
    fun `successful play shows scheme and status`() {
        RadioSourceStatus.played("http://ngcdn001.cnr.cn/live/zgzs/index.m3u8", "status=PLAY", 3_200)

        val text = RadioSourceStatus.render()
        assertTrue(text.contains("scheme=http"))
        assertTrue(text.contains("结果=成功"))
        assertTrue(text.contains("status=PLAY"))
        assertTrue(text.contains("3.2s"))
    }

    @Test
    fun `https failure names the real cause instead of leaving it to the reader`() {
        RadioSourceStatus.failed(
            "https://lqingting.qingting.fm/live/4804/64k.mp3",
            SourceFailure.UNSUPPORTED,
            "status=STOP",
            6_100,
        )

        val text = RadioSourceStatus.render()
        assertTrue(text.contains("scheme=https"))
        assertTrue("必须直说音箱不认 https", text.contains("音箱不认 https"))
        assertTrue("要点明命令其实是回了 OK 的", text.contains("回了 OK"))
    }

    @Test
    fun `http failure is not blamed on https`() {
        RadioSourceStatus.failed("http://example.com/a.mp3", SourceFailure.UNSUPPORTED, "status=STOP", 6_000)

        val text = RadioSourceStatus.render()
        assertTrue(!text.contains("音箱不认 https"))
        assertTrue(text.contains("设备不支持"))
    }

    @Test
    fun `unreachable keeps its own classification`() {
        RadioSourceStatus.failed("http://example.com/a.mp3", SourceFailure.UNREACHABLE, "Connection refused", 800)

        val text = RadioSourceStatus.render()
        assertTrue(text.contains("连不上"))
        assertTrue(text.contains("Connection refused"))
    }

    @Test
    fun `invalid url is reported as parse failure not as playback failure`() {
        RadioSourceStatus.invalid("随便打的一串字")

        val s = RadioSourceStatus.snapshot()
        assertEquals(false, s.lastOk)
        assertTrue(s.failure!!.startsWith("回包看不懂"))
        assertTrue(RadioSourceStatus.render().contains("scheme=?"))
    }

    @Test
    fun `a later success clears the previous failure`() {
        RadioSourceStatus.failed("https://x/a.mp3", SourceFailure.UNSUPPORTED, "status=STOP", 6_000)
        RadioSourceStatus.played("http://x/a.mp3", "status=PLAY", 2_100)

        assertEquals(null, RadioSourceStatus.snapshot().failure)
        assertTrue(RadioSourceStatus.render().contains("结果=成功"))
    }
}
