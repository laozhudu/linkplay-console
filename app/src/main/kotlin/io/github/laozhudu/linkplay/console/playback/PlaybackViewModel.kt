package io.github.laozhudu.linkplay.console.playback

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.laozhudu.linkplay.protocol.DeviceHttpClient
import io.github.laozhudu.linkplay.protocol.player.HttpApiPlaybackEngine
import io.github.laozhudu.linkplay.protocol.media.RelayMediaServer
import io.github.laozhudu.linkplay.protocol.media.RelayTarget
import io.github.laozhudu.linkplay.protocol.player.LoopMode
import io.github.laozhudu.linkplay.protocol.queue.QueueItem
import io.github.laozhudu.linkplay.console.session.SessionStore
import io.github.laozhudu.linkplay.protocol.metadata.TagFetcher
import io.github.laozhudu.linkplay.protocol.session.PlaybackKey
import io.github.laozhudu.linkplay.protocol.LinkPlayCommands
import io.github.laozhudu.linkplay.protocol.player.PlaybackEngine
import io.github.laozhudu.linkplay.protocol.playback.QueueRestore
import io.github.laozhudu.linkplay.protocol.player.DeviceQueueState
import io.github.laozhudu.linkplay.protocol.player.PlayerSource
import io.github.laozhudu.linkplay.protocol.player.PlayerStatus
import io.github.laozhudu.linkplay.protocol.player.PlaybackSnapshot
import io.github.laozhudu.linkplay.protocol.player.DeviceGeneration
import io.github.laozhudu.linkplay.protocol.player.UpnpPlaybackEngine
import io.github.laozhudu.linkplay.protocol.upnp.PositionInfo
import io.github.laozhudu.linkplay.protocol.upnp.TransportState
import io.github.laozhudu.linkplay.protocol.upnp.SoapFaultException
import io.github.laozhudu.linkplay.protocol.upnp.UpnpFault
import io.github.laozhudu.linkplay.protocol.upnp.UpnpRendererClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import io.github.laozhudu.diagnostics.Severity
import io.github.laozhudu.linkplay.console.diagnostics.AppDiagnostics
import io.github.laozhudu.linkplay.console.diagnostics.ActionTiming
import io.github.laozhudu.linkplay.console.diagnostics.safeLaunch

data class PlaybackUiState(
    val preparing: Boolean = true,
    val ready: Boolean = false,
    /** 两条通道都不可用时的说明（不是所有固件都支持远程控制）。 */
    val unavailableReason: String? = null,
    /** 实际选中的通道：`UPnP` 或 `HTTP API`。 */
    val channel: String? = null,
    val friendlyName: String? = null,
    val sourceLabel: String? = null,
    /** 当前输入源 id（`wifi`/`line-in`/`bluetooth`/`udisk`/`optical`），见 [PlayerSource]。 */
    val sourceId: String? = null,
    /** 设备回报的原始 mode 数字。 */
    val mode: Int? = null,
    /**
     * 设备侧列表长度（`plicount`）。>0 说明设备自己握着队列 —— 这时 App 退出也不影响播放，
     * 界面的"第几首"应该以设备为准（用户要求「列表从设备同步」）。
     */
    val deviceQueueCount: Int = 0,
    /** 设备侧当前第几首（0 基，-1 表示设备没报）。 */
    val deviceQueueIndex: Int = -1,
    val state: TransportState = TransportState.UNKNOWN,
    val title: String? = null,
    val artist: String? = null,
    val albumArtUrl: String? = null,
    val positionSec: Long = 0,
    val durationSec: Long = 0,
    val volume: Int = 0,
    val mute: Boolean = false,
    /** 循环模式。设备侧用 `setPlayerCmd:loopmode:<n>`，与 [LoopMode.wire] 一一对应。 */
    val loopMode: LoopMode = LoopMode.OFF,
    val busy: Boolean = false,
    val castUrl: String = "",
    /** 该链接是否需要我们代补 Referer/Cookie（防盗链）。 */
    val castNeedsHeaders: Boolean = false,
    val castReferer: String = "",
    /**
     * 从**音频文件里内嵌的标签**读到的封面（原始字节）。
     *
     * 为什么要这条路径：DLNA 的 `albumArtURI` 不是所有服务器都给
     * （本项目的 fnOS/MiniDLNA 就不给），但文件自己带着 APIC / covr。
     * 实测 NAS 上的 m4a 能读出 59KB 的 JPEG。
     */
    val embeddedCover: ByteArray? = null,
    /** 内嵌歌词（ID3 的 USLT / MP4 的 ©lyr / FLAC 的 LYRICS）。没有就为 null，界面不显示假歌词。 */
    val lyrics: String? = null,
    /** 已尝试过读标签的地址，避免重复拉字节。 */
    val tagsLoadedFor: String? = null,
    val error: String? = null,
    /**
     * 可执行提示（不是故障）。
     *
     * 例如"这台音箱还没加载媒体，先去选一首" —— 用红色报错去说这句话，
     * 用户会以为 App 坏了。二者分开：[error] 红、[notice] 中性。
     */
    val notice: String? = null,
    val log: List<String> = emptyList(),
) {
    val progress: Float
        get() = if (durationSec <= 0) 0f else (positionSec.toFloat() / durationSec.toFloat()).coerceIn(0f, 1f)
}

/**
 * 播放控制。
 *
 * 通道选择顺序：**UPnP 优先，httpapi 兜底**。
 * - UPnP 状态字段规范、能投任意 URL，是首选；
 * - 有些老固件/精简固件没开 DLNA 渲染器，但支持 WiiM 官方的 `setPlayerCmd:*`，
 *   这时退到 httpapi（能力略窄：没有播放列表元数据，直播流无进度）。
 * 两条都探不通时明确告知用户，而不是给一个点不动的界面。
 */
class PlaybackViewModel(
    /**
     * 当前设备。**刻意不是 val**：一台 App 只应有一个 PlaybackViewModel，
     * 换设备走 [switchTo] 原地切换。
     *
     * 为什么（真 bug）：以前 PlayerScreen 用 `viewModel(key = "player-$deviceIp")`，
     * 换设备会**新建**一个 VM，但旧 VM 留在 Activity 的 ViewModelStore 里、
     * 它的轮询循环照旧每秒往 VolumeBus / QueueBus / 快照里写**上一台设备**的数据。
     * 表现就是「切到另一台后先是报错/串台，等一会儿又自己好了」
     * —— 其实是旧设备那次响应被新设备盖过去了。用户实测反馈过这个现象。
     */
    private var deviceIp: String,
    private var location: String?,
    /** 设备 uuid：播放快照按它存盘，换 IP 也能找回上次的队列。 */
    private var deviceUuid: String? = null,
    /** 会话仓库。传 null 表示"不恢复上次状态"（预览/测试用）。 */
    private val sessionStore: SessionStore? = null,
    /** 应用上下文：只用于在换设备时停掉绑定旧设备的投流服务。 */
    private val appContext: android.content.Context? = null,
) : ViewModel() {

    private val _state = MutableStateFlow(PlaybackUiState())
    val state: StateFlow<PlaybackUiState> = _state.asStateFlow()

    private var engine: PlaybackEngine? = null

    /** 直接的 httpapi 客户端：切换输入源这类命令不属于"播放通道"，不该塞进 PlaybackEngine。 */
    private val device = DeviceHttpClient()

    /**
     * 代际号：每次 [switchTo] +1。
     *
     * 为什么需要：切设备的瞬间可能还有上一次请求在飞（读描述、快照、队列），
     * 它们回来时不能把结果写进新设备的状态。所有异步写状态前都比一下代际号，
     * 不一致直接丢弃 —— 这比"取消协程"更可靠，因为 HTTP 请求可能刚好在取消前返回。
     */
    private val generation = DeviceGeneration()
    private var pollJob: kotlinx.coroutines.Job? = null

    /**
     * 同一台设备的 httpapi 客户端。
     *
     * 为什么单独留一个：上一首/下一首/循环模式这几条命令**只在 httpapi 上有**
     * （UPnP 的服务表里没有 Next/Previous 之外的循环控制，私有 PlayQueue 又要另建队列），
     * 而它们都是 80 端口、与当前播放通道无关。混进 PlaybackEngine 会污染那个接口的语义。
     */
    private val http = DeviceHttpClient()

    /**
     * 转发服务：只在「链接需要防盗链头」时才启动。
     *
     * 为什么必须由我们转发：实测设备拉流只发 `curl/7.36.0` 的裸 GET，没有 Referer/Cookie
     * （docs/16 §2），带防盗链的直链设备自己拉必然 403。
     */
    private var relay: RelayMediaServer? = null

    /**
     * 切换到另一台设备（原地切换，不新建 ViewModel）。
     *
     * 做的事：代际号 +1（作废所有在飞请求）→ 停轮询 → 停转发服务 → 丢引擎 →
     * 状态清成"准备中"（**不保留上一台的曲名/音量/队列，否则界面会短暂显示错的内容**）
     * → 重新建通道 → 重新从新设备读回状态与列表。
     */
    fun switchTo(ip: String, newLocation: String?, uuid: String?) {
        // 同一台设备、且描述地址没有从 null 补成真实值 → 什么都不做
        // （首次组合时 LaunchedEffect 一定会调到这里，不能白白重连一次）
        if (ip == deviceIp && (newLocation == null || newLocation == location)) return

        generation.bump()
        pollJob?.cancel()
        pollJob = null
        relay?.stop()
        relay = null
        engine = null
        deviceIp = ip
        location = newLocation
        deviceUuid = uuid

        // 队列**按设备归属**：换了设备就把上一台的列表整份丢掉，
        // 否则播放页会显示「doss1 … 设备列表 122 首 · 第 3 首」这种别人的数据（用户实测截图）
        //
        // 同时停掉客户端投流服务：它绑定的是**一台设备**的地址，
        // 留着它会让旧设备继续被我们逐首投流，而界面上已经是新设备 —— 两边对不上。
        appContext?.let { ctx -> runCatching { QueuePlaybackService.stop(ctx) } }
        QueueBus.setOwner(ip)

        // 只保留用户正在输入的投流参数；其余全清（尤其是 error 与设备队列数字）
        _state.update {
            PlaybackUiState(
                preparing = true,
                castUrl = it.castUrl,
                castNeedsHeaders = it.castNeedsHeaders,
                castReferer = it.castReferer,
                log = it.log + "切换到设备 $ip",
            )
        }
        VolumeBus.forget(ip)

        safeLaunch("switchTo") {
            prepare()
            restoreLastSession()
        }
    }

    private suspend fun prepare() {
        val gen = generation.current()
        val notes = mutableListOf<String>()
        val location = location
        val deviceIp = deviceIp

        if (!location.isNullOrBlank()) {
            try {
                val description = withContext(Dispatchers.IO) { UpnpRendererClient.describe(location) }
                // 读描述的这段时间里用户可能已经切走 —— 那就别再往新设备的状态里写旧设备的信息
                if (generation.isStale(gen)) return
                if (description.avTransportControlUrl != null) {
                    engine = withContext(Dispatchers.IO) { UpnpPlaybackEngine(UpnpRendererClient(description)) }
                    _state.update {
                        it.copy(
                            preparing = false,
                            ready = true,
                            channel = engine!!.channelName,
                            friendlyName = description.friendlyName,
                        )
                    }
                    log("通道：UPnP（${description.friendlyName ?: deviceIp}）")
                    refreshOnce(silent = true)
                    startPolling()
                    return
                }
                notes += "设备未声明 AVTransport 服务"
            } catch (t: Throwable) {
                notes += "读取 UPnP 描述失败（${t.message}）"
            }
        } else {
            notes += "没有 UPnP 描述地址（手动输入 IP 时设备不会告知）"
        }

        // 兜底：httpapi 播放控制
        val fallback = HttpApiPlaybackEngine(DeviceHttpClient(), deviceIp)
        val probed = withContext(Dispatchers.IO) { fallback.probe() }
        if (generation.isStale(gen)) return
        if (probed) {
            engine = fallback
            _state.update {
                it.copy(preparing = false, ready = true, channel = fallback.channelName)
            }
            log("通道：HTTP API（${notes.joinToString("；")}）")
            refreshOnce(silent = true)
            startPolling()
            return
        }

        _state.update {
            it.copy(
                preparing = false,
                unavailableReason = notes.joinToString("；") +
                    "；httpapi 的 getPlayerStatus 也不可用。" +
                    "这台设备的固件可能只允许它自己的 App 控制。",
            )
        }
    }

    private fun startPolling() {
        pollJob?.cancel()
        val gen = generation.current()
        pollJob = safeLaunch("startPolling") {
            while (isActive) {
                delay(POLL_INTERVAL_MS)
                if (generation.isStale(gen)) return@safeLaunch
                refreshOnce(silent = true)
            }
        }
    }

    /** 读设备侧队列现状（`plicount`/`plicurr`）。读不到返回 null，不影响主状态。 */
    private suspend fun readDeviceQueue(): DeviceQueueState? = withContext(Dispatchers.IO) {
        runCatching {
            DeviceQueueState.from(
                PlayerStatus.parse(device.send(deviceIp, LinkPlayCommands.getPlayerStatus())),
            )
        }.getOrNull()
    }

    fun refresh() {
        safeLaunch("refresh") { refreshOnce() }
    }

    private suspend fun refreshOnce(silent: Boolean = false) {
        val e = engine ?: return
        val gen = generation.current()
        if (!silent) _state.update { it.copy(busy = true, error = null) }
        try {
            val snapshot: PlaybackSnapshot = withContext(Dispatchers.IO) { e.snapshot() }
            // 请求在飞的过程中用户切了设备 → 这份快照属于上一台，丢弃
            if (generation.isStale(gen)) return
            // 设备侧的真实音量同步给全站（首页多房间卡片也订阅它）
            VolumeBus.publish(deviceIp, snapshot.volume, snapshot.mute)
            // 设备侧队列位置（1 基转 0 基在 DeviceQueueState 里做）同步给列表高亮
            readDeviceQueue()?.let { q ->
                if (q.hasQueue) QueueBus.syncDeviceIndex(q.index)
                _state.update { it.copy(deviceQueueCount = q.count, deviceQueueIndex = q.index) }
            }
            _state.update {
                it.copy(
                    busy = false,
                    state = snapshot.state,
                    title = snapshot.title ?: it.title,
                    artist = snapshot.artist,
                    albumArtUrl = snapshot.albumArtUrl,
                    positionSec = snapshot.positionSec,
                    durationSec = snapshot.durationSec,
                    volume = snapshot.volume,
                    mute = snapshot.mute,
                    sourceLabel = snapshot.sourceLabel,
                    sourceId = snapshot.sourceId,
                    mode = snapshot.mode,
                    error = null,
                )
            }
        } catch (t: Throwable) {
            _state.update { it.copy(busy = false, error = if (silent) it.error else t.message) }
        }
    }

    /**
     * 设备侧状态 → 界面唯一认识的 [PlayerUiState]。
     *
     * 给 `UpnpPlayer`（通知栏）和界面共用：两边读同一份真相，
     * 不会出现"通知栏说在放、播放页说暂停"。
     */
    fun toUiState(): io.github.laozhudu.linkplay.console.ui.player.PlayerUiState {
        val s = _state.value
        return io.github.laozhudu.linkplay.console.ui.player.PlayerUiState(
            title = s.title,
            playing = s.state == TransportState.PLAYING,
            hasQueue = s.deviceQueueCount > 0,
            positionMs = s.positionSec * 1000L,
            durationMs = s.durationSec * 1000L,
            isLive = s.durationSec <= 0,
            volume = s.volume,
        )
    }

    fun setCastUrl(value: String) = _state.update { it.copy(castUrl = value) }

    fun setCastNeedsHeaders(v: Boolean) = _state.update { it.copy(castNeedsHeaders = v) }

    fun setCastReferer(v: String) = _state.update { it.copy(castReferer = v) }

    /**
     * 投流。若开启了「需要防盗链头」，先把链接登记到本机转发服务，再把转发地址给音箱。
     *
     * 关键在于**给音箱的必须是本机地址**：音箱只会裸 GET，它没法替自己补 Referer。
     */
    fun cast() {
        val raw = _state.value.castUrl.trim()
        if (raw.isEmpty()) {
            _state.update { it.copy(error = "请先填一条音频 URL") }
            return
        }
        val needsHeaders = _state.value.castNeedsHeaders
        val referer = _state.value.castReferer.trim()
        act("投流", ActionTiming.Action.CAST_STREAM) {
            val target = if (needsHeaders) {
                val host = localIpAddress() ?: error("拿不到本机局域网 IP，无法转发")
                val server = relay ?: RelayMediaServer(advertiseHost = host).also {
                    it.start()
                    relay = it
                }
                val headers = buildMap {
                    if (referer.isNotEmpty()) {
                        put("Referer", referer)
                        // 不少平台还校验 Origin，一并补上（缺它同样 403）
                        put("Origin", referer.substringBefore('/', "https://").let { scheme ->
                            referer.substringBefore("://") + "://" + referer.substringAfter("://").substringBefore('/')
                        })
                    }
                }
                server.urlFor(RelayTarget(raw, headers), host, extensionOf(raw))
            } else {
                raw
            }
            engine!!.playUrl(target, null)
            // 投进来的一首也要进队列 —— 否则播放页的列表永远是空的（用户实际遇到的就是这个）
            QueueBus.begin(
                deviceIp,
                listOf(
                    QueueItem(
                        url = target,
                        title = raw.substringAfterLast('/').ifBlank { "投流音频" },
                        mimeType = "audio/mpeg",
                    ),
                ),
            )
            QueueBus.playing(deviceIp, raw.substringAfterLast('/').ifBlank { "投流音频" }, 0)
            log(if (needsHeaders) "已转发投流：$target" else "已投流：$target")
        }
    }

    /**
     * 把播放状态注册进诊断页。
     *
     * 为什么在这里而不是界面里读：`PlaybackUiState` 是私有的 StateFlow，
     * 界面读得到的是"渲染后"的样子；诊断要的是引擎通道、设备队列位置、
     * 起播失败原因这些原始值 —— 它们只在 ViewModel 里。
     *
     * 注销在 [onCleared] 里做，见 [AppDiagnostics.sections] 的类注释（切设备会重建实例）。
     */
    private fun registerDiagnostics() {
        AppDiagnostics.sections.register(
            "playback",
            "播放",
            {
                val failed = relay?.stats()?.failed ?: 0
                if (_state.value.unavailableReason != null || _state.value.notice != null || failed > 0) {
                    Severity.WARN
                } else {
                    Severity.INFO
                }
            },
        ) {
            val s = _state.value
            buildString {
                appendLine("  通道=${s.channel ?: "未建立"}  就绪=${s.ready}  准备中=${s.preparing}")
                s.unavailableReason?.let { appendLine("  不可用原因=$it") }
                appendLine("  输入源=${s.sourceLabel ?: "-"}（id=${s.sourceId ?: "-"}, mode=${s.mode ?: "-"}）")
                appendLine("  传输态=${s.state}  曲目=${s.title ?: "-"}")
                appendLine("  位置=${s.positionSec}s/${s.durationSec}s  音量=${s.volume}  静音=${s.mute}  循环=${s.loopMode}")
                appendLine("  设备队列=${s.deviceQueueCount} 首 · 第 ${s.deviceQueueIndex + 1} 首")
                // 中继统计：在线电台/DLNA 投流都走它。没有这几行，
                // 「没声音」分不清是设备没来拉、上游 502、还是拉到一半断了。
                relay?.let { r ->
                    val st = r.stats()
                    appendLine(
                        "  中继=${r.baseUrl()} 请求=${st.requests} 完成=${st.completed}" +
                            " 中断=${st.aborted} 失败=${st.failed}",
                    )
                    if (st.aborted > 0) appendLine("    最近中断=${st.lastAbortNote}")
                    if (st.failed > 0) appendLine("    最近失败=${st.lastFailedNote}")
                } ?: appendLine("  中继=未启动")
                appendLine("  封面=${if (s.embeddedCover != null) "${s.embeddedCover!!.size}B（内嵌）" else (s.albumArtUrl ?: "无")}")
                appendLine("  歌词=${if (s.lyrics.isNullOrBlank()) "无" else "${s.lyrics!!.length} 字"}")
                s.notice?.let { appendLine("  提示=$it") }
            }
        }
    }

    /** 转发服务占着端口，页面销毁时要还回去。 */
    override fun onCleared() {
        // 必须注销：切设备会重建本 VM，旧实例留在注册表里会显示上一台的状态
        AppDiagnostics.sections.unregister("playback")
        relay?.stop()
        relay = null
        super.onCleared()
    }

    private fun localIpAddress(): String? = runCatching {
        java.net.NetworkInterface.getNetworkInterfaces().toList()
            .asSequence()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList().asSequence() }
            .filterIsInstance<java.net.Inet4Address>()
            .firstOrNull { !it.isLoopbackAddress }
            ?.hostAddress
    }.getOrNull()

    private fun extensionOf(url: String): String {
        val ext = url.substringBefore('?').substringAfterLast('/', "")
            .substringAfterLast('.', "").lowercase()
        return ext.takeIf { it in setOf("mp3", "m4a", "aac", "flac", "ogg", "wav") } ?: "mp3"
    }

    /**
     * 播放。
     *
     * ## 为什么要专门处理 704
     *
     * 真机实测（`.164`，`GetMediaInfo` 的 `PlayMedium=UNKNOWN`）：设备**没有加载媒体**时
     * `AVTransport.Play` 回 `500 / 704 play not allowed`。原来我们把这个原始报文
     * 直接怼到界面上（用户看到的红字是 `SOAP 500 for "...AVTransport:1#Play"`）。
     * 原厂从不会碰到这条 —— 它点播放前一定先下发过 URI/队列。
     *
     * 这里的顺序与原厂一致：
     * 1. 设备自己握着队列（`plicount>0`）→ 先 `AVTransport.Play`，失败再试 httpapi `play`；
     * 2. 设备没有队列、本地队列有内容 → 先把队列交给设备，再播；
     * 3. 都没有 → 明确提示去选歌（不是"故障"）。
     */
    fun play() {
        if (_state.value.busy) return
        val e = engine ?: run { httpAct("播放", "setPlayerCmd:play"); return }
        _state.update { it.copy(busy = true, error = null) }
        safeLaunch("play") {
            val gen = generation.current()
            val hasDeviceQueue = _state.value.deviceQueueCount > 0
            val localItems = QueueBus.state.value.items

            // ③ 什么都没有：这不是错误，是"该选歌了"
            if (!hasDeviceQueue && localItems.isEmpty()) {
                _state.update {
                    it.copy(busy = false, error = null, notice = UpnpFault.message(UpnpFault.NO_MEDIA))
                }
                log("播放：设备没有队列，本地队列也空")
                return@safeLaunch
            }

            // ② 本地有队列但设备没有：把队列交给设备（原厂点"全部播放"就是这个动作）
            //
            // 走唯一入口 startQueueOnDevice（docs/86 不变量 I1：一个动作只有一条实现）。
            // 以前这里直连 DevicePlaylistGateway.push，于是这条路：
            //   ① 绕过了能力位判断 —— 投通用 DLNA 渲染器（电视盒子）会白撞一次 401 超时；
            //   ② 绕过了二段/三段降级 —— PlayQueue 拒了就放弃，不会退 m3u 再退客户端逐首；
            //   ③ 绕过了「最近播放」记账 —— 只做了 markDeviceOwned。
            if (!hasDeviceQueue && localItems.isNotEmpty()) {
                val ctx = appContext
                if (ctx == null) {
                    log("播放：本地有 ${localItems.size} 首，但拿不到 Context，没法交给设备")
                } else {
                    val pushed = withContext(Dispatchers.IO) {
                        runCatching {
                            startQueueOnDevice(
                                context = ctx,
                                deviceIp = deviceIp,
                                location = location,
                                deviceUuid = deviceUuid,
                                items = localItems,
                                startAt = 0,
                                note = "全部播放",
                                log = ::log,
                            )
                        }.getOrDefault(false)
                    }
                    if (pushed) {
                        if (generation.isStale(gen)) return@safeLaunch
                        _state.update { it.copy(busy = false, error = null) }
                        // 「最近播放」与 owner 由 startQueueOnDevice 内部记（docs/88 坑 3：别记两次）
                        refreshOnce(silent = true)
                        return@safeLaunch
                    }
                }
            }

            // ① 设备有队列：直接播；Play 被拒就换 httpapi 再试一次
            val failure = withContext(Dispatchers.IO) { runCatching { e.resume() }.exceptionOrNull() }
            if (generation.isStale(gen)) return@safeLaunch
            if (failure == null) {
                _state.update { it.copy(busy = false, error = null, notice = null) }
                log("播放 ok")
                refreshOnce(silent = true)
                return@safeLaunch
            }

            val upnpCode = (failure as? SoapFaultException)?.upnpCode
            if (UpnpFault.isRetryableOnAnotherChannel(upnpCode)) {
                val retry = withContext(Dispatchers.IO) {
                    runCatching { device.send(deviceIp, "setPlayerCmd:play") }.getOrNull()
                }
                log("AVTransport.Play 被拒（UPnP $upnpCode），httpapi 兜底 → ${retry ?: "失败"}")
                if (retry != null) {
                    _state.update { it.copy(busy = false, error = null) }
                    refreshOnce(silent = true)
                    return@safeLaunch
                }
            }
            // 翻译成人话再显示；原始报文只进日志
            _state.update { it.copy(busy = false, error = UpnpFault.friendlyOf(failure)) }
            log("播放失败：${failure.message}")
        }
    }

    /**
     * 下一首：走播放引擎（UPnP 的 AVTransport.Next，原厂做法）。
     *
     * 以前这里是 `httpAct("下一首", "setPlayerCmd:next")` —— 那是自研的组合，
     * 在老固件上与设备侧 PlayQueue 不同步（只挪当前曲的索引，不动队列）。
     * 引擎不可用时才退回 httpapi。
     */
    fun next() {
        val e = engine
        if (e == null) { httpAct("下一首", "setPlayerCmd:next"); return }
        act("下一首", ActionTiming.Action.NEXT_TRACK) { e.next() }
    }

    /** 上一首：同上，走 AVTransport.Previous。 */
    fun previous() {
        val e = engine
        if (e == null) { httpAct("上一首", "setPlayerCmd:prev"); return }
        act("上一首", ActionTiming.Action.PREV_TRACK) { e.previous() }
    }

    /**
     * 切循环模式。先改本地状态再下发：设备回读 `loop` 有延迟，
     * 等回读再更新会让按钮「点了不动」，手感很差。
     */
    fun cycleLoopMode() {
        val nextMode = LoopMode.next(_state.value.loopMode)
        _state.update { it.copy(loopMode = nextMode) }
        httpAct("循环 ${nextMode.label}", "setPlayerCmd:loopmode:${nextMode.wire}")
    }

    private fun httpAct(label: String, command: String) {
        if (engine == null) return
        _state.update { it.copy(busy = true, error = null) }
        safeLaunch("http:$label") {
            val failure = runCatching {
                withContext(Dispatchers.IO) { http.send(deviceIp, command) }
            }.exceptionOrNull()
            if (failure != null) {
                _state.update { it.copy(busy = false, error = failure.message) }
                log("$label 失败：${failure.message}")
            } else {
                _state.update { it.copy(busy = false) }
                log("$label ok")
                refreshOnce(silent = true)
            }
        }
    }
    fun pause() = act("暂停", ActionTiming.Action.PAUSE) { engine!!.pause() }
    fun stop() = act("停止", ActionTiming.Action.STOP_PLAYBACK) { engine!!.stop() }
    fun seekTo(seconds: Long) = act("跳转", ActionTiming.Action.SEEK) { engine!!.seek(seconds) }
    fun setVolume(value: Int) = act("音量 ${value.coerceIn(0, 100)}", ActionTiming.Action.SET_VOLUME) { engine!!.setVolume(value) }

    /**
     * 拖动音量条时用的「跟手」版本。
     *
     * 为什么不能用 [setVolume]：
     * 1. 它走 [act]，那里有 `busy` 闸门 —— 拖动时上一个请求还没回来，后面几十个全被丢掉，
     *    表现就是「拖了半天音量不动，松手才跳一下」；
     * 2. 每次成功都 `refreshOnce()` + 写一条日志，几十次会把状态刷成毛刺。
     *
     * 这里做两件事：先把本地音量乐观改掉（数字立刻跟手），再把值丢进合并通道，
     * 后台每 80ms 取最新值下发一次 —— 请求量可控，手感连续。
     */
    fun setVolumeLive(value: Int) {
        val v = value.coerceIn(0, 100)
        _state.update { it.copy(volume = v) }
        // 立刻广播给全站：首页的音量条同一帧就跟上，不用等设备回读
        VolumeBus.publishVolume(deviceIp, v)
        volumeChannel.trySend(v)
    }

    private val volumeChannel = Channel<Int>(Channel.CONFLATED)

    /**
     * 播放列表里点某一首。
     *
     * 服务在跑就让它跳过去（它负责逐首投流）；服务不在跑就退化成「直接投这一首」——
     * 单曲投流是本项目最可靠的能力，比什么都不发生强。
     */
    fun playQueueItem(index: Int) {
        val q = QueueBus.state.value
        val item = q.items.getOrNull(index) ?: return
        // 占位列表（设备有队列但本机没有曲目单）：只能按设备的方式跳位，不能投 device:// 这种假地址
        if (QueueRestore.isPlaceholder(item)) {
            val delta = index - q.index
            if (delta == 0) return
            safeLaunch("playQueueItem") {
                repeat(kotlin.math.abs(delta)) {
                    val cmd = if (delta > 0) "setPlayerCmd:next" else "setPlayerCmd:prev"
                    withContext(Dispatchers.IO) { runCatching { device.send(deviceIp, cmd) } }
                    delay(250)
                }
                QueueBus.syncDeviceIndex(index)
                refreshOnce(silent = true)
            }
            return
        }
        // 设备握着列表时用设备的跳位（原厂 PlayQueueWithIndex），这样设备侧进度才是真的
        if (q.deviceOwned) {
            safeLaunch("playQueueItem") {
                val ok = DevicePlaylistGateway.playIndex(deviceIp, location, index)
                if (ok) {
                    QueueBus.syncDeviceIndex(index)
                    refreshOnce(silent = true)
                } else {
                    _state.update { it.copy(error = "设备没有接受跳转（第 ${index + 1} 首）") }
                }
            }
            return
        }
        if (QueueBus.hasController) {
            QueueBus.skipTo(index)
            return
        }
        act("播放第 ${index + 1} 首", ActionTiming.Action.PLAY_ITEM) { engine!!.playUrl(item.url, item.title) }
    }

    /**
     * 读这首曲子文件里内嵌的封面与歌词。
     *
     * 只在「还没有封面」或「还没试过这个地址」时才发请求：
     * 用 HTTP Range 读开头 256KB（ID3/FLAC），若是 MP4 再读尾部 1MB（moov 通常在末尾）。
     * 一首 13MB 的歌总共只取几百 KB，不会拖慢投流。
     */
    fun loadEmbeddedTags(url: String?) {
        if (url.isNullOrBlank()) return
        val st = _state.value
        if (st.tagsLoadedFor == url) return
        _state.update { it.copy(tagsLoadedFor = url) }
        safeLaunch("loadEmbeddedTags") {
            val tags = withContext(Dispatchers.IO) {
                runCatching { TagFetcher.fetch(url) }.getOrNull()
            }
            if (tags == null) return@safeLaunch
            _state.update {
                it.copy(
                    embeddedCover = tags.cover ?: it.embeddedCover,
                    lyrics = tags.lyrics ?: it.lyrics,
                    // 设备不回填元数据时，用文件里的标题/歌手补上（比显示 unknown 有信息量）
                    title = it.title ?: tags.title,
                    artist = it.artist ?: tags.artist,
                )
            }
            log("内嵌标签：封面 ${tags.cover?.size ?: 0}B，歌词 ${tags.lyrics?.length ?: 0} 字")
        }
    }

    /** 从播放列表里删掉第 [index] 首。 */
    fun removeQueueItem(index: Int) = QueueBus.removeAt(index)
    fun toggleMute() {
        val next = !_state.value.mute
        _state.update { it.copy(mute = next) }
        VolumeBus.publish(deviceIp, _state.value.volume, next)
        act("静音切换", ActionTiming.Action.MUTE_TOGGLE) { engine!!.setMute(next) }
    }

    private fun act(label: String, actionName: ActionTiming.Action? = null, block: () -> Unit) {
        if (_state.value.busy || engine == null) return
        _state.update { it.copy(busy = true, error = null) }
        safeLaunch(label) {
            // 计时从这条协程真正开始跑算起（而不是用户点下去那一刻）：
            // 前者是"我们多久办完"，后者还要算上排队，两者混在一起就分不清是卡在排队还是卡在网络。
            val timing = actionName?.let { ActionTiming.begin(it) }
            val failure = runCatching { withContext(Dispatchers.IO) { block() } }.exceptionOrNull()
            if (failure != null) {
                // 界面只显示翻译过的一句话；原始 SOAP 报文只进日志（用户看不懂也不该看懂）
                _state.update { it.copy(busy = false, error = UpnpFault.friendlyOf(failure)) }
                log("$label 失败：${failure.message}")
                timing?.end(note = label, ok = false)
            } else {
                _state.update { it.copy(busy = false) }
                log("$label ok")
                // 先收计时：后面的静默刷新是异步的，算进去会把"多久办完"污染成"多久刷完"
                timing?.end(note = label)
                refreshOnce(silent = true)
            }
        }
    }

    private fun log(line: String) = _state.update { it.copy(log = (it.log + line).takeLast(40)) }

    /**
     * 恢复上次的设备播放状态。
     *
     * 两路信息合起来看：
     * 1. **设备侧**（`getPlayerStatus`）：真的在放什么、音量多少、输入源、进度。这是权威；
     * 2. **本地快照**（`SessionStore.loadPlayback`）：队列列表与上次播到第几首。
     *
     * 为什么不干脆由设备给列表：本固件不支持整列表推送（docs/10），
     * 设备只知道"当前这一首"，队列只存在于我们这边，所以必须两边合起来才完整。
     */
    private suspend fun restoreLastSession() {
        val store = sessionStore ?: return
        // 先等 prepare() 把通道建好；最多等 4 秒，避免通道彻底不可用时一直挂着
        var waited = 0L
        while (engine == null && _state.value.preparing && waited < 4_000) {
            delay(200); waited += 200
        }

        // 1) 设备侧现状
        val snapshot = withContext(Dispatchers.IO) { runCatching { engine?.snapshot() }.getOrNull() }
        if (snapshot != null) {
            _state.update {
                it.copy(
                    state = snapshot.state,
                    volume = snapshot.volume,
                    mute = snapshot.mute,
                    positionSec = snapshot.positionSec,
                    durationSec = snapshot.durationSec,
                    title = snapshot.title ?: it.title,
                    artist = snapshot.artist,
                    albumArtUrl = snapshot.albumArtUrl,
                    sourceLabel = snapshot.sourceLabel,
                    sourceId = snapshot.sourceId,
                    mode = snapshot.mode,
                )
            }
            VolumeBus.publish(deviceIp, snapshot.volume, snapshot.mute)
            // 设备侧队列位置（1 基转 0 基在 DeviceQueueState 里做）同步给列表高亮
            readDeviceQueue()?.let { q ->
                if (q.hasQueue) QueueBus.syncDeviceIndex(q.index)
                _state.update { it.copy(deviceQueueCount = q.count, deviceQueueIndex = q.index) }
            }
            log("恢复设备状态：${snapshot.state} vol=${snapshot.volume} src=${snapshot.sourceLabel ?: "-"}")
        }

        // 2) 队列：优先把设备上的整份列表读回来（原厂 BrowseQueue），读不到再用本地快照
        val gen = generation.current()
        val deviceList = DevicePlaylistGateway.read(deviceIp, location)
        if (generation.isStale(gen)) return
        if (deviceList != null && !deviceList.isEmpty) {
            val items = DevicePlaylistGateway.toQueueItems(deviceList)
            val idx = deviceList.currentIndex.coerceIn(0, items.lastIndex)
            QueueBus.restore(
                deviceIp = deviceIp,
                items = items,
                index = idx,
                title = items.getOrNull(idx)?.title,
                deviceOwned = true,
                note = "设备列表 ${items.size} 首 · 第 ${idx + 1} 首",
            )
            log("从设备读回播放列表：${items.size} 首")
            return
        }

        // 3) 设备没有队列：退回「设备真值 + 本地快照」的判定
        //    （这里不再自己写一套恢复逻辑——两套实现必然漂移，这也是"重开列表不同步"的老根因）
        val savedSession = withContext(Dispatchers.IO) {
            PlaybackKey.candidates(deviceUuid, deviceIp).firstNotNullOfOrNull { key ->
                runCatching { store.loadPlayback(key) }.getOrNull()
            }
        }
        val deviceQueue = readDeviceQueue()
        QueueRestore.plan(savedSession, deviceQueue)?.let { plan ->
            QueueBus.restore(
                deviceIp = deviceIp,
                items = plan.items,
                index = plan.index,
                title = plan.items.getOrNull(plan.index)?.title,
                deviceOwned = plan.fromDevice,
                offset = savedSession?.deviceOffset ?: 0,
                note = plan.note,
            )
            log("恢复播放列表：${plan.items.size} 首 · ${plan.note}")
        }
    }

    /** 换曲时清掉上一首的封面/歌词，避免新歌配着旧封面。 */
    /**
     * 切换输入源。
     *
     * 真机教训：切到非 Wi-Fi 源之后，设备的 httpapi **通常仍然可达**（还在同一 Wi-Fi 上），
     * 但音频路径换掉了；如果设备把 Wi-Fi 也断了（部分固件的蓝牙模式会），
     * 手机就控制不到它，只能到机身上按回去。所以 UI 上对这些源必须明确提示风险。
     *
     * 切完立刻回读 `mode`，以设备回报为准 —— 命令回 OK 不代表真的切了（这条在本项目反复出现）。
     */
    fun switchSource(sourceId: String) {
        val src = PlayerSource.byId(sourceId) ?: return
        safeLaunch("switchSource") {
            _state.update { it.copy(busy = true, error = null) }
            val root = runCatching {
                withContext(Dispatchers.IO) { device.send(deviceIp, LinkPlayCommands.switchMode(src.id)) }
            }.getOrElse { t ->
                _state.update { it.copy(busy = false, error = "切换失败：${t.message}") }
                return@safeLaunch
            }
            // 给设备 1.5 秒完成切换，再回读真实 mode
            delay(1500)
            refreshOnce(silent = true)
            val now = _state.value.mode
            val matched = PlayerSource.currentIdOf(now) == src.id
            _state.update {
                it.copy(
                    busy = false,
                    error = if (matched) null else "设备回报的输入源是「${PlayerSource.describe(now)}」，没切到「${src.label}」（命令返回 $root）",
                )
            }
            log("switchmode:${src.id} → $root，回读 mode=$now")
        }
    }

    fun clearEmbeddedTags() = _state.update {
        it.copy(embeddedCover = null, lyrics = null, tagsLoadedFor = null)
    }

    init {
        // 重开 App 时恢复：设备侧还在放的那首歌 + 本地快照里的队列。
        // 这一条是用户明确要求的「原厂退出后重开能读到原来的状态包括列表」。
        safeLaunch("clearEmbeddedTags") {
            restoreLastSession()
        }
        // 音量合并通道：只保留最新值，每 80ms 最多下发一次
        safeLaunch("clearEmbeddedTags") {
            for (v in volumeChannel) {
                runCatching { withContext(Dispatchers.IO) { engine?.setVolume(v) } }
                delay(80)
            }
        }
    }

    companion object {
        /** 播放页刷新节奏。**定义在 `PollCadence`** —— 所有周期在一处，调手感只改那里。 */
        val POLL_INTERVAL_MS: Long = io.github.laozhudu.linkplay.console.util.PollCadence.PLAYER_SCREEN_MS

        fun format(seconds: Long): String = PositionInfo.formatTime(seconds)
    }
    // 启动流程：**必须放在所有属性声明之后**（Kotlin 按声明顺序初始化字段与 init 块）。
    // 详见 ConsoleViewModel 类末尾的说明与 scripts/check-init-order.py。

    init {
        registerDiagnostics()
        safeLaunch("clearEmbeddedTags") { prepare() }
    }
}
