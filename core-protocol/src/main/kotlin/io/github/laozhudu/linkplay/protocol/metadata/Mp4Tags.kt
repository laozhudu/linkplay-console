package io.github.laozhudu.linkplay.protocol.metadata

/**
 * MP4 / M4A 的标签解析。
 *
 * 为什么要单独一个：本项目 NAS（fnOS/MiniDLNA）上的曲目实测是 **M4A**，
 * 而且 `moov` 在文件**末尾**（不是 faststart），所以只看开头的 ID3 永远读不到封面。
 * 对应地，取标签要"读尾部"这段区间。
 *
 * 结构（只走需要的那条路）：
 * ```
 * moov
 *  └ udta
 *     └ meta（头 4 字节是 version/flags）
 *        └ ilst
 *           ├ ©nam / ©ART / ©alb   → data(type=1, UTF-8)
 *           ├ ©lyr                 → data(type=1, UTF-8)   歌词
 *           └ covr                 → data(type=13/14, JPEG/PNG) 封面
 * ```
 */
object Mp4Tags {

    /** 只有这些类型才值得「读尾部」：GB 级视频的 moov 也可能在尾部。 */
    fun looksLikeMp4(prefix: ByteArray): Boolean {
        if (prefix.size < 12) return false
        val ftyp = String(prefix, 4, 4, Charsets.ISO_8859_1)
        return ftyp == "ftyp"
    }

    /**
     * 从尾部一段字节里解析 moov 内的标签。
     *
     * 关键：读尾部时第一段一定是**半个 atom**，从头顺序走会读出一个几 GB 的假长度。
     * 所以做法是「找到 `ilst` 标记 → 用它前面 4 字节当长度并校验边界 → 解析这一块」，
     * 一次不成就换下一个出现位置（真实文件里垃圾数据可能凑巧匹配）。
     */
    fun parse(tail: ByteArray): EmbeddedTags? {
        val marker = "ilst".toByteArray(Charsets.ISO_8859_1)
        var found: EmbeddedTags? = null
        var i = 4
        while (i + 4 <= tail.size && found == null) {
            if (matchesAt(tail, i, marker)) {
                val size = beInt(tail, i - 4)
                val contentStart = i + 4
                val contentEnd = i - 4 + size
                if (size >= 8 && contentEnd <= tail.size) {
                    found = readIlst(tail, contentStart, contentEnd)
                }
            }
            i++
        }
        return found
    }

    private fun readIlst(tail: ByteArray, start: Int, end: Int): EmbeddedTags? {
        var title: String? = null
        var artist: String? = null
        var album: String? = null
        var lyrics: String? = null
        var cover: ByteArray? = null
        var coverMime: String? = null

        walk(start, end, tail) { type, cs, ce ->
            when (type) {
                "\u00A9nam" -> title = title ?: readDataText(tail, cs, ce)
                "\u00A9ART" -> artist = artist ?: readDataText(tail, cs, ce)
                "\u00A9alb" -> album = album ?: readDataText(tail, cs, ce)
                "\u00A9lyr" -> lyrics = lyrics ?: readDataText(tail, cs, ce)
                "covr" -> if (cover == null) {
                    val data = readDataAtom(tail, cs, ce)
                    if (data != null && data.second.isNotEmpty()) {
                        cover = data.second
                        coverMime = when (data.first) {
                            13 -> "image/jpeg"
                            14 -> "image/png"
                            27 -> "image/bmp"
                            else -> "image/jpeg"
                        }
                    }
                }
            }
        }
        val tags = EmbeddedTags(title, artist, album, lyrics, cover, coverMime)
        return if (tags.hasAnything) tags else null
    }

    /** 遍历 [start, end) 里所有 atom，回调 (类型, 内容起, 内容止)。 */
    private inline fun walk(
        start: Int,
        end: Int,
        bytes: ByteArray,
        onAtom: (String, Int, Int) -> Unit,
    ) {
        var p = start
        while (p + 8 <= end) {
            val size = beInt(bytes, p)
            if (size < 8) return
            val type = String(bytes, p + 4, 4, Charsets.ISO_8859_1)
            val cs = p + 8
            val ce = (p + size).coerceAtMost(end)
            onAtom(type, cs, ce)
            p += size
        }
    }

    /** item 里第一个 `data` atom：4 字节 version/flags + 4 字节类型 + 4 字节 locale + 负载 */
    private fun readDataAtom(bytes: ByteArray, start: Int, end: Int): Pair<Int, ByteArray>? {
        var result: Pair<Int, ByteArray>? = null
        walk(start, end, bytes) { type, cs, ce ->
            if (type == "data" && result == null && cs + 8 <= ce) {
                val flag = beInt(bytes, cs) and 0x00FFFFFF   // version(1)+flags(3)，类型在低 24 位
                val payload = bytes.copyOfRange(cs + 8, ce)
                result = flag to payload
            }
        }
        return result
    }

    private fun readDataText(bytes: ByteArray, start: Int, end: Int): String? {
        val data = readDataAtom(bytes, start, end) ?: return null
        return String(data.second, Charsets.UTF_8).trim().takeIf { it.isNotEmpty() }
    }

    private fun matchesAt(b: ByteArray, pos: Int, marker: ByteArray): Boolean {
        if (pos < 0 || pos + marker.size > b.size) return false
        for (k in marker.indices) if (b[pos + k] != marker[k]) return false
        return true
    }

    private fun beInt(b: ByteArray, off: Int): Int {
        if (off + 4 > b.size) return -1
        return ((b[off].toInt() and 0xFF) shl 24) or
            ((b[off + 1].toInt() and 0xFF) shl 16) or
            ((b[off + 2].toInt() and 0xFF) shl 8) or
            (b[off + 3].toInt() and 0xFF)
    }
}
