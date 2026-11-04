package io.github.laozhudu.diagnostics

import java.util.concurrent.ConcurrentLinkedDeque

/**
 * 环形诊断日志 —— 内存里只留最近 [capacity] 条。
 *
 * ## 为什么是环形而不是全留
 *
 * App 跑一整天，失败记录可能上千条。全留会：
 * 1. 撑内存（每条带堆栈，几百字节到几 KB）；
 * 2. 让报告变长，用户截图截不完。
 *
 * 而排障只需要**最近发生的那几条** —— 用户是"刚操作完就出问题"才来报的。
 *
 * ## 为什么是实例而不是 object
 *
 * 第一版是 `object IssueLog`：全局单例。
 * 后果是**单测之间互相污染**（一个用例记的条目出现在另一个用例的报告里），
 * 只能靠 `clear()` 在 `@Before` 里打补丁；而且同一个进程里想给两个模块各留一份日志做不到。
 * 现在它是普通类，谁需要谁 new。
 */
class EntryLog(
    val capacity: Int = 200,
    private val clock: Clock = Clock.SYSTEM,
    private val maxMessage: Int = 400,
    /** 额外出口（落盘、logcat）。默认只留内存。 */
    private val downstream: DiagnosticSink = DiagnosticSink.NONE,
) : DiagnosticSink {

    private val ring = ConcurrentLinkedDeque<DiagnosticEntry>()

    init {
        require(capacity > 0) { "capacity 必须大于 0" }
    }

    override fun record(entry: DiagnosticEntry) {
        // 绝不抛：这个函数在异常处理路径上被调用
        runCatching {
            ring.addLast(entry)
            while (ring.size > capacity) ring.pollFirst()
            downstream.record(entry)
        }
    }

    fun error(source: String, error: Throwable) =
        record(DiagnosticEntry.ofError(clock.nowMs(), source, error))

    fun warn(source: String, message: String) =
        record(DiagnosticEntry(clock.nowMs(), source, Severity.WARN, message))

    fun info(source: String, message: String) =
        record(DiagnosticEntry(clock.nowMs(), source, Severity.INFO, message))

    /**
     * 包住一次可能失败的动作，**成功也记**（INFO，带耗时）。
     *
     * 为什么成功也要记：排障时最常问的是"这个动作到底跑了没有"。
     * 只有失败才记的话，"没反应"和"根本没触发"分不开 —— 这两种的修法完全不同。
     * 耗时可用来判断"慢"还是"断"。
     */
    fun <T> timed(source: String, block: () -> T): T {
        val started = clock.nowMs()
        return try {
            val value = block()
            info(source, "完成 ${clock.nowMs() - started}ms")
            value
        } catch (error: Throwable) {
            record(DiagnosticEntry.ofError(started, source, error))
            throw error
        }
    }

    /** 最近 [limit] 条，**新→旧**（报告和界面都按这个顺序）。 */
    fun recent(limit: Int = Int.MAX_VALUE): List<DiagnosticEntry> =
        ring.toList().asReversed().take(limit)

    fun counts(): Map<Severity, Int> {
        val map = Severity.entries.associateWith { 0 }.toMutableMap()
        ring.forEach { map[it.severity] = (map[it.severity] ?: 0) + 1 }
        return map
    }

    fun worst(): Severity = Severity.worst(ring.map { it.severity })

    val size: Int get() = ring.size

    fun clear() = ring.clear()

    /** 落盘格式：一行一条，`epochMs|SEV|source|message`。**解析回去必须无损**。 */
    fun encode(): String = ring.joinToString("\n") { e ->
        "${e.atMs}|${e.severity.code}|${e.source}|${e.message.replace('\n', ' ')}"
    }

    /** 读回落盘内容。格式不对的行**跳过**，不抛。 */
    fun decode(text: String) {
        runCatching {
            text.lineSequence().forEach { line ->
                val parts = line.split('|', limit = 4)
                if (parts.size == 4) {
                    parts[0].toLongOrNull()?.let { ms ->
                        record(DiagnosticEntry(ms, parts[2], Severity.ofCode(parts[1]), parts[3]))
                    }
                }
            }
        }
    }
}
