package io.github.laozhudu.linkplay.console.usecase

import io.github.laozhudu.linkplay.protocol.queue.QueueItem

/**
 * 「源里的一条 → 可投送的一条」的唯一入口。
 *
 * ## 收的是什么
 *
 * 全仓有 **8 处**手写 `QueueItem(...)`（NAS / 音乐库两处 / 队列 / 电台 / 路由 /
 * 设备播放列表回读 / 播放页）。其中音乐库那两处是**逐字重复**——
 * 一处是"播放列表"，一处是"加入歌单"，代码一模一样，因为它们是同一个动作。
 *
 * 手写这么多次的真实后果不是啰嗦，是**字段迟早对不上**：
 * 加一个字段（比如 `mimeOf` 的推导）后，改到第六处忘了第七处，
 * 表现是"某一条路径没有封面 / 时长永远是 0"，而且只在那个源上复现。
 *
 * ## 为什么不引入一个 `SourceTrack` 抽象
 *
 * 五个源的原始类型完全不同（DLNA 的 `BrowseEntry`、本地的 `LocalTrack`、
 * 在线的 `SubsonicSong`、电台的 `RadioStation`、洛雪的 `LxTrack`）。
 * 为了统一而造一个公共父类，等于**让五个源都去实现它** —— 那是牵动五个模块的改动，
 * 而收益只是"少写一个字段名"。
 *
 * 所以这里用泛型 + 两个 lambda：**不新增类型，只统一流程和字段落点**。
 */
object ToQueueItemsUseCase {

    /**
     * 单条构造。
     *
     * `mimeType` 默认 `audio/mpeg`：这是设备接受度最高的值。
     * 真实类型（flac/m4a）对 LinkPlay 的 PlayQueue 没有好处，反而有固件不认的风险。
     */
    fun of(
        url: String,
        title: String,
        artist: String? = null,
        album: String? = null,
        durationSec: Long = 0,
        mimeType: String = "audio/mpeg",
        albumArtUrl: String? = null,
    ): QueueItem = QueueItem(
        url = url,
        title = title,
        artist = artist,
        album = album,
        durationSec = durationSec,
        mimeType = mimeType,
        albumArtUrl = albumArtUrl,
    )

    /**
     * 一批：解析地址 + 跳过解析失败的。
     *
     * ## 为什么这一步必须收口
     *
     * 五个源各写了一遍这个循环，而且**每处都写了 `runCatching { }.getOrNull()?.let { }`**。
     * 这是最容易写歪的地方：
     * - 有人用 `getOrDefault(null)` —— 一样；
     * - 有人直接 `!!` —— 一条解析失败就整批崩；
     * - 有人把失败项**保留成空 url** —— 投出去设备回 400，看起来像"随机有几首放不了"。
     *
     * 统一规则：**解析失败的那一条直接丢弃**，不占位、不崩整批。
     *
     * @param resolve 把源里的原始对象解析成"设备能直接取到的 URL"。返回 null = 这条放不了。
     * @param toItem 用"原始对象 + 解析出的 URL"拼出一条队列项。
     */
    suspend fun <T> resolveAll(
        tracks: List<T>,
        resolve: suspend (T) -> String?,
        toItem: (T, String) -> QueueItem,
    ): List<QueueItem> = tracks.mapNotNull { track ->
        runCatching { resolve(track) }.getOrNull()?.let { url -> toItem(track, url) }
    }
}
