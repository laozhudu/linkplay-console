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
import io.github.laozhudu.linkplay.protocol.player.UpnpPlaybackEngine
import io.github.laozhudu.linkplay.protocol.upnp.PositionInfo
import io.github.laozhudu.linkplay.protocol.upnp.TransportState
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
    private val deviceIp: String,
    private val location: String?,
    /** 设备 uuid：播放快照按它存盘，换 IP 也能找回上次的队列。 */
    private val deviceUuid: String? = null,
    /** 会话仓库。传 null 表示"不恢复上次状态"（预览/测试用）。 */
    private val sessionStore: SessionStore? = null,
) : ViewModel() {

    private val _state = MutableStateFlow(PlaybackUiState())
    val state: StateFlow<PlaybackUiState> = _state.asStateFlow()

    private var engine: PlaybackEngine? = null

    /** 直接的 httpapi 客户端：切换输入源这类命令不属于"播放通道"，不该塞进 PlaybackEngine。 */
    private val device = DeviceHttpClient()
    private var polling = false

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

    init {
        viewModelScope.launch { prepare() }
    }

    private suspend fun prepare() {
        val notes = mutableListOf<String>()

        if (!location.isNullOrBlank()) {
            try {
                val description = withContext(Dispatchers.IO) { UpnpRendererClient.describe(location) }
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
        if (polling) return
        polling = true
        viewModelScope.launch {
            while (isActive) {
                delay(POLL_INTERVAL_MS)
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
        viewModelScope.launch { refreshOnce() }
    }

    private suspend fun refreshOnce(silent: Boolean = false) {
        val e = engine ?: return
        if (!silent) _state.update { it.copy(busy = true, error = null) }
        try {
            val snapshot: PlaybackSnapshot = withContext(Dispatchers.IO) { e.snapshot() }
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
        act("投流") {
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
                listOf(
                    QueueItem(
                        url = target,
                        title = raw.substringAfterLast('/').ifBlank { "投流音频" },
                        mimeType = "audio/mpeg",
                    ),
                ),
            )
            QueueBus.playing(raw.substringAfterLast('/').ifBlank { "投流音频" }, 0)
            log(if (needsHeaders) "已转发投流：$target" else "已投流：$target")
        }
    }

    /** 转发服务占着端口，页面销毁时要还回去。 */
    override fun onCleared() {
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

    fun play() = act("播放") { engine!!.resume() }

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
        act("下一首") { e.next() }
    }

    /** 上一首：同上，走 AVTransport.Previous。 */
    fun previous() {
        val e = engine
        if (e == null) { httpAct("上一首", "setPlayerCmd:prev"); return }
        act("上一首") { e.previous() }
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
        viewModelScope.launch {
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
    fun pause() = act("暂停") { engine!!.pause() }
    fun stop() = act("停止") { engine!!.stop() }
    fun seekTo(seconds: Long) = act("跳转") { engine!!.seek(seconds) }
    fun setVolume(value: Int) = act("音量 ${value.coerceIn(0, 100)}") { engine!!.setVolume(value) }

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
            viewModelScope.launch {
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
            viewModelScope.launch {
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
        act("播放第 ${index + 1} 首") { engine!!.playUrl(item.url, item.title) }
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
        viewModelScope.launch {
            val tags = withContext(Dispatchers.IO) {
                runCatching { TagFetcher.fetch(url) }.getOrNull()
            }
            if (tags == null) return@launch
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
        act("静音切换") { engine!!.setMute(next) }
    }

    private fun act(label: String, block: () -> Unit) {
        if (_state.value.busy || engine == null) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            val failure = runCatching { withContext(Dispatchers.IO) { block() } }.exceptionOrNull()
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
        val deviceList = DevicePlaylistGateway.read(deviceIp, location)
        if (deviceList != null && !deviceList.isEmpty) {
            val items = DevicePlaylistGateway.toQueueItems(deviceList)
            val idx = deviceList.currentIndex.coerceIn(0, items.lastIndex)
            QueueBus.restore(
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
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            val root = runCatching {
                withContext(Dispatchers.IO) { device.send(deviceIp, LinkPlayCommands.switchMode(src.id)) }
            }.getOrElse { t ->
                _state.update { it.copy(busy = false, error = "切换失败：${t.message}") }
                return@launch
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
        viewModelScope.launch {
            restoreLastSession()
        }
        // 音量合并通道：只保留最新值，每 80ms 最多下发一次
        viewModelScope.launch {
            for (v in volumeChannel) {
                runCatching { withContext(Dispatchers.IO) { engine?.setVolume(v) } }
                delay(80)
            }
        }
    }

    companion object {
        const val POLL_INTERVAL_MS = 1_000L

        fun format(seconds: Long): String = PositionInfo.formatTime(seconds)
    }
}
