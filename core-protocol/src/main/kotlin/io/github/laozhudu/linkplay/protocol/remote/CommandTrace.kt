package io.github.laozhudu.linkplay.protocol.remote

import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicInteger

/**
 * 遥控口令的流水账 —— 最近发出去的命令、结果、耗时。
 *
 * ## 为什么必须记
 *
 * 口令层是**最典型的静默失败区**：
 * - 命令发出去了，设备没照做（`chext` 那种）
 * - 命令根本没发出去（设备睡了、IP 变了）
 * - 命令发对了但回包看不懂
 *
 * 三种情况在界面上**表现完全一样**：用户点了，没反应。
 * 不记录的话只能靠猜；记录下来一眼就能分清。
 *
 * ## 为什么记成功的
 *
 * 排障时最常问的是"这个动作到底跑了没有"。
 * 只记失败的话，「没反应」和「根本没触发」分不开 —— 这两种的修法完全相反
 * （前者查设备，后者查按钮/事件绑定）。
 *
 * ## 为什么是实例而不是全局单例
 *
 * 单例会让测试互相污染，也没法在一个进程里给两套连接各记一份。
 * 默认给一个 [DEFAULT] 供 App 用，但测试可以自己 new。
 */
class CommandTrace(
    val capacity: Int = 40,
) {

    /** 一条流水。 */
    data class Entry(
        val atMs: Long,
        /** 指令的可读形式（`SetVolume(45)`），不是协议文本。 */
        val command: String,
        /** 目标设备 IP。多台设备时才知道发给了谁。 */
        val deviceIp: String,
        val acked: Boolean,
        /** 失败原因（人话）；成功时为 null。 */
        val failureText: String?,
        val elapsedMs: Long,
    )

    private val ring = ConcurrentLinkedDeque<Entry>()
    private val total = AtomicInteger(0)
    private val failed = AtomicInteger(0)

    init {
        require(capacity > 0) { "capacity 必须大于 0" }
    }

    fun record(entry: Entry) {
        runCatching {
            ring.addLast(entry)
            while (ring.size > capacity) ring.pollFirst()
            total.incrementAndGet()
            if (!entry.acked) failed.incrementAndGet()
        }
    }

    /** 最近 [limit] 条，**新→旧**。 */
    fun recent(limit: Int = capacity): List<Entry> =
        ring.toList().asReversed().take(limit)

    val totalCount: Int get() = total.get()
    val failedCount: Int get() = failed.get()

    /** 平均耗时（只算有耗时的，即全部）。没有记录时为 0。 */
    fun averageElapsedMs(): Long {
        val list = ring.toList()
        if (list.isEmpty()) return 0
        return list.sumOf { it.elapsedMs } / list.size
    }

    /** 连续失败几次（从最新往回数）。用于判断"是不是整条链路断了"。 */
    fun consecutiveFailures(): Int {
        var n = 0
        for (e in ring.toList().asReversed()) {
            if (e.acked) break
            n++
        }
        return n
    }

    fun clear() {
        ring.clear()
        total.set(0)
        failed.set(0)
    }

    /**
     * 渲染成诊断报告里的一段。
     *
     * 排版原则（见 docs/52）：用户截图给我之后，我应该**不用再问任何问题**。
     * 所以这一段必须同时给出：总量、成功率、最近几条、失败原因、耗时。
     */
    fun render(): String = buildString {
        if (totalCount == 0) {
            appendLine("  本次启动还没有下发过指令")
            return@buildString
        }
        val success = totalCount - failedCount
        appendLine(
            "  共 $totalCount 条 · 成功 $success · 失败 $failedCount" +
                " · 平均 ${averageElapsedMs()}ms",
        )
        // 连续失败 = 整条链路断了（设备离线/网络变了），和"偶发一条失败"是两回事
        val streak = consecutiveFailures()
        if (streak >= 3) appendLine("  ⚠ 连续失败 $streak 次 —— 可能是设备离线或地址变了")

        val list = recent(6)
        appendLine("  最近 ${list.size} 条（新→旧）：")
        list.forEach { e ->
            val time = formatClock(e.atMs)
            val verdict = if (e.acked) "OK" else "失败：${e.failureText ?: "未知"}"
            appendLine("    $time  ${e.command} @${e.deviceIp} → $verdict  ${e.elapsedMs}ms")
        }
    }

    private fun formatClock(atMs: Long): String {
        val fmt = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US)
        return fmt.format(java.util.Date(atMs))
    }

    companion object {
        /** App 用的那一份。测试请自己 new，避免互相污染。 */
        val DEFAULT = CommandTrace()

        /** 指令的可读形式。**不用 toString()** —— data class 的 toString 带全限定名，太长。 */
        fun labelOf(command: RemoteCommand): String = when (command) {
            RemoteCommand.Play -> "Play"
            RemoteCommand.Pause -> "Pause"
            RemoteCommand.Stop -> "Stop"
            RemoteCommand.Next -> "Next"
            RemoteCommand.Previous -> "Previous"
            is RemoteCommand.Seek -> "Seek(${command.positionSec}s)"
            is RemoteCommand.SetVolume -> "SetVolume(${command.level})"
            RemoteCommand.Mute -> "Mute"
            RemoteCommand.Unmute -> "Unmute"
            is RemoteCommand.SelectInput -> "SelectInput(${command.id})"
            is RemoteCommand.Preset -> "Preset(${command.uiIndex})"
        }
    }
}
