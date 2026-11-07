package io.github.laozhudu.linkplay.console.playback

import android.content.Context
import io.github.laozhudu.linkplay.console.LxSourceStatus
import io.github.laozhudu.linkplay.console.playback.local.LocalPlayer
import io.github.laozhudu.linkplay.console.ui.player.PlayerUiState
import io.github.laozhudu.linkplay.protocol.queue.QueueItem
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/** 声音从哪出来。 */
sealed interface PlaybackOutput {

    /** 手机上直接放（Media3 ExoPlayer）。 */
    data object Local : PlaybackOutput

    /**
     * 投到**某一台**设备。
     *
     * ## 为什么不是「本机 / 音箱」两个
     *
     * 家里有三台 DOSS + 一台电视盒子。原来只有 `Remote` 一个值，
     * 于是"投给谁"完全由**上一个页面选中的设备**决定 —— 用户在播放页切了输出口，
     * 却不知道该投给哪台，而列表页里选中的那台才真正生效。
     *
     * 现在每台设备一个输出口：`Device("192.168.99.153", "doss3")`。
     *
     * `ip` 为空表示「跟随当前选中的设备」（老数据 `remote` 也映射到这里），
     * 这样升级前存的偏好不会丢，行为也和以前一致。
     */
    data class Device(val ip: String, val label: String = ip) : PlaybackOutput
}

/** 还没指定具体设备时跟随当前设备。 */
val PlaybackOutput_follow = PlaybackOutput.Device("")

/**
 * 输出选择的持久化。
 *
 * 为什么不放 SharedPreferences / DataStore：只有两个值，一个文本文件最省事，
 * 而且能直接 `cat` 出来排障。真到了要存多字段的时候再换。
 */
object PlaybackOutputStore {

    private const val FILE = "playback-output.txt"

    /**
     * 纯函数，可单测：读到的文本 → 输出目标。
     *
     * 认不出来的值一律回退「跟随当前设备」（= 老版本的 `remote`）——
     * **不能回退到本机**：那会让手机突然外放。
     */
    fun parse(raw: String?): PlaybackOutput {
        val v = raw?.trim().orEmpty()
        if (v.equals("local", ignoreCase = true)) return PlaybackOutput.Local
        if (v.startsWith("device:", ignoreCase = true)) {
            val ip = v.substringAfter(':').trim()
            if (ip.isNotEmpty()) return PlaybackOutput.Device(ip)
        }
        return PlaybackOutput_follow
    }

    /** [parse] 的反向。老值 `remote` 仍然写出来，回滚到旧版本时不会变成"本机"。 */
    fun format(output: PlaybackOutput): String = when (output) {
        PlaybackOutput.Local -> "local"
        is PlaybackOutput.Device -> if (output.ip.isEmpty()) "remote" else "device:${output.ip}"
    }

    /**
     * **唯一真值的广播**（`docs/96` 模型 A）。
     *
     * 文件是持久化，这个流是"当前值"。任何一处改目标，所有观察者立刻拿到，
     * 不需要谁去轮询文件。
     *
     * `null` = 还没读过（进程刚起）。用 [prime] 初始化，避免第一帧拿到 null。
     */
    private val _current = kotlinx.coroutines.flow.MutableStateFlow<PlaybackOutput?>(null)
    val current: kotlinx.coroutines.flow.StateFlow<PlaybackOutput?> = _current

    fun read(context: Context): PlaybackOutput =
        runCatching { File(context.applicationContext.filesDir, FILE).readText() }
            .getOrNull()
            .let { parse(it) }

    /** 进程启动时读一次，把文件里的值灌进流。幂等。 */
    fun prime(context: Context) {
        if (_current.value == null) _current.value = read(context)
    }

    fun write(context: Context, output: PlaybackOutput): Boolean = runCatching {
        File(context.applicationContext.filesDir, FILE).writeText(format(output))
        // 值没变时 StateFlow 不会发射（不需要手动判重），所以 open() 里再写一次也不会循环
        _current.value = output
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

    /**
     * 用户指定的目标设备地址；`null` = 跟随调用方给的那台，或在播放页选了「本机」。
     *
     * 为什么单独一个：调用方（NAS 页 / 曲库页）总会带一个 `deviceIp`（它们眼里"选中设备"就是目标），
     * 而播放页的输出口是**用户显式选的**。显式选择优先 —— 否则"我选了 doss1，声音却去了 doss3"。
     */
    fun outputIp(): String? = (output() as? PlaybackOutput.Device)?.ip?.takeIf { it.isNotEmpty() }

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
                // 本机播放也记最近 —— 否则「用手机听」这件事从不进历史，
                // 定稿「接着听」里就永远只有投给音箱的那几首。
                // deviceIp 传 null：本机不属于任何一台设备。
                runCatching {
                    io.github.laozhudu.linkplay.console.library.PlayJournal.record(
                        app,
                        io.github.laozhudu.linkplay.protocol.queue.QueueItem(url = url, title = title ?: url),
                        deviceIp = null,
                    )
                }
                true
            }
            is PlaybackOutput.Device -> startQueueOnDevice(
                context = app,
                deviceIp = outputIp() ?: deviceIp,
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
                // 整份队列交给本机播放器 —— 原来只播一首，还把"暂不支持"写进日志。
                // 那是实现缺口：ExoPlayer 本身就支持媒体项列表。
                startLocalService()
                // 队列**必须也发布到 QueueBus**：界面的播放列表读的是它。
                // 只把列表塞给 ExoPlayer 而不登记，用户会看到"在放第 1 首，
                // 但播放列表是空的"—— 这正是上一版被截图抓到的形态。
                QueueBus.begin(QueueBus.LOCAL_OWNER, items, startAt)
                local.playQueue(items.map { it.url to it.title }, startAt)
                log("本机播放：${items.size} 首，从第 ${startAt + 1} 首开始")
                true
            }
            is PlaybackOutput.Device -> startQueueOnDevice(
                context = app,
                deviceIp = outputIp() ?: deviceIp,
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
