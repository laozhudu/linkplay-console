package io.github.laozhudu.linkplay.protocol.session

import io.github.laozhudu.linkplay.protocol.Json
import io.github.laozhudu.linkplay.protocol.JsonValue
import io.github.laozhudu.linkplay.protocol.JsonWriter

/** 上次连过的 DLNA 媒体服务器。存 host/port 而不是完整 URL，方便改端口后仍能复用。 */
data class NasServerRef(
    val host: String,
    val port: Int,
    val friendlyName: String? = null,
) {
    /** 描述文件地址（DLNA 的标准入口之一）。 */
    val rootDescUrl: String get() = "http://$host:$port/rootDesc.xml"

    /** 手输框里显示的形式。 */
    val label: String get() = if (port == 80) host else "$host:$port"
}

/** 浏览路径上的一层。 */
data class NasPathStep(val objectId: String, val title: String)

/**
 * 一台设备的 NAS 会话：连的是谁 + 上次停在哪一层。
 *
 * 为什么要按**设备**存：家里可能有两台音箱，各自上次听的库和目录不同；
 * 而且设备换了 DHCP 租约（`.164` → `.153` 实测发生过）后，用 IP 做键会让记忆全丢，
 * 所以上层用设备 UUID 做键，这个对象本身只管内容。
 */
data class NasSession(
    val server: NasServerRef,
    val path: List<NasPathStep> = emptyList(),
) {
    fun encode(): String = JsonWriter.encode(
        JsonValue.Obj(
            mapOf(
                "server" to JsonValue.Obj(
                    buildMap {
                        put("host", JsonValue.Str(server.host))
                        put("port", JsonValue.Num(server.port.toDouble()))
                        server.friendlyName?.let { put("name", JsonValue.Str(it)) }
                    },
                ),
                "path" to JsonValue.Arr(
                    path.map {
                        JsonValue.Obj(mapOf("id" to JsonValue.Str(it.objectId), "title" to JsonValue.Str(it.title)))
                    },
                ),
            ),
        ),
    )

    companion object {
        /** 解析失败返回 null —— 记忆是「锦上添花」，坏了就当没有，不能让它拖垮页面。 */
        fun decode(text: String?): NasSession? {
            if (text.isNullOrBlank()) return null
            return runCatching {
                val root = Json.parse(text) as? JsonValue.Obj ?: return null
                val s = root.obj("server") ?: return null
                val host = s.string("host")?.takeIf { it.isNotBlank() } ?: return null
                val port = s.int("port")?.takeIf { it in 1..65535 } ?: 8200
                val path = root.array("path")?.items.orEmpty().mapNotNull { v ->
                    val o = v as? JsonValue.Obj ?: return@mapNotNull null
                    val id = o.string("id") ?: return@mapNotNull null
                    NasPathStep(id, o.string("title").orEmpty())
                }
                NasSession(NasServerRef(host, port, s.string("name")), path)
            }.getOrNull()
        }
    }
}
