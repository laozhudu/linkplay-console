package io.github.laozhudu.linkplay.console.radio

import io.github.laozhudu.linkplay.console.diagnostics.SourceFailure
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference

/**
 * 电台的最近状态，供诊断页渲染。
 *
 * 形状照抄 `nas/NasSourceStatus.kt`（`docs/65` 批次 1 模板）。
 *
 * ## 这个源为什么难，以及报告要写什么
 *
 * 「电台加了不响」有四种原因，而**命令全都回 OK**：
 *
 * | 真因 | 报了什么 | 修法 |
 * |---|---|---|
 * | 地址是 `https` | `setPlayerCmd:play:<url>` 回 OK，但 status 永远不是 PLAY | 换 http 源 |
 * | 地址失效 | 同上 | 换地址 |
 * | 设备到不了该站 | 同上 | 换网络/换源 |
 * | 地址不合法 | 解析阶段就拒 | 补协议头 |
 *
 * 真机实测（`docs/42`）：`.164` 上 `http://` 能播、`https://` 命令回 OK 但没播。
 * 这是这个项目「命令回 OK ≠ 功能生效」四次踩坑里最典型的一次。
 *
 * 所以报告里 **scheme 必须单独写出来**，并直接给出结论「音箱不认 https」——
 * 不能让看报告的人自己从 URL 前面那五个字母推断。
 */
object RadioSourceStatus {

    /** 6 秒内没到 PLAY 就算超时（与 `playUrl` 的轮询次数一致）。 */
    const val WATCH_MS = 6_000L

    private val ref = AtomicReference(RadioStatus())

    fun snapshot(): RadioStatus = ref.get()

    private fun mutate(transform: (RadioStatus) -> RadioStatus) {
        ref.updateAndGet(transform)
    }

    fun reset() = ref.set(RadioStatus())

    fun library(count: Int) = mutate { it.copy(libraryCount = count) }

    /** 地址没通过规范化（通常是没写 http:// 且不像链接）。 */
    fun invalid(raw: String) = mutate {
        it.copy(
            lastUrl = raw,
            lastScheme = null,
            lastOk = false,
            lastDetail = null,
            failure = SourceFailure.PARSE_FAILED.describe("地址不合法：$raw"),
            failureAt = System.currentTimeMillis(),
        )
    }

    fun played(url: String, detail: String?, ms: Long) = mutate {
        it.copy(
            lastUrl = url,
            lastScheme = schemeOf(url),
            lastOk = true,
            lastDetail = detail,
            lastMs = ms,
            failure = null,
        )
    }

    /**
     * 没播起来。 [kind] 由调用方判定（`UNSUPPORTED` / `TIMEOUT` / `UNREACHABLE`）。
     *
     * 为什么不在这个函数里判：它拿不到设备状态和历史，只会瞎猜。
     * 判定放在真正轮询到 status 的地方，这里只负责写报告。
     */
    fun failed(url: String, kind: SourceFailure, detail: String?, ms: Long) = mutate {
        val https = schemeOf(url) == "https"
        // https 是最确定的结论：真机上它 100% 表现为"回 OK 但 status 不动"
        val classified = if (https && kind == SourceFailure.UNSUPPORTED) {
            SourceFailure.UNSUPPORTED.describe("音箱不认 https（命令回了 OK 但 status=${detail ?: "不是 PLAY"}），换 http 源")
        } else {
            kind.describe(detail)
        }
        it.copy(
            lastUrl = url,
            lastScheme = schemeOf(url),
            lastOk = false,
            lastDetail = detail,
            lastMs = ms,
            failure = classified,
            failureAt = System.currentTimeMillis(),
        )
    }

    fun render(): String {
        val s = snapshot()
        return buildString {
            appendLine(
                if (s.libraryCount < 0) "  库内=还没读过" else "  库内=${s.libraryCount} 台",
            )
            when {
                s.lastUrl == null -> appendLine("  最近播放=还没试过")
                s.lastOk == true -> {
                    appendLine("  最近播放=${s.lastUrl}（scheme=${s.lastScheme ?: "?"}）")
                    appendLine("  结果=成功  ${s.lastDetail ?: "-"} / ${seconds(s.lastMs)}")
                }
                else -> {
                    appendLine("  最近失败=${s.lastUrl}（scheme=${s.lastScheme ?: "?"}）")
                    s.failure?.let { appendLine("    原因=$it / ${seconds(s.lastMs)}") }
                }
            }
        }
    }

    private fun schemeOf(url: String): String? =
        when {
            url.startsWith("https://", ignoreCase = true) -> "https"
            url.startsWith("http://", ignoreCase = true) -> "http"
            else -> null
        }

    private fun seconds(ms: Long): String = when {
        ms <= 0L -> "-"
        ms < 1000 -> "${ms}ms"
        else -> String.format(Locale.US, "%.1fs", ms / 1000.0)
    }
}

/** 一次快照。字段都是给人看的，会在诊断页原样出现。 */
data class RadioStatus(
    /** -1 = 还没读过库。 */
    val libraryCount: Int = -1,
    val lastUrl: String? = null,
    val lastScheme: String? = null,
    /** null = 还没试过播。 */
    val lastOk: Boolean? = null,
    val lastDetail: String? = null,
    val lastMs: Long = 0L,
    val failureAt: Long = 0L,
    val failure: String? = null,
)

/**
 * `playUrl` 的判定结果：成功与否 + 给报告看的说明 + 失败分类。
 *
 * 放在这个文件而不是 `RadioViewModel.kt` 末尾：`scripts/check-init-order.py`
 * 会把任何 4 空格缩进的 `val` 当成类的成员，末尾的顶层 data class 会被误判成
 * "init 之后还有属性"。挪到这里既避开误判，也让判定结果和它服务的报告放在一起。
 */
internal data class RadioOutcome(
    val ok: Boolean,
    val detail: String?,
    val kind: SourceFailure?,
)
