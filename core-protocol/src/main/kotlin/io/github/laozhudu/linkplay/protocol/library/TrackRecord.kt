package io.github.laozhudu.linkplay.protocol.library

/**
 * 与设备/本地无关的曲目记录。
 *
 * 字段刻意对齐原厂 App 的两张表（`tb_album_info` / `tb_last_played`），
 * 这样导入旧数据时不需要二次映射：
 * `playuri, title, album, creator, artist, albumarturi, duration, folder_name, source_type, song_id`
 *
 * 设计取舍：**不带任何 Android 类型**，因此收藏/最近/搜索逻辑可以纯 JVM 单测，
 * 也方便把这一层直接移植到桌面端或服务端（对接开源项目时复用同一套语义）。
 */
data class TrackRecord(
    /** 媒体地址；本地文件是 `http://<手机IP>:<端口>/t/<id>`，网络流是原 URL。 */
    val playUri: String,
    val title: String,
    val album: String? = null,
    /** 原厂把「创建者」与「表演者」分开存，这里保留两栏以兼容导入。 */
    val creator: String? = null,
    val artist: String? = null,
    val albumArtUri: String? = null,
    /** 秒；未知 0。 */
    val durationSec: Long = 0,
    val folderName: String? = null,
    /** 来源标识，例如 `local` / `radio` / `subsonic`。 */
    val sourceType: String = SOURCE_LOCAL,
    val songId: Long = 0,
    /** 最近播放用：首次播放时间（毫秒）。 */
    val firstPlayedAt: Long = 0,
) {
    val displayArtist: String? get() = artist?.takeIf { it.isNotBlank() } ?: creator?.takeIf { it.isNotBlank() }

    val displayName: String get() = displayArtist?.let { "$title · $it" } ?: title

    /** 去重键：同一首本地歌的 URL 会随手机 IP 变化，所以本地曲目按 songId 去重。 */
    val dedupeKey: String
        get() = if (sourceType == SOURCE_LOCAL && songId != 0L) "$sourceType:$songId" else "$sourceType:$playUri"

    companion object {
        const val SOURCE_LOCAL = "local"
        const val SOURCE_RADIO = "radio"
        const val SOURCE_SUBSONIC = "subsonic"
    }
}

/** 收藏（对应原厂 `tb_music_collection`，但这里存整条记录而不是只存 URI）。 */
interface FavoriteStore {
    fun all(): List<TrackRecord>
    fun contains(dedupeKey: String): Boolean
    /** 已存在则不变，返回 true 表示本次新增。 */
    fun add(track: TrackRecord): Boolean
    fun remove(dedupeKey: String): Boolean
    fun clear()
}

/**
 * 一条「最近播放」事件：放了什么、投给谁、什么时候。
 *
 * 刻意独立于 [TrackRecord]：曲目是曲目，播放事件是播放事件。
 * 合并成一个类会让"这首歌的时长"和"这次投给了谁"放在同一层，
 * 以后加"投给音箱还是本机"又要往里塞字段。
 */
data class RecentPlay(
    val track: TrackRecord,
    /** 当时投给的设备 IP；投本机或旧数据为 null。 */
    val deviceIp: String?,
    val playedAt: Long,
)

/**
 * 最近播放（对应原厂 `tb_last_played`，原厂限制 100 条）。
 *
 * 语义：同一首歌重复播放只更新 `playedAt` 并前置，不产生重复条目——
 * 原厂是「每次播放插一行再 order by 时间」，会产生重复，这里刻意改掉。
 */
interface RecentStore {
    fun recent(limit: Int = DEFAULT_LIMIT): List<TrackRecord>
    fun record(track: TrackRecord, playedAt: Long)
    fun clear()

    /**
     * 带「投给了哪台设备」的读法（定稿「接着听」那一行要显示 `doss1`）。
     *
     * **默认实现降级成 [recent]**，设备字段为空 —— 这样纯内存实现和旧实现
     * 不用改就能编译，只有真正持久化的那份（[LibraryStore]）覆盖它。
     *
     * 为什么设备不放进 [TrackRecord]：那个类的契约是「与设备无关的曲目记录」，
     * 一台歌投给谁播属于**播放事件**的属性，不是曲目自身的属性。
     */
    fun recentWithDevice(limit: Int = DEFAULT_LIMIT): List<RecentPlay> =
        recent(limit).map { RecentPlay(track = it, deviceIp = null, playedAt = it.firstPlayedAt) }

    /**
     * 带设备地记一条。
     *
     * 默认降级成 [record] —— 同样是为了不让旧实现必须改。
     */
    fun recordWithDevice(track: TrackRecord, playedAt: Long, deviceIp: String?) {
        record(track, playedAt)
    }

    companion object {
        const val DEFAULT_LIMIT = 100
    }
}

/** 纯内存实现：单测用，也可作为「未接入数据库」时的降级实现。 */
class InMemoryFavoriteStore : FavoriteStore {
    private val items = LinkedHashMap<String, TrackRecord>()

    override fun all(): List<TrackRecord> = items.values.toList()

    override fun contains(dedupeKey: String): Boolean = items.containsKey(dedupeKey)

    override fun add(track: TrackRecord): Boolean {
        if (items.containsKey(track.dedupeKey)) return false
        items[track.dedupeKey] = track
        return true
    }

    override fun remove(dedupeKey: String): Boolean = items.remove(dedupeKey) != null

    override fun clear() = items.clear()
}

class InMemoryRecentStore : RecentStore {
    private val items = LinkedHashMap<String, TrackRecord>()

    override fun recent(limit: Int): List<TrackRecord> =
        items.values.sortedByDescending { it.firstPlayedAt }.take(limit)

    override fun record(track: TrackRecord, playedAt: Long) {
        items.remove(track.dedupeKey)
        items[track.dedupeKey] = track.copy(firstPlayedAt = playedAt)
    }

    override fun clear() = items.clear()
}
