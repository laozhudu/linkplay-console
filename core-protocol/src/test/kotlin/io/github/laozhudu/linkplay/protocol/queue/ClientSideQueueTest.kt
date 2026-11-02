package io.github.laozhudu.linkplay.protocol.queue

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 用假播放器 + 假时钟验证「何时推下一首」，不真等待。 */
class ClientSideQueueTest {

    private class FakeTarget(private val durations: List<Long>) : QueueTarget {
        val played = ArrayList<String>()
        var index = 0
        var positionInTrack = 0L
        var state = "STOPPED"
        var returnNullPosition = false

        override fun play(item: QueueItem) {
            played += item.title
            index = durations.indexOfFirst { it == item.durationSec }.coerceAtLeast(0)
            positionInTrack = 0
            state = "PLAYING"
        }

        override fun position(): Pair<Long, Long>? {
            if (returnNullPosition) return null
            val d = durations.getOrElse(index) { 0 }
            return positionInTrack to d
        }

        override fun transportState(): String = state
    }

    private class FakeClock {
        var t = 0L
        fun now() = t
        fun sleep(ms: Long) {
            t += ms
        }
    }

    private fun item(n: Int, dur: Long) = QueueItem("http://nas/$n.mp3", "T$n", durationSec = dur)

    /** 让假播放器随假时钟推进 */
    private fun advance(target: FakeTarget, clock: FakeClock, ms: Long) {
        clock.sleep(ms)
        target.positionInTrack += ms / 1000
        val d = target.durations()
        if (target.positionInTrack >= d) target.state = "STOPPED"
    }

    private fun FakeTarget.durations(): Long = (position()?.second ?: 0)

    @Test
    fun `sequential playback pushes the next track when the current one nears its end`() {
        val dur = 5L
        val target = FakeTarget(listOf(dur, dur, dur))
        val clock = FakeClock()
        val queue = ClientSideQueue(target, advanceLeadSeconds = 2, pollIntervalMs = 1_000)
        queue.loopMode = ClientSideQueue.LOOP_NONE

        // 每次 sleep 推进 1 秒；5 秒曲目在剩余 ≤2 秒（第 4 秒）时应切歌
        val played = queue.run({ listOf(item(1, dur), item(2, dur), item(3, dur)) }()
            .let { items -> items },
            sleep = { ms -> advance(target, clock, ms) },
            now = clock::now,
        )
        assertEquals(3, played)
        assertEquals(listOf("T1", "T2", "T3"), target.played)
    }

    @Test
    fun `single loop repeats the same track`() {
        val target = FakeTarget(listOf(3L))
        val clock = FakeClock()
        val queue = ClientSideQueue(target, advanceLeadSeconds = 1, pollIntervalMs = 1_000)
        queue.loopMode = ClientSideQueue.LOOP_SINGLE

        var launches = 0
        queue.run(
            listOf(item(1, 3L)),
            sleep = { ms -> advance(target, clock, ms) },
            now = clock::now,
            shouldStop = { ++launches > 40 },
        )
        assertTrue("单曲循环应该重复投同一首", target.played.size >= 2)
        assertTrue(target.played.all { it == "T1" })
    }

    @Test
    fun `list loop wraps around once and stops`() {
        val target = FakeTarget(listOf(2L, 2L))
        val clock = FakeClock()
        val queue = ClientSideQueue(target, advanceLeadSeconds = 1, pollIntervalMs = 1_000)
        queue.loopMode = ClientSideQueue.LOOP_ALL

        val played = queue.run(
            listOf(item(1, 2L), item(2, 2L)),
            sleep = { ms -> advance(target, clock, ms) },
            now = clock::now,
        )
        // 列表循环只跑完一轮就返回（避免无限循环），且两首都播过
        assertTrue(played >= 2)
        assertTrue(target.played.contains("T1"))
        assertTrue(target.played.contains("T2"))
    }

    @Test
    fun `missing position readings are tolerated then abandoned`() {
        val target = FakeTarget(listOf(10L)).apply { returnNullPosition = true }
        val clock = FakeClock()
        val queue = ClientSideQueue(target, pollIntervalMs = 1_000, maxMisses = 3)

        val played = queue.run(
            listOf(item(1, 10L), item(2, 10L)),
            sleep = { ms -> clock.sleep(ms) },
            now = clock::now,
        )
        // 读不到位置时只播第一首就退出，不会盲目往下推
        assertEquals(1, played)
    }

    @Test
    fun `external stop halts the queue`() {
        val target = FakeTarget(listOf(100L))
        val clock = FakeClock()
        val queue = ClientSideQueue(target, pollIntervalMs = 1_000)
        var polls = 0

        val played = queue.run(
            listOf(item(1, 100L), item(2, 100L)),
            sleep = { ms -> clock.sleep(ms) },
            now = clock::now,
            shouldStop = { polls++ > 2 },
        )
        assertEquals(1, played)
    }

    @Test
    fun `stops at end of list for LOOP_NONE`() {
        val target = FakeTarget(listOf(1L, 1L))
        val clock = FakeClock()
        val queue = ClientSideQueue(target, advanceLeadSeconds = 0, pollIntervalMs = 1_000)
        queue.loopMode = ClientSideQueue.LOOP_NONE

        val played = queue.run(
            listOf(item(1, 1L), item(2, 1L)),
            sleep = { ms -> advance(target, clock, ms) },
            now = clock::now,
        )
        assertEquals(2, played)
    }

    @Test
    fun `mode labels are stable`() {
        assertEquals("列表循环", ClientSideQueue.label(ClientSideQueue.LOOP_ALL))
        assertEquals("单曲循环", ClientSideQueue.label(ClientSideQueue.LOOP_SINGLE))
        assertEquals("顺序播放", ClientSideQueue.label(ClientSideQueue.LOOP_NONE))
        assertEquals("随机", ClientSideQueue.label(ClientSideQueue.LOOP_SHUFFLE))
    }
}
