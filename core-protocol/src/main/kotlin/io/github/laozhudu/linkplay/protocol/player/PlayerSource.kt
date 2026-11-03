package io.github.laozhudu.linkplay.protocol.player

/**
 * 输入源（设备 `setPlayerCmd:switchmode:<id>` 的取值）。
 *
 * 取值逐字取自原厂 APK 的资源串：`wifi` / `line-in` / `bluetooth` / `udisk` / `optical`。
 * **不要**自己发明名字 —— 命令名不认识时设备只会静默忽略。
 *
 * 与 [PlayerStatus.modeLabel] 的关系：`mode` 是设备回报的**当前状态数字**，
 * 这里是从命令角度看的**可切换目标**。两者对不上时以设备回报为准（那才是真值）。
 */
object PlayerSource {

    data class Source(
        val id: String,
        val label: String,
        val glyph: String,
        /** 界面上是否建议普通用户点：切走 Wi-Fi 后手机可能控制不到设备，需要谨慎。 */
        val risky: Boolean,
    )

    val all: List<Source> = listOf(
        Source("wifi", "网络播放", "🌐", risky = false),
        Source("line-in", "线路输入", "🔌", risky = true),
        Source("bluetooth", "蓝牙", "🔵", risky = true),
        Source("udisk", "U 盘", "💾", risky = true),
        Source("optical", "光纤", "🔆", risky = true),
    )

    fun byId(id: String?): Source? = all.firstOrNull { it.id == id }

    /**
     * 从设备回报的 `mode` 数字猜出**当前**输入源 id。
     *
     * 为什么需要反向映射：界面要能标出「现在在哪一路上」，而不只是列一堆按钮。
     * 数字含义见 [PlayerStatus.modeLabel]（1=AirPlay 2=DLNA 10..19=本地播放列表 40=AUX 41=蓝牙）。
     * 注意 airplay/dlna/local 都跑在 wifi 这一路上，所以都归到 `wifi`。
     */
    fun currentIdOf(mode: Int?): String? = when (mode) {
        null -> null
        // 按官方 mode 表（PlayerStatus.modeLabel 的注释）：
        // 1 AirPlay / 2 DLNA / 10-19 WiiMu 播放列表 / 31 Spotify / 32 TIDAL —— 都跑在 wifi 这一路上
        1, 2, 31, 32 -> "wifi"
        in 10..19 -> if (mode == 11 || mode == 16) "udisk" else "wifi"   // 11=U 盘 16=TF 卡
        40 -> "line-in"
        41 -> "bluetooth"
        42 -> "udisk"      // 外置存储
        43 -> "optical"    // 光纤
        else -> null
    }

    /** 给界面用的一句话描述：`网络播放 · 本地播放列表`。 */
    fun describe(mode: Int?): String {
        val id = currentIdOf(mode)
        val base = byId(id)?.label ?: "未知输入"
        val detail = PlayerStatus.modeLabel(mode)
        return if (base == detail) base else "$base · $detail"
    }
}
