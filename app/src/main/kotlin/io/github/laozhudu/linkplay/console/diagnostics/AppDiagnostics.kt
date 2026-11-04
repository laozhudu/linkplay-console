package io.github.laozhudu.linkplay.console.diagnostics

import android.content.Context
import io.github.laozhudu.diagnostics.Clock
import io.github.laozhudu.diagnostics.DiagnosticEntry
import io.github.laozhudu.diagnostics.DiagnosticSink
import io.github.laozhudu.diagnostics.EntryLog
import io.github.laozhudu.diagnostics.FileStore
import io.github.laozhudu.diagnostics.SectionRegistry
import io.github.laozhudu.diagnostics.Severity
import java.io.File

/**
 * 诊断在本 App 的**组装根** —— 把 `core-diagnostics` 的零件接上 Android 的落盘目录。
 *
 * ## 为什么需要这一层
 *
 * 诊断能力本身是通用的（环形日志、状态段注册表、报告排版），已经搬进
 * `core-diagnostics` 模块，纯 JVM、零依赖、可发布 —— 别的项目 `implementation` 一下就能用。
 *
 * 但"存到哪"是各项目自己的事：Android 是 `filesDir`，桌面是任意目录，测试是内存。
 * 所以这一层只做一件事：**给模块的零件选好 Android 的实现**。
 *
 * ## 为什么删掉了原来的 `IssueLog` / `DiagnosticsHub` 两个 object
 *
 * 第一版它们是两个全局单例，各自管一半（一个管条目、一个管段）。
 * 引入模块后如果保留，就会变成"模块里有一套、App 里还有一套"——
 * 同一个能力两个名字、两处语义，正是这个项目反复在治的病（docs/41 那 7 条）。
 *
 * 所以统一成：`AppDiagnostics.log`（条目）+ `AppDiagnostics.sections`（段），
 * 二者在模块里是普通类，可测、可隔离、可替换。
 */
object AppDiagnostics {

    /** 环形容量。200 条够覆盖"刚操作完就出问题"的场景，又不至于把报告撑爆。 */
    const val CAPACITY = 200

    /** 落盘子目录（`filesDir/diagnostics/`）。 */
    private const val DIR = "diagnostics"
    private const val ISSUE_FILE = "issue-log.txt"

    /**
     * 运行期条目。所有协程失败、命令失败、静默降级都进这里。
     *
     * `downstream` 留给未来接 logcat / 上报 —— 现在不接，因为自用、局域网内、不联网上报。
     */
    val log: EntryLog = EntryLog(
        capacity = CAPACITY,
        clock = Clock.SYSTEM,
        downstream = DiagnosticSink.NONE,
    )

    /**
     * 状态段注册表。谁有状态谁注册（设备 / 播放 / 队列 / 后台服务 / 未来任何模块）。
     *
     * 把 [log] 传进去是为了：**段自己渲染崩了要留痕**。
     * 否则报告少一段，用户和我都不知道为什么少。
     */
    val sections: SectionRegistry = SectionRegistry(sink = log)

    fun recent(): List<DiagnosticEntry> = log.recent()

    /**
     * 进程启动时刻。报告头显示"运行时长"，用于对上用户那句"用了两个多小时才出问题"。
     *
     * 为什么不用 `Process.getStartUptimeMillis()`：那是 `SystemClock` 的单调时钟，
     * 与墙钟不同源，跨一次深睡就对不上。这里用墙钟，只在 [markStart] 记一次。
     */
    @Volatile
    private var startedAtMs: Long = 0L

    fun markStart(nowMs: Long = System.currentTimeMillis()) {
        if (startedAtMs == 0L) startedAtMs = nowMs
    }

    /** `2h13m` / `13m05s` / `40s`；没标记启动时间时返回 null（报告里就不显示这一行）。 */
    fun uptimeText(nowMs: Long = System.currentTimeMillis()): String? {
        val start = startedAtMs
        if (start <= 0L || nowMs < start) return null
        val total = (nowMs - start) / 1000
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return when {
            h > 0 -> "${h}h${m.toString().padStart(2, '0')}m"
            m > 0 -> "${m}m${s.toString().padStart(2, '0')}s"
            else -> "${s}s"
        }
    }

    /** 落盘，供下次启动查看。 */
    fun flush(context: Context): Boolean =
        store(context).write(ISSUE_FILE, log.encode())

    /** 上次会话的记录（已解码成结构化条目）。 */
    fun previous(context: Context): List<DiagnosticEntry> {
        val text = store(context).read(ISSUE_FILE) ?: return emptyList()
        val scratch = EntryLog(capacity = CAPACITY, clock = Clock.SYSTEM)
        scratch.decode(text)
        return scratch.recent()
    }

    /** 清空内存与落盘。 */
    fun clear(context: Context) {
        log.clear()
        store(context).delete(ISSUE_FILE)
    }

    private fun store(context: Context) =
        FileStore(File(context.applicationContext.filesDir, DIR))
}
