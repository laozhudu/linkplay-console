package io.github.laozhudu.linkplay.protocol.metadata

/**
 * 音频文件里**内嵌**的元数据。
 *
 * 为什么需要它：DLNA 的 DIDL 只保证给 `dc:title` / `upnp:albumArtURI`，
 * 很多服务器（本项目的 fnOS/MiniDLNA 就是）**不给封面**，歌词更是完全不给。
 * 但文件本身通常带着 ID3v2 的 `APIC`（封面）和 `USLT`（歌词）、
 * FLAC 的 `PICTURE` / `LYRICS`。这些信息音箱不会转发，只能我们自己去读。
 *
 * 读的地方是手机自己：投给音箱的 URL 手机也能取（同一局域网），
 * 只需要 **HTTP Range 读开头几百 KB**，不必下整首歌。
 */
data class EmbeddedTags(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    /** 纯文本歌词（已去掉时间轴标记里的方括号？—— 不，保留原文，由界面决定怎么显示）。 */
    val lyrics: String? = null,
    /** 封面原始字节（JPEG/PNG）。为空表示文件里没内嵌封面。 */
    val cover: ByteArray? = null,
    val coverMime: String? = null,
) {
    val hasAnything: Boolean
        get() = title != null || artist != null || lyrics != null || cover != null

    val hasCover: Boolean get() = cover != null && cover.isNotEmpty()

    // data class 里带 ByteArray 必须手写 equals/hashCode，否则比较永远是 false
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EmbeddedTags) return false
        return title == other.title && artist == other.artist && album == other.album &&
            lyrics == other.lyrics && coverMime == other.coverMime &&
            (cover?.contentEquals(other.cover) ?: (other.cover == null))
    }

    override fun hashCode(): Int {
        var result = title?.hashCode() ?: 0
        result = 31 * result + (artist?.hashCode() ?: 0)
        result = 31 * result + (album?.hashCode() ?: 0)
        result = 31 * result + (lyrics?.hashCode() ?: 0)
        result = 31 * result + (cover?.contentHashCode() ?: 0)
        return result
    }
}

/**
 * ID3v2 / FLAC 标签解析（纯函数，可单测）。
 *
 * 只做「拿到封面和歌词」这一件事，不追求完整的标签规范：
 * - ID3v2.2/2.3/2.4：`APIC`(2.2 是 `PIC`) / `USLT`(2.2 是 `ULT`) / `TIT2` / `TPE1` / `TALB`
 * - FLAC：`VORBIS_COMMENT`（LYRICS / UNSYNCEDLYRICS / TITLE / ARTIST / ALBUM）+ `PICTURE`
 *
 * 为什么不用现成库：读取只需几百行，而引一个 ID3 库会把 APK 体积和许可证都带进来；
 * 更重要的是**纯 JVM 就能测** —— 用合成字节流覆盖各种帧头更靠谱。
 */
object TagParser {

    /** 从文件开头一段字节里尽力解析。解析不出就返回 null，不抛异常。 */
    fun parse(bytes: ByteArray): EmbeddedTags? = when {
        bytes.size >= 3 && bytes[0] == 'I'.code.toByte() &&
            bytes[1] == 'D'.code.toByte() && bytes[2] == '3'.code.toByte() -> parseId3(bytes)
        bytes.size >= 4 && bytes[0] == 'f'.code.toByte() && bytes[1] == 'L'.code.toByte() &&
            bytes[2] == 'a'.code.toByte() && bytes[3] == 'C'.code.toByte() -> parseFlac(bytes)
        else -> null
    }

    /** 建议的前缀读取长度：ID3 常见 100-300KB（封面大时更多），FLAC 标签多在开头。 */
    const val PREFIX_HINT: Int = 512 * 1024

    // ---------------------------------------------------------------- ID3v2

    private fun parseId3(b: ByteArray): EmbeddedTags? {
        if (b.size < 10) return null
        val major = b[3].toInt() and 0xFF           // 2 / 3 / 4
        val flags = b[5].toInt() and 0xFF
        val tagSize = syncSafe(b, 6)
        if (tagSize <= 0) return null
        // 有扩展头（0x40）时先跳过；不做去同步化（0x80），那种老文件极少，直接尽力解析
        var pos = 10
        if (flags and 0x40 != 0 && pos + 4 <= b.size) {
            pos += if (major >= 4) syncSafe(b, pos) else beInt(b, pos) + 4
        }
        val end = minOf(b.size, 10 + tagSize)
        var title: String? = null
        var artist: String? = null
        var album: String? = null
        var lyrics: String? = null
        var cover: ByteArray? = null
        var coverMime: String? = null

        while (pos + 10 <= end) {
            val id = String(b, pos, if (major == 2) 3 else 4, Charsets.ISO_8859_1)
            if (id.isBlank() || id[0] == '\u0000') break
            val size = if (major == 2) beInt24(b, pos + 3)
            else if (major >= 4) syncSafe(b, pos + 4)
            else beInt(b, pos + 4)
            if (size <= 0 || pos + (if (major == 2) 6 else 10) + size > b.size) break
            val dataStart = pos + (if (major == 2) 6 else 10)
            val data = b.copyOfRange(dataStart, dataStart + size)
            when (id) {
                "TIT2", "TT2" -> title = title ?: decodeText(data)
                "TPE1", "TP1" -> artist = artist ?: decodeText(data)
                "TALB", "TAL" -> album = album ?: decodeText(data)
                "USLT", "ULT" -> lyrics = lyrics ?: decodeLyrics(data, major)
                "APIC", "PIC" -> if (cover == null) {
                    val pic = decodePicture(data, major)
                    if (pic != null) {
                        cover = pic.first
                        coverMime = pic.second
                    }
                }
            }
            pos = dataStart + size
        }
        val tags = EmbeddedTags(title, artist, album, lyrics, cover, coverMime)
        return if (tags.hasAnything) tags else null
    }

    /** APIC: 编码(1) mime(NUL 结尾 latin1) 图片类型(1) 描述(按编码, NUL 结尾) 数据 */
    private fun decodePicture(data: ByteArray, major: Int): Pair<ByteArray, String>? {
        if (data.size < 4) return null
        val encoding = data[0].toInt() and 0xFF
        var p = 1
        val mime: String
        if (major == 2) {
            // PIC 用 3 字节图片格式（JPG/PNG）
            if (data.size < 5) return null
            val fmt = String(data, 1, 3, Charsets.ISO_8859_1).uppercase()
            mime = if (fmt == "PNG") "image/png" else "image/jpeg"
            p = 4
        } else {
            val mimeEnd = indexOfNul(data, p, wide = false)
            if (mimeEnd < 0) return null
            mime = String(data, p, mimeEnd - p, Charsets.ISO_8859_1).ifBlank { "image/jpeg" }
            p = mimeEnd + 1
        }
        if (p >= data.size) return null
        p += 1                                        // 跳过图片类型
        // 跳过描述（按编码宽度找 NUL）
        val wide = encoding == 1 || encoding == 2
        val descEnd = indexOfNul(data, p, wide = wide)
        if (descEnd < 0) return null
        p = descEnd + if (wide) 2 else 1
        if (p >= data.size) return null
        return data.copyOfRange(p, data.size) to mime
    }

    /** USLT: 编码(1) 语言(3) 描述(按编码 NUL 结尾) 歌词正文 */
    private fun decodeLyrics(data: ByteArray, major: Int): String? {
        if (data.size < 5) return null
        val encoding = data[0].toInt() and 0xFF
        val wide = encoding == 1 || encoding == 2
        val descEnd = indexOfNul(data, 4, wide = wide)
        val start = if (descEnd < 0) 4 else descEnd + if (wide) 2 else 1
        if (start >= data.size) return null
        // decodeText 期望首字节是编码标记，这里把 4 字节头里的编码补回去再交给它
        val body = byteArrayOf(data[0]) + data.copyOfRange(start, data.size)
        return decodeText(body)?.trim()?.takeIf { it.isNotEmpty() }
    }

    /** 文本帧：编码(1) + 正文。0=ISO-8859-1 1=UTF-16(BOM) 2=UTF-16BE 3=UTF-8 */
    private fun decodeText(data: ByteArray): String? {
        if (data.size < 2) return null
        val encoding = data[0].toInt() and 0xFF
        val body = data.copyOfRange(1, data.size)
        val text = when (encoding) {
            0 -> String(body, Charsets.ISO_8859_1)
            1 -> {
                // 有 BOM 就按 BOM，没有就按 LE（ID3v2 的约定）
                if (body.size >= 2 && body[0] == 0xFF.toByte() && body[1] == 0xFE.toByte()) {
                    String(body, 2, body.size - 2, Charsets.UTF_16LE)
                } else if (body.size >= 2 && body[0] == 0xFE.toByte() && body[1] == 0xFF.toByte()) {
                    String(body, 2, body.size - 2, Charsets.UTF_16BE)
                } else {
                    String(body, Charsets.UTF_16LE)
                }
            }
            2 -> String(body, Charsets.UTF_16BE)
            3 -> String(body, Charsets.UTF_8)
            else -> return null
        }
        return text.trimEnd('\u0000').trim().takeIf { it.isNotEmpty() }
    }

    // ---------------------------------------------------------------- FLAC

    private fun parseFlac(b: ByteArray): EmbeddedTags? {
        var pos = 4
        var title: String? = null
        var artist: String? = null
        var album: String? = null
        var lyrics: String? = null
        var cover: ByteArray? = null
        var coverMime: String? = null
        while (pos + 4 <= b.size) {
            val header = b[pos].toInt() and 0xFF
            val last = header and 0x80 != 0
            val type = header and 0x7F
            val len = beInt24(b, pos + 1)
            val start = pos + 4
            if (len < 0 || start + len > b.size) break
            when (type) {
                4 -> {   // VORBIS_COMMENT
                    val block = b.copyOfRange(start, start + len)
                    val fields = readVorbisComments(block)
                    title = title ?: fields["TITLE"]
                    artist = artist ?: fields["ARTIST"]
                    album = album ?: fields["ALBUM"]
                    lyrics = lyrics ?: fields["LYRICS"] ?: fields["UNSYNCEDLYRICS"]
                }
                6 -> {   // PICTURE
                    val block = b.copyOfRange(start, start + len)
                    val pic = readFlacPicture(block)
                    if (pic != null && cover == null) {
                        cover = pic.first
                        coverMime = pic.second
                    }
                }
            }
            if (last) break
            pos = start + len
        }
        val tags = EmbeddedTags(title, artist, album, lyrics, cover, coverMime)
        return if (tags.hasAnything) tags else null
    }

    private fun readVorbisComments(block: ByteArray): Map<String, String> {
        val out = mutableMapOf<String, String>()
        if (block.size < 8) return out
        var p = 4                                   // 跳过 vendor 长度
        val vendorLen = leInt(block, 0)
        if (vendorLen < 0 || 4 + vendorLen + 4 > block.size) return out
        p = 4 + vendorLen
        val count = leInt(block, p)
        p += 4
        repeat(count.coerceAtMost(512)) {
            if (p + 4 > block.size) return out
            val len = leInt(block, p)
            p += 4
            if (len < 0 || p + len > block.size) return out
            val entry = String(block, p, len, Charsets.UTF_8)
            p += len
            val eq = entry.indexOf('=')
            if (eq > 0) out[entry.substring(0, eq).uppercase()] = entry.substring(eq + 1)
        }
        return out
    }

    /** PICTURE: type(4) mimeLen(4) mime descLen(4) desc w h depth colors dataLen(4) data */
    private fun readFlacPicture(block: ByteArray): Pair<ByteArray, String>? {
        if (block.size < 32) return null
        var p = 4
        val mimeLen = beInt(block, p); p += 4
        if (mimeLen < 0 || p + mimeLen + 4 > block.size) return null
        val mime = String(block, p, mimeLen, Charsets.ISO_8859_1).ifBlank { "image/jpeg" }
        p += mimeLen
        val descLen = beInt(block, p); p += 4
        if (descLen < 0 || p + descLen + 20 > block.size) return null
        p += descLen + 16                           // width/height/depth/colors
        val dataLen = beInt(block, p); p += 4
        if (dataLen <= 0 || p + dataLen > block.size) return null
        return block.copyOfRange(p, p + dataLen) to mime
    }

    // ---------------------------------------------------------------- 小工具

    private fun indexOfNul(b: ByteArray, from: Int, wide: Boolean): Int {
        if (!wide) {
            for (i in from until b.size) if (b[i] == 0.toByte()) return i
            return -1
        }
        var i = from
        while (i + 1 < b.size) {
            if (b[i] == 0.toByte() && b[i + 1] == 0.toByte()) return i
            i += 2
        }
        return -1
    }

    /** ID3 的「同步安全整数」：每字节只用低 7 位。 */
    private fun syncSafe(b: ByteArray, off: Int): Int {
        if (off + 4 > b.size) return 0
        return ((b[off].toInt() and 0x7F) shl 21) or
            ((b[off + 1].toInt() and 0x7F) shl 14) or
            ((b[off + 2].toInt() and 0x7F) shl 7) or
            (b[off + 3].toInt() and 0x7F)
    }

    private fun beInt(b: ByteArray, off: Int): Int {
        if (off + 4 > b.size) return 0
        return ((b[off].toInt() and 0xFF) shl 24) or
            ((b[off + 1].toInt() and 0xFF) shl 16) or
            ((b[off + 2].toInt() and 0xFF) shl 8) or
            (b[off + 3].toInt() and 0xFF)
    }

    private fun beInt24(b: ByteArray, off: Int): Int {
        if (off + 3 > b.size) return 0
        return ((b[off].toInt() and 0xFF) shl 16) or
            ((b[off + 1].toInt() and 0xFF) shl 8) or
            (b[off + 2].toInt() and 0xFF)
    }

    private fun leInt(b: ByteArray, off: Int): Int {
        if (off + 4 > b.size) return 0
        return (b[off].toInt() and 0xFF) or
            ((b[off + 1].toInt() and 0xFF) shl 8) or
            ((b[off + 2].toInt() and 0xFF) shl 16) or
            ((b[off + 3].toInt() and 0xFF) shl 24)
    }
}
