package io.github.laozhudu.linkplay.protocol.identity

import io.github.laozhudu.linkplay.protocol.upnp.DeviceDescription
import io.github.laozhudu.linkplay.protocol.upnp.UpnpServices

/**
 * 判断「这台 UPnP 设备是不是我们要的那一类」。
 *
 * 为什么必须筛：局域网里的 UPnP 设备远不止音箱 —— NAS、路由器、电视、打印机、智能插座都会响应
 * SSDP 的 `upnp:rootdevice`。原来 `discover()` 把响应的全收进来，首页就变成一堆 `192.168.99.x`，
 * 用户根本不知道点哪个，而且点错了还会往路由器投流。
 *
 * 判据分三档，**从便宜到昂贵**，能在前面拦掉就不往后走：
 *
 * 1. SSDP 包头（零成本）：`ST`/`USN`/`SERVER` 里出现 wiimu / LinkPlay 字样；
 * 2. 设备描述（一次 HTTP）：声明了 LinkPlay 私有服务 —— 这是**最硬的证据**，
 *    真机 `192.168.99.153` 的描述里有
 *    `urn:schemas-wiimu-com:service:PlayQueue:1` 与 `urn:schemas-tencent-com:service:QPlay:1`；
 * 3. httpapi 探活（一次 HTTP）：`getStatusEx` 返回含 `project`/`firmware` 的 JSON。
 *
 * 第 3 档留给「描述里看不出品牌」的白标固件 —— 但那一步需要联网，所以放在 App 层做，
 * 本对象只负责前两档，保持纯 JVM 可测。
 */
object LinkPlayIdentity {

    /** 这些字符串出现在 SSDP 头里，就可以先标记为「可能」。 */
    private val BRAND_HINTS = listOf("wiimu", "linkplay", "doss")

    /** 设备描述里出现这些 serviceType 就是铁证（厂商私有命名空间）。 */
    private val SIGNATURE_SERVICES = listOf(
        UpnpServices.WIIMU_PLAY_QUEUE,
        "urn:schemas-tencent-com:service:QPlay:1",
    )

    /**
     * 第一道筛：只看 SSDP 包头。
     *
     * 返回 true 只代表「值得进一步拉描述」，不代表一定是音箱 —— 反过来 false 也不代表排除，
     * 因为不少固件把品牌名写在描述里而不是 SSDP 头里（真机就是这样：
     * `SERVER` 是 `Linux/3.x UPnP/1.0`，看不出任何品牌）。
     */
    fun looksPromising(server: String?, usn: String?, searchTarget: String?): Boolean {
        val haystack = listOfNotNull(server, usn, searchTarget).joinToString(" ").lowercase()
        return BRAND_HINTS.any { haystack.contains(it) }
    }

    /**
     * 第二道筛（权威）：看设备描述。
     *
     * 三条判据任意成立即认定为 LinkPlay 系：
     * - 声明了 wiimu/QPlay 私有服务（真机即此）；
     * - manufacturer / modelName 含品牌字样；
     * - 同时具备 AVTransport 与 RenderingControl **且** 有 PlayQueue（私有服务兜底）。
     *
     * 注意第三条是 `且`：只靠标准 AVTransport+RenderingControl 会把电视和功放也算进来。
     */
    fun isLinkPlayFamily(desc: DeviceDescription): Boolean {
        val types = desc.services.map { it.serviceType.lowercase() }
        if (SIGNATURE_SERVICES.any { sig -> types.any { it == sig.lowercase() } }) return true

        val brandText = listOfNotNull(desc.manufacturer, desc.modelName, desc.friendlyName)
            .joinToString(" ")
            .lowercase()
        if (BRAND_HINTS.any { brandText.contains(it) }) return true

        val hasPlayQueue = types.any { it.contains("playqueue") }
        return hasPlayQueue &&
            desc.avTransportControlUrl != null &&
            desc.renderingControlUrl != null
    }

    /**
     * 给用户看的一句话说明，说明「为什么这台设备被收录」。
     * 排查「为什么我的音箱没出现」时这行字很关键。
     */
    fun reasonFor(desc: DeviceDescription): String = when {
        desc.services.any { it.serviceType.equals(UpnpServices.WIIMU_PLAY_QUEUE, ignoreCase = true) } ->
            "LinkPlay 私有播放列表服务"
        desc.services.any { it.serviceType.lowercase().contains("qplay") } -> "QPlay 服务"
        listOfNotNull(desc.manufacturer, desc.modelName, desc.friendlyName)
            .joinToString(" ").lowercase().let { t -> BRAND_HINTS.any { t.contains(it) } } -> "品牌标识"
        else -> "播放列表 + DLNA 渲染器"
    }
}
