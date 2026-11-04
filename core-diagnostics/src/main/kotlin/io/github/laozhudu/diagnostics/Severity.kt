package io.github.laozhudu.diagnostics

/**
 * 诊断条目的严重度。
 *
 * ## 为什么要分级（而不是一条条纯文本）
 *
 * 第一版的问题记录是 `List<String>`：能看，但**不能统计、不能过滤、不能排序**。
 * 用户说"老是时好时坏"，我们手上 200 条文本，得逐行读才知道到底几类问题。
 *
 * 分级之后能做三件事：
 * 1. 报告头给出 `E3 W12 I45`，一眼看出"是硬错误还是噪声"；
 * 2. 界面按级别过滤，用户只看错误；
 * 3. 段的级别可以汇总（某段自报 WARN → 报告头显示），
 *    不用读完就知道"哪一块在喊"。
 *
 * ## 为什么只有四档
 *
 * 每多一档就要在"这条算 WARN 还是 ERROR"上纠结一次。四档的判据是**要不要用户动手**：
 * - [INFO] 正常事件（连上了、扫到 3 台）—— 用户不需要做任何事
 * - [WARN] 降级但还能用（走了 httpapi 兜底、设备没报电量）—— 用户可能想知道
 * - [ERROR] 一个动作失败了（播放失败、命令 500）—— 用户必须知道
 * - [FATAL] 进程级故障（未捕获异常）—— 用户只能重启
 */
enum class Severity(val code: String, val label: String) {
    INFO("I", "信息"),
    WARN("W", "警告"),
    ERROR("E", "错误"),
    FATAL("F", "致命"),
    ;

    /** 数值越大越严重，用于"取最严重的那条"。 */
    val weight: Int get() = ordinal

    companion object {
        /** 取一组里最严重的；空集合返回 [INFO]。 */
        fun worst(items: Iterable<Severity>): Severity =
            items.maxByOrNull { it.weight } ?: INFO

        /** 从短码反查（解析落盘日志时用）。 */
        fun ofCode(code: String): Severity =
            entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: INFO
    }
}
