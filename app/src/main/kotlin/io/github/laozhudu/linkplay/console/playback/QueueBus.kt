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
 * 队列就断了（`ClientSideQueue` 的循环随协程一起没）。挪进前台服务后，服务持有循环，
 * UI 只订阅这份状态 —— 页面来去都不影响播放。
 *
 * **队列内容也放在这里**（[QueueRunState.items]）：播放页要展示「播放列表」并支持点某首跳过去，
 * 那是播放页的一部分，不该再藏到别的页面里。
 */
data class QueueRunState(
    val running: Boolean = false,
    val items: List<QueueItem> = emptyList(),
    /** 当前正在播第几首（0 基）。空队列为 -1。 */
    val index: Int = -1,
    /** 正在播的曲名。 */
    val nowPlaying: String? = null,
    val playedCount: Int = 0,
    val total: Int = 0,
    /** 编排提前结束（不是用户停止）。通常是控制面掉线。 */
    val controlLost: Boolean = false,
    val error: String? = null,
    val log: List<String> = emptyList(),
) {
    val current: QueueItem? get() = items.getOrNull(index)
}

object QueueBus {
    private val _state = MutableStateFlow(QueueRunState())
    val state: StateFlow<QueueRunState> = _state.asStateFlow()

    /**
     * 服务注册进来的「操作队列」入口。
     *
     * 为什么用回调而不是让 UI 持有 Service：UI 只该看到 state 和几个动作；
     * 直接引用 Service 会让 ViewModel / Compose 的生命周期跟着服务乱掉。
     */
    @Volatile
    private var controller: QueueController? = null

    fun bind(c: QueueController?) {
        controller = c
    }

    fun begin(items: List<QueueItem>) = _state.update {
        QueueRunState(running = true, items = items, index = 0, total = items.size, playedCount = 0)
    }

    fun playing(title: String, index: Int, items: List<QueueItem>? = null) = _state.update {
        it.copy(
            nowPlaying = title,
            index = index,
            playedCount = index + 1,
            items = items ?: it.items,
        )
    }

    fun finish(controlLost: Boolean, log: List<String>) = _state.update {
        it.copy(running = false, controlLost = controlLost, log = log.takeLast(30))
    }

    fun fail(message: String) = _state.update {
        it.copy(running = false, error = message)
    }

    fun appendLog(line: String) = _state.update { it.copy(log = (it.log + line).takeLast(30)) }

    /** 停止后保留 items/nowPlaying，方便 UI 显示「上次播到哪」。 */
    fun stopped() = _state.update { it.copy(running = false) }

    fun clear() = _state.update { QueueRunState() }

    /** 跳到第 [index] 首。服务不在时什么都不做（UI 自己回读状态即可）。 */
    fun skipTo(index: Int) {
        controller?.skipTo(index)
    }

    /** 从队列里移除第 [index] 首。 */
    fun removeAt(index: Int) {
        controller?.removeAt(index)
    }
}

/** 队列操作面：只暴露播放页需要的两个动作，避免 UI 依赖整个 Service。 */
interface QueueController {
    fun skipTo(index: Int)
    fun removeAt(index: Int)
}
