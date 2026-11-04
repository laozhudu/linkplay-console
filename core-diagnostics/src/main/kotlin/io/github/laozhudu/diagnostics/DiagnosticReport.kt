package io.github.laozhudu.diagnostics

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 诊断报告（**纯函数**，格式可在单测里锁死）。
 *
 * ## 为什么是"报告"而不是"日志列表"
 *
 * 用户手机上看不到 adb，只能截图。截图要能一眼回答四个问题：
 * 1. 崩在哪（异常类 + 消息 + 最上面几行堆栈）
 * 2. 崩的时候应用是什么状态（各模块自报的段）
 * 3. 每台设备/每个连接的取数通道通不通（哪条没答 → 功能为什么没反应）
 * 4. 有没有静默失败（异常被记下来但没崩）
 *
 * 所以输出是一段**定宽纯文本**：手机上能完整滚动、可整段复制、截图不丢信息。
 * 不用富文本卡片：截图时排版越简单越不会漏。
 *
 * ## 为什么报告骨架在库里、内容由接入方给
 *
 * 骨架（头部、崩溃段、条目段、统计）是通用的；"设备/播放/队列"是各项目自己的。
 * 所以这里只定义骨架，正文由调用方渲染好传进来（`body`）。
 * 这样换一个项目接进来，一行报告代码都不用重写。
 */
object DiagnosticReport {

    /** 报告头。 */
    data class Header(
        val appName: String,
        val version: String,
        /** 生成时刻，**由调用方格式化**（各项目的时间精度要求不同）。 */
        val atText: String,
        /** 额外的头部行（如"运行时长：2h13m"、"设备：3 台"）。 */
        val extra: List<String> = emptyList(),
    )

    /** 可覆盖的文案 —— 换语言/换项目时不用改代码。 */
    data class Labels(
        val title: String = "诊断报告",
        val version: String = "版本",
        val time: String = "时间",
        val crash: String = "崩溃记录",
        val noCrash: String = "无（本次启动未捕获到崩溃）",
        val issues: String = "运行期问题",
        val noIssues: String = "无",
        val more: String = "另有",
    )

    data class Options(
        /** 崩溃堆栈只留前几行：截图能截全，且前几行就是真凶。 */
        val maxCrashLines: Int = 16,
        /** 条目只列前几条，其余折叠成一行"另有 N 条"。 */
        val maxEntries: Int = 30,
        val maxMessageChars: Int = 400,
        val labels: Labels = Labels(),
    )

    /**
     * 生成整份报告。
     *
     * @param crashText 上次崩溃的原始记录（含堆栈）；null/空 → 显示"无"
     * @param body 各模块自报的段（已渲染）。空串 → 这一段整体省略
     * @param entries 运行期问题（**新→旧**）
     */
    fun build(
        header: Header,
        crashText: String?,
        body: String,
        entries: List<DiagnosticEntry>,
        options: Options = Options(),
    ): String = buildString {
        val l = options.labels
        appendLine("=== ${header.appName} ${l.title} ===")
        appendLine("${l.version}：${header.version}")
        appendLine("${l.time}：${header.atText}")
        header.extra.forEach { appendLine(it) }
        appendLine()

        // ---- 1. 崩溃 ----
        appendLine("[${l.crash}]")
        if (crashText.isNullOrBlank()) {
            appendLine(l.noCrash)
        } else {
            crashText.lineSequence()
                .filter { it.isNotBlank() }
                .take(options.maxCrashLines)
                .forEach { appendLine("  $it") }
        }
        appendLine()

        // ---- 2. 各模块自报 ----
        if (body.isNotBlank()) {
            append(body.trimEnd())
            appendLine()
            appendLine()
        }

        // ---- 3. 运行期问题 ----
        appendEntries(entries, options)
    }

    private fun StringBuilder.appendEntries(entries: List<DiagnosticEntry>, options: Options) {
        val l = options.labels
        // 级别统计：一眼看出"是硬错误还是噪声"，不用逐行读
        // 固定 E→W→I 顺序（不靠 enum 声明顺序，改枚举不该改报告）；
        // FATAL 只有真崩过才出现，平时不占位置
        val counts = buildString {
            listOf(Severity.ERROR, Severity.WARN, Severity.INFO).forEach { s ->
                append("${s.code}${entries.count { it.severity == s}} ")
            }
            val fatal = entries.count { it.severity == Severity.FATAL }
            if (fatal > 0) append("F$fatal")
        }.trim()
        appendLine("[${l.issues} ${entries.size} 条（新→旧）｜$counts]")
        if (entries.isEmpty()) {
            appendLine("  ${l.noIssues}")
        } else {
            entries.take(options.maxEntries).forEach { e ->
                appendLine("  ${clockText(e.atMs)} ${e.headline(options.maxMessageChars)}")
            }
            if (entries.size > options.maxEntries) {
                appendLine("  … ${l.more} ${entries.size - options.maxEntries} 条")
            }
        }
    }

    /**
     * epoch 毫秒 → `HH:mm:ss`。
     *
     * 为什么用 `SimpleDateFormat` 而不是 `java.time`：这是纯 JVM 库，
     * 但会被 Android 项目（minSdk 24、未开 desugaring）引用，
     * `java.time` 在 API 26 以下不存在。`SimpleDateFormat` 全版本可用。
     * 每次 new 有开销，所以用 ThreadLocal 缓存 —— 报告不是热路径，但没必要浪费。
     */
    private val formatter = ThreadLocal.withInitial { SimpleDateFormat("HH:mm:ss", Locale.US) }

    fun clockText(atMs: Long): String =
        (formatter.get() ?: SimpleDateFormat("HH:mm:ss", Locale.US)).format(Date(atMs))
}
