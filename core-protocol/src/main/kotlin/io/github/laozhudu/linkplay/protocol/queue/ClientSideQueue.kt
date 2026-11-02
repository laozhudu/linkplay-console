package io.github.laozhudu.linkplay.protocol.queue

/** 队列里的一项（已经解析好的可播放地址）。 */
data class QueueItem(
    val url: String,
    val title: String,
    val artist: String? = null,
    val album: String? = null,
    val durationSec: Long = 0,
    val mimeType: String = "audio/mpeg",
    /**
     * 封面地址（DIDL 的 `upnp:albumArtURI`）。
     *
     * 为什么放在队列项里而不是监听时再查：设备投流后**不回填元数据**
     * （实测 Title/Artist 为空，docs/14 §0.1），封面更是拿不回来。
     * 只有在浏览 DLNA 目录的那一刻才知道封面地址，所以必须随队列入栈。
     */
    val albumArtUrl: String? = null,
)

/** 单曲等待的结局。 */
private enum class TrackOutcome { FINISHED, ABORTED, CANCELLED }

/** 编排器面对的最小播放器接口：注入后可在单测里用假实现驱动。 */
interface QueueTarget {
    /** 投流并开始播放。 */
    fun play(item: QueueItem)

    /** 当前播放位置与总时长（秒）。返回 null 表示读不到（设备掉线等）。 */
    fun position(): Pair<Long, Long>?

    /** 当前传输状态（`PLAYING` / `STOPPED` / …）。 */
    fun transportState(): String
}

/**
 * 客户端排队编排器 —— 用来绕过这台固件「不能推列表」的限制。
 *
 * 背景（真机结论，见 docs/08 §5.2）：DOSS DS-1831 的 PlayQueue 只接受「在线源描述符」，
 * 不接受曲目节点，所以没法让音箱自己连续播一份列表。
 * 标准做法（Music Assistant 的 queue flow 同思路）：**控制端逐首投流**，当前曲目快结束时推下一首。
 *
 * 关键设计：
 * - `probe` / `sleep` / `now` 全部注入，单测用假时钟验证「何时推下一首、何时停止」；
 * - 触发条件用「剩余时间 ≤ [advanceLeadSeconds]」而不是「播放结束」，避免每首之间有静默；
 * - 读不到位置（`position()` 返回 null）不立即判失败，容忍 [maxMisses] 次；
 * - 支持单曲循环 / 列表循环 / 顺序播放三种，与 [`QueueLoopMode`] 语义对齐。
 */
class ClientSideQueue(
    private val target: QueueTarget,
    private val advanceLeadSeconds: Long = 2,
    private val pollIntervalMs: Long = 800,
    private val maxMisses: Int = 5,
) {
    /** 与播放器的循环模式保持一致：0 不循环 / 1 单曲 / 2 列表循环 / 3 随机。 */
    var loopMode: Int = 2

    /** 跑一遍队列到结束。返回实际播放的曲目数。 */
    fun run(
        items: List<QueueItem>,
        sleep: (Long) -> Unit = { Thread.sleep(it) },
        now: () -> Long = { System.currentTimeMillis() },
        shouldStop: () -> Boolean = { false },
    ): Int {
        if (items.isEmpty()) return 0
        var played = 0
        var index = 0
        var guard = 0
        val maxIterations = items.size * (if (loopMode == 2) 2 else 1) + 2

        while (index in items.indices && guard++ < maxIterations) {
            if (shouldStop()) break
            target.play(items[index])
            played++
            when (waitUntilTrackEnds(items[index], sleep, now, shouldStop)) {
                // 读不到位置时**中止队列**，而不是继续切下一首——否则会把每首歌都打断
                TrackOutcome.ABORTED -> return played
                TrackOutcome.CANCELLED -> return played
                TrackOutcome.FINISHED -> Unit
            }

            index = when (loopMode) {
                1 -> index                // 单曲循环
                2 -> (index + 1) % items.size  // 列表循环
                3 -> nextRandom(items.size, index, now) // 随机
                else -> index + 1          // 顺序，播完停
            }
            if (loopMode == 2 && index == 0 && guard >= items.size + 1) break // 只跑完一轮
        }
        return played
    }

    /**
     * 等当前曲目播完。
     *
     * 三种结局必须区分开：正常结束要推下一首；读不到位置要**中止**（继续推只会打断播放）；
     * 外部要求停止要立刻退出。
     */
    private fun waitUntilTrackEnds(
        item: QueueItem,
        sleep: (Long) -> Unit,
        now: () -> Long,
        shouldStop: () -> Boolean,
    ): TrackOutcome {
        val knownDuration = item.durationSec
        var misses = 0
        var started = false
        var lastPos = 0L
        val startAt = now()

        while (true) {
            if (shouldStop()) return TrackOutcome.CANCELLED
            val pos = target.position()
            val state = target.transportState()

            if (pos == null) {
                misses++
                if (misses > maxMisses) return TrackOutcome.ABORTED // 读不到位置，别再切歌
            } else {
                misses = 0
                val (rel, duration) = pos
                val total = if (duration > 0) duration else knownDuration
                if (rel > 0 || state.equals("PLAYING", true)) started = true
                lastPos = rel

                // 正常触发：接近结尾
                if (total > 0 && started && total - rel <= advanceLeadSeconds) return TrackOutcome.FINISHED
                // 兜底触发：走到 STOPPED 且已经开播过（有些曲目设备不给 duration）
                if (started && state.equals("STOPPED", true)) return TrackOutcome.FINISHED
                // 防御：位置长时间不动（设备卡死），超过 30 秒仍未开播则放弃
                if (!started && now() - startAt > 30_000L) return TrackOutcome.ABORTED
            }
            sleep(pollIntervalMs)
        }
    }

    private fun nextRandom(size: Int, current: Int, now: () -> Long): Int {
        if (size <= 1) return current
        var next = current
        val seed = now()
        while (next == current) next = ((seed / pollIntervalMs) % size).toInt()
        return next
    }

    companion object {
        const val LOOP_NONE = 0
        const val LOOP_SINGLE = 1
        const val LOOP_ALL = 2
        const val LOOP_SHUFFLE = 3

        fun label(mode: Int): String = when (mode) {
            LOOP_NONE -> "顺序播放"
            LOOP_SINGLE -> "单曲循环"
            LOOP_ALL -> "列表循环"
            LOOP_SHUFFLE -> "随机"
            else -> "未知($mode)"
        }
    }
}
