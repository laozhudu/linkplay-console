package io.github.laozhudu.linkplay.console

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference

/**
 * 「组」的最近状态，供诊断页渲染。
 *
 * ## 为什么组也要一段诊断
 *
 * 「加了组却退不出来」是用户报得最具体、也最难查的一类问题，因为它有两层：
 *
 * 1. **设备端**：从机确实在主机 `slave_list` 里（声音真的在同步）
 * 2. **界面端**：我们不认为它在组里 → 于是**连"退出同步"的按钮都不显示**
 *
 * 两层一旦错位，用户在界面上就没有任何操作路径 —— 看起来就是"进得去出不来"。
 *
 * 所以这一段要能回答：我们**最后一次读到拓扑是什么时候、读到几台、有没有对不上的设备**。
 * 只有这个能区分"设备没退"和"我们以为它没在组里"。
 *
 * ## 与「设备」段的分工
 *
 * 「设备」段说每台在线/音量/电量；这一段只说**组**：谁在组里、拓扑什么时候读的、
 * 有没有读不到或者匹配不上的成员。
 */
object GroupSourceStatus {

    private val ref = AtomicReference(GroupStatus())

    fun snapshot(): GroupStatus = ref.get()

    private fun mutate(transform: (GroupStatus) -> GroupStatus) {
        ref.updateAndGet(transform)
    }

    fun reset() = ref.set(GroupStatus())

    /**
     * 读了一次拓扑。
     *
     * [slaves] 是主机自报的从机数；[unmatched] 是其中**在本地设备记录里找不到对应**的数量 ——
     * 后者大于 0 时，界面上会出现"设备在组里但卡片上没有归属"的现象。
     */
    fun topologyRead(masterName: String?, slaves: Int, unmatched: Int, ms: Long) = mutate {
        it.copy(
            reads = it.reads + 1,
            lastMaster = masterName,
            lastSlaves = slaves,
            lastUnmatched = unmatched,
            lastMs = ms,
            lastReadAt = System.currentTimeMillis(),
            // 读到非空拓扑 = 组还在，把"已解散"的时刻清掉
            clearedAt = if (slaves > 0) 0L else it.clearedAt,
        )
    }

    /** 所有在线设备都读成功且都是空 —— 组确实解散了。 */
    fun topologyEmpty(ms: Long) = mutate {
        it.copy(
            reads = it.reads + 1,
            lastMaster = null,
            lastSlaves = 0,
            lastUnmatched = 0,
            lastMs = ms,
            lastReadAt = System.currentTimeMillis(),
            clearedAt = if (it.lastSlaves > 0) System.currentTimeMillis() else it.clearedAt,
        )
    }

    /** 读失败（网络/主机不可达）：**不算"组没了"**，只是这一轮没读到。 */
    fun topologyFailed(reason: String) = mutate {
        it.copy(lastFailure = reason, lastReadAt = System.currentTimeMillis())
    }

    /** 退组命令 + 确认结果，`confirmed` 为 null 表示还在等。 */
    fun ungroup(action: String, confirmed: Boolean?) = mutate {
        it.copy(
            lastAction = action,
            actionAt = System.currentTimeMillis(),
            actionConfirmed = confirmed,
        )
    }

    fun render(): String {
        val s = snapshot()
        return buildString {
            if (s.lastReadAt <= 0L && s.lastAction == null) {
                appendLine("  还没读过组拓扑")
                return@buildString
            }
            appendLine("  拓扑读取=${s.reads} 次  最后一次=${clock(s.lastReadAt)} / ${s.lastMs}ms")
            if (s.lastSlaves > 0) {
                appendLine("  主机=${s.lastMaster ?: "未知"}  从机=${s.lastSlaves} 台")
                if (s.lastUnmatched > 0) {
                    appendLine(
                        "  ⚠ ${s.lastUnmatched} 台从机在本地设备记录里找不到对应（uuid 对不上）—— " +
                            "它们会显示成「不在组里」，于是没有「退出同步」入口",
                    )
                }
            } else {
                appendLine("  当前无组" + if (s.clearedAt > 0L) "（${clock(s.clearedAt)} 确认解散）" else "")
            }
            s.lastAction?.let {
                appendLine(
                    "  最近退组=$it / ${clock(s.actionAt)} → " + when (s.actionConfirmed) {
                        true -> "已确认从列表消失"
                        false -> "**命令被接受但设备仍在组里**"
                        null -> "等待确认"
                    },
                )
            }
            s.lastFailure?.let { appendLine("  最近读取失败=$it") }
        }
    }

    private fun clock(ms: Long): String =
        if (ms <= 0L) "-" else SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(ms))
}

/** 一次快照。 */
data class GroupStatus(
    val reads: Int = 0,
    val lastMaster: String? = null,
    /** -1 = 还没读到过；0 = 读过且确实没有组。 */
    val lastSlaves: Int = -1,
    val lastUnmatched: Int = 0,
    val lastMs: Long = 0L,
    val lastReadAt: Long = 0L,
    /** 上次"确认解散"的时刻。 */
    val clearedAt: Long = 0L,
    val lastFailure: String? = null,
    val lastAction: String? = null,
    val actionAt: Long = 0L,
    /** null = 还在等；false = 命令被接受但设备仍在组里（回 OK ≠ 生效）。 */
    val actionConfirmed: Boolean? = null,
)
