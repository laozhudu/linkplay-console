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
            // 先做长度与空格检查，报错才能给到具体原因（normalizeUrl 只会给一句笼统的"不合法"）
            if (trimmed.length > 2048) return "地址过长"
            if (trimmed.contains(' ')) return "地址里不能有空格（请对空格做 %20 编码）"
            if (normalizeUrl(trimmed) == null) return "只支持 http/https 流地址"
            return null
        }

        /**
         * 把用户粘进来的地址收拾成能用的形状。
         *
         * 真机踩过的坑：
         * 1. 用户常常只粘 `ice1.somafm.com/xxx`（没有协议头）—— 之前的实现直接判非法，
         *    按钮写着「填个有效地址」且点不动，看起来就是「电台无法添加链接」。现在自动补 `http://`。
         * 2. 末尾多余空格、全角空格、从聊天窗口粘来的换行，一律清掉。
         * 3. 兜底补 http:// 之后仍要以 http/https 开头，否则返回 null 由调用方报错。
         */
        fun normalizeUrl(raw: String): String? {
            var u = raw.trim()
                .replace("\u00A0", "")
                .replace("\u3000", "")
                .filterNot { it == '\n' || it == '\r' || it == '\t' }
                .trim()
            if (u.isEmpty()) return null
            // 用户贴了 "ice1.somafm.com:8000/live" 这种，冒号会被当成协议分隔符，先判有没有 ://
            if (!u.contains("://")) {
                u = if (u.startsWith("//")) "http:$u" else "http://$u"
            }
            val lower = u.lowercase()
            if (!lower.startsWith("http://") && !lower.startsWith("https://")) return null
            if (u.length > 2048) return null
            // 地址里真的不能有空格（路径里的空格要 %20）
            if (u.any { it == ' ' }) return null
            return u
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

/**
 * 出厂就带、且**在真机上逐条验证过能出声**的电台。
 *
 * 为什么内置：用户打开「网络电台」看到空白页根本不知道填什么，
 * 而随便从网上抄一条地址又多半放不出来（旧固件 TLS 弱、HLS 支持不稳）。
 * 这几条都是实测 `status=play` 且 `curpos` 递增的，点一下就能播。
 *
 * 合规边界没变：全是公开直连的流地址，不含任何平台接口、凭据或会员内容。
 */
object VerifiedRadioCatalog {

    /** 一条可一键添加的电台。[deviceVerifiedAt] 是实测记录，方便以后复核。 */
    data class Entry(
        val name: String,
        val url: String,
        val note: String,
        val deviceVerifiedAt: String,
    )

    val entries: List<Entry> = listOf(
        Entry(
            name = "Radio Paradise",
            url = "http://stream.radioparadise.com/mp3-192",
            note = "美国，192k MP3，无广告",
            deviceVerifiedAt = "2026-11-02 DS-1831 实测 play 且 curpos 递增",
        ),
        Entry(
            name = "NPR News",
            url = "http://npr-ice.streamguys1.com/live.mp3",
            note = "美国公共广播，新闻谈话",
            deviceVerifiedAt = "2026-11-02 DS-1831 实测 play 且 curpos 递增",
        ),
        Entry(
            name = "FIP",
            url = "http://icecast.radiofrance.fr/fip-hifi.aac",
            note = "法国电台，AAC",
            deviceVerifiedAt = "2026-11-02 DS-1831 实测 play",
        ),
        Entry(
            name = "CNR 中国之声",
            url = "http://ngcdn001.cnr.cn/live/zgzs/index.m3u8",
            note = "中央人民广播电台，HLS",
            deviceVerifiedAt = "2026-11-02 DS-1831 实测 play 且 curpos 递增",
        ),
        Entry(
            name = "CNR 音乐之声",
            url = "http://ngcdn002.cnr.cn/live/yyzs/index.m3u8",
            note = "中央人民广播电台，HLS",
            deviceVerifiedAt = "2026-11-02 DS-1831 实测 play 且 curpos 递增",
        ),
    )

    fun toStation(entry: Entry, deviceName: String?) = RadioStation(
        name = entry.name,
        url = entry.url,
        deviceName = deviceName,
    )
}
