package io.github.laozhudu.linkplay.protocol.upnp

/**
 * DLNA MediaServer 的 ContentDirectory 控制点（浏览 NAS/媒体服务器上的内容）。
 *
 * 为什么需要它：之前整条链路假设「手机当文件服务器」，但真机证明音箱能直接拉 NAS
 * （docs/09 的结论在真机上成立：`http://192.168.99.223:8200/MediaItems/24.mp3` 直接播起来了）。
 * 于是「局域网 DLNA 发现媒体文件」= 用 ContentDirectory 浏览 MediaServer，比在手机上跑 HTTP 服务更稳。
 *
 * 已实测的对象：`fnOS`（MiniDLNA 系）在 `:8200`，根目录返回
 * `Browse Folders(64)` / `Music(1)` / `Pictures(3)` / `Video(2)`，Music 下有
 * `Album` / `All Music` / `Artist` / `Folders` / `Genre` / `Playlists` / `Recently Added` 七个容器。
 */
object ContentDirectory {
    const val SERVICE_TYPE = "urn:schemas-upnp-org:service:ContentDirectory:1"
    const val ROOT_ID = "0"

    /** 浏览模式。 */
    const val BROWSE_DIRECT_CHILDREN = "BrowseDirectChildren"
    const val BROWSE_METADATA = "BrowseMetadata"
}

/** 浏览返回的一个条目：容器（目录）或媒体项（可播放）。 */
data class BrowseEntry(
    val id: String,
    val parentId: String?,
    val title: String,
    /** true = 目录，false = 可播放项。 */
    val isContainer: Boolean,
    val upnpClass: String?,
    val artist: String? = null,
    val album: String? = null,
    val albumArtUri: String? = null,
    /** `res` 的媒体地址（可播放项才有）。 */
    val url: String? = null,
    val protocolInfo: String? = null,
    /** DIDL 的 `duration` 是 `H:MM:SS.mmm`。 */
    val durationSec: Long = 0,
    val sizeBytes: Long = 0,
) {
    val displayName: String get() = artist?.takeIf { it.isNotBlank() }?.let { "$title · $it" } ?: title
}

/** 一次 Browse 的结果。 */
data class BrowseResult(
    val entries: List<BrowseEntry>,
    val totalMatches: Int,
    /** 用于分页：`totalMatches > startingIndex + entries.size` 时还有下一页。 */
    val startingIndex: Int,
) {
    val hasMore: Boolean get() = totalMatches > startingIndex + entries.size
}

/**
 * DIDL-Lite 响应解析器。
 *
 * 只解析浏览场景实际出现的字段。`<container>`/`<item>` 的属性顺序不固定，
 * 因此用「先取整块再取值」的方式解析，而不是依赖正则捕获组的顺序。
 */
object DidlParser {

    fun parseResult(resultXml: String): List<BrowseEntry> = parseEntries(resultXml)

    fun parseEntries(didl: String): List<BrowseEntry> {
        val out = ArrayList<BrowseEntry>()
        // container：目录
        Regex("<container\\b([^>]*)>(.*?)</container>", RegexOption.DOT_MATCHES_ALL).findAll(didl).forEach { m ->
            val attrs = attrMap(m.groupValues[1])
            val body = m.groupValues[2]
            out += BrowseEntry(
                id = attrs["id"].orEmpty(),
                parentId = attrs["parentID"],
                title = UpnpXml.value(body, "dc:title").orEmpty().trim(),
                isContainer = true,
                upnpClass = UpnpXml.value(body, "upnp:class")?.trim(),
            )
        }
        // item：可播放项
        Regex("<item\\b([^>]*)>(.*?)</item>", RegexOption.DOT_MATCHES_ALL).findAll(didl).forEach { m ->
            val attrs = attrMap(m.groupValues[1])
            val body = m.groupValues[2]
            val resAttrs = Regex("<res\\b([^>]*)>", RegexOption.DOT_MATCHES_ALL)
                .find(body)?.groupValues?.get(1)?.let { attrMap(it) }
            out += BrowseEntry(
                id = attrs["id"].orEmpty(),
                parentId = attrs["parentID"],
                title = UpnpXml.value(body, "dc:title").orEmpty().trim(),
                isContainer = false,
                upnpClass = UpnpXml.value(body, "upnp:class")?.trim(),
                artist = UpnpXml.value(body, "upnp:artist")?.trim()?.takeIf { it.isNotEmpty() },
                album = UpnpXml.value(body, "upnp:album")?.trim()?.takeIf { it.isNotEmpty() },
                albumArtUri = UpnpXml.value(body, "upnp:albumArtURI")?.trim()?.takeIf { it.isNotEmpty() },
                url = UpnpXml.value(body, "res")?.trim()?.takeIf { it.isNotEmpty() },
                protocolInfo = resAttrs?.get("protocolInfo"),
                durationSec = parseDidlDuration(resAttrs?.get("duration")),
                sizeBytes = resAttrs?.get("size")?.toLongOrNull() ?: 0,
            )
        }
        return out
    }

    private fun attrMap(raw: String): Map<String, String> =
        Regex("([A-Za-z_:][\\w:.-]*)\\s*=\\s*\"([^\"]*)\"").findAll(raw)
            .associate { it.groupValues[1] to UpnpXml.unescape(it.groupValues[2]) }

    /** `H:MM:SS.mmm` / `H:MM:SS` / `MM:SS` → 秒。 */
    fun parseDidlDuration(raw: String?): Long {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return 0
        val head = text.substringBefore('.')
        val parts = head.split(':').mapNotNull { it.trim().toLongOrNull() }
        return when (parts.size) {
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            2 -> parts[0] * 60 + parts[1]
            1 -> parts[0]
            else -> 0
        }
    }
}

/**
 * ContentDirectory 客户端。
 *
 * 分页策略：先按 [pageSize] 取一页，需要更多时用 `startingIndex` 继续取，
 * 直到 `entries.size >= totalMatches` 或触发 [maxEntries]（防止一次拉 1900 首把内存打满）。
 */
class ContentDirectoryClient(
    private val controlUrl: String,
    private val transport: UpnpTransport = HttpUpnpTransport(),
    private val pageSize: Int = 100,
) {
    fun browse(objectId: String, startingIndex: Int = 0, requestedCount: Int = pageSize): BrowseResult {
        val body = SoapEnvelope.build(
            ContentDirectory.SERVICE_TYPE,
            "Browse",
            listOf(
                "ObjectID" to objectId,
                "BrowseFlag" to ContentDirectory.BROWSE_DIRECT_CHILDREN,
                "Filter" to "*",
                "StartingIndex" to startingIndex.toString(),
                "RequestedCount" to requestedCount.toString(),
                "SortCriteria" to "",
            ),
        )
        val response = transport.postSoap(
            controlUrl,
            SoapEnvelope.soapAction(ContentDirectory.SERVICE_TYPE, "Browse"),
            body,
        )
        val resultXml = UpnpXml.value(response, "Result").orEmpty()
        return BrowseResult(
            entries = DidlParser.parseResult(resultXml),
            totalMatches = UpnpXml.value(response, "TotalMatches")?.trim()?.toIntOrNull() ?: 0,
            startingIndex = startingIndex,
        )
    }

    /** 取某目录下全部条目（带上限）。 */
    fun browseAll(objectId: String, maxEntries: Int = 2000): List<BrowseEntry> {
        val out = ArrayList<BrowseEntry>()
        var index = 0
        while (out.size < maxEntries) {
            val page = browse(objectId, index, pageSize)
            if (page.entries.isEmpty()) break
            out += page.entries
            if (!page.hasMore) break
            index += page.entries.size
        }
        return out.take(maxEntries)
    }

    companion object {
        /** 从设备描述里取 ContentDirectory 的 controlURL。 */
        fun controlUrlOf(description: DeviceDescription): String? =
            description.controlUrl(ContentDirectory.SERVICE_TYPE)
    }
}
