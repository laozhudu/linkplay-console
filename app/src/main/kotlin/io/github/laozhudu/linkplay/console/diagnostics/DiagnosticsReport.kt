package io.github.laozhudu.linkplay.console.diagnostics

import io.github.laozhudu.diagnostics.DiagnosticEntry
import io.github.laozhudu.diagnostics.DiagnosticReport as CoreReport

/**
 * 本 App 的诊断报告 —— **只负责组装**，排版交给 `core-diagnostics` 的 [CoreReport]。
 *
 * ## 为什么还要这一层
 *
 * 报告骨架（头部 / 崩溃段 / 条目段 / 级别统计）是通用的，已经搬进模块。
 * 但"一台设备该显示哪些字段"是这个项目独有的知识：
 * 电量、声道、取数通道、能力档 —— 换一个项目就没有这些概念。
 *
 * 所以这里只留两样东西：
 * 1. [DeviceLine]（设备一行的字段）与 [channelText]（声道数字 → 人话）；
 * 2. [build]：把 App 的参数翻译成模块的输入。
 *
 * 这样模块能整份复用，而项目语义留在项目里。
 */
object DiagnosticsReport {

    /** 一台设备在报告里的那一行所需的信息。 */
    data class DeviceLine(
        val name: String,
        val ip: String,
        /** true 在线 / false 离线 / null 未探测 */
        val online: Boolean?,
        /** 属于哪台主机（组内从机才有）。 */
        val groupedUnder: String?,
        val battery: Int?,
        val charging: Boolean,
        /** 0 立体声 / 1 仅左 / 2 仅右。 */
        val channel: Int?,
        val volume: Int,
        val playing: Boolean,
        /** 这一轮哪几条取数通道真的答了。 */
        val answered: Set<String>,
        /** 能力档名（LINKPLAY / GENERIC_DMR / UNKNOWN）。 */
        val profile: String,
    )

    /**
     * 生成整份报告。
     *
     * @param entries 运行期问题（新→旧，结构化）
     * @param sections 各模块自报的状态（[AppDiagnostics.sections] 的渲染结果）。
     *   给了它就**不再渲染 [devices] 段** —— 注册表里已经有一段「设备」，
     *   两边都画就是同一份信息出现两次，截图时白白占掉半屏。
     */
    fun build(
        version: String,
        now: String,
        crash: String?,
        devices: List<DeviceLine>,
        entries: List<DiagnosticEntry>,
        sections: String? = null,
        /** 运行时长（`2h13m`）。null → 不显示这一行。 */
        uptime: String? = null,
    ): String = CoreReport.build(
        header = CoreReport.Header(
            appName = "声控台",
            version = version,
            atText = now,
            // 头部多一行级别汇总：不用往下翻就知道"这次有没有硬错误"
            extra = buildList {
                add("问题汇总：${severitySummary(entries)}")
                if (uptime != null) add("运行时长：$uptime")
            },
        ),
        crashText = crash,
        body = sections ?: deviceSection(devices),
        entries = entries,
    )

    /** `E1 W2 I5` 形式的一行统计（FATAL 只有真崩过才出现）。 */
    fun severitySummary(entries: List<DiagnosticEntry>): String {
        val e = entries.count { it.severity == io.github.laozhudu.diagnostics.Severity.ERROR }
        val w = entries.count { it.severity == io.github.laozhudu.diagnostics.Severity.WARN }
        val i = entries.count { it.severity == io.github.laozhudu.diagnostics.Severity.INFO }
        val f = entries.count { it.severity == io.github.laozhudu.diagnostics.Severity.FATAL }
        return "E$e W$w I$i" + if (f > 0) " F$f" else ""
    }

    /** 老路径：只画设备表（没有注册表的调用方用）。 */
    private fun deviceSection(devices: List<DeviceLine>): String = buildString {
        appendLine("[设备 ${devices.size} 台]")
        if (devices.isEmpty()) {
            appendLine("  没有已保存的设备")
        } else {
            devices.forEach { d ->
                appendLine("  ${d.name}  ${d.ip}")
                appendLine(
                    "    状态=" + when (d.online) {
                        true -> "在线"; false -> "离线"; null -> "未探测"
                    } +
                        (d.groupedUnder?.let { "  属=$it" } ?: "") +
                        "  能力=${d.profile}",
                )
                appendLine(
                    "    音量=${d.volume}  声道=${channelText(d.channel)}" +
                        "  电量=${d.battery?.let { "$it%" } ?: "无"}" +
                        (if (d.battery != null && d.charging) "（充电）" else "") +
                        "  播放=${if (d.playing) "是" else "否"}",
                )
                // 通道是排障关键：哪条没答，对应的功能就一定是"没反应"
                appendLine(
                    "    取数通道=" +
                        (if (d.answered.isEmpty()) "全部未应答" else d.answered.sorted().joinToString("+")),
                )
            }
        }
        appendLine()
    }

    /** 声道数字 → 人话。null 表示设备没报（不是"立体声"）。 */
    fun channelText(channel: Int?): String = when (channel) {
        null -> "未报"
        0 -> "立体声"
        1 -> "仅左"
        2 -> "仅右"
        else -> "未知($channel)"
    }
}
