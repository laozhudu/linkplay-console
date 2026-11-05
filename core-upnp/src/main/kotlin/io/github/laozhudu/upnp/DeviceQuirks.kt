package io.github.laozhudu.upnp

/** 一条「设备族知识」：**不是协议规定，是踩出来的**。 */
data class Quirk(
    /** 稳定 id，用于诊断里对比"换了固件之后这条还命中吗"。 */
    val id: String,
    /** 人话说明 —— 要能直接贴给人看。 */
    val note: String,
)

/**
 * 设备怪癖表 —— 把散在各处的"这台设备有毛病"集中到一处。
 *
 * ## 为什么需要它
 *
 * 项目里已经踩过至少五条：
 *
 * | 怪癖 | 表现 | 原来判断写在哪 |
 * |---|---|---|
 * | uuid 两种写法 | 字符串比较永远不相等 | `UuidMatch` |
 * | 暂停时位置是垃圾 | 界面显示"位置 7.8 小时" | 没人判断，直接照抄 |
 * | 老固件不切输入源不播 | 命令回 OK 但没声音 | `RadioViewModel` |
 * | 组网后从机不可直连 | 从机集体"离线" | `docs/66` 的分析 |
 * | HLS 支持不一 | 同一批台 37% 能播 | `core-radio` 的 `playbackHint` |
 *
 * 这些**不是协议知识**，任何通用 UPnP 库都不会告诉你。散在 ViewModel 里的后果是：
 * 换一台设备、换一版固件，就得重新盲试一遍。
 *
 * ## 这张表不重复实现已有判断
 *
 * uuid 归一化仍在 `core-protocol` 的 `UuidMatch`（那里有 613 个测试之一）。
 * 这里只做**登记**：这台设备命中了哪些已知问题，让人一眼看到，而不是重写规则。
 */
object DeviceQuirks {

    /** 超过这个值的位置读数一定是垃圾（真机上见过 28242665ms ≈ 7.8 小时）。 */
    const val MAX_POSITION_MS = 24L * 3600 * 1000

    /** 位置与总时长的容忍抖动（秒级设备回包常见）。 */
    private const val TOLERANCE_MS = 2_000L

    /**
     * 清洗位置读数。**取不到就返回 null，不要返回 0** ——
     * 返回 0 会让进度条看起来"刚从头开始"，比不显示更误导。
     */
    fun sanitizePositionMs(positionMs: Long?, durationMs: Long?): Long? {
        val pos = positionMs ?: return null
        if (pos < 0) return null
        if (pos > MAX_POSITION_MS) return null
        if (durationMs != null && durationMs > 0 && pos > durationMs + TOLERANCE_MS) return null
        return pos
    }

    /**
     * 这台设备命中哪些已知怪癖。
     *
     * 参数全部可空：拿不到固件/型号时**只报通用那几条**，不猜。
     */
    fun of(
        deviceName: String? = null,
        modelName: String? = null,
        firmware: String? = null,
        isLinkPlay: Boolean = false,
    ): List<Quirk> {
        val out = mutableListOf(
            // 所有设备都要防：位置回垃圾值是 UPnP 实现里最常见的偷懒
            Quirk("position-garbage", "位置读数可能是垃圾值，取不到显示「-」而不是 0:00"),
        )
        if (isLinkPlay) {
            out += Quirk("uuid-two-formats", "uuid 有 24 位与 32 位两种写法，必须归一化后比较")
            out += Quirk("wifi-switch", "老固件不先切到 wifi 输入源，命令回 OK 但不会播")
        }
        if (firmware != null && firmware.startsWith("3.6.5310")) {
            out += Quirk("https-radio", "该固件 https 电台命令回 OK 但状态不变，优先用 http")
        }
        if (modelName?.contains("DS-1831", ignoreCase = true) == true || deviceName?.contains("DOSS", ignoreCase = true) == true) {
            out += Quirk("slave-unreachable", "组网后从机落在主机 AP 网段（10.10.10.x），手机连不到")
        }
        return out
    }
}
