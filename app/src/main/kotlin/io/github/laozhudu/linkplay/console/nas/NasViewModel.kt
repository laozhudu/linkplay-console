package io.github.laozhudu.linkplay.console.nas

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.laozhudu.linkplay.discovery.DiscoveredDevice
import io.github.laozhudu.linkplay.discovery.SsdpDiscovery
import io.github.laozhudu.linkplay.protocol.DeviceHttpClient
import io.github.laozhudu.linkplay.protocol.LinkPlayCommands
import io.github.laozhudu.linkplay.protocol.queue.ClientSideQueue
import io.github.laozhudu.linkplay.protocol.recovery.RendererHealth
import io.github.laozhudu.linkplay.protocol.recovery.RendererProbe
import io.github.laozhudu.linkplay.protocol.recovery.RendererRecovery
import io.github.laozhudu.linkplay.protocol.session.NasPathStep
import io.github.laozhudu.linkplay.protocol.session.NasServerRef
import io.github.laozhudu.linkplay.protocol.session.NasSession
import io.github.laozhudu.linkplay.protocol.session.PlaybackSession
import io.github.laozhudu.linkplay.console.session.SessionStore
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
    /** 控制面（UPnP 49152）掉线。设备本身在线，只是控制通道没了，提示用户重启音箱。 */
    val controlLost: Boolean = false,
    /** 正在重启音箱并等待恢复。 */
    val healing: Boolean = false,
    /** 上次连过的 NAS（有就展示快捷入口，省掉每次搜索）。 */
    val rememberedServer: NasServerRef? = null,
    /** 上次停在哪一层（用于说明「继续上次」会跳到哪）。 */
    val rememberedPath: List<NasPathStep> = emptyList(),
    /** 上次没播完的队列（断点续播）。 */
    val resumable: PlaybackSession? = null,
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
    /** 设备 UUID。DHCP 换 IP 后记忆仍能对上，所以记忆的键是它而不是 IP。 */
    private val deviceUuid: String?,
) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(NasUiState())
    val state: StateFlow<NasUiState> = _state.asStateFlow()

    private val discovery = SsdpDiscovery(timeoutMs = 3_000)
    private val sessions = SessionStore.of(app)
    /** 记忆的键：优先设备 UUID；拿不到（比如手动 IP 且未连上）就退回 IP。 */
    private val memoryKey: String = deviceUuid?.takeIf { it.isNotBlank() } ?: "ip:$rendererIp"

    init {
        restoreMemory()
    }

    /** 读出「上次连的 NAS / 停在哪层 / 没播完的队列」，只读一次，不自动连接（避免一进页面就发请求）。 */
    private fun restoreMemory() {
        val nas = runCatching { sessions.loadNasSession(memoryKey) }.getOrNull()
        val playback = runCatching { sessions.loadPlayback(memoryKey) }.getOrNull()
        _state.update {
            it.copy(
                rememberedServer = nas?.server,
                rememberedPath = nas?.path.orEmpty(),
                resumable = playback,
                manualHost = it.manualHost.ifBlank { nas?.server?.label.orEmpty() },
            )
        }
        if (nas != null) log("上次用的 NAS：${nas.server.friendlyName ?: nas.server.label}")
        playback?.let { log("有未播完的队列：${it.remaining.size} 首，可从第 ${it.index + 1} 首继续") }
    }
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

    /** 待重放的路径（「继续上次」用）。连上服务器后逐层走完就清空。 */
    private var pendingPath: List<NasPathStep> = emptyList()

    /** 从 LOCATION 里拆出 host/port。DLNA 的 LOCATION 一定是完整 URL。 */
    private fun serverRefOf(location: String, friendlyName: String?): NasServerRef? = runCatching {
        val uri = java.net.URI(location)
        val host = uri.host ?: return@runCatching null
        val port = if (uri.port > 0) uri.port else 80
        NasServerRef(host, port, friendlyName)
    }.getOrNull()

    private fun savePlayback(items: List<QueueItem>, index: Int, loopMode: Int) {
        runCatching {
            sessions.savePlayback(
                memoryKey,
                PlaybackSession(
                    deviceUuid = deviceUuid ?: rendererIp,
                    deviceIp = rendererIp,
                    items = items,
                    index = index.coerceIn(0, (items.size - 1).coerceAtLeast(0)),
                    loopMode = loopMode,
                ),
            )
        }
    }

    private fun remember(server: NasServerRef?, path: List<NavStep>) {
        if (server == null) return
        runCatching {
            sessions.saveNasSession(memoryKey, NasSession(server, path.map { NasPathStep(it.objectId, it.title) }))
        }
    }

    /**
     * 「继续上次」：先连上次那台 NAS，再逐层走到上次停的目录。
     *
     * 为什么逐层重放而不是直接 browse 目标 objectId：DLNA 的 objectId 在不同服务器实现里
     * 语义不同（fnOS 是 `1$4` 这种组合键），直接跳可以工作但一旦 objectId 过期就白屏；
     * 逐层走失败时能停在上一个有效层级，用户也看得懂自己在哪。
     */
    fun resumeRemembered() {
        val server = _state.value.rememberedServer
        if (server == null) {
            _state.update { it.copy(error = "没有可恢复的记录，先搜索或手填 NAS 地址") }
            return
        }
        pendingPath = _state.value.rememberedPath
        connect(server.rootDescUrl)
    }

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
                val server = serverRefOf(location, desc.friendlyName)
                _state.update {
                    it.copy(
                        loadingDir = false,
                        connectedServer = desc.friendlyName ?: location,
                        path = emptyList(),
                        rememberedServer = server,
                    )
                }
                log("已连接 ${desc.friendlyName ?: location}")
                browseInto(ContentDirectory.ROOT_ID, desc.friendlyName ?: "根目录", push = false)

                // 「继续上次」：连上之后逐层重放到上次停的那一层。
                // 中途某层打不开就停在上一层，并说明原因——比直接报错白屏有用。
                val target = pendingPath
                pendingPath = emptyList()
                if (target.isNotEmpty()) {
                    log("正在回到上次的目录（${target.size} 层）…")
                    for (step in target) {
                        val ok = browseInto(step.objectId, step.title, push = true)
                        if (!ok) {
                            log("「${step.title}」打不开了（可能曲库重建），停在上层")
                            break
                        }
                    }
                } else {
                    remember(server, emptyList())
                }
            } catch (t: Throwable) {
                _state.update { it.copy(loadingDir = false, error = t.message) }
            }
        }
    }

    fun openDir(objectId: String, title: String, push: Boolean = true) {
        viewModelScope.launch { browseInto(objectId, title, push) }
    }

    /**
     * 浏览一层并把结果写进状态。返回是否成功——「继续上次」需要知道哪一层断了。
     *
     * 抽成 suspend 而不是 fire-and-forget：重放路径必须按顺序、且失败要能停。
     */
    private suspend fun browseInto(objectId: String, title: String, push: Boolean): Boolean {
        val client = contentDir ?: return false
        _state.update { it.copy(loadingDir = true, error = null) }
        return runCatching {
            val r = withContext(Dispatchers.IO) { client.browse(objectId, 0, 300) }
            _state.update { cur ->
                cur.copy(
                    loadingDir = false,
                    entries = r.entries,
                    selected = emptySet(),
                    path = if (push) cur.path + NavStep(objectId, title) else cur.path,
                )
            }
            log("${r.entries.size} 项 / 共 ${r.totalMatches} 项")
            // 只在「往下走」时记路径：返回上级不该覆盖成更浅的位置，否则重进 App 停在浅层
            if (push) {
                val cur = _state.value
                remember(cur.rememberedServer, cur.path)
            }
            true
        }.onFailure { t ->
            _state.update { it.copy(loadingDir = false, error = t.message) }
        }.isSuccess
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
        pushQueue(items.map(::toQueueItem))
    }

    /** 「继续上次播放」：从上次断掉的那一首接着推。 */
    fun resumePlayback() {
        val saved = _state.value.resumable
        if (saved == null) {
            _state.update { it.copy(error = "没有可续播的队列") }
            return
        }
        if (queueJob?.isActive == true) return
        log("续播：从第 ${saved.index + 1} 首开始，共 ${saved.remaining.size} 首")
        pushQueue(saved.remaining)
    }

    private fun toQueueItem(e: BrowseEntry) = QueueItem(
        url = e.url!!,
        title = e.title,
        artist = e.artist,
        album = e.album,
        durationSec = e.durationSec,
        mimeType = mimeOf(e.protocolInfo),
    )

    private fun pushQueue(items: List<QueueItem>) {
        if (items.isEmpty()) {
            _state.update { it.copy(error = "没有可播放的曲目") }
            return
        }
        if (queueJob?.isActive == true) return

        stopRequested = false
        _state.update { it.copy(playing = true, error = null, playedCount = 0) }
        log("开始推送 ${items.size} 首到 $rendererIp")
        // 先落一份快照：即使这一刻直接杀进程，重启后也知道该从哪首继续
        savePlayback(items, 0, _state.value.loopMode)

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
                            // 记录当前是第几首，断点续播靠它
                            savePlayback(items, items.indexOfFirst { it.url == item.url }.coerceAtLeast(0), _state.value.loopMode)
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
                    queue.run(items = items, shouldStop = { stopRequested })
                }
                val expected = items.size
                // 中途停止是用户意图，不算故障；提前结束才说明控制面掉了
                val dropped = !stopRequested && played < expected
                _state.update { it.copy(playing = false, controlLost = dropped) }
                if (!dropped) {
                    // 正常播完就把快照清掉，否则下次进页面还会提示「继续上次」
                    runCatching { sessions.clearPlayback(memoryKey) }
                    _state.update { it.copy(resumable = null) }
                }
                if (dropped) {
                    log("提前中断：只播了 $played/$expected 首 —— 音箱的 UPnP 控制口(49152)掉线了")
                    log("httpapi 仍可用，说明设备在线；可点「重启音箱」恢复（实测约 40 秒）")
                } else {
                    log("结束，共播 $played 首")
                }
            } catch (t: Throwable) {
                _state.update { it.copy(playing = false, error = t.message) }
                log("推送失败：${t.message}")
            }
        }
    }

    /**
     * 重启音箱以恢复 UPnP 控制口。
     *
     * 为什么必须由人/客户端触发：实测这台固件掉 49152 之后**不会自己回来**，
     * 而 `reboot`（httpapi 裸命令，不是 `SystemCommand+rebootdevice`）能把整机重启并恢复端口。
     * 恢复约需 40 秒，期间以「49152 可连」为成功标准（只看 httpapi 会误判）。
     */
    fun restartRenderer() {
        if (_state.value.healing) return
        _state.update { it.copy(healing = true) }
        log("正在重启音箱（约 40 秒）…")
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                val http = DeviceHttpClient()
                val probe = object : RendererProbe {
                    override fun httpApiOk(): Boolean =
                        runCatching { http.status(rendererIp) }.isSuccess

                    override fun upnpOk(): Boolean = canConnect(rendererIp, 49152)

                    override fun reboot(): Boolean =
                        runCatching { http.send(rendererIp, LinkPlayCommands.reboot()) }.isSuccess
                }
                when (val health = RendererRecovery(probe).diagnose()) {
                    RendererHealth.HEALTHY -> true
                    RendererHealth.OFFLINE -> {
                        log("音箱不在线（httpapi 也不通），先检查电源/Wi-Fi")
                        false
                    }
                    RendererHealth.CONTROL_LOST -> RendererRecovery(probe).heal()
                }
            }
            _state.update { it.copy(healing = false, controlLost = !ok) }
            log(if (ok) "音箱已恢复，可以继续推送" else "恢复失败；请手动断电重开音箱")
        }
    }

    /** 端口可连探测：比发 SOAP 快，且不需要知道控制路径。 */
    private fun canConnect(ip: String, port: Int): Boolean = runCatching {
        java.net.Socket().use { it.connect(java.net.InetSocketAddress(ip, port), 800) }
        true
    }.getOrDefault(false)

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
