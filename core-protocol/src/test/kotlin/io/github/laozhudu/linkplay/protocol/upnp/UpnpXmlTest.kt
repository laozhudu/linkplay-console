package io.github.laozhudu.linkplay.protocol.upnp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpnpXmlTest {

    @Test
    fun `value picks the first matching tag`() {
        val xml = "<a><b>one</b><b>two</b></a>"
        assertEquals("one", UpnpXml.value(xml, "b"))
        assertEquals(listOf("one", "two"), UpnpXml.values(xml, "b"))
    }

    @Test
    fun `value skips self closing tags`() {
        val xml = "<root><empty/><b>real</b></root>"
        assertEquals("real", UpnpXml.value(xml, "empty") ?: "real")
        assertEquals("real", UpnpXml.value(xml, "b"))
    }

    @Test
    fun `value handles attributes on the opening tag`() {
        val xml = """<res protocolInfo="http-get:*:audio/mpeg:*">http://h/a.mp3</res>"""
        assertEquals("http://h/a.mp3", UpnpXml.value(xml, "res"))
    }

    @Test
    fun `value returns null when absent`() {
        assertNull(UpnpXml.value("<a></a>", "zzz"))
    }

    @Test
    fun `blocks returns whole service elements`() {
        val xml = "<root><service><serviceType>T1</serviceType></service><service><serviceType>T2</serviceType></service></root>"
        val blocks = UpnpXml.blocks(xml, "service")
        assertEquals(2, blocks.size)
        assertEquals("T1", UpnpXml.value(blocks[0], "serviceType"))
        assertEquals("T2", UpnpXml.value(blocks[1], "serviceType"))
    }

    @Test
    fun `escape and unescape are inverse`() {
        val raw = """a & b < c > d " e ' f"""
        val escaped = UpnpXml.escape(raw)
        assertTrue(escaped.contains("&amp;"))
        assertTrue(escaped.contains("&lt;"))
        assertTrue(escaped.contains("&quot;"))
        assertTrue(escaped.contains("&apos;"))
        assertEquals(raw, UpnpXml.unescape(escaped))
    }

    @Test
    fun `amed is not double unescaped`() {
        // &amp;lt; 应还原为 &lt; 而不是 <
        assertEquals("&lt;", UpnpXml.unescape("&amp;lt;"))
    }
}
