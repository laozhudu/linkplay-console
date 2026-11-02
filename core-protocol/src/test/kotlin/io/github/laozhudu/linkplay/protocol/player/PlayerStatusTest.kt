package io.github.laozhudu.linkplay.protocol.player

import io.github.laozhudu.linkplay.protocol.LinkPlayCommands
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerStatusTest {

    private val sample = """
        {"type":"0","ch":"2","mode":"10","loop":"4","eq":"0","status":"play",
         "curpos":"184919","offset_pts":"184919","totlen":"240000","alarmflag":"0",
         "plicount":"0","plicurr":"0","vol":"39","mute":"0"}
    """.trimIndent()

    @Test
    fun `parses the official getPlayerStatus payload`() {
        val s = PlayerStatus.parse(sample)
        assertEquals(0, s.type)
        assertEquals(2, s.channel)
        assertEquals(10, s.mode)
        assertEquals(4, s.loopMode)
        assertEquals(PlayState.PLAY, s.playState)
        assertEquals(184_919L, s.positionMs)
        assertEquals(184L, s.positionSec)
        assertEquals(240_000L, s.durationMs)
        assertEquals(39, s.volume)
        assertFalse(s.mute)
    }

    @Test
    fun `progress is computed from milliseconds`() {
        val s = PlayerStatus.parse(sample)
        assertEquals(0.7705f, s.progress, 0.001f)
    }

    @Test
    fun `live stream with zero duration has no progress`() {
        val s = PlayerStatus.parse("""{"status":"play","curpos":"5000","totlen":"0","vol":"10","mute":"0"}""")
        assertEquals(0f, s.progress, 0.001f)
    }

    @Test
    fun `string and numeric field types are both tolerated`() {
        val s = PlayerStatus.parse("""{"status":"pause","curpos":1234,"totlen":60000,"vol":17,"mute":1}""")
        assertEquals(PlayState.PAUSE, s.playState)
        assertEquals(1234L, s.positionMs)
        assertEquals(17, s.volume)
        assertTrue(s.mute)
    }

    @Test
    fun `unknown status maps to UNKNOWN`() {
        assertEquals(PlayState.UNKNOWN, PlayState.from("buffering"))
        assertEquals(PlayState.UNKNOWN, PlayState.from(null))
    }

    @Test
    fun `mode labels follow the official table`() {
        assertEquals("AirPlay", PlayerStatus.modeLabel(1))
        assertEquals("DLNA", PlayerStatus.modeLabel(2))
        assertEquals("本地播放列表", PlayerStatus.modeLabel(16))
        assertEquals("Spotify Connect", PlayerStatus.modeLabel(31))
        assertEquals("AUX-In", PlayerStatus.modeLabel(40))
        assertEquals("蓝牙", PlayerStatus.modeLabel(41))
        assertEquals("光纤输入", PlayerStatus.modeLabel(43))
        assertEquals("从机", PlayerStatus.modeLabel(99))
        assertTrue(PlayerStatus.modeLabel(25).contains("保留"))
        assertTrue(PlayerStatus.modeLabel(7).contains("7"))
    }

    @Test
    fun `metadata parses nested metaData and tolerates trailing spaces in keys`() {
        val json = """
            {"metaData":{"album":"Country Heat","title":"Old Dirt Roads","artist":"Owen Riegling",
             "albumArtURI ":"https://h/a.jpg","sampleRate ":"44100","bitDepth":"16"}}
        """.trimIndent()
        val m = PlayerMetadata.parse(json)!!
        assertEquals("Old Dirt Roads", m.title)
        assertEquals("Owen Riegling", m.artist)
        assertEquals("Country Heat", m.album)
        assertEquals("https://h/a.jpg", m.albumArtUrl)
        assertEquals(44100, m.sampleRate)
        assertEquals(16, m.bitDepth)
    }

    @Test
    fun `metadata without wrapper still parses`() {
        val m = PlayerMetadata.parse("""{"title":"T","artist":"A"}""")!!
        assertEquals("T", m.title)
    }

    @Test
    fun `metadata returns null on garbage`() {
        assertEquals(null, PlayerMetadata.parse("not json"))
    }

    @Test
    fun `playback commands match the documented spelling`() {
        assertEquals("getPlayerStatus", LinkPlayCommands.getPlayerStatus())
        assertEquals("setPlayerCmd:play:http://h/a.mp3", LinkPlayCommands.playUrl("http://h/a.mp3"))
        assertEquals("setPlayerCmd:pause", LinkPlayCommands.pause())
        assertEquals("setPlayerCmd:resume", LinkPlayCommands.resume())
        assertEquals("setPlayerCmd:onepause", LinkPlayCommands.togglePause())
        assertEquals("setPlayerCmd:stop", LinkPlayCommands.stopPlayer())
        assertEquals("setPlayerCmd:seek:90", LinkPlayCommands.seekPlayer(90))
        assertEquals("setPlayerCmd:vol:39", LinkPlayCommands.playerVolume(39))
        assertEquals("setPlayerCmd:mute:1", LinkPlayCommands.playerMute(true))
        assertEquals("setPlayerCmd:loopmode:-1", LinkPlayCommands.loopMode(-1))
        assertEquals("setShutdown:-1", LinkPlayCommands.shutdown(-1))
        assertEquals("EQLoad:Flat", LinkPlayCommands.eqLoad("Flat"))
    }

    @Test
    fun `volume and seek arguments are clamped`() {
        assertEquals("setPlayerCmd:vol:100", LinkPlayCommands.playerVolume(500))
        assertEquals("setPlayerCmd:vol:0", LinkPlayCommands.playerVolume(-3))
        assertEquals("setPlayerCmd:seek:0", LinkPlayCommands.seekPlayer(-10))
    }
}
