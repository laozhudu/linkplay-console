package io.github.laozhudu.linkplay.console.diagnostics

/**
 * 诊断报告（**纯函数**，可在单测里锁死格式）。
 *
 * ## 为什么要"报告"而不是"日志列表"
 *
 * 用户手机上看不到 adb，只能截图。截图要能一眼回答四个问题：
 * 1. 崩在哪（异常类 + 消息 + 最上面几行堆栈）
 * 2. 崩的时候 App 是什么状态（设备几台、谁在线、谁在组里）
 * 3. 每台设备的取数通道通不通（哪条通道没答 → 功能为什么没反应）
 * 4. 后台有没有静默失败（协程异常被记下来但没崩）
 *
 * 所以输出是一段**定宽纯文本**：手机上能完整滚动、可整段复制、截图不丢信息。
 * 不用富文本卡片：截图时排版越简单越不会漏。
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
     * @param version 版本号（versionName + versionCode）
     * @param now 时间字符串（由调用方给，便于测试固定）
     * @param crash 上次崩溃记录（原始堆栈文本），没有传 null
     * @param devices 设备快照
     * @param issues 运行期问题（新→旧）
     */
    fun build(
        version: String,
        now: String,
        crash: String?,
        devices: List<DeviceLine>,
        issues: List<String>,
    ): String = buildString {
        appendLine("=== LinkPlay Console 诊断报告 ===")
        appendLine("版本：$version")
        appendLine("时间：$now")
        appendLine()

        // ---- 1. 崩溃 ----
        appendLine("[崩溃记录]")
        if (crash.isNullOrBlank()) {
            appendLine("无（本次启动未捕获到崩溃）")
        } else {
            // 只留前 12 行堆栈：截图能截全，且前几行就是真凶
            crash.lineSequence()
                .filter { it.isNotBlank() }
                .take(16)
                .forEach { appendLine("  $it") }
        }
        appendLine()

        // ---- 2. 设备 ----
        appendLine("[设备 ${devices.size} 台]")
        if (devices.isEmpty()) {
            appendLine("  没有已保存的设备")
        } else {
            devices.forEach { d ->
                appendLine("  ${d.name}  ${d.ip}")
                appendLine(
                    "    状态=${when (d.online) { true -> "在线"; false -> "离线"; null -> "未探测" }}" +
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
                appendLine("    取数通道=${if (d.answered.isEmpty()) "全部未应答" else d.answered.joinToString("+")}")
            }
        }
        appendLine()

        // ---- 3. 运行期问题 ----
        appendLine("[运行期问题 ${issues.size} 条（新→旧）]")
        if (issues.isEmpty()) {
            appendLine("  无")
        } else {
            issues.take(30).forEach { appendLine("  $it") }
            if (issues.size > 30) appendLine("  … 另有 ${issues.size - 30} 条")
        }
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
