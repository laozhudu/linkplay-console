package io.github.laozhudu.linkplay.console.playback

import io.github.laozhudu.linkplay.console.playback.endpoint.DeviceSnapshotInput
import io.github.laozhudu.linkplay.console.playback.endpoint.Endpoints
import io.github.laozhudu.linkplay.console.playback.endpoint.PlaybackEndpoint
import io.github.laozhudu.linkplay.protocol.dlna.DeviceCapabilities
import io.github.laozhudu.linkplay.protocol.playback.PlayIntent
import io.github.laozhudu.linkplay.protocol.upnp.TransportState

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
    fun can(intent: PlayIntent): Boolean {
        val c = endpoint().capabilities()
        return when (intent) {
            PlayIntent.Next -> c.canNext
            PlayIntent.Previous -> c.canPrevious
            is PlayIntent.SeekTo -> c.canSeek
            PlayIntent.Mute -> c.canMute
            is PlayIntent.SetVolume, PlayIntent.VolumeUp, PlayIntent.VolumeDown -> true
            PlayIntent.LoopNext -> c.hasQueue
            else -> true
        }
    }

    /**
     * 当前目标那个 endpoint。
     *
     * ## 这就是本类存在的全部意义
     *
     * 原来这里有 **12 个 `when (router.output())`** —— 每个意图都写一遍
     * 「本机这样、设备那样」。加第三类（通用 UPnP）时，12 处都要回头看一遍，
     * 漏一处就是"这个功能在盒子上不生效"。
     *
     * 现在判断只在 `Endpoints` 工厂里做一次，这里只负责转交。
     */
    private fun endpoint(): PlaybackEndpoint {
        val ip = QueueBus.owner()
        val capsNow = ip?.let { capabilities(it) } ?: DeviceCapabilities.NONE
        val snapshot = vm()?.state?.value
        return Endpoints.current(
            router = router,
            vm = this::vm,
            ip = ip,
            label = snapshot?.friendlyName ?: (ip ?: "设备"),
            capabilities = capsNow,
            input = ::deviceInput,
        )
    }

    /** 把 VM 的状态投影成"不依赖具体协议"的原料（见 `DeviceSnapshotInput` 的注释）。 */
    private fun deviceInput(): DeviceSnapshotInput {
        val s = vm()?.state?.value ?: return DeviceSnapshotInput()
        val queue = QueueBus.state.value
        return DeviceSnapshotInput(
            playing = s.state == TransportState.PLAYING ||
                s.state == TransportState.TRANSITIONING,
            stopped = s.state == TransportState.STOPPED || s.state == TransportState.NO_MEDIA_PRESENT,
            title = s.title,
            artist = s.artist,
            positionSec = s.positionSec,
            durationSec = s.durationSec,
            volume = s.volume,
            mute = s.mute,
            queueIndex = (queue.index + 1).coerceAtLeast(0),
            queueCount = queue.items.size.coerceAtLeast(0),
            // 输入源的人话名字由界面层已有的 translator 给；这里只管"是不是网络输入"
            inputSourceLabel = null,
        )
    }

    /** 当前受控设备的能力位。取不到设备时给「什么都没有」，走最保守的一支。 */
    private fun caps(): DeviceCapabilities {
        val ip = QueueBus.owner() ?: return DeviceCapabilities.NONE
        return capabilities(ip)
    }

    private fun vm(): PlaybackViewModel? = PlaybackViewModels.current

    // ---- 意图 → endpoint 的方法。这里**不许再长出第二层判断** ----

    private suspend fun next(): Boolean = endpoint().let { it.next(); it.capabilities().canNext }

    private suspend fun previous(): Boolean =
        endpoint().let { it.previous(); it.capabilities().canPrevious }

    /**
     * 播放 / 暂停是**幂等**的，不能直接调 `toggle()`。
     *
     * `toggle()` 是"切换"，而 `PlayIntent.Play` 的语义是"要它放"。
     * 如果当前已经在放，再 toggle 一下就停了 —— 语音说「播放」反而把歌停了，
     * 这类错只在"已经在放的时候又说一遍"才出现，很难复现。
     */
    private suspend fun play(): Boolean {
        if (router.output() == PlaybackOutput.Local) router.startLocalService()
        val ep = endpoint()
        if (!ep.snapshot().playing) ep.toggle()
        return true
    }

    private suspend fun pause(): Boolean {
        val ep = endpoint()
        if (ep.snapshot().playing) ep.toggle()
        return true
    }

    private suspend fun stop(): Boolean {
        if (router.output() == PlaybackOutput.Local) {
            router.local.raw().stop()
            return true
        }
        vm()?.stop()
        return true
    }

    /** 「播放/暂停」取决于当前状态 —— 以 endpoint 的报告为准，不在本地猜。 */
    private suspend fun toggle(): Boolean {
        endpoint().toggle()
        return true
    }

    private suspend fun volumeBy(delta: Int): Boolean {
        val ep = endpoint()
        ep.setVolume(ep.snapshot().volume + delta)
        return true
    }

    private suspend fun setVolume(value: Int): Boolean {
        endpoint().setVolume(value)
        return true
    }

    private suspend fun seekTo(seconds: Long): Boolean {
        endpoint().seek(seconds)
        return true
    }

    private suspend fun mute(): Boolean {
        endpoint().setMute(true)
        return true
    }

    private suspend fun loopNext(): Boolean {
        // 循环模式只有设备侧有（本机侧 ExoPlayer 的 repeatMode 不在本轮范围）
        if (router.output() !is PlaybackOutput.Device) return false
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
