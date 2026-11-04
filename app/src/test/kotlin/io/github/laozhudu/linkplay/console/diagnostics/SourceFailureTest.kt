package io.github.laozhudu.linkplay.console.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * 失败分类的映射规则。
 *
 * 这些用例锁的是「报告里那句话准不准」：
 * 用户说"NAS 连不上"时，我需要在报告里看到 `连不上` 还是 `超时` 还是 `没权限` ——
 * 三者的下一步动作完全不同（换线路 / 等一会 / 去设置授权）。
 */
class SourceFailureTest {

    @Test
    fun `timeouts map to timeout not unreachable`() {
        assertEquals(SourceFailure.TIMEOUT, SourceFailure.of(SocketTimeoutException("timeout")))
    }

    @Test
    fun `refused and unresolved both map to unreachable`() {
        assertEquals(SourceFailure.UNREACHABLE, SourceFailure.of(ConnectException("refused")))
        assertEquals(SourceFailure.UNREACHABLE, SourceFailure.of(UnknownHostException("no host")))
    }

    @Test
    fun `security exception maps to permission denied`() {
        assertEquals(SourceFailure.PERMISSION_DENIED, SourceFailure.of(SecurityException("denied")))
    }

    @Test
    fun `unknown exception stays unknown instead of being guessed`() {
        // 宁可写"未知错误"让人去查，也不能塞进某个分类假装知道原因
        assertEquals(SourceFailure.UNKNOWN, SourceFailure.of(IllegalStateException("boom")))
    }

    @Test
    fun `describe keeps the label and appends the detail`() {
        assertEquals("连不上", SourceFailure.UNREACHABLE.describe(null))
        assertEquals("连不上 —— Connection refused", SourceFailure.UNREACHABLE.describe("Connection refused"))
        assertEquals("连不上", SourceFailure.UNREACHABLE.describe("   "))
    }

    @Test
    fun `empty directory says empty not failure`() {
        val line = SourceFailure.EMPTY.describe("目录「/All Music」返回 0 项")
        assertTrue(line.startsWith("连上了但是空的"))
    }
}
