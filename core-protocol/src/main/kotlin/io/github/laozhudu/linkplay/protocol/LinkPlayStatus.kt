package io.github.laozhudu.linkplay.protocol

/**
 * `getStatusEx` 的返回模型。
 *
 * 字段名取自原厂 App 的解析代码（`com.wifiaudio.a.j`），全部为顶层字符串/整数：
 * ssid, hardware, firmware, uuid, eth2, apcli0, essid, expired, internet, netstat,
 * language, Release, build, securemode。
 */
data class LinkPlayStatus(
    val ssid: String?,
    val hardware: String?,
    val firmware: String?,
    val uuid: String?,
    val ethernetMac: String?,
    val wifiMac: String?,
    /** 当前连接的上游 SSID；设备可能返回 hex，这里已尝试解码。 */
    val essid: String?,
    val expired: Int?,
    /** 1 = 已联网。 */
    val internet: Int?,
    val netstat: Int?,
    val language: String?,
    val release: String?,
    val build: String?,
    val secureMode: String?,
) {
    val isOnline: Boolean get() = internet == 1

    companion object {
        fun parse(json: String): LinkPlayStatus {
            val root = Json.parse(json) as? JsonValue.Obj
                ?: throw JsonParseException("getStatusEx 返回的不是 JSON 对象")
            return LinkPlayStatus(
                ssid = root.string("ssid"),
                hardware = root.string("hardware"),
                firmware = root.string("firmware"),
                uuid = root.string("uuid"),
                ethernetMac = root.string("eth2"),
                wifiMac = root.string("apcli0"),
                essid = root.string("essid")?.let { HexCodec.decodeIfHex(it) },
                expired = root.int("expired"),
                internet = root.int("internet"),
                netstat = root.int("netstat"),
                language = root.string("language"),
                release = root.string("Release"),
                build = root.string("build"),
                secureMode = root.string("securemode"),
            )
        }
    }
}

/**
 * `multiroom:getSlaveList` 的返回模型。
 *
 * 形如 `{"slave_list":[{"ip":"...","mask":"0|1","version":"...","uuid":"...",
 * "name":"...","ssid":"...","volume":42}]}`。
 * `mask=1` 表示该从机已加入本机群组，`mask=0` 表示未加入。
 */
data class SlaveDevice(
    val ip: String,
    val inGroup: Boolean,
    val version: String?,
    val uuid: String?,
    val name: String?,
    val ssid: String?,
    val volume: Int?,
) {
    companion object {
        fun parseList(json: String): List<SlaveDevice> {
            val root = Json.parse(json) as? JsonValue.Obj ?: return emptyList()
            val list = root.array("slave_list") ?: return emptyList()
            return list.items.mapNotNull { item ->
                val obj = item as? JsonValue.Obj ?: return@mapNotNull null
                val ip = obj.string("ip") ?: return@mapNotNull null
                SlaveDevice(
                    ip = ip,
                    inGroup = obj.string("mask")?.trim()?.let { it != "0" } ?: false,
                    version = obj.string("version"),
                    uuid = obj.string("uuid"),
                    name = obj.string("name"),
                    ssid = obj.string("ssid")?.let { HexCodec.decodeIfHex(it) },
                    volume = obj.int("volume"),
                )
            }
        }
    }
}
