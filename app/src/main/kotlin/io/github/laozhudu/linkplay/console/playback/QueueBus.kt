package io.github.laozhudu.linkplay.console.playback

import io.github.laozhudu.linkplay.protocol.queue.QueueItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 队列播放在整个进程内的唯一状态源。
 *
 * 为什么要有它：编排原来在 `NasViewModel` 里跑，用户退出页面 ViewModel 被清掉，
 * 队列就断了（[ClientSideQueue] 的循环随协程一起没）。挪进前台服务后，
 * 服务持有循环，UI 只订阅这份状态——页面来去都不影响播放。
 *
 * 用单例而不是 bind 回调：只有一个播放队列，绑定带来的生命周期复杂度不值得。
 */
data class QueueRunState(
    val running: Boolean = false,
    /** 正在播的曲名。 */
    val nowPlaying: String? = null,
    val playedCount: Int = 0,
    val total: Int = 0,
    /** 编排提前结束（不是用户停止）。通常是控制面掉线。 */
    val controlLost: Boolean = false,
    val error: String? = null,
    val log: List<String> = emptyList(),
)

object QueueBus {
    private val _state = MutableStateFlow(QueueRunState())
    val state: StateFlow<QueueRunState> = _state.asStateFlow()

    fun begin(items: List<QueueItem>) = _state.update {
        QueueRunState(running = true, total = items.size, playedCount = 0)
    }

    fun playing(title: String, index: Int) = _state.update {
        it.copy(nowPlaying = title, playedCount = index + 1)
    }

    fun finish(controlLost: Boolean, log: List<String>) = _state.update {
        it.copy(running = false, controlLost = controlLost, log = log.takeLast(30))
    }

    fun fail(message: String) = _state.update {
        it.copy(running = false, error = message)
    }

    fun appendLog(line: String) = _state.update { it.copy(log = (it.log + line).takeLast(30)) }

    /** 停止后保留 nowPlaying/playedCount，方便 UI 显示「上次播到哪」。 */
    fun stopped() = _state.update { it.copy(running = false) }
}
