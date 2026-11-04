package io.github.laozhudu.linkplay.console.nas

import java.net.ConnectException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * NAS 诊断段的排版与状态转移。
 *
 * 为什么测渲染而不只是测字段：诊断报告是**唯一**给用户看的东西，
 * 字段对了但排版丢了（比如"0 项"和"连不上"看起来一样）等于没补。
 * 所以这里断言的是"报告里那句话"，不是内部变量。
 */
class NasSourceStatusTest {

    @Before
    fun setUp() = NasSourceStatus.reset()

    @After
    fun tearDown() = NasSourceStatus.reset()

    @Test
    fun `before opening the page the section says so instead of showing zeros`() {
        val text = NasSourceStatus.render()
        assertTrue(text.contains("还没扫过"))
        assertTrue(text.contains("还没打开过 NAS 页"))
    }

    @Test
    fun `scanning found nothing is empty and explains it is not unreachable`() {
        NasSourceStatus.scanFound(0, emptyList(), 120)
        val s = NasSourceStatus.snapshot()
        assertEquals(0, s.scanCount)
        assertNotNull(s.failure)
        assertTrue("必须点明组播隔离的可能，别让人以为 NAS 关机了", s.failure!!.contains("组播"))
        assertTrue(NasSourceStatus.render().contains("发现=0 个"))
    }

    @Test
    fun `scanning success clears a previous failure`() {
        NasSourceStatus.scanFound(0, emptyList(), 100)
        assertNotNull(NasSourceStatus.snapshot().failure)

        NasSourceStatus.scanFound(2, listOf("http://192.168.99.223:8200/desc.xml"), 210)
        assertNull(NasSourceStatus.snapshot().failure)
        assertTrue(NasSourceStatus.render().contains("发现=2 个"))
    }

    @Test
    fun `connect failure is classified and keeps the target`() {
        NasSourceStatus.connectFailed("http://192.168.99.223:8200/desc.xml", ConnectException("refused"), 300)
        val s = NasSourceStatus.snapshot()
        assertTrue(s.failure!!.startsWith("连不上"))
        assertTrue("必须留下目标地址，否则不知道该去查哪台", s.failure!!.contains("192.168.99.223"))
        assertTrue(!s.connected)
    }

    @Test
    fun `connected then browsed renders count and elapsed time`() {
        NasSourceStatus.connected("fnOS/MiniDLNA", "http://192.168.99.223:8200/ctl/ContentDir", 412)
        NasSourceStatus.browsed("All Music", 1931, 1931, 210)

        val text = NasSourceStatus.render()
        assertTrue(text.contains("已连接"))
        assertTrue(text.contains("412ms"))
        assertTrue(text.contains("1931 项"))
        assertTrue(text.contains("210ms"))
    }

    @Test
    fun `empty directory is empty not a connection problem`() {
        NasSourceStatus.connected("fnOS", "http://x/ctl", 100)
        NasSourceStatus.browsed("空目录", 0, 0, 88)

        val s = NasSourceStatus.snapshot()
        assertTrue(s.failure!!.startsWith("连上了但是空的"))
        // 连上了就是连上了：不能因为目录空就把 connected 翻成 false
        assertTrue(s.connected)
    }

    @Test
    fun `control plane lost is its own line so it is not read as device offline`() {
        NasSourceStatus.controlLost(true)
        val text = NasSourceStatus.render()
        assertTrue(text.contains("49152"))
        assertTrue(text.contains("设备本身在线"))
    }

    @Test
    fun `route records which push path was taken`() {
        NasSourceStatus.route("设备列表(PlayQueue)", "ok", 640)
        val text = NasSourceStatus.render()
        assertTrue(text.contains("设备列表(PlayQueue)"))
        assertTrue(text.contains("640ms"))
    }
}
