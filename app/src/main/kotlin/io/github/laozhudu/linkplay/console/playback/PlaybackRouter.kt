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
    val local: LocalPlayer = LocalPlayer(context),
) {

    private val app = context.applicationContext

    val localState: StateFlow<PlayerUiState> = local.state

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
                // 先拉起前台服务：不启动它，通知栏与锁屏都不会出现（MediaSessionService 只有在
                // 被启动/绑定之后才持有会话）。失败不影响出声，所以吞掉。
                startLocalService()
                local.play(url, title)
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

    /**
     * 播一整份列表（NAS 目录 / 手机本地 / 在线曲库 / 歌单都走这里）。
     *
     * 为什么必须和 [playOne] 在同一个类里：**输出选择不该由各个源自己判断**。
     * 以前只有洛雪能选「本机/音箱」，因为只有它调了 router；其它源直接投音箱，
     * 于是"手机上能放的内容"只有 1/6，`PlaybackService`（通知栏/锁屏/耳机键）
     * 在另外 5 个场景里都是死的（docs/86 第一批 1.3）。
     *
     * 本地播放器目前只支持单曲：切到本机时播 `startAt` 那一首，队列不跟过去 ——
     * 这一点**必须让人知道**，不能假装"整份列表都在手机上排好了"。
     */
    suspend fun playQueue(
        items: List<QueueItem>,
        startAt: Int,
        deviceIp: String,
        location: String?,
        deviceUuid: String?,
        note: String,
        log: (String) -> Unit = {},
    ): Boolean {
        if (items.isEmpty()) return false
        return when (output()) {
            PlaybackOutput.Local -> {
                val item = items.getOrNull(startAt.coerceIn(0, items.lastIndex)) ?: items.first()
                startLocalService()
                local.play(item.url, item.title)
                log("本机播放：只播这一首（本地列表暂不支持） —— ${item.title}")
                true
            }
            PlaybackOutput.Remote -> startQueueOnDevice(
                context = app,
                deviceIp = deviceIp,
                location = location,
                deviceUuid = deviceUuid,
                items = items,
                startAt = startAt,
                note = note,
                log = log,
            )
        }
    }

    /** 拉起 MediaSession 前台服务（通知栏/锁屏才活得下去）。失败不影响出声。 */
    fun startLocalService() {
        runCatching { app.startService(android.content.Intent(app, PlaybackService::class.java)) }
    }

    /** 切输出时要把另一头停掉，否则两边一起响。 */
    fun pauseBoth() {
        runCatching { local.pause() }
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
