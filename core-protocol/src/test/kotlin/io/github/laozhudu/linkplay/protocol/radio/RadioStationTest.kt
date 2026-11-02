package io.github.laozhudu.linkplay.protocol.radio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RadioStationTest {

    @Test
    fun `valid http and https urls pass`() {
        assertNull(RadioStation.validate("Radio", "http://stream.example.com/a.mp3"))
        assertNull(RadioStation.validate("Radio", "https://stream.example.com/live"))
    }

    @Test
    fun `non http schemes are rejected`() {
        assertTrue(RadioStation.validate("R", "file:///sdcard/a.mp3")!!.contains("http"))
        assertTrue(RadioStation.validate("R", "content://media/1")!!.contains("http"))
        assertTrue(RadioStation.validate("R", "rtsp://h/a")!!.contains("http"))
    }

    @Test
    fun `blank name or url is rejected`() {
        assertEquals("电台名称不能为空", RadioStation.validate("  ", "http://h/a"))
        assertEquals("流地址不能为空", RadioStation.validate("R", "   "))
    }

    @Test
    fun `long name or url is rejected`() {
        assertTrue(RadioStation.validate("x".repeat(65), "http://h/a")!!.contains("过长"))
        assertTrue(RadioStation.validate("R", "http://h/" + "a".repeat(2100))!!.contains("过长"))
    }

    @Test
    fun `spaces in url are rejected with a fix hint`() {
        assertTrue(RadioStation.validate("R", "http://h/a b.mp3")!!.contains("%20"))
    }

    @Test
    fun `name suggestion uses the host`() {
        assertEquals("stream.example.com", RadioStation.suggestName("https://stream.example.com:8000/live"))
        assertEquals("网络电台", RadioStation.suggestName(""))
    }

    @Test
    fun `stream detection is only a hint`() {
        assertTrue(RadioStation.looksLikeStream("http://h/a.mp3"))
        assertTrue(RadioStation.looksLikeStream("http://h/live.m3u8"))
        assertTrue(RadioStation.looksLikeStream("http://h/stream"))
        assertFalse(RadioStation.looksLikeStream("http://h/abc"))
    }

    @Test
    fun `store groups by device and puts invalid last`() {
        val store = InMemoryRadioStationStore()
        val a = store.add(RadioStation("A", "http://h/a", deviceName = "kitchen"))
        val b = store.add(RadioStation("B", "http://h/b", deviceName = null))
        val c = store.add(RadioStation("C", "http://h/c", deviceName = "bedroom"))

        // 全部设备都能看到通用条目
        assertEquals(setOf("A", "B"), store.forDevice("kitchen").map { it.name }.toSet())
        assertEquals(setOf("B", "C"), store.forDevice("bedroom").map { it.name }.toSet())
        assertEquals(3, store.forDevice(null).size)

        store.markInvalid(a, true)
        assertEquals("B", store.forDevice("kitchen").first().name) // 无效的排后面
        assertTrue(store.forDevice("kitchen").last().invalid)
    }

    @Test
    fun `store remove and playing flag`() {
        val store = InMemoryRadioStationStore()
        val id = store.add(RadioStation("A", "http://h/a"))
        store.setPlaying(id, true)
        assertTrue(store.all().single().playing)
        assertTrue(store.remove(id))
        assertFalse(store.remove(id))
        assertTrue(store.all().isEmpty())
    }
}
