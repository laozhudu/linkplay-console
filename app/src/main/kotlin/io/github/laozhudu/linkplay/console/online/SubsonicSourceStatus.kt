package io.github.laozhudu.linkplay.console.online

import io.github.laozhudu.linkplay.console.diagnostics.SourceFailure
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference

/**
 * 在线曲库（Subsonic / tt-music）的最近状态，供诊断页渲染。
 *
 * 形状照抄 `nas/NasSourceStatus.kt`（`docs/65` 批次 1 模板）。
 *
 * ## 这个源真正要回答的问题
 *
 * 「搜不到歌」有两种原因，界面上是同一句"没搜到"：
 * 1. **关键词没命中** —— 换首歌名再搜就行
 * 2. **服务端曲库是空的** —— tt-music 现在就是这个状态，搜什么都搜不到（`docs/61` §2）
 *
 * 协议层没提供"库里有几首歌"的接口，而 `docs/65` 明确**不动 `core-protocol`**。
 * 所以这里用**旁证**：连续几次不同的搜索都是 0 条，那基本可以断定库是空的。
 *
 * 这不是精确判断，但它把"看起来一样的两件事"分开了 —— 而且不撒谎：
 * 只有连续 [ZERO_STREAK_HINT] 次以上才说"大概率是空的"，单次 0 条只说"这个词没命中"。
 */
object SubsonicSourceStatus {

    /** 连续几次 0 条才敢下"库空"的结论。 */
    const val ZERO_STREAK_HINT = 3

    private val ref = AtomicReference(SubsonicStatus())

    fun snapshot(): SubsonicStatus = ref.get()

    private fun mutate(transform: (SubsonicStatus) -> SubsonicStatus) {
        ref.updateAndGet(transform)
    }

    fun reset() = ref.set(SubsonicStatus())

    fun configured(baseUrl: String, user: String) = mutate {
        // 换了地址/账号，之前的 ping 结论和搜索连续性都作废
        it.copy(
            baseUrl = baseUrl,
            user = user,
            verified = null,
            pingMs = 0L,
            zeroStreak = 0,
            lastCount = -1,
            lastQuery = null,
            failure = null,
        )
    }

    fun pinged(ok: Boolean, ms: Long) = mutate {
        it.copy(
            verified = ok,
            pingMs = ms,
            failure = if (ok) null else SourceFailure.AUTH_FAILED.describe("连上了但 ping 返回失败（账号或权限不对）"),
            failureAt = if (ok) it.failureAt else System.currentTimeMillis(),
        )
    }

    fun pingFailed(error: Throwable, ms: Long) = mutate {
        it.copy(
            verified = false,
            pingMs = ms,
            failure = SourceFailure.of(error).describe(error.message),
            failureAt = System.currentTimeMillis(),
        )
    }

    fun searched(query: String, count: Int, ms: Long) = mutate {
        it.copy(
            lastQuery = query,
            lastCount = count,
            lastMs = ms,
            zeroStreak = if (count == 0) it.zeroStreak + 1 else 0,
            verified = true,
            // 单次 0 条不是"失败"，它是业务结果；结论在 render 里按连续次数给
            failure = null,
        )
    }

    fun searchFailed(query: String, error: Throwable, ms: Long) = mutate {
        it.copy(
            lastQuery = query,
            lastCount = -1,
            lastMs = ms,
            failure = SourceFailure.of(error).describe(error.message),
            failureAt = System.currentTimeMillis(),
        )
    }

    fun played(title: String, ok: Boolean, detail: String?) = mutate {
        it.copy(
            lastPlay = if (ok) "成功（$title）" else SourceFailure.UNSUPPORTED.describe("$title：${detail ?: "投送失败"}"),
            lastPlayAt = System.currentTimeMillis(),
        )
    }

    fun render(): String {
        val s = snapshot()
        return buildString {
            if (s.baseUrl == null && s.lastQuery == null && s.lastPlay == null) {
                appendLine("  还没配置过（也没搜过）")
                return@buildString
            }
            s.baseUrl?.let { appendLine("  地址=$it  用户=${s.user ?: "-"}") }
            s.verified?.let {
                appendLine(
                    if (it) "  配置=已通过 ping / ${s.pingMs}ms" else "  配置=未通过（ping 失败）",
                )
            }
            when {
                s.lastCount < 0 && s.lastQuery != null -> appendLine("  最近搜索「${s.lastQuery}」失败 / ${s.lastMs}ms")
                s.lastCount < 0 -> Unit
                s.lastCount > 0 -> appendLine("  最近搜索「${s.lastQuery}」${s.lastCount} 条 / ${s.lastMs}ms")
                s.zeroStreak >= ZERO_STREAK_HINT ->
                    // 这里才是"库空"的判据：连续多次不同关键词都是 0 条
                    appendLine(
                        "  最近搜索「${s.lastQuery}」0 条 / ${s.lastMs}ms" +
                            "  ← 连续 ${s.zeroStreak} 次都是 0 条：服务端曲库大概率是空的（EMPTY）",
                    )
                else -> appendLine(
                    "  最近搜索「${s.lastQuery}」0 条 / ${s.lastMs}ms  ← 这个词没命中" +
                        if (s.zeroStreak > 1) "（已连续 ${s.zeroStreak} 次）" else "",
                )
            }
            s.lastPlay?.let { appendLine("  最近投送=$it") }
            s.failure?.let { appendLine("  最近失败=${clock(s.failureAt)} $it") }
        }
    }

    private fun clock(ms: Long): String =
        if (ms <= 0L) "-" else SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(ms))
}

/** 一次快照。字段都是给人看的，会在诊断页原样出现。 */
data class SubsonicStatus(
    val baseUrl: String? = null,
    val user: String? = null,
    /** null = 还没 ping 过。 */
    val verified: Boolean? = null,
    val pingMs: Long = 0L,
    val lastQuery: String? = null,
    /** -1 = 上次搜索失败（没拿到条数）。 */
    val lastCount: Int = -1,
    val lastMs: Long = 0L,
    /** 连续多少次搜索是 0 条 —— 库空的旁证。 */
    val zeroStreak: Int = 0,
    val lastPlay: String? = null,
    val lastPlayAt: Long = 0L,
    val failureAt: Long = 0L,
    val failure: String? = null,
)
