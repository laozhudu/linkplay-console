package io.github.laozhudu.linkplay.protocol.ssdp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SsdpMessagesTest {

    @Test
    fun `m-search carries the three headers devices actually require`() {
        val msg = SsdpMessages.buildMSearch(SsdpMessages.ST_MEDIA_RENDERER)
        assertTrue(msg.startsWith("M-SEARCH * HTTP/1.1\r\n"))
        assertTrue(msg.contains("HOST: 239.255.255.250:1900\r\n"))
        // MAN 必须带引号，缺引号设备不回
        assertTrue(msg.contains("MAN: \"ssdp:discover\"\r\n"))
        assertTrue(msg.contains("MX: 2\r\n"))
        assertTrue(msg.contains("ST: urn:schemas-upnp-org:device:MediaRenderer:1\r\n"))
        assertTrue(msg.endsWith("\r\n\r\n"))
    }

    @Test
    fun `default search order asks for the private service first`() {
        assertEquals(SsdpMessages.ST_WIIMU_PLAY_QUEUE, SsdpMessages.DEFAULT_SEARCH_TARGETS.first())
        assertTrue(SsdpMessages.DEFAULT_SEARCH_TARGETS.contains("ssdp:all"))
    }

    @Test
    fun `parses a real device response verbatim`() {
        // 逐字取自 DOSS DS-1831 实测回包
        val raw = "HTTP/1.1 200 OK\r\n" +
            "CACHE-CONTROL: max-age=1800\r\n" +
            "DATE: Mon, 02 Nov 2026 01:54:20 GMT\r\n" +
            "EXT:\r\n" +
            "LOCATION: http://192.168.99.153:49152/description.xml\r\n" +
            "BOOTID.UPNP.ORG: FF31F017-4FE5-C9B2-A2AC-EAE3FF31F017\r\n" +
            "SERVER: Linux/2.6.36\r\n" +
            "ST: urn:schemas-wiimu-com:service:PlayQueue:1\r\n" +
            "USN: uuid:FF31F017-4FE5-C9B2-A2AC-EAE3FF31F017::urn:schemas-wiimu-com:service:PlayQueue:1\r\n\r\n"
        val r = SsdpMessages.parseResponse(raw)!!
        assertEquals("http://192.168.99.153:49152/description.xml", r.location)
        assertEquals("urn:schemas-wiimu-com:service:PlayQueue:1", r.st)
        assertEquals("Linux/2.6.36", r.server)
        assertEquals("uuid:FF31F017-4FE5-C9B2-A2AC-EAE3FF31F017", r.uuid)
        // 从 LOCATION 取主机，不依赖收包源地址
        assertEquals("192.168.99.153", r.hostFromLocation)
    }

    @Test
    fun `usn without service suffix still yields uuid`() {
        assertEquals(
            "uuid:FF31F017-4FE5-C9B2-A2AC-EAE3FF31F017",
            SsdpMessages.uuidOf("uuid:FF31F017-4FE5-C9B2-A2AC-EAE3FF31F017"),
        )
        assertNull(SsdpMessages.uuidOf("not-a-uuid"))
        assertNull(SsdpMessages.uuidOf(null))
    }

    @Test
    fun `non-200 or location-less responses are ignored`() {
        assertNull(SsdpMessages.parseResponse("NOTIFY * HTTP/1.1\r\nLOCATION: http://h/d.xml\r\n"))
        assertNull(SsdpMessages.parseResponse("HTTP/1.1 200 OK\r\nST: ssdp:all\r\n\r\n"))
    }

    @Test
    fun `host is extracted port-independently`() {
        val r = SsdpMessages.parseResponse("HTTP/1.1 200 OK\r\nLOCATION: http://10.0.0.5:1234/x.xml\r\n\r\n")!!
        assertEquals("10.0.0.5", r.hostFromLocation)
    }
}
