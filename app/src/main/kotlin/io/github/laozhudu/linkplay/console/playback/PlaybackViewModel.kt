package io.github.laozhudu.linkplay.console.playback

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.laozhudu.linkplay.protocol.DeviceHttpClient
import io.github.laozhudu.linkplay.protocol.player.HttpApiPlaybackEngine
import io.github.laozhudu.linkplay.protocol.media.RelayMediaServer
import io.github.laozhudu.linkplay.protocol.media.RelayTarget
import io.github.laozhudu.linkplay.protocol.player.LoopMode
import io.github.laozhudu.linkplay.protocol.player.PlaybackEngine
import io.github.laozhudu.linkplay.protocol.player.PlaybackSnapshot
import io.github.laozhudu.linkplay.protocol.player.UpnpPlaybackEngine
import io.github.laozhudu.linkplay.protocol.upnp.PositionInfo
import io.github.laozhudu.linkplay.protocol.upnp.TransportState
import io.github.laozhudu.linkplay.protocol.upnp.UpnpRendererClient
import kotlinx.coroutines.Dispatchers
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
) : ViewModel() {

    private val _state = MutableStateFlow(PlaybackUiState())
    val state: StateFlow<PlaybackUiState> = _state.asStateFlow()

    private var engine: PlaybackEngine? = null
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

    fun refresh() {
        viewModelScope.launch { refreshOnce() }
    }

    private suspend fun refreshOnce(silent: Boolean = false) {
        val e = engine ?: return
        if (!silent) _state.update { it.copy(busy = true, error = null) }
        try {
            val snapshot: PlaybackSnapshot = withContext(Dispatchers.IO) { e.snapshot() }
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

    fun next() = httpAct("下一首", "setPlayerCmd:next")

    fun previous() = httpAct("上一首", "setPlayerCmd:prev")

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
    fun toggleMute() = act("静音切换") { engine!!.setMute(!_state.value.mute) }

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

    companion object {
        const val POLL_INTERVAL_MS = 1_000L

        fun format(seconds: Long): String = PositionInfo.formatTime(seconds)
    }
}
