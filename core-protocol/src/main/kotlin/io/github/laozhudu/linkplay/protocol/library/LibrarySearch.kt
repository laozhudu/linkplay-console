package io.github.laozhudu.linkplay.protocol.library

/** 搜索命中的来源，用于排序与高亮提示。 */
enum class MatchKind {
    /** 标题前缀完全匹配。 */
    TITLE_PREFIX,

    /** 拼音首字母前缀匹配（输入 `yq` 命中「夜曲」）。 */
    INITIALS_PREFIX,

    /** 拼音首字母在标题+艺术家+专辑里出现（输入 `zjl` 命中周杰伦的所有歌）。 */
    INITIALS_CONTAINS,

    /** 标题包含。 */
    TITLE_CONTAINS,

    /** 艺术家包含。 */
    ARTIST_CONTAINS,

    /** 专辑/文件夹包含。 */
    ALBUM_CONTAINS,
    ;

    val rank: Int get() = ordinal
}

data class SearchHit(val track: TrackRecord, val kind: MatchKind)

/**
 * 曲库搜索（纯函数，无状态索引缓存，便于单测与跨端移植）。
 *
 * 匹配顺序即优先级：标题前缀 > 拼音首字母前缀 > 标题包含 > 艺术家 > 专辑。
 * 大小写不敏感；查询里的空格被忽略（用户常输「周杰伦 夜曲」）。
 */
object LibrarySearch {

    fun search(
        tracks: List<TrackRecord>,
        query: String,
        limit: Int = 100,
    ): List<SearchHit> {
        val raw = query.trim()
        if (raw.isEmpty()) return emptyList()
        val q = raw.lowercase()
        val compact = q.filterNot { it.isWhitespace() }
        val initialsQuery = PinyinInitials.initialsOf(raw)

        val hits = ArrayList<SearchHit>()
        for (t in tracks) {
            val title = t.title.lowercase()
            val artist = (t.displayArtist ?: "").lowercase()
            val album = (t.album ?: "").lowercase()
            val folder = (t.folderName ?: "").lowercase()

            val titleInitials = PinyinInitials.initialsOf(t.title)
            val kind = when {
                title.startsWith(q) -> MatchKind.TITLE_PREFIX
                initialsQuery.isNotEmpty() && titleInitials.startsWith(initialsQuery) -> MatchKind.INITIALS_PREFIX
                title.contains(compact) || title.replace(" ", "").contains(compact) -> MatchKind.TITLE_CONTAINS
                initialsQuery.isNotEmpty() && initialsOf(t).contains(initialsQuery) -> MatchKind.INITIALS_CONTAINS
                artist.contains(q) -> MatchKind.ARTIST_CONTAINS
                (album.contains(q) || folder.contains(q)) -> MatchKind.ALBUM_CONTAINS
                else -> null
            }
            if (kind != null) hits += SearchHit(t, kind)
        }
        return hits
            .sortedWith(compareBy({ it.kind.rank }, { it.track.title.length }, { it.track.title.lowercase() }))
            .take(limit)
    }

    /** 标题 + 艺术家 + 专辑的拼音首字母串，缓存键是三者拼接（不做全局索引，避免状态泄漏）。 */
    fun initialsOf(track: TrackRecord): String =
        PinyinInitials.initialsOf(track.title) + PinyinInitials.initialsOf(track.displayArtist ?: "") +
            PinyinInitials.initialsOf(track.album ?: "")
}
