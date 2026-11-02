package io.github.laozhudu.linkplay.protocol.ssdp

/**
 * SSDP 报文构造与解析（纯 JVM，可单测）。
 *
 * 为什么单独抽出来：发现逻辑原来只在 Android 库里，没法用普通 JUnit 覆盖；
 * 而报文格式恰恰是最容易写错的地方（MX/HOST/MAN 三个头有一个不对设备就不回）。
 *
 * 实机事实（DOSS DS-1831）：设备对**单播** M-SEARCH 也回 200，
 * 因此跨网段/VPN 时不需要组播也能发现——只要把包直接发给它的 IP。
 */
data class SsdpResponse(
    val location: String?,
    val usn: String?,
    val st: String?,
    val server: String?,
    /** 从 USN 里抽出的 device uuid（形如 `uuid:FF31F017-...`），没有则 null。 */
    val uuid: String?,
) {
    /** 设备 IP 从 LOCATION 里取，避免依赖收包源地址（NAT/转发场景下两者可能不同）。 */
    val hostFromLocation: String?
        get() = location
            ?.removePrefix("http://")
            ?.removePrefix("https://")
            ?.substringBefore('/')
            ?.substringBefore(':')
}

object SsdpMessages {

    const val MULTICAST_ADDRESS = "239.255.255.250"
    const val PORT = 1900

    /** 厂商私有服务：有它才说明能整列表推送。 */
    const val ST_WIIMU_PLAY_QUEUE = "urn:schemas-wiimu-com:service:PlayQueue:1"

    /** 腾讯 QPlay（QQ 音乐通道），实机确认存在于 DOSS DS-1831。 */
    const val ST_QPLAY = "urn:schemas-tencent-com:service:QPlay:1"

    const val ST_MEDIA_RENDERER = "urn:schemas-upnp-org:device:MediaRenderer:1"

    /** DLNA 媒体服务器（NAS 上的 MiniDLNA / fnOS DLNA 等）。 */
    const val ST_MEDIA_SERVER = "urn:schemas-upnp-org:device:MediaServer:1"

    /** 搜索顺序：先私有服务（信息量最大），再标准渲染器，最后 all 兜底老固件。 */
    val DEFAULT_SEARCH_TARGETS = listOf(ST_WIIMU_PLAY_QUEUE, ST_MEDIA_RENDERER, "ssdp:all")

    /**
     * M-SEARCH 请求。`MX` 给设备一个应答窗口，太大拖慢扫描、太小老设备来不及回。
     */
    fun buildMSearch(searchTarget: String, mx: Int = 2): String = buildString {
        append("M-SEARCH * HTTP/1.1\r\n")
        append("HOST: ").append(MULTICAST_ADDRESS).append(':').append(PORT).append("\r\n")
        append("MAN: \"ssdp:discover\"\r\n")
        append("MX: ").append(mx).append("\r\n")
        append("ST: ").append(searchTarget).append("\r\n")
        append("USER-AGENT: Android/1.0 UPnP/1.0 LinkPlayConsole/1.0\r\n")
        append("\r\n")
    }

    /** 解析 SSDP 应答；不是 200 响应或没有 LOCATION 时返回 null。 */
    fun parseResponse(raw: String): SsdpResponse? {
        if (!raw.startsWith("HTTP/1.1 200", ignoreCase = true)) return null
        val headers = HashMap<String, String>()
        raw.lineSequence().drop(1).forEach { line ->
            val idx = line.indexOf(':')
            if (idx > 0) {
                headers[line.substring(0, idx).trim().lowercase()] = line.substring(idx + 1).trim()
            }
        }
        val location = headers["location"] ?: return null
        val usn = headers["usn"]
        return SsdpResponse(
            location = location,
            usn = usn,
            st = headers["st"],
            server = headers["server"],
            uuid = uuidOf(usn),
        )
    }

    /**
     * 从 USN 抽 uuid。两种形状都要支持：
     * - `uuid:FF31F017-...`（根设备）
     * - `uuid:FF31F017-...::urn:schemas-wiimu-com:service:PlayQueue:1`（服务级）
     */
    fun uuidOf(usn: String?): String? {
        if (usn.isNullOrBlank()) return null
        val head = usn.substringBefore("::")
        return head.trim().takeIf { it.startsWith("uuid:", ignoreCase = true) }
    }
}
