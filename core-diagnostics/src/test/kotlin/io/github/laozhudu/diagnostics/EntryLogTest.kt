package io.github.laozhudu.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EntryLogTest {

    private val clock = Clock.ticking(1_000_000L, stepMs = 1000)

    @Test
    fun `ring keeps only the newest entries`() {
        val log = EntryLog(capacity = 3, clock = clock)
        (1..5).forEach { log.info("s", "m$it") }
        assertEquals(3, log.size)
        assertEquals(listOf("m5", "m4", "m3"), log.recent().map { it.message })
    }

    @Test
    fun `recent is newest first`() {
        val log = EntryLog(clock = clock)
        log.info("s", "first")
        log.info("s", "second")
        assertEquals("second", log.recent().first().message)
    }

    @Test
    fun `error carries class name and stack but never the word null`() {
        val log = EntryLog(clock = clock)
        log.error("probeSaved", IllegalStateException())  // message 是 null
        val e = log.recent().single()
        assertEquals(Severity.ERROR, e.severity)
        assertEquals("IllegalStateException", e.message)
        assertFalse(e.message.contains("null"))
        assertTrue(e.detail!!.contains("IllegalStateException"))
    }

    @Test
    fun `counts sum to size and cover every severity`() {
        val log = EntryLog(clock = clock)
        log.info("s", "a")
        log.warn("s", "b")
        log.error("s", RuntimeException("x"))
        val counts = log.counts()
        assertEquals(4, counts.size)
        assertEquals(1, counts[Severity.WARN])
        assertEquals(3, counts.values.sum())
    }

    @Test
    fun `timed records success with duration`() {
        // 成功也要记：否则"没反应"和"根本没触发"分不开
        val log = EntryLog(clock = clock)
        val value = log.timed("getStatusEx") { 42 }
        assertEquals(42, value)
        val e = log.recent().single()
        assertEquals(Severity.INFO, e.severity)
        assertTrue(e.message.startsWith("完成 "))
    }

    @Test
    fun `timed records failure and rethrows`() {
        val log = EntryLog(clock = clock)
        var caught = false
        try {
            log.timed("getStatusEx") { throw java.io.IOException("boom") }
        } catch (e: java.io.IOException) {
            caught = true
        }
        assertTrue(caught)                       // 不吞异常，边界在外面
        assertEquals(Severity.ERROR, log.recent().single().severity)
    }

    @Test
    fun `encode decode round trips severity and time`() {
        val log = EntryLog(clock = clock)
        log.warn("playQueueItem", "设备没回 OK")
        val restored = EntryLog(clock = clock)
        restored.decode(log.encode())
        val e = restored.recent().single()
        assertEquals(Severity.WARN, e.severity)
        assertEquals("playQueueItem", e.source)
        assertEquals("设备没回 OK", e.message)
    }

    @Test
    fun `decode skips malformed lines instead of throwing`() {
        val log = EntryLog(clock = clock)
        log.decode("garbage\n|||\n123|W|ok|fine")
        assertEquals(1, log.size)
        assertEquals("fine", log.recent().single().message)
    }

    @Test
    fun `downstream sink sees every entry`() {
        val seen = mutableListOf<DiagnosticEntry>()
        val log = EntryLog(clock = clock, downstream = { seen.add(it) })
        log.info("s", "a")
        log.warn("s", "b")
        assertEquals(2, seen.size)
    }

    @Test
    fun `clear empties`() {
        val log = EntryLog(clock = clock)
        log.info("s", "a")
        log.clear()
        assertEquals(0, log.size)
    }
}
