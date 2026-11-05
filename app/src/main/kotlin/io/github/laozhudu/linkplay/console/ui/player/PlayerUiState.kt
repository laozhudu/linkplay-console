package io.github.laozhudu.linkplay.console.ui.player

/**
 * 界面唯一认识的播放状态 —— **为"以后接本地播放器"留的口子**。
 *
 * ## 为什么现在就要有它
 *
 * 现在的播放是"投到音箱"：界面直接读 `QueueBus` / 设备快照。哪天接本机播放器（Media3），
 * 数据来源整个换掉，**每个读播放状态的界面都要改一遍**。
 *
 * 有了这一层，界面只问"现在播什么、在不在放、能不能控制"，底下是投送还是本机播放，
 * 界面不知道也不需要知道。
 *
 * ## 为什么参数是基本类型而不是 `QueueRunState`
 *
 * 这一层一旦依赖协议模块，将来本地播放器就得多造一个假的 `QueueRunState` 才能喂进来 ——
 * 那是把新实现硬塞进旧模型。用基本类型，两边都只是"填进去"。
 *
 * ## 现在谁在用
 *
 * `MiniPlayerBar`（常驻播放条）。它是唯一"每页都在"的播放展示，
 * 从这里开始换，将来换播放器时它自动跟着走。
 */
data class PlayerUiState(
    /** 当前曲目（没有就是 null，不要用 "" 冒充）。 */
    val title: String? = null,
    /** 是否真的在放 —— 来自设备回报，**不是本地记的**。 */
    val playing: Boolean = false,
    /** 队列里有内容（可能是暂停/停止，但"有歌可放"）。 */
    val hasQueue: Boolean = false,
    /** 控制权丢了（音箱被别人接管），界面不该再显示可点的按钮。 */
    val controlLost: Boolean = false,
    /** 内容是投到设备还是本机播。 */
    val source: Source = Source.Remote,

    /** 播放进度（毫秒）。 */
    val positionMs: Long = 0,

    /** 总时长（毫秒）。直播流为 0。 */
    val durationMs: Long = 0,

    /**
     * 直播流：没有总时长。
     *
     * 为什么要单独一个字段：`durationMs == 0` 也可能是"还没加载完"，
     * 界面据此画进度条会一直显示 0:00 — 看起来像卡住了。
     */
    val isLive: Boolean = false,

    /**
     * 音量 **0..100**。
     *
     * 刻度统一在这一层：ExoPlayer 内部是 0f..1f，设备侧协议是 0..100。
     * 让界面记两套刻度会出现"显示 60 实际 0.6"这种看不出来的错。
     */
    val volume: Int = 0,
) {

    enum class Source {
        /** 投到音箱（现在只有这一种）。 */
        Remote,

        /** 本机播放（将来接 Media3）。 */
        Local,
    }

    /** 有没有东西可展示。 */
    val hasContent: Boolean get() = playing || hasQueue || !title.isNullOrBlank()

    /** 播放条上那句主文案。 */
    val displayTitle: String get() = title?.trim()?.takeIf { it.isNotEmpty() } ?: "没有在播的内容"

    /** 播放条按钮该显示"播放"还是"暂停"。 */
    val toggleToPlay: Boolean get() = !playing

    companion object {
        /** 从"投到音箱"的现状拼出来。将来本机播放器自己填这个类型，不用这个函数。 */
        fun fromRemote(
            nowPlaying: String?,
            running: Boolean,
            playing: Boolean,
            controlLost: Boolean = false,
            positionMs: Long = 0,
            durationMs: Long = 0,
            volume: Int = 0,
        ): PlayerUiState = PlayerUiState(
            title = nowPlaying?.trim()?.takeIf { it.isNotEmpty() },
            playing = playing,
            hasQueue = running,
            controlLost = controlLost,
            source = Source.Remote,
            positionMs = positionMs,
            durationMs = durationMs,
            isLive = durationMs <= 0,
            volume = volume,
        )
    }
}
