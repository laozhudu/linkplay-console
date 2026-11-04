package io.github.laozhudu.diagnostics

/**
 * 一条诊断记录。
 *
 * ## 为什么存 epoch 毫秒而不是格式化好的字符串
 *
 * 第一版存的是 `"00:09:12 [ConsoleViewModel] IOException: ..."`。三个问题：
 * 1. **跨天看不出**：昨晚 23:59 和今早 00:01 看起来一样；
 * 2. **不能排序**：字符串排序在跨天/跨小时时是错的；
 * 3. **不能改格式**：想按级别着色、想加耗时，只能重新拼字符串。
 *
 * 现在存结构，渲染交给 [DiagnosticReport]。同一个对象可以渲染成
 * "报告里的一行"、"界面上的一个可展开条目"、"落盘的一行"，互不影响。
 *
 * @param atMs 发生时刻（[Clock] 给的 epoch 毫秒）
 * @param source 来源标签，**必须是"哪个动作"**（`playQueueItem` / `probeSaved`），
 *   不是类名 —— 用户报问题时说的是"点播放没反应"，标签要对得上这句话
 * @param severity 严重度，见 [Severity]
 * @param message 一行摘要（会截断到 [maxMessage]）
 * @param detail 可选的多行细节（堆栈）。**不进报告正文**（太长，截图截不完），
 *   只在界面展开时看
 */
data class DiagnosticEntry(
    val atMs: Long,
    val source: String,
    val severity: Severity,
    val message: String,
    val detail: String? = null,
) {
    /** 报告里的一行（不含时间，时间由渲染方加）。 */
    fun headline(maxMessage: Int = 400): String {
        val text = message.replace('\n', ' ').trim()
        val cut = if (text.length > maxMessage) text.take(maxMessage) + "…" else text
        return "[${severity.code}] [$source] $cut"
    }

    companion object {
        /** 从异常构造。`error.message` 为 null 时**不能**渲染成 "null"。 */
        fun ofError(atMs: Long, source: String, error: Throwable): DiagnosticEntry =
            DiagnosticEntry(
                atMs = atMs,
                source = source,
                severity = Severity.ERROR,
                message = error.javaClass.simpleName + (error.message?.let { ": $it" } ?: ""),
                detail = runCatching { error.stackTraceToString() }.getOrNull(),
            )
    }
}
