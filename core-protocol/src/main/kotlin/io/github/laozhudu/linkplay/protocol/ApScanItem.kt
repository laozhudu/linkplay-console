package io.github.laozhudu.linkplay.protocol

/**
 * `wlanGetApListEx` 返回的单条 AP。
 *
 * 线上格式（已确证）：
 * ```json
 * {"aplist":[{"ssid":"Home","bssid":"aa:bb:cc:dd:ee:ff","rssi":-47,
 *             "channel":6,"auth":"WPA2PSK","encry":"AES","extch":0}]}
 * ```
 * - `ssid` / `bssid` / `auth` / `encry` 是字符串
 * - `rssi` / `channel` / `extch` 是整数（rssi 为负值 dBm）
 * - `extch` 是扩展信道（40MHz 绑定宽度用），原厂在 AP 直连时恒写 `chext=0`，
 *   是否应改用该值尚未确证，见协议文档 §10。
 */
data class ApScanItem(
    val ssid: String,
    val bssid: String?,
    val rssi: Int?,
    val channel: Int,
    val auth: String,
    val encry: String,
    val extch: Int?,
) {
    /** 无密码网络：`auth` 为 OPEN/NONE 类值。 */
    val isOpen: Boolean
        get() = auth.equals("OPEN", ignoreCase = true) || auth.equals("NONE", ignoreCase = true)

    /** 信号强度分档，便于 UI 用固定图标而不依赖具体 dBm 阈值实现。 */
    val signalLevel: Int
        get() {
            val r = rssi ?: return 0
            return when {
                r >= -55 -> 4
                r >= -67 -> 3
                r >= -78 -> 2
                r >= -88 -> 1
                else -> 0
            }
        }

    companion object {
        /** 解析 `{"aplist":[...]}`；单条解析失败跳过，整体格式错误返回空列表。 */
        fun parseList(json: String): List<ApScanItem> {
            val root = runCatching { Json.parse(json) }.getOrNull() as? JsonValue.Obj ?: return emptyList()
            val list = root.array("aplist") ?: return emptyList()
            return list.items.mapNotNull { item ->
                val obj = item as? JsonValue.Obj ?: return@mapNotNull null
                val ssid = obj.string("ssid") ?: return@mapNotNull null
                ApScanItem(
                    ssid = ssid,
                    bssid = obj.string("bssid"),
                    rssi = obj.int("rssi"),
                    channel = obj.int("channel") ?: 0,
                    auth = obj.string("auth") ?: "OPEN",
                    encry = obj.string("encry") ?: "NONE",
                    extch = obj.int("extch"),
                )
            }
        }
    }
}
