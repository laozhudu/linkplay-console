package io.github.laozhudu.linkplay.console.playback.local

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import io.github.laozhudu.linkplay.console.ui.player.PlayerUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 本机播放 —— 直接用 Media3 的 `ExoPlayer`。
 *
 * ## 为什么是 Media3 而不是自写 MediaPlayer
 *
 * 三个理由，都不是"跟风"：
 * 1. **本地和投送要共用一个接口**（`Player`）。ExoPlayer 天然就是它；
 *    自写的封装最后还是要翻译成 Player，白写一层。
 * 2. 音频焦点（来电、别的 App 抢占）它替你管；
 * 3. 以后要通知栏/耳机键/锁屏，`MediaSession` 直接接这个实例。
 *
 * ## 这一版只做到"能响"
 *
 * 没有播放队列、没有进度上报、没有后台服务 —— 那些是下一批
 * （`docs/77` 阶段 2 的 `PlaybackService`）。先让"手机上直接放"这条路径通，
 * 再谈切输出和无缝续播。
 */
class LocalPlayer(context: Context) {

    private val player: ExoPlayer = ExoPlayer.Builder(context.applicationContext).build()

    private val _state = MutableStateFlow(PlayerUiState(source = PlayerUiState.Source.Local))
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    /** 当前曲名（Media3 的 mediaId 里存着，够用；不另建映射表）。 */
    private var currentTitle: String? = null

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) = publish()
        override fun onPlaybackStateChanged(playbackState: Int) = publish()
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = publish()
    }

    init {
        player.addListener(listener)
    }

    fun play(url: String, title: String?) {
        currentTitle = title
        player.setMediaItem(
            MediaItem.Builder()
                .setUri(url)
                .setMediaId(title ?: url)
                .build(),
        )
        player.prepare()
        player.play()
        publish()
    }

    fun pause() {
        player.pause()
        publish()
    }

    fun resume() {
        player.play()
        publish()
    }

    fun stop() {
        player.stop()
        publish()
    }

    /** 页面销毁时**不要**调 —— 播放要能继续。只在 App 退出/切换输出时调。 */
    fun release() {
        runCatching {
            player.removeListener(listener)
            player.release()
        }
    }

    /** Media3 的原始实例：接 MediaSession / 通知栏时要用。 */
    fun raw(): ExoPlayer = player

    private fun publish() {
        _state.value = PlayerUiState(
            title = currentTitle,
            playing = player.isPlaying,
            hasQueue = player.mediaItemCount > 0,
            source = PlayerUiState.Source.Local,
        )
    }
}
