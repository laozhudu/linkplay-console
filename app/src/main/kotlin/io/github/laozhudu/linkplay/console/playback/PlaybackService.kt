package io.github.laozhudu.linkplay.console.playback

import androidx.media3.session.MediaSession
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
        val player = runCatching { PlaybackRouter.of(this).local.raw() }.getOrNull()
        if (player == null) {
            // 拿不到播放器就不要建空会话 —— 空会话会显示一个点了没反应的控件
            stopSelf()
            return
        }
        session = MediaSession.Builder(this, player).build()
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
}
