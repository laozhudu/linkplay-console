package io.github.laozhudu.linkplay.console.voice

import io.github.laozhudu.linkplay.console.diagnostics.SourceFailure
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference

/**
 * 语音的最近状态，供诊断页渲染。
 *
 * 形状照抄 `nas/NasSourceStatus.kt`（`docs/65` 批次 1 模板）。
 *
 * ## 为什么要分三段记
 *
 * 「语音不管用」是用户能给出的全部信息。但链路上有三段，任何一段断都会表现为"不管用"：
 *
 * ```
 * 识别（系统引擎）  →  意图匹配（IntentRouter）  →  执行（ViewModel）
 *   麦克风权限           "放一首周杰伦" 现在是      next()/pause()/setVolume()
 *   有没有识别服务        Unknown（只认播放控制类）  目前都不会抛
 *   听没听清
 * ```
 *
 * 三种情况的修法完全不同：去授权 / 说法换一种 / 这个口令还没实现。
 * 报告必须写清断在哪一段，而不是只说"语音失败"。
 */
object VoiceSourceStatus {

    private val ref = AtomicReference(VoiceStatus())

    fun snapshot(): VoiceStatus = ref.get()

    private fun mutate(transform: (VoiceStatus) -> VoiceStatus) {
        ref.updateAndGet(transform)
    }

    fun reset() = ref.set(VoiceStatus())

    /** 点麦克风时的前置条件：有没有识别服务、麦克风有没有授权。 */
    fun environment(available: Boolean, micGranted: Boolean) = mutate {
        it.copy(available = available, micGranted = micGranted)
    }

    /** 识别成功（拿到了文本）。是否匹配意图还不知道。 */
    fun heard(text: String) = mutate {
        it.copy(
            lastHeard = text,
            lastIntent = null,
            lastResult = null,
            lastOk = null,
            failure = null,
        )
    }

    /**
     * 意图匹配 + 执行的结果。
     *
     * [matched] 为 false 表示 `IntentRouter` 没认出这个说法（`VoiceIntent.Unknown`）——
     * 这**不是**失败，是"还没实现这个口令"，报告里的措辞要能区分。
     */
    fun routed(intent: String, result: String, matched: Boolean) = mutate {
        it.copy(
            lastIntent = intent,
            lastResult = result,
            lastOk = matched,
            failure = if (matched) {
                null
            } else {
                SourceFailure.UNSUPPORTED.describe("没匹配到意图（只认播放控制类口令）")
            },
            failureAt = if (matched) it.failureAt else System.currentTimeMillis(),
        )
    }

    /** 识别段失败：没听清 / 没服务 / 没权限。 */
    fun failed(reason: String) = mutate {
        val kind = when {
            reason.contains("权限") -> SourceFailure.PERMISSION_DENIED
            // 本机没装/没启用识别引擎：这不是"失败"，是这台手机上压根没有这个能力
            reason.contains("识别") && reason.contains("没有") -> SourceFailure.NOT_CONFIGURED
            reason.contains("没听清") -> SourceFailure.EMPTY
            else -> SourceFailure.UNKNOWN
        }
        it.copy(
            lastHeard = null,
            lastIntent = null,
            lastResult = null,
            lastOk = false,
            failure = kind.describe(reason),
            failureAt = System.currentTimeMillis(),
        )
    }

    fun render(): String {
        val s = snapshot()
        return buildString {
            if (s.available == null && s.lastHeard == null && s.failure == null) {
                appendLine("  还没用过语音")
                return@buildString
            }
            if (s.available != null) {
                appendLine(
                    "  识别服务=" + (if (s.available) "可用" else "不可用（本机没有语音识别引擎）") +
                        if (s.micGranted != null) "  麦克风=" + (if (s.micGranted) "已授权" else "未授权") else "",
                )
            }
            when {
                s.lastHeard != null && s.lastIntent != null ->
                    appendLine("  最近识别「${s.lastHeard}」→ 意图=${s.lastIntent} → 执行=${s.lastResult}")
                s.lastHeard != null -> appendLine("  最近识别「${s.lastHeard}」→ 还没走到意图匹配")
                else -> Unit
            }
            s.failure?.let { appendLine("  最近失败=${clock(s.failureAt)} $it") }
        }
    }

    private fun clock(ms: Long): String =
        if (ms <= 0L) "-" else SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(ms))
}

/** 一次快照。字段都是给人看的，会在诊断页原样出现。 */
data class VoiceStatus(
    /** null = 还没点过麦克风。 */
    val available: Boolean? = null,
    val micGranted: Boolean? = null,
    /** 识别出来的原文。 */
    val lastHeard: String? = null,
    /** 匹配到的意图名；null = 没走到这一步。 */
    val lastIntent: String? = null,
    val lastResult: String? = null,
    /** null = 还没执行；false = 没匹配到意图（不是崩溃）。 */
    val lastOk: Boolean? = null,
    val failureAt: Long = 0L,
    val failure: String? = null,
)
