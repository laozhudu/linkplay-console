package io.github.laozhudu.linkplay.console.library

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.laozhudu.linkplay.protocol.playqueue.DidlLiteBuilder
import io.github.laozhudu.linkplay.protocol.playqueue.PlayQueueClient
import io.github.laozhudu.linkplay.protocol.playqueue.QueueContextFormat
import io.github.laozhudu.linkplay.protocol.playqueue.QueueLoopMode
import io.github.laozhudu.linkplay.protocol.upnp.UpnpRendererClient
import io.github.laozhudu.linkplay.protocol.upnp.UpnpServices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class QueueUiState(
    val loading: Boolean = true,
    val tracks: List<LocalTrack> = emptyList(),
    val selected: Set<Long> = emptySet(),
    val serverReady: Boolean = false,
    val serverUrl: String? = null,
    val deviceName: String? = null,
    /** 设备是否声明了私有 PlayQueue 服务；没有就只能单曲投流。 */
    val queueSupported: Boolean? = null,
    val loopMode: Int = QueueLoopMode.ALL,
    val busy: Boolean = false,
    val pushedCount: Int = 0,
    val error: String? = null,
    val log: List<String> = emptyList(),
    val permissionNeeded: Boolean = false,
)

/**
 * 整列表推送。
 *
 * 完整链路（对照原厂 App 的实现）：
 * ```
 * MediaStore 扫描 → 手机起 HTTP 服务 → 构建 DIDL-Lite 列表
 *   → PlayQueue.CreateQueue(QueueContext)   （或 AppendTracksInQueue）
 *   → PlayQueueWithIndex(QueueName, 0)      （开始播第 0 条）
 *   → SetQueueLoopMode(mode)                （列表循环）
 * ```
 * 设备断网/重启后队列可能丢失，所以「重推」是一等操作。
 */
class QueueViewModel(
    app: Application,
    private val location: String?,
) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(QueueUiState())
    val state: StateFlow<QueueUiState> = _state.asStateFlow()

    private var queueClient: PlayQueueClient? = null

    init {
        viewModelScope.launch { initDevice() }
        loadTracks()
    }

    private suspend fun initDevice() {
        if (location.isNullOrBlank()) {
            _state.update { it.copy(queueSupported = null, deviceName = null) }
            return
        }
        try {
            val description = withContext(Dispatchers.IO) { UpnpRendererClient.describe(location) }
            val client = PlayQueueClient(description)
            queueClient = client
            _state.update {
                it.copy(
                    queueSupported = client.isSupported,
                    deviceName = description.friendlyName,
                )
            }
            log(
                if (client.isSupported) {
                    "设备声明了 PlayQueue 服务，可整列表推送"
                } else {
                    "设备没有 PlayQueue 服务：只能单曲投流（用播放卡片）"
                },
            )
        } catch (t: Throwable) {
            _state.update { it.copy(queueSupported = false) }
            log("读取设备描述失败：${t.message}")
        }
    }

    fun loadTracks() {
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) { LocalLibrary.query(getApplication()) }
            }
            if (result.isFailure) {
                _state.update {
                    it.copy(
                        loading = false,
                        permissionNeeded = true,
                        error = "读取本地音乐失败：${result.exceptionOrNull()?.message}",
                    )
                }
                return@launch
            }
            val tracks = result.getOrDefault(emptyList())
            _state.update {
                it.copy(
                    loading = false,
                    tracks = tracks,
                    selected = if (tracks.isEmpty()) emptySet() else tracks.map { t -> t.id }.toSet(),
                    permissionNeeded = false,
                    error = if (tracks.isEmpty()) "MediaStore 里没有音乐文件（或未授予媒体权限）" else null,
                )
            }
            log("扫描到 ${tracks.size} 首本地歌曲")
        }
    }

    fun toggle(id: Long) = _state.update {
        val next = it.selected.toMutableSet()
        if (!next.add(id)) next.remove(id)
        it.copy(selected = next)
    }

    fun selectAll() = _state.update { it.copy(selected = it.tracks.map { t -> t.id }.toSet()) }
    fun clearSelection() = _state.update { it.copy(selected = emptySet()) }
    fun setLoopMode(mode: Int) = _state.update { it.copy(loopMode = mode) }

    /** 起服务并推送当前勾选的列表。 */
    fun pushSelected() {
        val s = _state.value
        if (s.busy) return
        val chosen = s.tracks.filter { it.id in s.selected }
        if (chosen.isEmpty()) {
            _state.update { it.copy(error = "还没有选中歌曲") }
            return
        }
        val client = queueClient
        if (client == null || !client.isSupported) {
            _state.update { it.copy(error = "这台设备不支持整列表推送（没有 PlayQueue 服务）") }
            return
        }

        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                val ctx = getApplication<Application>()
                val port = withContext(Dispatchers.IO) {
                    LocalMediaRegistry.ensureServer(ctx, s.tracks)
                }
                MediaServerService.start(ctx)
                val base = LocalMediaRegistry.baseUrl()
                if (base == null) {
                    throw IllegalStateException("拿不到本机局域网 IP（确认已连 Wi-Fi）")
                }
                _state.update { it.copy(serverReady = true, serverUrl = base) }
                log("本机媒体服务 $base（端口 $port）")

                val playlist = LocalLibrary.toPlaylist(chosen, base)
                val didl = DidlLiteBuilder.build(playlist)
                val queueName = QUEUE_NAME
                val context = QueueContextFormat.DIDL_ONLY.render(queueName, didl)

                withContext(Dispatchers.IO) {
                    // 先清掉同名旧队列（设备上残留会让 Index 错位），失败忽略
                    runCatching { client.deleteQueue(queueName) }
                    client.createQueue(context)
                    client.setQueueLoopMode(s.loopMode)
                    client.playQueueWithIndex(queueName, 0)
                }
                _state.update { it.copy(busy = false, pushedCount = chosen.size) }
                log("已推送 ${chosen.size} 首，循环=${QueueLoopMode.label(s.loopMode)}")
            } catch (t: Throwable) {
                _state.update { it.copy(busy = false, error = t.message) }
                log("推送失败：${t.message}")
            }
        }
    }

    /** 重推：设备重启/断网后队列常丢，这不是异常而是常态操作。 */
    fun repush() = pushSelected()

    fun stopServer() {
        MediaServerService.stop(getApplication())
        LocalMediaRegistry.stopServer()
        _state.update { it.copy(serverReady = false, serverUrl = null) }
        log("已停止本机媒体服务")
    }

    fun clearError() = _state.update { it.copy(error = null) }

    private fun log(line: String) = _state.update { it.copy(log = (it.log + line).takeLast(40)) }

    companion object {
        /** 固定队列名，便于重推时先删旧队列。 */
        const val QUEUE_NAME = "linkplay-console"

        /** 设备支持的服务类型常量，供 UI 展示判断依据。 */
        const val REQUIRED_SERVICE = UpnpServices.WIIMU_PLAY_QUEUE
    }
}
