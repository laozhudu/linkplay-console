package io.github.laozhudu.linkplay.protocol.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 可编程的假引擎：按调用序号决定是否抛错，并记录调用。 */
private class FakeEngine(
    override val channelName: String,
    private val failSnapshot: (Int) -> Boolean = { false },
    private val failWrites: (Int) -> Boolean = { false },
) : PlaybackEngine {
    var snapshotCalls = 0; private set
    var writeCalls = 0; private set
    var lastUrl: String? = null
    var lastVolume = 0

    override fun snapshot(): PlaybackSnapshot {
        snapshotCalls++
        if (failSnapshot(snapshotCalls)) throw IllegalStateException("$channelName 不可用")
        return PlaybackSnapshot(
            state = io.github.laozhudu.linkplay.protocol.upnp.TransportState.PLAYING,
            title = "$channelName-title",
            artist = null, albumArtUrl = null,
            positionSec = 10, durationSec = 100,
            volume = lastVolume, mute = false, sourceLabel = channelName,
        )
    }

    override fun playUrl(url: String, title: String?) {
        writeCalls++
        if (failWrites(writeCalls)) throw IllegalStateException("$channelName 写入失败")
        lastUrl = url
    }

    override fun resume() { writeCalls++ }
    override fun pause() { writeCalls++ }
    override fun stop() { writeCalls++ }
    override fun seek(seconds: Long) { writeCalls++ }
    override fun setVolume(volume: Int) { writeCalls++; lastVolume = volume }
    override fun setMute(mute: Boolean) { writeCalls++ }
}

private class Clock { var t = 0L; fun now() = t }

class FallbackPlaybackEngineTest {

    @Test
    fun `主通道正常时不碰备用通道`() {
        val secondary = FakeEngine("HTTP")
        val e = FallbackPlaybackEngine(FakeEngine("UPnP"), secondary)
        assertEquals("UPnP-title", e.snapshot().title)
        assertFalse(e.degraded)
        assertEquals(0, secondary.snapshotCalls)
    }

    @Test
    fun `读失败立即降级并返回备用通道的状态`() {
        val e = FallbackPlaybackEngine(
            FakeEngine("UPnP", failSnapshot = { it == 1 }),
            FakeEngine("HTTP"),
        )
        assertEquals("HTTP-title", e.snapshot().title)
        assertTrue(e.degraded)
        assertEquals("HTTP", e.channelName)
    }

    @Test
    fun `写失败一次不降级，两次才切并在备用通道重放`() {
        val primary = FakeEngine("UPnP", failWrites = { true })
        val secondary = FakeEngine("HTTP")
        val e = FallbackPlaybackEngine(primary, secondary)
        assertTrue(runCatching { e.playUrl("http://a/1.mp3") }.isFailure)
        assertFalse(e.degraded)
        assertNull(runCatching { e.playUrl("http://a/2.mp3") }.exceptionOrNull())
        assertTrue(e.degraded)
        assertEquals("http://a/2.mp3", secondary.lastUrl)
    }

    @Test
    fun `备用通道也失败时如实抛出而不是假装降级成功`() {
        val e = FallbackPlaybackEngine(
            FakeEngine("UPnP", failSnapshot = { true }),
            FakeEngine("HTTP", failSnapshot = { true }),
        )
        val err = runCatching { e.snapshot() }.exceptionOrNull()
        assertTrue(err is IllegalStateException)
        assertEquals("HTTP 不可用", err?.message)
    }

    @Test
    fun `冷却期到点后探测主通道并自动切回`() {
        val clock = Clock()
        val primary = FakeEngine("UPnP", failSnapshot = { it == 1 })
        val e = FallbackPlaybackEngine(
            primary, FakeEngine("HTTP"),
            retryAfterMs = 60_000, now = clock::now,
        )
        e.snapshot()
        assertTrue(e.degraded)

        clock.t += 59_000
        e.snapshot()
        assertTrue("冷却未到不应切回", e.degraded)

        clock.t += 2_000
        e.snapshot()
        assertFalse("冷却到点且主通道恢复，应切回", e.degraded)
        assertEquals("UPnP", e.channelName)
    }

    @Test
    fun `恢复后再次坏掉仍能降级`() {
        val clock = Clock()
        val flaky = FakeEngine("UPnP", failSnapshot = { it == 1 || it >= 5 })
        val e = FallbackPlaybackEngine(
            flaky, FakeEngine("HTTP"), retryAfterMs = 1_000, now = clock::now,
        )
        e.snapshot()
        assertTrue(e.degraded)
        clock.t += 1_500
        e.snapshot()
        assertFalse(e.degraded)
        e.snapshot()
        e.snapshot()
        e.snapshot()
        assertTrue("恢复后再次坏掉应当还能降级", e.degraded)
    }

    @Test
    fun `降级后音量写入仍能生效`() {
        val secondary = FakeEngine("HTTP")
        val e = FallbackPlaybackEngine(
            FakeEngine("UPnP", failSnapshot = { it == 1 }), secondary,
            writeFailureThreshold = 1,
        )
        e.snapshot()
        e.setVolume(42)
        assertEquals(42, secondary.lastVolume)
    }
}
