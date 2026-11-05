package io.github.laozhudu.linkplay.console.playback

import android.content.Context
import io.github.laozhudu.linkplay.protocol.dlna.DeviceCapabilities
import io.github.laozhudu.linkplay.protocol.playback.PlayIntent

/**
 * **全部播放意图的唯一执行点。**
 *
 * ## 为什么必须有这一层
 *
 * 「下一首」以前有 4 条路径：
 *   1 界面    `PlayerScreen` → `vm.next()`
 *   2 语音    `IntentRouter` → `vm.next()`
 *   3 悬浮窗  `FloatingControlService` → `send(LinkPlayCommands.nextTrack())`  ← 绕过一切
 *   4 通知栏  MediaSession(ExoPlayer) → 只对本机有效
 *
 * 路径 3 是 bug：那是 LinkPlay 私有 httpapi，投通用 DLNA 渲染器时设备不认，
 * 而 `DeviceCapabilities.supportsNextPrev` 从来没被读过 —— 界面上那个按钮
 * 在电视盒子上照样画出来，按了毫无反应。
 *
 * ## 两条硬规矩
 *
 * 1. **能力位判断在这里做，不在界面做** —— 界面只问 [can]，不自己判断型号。
 * 2. **不实现播放逻辑，只做分派** —— 真正的实现仍在 `PlaybackRouter` / `PlaybackViewModel`。
 *
 * 这条分工是为了防止它长成第二个巨型 `when`：每个意图一个私有小函数，
 * `dispatch` 只负责把它们对上号。
 */
class PlaybackDispatcher(
    private val app: android.content.Context,
    private val router: PlaybackRouter = PlaybackRouter.of(app),
    private val capabilities: (ip: String) -> DeviceCapabilities = { DeviceProfileCache.capabilitiesOf(it) },
) {

    /**
     * 执行一个意图。
     *
     * @return true 表示「这个意图在当前输出上能执行且已发出」；
     *   false 表示「这台设备没有这个能力」或「当前没有播放器」——
     *   界面据此**不渲染**对应按钮，而不是画出来再让用户按了没反应。
     */
    suspend fun dispatch(intent: PlayIntent): Boolean = when (intent) {
        PlayIntent.Next -> next()
        PlayIntent.Previous -> previous()
        PlayIntent.Play -> play()
        PlayIntent.Pause -> pause()
        PlayIntent.Stop -> stop()
        PlayIntent.TogglePlay -> toggle()
        PlayIntent.VolumeUp -> volumeBy(+5)
        PlayIntent.VolumeDown -> volumeBy(-5)
        PlayIntent.Mute -> mute()
        PlayIntent.LoopNext -> loopNext()
        is PlayIntent.SetVolume -> setVolume(intent.value)
        is PlayIntent.SeekTo -> seekTo(intent.seconds)
        // 搜索不归播放分派管：它要跳界面、要选源，界面自己处理
        is PlayIntent.Search -> false
        is PlayIntent.Unknown -> false
    }

    /**
     * 这个意图在当前输出上能不能执行。界面用它决定画不画按钮。
     *
     * 注意 `Local` 分支一律 true —— 本机播放器什么都能做，不存在能力位问题。
     */
    fun can(intent: PlayIntent): Boolean = when (intent) {
        PlayIntent.Next, PlayIntent.Previous -> when (router.output()) {
            PlaybackOutput.Local -> true
            is PlaybackOutput.Device -> caps().supportsNextPrev
        }
        is PlayIntent.SeekTo -> when (router.output()) {
            PlaybackOutput.Local -> true
            is PlaybackOutput.Device -> caps().supportsSeek
        }
        is PlayIntent.SetVolume, PlayIntent.VolumeUp, PlayIntent.VolumeDown ->
            router.output() == PlaybackOutput.Local || caps().supportsVolume
        PlayIntent.Mute -> router.output() == PlaybackOutput.Local || caps().supportsMute
        PlayIntent.LoopNext -> router.output() == PlaybackOutput.Local
        else -> true
    }

    /** 当前受控设备的能力位。取不到设备时给「什么都没有」，走最保守的一支。 */
    private fun caps(): DeviceCapabilities {
        val ip = QueueBus.owner() ?: return DeviceCapabilities.NONE
        return capabilities(ip)
    }

    private fun vm(): PlaybackViewModel? = PlaybackViewModels.current

    // ---- 每个意图一个私有实现。dispatch 只做分派，这里不许再长出第二层 when ----

    private suspend fun next(): Boolean {
        val target = vm() ?: return false
        return when (router.output()) {
            PlaybackOutput.Local -> { router.local.raw().seekToNextMediaItem(); true }
            is PlaybackOutput.Device -> caps().supportsNextPrev.also { if (it) target.next() }
        }
    }

    private suspend fun previous(): Boolean {
        val target = vm() ?: return false
        return when (router.output()) {
            PlaybackOutput.Local -> { router.local.raw().seekToPreviousMediaItem(); true }
            is PlaybackOutput.Device -> caps().supportsNextPrev.also { if (it) target.previous() }
        }
    }

    private suspend fun play(): Boolean {
        val target = vm() ?: return false
        return when (router.output()) {
            PlaybackOutput.Local -> { router.startLocalService(); router.local.raw().play(); true }
            is PlaybackOutput.Device -> { target.play(); true }
        }
    }

    private suspend fun pause(): Boolean {
        val target = vm() ?: return false
        return when (router.output()) {
            PlaybackOutput.Local -> { router.local.raw().pause(); true }
            is PlaybackOutput.Device -> { target.pause(); true }
        }
    }

    private suspend fun stop(): Boolean {
        val target = vm() ?: return false
        return when (router.output()) {
            PlaybackOutput.Local -> { router.local.raw().stop(); true }
            is PlaybackOutput.Device -> { target.stop(); true }
        }
    }

    /** 「播放/暂停」取决于当前状态 —— 以设备回报为准，不在本地猜。 */
    private suspend fun toggle(): Boolean =
        if (vm()?.state?.value?.state == io.github.laozhudu.linkplay.protocol.upnp.TransportState.PLAYING) {
            pause()
        } else {
            play()
        }

    private suspend fun volumeBy(delta: Int): Boolean {
        val target = vm() ?: return false
        return when (router.output()) {
            PlaybackOutput.Local -> {
                // ExoPlayer 的音量是 0f..1f，设备侧是 0..100 —— 换算是这一层的责任，
                // 别让上层记两套刻度（真机上「显示 60 实际 0.6」这种错很难看出来）。
                val player = router.local.raw()
                val current = (player.volume * 100f).toInt()
                player.volume = ((current + delta).coerceIn(0, 100)) / 100f
                true
            }
            is PlaybackOutput.Device -> {
                if (!caps().supportsVolume) return false
                target.setVolumeLive((target.state.value.volume + delta).coerceIn(0, 100))
                true
            }
        }
    }

    private suspend fun setVolume(value: Int): Boolean {
        val target = vm() ?: return false
        return when (router.output()) {
            PlaybackOutput.Local -> { router.local.raw().volume = value.coerceIn(0, 100) / 100f; true }
            is PlaybackOutput.Device -> {
                if (!caps().supportsVolume) return false
                target.setVolumeLive(value.coerceIn(0, 100))
                true
            }
        }
    }

    private suspend fun seekTo(seconds: Long): Boolean {
        val target = vm() ?: return false
        return when (router.output()) {
            // 本机侧用毫秒；设备侧协议就是秒（见 PlaybackViewModel.seekTo 的参数）
            PlaybackOutput.Local -> { router.local.raw().seekTo(seconds * 1000L); true }
            is PlaybackOutput.Device -> {
                if (!caps().supportsSeek) return false
                target.seekTo(seconds)
                true
            }
        }
    }

    private suspend fun mute(): Boolean {
        val target = vm() ?: return false
        return when (router.output()) {
            PlaybackOutput.Local -> { router.local.raw().volume = 0f; true }
            is PlaybackOutput.Device -> {
                if (!caps().supportsMute) return false
                target.toggleMute()
                true
            }
        }
    }

    private suspend fun loopNext(): Boolean {
        // 循环模式只有设备侧有（本机侧 ExoPlayer 的 repeatMode 不在本轮范围）
        if (router.output() != PlaybackOutput.Device("")) return false
        val target = vm() ?: return false
        target.cycleLoopMode()
        return true
    }
}

/**
 * 当前受控的 [PlaybackViewModel]。
 *
 * ## 为什么要有这个持有器
 *
 * `PlaybackViewModel` 由 `PlayerScreen` 创建（它的 `deviceIp` 是构造参数），
 * dispatcher 是进程级的、拿不到 Composable 的作用域。所以由播放页在进入时登记、
 * 离开时注销。
 *
 * ## 为什么不是 map<ip, vm>
 *
 * 项目里 `PlaybackViewModel` **刻意只有一份**（`switchTo` 原地换设备，见那个类的 KDoc：
 * 以前按 ip 建多个 VM，旧 VM 的轮询会往新设备的状态里写，表现是「切过去先是报错，一会儿自己好了」）。
 * 所以这里只能是单值 —— 如果哪天要按 ip 存多个，**先回去读那段注释**。
 */
object PlaybackViewModels {

    @Volatile
    private var currentVm: PlaybackViewModel? = null

    val current: PlaybackViewModel? get() = currentVm

    fun bind(vm: PlaybackViewModel) {
        currentVm = vm
    }

    fun unbind(vm: PlaybackViewModel) {
        if (currentVm === vm) currentVm = null
    }
}
