package io.github.laozhudu.linkplay.protocol.mediasource

/**
 * 一首歌在某个音源里的身份。
 *
 * 为什么不用 `QueueItem` 直接顶替：`QueueItem` 是**播放队列**的单位（带投给设备的直链），
 * 而这里的单位是**搜索结果**（还没解析直链、可能拿不到直链）。两者混在一起会出现
 * "搜索结果里塞了一个假的 url"这种脏数据。
 */
data class MediaTrack(
    /** 音源内部 id（Subsonic 是数字字符串，NAS 是 `MediaItems/24.mp3` 这种）。 */
    val id: String,
    val title: String,
    val artist: String? = null,
    val album: String? = null,
    /** 秒；未知 0。 */
    val durationSec: Long = 0,
    /** 音源标识（`subsonic` / `nas` / `local` / `radio`），用于诊断与去重。 */
    val providerId: String,
    val suffix: String? = null,
    val sizeBytes: Long = 0,
    /** 封面所需参数（Subsonic 用歌曲 id 直接取封面）。 */
    val coverId: String? = null,
)

/**
 * 音源抽象。
 *
 * ## 为什么要有这一层
 *
 * 之前"歌从哪来"是写死在几个入口里的（NAS / 手机本地 / 电台 / 歌单），
 * 于是每加一个源就要改一处播放逻辑。把"取列表"和"取直链"抽出来之后，
 * 加源 = 加一个实现类，**播放链路（PlayQueue / m3u / RelayMediaServer）一行不动**。
 *
 * ## 为什么 resolve 是 suspend 且可能失败
 *
 * 音箱只发裸 GET（真机抓到 `UA curl/7.36.0`，无 Referer/Cookie），
 * 有些源的直链需要附带请求头 —— 那种必须经 `RelayMediaServer` 转发，
 * 而转发地址是在 resolve 时才知道的。所以这一步允许异步、允许失败。
 */
interface MediaProvider {

    /** 稳定标识，用于持久化配置与去重。 */
    val id: String

    /** 界面上显示的名字。 */
    val displayName: String

    /** 是否已配置好（没配好的源不该出现在可选项里）。 */
    val configured: Boolean

    /** 搜索。返回空列表表示"没搜到"，抛异常表示"源有问题"。 */
    suspend fun search(query: String, limit: Int = 50): List<MediaTrack>

    /** 把搜索结果解析成**设备能直接拉的 URL**。 */
    suspend fun resolve(track: MediaTrack): String

    /** 封面地址；没有返回 null（界面用首字母色块兜底）。 */
    fun coverUrl(track: MediaTrack, sizePx: Int = 300): String? = null
}

/** 音源返回的错误：带上"哪个源 + 设备说了什么"，避免界面只显示一句网络错误。 */
class MediaSourceException(
    val providerId: String,
    val code: Int?,
    message: String,
) : Exception(message)
