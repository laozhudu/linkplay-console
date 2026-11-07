package io.github.laozhudu.linkplay.console.playback.local

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import io.github.laozhudu.linkplay.console.ui.player.PlayerUiState
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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

    /** 构造参数 `context` 不是 val，类内拿不到 —— 存一份给 AudioManager 用。 */
    private val appContext: Context = context.applicationContext

    /**
     * 系统媒体音量的读写口。
     *
     * ⚠️ **必须声明在 `init` 块之前**（`docs/91` 记过这个坑）：
     * Kotlin 初始化的顺序是「属性按声明顺序 → init 块」，
     * 如果这个 getter 依赖的属性排在 init 之后，init 里用到它就是 null，
     * 而且报的是 NPE 而不是"顺序错了"，极难查。`check-init-order.py` 挡的就是这个。
     */
    private val audioManager: android.media.AudioManager
        get() = appContext.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager

    private val player: ExoPlayer = ExoPlayer.Builder(context.applicationContext).build()

    private val _state = MutableStateFlow(PlayerUiState(
        // 没播过时也要反映真实音量：ExoPlayer 默认 1.0 = 100
        volume = (player.volume * 100f).toInt().coerceIn(0, 100),
source = PlayerUiState.Source.Local))
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    /** 当前曲名（Media3 的 mediaId 里存着，够用；不另建映射表）。 */
    private var currentTitle: String? = null

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) = publish()
        override fun onPlaybackStateChanged(playbackState: Int) = publish()
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = publish()
    }

    /**
     * 进度 ticker（500ms）。
     *
     * 为什么必须有：Media3 **不会**每次位置变化都回调 —— 只在状态变化时给一次。
     * 不自己轮询，进度条就停在开始那一秒不动，看起来像"卡住了"。
     *
     * 只在真的在放时才 publish，暂停时不空转。
     */
    private val ticker = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.Dispatchers.Main.immediate + kotlinx.coroutines.SupervisorJob(),
    )

    init {
        player.addListener(listener)
        ticker.launch {
            while (true) {
                delay(io.github.laozhudu.linkplay.console.util.PollCadence.LOCAL_PROGRESS_MS)
                if (player.isPlaying) publish()
            }
        }
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
            ticker.cancel()
            player.removeListener(listener)
            player.release()
        }
    }

    /** Media3 的原始实例：接 MediaSession / 通知栏时要用。 */
    fun raw(): ExoPlayer = player

    private fun publish() {
        val duration = player.duration
        _state.value = PlayerUiState(
            title = currentTitle,
            playing = player.isPlaying,
            hasQueue = player.mediaItemCount > 0,
            source = PlayerUiState.Source.Local,
            positionMs = player.currentPosition.coerceAtLeast(0L),
            // Media3 用 C.TIME_UNSET（负数）表示"还不知道"，不能直接当 0 用
            durationMs = if (duration > 0) duration else 0L,
            isLive = player.isCurrentMediaItemLive || duration <= 0,
            volume = systemVolumePercent(),
        )
    }

    /**
     * 本机音量（0..100）。
     *
     * ## 为什么单独一个（真机问题）
     *
     * 用户：「音量控制应该也与对应设备直接相关」。
     *
     * 原来播放页的音量条**无论输出是谁都调设备** —— 切到本机拖音量条，
     * 改的却是音箱的音量，手机这边的声音一点没变。
     *
     * ExoPlayer 的音量是 `0f..1f`，界面是 `0..100` —— 换算只放在这一层，
     * 和 `PlayerUiState.volume` 的读出方向对称。
     */
    fun setVolume(percent: Int) {
        // **写系统媒体音量**，不是 ExoPlayer 的 volume。
        //
        // 用户要的是一一对应：按音量键调本机、拖音量条也调本机，两者必须是同一件事。
        // 若改的是 `player.volume`（App 内缩放），按音量键变系统音量、拖条变 App 音量，
        // 就是两个旋钮，数字永远对不上。
        runCatching {
            audioManager.setStreamVolume(
                android.media.AudioManager.STREAM_MUSIC,
                percentToSystem(percent),
                0, // 不带 FLAG_SHOW_UI：我们自己的界面已经在显示这个值了
            )
        }
        publish()
    }

    /** 系统媒体音量百分比（0..100）。 */
    private fun systemVolumePercent(): Int = runCatching {
        val max = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
        if (max <= 0) 100 else (audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC) * 100 / max).coerceIn(0, 100)
    }.getOrDefault(100)

    /** 百分比 → 系统步数（四舍五入）。 */
    private fun percentToSystem(percent: Int): Int {
        val max = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
        if (max <= 0) return 0
        return Math.round(percent.coerceIn(0, 100) * max / 100f).coerceIn(0, max)
    }


}
