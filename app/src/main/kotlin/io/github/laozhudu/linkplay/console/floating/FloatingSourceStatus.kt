package io.github.laozhudu.linkplay.console.floating

import io.github.laozhudu.linkplay.console.diagnostics.SourceFailure
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference

/**
 * 悬浮窗的最近状态，供诊断页渲染。
 *
 * 形状照抄 `nas/NasSourceStatus.kt`（`docs/65` 批次 1 模板）。
 *
 * ## 这个源为什么容易被误判
 *
 * 「悬浮窗不显示」有三种原因，界面上都是"没有那个条"：
 *
 * | 真因 | 现象 | 修法 |
 * |---|---|---|
 * | 没有悬浮权限 | 设置页开关会自己弹回去 | 去系统设置授权 |
 * | 有权限但 `addView` 被 ROM 拒 | 权限显示已授予，条却没出来 | ROM 限制，看具体异常 |
 * | 窗口算出了 0 尺寸 | 有条但看不见 | 布局问题 |
 *
 * 第一种用户自己能看出来（开关弹回），后两种只能靠报告。
 *
 * 关键点：`canDrawOverlays()` 返回 true **不代表** `addView` 一定会成功 ——
 * 部分 ROM 会再拒一次。所以两个结果必须分开记。
 */
object FloatingSourceStatus {

    private val ref = AtomicReference(FloatingStatus())

    fun snapshot(): FloatingStatus = ref.get()

    private fun mutate(transform: (FloatingStatus) -> FloatingStatus) {
        ref.updateAndGet(transform)
    }

    fun reset() = ref.set(FloatingStatus())

    fun permission(granted: Boolean) = mutate {
        it.copy(
            permissionGranted = granted,
            // 权限被撤掉是"当前状态"，不是一次失败事件；失败事件由 failed() 记
            added = if (granted) it.added else null,
        )
    }

    fun started(targetIp: String?) = mutate {
        it.copy(started = true, targetIp = targetIp, action = "开", actionAt = System.currentTimeMillis())
    }

    fun stopped(reason: String) = mutate {
        it.copy(
            started = false,
            added = null,
            action = "关（$reason）",
            actionAt = System.currentTimeMillis(),
            // 用户主动关闭不是失败
            failure = if (reason.contains("用户")) null else it.failure,
        )
    }

    fun added(widthPx: Int, heightPx: Int, x: Int, y: Int, targetIp: String?) = mutate {
        it.copy(
            added = true,
            widthPx = widthPx,
            heightPx = heightPx,
            x = x,
            y = y,
            targetIp = targetIp ?: it.targetIp,
            failure = if (widthPx <= 0 || heightPx <= 0) {
                SourceFailure.UNSUPPORTED.describe("窗口算出了 0 尺寸（${widthPx}×${heightPx}），加了也看不见")
            } else {
                null
            },
            failureAt = if (widthPx <= 0 || heightPx <= 0) System.currentTimeMillis() else it.failureAt,
        )
    }

    /** `canDrawOverlays` 为 true 但 `addView` 仍然失败 —— 这类只有报告能看出来。 */
    fun addFailed(error: Throwable) = mutate {
        it.copy(
            added = false,
            failure = SourceFailure.of(error).describe("加窗被拒：${error.javaClass.simpleName}: ${error.message}"),
            failureAt = System.currentTimeMillis(),
        )
    }

    fun failed(kind: SourceFailure, detail: String?) = mutate {
        it.copy(
            added = false,
            failure = kind.describe(detail),
            failureAt = System.currentTimeMillis(),
        )
    }

    fun render(): String {
        val s = snapshot()
        return buildString {
            if (s.permissionGranted == null) {
                appendLine("  还没开过悬浮窗")
                return@buildString
            }
            appendLine(
                "  权限=" + if (s.permissionGranted) {
                    "已授予（SYSTEM_ALERT_WINDOW）"
                } else {
                    "未授予（SYSTEM_ALERT_WINDOW）  ← 设置页开关会自己弹回「关」"
                },
            )
            if (s.started != null) {
                appendLine("  服务=" + (if (s.started) "运行中" else "未运行"))
            }
            if (s.added == true) {
                appendLine("  窗口=${s.widthPx}×${s.heightPx}px @ (${s.x}, ${s.y})")
            }
            s.targetIp?.let { appendLine("  目标设备=$it") }
            s.action?.let { appendLine("  最近动作=$it / ${clock(s.actionAt)}") }
            s.failure?.let { appendLine("  最近失败=${clock(s.failureAt)} $it") }
        }
    }

    private fun clock(ms: Long): String =
        if (ms <= 0L) "-" else SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(ms))
}

/** 一次快照。字段都是给人看的，会在诊断页原样出现。 */
data class FloatingStatus(
    /** null = 还没开过。 */
    val permissionGranted: Boolean? = null,
    /** null = 还没启动过服务。 */
    val started: Boolean? = null,
    /** null = 没尝试加窗 / 已被移除。 */
    val added: Boolean? = null,
    val widthPx: Int = 0,
    val heightPx: Int = 0,
    val x: Int = 0,
    val y: Int = 0,
    val targetIp: String? = null,
    val action: String? = null,
    val actionAt: Long = 0L,
    val failureAt: Long = 0L,
    val failure: String? = null,
)
