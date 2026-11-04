package io.github.laozhudu.linkplay.radio

import io.github.laozhudu.linkplay.protocol.radio.RadioStation as FavouriteStation
import io.github.laozhudu.radiodiscovery.model.RadioStation as DiscoveryStation

/**
 * 流格式分类。**只看地址形态，不做网络探测。**
 *
 * 为什么要分：实测中国区 500 个台里 **45% 是 `.m3u8`（HLS）**，而 HLS 只有
 * **37% 能播**（`rscdn.ajmide.com`、`live.ximalaya.com` 能播；
 * `vip1.fhbf9.com`、`ls.qingting.fm`、`myradio.023520.xyz` 播不了）。
 *
 * 直连流（`.mp3` / `.aac` / 无后缀）实测 91%～100% 能播。
 *
 * 所以界面在把台交给用户点之前，应该对 [HLS] 标一句"可能不支持" ——
 * 而不是让用户点了没反应（项目铁律：不给按了没反应的按钮）。
 */
enum class StreamKind {
    /** `.mp3` / `.aac` / `.ogg` / 无后缀的直连流，实测成功率最高。 */
    DIRECT,

    /** `.m3u8`（HLS），成功率取决于主机，要提示用户。 */
    HLS,

    /** 认不出来，按直连处理但不要打包票。 */
    UNKNOWN,
}

/** 判断一个地址属于哪一类。 */
fun streamKindOf(url: String): StreamKind {
    val path = url.substringBefore('?').substringBefore('#').lowercase()
    return when {
        path.endsWith(".m3u8") -> StreamKind.HLS
        path.endsWith(".mp3") || path.endsWith(".aac") || path.endsWith(".ogg") ||
            path.endsWith(".flac") || path.endsWith(".wma") -> StreamKind.DIRECT
        // `.pls` / `.m3u` 是播放列表文件，需要再解析一层，先归到未知
        path.endsWith(".pls") || path.endsWith(".m3u") -> StreamKind.UNKNOWN
        else -> StreamKind.UNKNOWN
    }
}

/** 目录里查到的台 → 能存进收藏的形状。 */
fun DiscoveryStation.toFavourite(deviceName: String? = null): FavouriteStation =
    FavouriteStation(
        name = name.trim().ifBlank { "网络电台" },
        // 优先用解析后的真实地址：中间页存下来下次还要再解析一次，没必要
        url = resolvedUrl?.takeIf { it.isNotBlank() } ?: url,
        deviceName = deviceName,
    )

/**
 * 给界面用的一句话提示。`null` 表示"没什么要提醒的"。
 *
 * 不写成"不可播放"——我们**没有做网络探测**，只是按格式给出概率性提示。
 * 把猜测说成断言就是撒谎。
 */
fun DiscoveryStation.playbackHint(): String? = when (streamKindOf(resolvedUrl ?: url)) {
    StreamKind.HLS -> "HLS 流，部分音箱播不了"
    StreamKind.DIRECT -> null
    StreamKind.UNKNOWN -> null
}
