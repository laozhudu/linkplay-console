package io.github.laozhudu.linkplay.protocol.upnp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpnpModelsTest {

    @Test
    fun `transport state maps wire values`() {
        assertEquals(TransportState.PLAYING, TransportState.from("PLAYING"))
        assertEquals(TransportState.PAUSED_PLAYBACK, TransportState.from("paused_playback"))
        assertEquals(TransportState.NO_MEDIA_PRESENT, TransportState.from(" NO_MEDIA_PRESENT "))
        assertEquals(TransportState.UNKNOWN, TransportState.from("SOMETHING_NEW"))
        assertEquals(TransportState.UNKNOWN, TransportState.from(null))
        assertTrue(TransportState.PLAYING.isPlaying)
        assertFalse(TransportState.STOPPED.isPlaying)
    }

    @Test
    fun `transport info parses standard response`() {
        val xml = """
            <u:GetTransportInfoResponse xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
              <CurrentTransportState>PLAYING</CurrentTransportState>
              <CurrentTransportStatus>OK</CurrentTransportStatus>
              <CurrentSpeed>1</CurrentSpeed>
            </u:GetTransportInfoResponse>
        """.trimIndent()
        val info = TransportInfo.parse(xml)
        assertEquals(TransportState.PLAYING, info.state)
        assertEquals("OK", info.status)
        assertEquals("1", info.speed)
    }

    @Test
    fun `time parsing handles h mm ss and mm ss`() {
        assertEquals(0, PositionInfo.parseTime("00:00:00"))
        assertEquals(201, PositionInfo.parseTime("0:03:21"))
        assertEquals(201, PositionInfo.parseTime("00:03:21"))
        assertEquals(90, PositionInfo.parseTime("1:30"))
        assertEquals(0, PositionInfo.parseTime("garbage"))
        assertEquals(0, PositionInfo.parseTime(null))
        assertEquals(0, PositionInfo.parseTime(""))
    }

    @Test
    fun `time formatting pads minutes and seconds`() {
        assertEquals("0:00", PositionInfo.formatTime(0))
        assertEquals("3:21", PositionInfo.formatTime(201))
        assertEquals("1:00:05", PositionInfo.formatTime(3605))
    }

    @Test
    fun `position info parses metadata and progress`() {
        val didl = UpnpXml.escape(
            """<DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/" """ +
                """xmlns:dc="http://purl.org/dc/elements/1.1/" """ +
                """xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/">""" +
                """<item id="0" parentID="-1" restricted="1">""" +
                """<dc:title>Test Track</dc:title>""" +
                """<upnp:artist>Nobody</upnp:artist>""" +
                """<upnp:album>Nowhere</upnp:album>""" +
                """<res protocolInfo="http-get:*:audio/mpeg:*">http://192.168.1.5:8080/t.mp3</res>""" +
                """</item></DIDL-Lite>""",
        )
        val xml = """
            <u:GetPositionInfoResponse xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
              <Track>1</Track>
              <TrackDuration>0:04:00</TrackDuration>
              <TrackMetaData>$didl</TrackMetaData>
              <TrackURI>http://192.168.1.5:8080/t.mp3</TrackURI>
              <RelTime>0:01:00</RelTime>
              <AbsTime>NOT_IMPLEMENTED</AbsTime>
            </u:GetPositionInfoResponse>
        """.trimIndent()

        val pos = PositionInfo.parse(xml)
        assertEquals(240, pos.trackDurationSec)
        assertEquals(60, pos.relTimeSec)
        assertEquals(0.25f, pos.progress, 0.001f)
        assertEquals(1, pos.track)
        assertEquals("http://192.168.1.5:8080/t.mp3", pos.trackUri)
        assertEquals("Test Track", pos.trackMetadata?.title)
        assertEquals("Nobody", pos.trackMetadata?.artist)
        assertEquals("Nowhere", pos.trackMetadata?.album)
        assertEquals("http-get:*:audio/mpeg:*", pos.trackMetadata?.protocolInfo)
    }

    @Test
    fun `empty metadata yields null instead of throwing`() {
        val xml = """
            <u:GetPositionInfoResponse>
              <TrackDuration>0:00:00</TrackDuration>
              <TrackMetaData></TrackMetaData>
              <TrackURI>NOT_IMPLEMENTED</TrackURI>
              <RelTime>0:00:00</RelTime>
            </u:GetPositionInfoResponse>
        """.trimIndent()
        val pos = PositionInfo.parse(xml)
        assertNull(pos.trackMetadata)
        assertNull(pos.trackUri)
        assertEquals(0f, pos.progress, 0.001f)
    }

    @Test
    fun `volume parsing handles muted device`() {
        val xml = """
            <u:GetVolumeResponse xmlns:u="urn:schemas-upnp-org:service:RenderingControl:1">
              <CurrentVolume>42</CurrentVolume>
              <CurrentMute>1</CurrentMute>
            </u:GetVolumeResponse>
        """.trimIndent()
        val v = VolumeInfo.parse(xml)
        assertEquals(42, v.volume)
        assertTrue(v.mute)
    }

    @Test
    fun `volume parsing defaults when fields missing`() {
        val v = VolumeInfo.parse("<u:GetVolumeResponse></u:GetVolumeResponse>")
        assertEquals(0, v.volume)
        assertFalse(v.mute)
    }
}
