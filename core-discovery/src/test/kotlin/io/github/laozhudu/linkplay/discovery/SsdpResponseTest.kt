package io.github.laozhudu.linkplay.discovery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SSDP 报文解析。这个模块此前**零测试** —— 解析错了的唯一表现是"设备扫不到"，
 * 而真机上那只意味着"列表是空的"，看不出是哪一步。
 */
class SsdpResponseTest {

    private val real = "HTTP/1.1 200 OK\r\n" +
        "CACHE-CONTROL: max-age=1800\r\n" +
        "LOCATION: http://192.168.99.175:3500/\r\n" +
        "SERVER: Linux/3.10 UPnP/1.0 AirReceiver/1.0\r\n" +
        "ST: urn:schemas-upnp-org:device:MediaRenderer:1\r\n" +
        "USN: uuid:81adb0b2-ade5-4526-a3cf-adc6e1db9e01::urn:schemas-upnp-org:device:MediaRenderer:1\r\n" +
        "\r\n"

    @Test
    fun `a real reply yields location usn and st`() {
        val r = parseSsdpResponse(real)!!
        assertEquals("http://192.168.99.175:3500/", r.location)
        assertEquals("urn:schemas-upnp-org:device:MediaRenderer:1", r.searchTarget)
        assertTrue(r.usable)
        assertTrue(r.server!!.contains("AirReceiver"))
    }

    @Test
    fun `uuid is extracted without the trailing service type`() {
        // USN 尾部挂 ::urn:...，直接当 uuid 用会导致"按 uuid 找不到设备"
        assertEquals("81adb0b2-ade5-4526-a3cf-adc6e1db9e01", parseSsdpResponse(real)!!.uuid)
    }

    @Test
    fun `header names are case insensitive`() {
        val mixed = "HTTP/1.1 200 OK\r\nLocation: http://10.0.0.2/desc.xml\r\nUsn: uuid:abc\r\n\r\n"
        val r = parseSsdpResponse(mixed)!!
        assertEquals("http://10.0.0.2/desc.xml", r.location)
        assertEquals("abc", r.uuid)
    }

    @Test
    fun `values containing colons are preserved`() {
        // 只能按第一个冒号切：LOCATION 里本来就有端口冒号
        val r = parseSsdpResponse("HTTP/1.1 200 OK\nLOCATION: http://192.168.1.9:49152/desc.xml\n\n")!!
        assertEquals("http://192.168.1.9:49152/desc.xml", r.location)
    }

    @Test
    fun `bare LF line endings are accepted`() {
        val r = parseSsdpResponse("HTTP/1.1 200 OK\nLOCATION: http://a/b\nST: x\n")!!
        assertEquals("http://a/b", r.location)
        assertEquals("x", r.searchTarget)
    }

    @Test
    fun `notify replies use NT instead of ST`() {
        val notify = "NOTIFY * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nNT: upnp:rootdevice\r\nUSN: uuid:zzz::upnp:rootdevice\r\n\r\n"
        val r = parseSsdpResponse(notify)!!
        assertEquals("upnp:rootdevice", r.searchTarget)
        assertEquals("zzz", r.uuid)
    }

    @Test
    fun `a reply without location is not usable`() {
        val r = parseSsdpResponse("HTTP/1.1 200 OK\r\nST: urn:x\r\n\r\n")!!
        assertNull(r.location)
        assertFalse("没有 LOCATION 就没法拉描述", r.usable)
    }

    @Test
    fun `garbage is null instead of a half parsed object`() {
        assertNull(parseSsdpResponse(""))
        assertNull(parseSsdpResponse("   "))
        assertNull(parseSsdpResponse("hello world"))
        // 只有头没有状态行
        assertNull(parseSsdpResponse("LOCATION: http://a/b\r\n"))
    }

    @Test
    fun `usn without the uuid prefix gives no uuid`() {
        assertNull(parseSsdpResponse("HTTP/1.1 200 OK\r\nUSN: 12345\r\n\r\n")!!.uuid)
    }

    @Test
    fun `duplicate headers keep the last one instead of crashing`() {
        val dup = "HTTP/1.1 200 OK\r\nLOCATION: http://first/\r\nLOCATION: http://second/\r\n\r\n"
        assertEquals("http://second/", parseSsdpResponse(dup)!!.location)
    }

    @Test
    fun `blank header values are treated as missing`() {
        val r = parseSsdpResponse("HTTP/1.1 200 OK\r\nLOCATION:   \r\n\r\n")!!
        assertNull(r.location)
        assertFalse(r.usable)
    }
}
