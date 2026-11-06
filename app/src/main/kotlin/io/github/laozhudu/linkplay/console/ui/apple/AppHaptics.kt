package io.github.laozhudu.linkplay.console.ui.apple

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * 按语义分级的触觉反馈（借鉴苹果的 haptic 三档）。
 *
 * ## 为什么不能只用 `HapticFeedbackType.TextHandleMove`
 *
 * 项目原来全站都用它 —— 那是最轻的一档（近似"文本光标移动"）。
 * 结果：**删除一台设备、组网成功、点一行，手感完全一样**。
 * 反馈一样 = 没有反馈（苹果的 HIG 专门讲过这点）。
 *
 * ## 三档语义
 *
 * | 档 | 场景 | 常量 |
 * |---|---|---|
 * | [tap] | 点一行、切 tab、chip 变化 | `TEXT_HANDLE_MOVE` |
 * | [impact] | 长按出菜单、拖动到边界 | `LONG_PRESS` / `CLOCK_TICK` |
 * | [success] / [error] | 组网成功 / 播放失败 | `CONFIRM` / `REJECT` |
 *
 * ## 为什么用 `View.performHapticFeedback` 而不是 Compose 的 `LocalHapticFeedback`
 *
 * Compose 的枚举只有两档（`LongPress` / `TextHandleMove`），给不出"成功/失败"。
 * 系统常量更细，而且会**自动尊重用户的"关闭触感"设置**（`performHapticFeedback` 内部判断）。
 */
class AppHaptics internal constructor(private val view: View?) {

    /** 轻点：选择、切页、chip。 */
    fun tap() = fire(HapticFeedbackConstants.TEXT_HANDLE_MOVE)

    /** 影响：长按出菜单、拖动跨过边界、开关切换。 */
    fun impact() = fire(HapticFeedbackConstants.LONG_PRESS)

    /**
     * 拖动过程的刻度感。
     *
     * 用于音量/进度拖动 —— 苹果在滑块上给的是"每跨一格一次轻响"，
     * 不是持续震动（持续震会让人手麻）。
     */
    fun tick() = fire(HapticFeedbackConstants.CLOCK_TICK)

    /** 成功：组网成功、投送成功、保存成功。 */
    fun success() = fire(HapticFeedbackConstants.CONFIRM)

    /** 失败：命令被拒、连不上、超时。 */
    fun error() = fire(HapticFeedbackConstants.REJECT)

    private fun fire(constant: Int) {
        // 拿不到 view 时静默跳过：有些环境（预览/测试）没有真实 View
        view?.performHapticFeedback(constant) ?: Unit
    }
}

@Composable
fun rememberAppHaptics(): AppHaptics {
    val view = LocalView.current
    return remember(view) { AppHaptics(if (view.isInEditMode) null else view) }
}
