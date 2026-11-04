package io.github.laozhudu.linkplay.console

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference

/**
 * 洛雪脚本引擎（`core-lx`）的**启动自检**状态。
 *
 * ## 为什么第一件事是做自检，而不是做界面
 *
 * `docs/74` 第六节把"真机还没验过"列为最大缺口：`core-lx` 的单测全部跑在假引擎上，
 * 而真机上要跑 QuickJS，`pako` / `buffer` 这些 polyfill 在 QuickJS 里可能和 Node 不一样。
 *
 * 所以接进来的第一步不是画页面，而是**让 App 自己跑一次并说出结果**：
 *
 * ```
 * [洛雪]
 *   引擎=可用（加载 820ms）
 *   自检=搜「周杰伦」@wy → 5 条 / 1.2s
 * ```
 *
 * 这一步过了再谈界面；不过就说明问题在引擎层，画多少界面都是白画。
 *
 * ## 为什么结果存在这里而不是直接弹 toast
 *
 * 自检发生在启动阶段，用户多半看不到。诊断页是唯一能"事后查看"的地方 ——
 * 而且能截图/进 logcat 给我看。
 */
object LxSourceStatus {

    private val ref = AtomicReference(LxStatus())

    fun snapshot(): LxStatus = ref.get()

    private fun mutate(transform: (LxStatus) -> LxStatus) {
        ref.updateAndGet(transform)
    }

    fun reset() = ref.set(LxStatus())

    fun started() = mutate {
        it.copy(phase = "加载中", ok = null, error = null, atMs = System.currentTimeMillis())
    }

    fun loaded(ms: Long) = mutate { it.copy(phase = "已加载", loadMs = ms) }

    fun searched(source: String, query: String, count: Int, ms: Long) = mutate {
        it.copy(
            phase = "可用",
            source = source,
            query = query,
            count = count,
            searchMs = ms,
            ok = true,
            error = null,
            atMs = System.currentTimeMillis(),
        )
    }

    /** 任何一步失败：引擎加载不出来、bundle 解析不了、搜索抛异常。 */
    fun failed(stage: String, message: String?) = mutate {
        it.copy(
            phase = "不可用",
            ok = false,
            error = if (message.isNullOrBlank()) stage else "$stage：$message",
            atMs = System.currentTimeMillis(),
        )
    }

    fun render(): String {
        val s = snapshot()
        return buildString {
            if (s.phase == null) {
                appendLine("  还没跑过自检（启动时会自动跑一次）")
                return@buildString
            }
            appendLine("  引擎=${s.phase}" + if (s.loadMs > 0) "（加载 ${s.loadMs}ms）" else "")
            if (s.count >= 0) {
                appendLine(
                    "  自检=搜「${s.query}」@${s.source} → ${s.count} 条 / ${s.searchMs}ms" +
                        if (s.count == 0) "  ← 引擎活了但没结果，可能是网络或平台接口变了" else "",
                )
            }
            s.error?.let { appendLine("  自检失败=$it") }
            if (s.atMs > 0L) {
                appendLine(
                    "  上次尝试=" + SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(s.atMs)),
                )
            }
        }
    }
}

/** 一次自检快照。 */
data class LxStatus(
    /** null=没跑过；加载中 / 已加载 / 可用 / 不可用。 */
    val phase: String? = null,
    val loadMs: Long = 0L,
    val source: String = "wy",
    val query: String = "",
    /** -1 = 还没搜过。0 = 搜了但是空（引擎活了，结果为空是另一种问题）。 */
    val count: Int = -1,
    val searchMs: Long = 0L,
    val ok: Boolean? = null,
    val error: String? = null,
    val atMs: Long = 0L,
)
