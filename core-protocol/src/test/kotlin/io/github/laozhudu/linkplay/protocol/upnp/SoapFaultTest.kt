package io.github.laozhudu.linkplay.protocol.upnp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SOAP 错误的结构化与翻译。
 *
 * 锁死的是这条真实故障：`.164` 没加载媒体时 `AVTransport.Play` 回
 * `500 / 704 play not allowed`，我们把原始报文摊给了用户
 * （截图里的红字 `SOAP 500 for "urn:schemas-upnp-org:service:AVTransport:1#Play"`）。
 */
class SoapFaultTest {

    @Test
    fun `704 is classified as no media and says what to do`() {
        assertTrue(UpnpFault.isNoMedia(704))
        val msg = UpnpFault.message(704, "play not allowed", "Play")
        assertTrue("必须告诉用户去哪里选歌", msg.contains("我的音乐"))
        assertFalse("不能再把原始报文塞给用户", msg.contains("SOAP"))
        assertFalse(msg.contains("urn:schemas"))
    }

    @Test
    fun `704 is retryable on another channel`() {
        assertTrue(
            "httpapi 的 setPlayerCmd 在 704 场景下常常能接着放，所以必须允许换通道重试",
            UpnpFault.isRetryableOnAnotherChannel(704),
        )
    }

    @Test
    fun `unknown codes never leak raw soap`() {
        val msg = UpnpFault.message(9999, "weird", "Play")
        assertTrue(msg.contains("9999"))
        assertFalse(msg.contains("SOAP"))
        assertFalse(msg.contains("urn:"))
    }

    @Test
    fun `null code still produces a readable sentence`() {
        val msg = UpnpFault.message(null, "boom")
        assertTrue(msg.contains("boom"))
        assertFalse(msg.contains("SOAP"))
    }

    @Test
    fun `friendlyOf unwraps a soap fault and passes other exceptions through`() {
        val fault = SoapFaultException(500, 704, "play not allowed", "Play")
        assertTrue(UpnpFault.friendlyOf(fault).contains("我的音乐"))

        val other = IllegalStateException("网络超时")
        assertEquals("网络超时", UpnpFault.friendlyOf(other))
    }

    @Test
    fun `exception message keeps the technical detail for logs`() {
        val fault = SoapFaultException(500, 704, "play not allowed", "Play")
        val raw = fault.message.orEmpty()
        assertTrue("日志里要保留技术细节，方便排查", raw.contains("SOAP 500"))
        assertTrue(raw.contains("704"))
        assertTrue(raw.contains("play not allowed"))
    }

    @Test
    fun `401 explains the firmware does not support the action`() {
        val msg = UpnpFault.message(401, "invalid action", "BrowseQueueUSBDisk")
        assertTrue(msg.contains("不支持"))
        assertTrue("要点出是哪个动作不支持", msg.contains("BrowseQueueUSBDisk"))
    }
}
