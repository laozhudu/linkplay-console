package io.github.laozhudu.linkplay.protocol.session

import io.github.laozhudu.linkplay.protocol.Json
import io.github.laozhudu.linkplay.protocol.JsonValue
import io.github.laozhudu.linkplay.protocol.JsonWriter
import io.github.laozhudu.linkplay.protocol.queue.QueueItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionStoreModelTest {

    // ---- JsonWriter ----

    @Test
    fun `写入后能被自己的解析器读回`() {
        val v = JsonValue.Obj(
            mapOf(
                "s" to JsonValue.Str("a\"b\\c\nd\te"),
                "n" to JsonValue.Num(42.0),
                "f" to JsonValue.Num(1.5),
                "b" to JsonValue.Bool(true),
                "nil" to JsonValue.Null,
                "arr" to JsonValue.Arr(listOf(JsonValue.Str("x"), JsonValue.Num(1.0))),
            ),
        )
        val back = Json.parse(JsonWriter.encode(v)) as JsonValue.Obj
        assertEquals("a\"b\\c\nd\te", back.string("s"))
        assertEquals(42, back.int("n"))
        assertEquals(1.5, (back["f"] as JsonValue.Num).value, 0.0001)
        assertEquals(true, (back["b"] as JsonValue.Bool).value)
        assertEquals(2, back.array("arr")!!.items.size)
    }

    @Test
    fun `整数不输出成小数`() {
        val text = JsonWriter.encode(JsonValue.Num(8200.0))
        assertEquals("8200", text)
    }

    @Test
    fun `控制字符被转义`() {
        val text = JsonWriter.encode(JsonValue.Str("\u0001"))
        assertEquals("\"\\u0001\"", text)
    }

    // ---- NasSession ----

    @Test
    fun `NAS 会话能往返`() {
        val s = NasSession(
            server = NasServerRef("192.168.99.223", 8200, "fnos"),
            path = listOf(NasPathStep("1", "Music"), NasPathStep("1\$4", "All Music")),
        )
        val back = NasSession.decode(s.encode())!!
        assertEquals("192.168.99.223", back.server.host)
        assertEquals(8200, back.server.port)
        assertEquals("fnos", back.server.friendlyName)
        assertEquals(2, back.path.size)
        assertEquals("1\$4", back.path[1].objectId)
        assertEquals("All Music", back.path[1].title)
    }

    @Test
    fun `曲名带引号换行也能往返`() {
        val s = NasSession(
            server = NasServerRef("10.0.0.5", 80, "a\"b\nc"),
            path = listOf(NasPathStep("2", "《水浒传》\t第一回")),
        )
        val back = NasSession.decode(s.encode())!!
        assertEquals("a\"b\nc", back.server.friendlyName)
        assertEquals("《水浒传》\t第一回", back.path[0].title)
    }

    @Test
    fun `坏数据返回 null 而不是抛异常`() {
        assertNull(NasSession.decode(null))
        assertNull(NasSession.decode(""))
        assertNull(NasSession.decode("{not json"))
        assertNull(NasSession.decode("""{"path":[]}"""))            // 缺 server
        assertNull(NasSession.decode("""{"server":{"host":""}}"""))  // host 为空
    }

    @Test
    fun `端口非法时回退 8200`() {
        val back = NasSession.decode("""{"server":{"host":"1.2.3.4","port":99999}}""")!!
        assertEquals(8200, back.server.port)
    }

    @Test
    fun `label 在 80 端口不显示端口号`() {
        assertEquals("10.0.0.1", NasServerRef("10.0.0.1", 80).label)
        assertEquals("10.0.0.1:8200", NasServerRef("10.0.0.1", 8200).label)
    }

    // ---- PlaybackSession ----

    private fun item(n: Int) = QueueItem(
        url = "http://nas/MediaItems/$n.mp3",
        title = "第 $n 首 \"quoted\"",
        artist = "作者$n",
        album = null,
        durationSec = 120L + n,
        mimeType = "audio/mpeg",
    )

    @Test
    fun `播放会话能往返并保留索引`() {
        val s = PlaybackSession(
            deviceUuid = "FF31F0174FE5C9B2A2ACEAE3",
            deviceIp = "192.168.99.153",
            items = (1..3).map(::item),
            index = 1,
            loopMode = 3,
        )
        val back = PlaybackSession.decode(s.encode())!!
        assertEquals("FF31F0174FE5C9B2A2ACEAE3", back.deviceUuid)
        assertEquals("192.168.99.153", back.deviceIp)
        assertEquals(3, back.items.size)
        assertEquals(1, back.index)
        assertEquals(3, back.loopMode)
        assertEquals("第 2 首 \"quoted\"", back.items[1].title)
        assertEquals(122L, back.items[1].durationSec)
        assertNull(back.items[0].album)
    }

    @Test
    fun `remaining 从索引开始`() {
        val s = PlaybackSession("u", "ip", (1..3).map(::item), index = 1, loopMode = 2)
        assertEquals(2, s.remaining.size)
        assertEquals("第 2 首 \"quoted\"", s.remaining.first().title)
    }

    @Test
    fun `索引越界被夹回范围`() {
        val s = PlaybackSession.decode(
            JsonWriter.encode(
                JsonValue.Obj(
                    mapOf(
                        "uuid" to JsonValue.Str("u"),
                        "ip" to JsonValue.Str("i"),
                        "index" to JsonValue.Num(99.0),
                        "items" to JsonValue.Arr(
                            listOf(
                                JsonValue.Obj(
                                    mapOf("url" to JsonValue.Str("http://a/1.mp3"), "title" to JsonValue.Str("t")),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )!!
        assertEquals(0, s.index)
    }

    @Test
    fun `空队列或坏数据返回 null`() {
        assertNull(PlaybackSession.decode(""))
        assertNull(PlaybackSession.decode("""{"uuid":"u"}"""))
        // 有 uuid/ip 但没有曲目 → 无意义，应当作没有会话
        assertNull(PlaybackSession.decode("""{"uuid":"u","ip":"i","items":[]}"""))
    }

    @Test
    fun `缺 mime 时用默认值`() {
        val s = PlaybackSession.decode(
            """{"uuid":"u","ip":"i","items":[{"url":"http://a/1.mp3","title":"t"}]}""",
        )!!
        assertEquals("audio/mpeg", s.items[0].mimeType)
        assertEquals(0L, s.items[0].durationSec)
        assertTrue(s.items[0].artist == null)
    }
}
