package io.github.laozhudu.linkplay.console.playback

import android.content.Context
import io.github.laozhudu.linkplay.console.LxSourceStatus
import io.github.laozhudu.linkplay.console.playback.local.LocalPlayer
import io.github.laozhudu.linkplay.console.ui.player.PlayerUiState
import io.github.laozhudu.linkplay.protocol.queue.QueueItem
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/** 声音从哪出来。 */
enum class PlaybackOutput {
    /** 投到音箱（UPnP / httpapi，现在的主力路径）。 */
    Remote,

    /** 手机上直接放（Media3 ExoPlayer）。 */
    Local,
}

/**
 * 输出选择的持久化。
 *
 * 为什么不放 SharedPreferences / DataStore：只有两个值，一个文本文件最省事，
 * 而且能直接 `cat` 出来排障。真到了要存多字段的时候再换。
 */
object PlaybackOutputStore {

    private const val FILE = "playback-output.txt"

    /** 纯函数，可单测：读到的文本 → 输出目标。**认不出来的值一律回退 Remote**。 */
    fun parse(raw: String?): PlaybackOutput =
        if (raw?.trim()?.equals("local", ignoreCase = true) == true) PlaybackOutput.Local else PlaybackOutput.Remote

    fun read(context: Context): PlaybackOutput =
        runCatching { File(context.applicationContext.filesDir, FILE).readText() }
            .getOrNull()
            .let { parse(it) }

    fun write(context: Context, output: PlaybackOutput): Boolean = runCatching {
        File(context.applicationContext.filesDir, FILE).writeText(
            if (output == PlaybackOutput.Local) "local" else "remote",
        )
        true
    }.getOrDefault(false)
}

/**
 * **播放入口的唯一分派点** —— 本地播和推流从这里选，界面不该各自判断。
 *
 * ## 为什么需要这一层
 *
 * 同一首歌，投到音箱和手机外放是两套完全不同的实现（UPnP 队列 vs ExoPlayer）。
 * 如果每个入口自己 `if (本地) ... else ...`，那么"洛雪能切、电台不能切、曲库忘了切"
 * 这种不一致一定会出现 —— 项目已经因为这个病重构过三次。
 *
 * ## 双用之后界面拿到的是同一个东西
 *
 * 两个实现都产出 [PlayerUiState]，所以 `MiniPlayerBar` 和以后的播放页都不用改。
 */
class PlaybackRouter(
    context: Context,
    /** 本地播放器进程级唯一：ExoPlayer 每 new 一个都占一份音频焦点和缓冲。 */
    private val localPlayer: LocalPlayer = LocalPlayer(context),
) {

    private val app = context.applicationContext

    val localState: StateFlow<PlayerUiState> = localPlayer.state

    fun output(): PlaybackOutput = PlaybackOutputStore.read(app)

    fun setOutput(output: PlaybackOutput): Boolean = PlaybackOutputStore.write(app, output)

    /**
     * 播一条地址。
     *
     * @return 是否真的开始播（远程侧要等设备回状态，所以这里是它的返回值）
     */
    suspend fun playOne(
        url: String,
        title: String?,
        deviceIp: String,
        location: String?,
        deviceUuid: String?,
    ): Boolean {
        return when (output()) {
            PlaybackOutput.Local -> {
                localPlayer.play(url, title)
                true
            }
            PlaybackOutput.Remote -> startQueueOnDevice(
                context = app,
                deviceIp = deviceIp,
                location = location,
                deviceUuid = deviceUuid,
                items = listOf(QueueItem(url = url, title = title ?: url)),
                startAt = 0,
                note = "单曲投送",
            )
        }
    }

    /** 切输出时要把另一头停掉，否则两边一起响。 */
    fun pauseBoth() {
        runCatching { localPlayer.pause() }
        LxSourceStatus.reset()
    }

    companion object {
        @Volatile
        private var shared: PlaybackRouter? = null

        fun of(context: Context): PlaybackRouter =
            shared ?: synchronized(this) {
                shared ?: PlaybackRouter(context).also { shared = it }
            }
    }
}
