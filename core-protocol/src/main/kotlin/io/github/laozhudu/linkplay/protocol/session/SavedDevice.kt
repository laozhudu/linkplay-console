package io.github.laozhudu.linkplay.protocol.session

import io.github.laozhudu.linkplay.protocol.Json
import io.github.laozhudu.linkplay.protocol.JsonValue
import io.github.laozhudu.linkplay.protocol.JsonWriter

/**
 * 一台被记住的音箱。
 *
 * 为什么要存而不是每次搜：SSDP 组播在部分路由器/手机上收不到（真机调试期就遇到过），
 * 而且「打开 App → 等 3 秒扫描 → 点设备」这三步对每天要用的人来说是纯浪费。
 * 记住之后首页一眼就能点开。
 *
 * 键用 **uuid** 而不是 ip：真机从 `.164` 漂到 `.153` 是实测过的（DHCP 续租），
 * 用 IP 当键会让记录直接失效。
 */
data class SavedDevice(
    val uuid: String,
    val name: String,
    /** 最近一次成功通信时的 IP。可能是旧的，用前要探活。 */
    val ip: String,
    /** UPnP 描述地址（`http://ip:49152/description.xml`）。手动输 IP 添加的设备为空。 */
    val location: String? = null,
    val modelName: String? = null,
    val lastSeenMs: Long = 0L,
) {
    /** 展示用：名字 + IP。名字为空时退回 IP，避免出现空标题。 */
    val title: String get() = name.ifBlank { ip }

    fun encode(): String = JsonWriter.encode(
        JsonValue.Obj(
            buildMap {
                put("uuid", JsonValue.Str(uuid))
                put("name", JsonValue.Str(name))
                put("ip", JsonValue.Str(ip))
                location?.let { put("location", JsonValue.Str(it)) }
                modelName?.let { put("model", JsonValue.Str(it)) }
                put("seen", JsonValue.Num(lastSeenMs.toDouble()))
            },
        ),
    )

    companion object {
        fun decode(text: String?): SavedDevice? {
            if (text.isNullOrBlank()) return null
            return runCatching {
                val o = Json.parse(text) as? JsonValue.Obj ?: return null
                val uuid = o.string("uuid")?.takeIf { it.isNotBlank() } ?: return null
                val ip = o.string("ip")?.takeIf { it.isNotBlank() } ?: return null
                SavedDevice(
                    uuid = uuid,
                    name = o.string("name").orEmpty(),
                    ip = ip,
                    location = o.string("location"),
                    modelName = o.string("model"),
                    lastSeenMs = (o["seen"] as? JsonValue.Num)?.value?.toLong() ?: 0L,
                )
            }.getOrNull()
        }
    }
}
