package io.github.laozhudu.linkplay.protocol.radio

/**
 * 网络电台（对应原厂 `tb_net_radio`：`isKept, isPlaying, soundName, soundUrl, invalid, deviceName`）。
 *
 * 与「第三方内容源」不同，这里只存**用户自己填的流地址**，不内置任何平台接口——
 * 合规边界见 `docs/06-music-sources.md`。
 */
data class RadioStation(
    val name: String,
    val url: String,
    /** 是否常驻在列表里（原厂字段 `isKept`）。 */
    val kept: Boolean = true,
    /** 上次是否在播（原厂字段 `isPlaying`）。 */
    val playing: Boolean = false,
    /** 原厂字段 `invalid`：探测失败后标记，UI 上灰掉而不是删掉。 */
    val invalid: Boolean = false,
    /** 绑定到哪台设备（原厂为每台设备各存一份）。null 表示通用。 */
    val deviceName: String? = null,
    val id: Long = 0,
) {
    companion object {
        /** 允许的流地址：只接受 http/https，避免 `file://`、`content://` 之类被塞进来。 */
        private val SUPPORTED_SCHEMES = listOf("http://", "https://")

        /** 常见流格式后缀，仅用于给出提示，不做硬校验（很多电台 URL 没有后缀）。 */
        private val HINT_EXTENSIONS = listOf("mp3", "m3u8", "m3u", "aac", "ogg", "flac", "pls")

        /** 返回 null 表示合法，否则返回给用户看的原因。 */
        fun validate(name: String, url: String): String? {
            if (name.isBlank()) return "电台名称不能为空"
            if (name.length > 64) return "名称过长（≤64 字符）"
            val trimmed = url.trim()
            if (trimmed.isEmpty()) return "流地址不能为空"
            if (SUPPORTED_SCHEMES.none { trimmed.startsWith(it, ignoreCase = true) }) {
                return "只支持 http/https 流地址"
            }
            if (trimmed.length > 2048) return "地址过长"
            if (trimmed.contains(' ')) return "地址里不能有空格（请对空格做 %20 编码）"
            return null
        }

        /** 从 URL 猜一个默认名称，方便用户只粘贴地址。 */
        fun suggestName(url: String): String {
            val authority = url.substringAfter("://", "").substringBefore('/').substringBefore('?')
            // 去掉端口（`h:8000` → `h`），但别把 IPv6 的冒号当分隔符处理坏
            val host = if (authority.count { it == ':' } == 1) authority.substringBefore(':') else authority
            return host.ifBlank { "网络电台" }
        }

        /** 是否看起来像可识别的流格式（只用于提示，不阻止保存）。 */
        fun looksLikeStream(url: String): Boolean {
            val lower = url.lowercase()
            if (HINT_EXTENSIONS.any { lower.contains(".$it") }) return true
            return lower.contains("stream") || lower.contains("radio") || lower.contains("listen")
        }
    }
}

/**
 * 电台仓储。
 *
 * 语义约定：
 * - 按 `deviceName` 分组展示，但允许通用条目（`deviceName == null`）出现在每台设备下；
 * - 去重键是 `name + url`（同一地址可以有两个名字，视为两条）；
 * - `invalid` 的条目保留，只在排序上靠后。
 */
interface RadioStationStore {
    fun all(): List<RadioStation>
    fun forDevice(deviceName: String?): List<RadioStation>
    fun add(station: RadioStation): Long
    fun remove(id: Long): Boolean
    fun markInvalid(id: Long, invalid: Boolean)
    fun setPlaying(id: Long, playing: Boolean)
    fun clear()
}

/** 纯内存实现，供单测与「尚未接数据库」时使用。 */
class InMemoryRadioStationStore : RadioStationStore {
    private val items = LinkedHashMap<Long, RadioStation>()
    private var nextId = 1L

    override fun all(): List<RadioStation> = items.values.toList()

    override fun forDevice(deviceName: String?): List<RadioStation> =
        items.values
            .filter { deviceName == null || it.deviceName == null || it.deviceName == deviceName }
            .sortedWith(compareBy({ it.invalid }, { !it.kept }, { it.name.lowercase() }))

    override fun add(station: RadioStation): Long {
        val id = nextId++
        items[id] = station.copy(id = id)
        return id
    }

    override fun remove(id: Long): Boolean = items.remove(id) != null

    override fun markInvalid(id: Long, invalid: Boolean) {
        items[id]?.let { items[id] = it.copy(invalid = invalid) }
    }

    override fun setPlaying(id: Long, playing: Boolean) {
        items[id]?.let { items[id] = it.copy(playing = playing) }
    }

    override fun clear() {
        items.clear()
        nextId = 1L
    }
}
