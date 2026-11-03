package io.github.laozhudu.linkplay.protocol.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepTimerTest {

    private val t0 = 1_700_000_000_000L

    @Test
    fun `inactive timer is never due`() {
        val t = SleepTimer()
        assertFalse(t.active)
        assertFalse("没设定时器时不能误判到期", t.isDue(t0 + 999_999_999))
        assertEquals(0L, t.remainingMs(t0))
    }

    @Test
    fun `start sets end time relative to now`() {
        val t = SleepTimer().start(30, t0)
        assertTrue(t.active)
        assertEquals(t0 + 30 * 60_000L, t.endAtMs)
        assertEquals(30 * 60_000L, t.remainingMs(t0))
    }

    @Test
    fun `zero or negative minutes cancels instead of scheduling`() {
        assertEquals(SleepTimer(), SleepTimer().start(0, t0))
        assertEquals(SleepTimer(), SleepTimer().start(-5, t0))
    }

    @Test
    fun `becomes due exactly at the deadline and stays due`() {
        val t = SleepTimer().start(15, t0)
        val end = t0 + 15 * 60_000L
        assertFalse(t.isDue(end - 1))
        assertTrue(t.isDue(end))
        assertTrue(t.isDue(end + 60_000L))
    }

    @Test
    fun `remaining never goes negative`() {
        val t = SleepTimer().start(1, t0)
        assertEquals(0L, t.remainingMs(t0 + 10 * 60_000L))
    }

    @Test
    fun `remaining text rounds up so it never shows 0 00 while still playing`() {
        val t = SleepTimer().start(1, t0)
        // 还剩 400ms，向上取整应显示 0:01 而不是 0:00
        assertEquals("0:01", t.remainingText(t0 + 60_000L - 400))
        assertEquals("1:00", t.remainingText(t0))
    }

    @Test
    fun `cancel clears the deadline`() {
        val t = SleepTimer().start(45, t0).cancel()
        assertFalse(t.active)
        assertFalse(t.isDue(t0 + 45 * 60_000L))
    }

    @Test
    fun `presets are all positive and sorted`() {
        assertTrue(SleepTimer.PRESETS.isNotEmpty())
        assertTrue(SleepTimer.PRESETS.all { it > 0 })
        assertEquals(SleepTimer.PRESETS.sorted(), SleepTimer.PRESETS)
    }
}
