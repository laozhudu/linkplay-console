package io.github.laozhudu.linkplay.protocol.metadata

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 内嵌标签解析。
 *
 * 为什么自己造样本而不是拿真文件：真文件几十 MB 进不了仓库，
 * 而这里要覆盖的是**帧头布局**（不同编码、宽字符 NUL、2.2 的 3 字节帧号），
 * 用合成字节反而更精确。
 */
class TagParserTest {

    // ------------------------------------------------------------ 造 ID3

    private fun syncSafe(n: Int): ByteArray = byteArrayOf(
        ((n shr 21) and 0x7F).toByte(), ((n shr 14) and 0x7F).toByte(),
        ((n shr 7) and 0x7F).toByte(), (n and 0x7F).toByte(),
    )

    private fun beInt(n: Int): ByteArray = byteArrayOf(
        ((n shr 24) and 0xFF).toByte(), ((n shr 16) and 0xFF).toByte(),
        ((n shr 8) and 0xFF).toByte(), (n and 0xFF).toByte(),
    )

    private fun id3v23(vararg frames: Pair<String, ByteArray>): ByteArray {
        val body = java.io.ByteArrayOutputStream()
        frames.forEach { (id, data) ->
            body.write(id.toByteArray(Charsets.ISO_8859_1))
            body.write(beInt(data.size))
            body.write(byteArrayOf(0, 0))          // flags
            body.write(data)
        }
        val payload = body.toByteArray()
        return java.io.ByteArrayOutputStream().apply {
            write(byteArrayOf('I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(), 3, 0, 0))
            write(syncSafe(payload.size))
            write(payload)
        }.toByteArray()
    }

    /** 文本帧：编码 3 = UTF-8 */
    private fun textFrame(s: String) = byteArrayOf(3) + s.toByteArray(Charsets.UTF_8)

    /** APIC：编码 0(ISO-8859-1) + "image/png\0" + 图片类型 3 + 空描述 + 数据 */
    private fun apicFrame(mime: String, data: ByteArray) =
        byteArrayOf(0) + mime.toByteArray(Charsets.ISO_8859_1) + byteArrayOf(0) +
            byteArrayOf(3) + byteArrayOf(0) + data

    /** USLT：编码 3(UTF-8) + "chi" + 空描述 + 歌词 */
    private fun usltFrame(lyrics: String) =
        byteArrayOf(3) + "chi".toByteArray(Charsets.ISO_8859_1) + byteArrayOf(0) +
            lyrics.toByteArray(Charsets.UTF_8)

    private val coverBytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 1, 2, 3, 4)

    @Test
    fun 解析_ID3v2_3_的标题歌手与封面() {
        val tags = TagParser.parse(
            id3v23(
                "TIT2" to textFrame("五百年桑田沧海"),
                "TPE1" to textFrame("未知歌手"),
                "TALB" to textFrame("五百年桑田沧海"),
                "APIC" to apicFrame("image/png", coverBytes),
            ),
        )
        assertNotNull(tags)
        assertEquals("五百年桑田沧海", tags!!.title)
        assertEquals("未知歌手", tags.artist)
        assertEquals("五百年桑田沧海", tags.album)
        assertTrue(tags.hasCover)
        assertEquals("image/png", tags.coverMime)
        assertTrue(tags.cover!!.contentEquals(coverBytes))
    }

    @Test
    fun 解析_USLT_内嵌歌词() {
        val lyrics = "长亭外，古道边\n芳草碧连天"
        val tags = TagParser.parse(id3v23("USLT" to usltFrame(lyrics)))
        assertEquals(lyrics, tags?.lyrics)
    }

    @Test
    fun 解析_UTF16_带_BOM_的标题() {
        val body = "中文标题".toByteArray(Charsets.UTF_16LE)
        val frame = byteArrayOf(1, 0xFF.toByte(), 0xFE.toByte()) + body
        val tags = TagParser.parse(id3v23("TIT2" to frame))
        assertEquals("中文标题", tags?.title)
    }

    @Test
    fun 没有封面时_hasCover_为假且不抛异常() {
        val tags = TagParser.parse(id3v23("TIT2" to textFrame("只有标题")))
        assertEquals("只有标题", tags?.title)
        assertNull(tags?.cover)
        assertTrue(tags?.hasCover == false)
    }

    @Test
    fun 不是标签文件返回_null() {
        assertNull(TagParser.parse(ByteArray(64)))
        assertNull(TagParser.parse(byteArrayOf(1, 2, 3)))
        assertNull(TagParser.parse(ByteArray(0)))
    }

    @Test
    fun 截断的_ID3_不崩且能拿到已完整解析的部分() {
        val full = id3v23("TIT2" to textFrame("完整标题"), "APIC" to apicFrame("image/jpeg", ByteArray(2000)))
        // 砍掉一半：标题帧完整，封面帧残缺 → 标题应可用，封面为空
        val cut = full.copyOfRange(0, full.size / 2)
        val tags = TagParser.parse(cut)
        assertNotNull(tags)
        assertEquals("完整标题", tags!!.title)
        assertTrue(!tags.hasCover)
    }

    // ------------------------------------------------------------ FLAC

    private fun leInt(n: Int): ByteArray = byteArrayOf(
        (n and 0xFF).toByte(), ((n shr 8) and 0xFF).toByte(),
        ((n shr 16) and 0xFF).toByte(), ((n shr 24) and 0xFF).toByte(),
    )

    private fun vorbisComment(vararg entries: Pair<String, String>): ByteArray {
        val vendor = "test".toByteArray()
        val out = java.io.ByteArrayOutputStream()
        out.write(leInt(vendor.size)); out.write(vendor)
        out.write(leInt(entries.size))
        entries.forEach { (k, v) ->
            val e = "$k=$v".toByteArray(Charsets.UTF_8)
            out.write(leInt(e.size)); out.write(e)
        }
        return out.toByteArray()
    }

    private fun flacBlock(type: Int, payload: ByteArray, last: Boolean = false): ByteArray {
        val header = byteArrayOf(
            ((if (last) 0x80 else 0) or type).toByte(),
            ((payload.size shr 16) and 0xFF).toByte(),
            ((payload.size shr 8) and 0xFF).toByte(),
            (payload.size and 0xFF).toByte(),
        )
        return header + payload
    }

    private fun flac(vararg blocks: ByteArray): ByteArray =
        byteArrayOf('f'.code.toByte(), 'L'.code.toByte(), 'a'.code.toByte(), 'C'.code.toByte()) +
            blocks.reduce { a, b -> a + b }

    @Test
    fun 解析_FLAC_的_VORBIS_COMMENT() {
        val bytes = flac(
            flacBlock(0, ByteArray(34)),          // STREAMINFO 占位
            flacBlock(4, vorbisComment("TITLE" to "海阔天空", "ARTIST" to "Beyond", "LYRICS" to "今天我")),
        )
        val tags = TagParser.parse(bytes)
        assertEquals("海阔天空", tags?.title)
        assertEquals("Beyond", tags?.artist)
        assertEquals("今天我", tags?.lyrics)
    }

    @Test
    fun 解析_FLAC_的_PICTURE() {
        val mime = "image/jpeg".toByteArray()
        val pic = java.io.ByteArrayOutputStream().apply {
            write(beInt(3))                        // 图片类型
            write(beInt(mime.size)); write(mime)
            write(beInt(0))                        // 描述长度
            write(beInt(1)); write(beInt(1))       // 宽高
            write(beInt(24)); write(beInt(0))      // 色深/调色板
            write(beInt(coverBytes.size)); write(coverBytes)
        }.toByteArray()
        val tags = TagParser.parse(
            flac(flacBlock(0, ByteArray(34)), flacBlock(6, pic, last = true)),
        )
        assertTrue(tags?.hasCover == true)
        assertEquals("image/jpeg", tags?.coverMime)
        assertTrue(tags!!.cover!!.contentEquals(coverBytes))
    }
}
