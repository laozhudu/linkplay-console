package io.github.laozhudu.linkplay.console.playback

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.laozhudu.linkplay.protocol.upnp.DeviceDescription
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
    /** 设备没给 UPnP 描述地址时的说明（手动输入 IP 的路径会走到这里）。 */
    val unavailableReason: String? = null,
    val friendlyName: String? = null,
    val state: TransportState = TransportState.UNKNOWN,
    val title: String? = null,
    val artist: String? = null,
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
 * UPnP 播放控制。
 *
 * 与 httpapi 那套分开的原因：播放/音量只有 UPnP 有（httpapi 里 `setPlayerCmd` 只管输入源）。
 * 轮询而不是 GENA 事件订阅——理由见 `UpnpRendererClient` 的类注释。
 */
class PlaybackViewModel(
    private val deviceIp: String,
    private val location: String?,
) : ViewModel() {

    private val _state = MutableStateFlow(PlaybackUiState())
    val state: StateFlow<PlaybackUiState> = _state.asStateFlow()

    private var client: UpnpRendererClient? = null
    private var polling = false

    init {
        viewModelScope.launch { prepare() }
    }

    private suspend fun prepare() {
        if (location.isNullOrBlank()) {
            _state.update {
                it.copy(
                    preparing = false,
                    unavailableReason = "这台设备没有 UPnP 描述地址（LOCATION）。用 SSDP 搜索进入可拿到，" +
                        "手动输入 IP 时设备不会告知描述文件位置。",
                )
            }
            return
        }
        try {
            val description: DeviceDescription = withContext(Dispatchers.IO) {
                UpnpRendererClient.describe(location)
            }
            if (description.avTransportControlUrl == null) {
                _state.update {
                    it.copy(
                        preparing = false,
                        friendlyName = description.friendlyName,
                        unavailableReason = "设备未声明 AVTransport 服务（不是 DLNA 渲染器），无法做播放控制。",
                    )
                }
                return
            }
            val created = withContext(Dispatchers.IO) { UpnpRendererClient(description) }
            client = created
            _state.update {
                it.copy(
                    preparing = false,
                    ready = true,
                    friendlyName = description.friendlyName,
                )
            }
            refreshOnce()
            startPolling()
        } catch (t: Throwable) {
            _state.update {
                it.copy(
                    preparing = false,
                    unavailableReason = "读取设备描述失败：${t.message}。老固件可能未启用 DLNA 渲染器。",
                )
            }
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
        val c = client ?: return
        if (!silent) _state.update { it.copy(busy = true, error = null) }
        try {
            val (transport, position, volume) = withContext(Dispatchers.IO) {
                Triple(
                    c.transportInfo(),
                    runCatching { c.positionInfo() }.getOrNull(),
                    runCatching { c.volume() }.getOrNull(),
                )
            }
            _state.update {
                it.copy(
                    busy = false,
                    state = transport.state,
                    title = position?.trackMetadata?.title
                        ?: it.title,
                    artist = position?.trackMetadata?.artist,
                    positionSec = position?.relTimeSec ?: 0,
                    durationSec = position?.trackDurationSec ?: 0,
                    volume = volume?.volume ?: it.volume,
                    mute = volume?.mute ?: it.mute,
                    error = null,
                )
            }
        } catch (t: Throwable) {
            _state.update { it.copy(busy = false, error = if (silent) it.error else t.message) }
        }
    }

    fun setCastUrl(value: String) = _state.update { it.copy(castUrl = value) }

    /** 把一条媒体 URL 投给音箱：SetAVTransportURI + Play。 */
    fun cast() {
        val c = client ?: return
        val url = _state.value.castUrl.trim()
        if (url.isEmpty()) {
            _state.update { it.copy(error = "先填一条媒体 URL（音箱会自己去拉流）") }
            return
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            _state.update { it.copy(error = "只支持 http/https 直链；本地文件需要先提供 HTTP 服务") }
            return
        }
        act("投流") {
            c.setAvTransportUri(url, title = url.substringAfterLast('/').take(80))
            c.play()
            true
        }
    }

    fun play() = act("播放") { client!!.play(); true }
    fun pause() = act("暂停") { client!!.pause(); true }
    fun stop() = act("停止") { client!!.stop(); true }

    fun seekTo(seconds: Long) = act("跳转") { client!!.seek(seconds); true }

    fun setVolume(value: Int) = act("音量 ${value.coerceIn(0, 100)}") {
        client!!.setVolume(value)
        true
    }

    fun toggleMute() = act("静音切换") {
        client!!.setMute(!_state.value.mute)
        true
    }

    private fun act(label: String, block: () -> Boolean) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, error = null) }
        log("$label …")
        viewModelScope.launch {
            val failure = runCatching { withContext(Dispatchers.IO) { block() } }.exceptionOrNull()
            if (failure != null) {
                _state.update { it.copy(busy = false, error = "${failure.message}") }
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
