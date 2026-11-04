package io.github.laozhudu.linkplay.console.library

import io.github.laozhudu.linkplay.console.diagnostics.SourceFailure
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference

/**
 * 本地曲库的**最近一次扫描状态**，供诊断页渲染。
 *
 * 形状照抄 `nas/NasSourceStatus.kt`（`docs/65` 批次 1 的模板）：
 * 单例 + `AtomicReference` 快照 + 每个动作一个记录函数 + `render()`。
 *
 * ## 这个源最关键的判断
 *
 * 用户说「本地没音乐」时，界面上永远是同一句"没有找到音乐"。但有四种原因：
 *
 * | 原因 | 用户该做什么 | 报告里怎么区分 |
 * |---|---|---|
 * | 没给权限 | 去点"读取本机音乐库" | `权限=未授予` |
 * | 给了权限但手机真没歌 | 去下歌 | `扫描 0 首 ← MediaStore 里没有音频` |
 * | 有歌但路径为空 / 格式不支持 | 换格式或换歌 | `扫描 0 首 ← 查到 N 条但全被跳过` |
 * | 查询抛异常 | 看错误 | `最近失败=… IOException` |
 *
 * 前三种在界面上完全同形，而修法完全不同 —— 这正是 `docs/61` 要补它的理由。
 *
 * ## 为什么权限要**主动**检查
 *
 * 权限被拒时 `MediaStore` 可能返回空游标而不是抛异常。等它抛就晚了：
 * 报告里会变成"0 首"，看不出是没权限。所以扫描前先自己查一次。
 */
object LocalSourceStatus {

    private val ref = AtomicReference(LocalStatus())

    fun snapshot(): LocalStatus = ref.get()

    private fun mutate(transform: (LocalStatus) -> LocalStatus) {
        ref.updateAndGet(transform)
    }

    /** 测试用：清回初始，避免用例互相污染。 */
    fun reset() = ref.set(LocalStatus())

    /**
     * 记一次权限检查结果。
     *
     * 不在这里写 `failure` —— 权限不足由 [LocalStatus.granted] 表达，
     * 再写一条失败信息会让报告里同一件事说两遍。
     */
    fun permission(name: String, granted: Boolean) = mutate {
        it.copy(permission = name, granted = granted)
    }

    fun scanned(count: Int, skipped: Int, ms: Long) = mutate { cur ->
        val next = cur.copy(count = count, skipped = skipped, ms = ms)
        when {
            // 没权限的时候"0 首"是意料之中的，不该盖掉权限那条结论
            cur.granted == false -> next
            count > 0 -> next.copy(failure = null)
            skipped > 0 -> next.copy(
                failure = SourceFailure.PARSE_FAILED.describe("查到 $skipped 条，但路径为空或格式不支持，全部跳过"),
                failureAt = System.currentTimeMillis(),
            )
            else -> next.copy(
                failure = SourceFailure.EMPTY.describe("MediaStore 里没有音频"),
                failureAt = System.currentTimeMillis(),
            )
        }
    }

    fun scanFailed(error: Throwable, ms: Long) = mutate {
        it.copy(
            count = 0,
            ms = ms,
            failure = SourceFailure.of(error).describe(error.message),
            failureAt = System.currentTimeMillis(),
        )
    }

    fun render(): String {
        val s = snapshot()
        return buildString {
            when {
                s.permission == null -> appendLine("  权限=还没检查过")
                s.granted == true -> appendLine("  权限=已授予（${s.permission}）")
                // 这一行必须点明后果：否则"未授予"会被读成一句无关紧要的提示
                else -> appendLine("  权限=未授予（${s.permission}）  ← 扫描会被系统拒掉，界面只会显示 0 首")
            }
            when {
                s.count < 0 -> appendLine("  扫描=还没扫过")
                s.count > 0 -> appendLine(
                    "  扫描=${s.count} 首 / ${s.ms}ms" +
                        if (s.skipped > 0) "（跳过 ${s.skipped} 条路径为空或格式不支持）" else "",
                )
                s.skipped > 0 -> appendLine("  扫描 0 首 / ${s.ms}ms  ← 查到 ${s.skipped} 条但全被跳过")
                else -> appendLine("  扫描 0 首 / ${s.ms}ms  ← MediaStore 里没有音频")
            }
            s.failure?.let { appendLine("  最近失败=${clock(s.failureAt)} $it") }
        }
    }

    private fun clock(ms: Long): String =
        if (ms <= 0L) "-" else SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(ms))
}

/** 一次快照。字段都是给人看的，会在诊断页原样出现。 */
data class LocalStatus(
    /** 检查过哪个权限（`READ_MEDIA_AUDIO` / `READ_EXTERNAL_STORAGE`）。 */
    val permission: String? = null,
    val granted: Boolean? = null,
    /** -1 = 还没扫过。 */
    val count: Int = -1,
    /** 查到但被跳过的条目数（路径为空、扩展名不支持）。 */
    val skipped: Int = 0,
    val ms: Long = 0L,
    val failureAt: Long = 0L,
    val failure: String? = null,
)
