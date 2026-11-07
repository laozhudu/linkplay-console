package io.github.laozhudu.linkplay.console

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import io.github.laozhudu.linkplay.console.playback.VolumeBus
import io.github.laozhudu.linkplay.console.playback.VolumeOsd
import io.github.laozhudu.linkplay.console.ui.theme.LinkPlayTheme

class MainActivity : ComponentActivity() {

    /**
     * 提到 Activity 层是为了接实体音量键 —— 按键事件来自 Activity，不在 Compose 树里。
     */
    private val consoleVm: ConsoleViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 崩溃记录必须最早装：晚于 ViewModel 初始化就可能漏掉启动期的崩溃。
        // 它只记录不吞异常（闪退行为不变），但下次启动首页会显示堆栈摘要。
        // 记启动时刻：诊断报告的"运行时长"靠它，用于对上"用了两个多小时才出问题"这种描述
        io.github.laozhudu.linkplay.console.diagnostics.AppDiagnostics.markStart()
        io.github.laozhudu.linkplay.console.crash.CrashGuard.install(this) {
            val st = consoleVm.state.value
            "已保存设备=${st.saved.size} 在线=${st.online.count { it.value }} " +
                "选中=${st.selectedIp ?: "无"} 组主机=${st.groupMasterIp ?: "无"} " +
                "卡片=${st.cards.size}"
        }
        // 主题要在第一帧之前读出来，否则会先白一下再变深
        io.github.laozhudu.linkplay.console.ui.theme.ThemePrefs.init(this)

        setContent {
            val mode by io.github.laozhudu.linkplay.console.ui.theme.ThemePrefs.mode
                .collectAsState()
            val dark = io.github.laozhudu.linkplay.console.ui.theme.ThemePrefs.resolve(
                mode = mode,
                systemDark = androidx.compose.foundation.isSystemInDarkTheme(),
            )
            LinkPlayTheme(darkTheme = dark) {
                ConsoleApp(consoleVm)
            }
        }
    }

    /**
     * 手机音量键 → 音箱音量（原厂 App 的行为）。
     *
     * 两个容易踩的点：
     * 1. **必须在 dispatchKeyEvent 拦**，不能只拦 onKeyDown：系统在 ACTION_UP 上也会调音量；
     * 2. DOWN 和 UP 必须给出**同一个判断结果**，否则会出现"按下改音箱、抬起改手机"。
     *    所以两者都问 [ConsoleViewModel.canHandleVolumeKey]，而不是各自判断。
     *
     * 没选设备/设备离线时一律不拦，让系统照常调手机音量 —— 不能把用户的手机变成砖。
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val isVolUp = event.keyCode == KeyEvent.KEYCODE_VOLUME_UP
        val isVolDown = event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
        if ((isVolUp || isVolDown) && consoleVm.canHandleVolumeKey()) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                // 返回新音量就顺手弹出系统样式的音量条（Android 自己那根已经被我们吞掉了）
                consoleVm.adjustVolumeByKey(if (isVolUp) 1 else -1)?.let { v ->
                    // 浮层上写**目标那台**的名字，不是"选中的设备"
                    // （切了目标之后按音量键，浮层写错设备名比不写更糟）
                    val targetIp = (
                        io.github.laozhudu.linkplay.console.playback.PlaybackOutputStore.current.value
                            as? io.github.laozhudu.linkplay.console.playback.PlaybackOutput.Device
                        )?.ip?.takeIf { it.isNotEmpty() }
                        ?: consoleVm.state.value.selectedIp
                    VolumeOsd.show(
                        volume = v,
                        mute = targetIp?.let { VolumeBus.volumeOf(it)?.mute } ?: false,
                        deviceName = consoleVm.state.value.saved
                            .firstOrNull { it.ip == targetIp }?.title,
                    )
                }
            }
            // DOWN 与 UP 都吞掉，系统就不会再去动手机自己的媒体音量
            return true
        }
        return super.dispatchKeyEvent(event)
    }
}
