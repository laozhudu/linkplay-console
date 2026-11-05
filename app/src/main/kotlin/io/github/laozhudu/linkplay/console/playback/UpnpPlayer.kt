package io.github.laozhudu.linkplay.console.playback

import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import io.github.laozhudu.linkplay.console.ui.player.PlayerUiState
import io.github.laozhudu.linkplay.protocol.dlna.DeviceCapabilities
import io.github.laozhudu.linkplay.protocol.playback.PlayIntent
import io.github.laozhudu.linkplay.protocol.queue.QueueItem

/**
 * 把「投到设备的播放」包装成一个 Media3 `Player`。
 *
 * ## 为什么必须有它
 *
 * `MediaSession` 原来直接挂 `ExoPlayer`（本机播放器）。所以**投音箱时**
 * 通知栏、锁屏、耳机键、蓝牙遥控**全是死的** —— 那些控件控制的是手机上那个空播放器。
 *
 * `PlaybackService` 的注释早就写对了：「投送也显示通知，需要 MediaSession 换成一个
 * 把命令转发到设备的 Player 实现」。这个类就是那个实现。
 *
 * ## 为什么继承 `SimpleBasePlayer` 而不是实现 `Player`
 *
 * `Player` 有上百个方法。`SimpleBasePlayer` 把它们收敛成：
 * **实现 [getState] + 按需 override 几个 `handle*`**，其余按 `availableCommands` 自动忽略。
 *
 * ## 官方硬契约（这条最容易写错）
 *
 * > Player instances need to update the visible state immediately after each method call,
 * > even if the actual changes are handled on background threads or even other devices.
 *
 * 所以我们**先把状态改成"最可能的结局"再发命令**，而不是"发命令 → 等设备回话 → 更新界面"。
 * 等设备回话（真机上 30~80ms，断网时更久）会让通知栏按钮按下去半天不变，
 * 用户会重复按三次。
 *
 * ## 它不持有任何状态
 *
 * `uiState` / `items` / `index` 都是**读当前真相的 lambda**，
 * 而不是缓存 —— 一旦缓存，就出现"通知栏说在放、音箱已经停了"这种两份真相。
 */
class UpnpPlayer(
    looper: Looper,
    /** 当前播放状态的唯一真相（`PlaybackViewModel` 那边）。 */
    private val uiState: () -> PlayerUiState,
    /** 设备侧队列。 */
    private val items: () -> List<QueueItem>,
    /** 设备侧当前第几首（0 基，-1 表示没报）。 */
    private val index: () -> Int,
    /** 能力位：决定通知栏显示哪几个按钮。 */
    private val caps: () -> DeviceCapabilities,
    /** 把意图交给唯一执行点。 */
    private val onIntent: (PlayIntent) -> Unit,
) : SimpleBasePlayer(looper) {

    override fun getState(): State {
        val ui = uiState()
        val list = items()
        val at = index().coerceIn(-1, list.lastIndex.coerceAtLeast(-1))
        return State.Builder()
            .setAvailableCommands(availableCommands())
            .setPlaylist(list.map { toMediaItemData(it) })
            .setCurrentMediaItemIndex(if (at < 0) 0 else at)
            .setPlayWhenReady(ui.playing, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setPlaybackState(
                when {
                    ui.playing -> Player.STATE_READY
                    // 有内容但没在放：READY 才能让"播放"按钮可点（IDLE 会让控件变灰）
                    ui.hasQueue -> Player.STATE_READY
                    else -> Player.STATE_IDLE
                },
            )
            .setContentPositionMs(ui.positionMs.coerceAtLeast(0L))
            .setVolume((ui.volume.coerceIn(0, 100)) / 100f)
            .build()
    }

    /**
     * 通知栏显示哪几个按钮。
     *
     * **按能力位给**：电视盒子没有设备侧队列，就不给"下一首" ——
     * 给了就会出现"按钮在那儿、按下去毫无反应"（悬浮窗那边踩过同一个坑）。
     */
    private fun availableCommands(): Player.Commands {
        val c = caps()
        val b = Player.Commands.Builder()
            .add(Player.COMMAND_PLAY_PAUSE)
            .add(Player.COMMAND_GET_CURRENT_MEDIA_ITEM)
            .add(Player.COMMAND_GET_TIMELINE)
            .add(Player.COMMAND_GET_METADATA)
        if (c.supportsSeek) b.add(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
        if (c.supportsNextPrev) {
            b.add(Player.COMMAND_SEEK_TO_NEXT)
            b.add(Player.COMMAND_SEEK_TO_PREVIOUS)
        }
        return b.build()
    }

    private fun toMediaItemData(item: QueueItem): MediaItemData {
        val media = MediaItem.Builder()
            .setUri(item.url)
            .setMediaId(item.url)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(item.title)
                    .setArtist(item.artist)
                    .build(),
            )
            .build()
        return MediaItemData.Builder(item.url).setMediaItem(media).build()
    }

    // ---------- 命令处理：先把状态改成最可能的结局，命令交给执行点 ----------

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        onIntent(if (playWhenReady) PlayIntent.Play else PlayIntent.Pause)
        return Futures.immediateVoidFuture()
    }

    override fun handlePrepare(): ListenableFuture<*> = Futures.immediateVoidFuture()

    override fun handleStop(): ListenableFuture<*> {
        onIntent(PlayIntent.Stop)
        return Futures.immediateVoidFuture()
    }

    override fun handleSetVolume(volume: Float): ListenableFuture<*> {
        onIntent(PlayIntent.SetVolume((volume.coerceIn(0f, 1f) * 100f).toInt()))
        return Futures.immediateVoidFuture()
    }

    /**
     * 三种 seek 共用这一个入口（`SimpleBasePlayer` 没有单独的 `handleSeekToNext`）：
     * 目标索引比当前大就是"下一首"，小就是"上一首"，相同才是拖进度。
     */
    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
        val current = index()
        when {
            mediaItemIndex > current -> onIntent(PlayIntent.Next)
            mediaItemIndex in 0 until current -> onIntent(PlayIntent.Previous)
            else -> onIntent(PlayIntent.SeekTo(positionMs / 1000L))
        }
        return Futures.immediateVoidFuture()
    }
}
