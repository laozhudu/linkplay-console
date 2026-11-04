package io.github.laozhudu.linkplay.console.library

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.laozhudu.linkplay.protocol.playqueue.DidlLiteBuilder
import io.github.laozhudu.linkplay.protocol.playqueue.PlayQueueClient
import io.github.laozhudu.linkplay.protocol.playqueue.QueueContextFormat
import io.github.laozhudu.linkplay.console.playback.QueuePlaybackService
import io.github.laozhudu.linkplay.protocol.player.LoopMode
import io.github.laozhudu.linkplay.protocol.playqueue.QueueLoopMode
import io.github.laozhudu.linkplay.protocol.queue.QueueItem
import io.github.laozhudu.linkplay.protocol.session.PlaybackSession
import io.github.laozhudu.linkplay.protocol.upnp.UpnpRendererClient
import io.github.laozhudu.linkplay.protocol.upnp.UpnpServices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import io.github.laozhudu.linkplay.console.diagnostics.safeLaunch

data class QueueUiState(
    val loading: Boolean = false,
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

    /** 设备地址来自 LOCATION（手动输 IP 时可能拿不到）。 */
    private val deviceHost: String? = location?.let { runCatching { java.net.URI(it).host }.getOrNull() }

    init {
        safeLaunch("initDevice") { initDevice() }
        // 不在构造时扫描：扫描需要读媒体权限，一进页面就弹权限框很粗暴。
        // 改为用户点「扫描本机音乐」时才请求（见 QueueScreen）。
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
        safeLaunch("loadTracks") {
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
                return@safeLaunch
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

    /**
     * 推送当前勾选的列表。
     *
     * **实现变更（重要）**：原来走「PlayQueue + DIDL 整列表」，但真机实测这条路过不去 ——
     * 固件只接受空的 `<Tracks/>`，放任何曲目都回 `404 ParseXmltoPlaylist error`（docs/08 §5.2）。
     * 也就是说这个按钮在真机上从来没有成功过。
     *
     * 现在改成已验证的路径：起本机媒体服务 → 交给前台服务用 [ClientSideQueue] 逐首投流。
     * 顺带白拿三件事：锁屏不断（前台服务）、断点续播（会话快照）、与 NAS 页同一套逻辑。
     */
    fun pushSelected() {
        val s = _state.value
        if (s.busy) return
        val chosen = s.tracks.filter { it.id in s.selected }
        if (chosen.isEmpty()) {
            _state.update { it.copy(error = "还没有选中歌曲") }
            return
        }
        push(chosen)
    }

    /** 点一首就播这一首 —— 与 NAS 页一致，不再要求先勾选。 */
    fun playNow(track: LocalTrack) = push(listOf(track))

    private fun push(chosen: List<LocalTrack>) {
        val s = _state.value
        if (s.busy) return
        _state.update { it.copy(busy = true, error = null) }
        safeLaunch("push") {
            try {
                val ctx = getApplication<Application>()
                withContext(Dispatchers.IO) { LocalMediaRegistry.ensureServer(ctx, s.tracks) }
                MediaServerService.start(ctx)
                val base = LocalMediaRegistry.baseUrl()
                    ?: throw IllegalStateException("拿不到本机局域网 IP（确认已连 Wi-Fi）")
                _state.update { it.copy(serverReady = true, serverUrl = base) }

                // 曲目地址形如 http://<本机IP>:<port>/t/<id>，由 LocalMediaServer 提供 Range
                val items = chosen.map {
                    QueueItem(
                        url = "$base/t/${it.id}",
                        title = it.title,
                        artist = it.artist,
                        album = it.album,
                        durationSec = it.durationSec,
                        mimeType = it.mimeType,
                    )
                }
                val host = deviceHost ?: throw IllegalStateException("拿不到音箱地址（需要先通过 SSDP 发现设备）")
                val session = PlaybackSession(
                    deviceUuid = host,
                    deviceIp = host,
                    items = items,
                    index = 0,
                    loopMode = s.loopMode,
                )
                QueuePlaybackService.start(ctx, location, session)
                _state.update { it.copy(busy = false, pushedCount = items.size) }
                log("已推送 ${items.size} 首（客户端逐首投流）")
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
