package io.github.laozhudu.linkplay.console.diagnostics

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedDeque

/**
 * 运行期问题记录 —— 让"没崩但也没成功"的事有迹可循。
 *
 * ## 为什么要它
 *
 * 这个项目反复出现的两类问题都不是崩溃，而是**静默失败**：
 * 命令回了 OK 但没生效、某个轮询抛异常被 runCatching 吞掉、设备一会儿能连一会儿不能。
 * 用户看到的是"功能时好时坏"，我们手上什么都没有，只能猜。
 *
 * 所以把每一次失败都记下来（异常类 + 消息 + 来源标签），
 * 在「设置 → 诊断记录」里可以看、可以复制。**排障从"猜"变成"读"**。
 *
 * 只保留最近 [MAX] 条在内存，同时落一份文件供下次启动查看；
 * 单条截断到 [MAX_CHARS]，防止某个异常把内存撑爆。
 */
object IssueLog {

    private const val MAX = 200
    private const val MAX_CHARS = 400
    private const val FILE = "issue-log.txt"

    private val ring = ConcurrentLinkedDeque<String>()

    /** 记一条。**绝不抛异常**（它在异常处理路径上被调用）。 */
    fun record(source: String, error: Throwable) {
        runCatching {
            val line = "${stamp()} [$source] ${error.javaClass.simpleName}: " +
                (error.message ?: "").replace('\n', ' ').take(MAX_CHARS)
            ring.addLast(line)
            while (ring.size > MAX) ring.pollFirst()
        }
    }

    /** 记一条普通信息（用于"命令回了 OK 但没生效"这类）。 */
    fun note(source: String, message: String) {
        runCatching {
            ring.addLast("${stamp()} [$source] $message".take(MAX_CHARS * 2))
            while (ring.size > MAX) ring.pollFirst()
        }
    }

    /** 当前会话的记录（新→旧）。 */
    fun recent(): List<String> = ring.toList().asReversed()

    /** 落盘，下次启动还能看。 */
    fun flush(context: Context) {
        runCatching {
            File(context.filesDir, FILE).writeText(ring.joinToString("\n"))
        }
    }

    /** 上次会话的记录。 */
    fun previous(context: Context): List<String> =
        runCatching { File(context.filesDir, FILE).readLines() }.getOrDefault(emptyList())

    fun clear(context: Context) {
        ring.clear()
        runCatching { File(context.filesDir, FILE).delete() }
    }

    private fun stamp(): String = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
}
