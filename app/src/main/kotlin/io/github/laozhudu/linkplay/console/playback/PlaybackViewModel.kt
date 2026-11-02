package io.github.laozhudu.linkplay.console.playback

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.laozhudu.linkplay.protocol.DeviceHttpClient
import io.github.laozhudu.linkplay.protocol.player.HttpApiPlaybackEngine
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
    val busy: Boolean = false,
    val castUrl: String = "",
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

    fun cast() {
        val e = engine ?: return
        val url = _state.value.castUrl.trim()
        if (url.isEmpty()) {
            _state.update { it.copy(error = "先填一条媒体 URL（音箱会自己去拉流）") }
            return
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            _state.update { it.copy(error = "只支持 http/https 直链；本地文件需要先提供 HTTP 服务") }
            return
        }
        act("投流") { e.playUrl(url, url.substringAfterLast('/').take(80)) }
    }

    fun play() = act("播放") { engine!!.resume() }
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
