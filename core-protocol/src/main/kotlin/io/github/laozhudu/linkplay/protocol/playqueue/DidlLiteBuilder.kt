package io.github.laozhudu.linkplay.protocol.playqueue

import io.github.laozhudu.linkplay.protocol.upnp.UpnpXml

/**
 * DIDL-Lite 播放列表构建。
 *
 * 为什么需要「多条 item」版本：单曲投流只要 SetAVTransportURI 一条 metadata，
 * 而整列表推送要把 N 条 item 拼进同一个 DIDL 文档交给设备的私有 PlayQueue 服务。
 *
 * 已确证的协议事实（见 docs/01 §12）：
 * - 私有服务 `urn:schemas-wiimu-com:service:PlayQueue:1`，动作 `CreateQueue` /
 *   `AppendTracksInQueue` 的唯一参数是 `QueueContext`；
 * - 原厂 App 在手机侧跑 GNaP MediaServer + NanoHTTPD，`Res` 的 URL 模板是
 *   `http://<手机IP>:<端口>/<文件路径>`（字节码里是 `http://#IP_Param#` 占位）。
 *
 * ⚠️ **QueueContext 的内部结构未确证**：反向工程只能确定它是一个字符串参数，
 * 看不到设备端如何解析（原厂的构造函数把任意 String 直接塞进去）。
 * 本项目按 UPnP 通行做法用 DIDL-Lite 文档，并在 [QueueContextFormat] 里把
 * 「我们猜的格式」与「已确证的部分」分开写清楚。
 */
data class PlaylistTrack(
    /** 手机 HTTP 服务上的可访问地址。 */
    val url: String,
    val title: String,
    val artist: String? = null,
    val album: String? = null,
    val albumArtUrl: String? = null,
    /** 秒；未知传 0。 */
    val durationSec: Long = 0,
    /** 形如 `audio/mpeg`，用于 `res protocolInfo`。 */
    val mimeType: String = "audio/mpeg",
    /** 字节数；未知传 0（部分 renderer 依赖它做进度）。 */
    val sizeBytes: Long = 0,
)

object DidlLiteBuilder {

    /** 单曲（沿用原有行为，供 AVTransport 单条投流用）。 */
    fun single(track: PlaylistTrack, parentId: String = "-1"): String = build(listOf(track), parentId)

    /**
     * 多曲 DIDL-Lite 文档。`id` 从 1 开始，`parentID` 指向 `parentId`，
     * 顺序即播放顺序——设备的 `PlayQueueWithIndex(Index)` 按这个下标定位。
     */
    fun build(tracks: List<PlaylistTrack>, parentId: String = "-1"): String = buildString {
        append("""<DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/" """)
        append("""xmlns:dc="http://purl.org/dc/elements/1.1/" """)
        append("""xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/" """)
        append("""xmlns:dlna="urn:schemas-dlna-org:metadata-1-0/">""")
        tracks.forEachIndexed { index, t ->
            val id = (index + 1).toString()
            append("<item id=\"").append(id).append("\" parentID=\"")
            append(UpnpXml.escape(parentId)).append("\" restricted=\"1\">")
            append("<dc:title>").append(UpnpXml.escape(t.title)).append("</dc:title>")
            t.artist?.let { append("<upnp:artist>").append(UpnpXml.escape(it)).append("</upnp:artist>") }
            t.album?.let { append("<upnp:album>").append(UpnpXml.escape(it)).append("</upnp:album>") }
            t.albumArtUrl?.let {
                append("<upnp:albumArtURI>").append(UpnpXml.escape(it)).append("</upnp:albumArtURI>")
            }
            append("<upnp:class>object.item.audioItem.musicTrack</upnp:class>")
            append("""<res protocolInfo="http-get:*:""").append(UpnpXml.escape(t.mimeType)).append(":*\"")
            if (t.sizeBytes > 0) append(" size=\"").append(t.sizeBytes).append("\"")
            if (t.durationSec > 0) append(" duration=\"").append(formatDuration(t.durationSec)).append("\"")
            append(">")
            append(UpnpXml.escape(t.url))
            append("</res>")
            append("</item>")
        }
        append("</DIDL-Lite>")
    }

    /** DIDL 的 duration 格式是 `H:MM:SS.mmm`。 */
    fun formatDuration(seconds: Long): String {
        val s = seconds.coerceAtLeast(0)
        return "%d:%02d:%02d.000".format(s / 3600, (s % 3600) / 60, s % 60)
    }
}

/**
 * 队列上下文格式策略。
 *
 * 我们**不确定**设备要的是纯 DIDL、还是「QueueName + DIDL」的组合、还是别的 JSON。
 * 因此把它做成可切换的策略，并在 UI/日志里显式显示当前用的是哪种，
 * 方便真机上一试就知道——而不是埋在代码里猜。
 */
enum class QueueContextFormat {
    /** 只包含 DIDL-Lite 文档（UPnP 通行做法）。 */
    DIDL_ONLY,

    /** `QueueName \n DIDL-Lite` 两段式。 */
    NAME_THEN_DIDL,

    /** 只用队列名做上下文（设备侧靠 BrowseQueue 自己找内容）。 */
    NAME_ONLY,
    ;

    fun render(queueName: String, didl: String): String = when (this) {
        DIDL_ONLY -> didl
        NAME_THEN_DIDL -> queueName + "\n" + didl
        NAME_ONLY -> queueName
    }
}
