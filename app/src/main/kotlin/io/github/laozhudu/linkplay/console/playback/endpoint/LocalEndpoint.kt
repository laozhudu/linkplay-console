package io.github.laozhudu.linkplay.console.playback.endpoint

import io.github.laozhudu.linkplay.console.playback.PlaybackRouter

/**
 * **本机播放** —— 手机上直接出声（Media3 ExoPlayer）。
 *
 * ## 为什么必须是一个独立模块（用户 2026-11-07）
 *
 * > 「还有本机播放应该也是个独立模块」
 *
 * 原来本机不是模块，是 `PlayerScreen` 里的 `if (isLocalOutput)` 分支：
 * 状态读 `router.localState`，控制调 `router.local.pause()`，
 * 而所有"设备才有"的东西（队列、静音、上一首下一首）在这条路上**根本没有**。
 *
 * 于是每次有人问"本机为什么和音箱不一样"，答案都是"因为它是 if 的 else 分支"。
 *
 * ## 本机的真实能力（如实报，不假装）
 *
 * | 能力 | 有没有 | 为什么 |
 * |---|---|---|
 * | 上一首 / 下一首 | **没有** | 本机播放器只播单曲（投流过来的就是一个 URL） |
 * | 队列 | **没有** | 同上 —— 画一个点开是空的播放列表比不画更糟 |
 * | 静音 | **没有** | 手机没有"设备侧静音"，只有音量 |
 * | 跳进度 | **有** | ExoPlayer 支持，本轮补的 |
 *
 * 这四条由 [capabilities] 如实声明，界面据此**不画**这些控件 —— 而不是画出来点了没反应。
 */
class LocalEndpoint(
    private val router: PlaybackRouter,
) : PlaybackEndpoint {

    override val key: String = "local"

    override val label: String = "本机"

    override fun capabilities(): EndpointCapabilities = LOCAL_CAPS

    override fun snapshot(): EndpointState {
        val ui = router.localState.value
        return EndpointState(
            playing = ui.playing,
            // 本机不显示"歌手"：投流过来的只有一个标题，那个字段是空的
            title = ui.title,
            artist = null,
            positionSec = ui.positionMs / 1000L,
            durationSec = ui.durationMs / 1000L,
            volume = ui.volume,
            mute = false,
            queueIndex = 0,
            queueCount = 0,
            statusText = when {
                ui.playing -> "本机播放"
                ui.hasQueue -> "已暂停"
                else -> "本机 · 待播放"
            },
        )
    }

    override suspend fun toggle() {
        val local = router.local
        if (router.localState.value.playing) local.pause() else local.resume()
    }

    /** 本机没有队列 —— 安静忽略（`capabilities()` 已经让界面不画这个键）。 */
    override suspend fun next() = Unit

    override suspend fun previous() = Unit

    override suspend fun seek(positionSec: Long) {
        router.local.seekTo(positionSec)
    }

    override suspend fun setVolume(percent: Int) {
        router.local.setVolume(percent)
    }

    /**
     * 本机没有"静音"这个设备侧状态，退化成**音量归零**。
     *
     * 为什么不是 0 和恢复值两个状态：要多记一份"上次多少"，
     * 而手机系统音量本来就能被音量键改 —— 那份记忆会立刻过期。
     */
    override suspend fun setMute(mute: Boolean) {
        router.local.setVolume(if (mute) 0 else DEFAULT_UNMUTE_VOLUME)
    }

    private companion object {
        /** 静音取消后给一个中间值，不是 100（突然巨响）。 */
        const val DEFAULT_UNMUTE_VOLUME = 60

        val LOCAL_CAPS = EndpointCapabilities(
            canNext = false,
            canPrevious = false,
            canSeek = true,
            canMute = false,
            hasQueue = false,
        )
    }
}
