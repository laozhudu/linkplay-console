package io.github.laozhudu.linkplay.console.playback

import androidx.media3.session.MediaSession
import kotlinx.coroutines.launch
import androidx.media3.session.MediaSessionService

/**
 * 播放的前台服务 —— **通知栏媒体控件、耳机键、锁屏曲名全靠它**。
 *
 * ## 为什么要单独一个服务（不能复用现有那个）
 *
 * 现有 `QueuePlaybackService` 的 `foregroundServiceType` 是 `dataSync`
 * （它跑的是"把队列推给音箱"的编排循环）。
 * Android 14+ 里**服务类型与权限一一对应**：`dataSync` 的服务显示不了媒体控件，
 * 要媒体控件就必须是 `mediaPlayback` + `FOREGROUND_SERVICE_MEDIA_PLAYBACK` 权限。
 * 两者不能混。
 *
 * ## 为什么 session 绑的是 PlaybackRouter 里那个 ExoPlayer
 *
 * 播放器实例是进程级唯一的（`PlaybackRouter.local`）。如果服务里另建一个 ExoPlayer，
 * 就会出现"通知栏显示在放，耳朵里没有声音"—— 两个播放器各放各的。
 *
 * ## 这一版只管本地播放
 *
 * 投到音箱那条路的声音不在手机上，媒体控件自然也不该出现（那会误导）。
 * 将来要做"投送也显示通知"，需要 `MediaSession` 换成一个把命令转发到设备的 Player 实现
 * （`docs/77` 的 `RemotePlayer`），那是下一阶段。
 */
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val player = runCatching { currentPlayer() }.getOrNull()
        if (player == null) {
            // 拿不到播放器就不要建空会话 —— 空会话会显示一个点了没反应的控件
            stopSelf()
            return
        }
        session = MediaSession.Builder(this, player).build()
    }

    /**
     * 按当前输出选播放器。
     *
     * - 「本机」→ 那个唯一的 ExoPlayer；
     * - 「某台设备」→ [UpnpPlayer]（把命令转发到设备）。
     *
     * ⚠️ `MediaSession` 一旦建立**不容易换 player 实例**，所以切输出后要重建 session
     * （见 [restart]）。这个取舍写在 `docs/90` S2.2 的风险登记里（R-3）。
     */
    private fun currentPlayer(): androidx.media3.common.Player? {
        val router = PlaybackRouter.of(this)
        return when (router.output()) {
            PlaybackOutput.Local -> router.local.raw()
            // 投设备：没有播放页打开过就拿不到设备状态，此时不建会话 ——
            // 空会话会显示一个点了没反应的控件，比没有更糟
            is PlaybackOutput.Device -> buildUpnpPlayer()
        }
    }

    private fun buildUpnpPlayer(): UpnpPlayer? {
        val vm = PlaybackViewModels.current ?: return null
        val scope = kotlinx.coroutines.CoroutineScope(
            kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main.immediate,
        )
        val dispatcher = PlaybackDispatcher(this)
        return UpnpPlayer(
            android.os.Looper.getMainLooper(),
            uiState = { vm.toUiState() },
            items = { QueueBus.state.value.items },
            index = { QueueBus.state.value.index },
            caps = {
                val ip = QueueBus.owner()
                if (ip == null) {
                    io.github.laozhudu.linkplay.protocol.dlna.DeviceCapabilities.LINKPLAY
                } else {
                    DeviceProfileCache.capabilitiesOf(ip)
                }
            },
            onIntent = { intent -> scope.launch { dispatcher.dispatch(intent) } },
        )
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: android.content.Intent?) {
        // 用户从最近任务里划掉 App：如果还在放就留着（音乐会断，这不礼貌）；
        // 没在放就顺手退出，别留一个空的通知。
        val player = session?.player
        if (player == null || !player.playWhenReady) {
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        session?.release()
        session = null
        super.onDestroy()
    }

    companion object {
        /**
         * 切输出后重建会话。
         *
         * `MediaSession` 建立后**不容易换 player 实例**（`docs/90` S2.2 风险 R-3），
         * 所以用"停服务再起"这个最笨但最可靠的办法：
         * 通知栏会闪一下，但不会出现"控件还在、声音没了"。
         */
        fun restart(context: android.content.Context) {
            val intent = android.content.Intent(context, PlaybackService::class.java)
            runCatching {
                context.stopService(intent)
                context.startService(intent)
            }
        }
    }
}
