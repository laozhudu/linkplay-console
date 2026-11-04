package io.github.laozhudu.linkplay.protocol.session

import io.github.laozhudu.linkplay.protocol.Json
import io.github.laozhudu.linkplay.protocol.JsonValue
import io.github.laozhudu.linkplay.protocol.JsonWriter
import io.github.laozhudu.linkplay.protocol.identity.UuidMatch

/**
 * 记住的设备清单（纯逻辑部分）。
 *
 * 这里只管「增删改查 + 排序」这类不依赖 Android 的规则，真正的落盘交给 App 层
 * （用已有的 `SessionStore` KV 表存整段 JSON）。
 *
 * 排序规则：**最近用过的排前面**。家的设备通常就一台两台，但排序决定了「打开就点第一个」
 * 是否是顺手的那台；按加入顺序排会让常用设备慢慢沉到后面。
 *
 * ## 去重用的是"宽容 uuid"而不是字符串相等
 *
 * 老版本把同一台音箱存成过两条（一条 24 位、一条 32 位带连字符，见 [SavedDevice] 的注释）。
 * 用户升级后不该看到两台一样的设备，所以 [decode] 里按 [UuidMatch.same] 合并 ——
 * **不是**按 `uuid ==` 比，那样两条脏数据都会留着。
 */
object DeviceList {

    private const val MAX_DEVICES = 16

    fun upsert(current: List<SavedDevice>, incoming: SavedDevice): List<SavedDevice> {
        // 同一台设备（宽容判等）覆盖；同 IP 但确实是另一台则两条都保留
        val replaced = current.map { if (UuidMatch.same(it.uuid, incoming.uuid)) incoming else it }
        val merged = if (replaced.any { UuidMatch.same(it.uuid, incoming.uuid) }) {
            replaced
        } else {
            replaced + incoming
        }
        return merged
            .sortedByDescending { it.lastSeenMs }
            .take(MAX_DEVICES)
    }

    fun remove(current: List<SavedDevice>, uuid: String): List<SavedDevice> =
        current.filterNot { UuidMatch.same(it.uuid, uuid) }

    /** 上次用过的那台（首页「继续」按钮用）。 */
    fun mostRecent(current: List<SavedDevice>): SavedDevice? =
        current.maxByOrNull { it.lastSeenMs }

    fun encode(list: List<SavedDevice>): String =
        JsonWriter.encode(JsonValue.Arr(list.map { JsonValue.Str(it.encode()) }))

    fun decode(text: String?): List<SavedDevice> {
        if (text.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = Json.parse(text) as? JsonValue.Arr ?: return emptyList()
            val raw = arr.items.mapNotNull { (it as? JsonValue.Str)?.let { s -> SavedDevice.decode(s.value) } }
            dedupe(raw).sortedByDescending { it.lastSeenMs }
        }.getOrDefault(emptyList())
    }

    /**
     * 合并同一台设备的重复记录。
     *
     * 保留**最近见过**的那条（它的 IP 更新），但名字取"第一个非空的" ——
     * 因为名字是用户设的，而 IP 是会漂的；丢了名字比丢 IP 更让用户困惑。
     */
    private fun dedupe(list: List<SavedDevice>): List<SavedDevice> {
        val result = ArrayList<SavedDevice>(list.size)
        list.sortedByDescending { it.lastSeenMs }.forEach { device ->
            val existingIndex = result.indexOfFirst { UuidMatch.same(it.uuid, device.uuid) }
            if (existingIndex < 0) {
                result.add(device)
            } else {
                val existing = result[existingIndex]
                if (existing.name.isBlank() && device.name.isNotBlank()) {
                    result[existingIndex] = existing.copy(name = device.name)
                }
            }
        }
        return result
    }
}
