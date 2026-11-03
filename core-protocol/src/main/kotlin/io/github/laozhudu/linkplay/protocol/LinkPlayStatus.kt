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
    /**
     * 电池百分比（`battery_percent`，真机 `.164` 返回 `100`）。
     *
     * 原厂不解析这个字段 —— 它在从机列表里取 `battery`。但独立设备（没组网）
     * 只能从 `getStatusEx` 拿到电量，所以两边都读：主机/独立=这里，从机=`slave_list[].battery`。
     */
    val batteryPercent: Int? = null,
    /**
     * 设备名（`DeviceName`）。
     *
     * **这才是改名的真值来源**：真机实测 `setDeviceName:<名字>` 之后
     * `getStatusEx.DeviceName` 立刻变，而 `description.xml` 的 `friendlyName` 要滞后几秒
     * （`.153` 实测：2 秒时还是旧名，5 秒才更新）。
     * 之前我们只用 friendlyName 回读，改完 800ms 就读 → 读回旧名 → 把用户设的名字又写回去，
     * 表现就是"改名不成功"。
     */
    val deviceName: String? = null,
    /**
     * 是否在用电池（`battery` 字段，真机 `"1"`=用电池、`"0"`=接电/充电）。
     *
     * 真机对照：`.164` 插着电 → `battery_percent=100` 且 `battery=0`；
     * `.153` 在放电 → `battery_percent=40` 且 `battery=1`。
     * 原厂把它当 `BatteryFlag` 用（见 `GetControlDeviceInfo` 的 map 解析），
     * 所以界面可以区分「⚡充电中 100%」和「🔋40%」—— 这也是 Sonos 等成熟产品的做法。
     */
    val onBattery: Boolean? = null,
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
                // 真机返回的是**字符串** "100"（不是数字），两种都认
                batteryPercent = root.string("battery_percent")?.toIntOrNull()
                    ?: root.int("battery_percent"),
                deviceName = root.string("DeviceName")?.takeIf { it.isNotBlank() },
                onBattery = (root.string("battery") ?: root.int("battery")?.toString())?.trim()?.let { it == "1" },
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
    /** 从机电量百分比（`battery`，真机从机返回 60/70）。 */
    val battery: Int? = null,
    /** 声道：0 立体声 / 1 仅左 / 2 仅右（`channel`）。 */
    val channel: Int? = null,
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
                    battery = obj.int("battery"),
                    channel = obj.int("channel"),
                )
            }
        }
    }
}
