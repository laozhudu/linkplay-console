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
 * ## 本机的真实能力（用户 2026-11-07 纠错后重写）
 *
 * | 能力 | 有没有 | 依据 |
 * |---|---|---|
 * | 上一首 / 下一首 | **有** | ExoPlayer 的 `seekToNext/PreviousMediaItem` |
 * | 队列 | **有** | ExoPlayer 的 `setMediaItems`（复数） |
 * | 跳进度 | 有 | |
 * | 静音 | **没有** | 手机只有音量，没有"设备侧静音"这个状态 |
 *
 * ## 上一版错在哪（必须记住）
 *
 * 我上一版写的是"本机没有队列、不能切歌"，理由写"本机播放器只播单曲"。
 * 那是**实现缺口伪装成平台限制** —— ExoPlayer 从来就支持列表，
 * 是我只调了 `setMediaItem`（单数），然后在日志里打了一句"暂不支持"。
 *
 * 用户一眼就看出来了：「通用 upnp 模块是能播放列表能切歌，**包括本机也是**，
 * 你的认知错误」。
 *
 * **教训：能力表只写"平台真做不到的"，不写"我这轮没做"。**
 * 后者应该进待办，不该进 `capabilities()` —— 写进去之后没有任何东西会提醒你补。
 */
class LocalEndpoint(
    private val router: PlaybackRouter,
) : PlaybackEndpoint {

    override val key: String = "local"

    override val label: String = "本机"

    override fun capabilities(): EndpointCapabilities = EndpointCapabilitiesCatalog.local

    override fun snapshot(): EndpointState {
        val ui = router.localState.value
        // 队列信息从 **ExoPlayer 真实状态**读，不读 QueueBus —— 后者是发布用的副本，
        // 用户点列表跳一首之后它不一定立刻跟上（切歌发生在 player 内部）。
        val player = router.local.raw()
        val count = player.mediaItemCount
        val idx = player.currentMediaItemIndex
        return EndpointState(
            playing = ui.playing,
            // 本机不显示"歌手"：投流过来的只有一个标题，那个字段是空的
            title = ui.title,
            artist = null,
            positionSec = ui.positionMs / 1000L,
            durationSec = ui.durationMs / 1000L,
            volume = ui.volume,
            mute = false,
            queueIndex = if (count > 0 && idx >= 0) idx + 1 else 0,
            queueCount = count,
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
    override suspend fun next() {
        router.local.seekNext()
        syncIndexToBus()
    }

    override suspend fun previous() {
        router.local.seekPrevious()
        syncIndexToBus()
    }

    /**
     * 切歌后把"第几首"同步给 `QueueBus`。
     *
     * 不这么做的话：列表高亮停在第 1 首，声音已经在第 2 首。
     * 界面读 `queue.index`（列表高亮）和 `snapshot().queueIndex`（真相）来自两处 ——
     * 必须让它们跟上，而不是在界面上再算一遍。
     */
    private fun syncIndexToBus() {
        val i = router.local.raw().currentMediaItemIndex
        if (i >= 0) {
            runCatching {
                io.github.laozhudu.linkplay.console.playback.QueueBus.syncDeviceIndex(i)
            }
        }
    }

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
    }
}
