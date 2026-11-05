package io.github.laozhudu.linkplay.discovery

/**
 * 一条 SSDP 响应（M-SEARCH 的回应，或设备主动发的 NOTIFY）—— **纯函数解析，可测**。
 *
 * ## 为什么把它从 SsdpDiscovery 里抽出来
 *
 * `SsdpDiscovery` 是 252 行、一个文件、**零测试**的模块（8 个模块里唯一）。
 * 原因不是不想测，而是它的逻辑全缠在 socket 收发里 —— 要测就得开真 socket。
 *
 * 但真正容易错的那部分（解析报文）完全可以离线测：大小写、CRLF、值里带冒号、
 * USN 尾部挂 urn。这些错了的表现是"设备扫不到"，而真机上只能看到"列表是空的"。
 */
data class SsdpResponse(
    /** `LOCATION` 头：设备描述地址。**没有它这条响应就没用**。 */
    val location: String? = null,
    /** `USN` 头原文。 */
    val usn: String? = null,
    /** `ST` / `NT`：这条响应对应哪个搜索目标。 */
    val searchTarget: String? = null,
    val server: String? = null,
    val headers: Map<String, String> = emptyMap(),
) {
    /**
     * uSN 里抽 uuid。
     *
     * 实际形态长这样：`uuid:FF310096-11F6-4576-72B5-250D::urn:schemas-upnp-org:device:MediaRenderer:1`
     * —— `::` 后面是服务类型，不是 uuid 的一部分。
     */
    val uuid: String?
        get() {
            val raw = usn ?: return null
            val afterPrefix = raw.substringAfter("uuid:", "").trim()
            if (afterPrefix.isEmpty()) return null
            return afterPrefix.substringBefore("::").trim().takeIf { it.isNotEmpty() }
        }

    /** 能不能拿来继续做（拉描述、连设备）。 */
    val usable: Boolean get() = !location.isNullOrBlank()
}

/**
 * 解析一条 SSDP 报文。**返回 null 表示"这根本不是 SSDP 响应"**（空、没有状态行）。
 *
 * 宽容三处，都有原因：
 * 1. 大小写 —— 有些设备的头名是全大写 `LOCATION`，有些是 `Location`；
 * 2. 行尾 —— 标准是 CRLF，但见过只发 `\n` 的实现；
 * 3. 冒号位置 —— 值里本来就带冒号（`http://192.168.1.9:49152/desc.xml`），
 *    所以只能按**第一个**冒号切。
 */
fun parseSsdpResponse(raw: String): SsdpResponse? {
    val text = raw.trim()
    if (text.isEmpty()) return null
    val lines = text.split("\r\n", "\n").filter { it.isNotBlank() }
    if (lines.isEmpty()) return null
    // 首行必须是状态行：`HTTP/1.1 200 OK` 或 `NOTIFY * HTTP/1.1`
    val first = lines.first().uppercase()
    if (!first.startsWith("HTTP/") && !first.startsWith("NOTIFY")) return null

    val headers = LinkedHashMap<String, String>()
    lines.drop(1).forEach { line ->
        val idx = line.indexOf(':')
        if (idx > 0) {
            val key = line.substring(0, idx).trim().lowercase()
            val value = line.substring(idx + 1).trim()
            if (key.isNotEmpty()) headers[key] = value
        }
    }

    return SsdpResponse(
        location = headers["location"]?.takeIf { it.isNotBlank() },
        usn = headers["usn"]?.takeIf { it.isNotBlank() },
        searchTarget = (headers["st"] ?: headers["nt"])?.takeIf { it.isNotBlank() },
        server = headers["server"]?.takeIf { it.isNotBlank() },
        headers = headers,
    )
}
