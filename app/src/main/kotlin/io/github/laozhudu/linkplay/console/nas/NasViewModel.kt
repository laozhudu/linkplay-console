package io.github.laozhudu.linkplay.console.nas

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.laozhudu.linkplay.discovery.DiscoveredDevice
import io.github.laozhudu.linkplay.discovery.SsdpDiscovery
import io.github.laozhudu.linkplay.protocol.queue.ClientSideQueue
import io.github.laozhudu.linkplay.protocol.queue.QueueItem
import io.github.laozhudu.linkplay.protocol.queue.QueueTarget
import io.github.laozhudu.linkplay.protocol.upnp.BrowseEntry
import io.github.laozhudu.linkplay.protocol.upnp.ContentDirectory
import io.github.laozhudu.linkplay.protocol.upnp.ContentDirectoryClient
import io.github.laozhudu.linkplay.protocol.upnp.DeviceDescription
import io.github.laozhudu.linkplay.protocol.upnp.PositionInfo
import io.github.laozhudu.linkplay.protocol.upnp.UpnpRendererClient
import io.github.laozhudu.linkplay.protocol.upnp.UpnpServices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 浏览路径上的一层（用于「返回上级」）。 */
data class NavStep(val objectId: String, val title: String)

data class NasUiState(
    val scanning: Boolean = false,
    val servers: List<DiscoveredDevice> = emptyList(),
    /** 手动填的 NAS 地址（组播被隔离时用）。 */
    val manualHost: String = "",
    val connectedServer: String? = null,
    val loadingDir: Boolean = false,
    val path: List<NavStep> = emptyList(),
    val entries: List<BrowseEntry> = emptyList(),
    val selected: Set<String> = emptySet(),
    /** 已加入队列的曲目（跨目录累积）。 */
    val picked: List<BrowseEntry> = emptyList(),
    val playing: Boolean = false,
    val nowPlaying: String? = null,
    val playedCount: Int = 0,
    val loopMode: Int = ClientSideQueue.LOOP_ALL,
    val error: String? = null,
    val log: List<String> = emptyList(),
)

/**
 * NAS 浏览 + 列表推送。
 *
 * 链路（真机验证过的组合）：
 * ```
 * SSDP 发现 MediaServer → 读描述拿 ContentDirectory controlURL
 *   → Browse 目录（容器/曲目）→ 选中若干曲目
 *   → 用 ClientSideQueue 逐首 SetAVTransportURI 给音箱（本固件不能自己排队）
 * ```
 * 视频/图片目录也会列出来，但只能播音频项（`audioItem`）。
 */
class NasViewModel(
    app: Application,
    private val rendererIp: String,
    private val rendererLocation: String?,
) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(NasUiState())
    val state: StateFlow<NasUiState> = _state.asStateFlow()

    private val discovery = SsdpDiscovery(timeoutMs = 3_000)
    private var contentDir: ContentDirectoryClient? = null
    private var queueJob: Job? = null
    @Volatile
    private var stopRequested = false

    fun scan() {
        if (_state.value.scanning) return
        _state.update { it.copy(scanning = true, error = null) }
        viewModelScope.launch {
            val found = runCatching { withContext(Dispatchers.IO) { discovery.discoverMediaServers() } }
                .getOrDefault(emptyList())
            _state.update { it.copy(scanning = false, servers = found) }
            log("发现 ${found.size} 个媒体服务器")
            if (found.isEmpty()) log("没找到？确认 NAS 开了 DLNA，或手动填 IP")
        }
    }

    fun setManualHost(v: String) = _state.update { it.copy(manualHost = v) }

    /** 手动连接：组播不通时直接给 IP:端口。 */
    fun connectManual() {
        val raw = _state.value.manualHost.trim().removePrefix("http://").removeSuffix("/")
        if (raw.isEmpty()) {
            _state.update { it.copy(error = "请输入 NAS 地址，如 192.168.99.223:8200") }
            return
        }
        val location = if (raw.contains(':')) "http://$raw/rootDesc.xml" else "http://$raw:8200/rootDesc.xml"
        connect(location)
    }

    fun connect(device: DiscoveredDevice) = device.location?.let { connect(it) }
        ?: _state.update { it.copy(error = "该服务器没有 LOCATION") }

    fun connect(location: String) {
        _state.update { it.copy(loadingDir = true, error = null) }
        viewModelScope.launch {
            try {
                val desc = withContext(Dispatchers.IO) { UpnpRendererClient.describe(location) }
                val url = ContentDirectoryClient.controlUrlOf(desc)
                    ?: throw IllegalStateException("该服务器没有 ContentDirectory 服务")
                contentDir = ContentDirectoryClient(url)
                _state.update {
                    it.copy(
                        loadingDir = false,
                        connectedServer = desc.friendlyName ?: location,
                        path = emptyList(),
                    )
                }
                log("已连接 ${desc.friendlyName ?: location}")
                openDir(ContentDirectory.ROOT_ID, desc.friendlyName ?: "根目录", push = false)
            } catch (t: Throwable) {
                _state.update { it.copy(loadingDir = false, error = t.message) }
            }
        }
    }

    fun openDir(objectId: String, title: String, push: Boolean = true) {
        val client = contentDir ?: return
        _state.update { it.copy(loadingDir = true, error = null) }
        viewModelScope.launch {
            val result = runCatching { withContext(Dispatchers.IO) { client.browse(objectId, 0, 300) } }
            result.onSuccess { r ->
                _state.update { cur ->
                    cur.copy(
                        loadingDir = false,
                        entries = r.entries,
                        selected = emptySet(),
                        path = if (push) cur.path + NavStep(objectId, title) else cur.path,
                    )
                }
                log("${r.entries.size} 项 / 共 ${r.totalMatches} 项")
            }.onFailure { t ->
                _state.update { it.copy(loadingDir = false, error = t.message) }
            }
        }
    }

    /** 返回上一级。 */
    fun up() {
        val path = _state.value.path
        if (path.size <= 1) return
        val parent = path.dropLast(1)
        val parentId = parent.lastOrNull()?.objectId ?: ContentDirectory.ROOT_ID
        _state.update { it.copy(path = parent) }
        openDir(parentId, parent.lastOrNull()?.title ?: "根目录", push = false)
    }

    fun toggleEntry(entry: BrowseEntry) = _state.update {
        val next = it.selected.toMutableSet()
        if (!next.add(entry.id)) next.remove(entry.id)
        it.copy(selected = next)
    }

    fun selectAllPlayable() = _state.update {
        it.copy(selected = it.entries.filter { e -> !e.isContainer && e.url != null }.map { e -> e.id }.toSet())
    }

    fun clearSelection() = _state.update { it.copy(selected = emptySet()) }

    /** 把当前选中的曲目加入待播队列（跨目录累积）。 */
    fun addSelectedToQueue() {
        val cur = _state.value
        val add = cur.entries.filter { !it.isContainer && it.url != null && it.id in cur.selected }
        if (add.isEmpty()) {
            _state.update { it.copy(error = "没有选中可播放的曲目") }
            return
        }
        _state.update {
            it.copy(picked = it.picked + add, selected = emptySet(), error = null)
        }
        log("已加入 ${add.size} 首，待播共 ${_state.value.picked.size} 首")
    }

    fun clearQueue() {
        stopRequested = true
        queueJob?.cancel()
        _state.update { it.copy(picked = emptyList(), playing = false, nowPlaying = null, playedCount = 0) }
    }

    fun setLoopMode(mode: Int) = _state.update { it.copy(loopMode = mode) }

    /** 开始把队列推给音箱。 */
    fun startQueue() {
        val items = _state.value.picked
        if (items.isEmpty()) {
            _state.update { it.copy(error = "队列为空，先在目录里选曲并「加入队列」") }
            return
        }
        if (queueJob?.isActive == true) return

        stopRequested = false
        _state.update { it.copy(playing = true, error = null, playedCount = 0) }
        log("开始推送 ${items.size} 首到 $rendererIp")

        queueJob = viewModelScope.launch {
            try {
                val client = withContext(Dispatchers.IO) {
                    if (!rendererLocation.isNullOrBlank()) {
                        val desc = UpnpRendererClient.describe(rendererLocation)
                        if (desc.avTransportControlUrl != null) UpnpRendererClient(desc)
                        else UpnpRendererClient.ofDevice(rendererIp)
                    } else {
                        UpnpRendererClient.ofDevice(rendererIp)
                    }
                }
                val queue = ClientSideQueue(
                    target = object : QueueTarget {
                        override fun play(item: QueueItem) {
                            withContextBlocking {
                                client.setAvTransportUri(item.url, item.title, item.mimeType)
                                client.play()
                            }
                            _state.update { s ->
                                s.copy(nowPlaying = item.title, playedCount = s.playedCount + 1)
                            }
                            log("▶ ${item.title}")
                        }

                        override fun position(): Pair<Long, Long>? = withContextBlocking {
                            runCatching {
                                val p = client.positionInfo()
                                p.relTimeSec to p.trackDurationSec
                            }.getOrNull()
                        }

                        override fun transportState(): String = withContextBlocking {
                            runCatching { client.transportInfo().state.wire }.getOrDefault("UNKNOWN")
                        }
                    },
                )
                queue.loopMode = _state.value.loopMode
                val played = withContext(Dispatchers.IO) {
                    queue.run(
                        items = items.map {
                            QueueItem(
                                url = it.url!!,
                                title = it.title,
                                artist = it.artist,
                                album = it.album,
                                durationSec = it.durationSec,
                                mimeType = mimeOf(it.protocolInfo),
                            )
                        },
                        shouldStop = { stopRequested },
                    )
                }
                _state.update { it.copy(playing = false) }
                log("结束，共播 $played 首")
            } catch (t: Throwable) {
                _state.update { it.copy(playing = false, error = t.message) }
                log("推送失败：${t.message}")
            }
        }
    }

    fun stopQueue() {
        stopRequested = true
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                runCatching {
                    val c = if (!rendererLocation.isNullOrBlank()) {
                        val d = UpnpRendererClient.describe(rendererLocation)
                        if (d.avTransportControlUrl != null) UpnpRendererClient(d) else UpnpRendererClient.ofDevice(rendererIp)
                    } else {
                        UpnpRendererClient.ofDevice(rendererIp)
                    }
                    c.stop()
                }
            }
            _state.update { it.copy(playing = false) }
            log("已停止")
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }

    private fun log(line: String) = _state.update { it.copy(log = (it.log + line).takeLast(30)) }

    /** 阻塞桥接：QueueTarget 是同步接口，为保持 core 层纯 JVM 而在这里做转换。 */
    private fun <T> withContextBlocking(block: suspend () -> T): T =
        kotlinx.coroutines.runBlocking { block() }

    private fun mimeOf(protocolInfo: String?): String =
        protocolInfo?.split(':')?.getOrNull(2)?.takeIf { it.isNotBlank() } ?: "audio/mpeg"

    companion object {
        fun formatDuration(seconds: Long): String = PositionInfo.formatTime(seconds)

        /** 便于 UI 判断「这层是音频目录还是别的」 */
        fun isAudioEntry(entry: BrowseEntry): Boolean =
            !entry.isContainer && entry.url?.contains("audio", ignoreCase = true) != false

        val REQUIRED_SERVICE = UpnpServices.AV_TRANSPORT
    }
}
