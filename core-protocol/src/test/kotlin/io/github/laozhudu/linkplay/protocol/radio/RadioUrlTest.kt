package io.github.laozhudu.linkplay.protocol.radio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 电台地址的清洗与校验。
 *
 * 用户原话「电台无法添加链接」。查下来是两个原因叠在一起：
 * 1. 只粘 `ice1.somafm.com/xxx`（没有协议头）时，校验直接判非法；
 * 2. 界面上那个按钮此时是禁用的，看起来就是"点了没反应"。
 * 所以这一层要能**自己补协议头**，而不是要求用户懂 URL 规范。
 */
class RadioUrlTest {

    @Test
    fun 没有协议头时自动补_http() {
        assertEquals("http://ice1.somafm.com/groovesalad", RadioStation.normalizeUrl("ice1.somafm.com/groovesalad"))
        assertEquals("http://example.com:8000/live.mp3", RadioStation.normalizeUrl("example.com:8000/live.mp3"))
        // "//host/path" 是协议相对写法，补成 http 后应还原成标准形式
        assertEquals("http://x.com/a", RadioStation.normalizeUrl("//x.com/a"))
    }

    @Test
    fun 已经有协议头时保持原样() {
        assertEquals("https://a.com/s", RadioStation.normalizeUrl("https://a.com/s"))
        assertEquals("http://a.com/s", RadioStation.normalizeUrl("http://a.com/s"))
    }

    @Test
    fun 去掉首尾空白换行与全角空格() {
        assertEquals("http://a.com/s", RadioStation.normalizeUrl("  http://a.com/s \n"))
        assertEquals("http://a.com/s", RadioStation.normalizeUrl("\u3000http://a.com/s\u00A0"))
        assertEquals("http://a.com/s", RadioStation.normalizeUrl("http://a.com/s\r\n"))
    }

    @Test
    fun 非法协议被拒() {
        assertNull(RadioStation.normalizeUrl("file:///etc/passwd"))
        assertNull(RadioStation.normalizeUrl("content://media/1"))
        assertNull(RadioStation.normalizeUrl(""))
        assertNull(RadioStation.normalizeUrl("   "))
    }

    @Test
    fun 内部空格被拒_避免设备拼出坏地址() {
        assertNull(RadioStation.normalizeUrl("http://a.com/my song.mp3"))
    }

    @Test
    fun 真实电台地址全部通过校验() {
        VerifiedRadioCatalog.entries.forEach { e ->
            assertNull("${e.name} 应合法", RadioStation.validate(e.name, e.url))
            assertTrue("${e.name} 应是 http/https", e.url.startsWith("http"))
            // 旧固件对 https 支持不可靠，内置的一律必须是明文 http
            assertTrue("${e.name} 应为明文 http（旧固件 TLS 弱）", e.url.startsWith("http://"))
            assertNotNull(e.deviceVerifiedAt)
        }
    }

    @Test
    fun 内置电台去重后没有重复地址() {
        val urls = VerifiedRadioCatalog.entries.map { it.url }
        assertEquals(urls.size, urls.distinct().size)
        val names = VerifiedRadioCatalog.entries.map { it.name }
        assertEquals(names.size, names.distinct().size)
    }

    @Test
    fun 名称过长或为空被拒() {
        assertNotNull(RadioStation.validate("", "http://a.com/s"))
        assertNotNull(RadioStation.validate("x".repeat(65), "http://a.com/s"))
        assertNull(RadioStation.validate("正常名字", "http://a.com/s"))
    }

    @Test
    fun 内置条目转成电台对象时带上设备名() {
        val s = VerifiedRadioCatalog.toStation(VerifiedRadioCatalog.entries.first(), "doss1")
        assertEquals("doss1", s.deviceName)
        assertTrue(s.kept)
        assertFalse(s.invalid)
    }
}
