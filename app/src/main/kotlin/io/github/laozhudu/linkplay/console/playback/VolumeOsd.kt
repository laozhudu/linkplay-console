package io.github.laozhudu.linkplay.console.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 音量浮层当前要显示的内容。 */
data class OsdState(
    val volume: Int,
    val mute: Boolean,
    val deviceName: String?,
    /** 每次按键都变，用来让浮层重新计时（同音量连按时也能刷新停留时间）。 */
    val nonce: Long = System.currentTimeMillis(),
)

/**
 * 音量浮层的驱动。
 *
 * 为什么用一个进程级对象而不是把状态一路传下去：
 * 按键事件在 `Activity.dispatchKeyEvent` 里（Compose 树之外），
 * 浮层要画在 Compose 树最上层。两者之间最干净的接法就是一个极小的 StateFlow，
 * 而不是把 Activity 的引用塞进 ViewModel。
 */
object VolumeOsd {
    private val _state = MutableStateFlow<OsdState?>(null)
    val state: StateFlow<OsdState?> = _state.asStateFlow()

    fun show(volume: Int, mute: Boolean, deviceName: String?) {
        _state.value = OsdState(volume, mute, deviceName)
    }

    /** 到时间才收起。带 [nonce] 比对，避免"上一轮定时器"把这一轮提前关掉。 */
    fun hideIfStill(nonce: Long) {
        val cur = _state.value ?: return
        if (cur.nonce == nonce) _state.value = null
    }
}
