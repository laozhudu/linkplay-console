package io.github.laozhudu.linkplay.protocol.upnp

/**
 * 设备侧播放队列（原厂 `PlayQueue` 服务）的数据模型与 XML 编解码。
 *
 * ## 这套格式从哪来
 *
 * **逐字取自原厂 APK**（`org.teleal.cling.support.c.a.l` 的 `a(...)` 与 `e.d.a(...)`）：
 * 它把整个播放列表编成一段 XML 当作 `QueueContext` 参数发给设备的
 * `urn:schemas-wiimu-com:service:PlayQueue:1` 服务。格式是：
 *
 * ```xml
 * <?xml version="1.0" ?>
 * <PlayList>
 *   <ListName>队列名</ListName>
 *   <ListInfo>
 *     <Radio>0</Radio>
 *     <SourceName>队列名</SourceName>
 *     <TrackNumber>4</TrackNumber>
 *     <SearchUrl></SearchUrl>
 *     <Quality>0</Quality>
 *   </ListInfo>
 *   <Tracks>
 *     <Track1>
 *       <Source>队列名</Source>
 *       <URL>曲目直链</URL>
 *       <Id>0</Id>
 *       <Key></Key>
 *       <Metadata>&lt;DIDL-Lite …&gt;</Metadata>   ← 二次转义过的 DIDL
 *     </Track1>
 *     …
 *   </Tracks>
 * </PlayList>
 * ```
 *
 * ## 为什么以前做不到
 *
 * 之前试过 100+ 种 `QueueContext` 格式全部被拒（docs/10 的记录），根因是：
 * 1. 以为 `<Tracks/>` 只能留空 —— 其实每首必须包成 `<TrackN>`（N 从 1 起，**标签名带序号**）；
 * 2. `<Metadata>` 里必须是 **XML 转义过的 DIDL-Lite**（先转义一次，随 QueueContext 再转义一次）；
 * 3. 队列名要和 `PlayQueueWithIndex` 里的 `QueueName` 对得上。
 *
 * 真机验证（2026-11-03，DS-1831 / fw 3.6.5310，`.153`）：
 * `CreateQueue(本格式)` + `PlayQueueWithIndex(QueueName, 0)` 之后
 * `getPlayerStatus` 报 `plicount=4 plicurr=1 mode=10 status=play`，
 * 且 `BrowseQueue(QueueName)` 能把整份列表（含曲名）原样读回来。
 */
data class QueueTrack(
    val url: String,
    val title: String,
    val artist: String? = null,
    val album: String? = null,
    val albumArtUri: String? = null,
    /** 秒；0 表示未知（原厂也写 0）。 */
    val durationSec: Long = 0,
    val id: Long = 0,
)

/** 从设备读回来的一份队列。 */
data class DevicePlaylist(
    val name: String,
    val tracks: List<QueueTrack>,
    /** 设备报的当前第几首，**0 基**；-1 表示设备没报。 */
    val currentIndex: Int,
) {
    val isEmpty: Boolean get() = tracks.isEmpty()
}

object QueueContextXml {

    /** 原厂用的固定质量位（`org.teleal.cling.support.c.a.e.c.f2735a - 1`）。 */
    private const val QUALITY = 0

    private fun escape(s: String): String = s
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("'", "&apos;").replace("\"", "&quot;")

    private fun unescape(s: String): String = s
        .replace("&lt;", "<").replace("&gt;", ">").replace("&apos;", "'")
        .replace("&quot;", "\"").replace("&amp;", "&")

    /**
     * 反复解转到稳定为止（最多 3 轮）。
     *
     * 为什么需要：曲目标题里如果有 `&`，它会被转义两次（DIDL 一次 + Metadata 一次），
     * 只解一次会留下 `&amp;`。原厂自己的解析是"取一次子串"，遇到这种标题就会显示成 `&amp;`
     * —— 我们不复刻这个毛病。
     */
    private fun deepUnescape(s: String): String {
        var cur = s
        repeat(3) {
            val next = unescape(cur)
            if (next == cur) return cur
            cur = next
        }
        return cur
    }

    /** 去掉控制字符（原厂 `e.e.c`）——它们会让设备的 XML 解析器直接放弃整份列表。 */
    private fun sanitize(s: String): String =
        s.map { if (it.code in 1..31) ' ' else it }.joinToString("")

    /** 单首曲目的 DIDL-Lite（**返回值已 XML 转义**，因为要塞进 `<Metadata>` 文本节点）。 */
    fun didlFor(track: QueueTrack): String {
        val raw = buildString {
            append("<DIDL-Lite ")
            append("xmlns:dc=\"http://purl.org/dc/elements/1.1/\" ")
            append("xmlns:upnp=\"urn:schemas-upnp-org:metadata-1-0/upnp/\" ")
            append("xmlns:song=\"www.wiimu.com/song/\" ")
            append("xmlns=\"urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/\"> ")
            append("<upnp:class>object.item.audioItem.musicTrack</upnp:class> <item> ")
            append("<res protocolInfo=\"http-get:*:audio/mpeg:DLNA.ORG_PN=MP3;DLNA.ORG_OP=01;\" ")
            append("duration=\"").append(if (track.durationSec > 0) track.durationSec else 0).append("\">")
            append(escape(track.url)).append("</res>")
            append("<dc:title>").append(escape(sanitize(track.title))).append("</dc:title> ")
            append("<upnp:artist>").append(escape(sanitize(track.artist.orEmpty()))).append("</upnp:artist> ")
            append("<upnp:album>").append(escape(sanitize(track.album.orEmpty()))).append("</upnp:album> ")
            append("<upnp:albumArtURI>").append(escape(sanitize(track.albumArtUri.orEmpty()))).append("</upnp:albumArtURI> ")
            append("</item> </DIDL-Lite> ")
        }
        // 原厂做法：DIDL 先整段转义，再作为 QueueContext 的文本内容被 SOAP 层转义第二次
        return escape(raw)
    }

    fun build(name: String, tracks: List<QueueTrack>): String = buildString {
        append("<?xml version=\"1.0\" ?>")
        append("<PlayList>")
        append("<ListName>").append(escape(sanitize(name))).append("</ListName>")
        append("<ListInfo>")
        append("<Radio>0</Radio>")
        append("<SourceName>").append(escape(sanitize(name))).append("</SourceName>")
        append("<TrackNumber>").append(tracks.size).append("</TrackNumber>")
        append("<SearchUrl></SearchUrl>")
        append("<Quality>").append(QUALITY).append("</Quality>")
        append("</ListInfo>")
        if (tracks.isNotEmpty()) {
            append("<Tracks>")
            tracks.forEachIndexed { i, t ->
                val n = i + 1                       // 标签名带序号：<Track1>…<TrackN>
                append("<Track").append(n).append(">")
                append("<Source>").append(escape(sanitize(name))).append("</Source>")
                append("<URL>").append(escape(t.url)).append("</URL>")
                append("<Id>").append(t.id).append("</Id>")
                append("<Key></Key>")
                append("<Metadata>").append(didlFor(t)).append("</Metadata>")
                append("</Track").append(n).append(">")
            }
            append("</Tracks>")
        }
        append("</PlayList>")
    }

    /**
     * 解析设备 `BrowseQueue` 返回的 QueueContext。
     *
     * 真实返回形如（`.`=换行）：
     * ```
     * <PlayList>.<ListName>X</ListName>.<ListInfo>.…<TrackNumber>4</TrackNumber>.…</ListInfo>
     * .<Tracks>.<Track1>.<URL>…</URL>.<Metadata>&lt;DIDL-Lite…&gt;</Metadata>.<Id>0</Id>.…</Track1>…</Tracks>.
     * </PlayList>
     * ```
     * 注意 `<Metadata>` 里是**再次转义**的 DIDL，要连解两次才能拿到 `<dc:title>`。
     */
    fun parse(xml: String): DevicePlaylist? {
        if (xml.isBlank()) return null
        // 有的固件把整份 XML 再转义一层
        val text = if (xml.contains("&lt;PlayList&gt;")) unescape(xml) else xml
        val name = tag(text, "ListName") ?: return null
        val tracks = Regex("<Track(\\d+)>(.*?)</Track\\1>", RegexOption.DOT_MATCHES_ALL)
            .findAll(text)
            .mapNotNull { m -> parseTrack(m.groupValues[2]) }
            .toList()
        // 当前位置：设备用 LastPlayIndex（1 基，真机实测与 GetQueueIndex 一致）；
        // RealIndex 是"分页内的下标"，实测常为 0，不能当曲目序号用。
        val oneBased = tag(text, "LastPlayIndex")?.toIntOrNull()?.takeIf { it >= 1 }
            ?: tag(text, "RealIndex")?.toIntOrNull()?.takeIf { it >= 1 }
        val index = if (oneBased != null) oneBased - 1 else -1
        return DevicePlaylist(name = name, tracks = tracks, currentIndex = index)
    }

    private fun parseTrack(block: String): QueueTrack? {
        val url = tag(block, "URL")?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val metaRaw = tag(block, "Metadata").orEmpty()
        // Metadata 自身是转义过的 DIDL；连解两次以兼容"少转义一层"的固件
        var meta = unescape(metaRaw)
        if (meta.contains("&lt;DIDL-Lite")) meta = unescape(meta)
        val title = deepUnescape(tag(meta, "dc:title").orEmpty())
        val artist = deepUnescape(tag(meta, "upnp:artist", "dc:creator").orEmpty()).ifBlank { null }
        val album = deepUnescape(tag(meta, "upnp:album").orEmpty()).ifBlank { null }
        val art = deepUnescape(tag(meta, "upnp:albumArtURI").orEmpty()).ifBlank { null }
        val dur = Regex("duration=\"(\\d+)\"").find(meta)?.groupValues?.get(1)?.toLongOrNull() ?: 0
        val id = tag(block, "Id")?.toLongOrNull() ?: 0
        return QueueTrack(
            url = deepUnescape(url),
            title = if (title.isBlank()) url.substringAfterLast('/') else title,
            artist = artist,
            album = album,
            albumArtUri = art,
            durationSec = dur,
            id = id,
        )
    }

    /** 取第一个匹配标签的文本（支持别名，按顺序尝试）。 */
    private fun tag(xml: String, vararg names: String): String? {
        for (n in names) {
            val m = Regex("<$n>(.*?)</$n>", RegexOption.DOT_MATCHES_ALL).find(xml)
            if (m != null) return m.groupValues[1]
        }
        return null
    }
}
