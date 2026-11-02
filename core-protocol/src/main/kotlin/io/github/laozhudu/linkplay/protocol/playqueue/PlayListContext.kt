package io.github.laozhudu.linkplay.protocol.playqueue

/**
 * 设备（实机）要求的队列上下文格式 —— **不是一个标准，是摸出来的方言**。
 *
 * 证据（DOSS DS-1831 / 固件 3.6.5310 实测）：
 * - `CreateQueue(纯 DIDL-Lite)` → HTTP 500 `404 ParseXmltoPlayList error`
 * - 100+ 种 DIDL/JSON/URL/m3u 变体 → 全部同样报错
 * - `CreateQueue(<PlayList><ListName>..</ListName><ListInfo>13 个字段</ListInfo></PlayList>)` → **200 OK**
 * - 之后 `BrowseQueue(QueueName=ListName)` 能回读，设备自己序列化出的结构就是 [build] 产出的这个形状，
 *   其中 `<Tracks></Tracks>` 是曲目容器
 *
 * **重要限制（也是未解项）**：`<Tracks>` 里只要放任何内容（Track/Item/Song/Url/MediaUrl… 12 种候选都试过），
 * 设备一律回 `404 ParseXmltoPlayList error`。空 `<Tracks/>` 才接受。
 * 也就是说这一版固件把 CreateQueue 当成「在线源描述符」入口（靠 `SearchUrl` 让设备自己去拉），
 * **不能**用它推一份本地文件列表。要推本地列表需要更高版本固件或 QPlay 通道（见 `QPlayClient`）。
 */
data class PlayListInfo(
    val searchUrl: String = "local",
    val markSearch: Int = 0,
    val trackNumber: Int = 0,
    val quality: Int = 0,
    val updateTime: Long = 0,
    val lastPlayIndex: Int = 0,
    val realIndex: Int = 0,
    val switchPageMode: Int = 0,
    val currentPage: Int = 0,
    val totalPages: Int = 1,
    val searching: Int = 0,
    val pressType: Int = 0,
    val volume: Int = 0,
)

object PlayListContext {

    /**
     * 构建设备接受的队列上下文。字段顺序照抄设备自己的序列化结果（顺序无关但便于对比排障）。
     * `tracksXml` 默认空——非空会被设备拒绝，见类注释。
     */
    fun build(name: String, info: PlayListInfo = PlayListInfo(), tracksXml: String = ""): String =
        buildString {
            append("<?xml version=\"1.0\"?>")
            append("<PlayList>")
            append("<ListName>").append(escape(name)).append("</ListName>")
            append("<ListInfo>")
            append("<SearchUrl>").append(escape(info.searchUrl)).append("</SearchUrl>")
            append("<MarkSearch>").append(info.markSearch).append("</MarkSearch>")
            append("<TrackNumber>").append(info.trackNumber).append("</TrackNumber>")
            append("<Quality>").append(info.quality).append("</Quality>")
            append("<UpdateTime>").append(info.updateTime).append("</UpdateTime>")
            append("<LastPlayIndex>").append(info.lastPlayIndex).append("</LastPlayIndex>")
            append("<RealIndex>").append(info.realIndex).append("</RealIndex>")
            append("<SwitchPageMode>").append(info.switchPageMode).append("</SwitchPageMode>")
            append("<CurrentPage>").append(info.currentPage).append("</CurrentPage>")
            append("<TotalPages>").append(info.totalPages).append("</TotalPages>")
            append("<searching>").append(info.searching).append("</searching>")
            append("<PressType>").append(info.pressType).append("</PressType>")
            append("<Volume>").append(info.volume).append("</Volume>")
            append("</ListInfo>")
            append("<Tracks>").append(tracksXml).append("</Tracks>")
            append("</PlayList>")
        }

    private fun escape(v: String) = v
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
}
