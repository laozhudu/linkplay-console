package io.github.laozhudu.linkplay.protocol.player

import io.github.laozhudu.linkplay.protocol.DeviceHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 用假的 HTTP 客户端记录设备收到的命令，验证 httpapi 通道的行为映射。 */
class HttpApiPlaybackEngineTest {

    private class FakeDeviceClient : DeviceHttpClient() {
        val commands = ArrayList<String>()
        var statusJson: String = """{"status":"play","curpos":"60000","totlen":"120000","vol":"30","mute":"0","mode":"10"}"""
        var metaJson: String? = """{"metaData":{"title":"Song A","artist":"Someone"}}"""

        override fun get(deviceIp: String, command: String, path: String, readTimeoutMs: Int): String {
            commands.add(command)
            return when {
                command.startsWith("getPlayerStatus") -> statusJson
                command.startsWith("getMetaInfo") -> metaJson ?: "{}"
                else -> "OK"
            }
        }
    }

    @Test
    fun `snapshot maps play state and metadata`() {
        val fake = FakeDeviceClient()
        val engine = HttpApiPlaybackEngine(fake, "192.168.1.20")

        val snap = engine.snapshot()
        assertEquals(ChannelOrState.PLAYING, ChannelOrState.of(snap.state.wire))
        assertEquals("Song A", snap.title)
        assertEquals("Someone", snap.artist)
        assertEquals(60L, snap.positionSec)
        assertEquals(120L, snap.durationSec)
        assertEquals(30, snap.volume)
        assertEquals(0.5f, snap.progress, 0.01f)
        // 改版后 sourceLabel 带上"输入源 · 模式细节"，因为界面上要同时看出
        // 「现在在哪一路」和「这一路里在做什么」（用户要求输入源可读可标）
        assertEquals("网络播放 · 本地播放列表", snap.sourceLabel)
        assertEquals("wifi", snap.sourceId)
        assertEquals(10, snap.mode)
        assertEquals("HTTP API", engine.channelName)
    }

    @Test
    fun `actions send the documented commands`() {
        val fake = FakeDeviceClient()
        val engine = HttpApiPlaybackEngine(fake, "192.168.1.20")

        engine.playUrl("http://h/a.mp3")
        engine.pause()
        engine.resume()
        engine.stop()
        engine.seek(45)
        engine.setVolume(55)
        engine.setMute(true)

        assertEquals(
            listOf(
                "setPlayerCmd:play:http://h/a.mp3",
                "setPlayerCmd:pause",
                "setPlayerCmd:resume",
                "setPlayerCmd:stop",
                "setPlayerCmd:seek:45",
                "setPlayerCmd:vol:55",
                "setPlayerCmd:mute:1",
            ),
            fake.commands,
        )
    }

    @Test
    fun `probe fails when the device rejects getPlayerStatus`() {
        val broken = object : DeviceHttpClient() {
            override fun get(deviceIp: String, command: String, path: String, readTimeoutMs: Int): String =
                throw java.io.IOException("404")
        }
        assertFalse(HttpApiPlaybackEngine(broken, "192.168.1.20").probe())
    }

    @Test
    fun `probe succeeds on a normal payload`() {
        assertTrue(HttpApiPlaybackEngine(FakeDeviceClient(), "192.168.1.20").probe())
    }

    @Test
    fun `missing metadata degrades to nulls without failing snapshot`() {
        val fake = FakeDeviceClient().apply { metaJson = null }
        val snap = HttpApiPlaybackEngine(fake, "192.168.1.20").snapshot()
        assertEquals(null, snap.title)
        assertEquals(60L, snap.positionSec)
    }

    /** 小工具：把 didl 状态转成可断言的常量，避免测试依赖 upnp 包的枚举细节。 */
    private object ChannelOrState {
        const val PLAYING = "PLAYING"
        fun of(wire: String) = wire
    }
}
