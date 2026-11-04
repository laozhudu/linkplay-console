package io.github.laozhudu.linkplay.console.nas

import io.github.laozhudu.linkplay.console.diagnostics.SourceFailure
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference

/**
 * NAS / DLNA 内容源的**最近一次状态**，供诊断页渲染。
 *
 * ## 为什么是进程级 object，而不是 ViewModel 的字段
 *
 * `NasViewModel` 跟着页面走：用户退出 NAS 页，实例就没了。
 * 而「我刚才连 NAS 没连上」这件事要在**之后**的诊断报告里还看得到 ——
 * 那正是用户想起来截图的时候。所以状态必须活得比页面长。
 *
 * 项目里已有同类做法：`CommandTrace.DEFAULT`、`QueueBus` 都是进程级 object。
 *
 * ## 为什么渲染方法在这个类里，不在诊断页里
 *
 * 渲染是纯函数，放在这里就能被单测直接喂状态验证排版；
 * 放到 `ConsoleDiagnostics` 里就只能靠真机看出来。
 *
 * ## 为什么记「耗时」
 *
 * 用户说"卡住了"和"失败了"是两种问题。只有条数没有耗时，
 * 我分不出「列了 1931 首花了 12 秒」（慢）和「连不上瞬间返回」（断）。
 */
object NasSourceStatus {

    private val ref = AtomicReference(NasStatus())

    fun snapshot(): NasStatus = ref.get()

    private fun mutate(transform: (NasStatus) -> NasStatus) {
        ref.updateAndGet(transform)
    }

    /** 测试用：把状态清回初始，避免用例互相污染。 */
    fun reset() = ref.set(NasStatus())

    fun scanFound(count: Int, servers: List<String>, ms: Long) = mutate {
        it.copy(
            scanCount = count,
            scanServers = servers.take(4),
            scanMs = ms,
            // 扫到东西就说明组播通路没问题，把上次的失败清掉
            failure = if (count == 0) {
                SourceFailure.EMPTY.describe("SSDP 一个都没扫到；可能是组播被隔离，不等于 NAS 不可达")
            } else {
                null
            },
            failureAt = if (count == 0) System.currentTimeMillis() else it.failureAt,
        )
    }

    fun scanFailed(error: Throwable, ms: Long) = mutate {
        it.copy(
            scanCount = 0,
            scanServers = emptyList(),
            scanMs = ms,
            failure = SourceFailure.of(error).describe(error.message),
            failureAt = System.currentTimeMillis(),
        )
    }

    fun connected(server: String, controlUrl: String, ms: Long) = mutate {
        it.copy(
            server = server,
            controlUrl = controlUrl,
            connectMs = ms,
            connected = true,
            failure = null,
        )
    }

    fun connectFailed(target: String, error: Throwable, ms: Long) = mutate {
        it.copy(
            server = target,
            connectMs = ms,
            connected = false,
            failure = SourceFailure.of(error).describe("${error.message}（目标 $target）"),
            failureAt = System.currentTimeMillis(),
        )
    }

    fun browsed(path: String, entries: Int, total: Int, ms: Long) = mutate {
        it.copy(
            path = path,
            entries = entries,
            total = total,
            browseMs = ms,
            connected = true,
            failure = if (entries == 0) {
                SourceFailure.EMPTY.describe("目录「$path」返回 0 项")
            } else {
                null
            },
            failureAt = if (entries == 0) System.currentTimeMillis() else it.failureAt,
        )
    }

    fun browseFailed(path: String, error: Throwable, ms: Long) = mutate {
        it.copy(
            path = path,
            entries = 0,
            browseMs = ms,
            failure = SourceFailure.of(error).describe("${error.message}（目录 $path）"),
            failureAt = System.currentTimeMillis(),
        )
    }

    /**
     * 三种推流路线里实际走了哪一条。
     *
     * 这是 `docs/60` 要改 NAS 推流路径时**唯一能对比改前改后**的依据：
     * 老代码自己走一套 `ClientSideQueue`，新路线走 `QueueStarter` 的降级链，
     * 没有这条记录就无法证明"改完还是走同一条路"。
     */
    fun route(name: String, detail: String?, ms: Long) = mutate {
        it.copy(route = name, routeDetail = detail, routeMs = ms)
    }

    fun controlLost(lost: Boolean) = mutate { it.copy(controlLost = lost) }

    fun render(): String {
        val s = snapshot()
        return buildString {
            when {
                s.scanCount < 0 -> appendLine("  发现=还没扫过")
                s.scanCount == 0 -> appendLine("  发现=0 个（组播没回来，可能是 NAS 没开 DLNA 或网络隔离）")
                else -> appendLine(
                    "  发现=${s.scanCount} 个 · ${s.scanServers.joinToString("，")}" +
                        if (s.scanMs > 0) " / ${s.scanMs}ms" else "",
                )
            }
            s.server?.let { server ->
                appendLine(
                    "  服务器=$server  状态=" + (if (s.connected) "已连接" else "未连接") +
                        if (s.connectMs > 0) " / ${s.connectMs}ms" else "",
                )
            }
            s.controlUrl?.let { appendLine("  控制面=$it") }
            // 掉线必须显示，哪怕还没连过服务器 —— 它是用户"点了没反应"的直接原因
            if (s.server != null || s.scanCount >= 0 || s.controlLost) {
                appendLine("  控制面状态=" + (if (s.controlLost) "掉线（49152 不通，设备本身在线）" else "正常"))
            }
            s.path?.let { path ->
                appendLine("  最近列目录 $path → ${s.entries} 项（共 ${s.total}）/ ${s.browseMs}ms")
            }
            s.route?.let { appendLine("  最近推流=$it（${s.routeDetail ?: "-"}）/ ${s.routeMs}ms") }
            s.failure?.let { appendLine("  最近失败=${clock(s.failureAt)} $it") }
            if (s.scanCount < 0 && s.server == null && s.route == null) {
                appendLine("  （还没打开过 NAS 页）")
            }
        }
    }

    private fun clock(ms: Long): String =
        if (ms <= 0L) "-" else SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(ms))
}

/** 一次快照。字段都是给人看的，会在诊断页原样出现。 */
data class NasStatus(
    /** -1 = 还没扫过；0 = 扫了但没扫到。 */
    val scanCount: Int = -1,
    val scanServers: List<String> = emptyList(),
    val scanMs: Long = 0L,
    val server: String? = null,
    val controlUrl: String? = null,
    val connectMs: Long = 0L,
    val connected: Boolean = false,
    val path: String? = null,
    val entries: Int = 0,
    val total: Int = 0,
    val browseMs: Long = 0L,
    val route: String? = null,
    val routeDetail: String? = null,
    val routeMs: Long = 0L,
    val controlLost: Boolean = false,
    val failureAt: Long = 0L,
    val failure: String? = null,
)
