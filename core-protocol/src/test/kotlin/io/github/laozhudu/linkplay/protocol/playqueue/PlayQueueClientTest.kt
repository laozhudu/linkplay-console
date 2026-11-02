package io.github.laozhudu.linkplay.protocol.playqueue

import io.github.laozhudu.linkplay.protocol.upnp.DeviceDescription
import io.github.laozhudu.linkplay.protocol.upnp.UpnpService
import io.github.laozhudu.linkplay.protocol.upnp.UpnpServices
import io.github.laozhudu.linkplay.protocol.upnp.UpnpTransport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayQueueClientTest {

    private class RecordingTransport : UpnpTransport {
        data class Call(val url: String, val soapAction: String, val body: String)

        val calls = ArrayList<Call>()
        var response = "<ok/>"

        override fun get(url: String): String = error("unexpected GET")

        override fun postSoap(url: String, soapAction: String, body: String): String {
            calls.add(Call(url, soapAction, body))
            return response
        }
    }

    private fun description(withPlayQueue: Boolean = true): DeviceDescription {
        val services = buildList {
            add(UpnpService(UpnpServices.AV_TRANSPORT, "id1", "http://h/av", null, null))
            if (withPlayQueue) {
                add(UpnpService(UpnpServices.WIIMU_PLAY_QUEUE, "id3", "http://h/pq", null, null))
            }
        }
        return DeviceDescription("http://h/d.xml", "LinkPlay", "WiiMu", "WiiMu Media player", "uuid:x", services)
    }

    @Test
    fun `service absence is reported instead of throwing on construction`() {
        val client = PlayQueueClient(description(withPlayQueue = false), RecordingTransport())
        assertFalse(client.isSupported)
        val error = runCatching { client.createQueue("x") }.exceptionOrNull()
        assertTrue(error is IllegalStateException)
        assertTrue(error!!.message!!.contains("PlayQueue"))
    }

    @Test
    fun `createQueue sends QueueContext to the private control url`() {
        val t = RecordingTransport()
        PlayQueueClient(description(), t).createQueue("<DIDL-Lite/>")

        val call = t.calls.single()
        assertEquals("http://h/pq", call.url)
        assertEquals("\"urn:schemas-wiimu-com:service:PlayQueue:1#CreateQueue\"", call.soapAction)
        assertTrue(call.body.contains("""<u:CreateQueue xmlns:u="urn:schemas-wiimu-com:service:PlayQueue:1">"""))
        // DIDL 作为参数值时被整体转义
        assertTrue(call.body.contains("&lt;DIDL-Lite/&gt;"))
    }

    @Test
    fun `playQueueWithIndex sends queue name then index`() {
        val t = RecordingTransport()
        PlayQueueClient(description(), t).playQueueWithIndex("q1", 3)

        val body = t.calls.single().body
        assertTrue(body.contains("<QueueName>q1</QueueName><Index>3</Index>"))
    }

    @Test
    fun `appendTracksEx keeps Direction before StartIndex`() {
        val t = RecordingTransport()
        PlayQueueClient(description(), t).appendTracksEx("ctx", startIndex = 5, direction = 1)

        val body = t.calls.single().body
        val dirAt = body.indexOf("<Direction>1</Direction>")
        val startAt = body.indexOf("<StartIndex>5</StartIndex>")
        assertTrue(dirAt in 0 until startAt)
    }

    @Test
    fun `removeTracks uses the device spelling RangStart and RangEnd`() {
        val t = RecordingTransport()
        PlayQueueClient(description(), t).removeTracks("q1", 2, 7)

        val body = t.calls.single().body
        assertTrue(body.contains("<RangStart>2</RangStart><RangEnd>7</RangEnd>"))
        assertFalse(body.contains("RangeStart"))
    }

    @Test
    fun `setQueueLoopMode sends the raw mode number`() {
        val t = RecordingTransport()
        val client = PlayQueueClient(description(), t)
        client.setQueueLoopMode(QueueLoopMode.ALL)

        assertTrue(t.calls.single().body.contains("<LoopMode>2</LoopMode>"))
        assertEquals("列表循环", QueueLoopMode.label(QueueLoopMode.ALL))
        assertEquals("单曲循环", QueueLoopMode.label(1))
        assertEquals("不循环", QueueLoopMode.label(0))
        assertEquals("随机", QueueLoopMode.label(3))
    }

    @Test
    fun `getQueueIndex parses response and tolerates missing value`() {
        val t = RecordingTransport().apply { response = "<Index>7</Index>" }
        assertEquals(7, PlayQueueClient(description(), t).getQueueIndex("q1"))

        val empty = RecordingTransport().apply { response = "<ok/>" }
        assertNull(PlayQueueClient(description(), empty).getQueueIndex("q1"))
    }

    @Test
    fun `deleteQueue and browseQueue address the queue by name`() {
        val t = RecordingTransport()
        val client = PlayQueueClient(description(), t)
        client.deleteQueue("q1")
        client.browseQueue("q1")
        assertTrue(t.calls[0].body.contains("<u:DeleteQueue"))
        assertTrue(t.calls[0].body.contains("<QueueName>q1</QueueName>"))
        assertTrue(t.calls[1].body.contains("<u:BrowseQueue"))
    }
}
