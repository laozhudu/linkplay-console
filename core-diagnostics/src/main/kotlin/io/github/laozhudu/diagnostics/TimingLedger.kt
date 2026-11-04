package io.github.laozhudu.diagnostics

import java.util.concurrent.ConcurrentLinkedDeque

/**
 * 一次**用户动作**的耗时样本。
 *
 * ## 为什么要单独记「指令条数」
 *
 * 光有耗时回答不了"为什么慢"。慢有两种完全不同的病：
 * 1. **一条指令但往返慢** —— 网络/设备问题，改代码没用；
 * 2. **发了三条指令** —— 我们自己在绕路（某个功能被两处各实现一遍，各自发了一条）。
 *
 * 第二种是本项目的老毛病（docs/41 那 7 条）。所以每个样本都带上
 * [commands]，判据很硬：**一次刷新 N 台设备应该正好 N 条指令**，多了就是绕路。
 */
data class ActionSample(
    /** 用户做了什么（`refreshDevices` / `setVolume`），不是类名。 */
    val name: String,
    /** 动作开始时刻（墙钟）。 */
    val atMs: Long,
    val durationMs: Long,
    /** 本次动作发出的指令条数。0 表示这件事不碰设备（读本地、算状态）。 */
    val commands: Int = 0,
    /** 发出的指令名（去重，最多几个），用来看出"这条动作到底碰了什么"。 */
    val commandLabels: List<String> = emptyList(),
    val ok: Boolean = true,
    /** 附加说明（哪台设备、几条结果）。 */
    val note: String? = null,
)

/**
 * 动作耗时账本 —— 回答"跟不跟手、有没有绕路、是不是两处在管同一件事"。
 *
 * ## 为什么不能只靠单条日志里的 "完成 240ms"
 *
 * `EntryLog.timed` 记的是**一次调用**的耗时，散落在 200 条环形日志里。
 * 想回答"刷新设备到底慢不慢"，得自己在几十条日志里挑出来再加一遍 —— 而用户只会截图给我。
 * 所以这里做**按动作名聚合**：同一个名字出现几次、平均多少、最慢多少、平均几条指令。
 *
 * ## 三个判据（都写进报告，不让读的人自己算）
 *
 * 1. **跟手**：< [FAST_MS] 跟手；< [SLOW_MS] 偏慢；>= [SLOW_MS] 慢。
 *    阈值取自人机交互的经验值（100ms 内算即时反馈，1s 内算注意力不断），**不是硬标准**，
 *    但比自己感觉靠谱 —— 项目已经因为"我觉得还行"错过两次真问题。
 * 2. **绕路**：同一次动作的 [ActionSample.commands] 明显多于"理论最少条数"。
 *    理论值由调用方给出（[detourOf]），账本只负责对比。
 * 3. **两处在管同一件事**：同一个动作名在 [repeatWindowMs] 内被触发多次。
 *    用户点一下只会有一次；出现两三次基本就是两个地方都在刷（界面 A 和心跳 B），
 *    或者点击事件绑了两遍 —— 这正是"同一能力多套入口"的现场证据。
 *
 * ## 为什么不记后台心跳
 *
 * 心跳每 3 秒一次，记进来会把所有平均值拉平，"跟手"就没意义了。
 * 账本只登记**用户主动触发的动作**。心跳次数由各自的诊断段去记。
 */
class TimingLedger(
    val capacity: Int = 60,
    /** 同一动作在这个窗口内重复触发，视为可疑（毫秒）。 */
    val repeatWindowMs: Long = 2_000L,
) {

    private val ring = ConcurrentLinkedDeque<ActionSample>()

    init {
        require(capacity > 0) { "capacity 必须大于 0" }
    }

    /** 记一条。**绝不抛** —— 它会在业务完成后的 finally 里被调用。 */
    fun record(sample: ActionSample) {
        runCatching {
            ring.addLast(sample)
            while (ring.size > capacity) ring.pollFirst()
        }
    }

    /** 最近 [limit] 条，**新→旧**。 */
    fun recent(limit: Int = Int.MAX_VALUE): List<ActionSample> =
        ring.toList().asReversed().take(limit)

    fun clear() = ring.clear()

    val size: Int get() = ring.size

    /** 按动作名聚合后的统计。 */
    data class Summary(
        val name: String,
        val count: Int,
        val avgMs: Long,
        val maxMs: Long,
        val minMs: Long,
        /** 平均每次发出几条指令。 */
        val avgCommands: Double,
        /** 失败次数。 */
        val failures: Int,
    )

    /** 全部动作的聚合，**按平均耗时降序**（慢的排前面，报告里第一眼就看到）。 */
    fun summaries(): List<Summary> =
        ring.groupBy { it.name }
            .map { (name, list) ->
                val durations = list.map { it.durationMs }
                Summary(
                    name = name,
                    count = list.size,
                    avgMs = durations.average().toLong(),
                    maxMs = durations.max(),
                    minMs = durations.min(),
                    avgCommands = list.map { it.commands }.average(),
                    failures = list.count { !it.ok },
                )
            }
            .sortedWith(compareByDescending<Summary> { it.avgMs }.thenByDescending { it.count })

    /** 最慢 [n] 个动作。 */
    fun slowest(n: Int = 5): List<Summary> = summaries().take(n)

    /** 一次重复触发。 */
    data class Repeat(val name: String, val count: Int, val spanMs: Long)

    /**
     * 找出"同一动作在 [repeatWindowMs] 内被连点/双触发"的情况。
     *
     * 判据是**同一个名字**，不问是不是同一台设备 —— 因为重复触发通常来自
     * 两处代码各刷一次，它们触发时带的上下文本来就不一样。
     */
    fun repeats(): List<Repeat> {
        val all = ring.toList()
        val out = ArrayList<Repeat>()
        all.groupBy { it.name }.forEach { (name, list) ->
            val times = list.map { it.atMs }.sorted()
            var start = 0
            var i = 0
            while (i < times.size) {
                if (times[i] - times[start] <= repeatWindowMs) {
                    i++
                } else {
                    if (i - start > 1) {
                        out += Repeat(name, i - start, times[i - 1] - times[start])
                    }
                    start = i
                    i++
                }
            }
            if (times.isNotEmpty() && times.size - start > 1) {
                out += Repeat(name, times.size - start, times[times.size - 1] - times[start])
            }
        }
        return out.sortedWith(compareByDescending<Repeat> { it.count })
    }

    /**
     * 核对最短路径：一次动作实际发了几条指令，vs 应该发几条。
     *
     * [expectCommands] 传理论最少条数（例如"在线 N 台设备，每台一条"）。
     * 实际更多就说明有绕路 —— 报告里直接写多出几条，而不是让人自己比。
     */
    fun detourOf(name: String, expectCommands: Int): Detour? {
        val list = ring.toList().filter { it.name == name }
        if (list.isEmpty()) return null
        val avg = list.map { it.commands }.average()
        if (avg <= expectCommands.toDouble()) return null
        return Detour(
            name = name,
            expected = expectCommands,
            actualAvg = avg,
            worst = list.maxOf { it.commands },
        )
    }

    data class Detour(val name: String, val expected: Int, val actualAvg: Double, val worst: Int)

    /** 人话评价。 */
    fun verdictOf(ms: Long): String = when {
        ms < FAST_MS -> "跟手"
        ms < SLOW_MS -> "偏慢"
        else -> "慢"
    }

    /**
     * 渲染成诊断文本。空账本返回一句"还没触发过"，而不是空字符串 ——
     * 报告里少一段和"这段是空的"必须能区分（docs/61 的老账）。
     */
    fun render(
        limit: Int = 6,
        slowestLimit: Int = 4,
        /** 已知的最短路径期望值：动作名 → 理论最少指令条数。 */
        expectations: Map<String, Int> = emptyMap(),
    ): String {
        if (ring.isEmpty()) return "还没触发过动作（点一下界面就会出现）"
        return buildString {
            val recent = recent(limit)
            appendLine("最近 ${recent.size} 次（新→旧）")
            recent.forEach { s ->
                val cmd = if (s.commands > 0) " ${s.commands} 条指令" else " 不碰设备"
                val labels = if (s.commandLabels.isEmpty()) "" else "（${s.commandLabels.joinToString("/")}）"
                val flag = if (s.ok) "" else "  ← 失败"
                appendLine("  ${clock(s.atMs)} ${s.name} ${s.durationMs}ms$cmd$labels ${verdictOf(s.durationMs)}$flag")
                s.note?.let { appendLine("      $it") }
            }

            val slow = slowest(slowestLimit)
            if (slow.isNotEmpty()) {
                appendLine("按平均排（慢的在前）")
                slow.forEach { s ->
                    appendLine(
                        "  ${s.name} 平均 ${s.avgMs}ms / 最慢 ${s.maxMs}ms / ${s.count} 次" +
                            " · ${oneDecimal(s.avgCommands)} 条指令" +
                            if (s.failures > 0) " · 失败 ${s.failures}" else "",
                    )
                }
            }

            val repeats = repeats()
            if (repeats.isNotEmpty()) {
                appendLine("重复触发（${repeatWindowMs}ms 内同一动作多次）")
                repeats.forEach { appendLine("  ${it.name} ×${it.count} / ${it.spanMs}ms  ← 可能有两处在刷同一件事") }
            }

            val detours = expectations.mapNotNull { (name, expect) -> detourOf(name, expect) }
            if (detours.isNotEmpty()) {
                appendLine("绕路（实际指令数多于最短路径）")
                detours.forEach {
                    appendLine("  ${it.name} 期望 ${it.expected} 条 / 实际平均 ${oneDecimal(it.actualAvg)} / 最多 ${it.worst}")
                }
            }
        }.trimEnd()
    }

    private fun oneDecimal(v: Double): String = String.format(java.util.Locale.US, "%.1f", v)

    private fun clock(atMs: Long): String =
        java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date(atMs))

    companion object {
        /** 100ms 内算即时反馈（人机交互经验值）。 */
        const val FAST_MS = 100L

        /** 超过 1s 人就会"觉得卡"（同上）。 */
        const val SLOW_MS = 1_000L
    }
}
