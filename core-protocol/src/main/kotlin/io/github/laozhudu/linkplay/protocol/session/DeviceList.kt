package io.github.laozhudu.linkplay.protocol.session

import io.github.laozhudu.linkplay.protocol.Json
import io.github.laozhudu.linkplay.protocol.JsonValue
import io.github.laozhudu.linkplay.protocol.JsonWriter

/**
 * 记住的设备清单（纯逻辑部分）。
 *
 * 这里只管「增删改查 + 排序」这类不依赖 Android 的规则，真正的落盘交给 App 层
 * （用已有的 `SessionStore` KV 表存整段 JSON）。
 *
 * 排序规则：**最近用过的排前面**。家的设备通常就一台两台，但排序决定了「打开就点第一个」
 * 是否是顺手的那台；按加入顺序排会让常用设备慢慢沉到后面。
 */
object DeviceList {

    private const val MAX_DEVICES = 16

    fun upsert(current: List<SavedDevice>, incoming: SavedDevice): List<SavedDevice> {
        // 同一 uuid 覆盖；同一 IP 但 uuid 不同则视为「换了一台设备占了这个 IP」，两条都保留
        val replaced = current.map { if (it.uuid == incoming.uuid) incoming else it }
        val merged = if (replaced.any { it.uuid == incoming.uuid }) replaced else replaced + incoming
        return merged
            .sortedByDescending { it.lastSeenMs }
            .take(MAX_DEVICES)
    }

    fun remove(current: List<SavedDevice>, uuid: String): List<SavedDevice> =
        current.filterNot { it.uuid == uuid }

    /** 上次用过的那台（首页「继续」按钮用）。 */
    fun mostRecent(current: List<SavedDevice>): SavedDevice? =
        current.maxByOrNull { it.lastSeenMs }

    fun encode(list: List<SavedDevice>): String =
        JsonWriter.encode(JsonValue.Arr(list.map { JsonValue.Str(it.encode()) }))

    fun decode(text: String?): List<SavedDevice> {
        if (text.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = Json.parse(text) as? JsonValue.Arr ?: return emptyList()
            arr.items.mapNotNull { (it as? JsonValue.Str)?.let { s -> SavedDevice.decode(s.value) } }
                .sortedByDescending { it.lastSeenMs }
        }.getOrDefault(emptyList())
    }
}
