package io.github.laozhudu.linkplay.console.online

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.laozhudu.linkplay.console.playback.QueueBus
import io.github.laozhudu.linkplay.console.playback.startQueueOnDevice
import io.github.laozhudu.linkplay.console.session.SessionStore
import io.github.laozhudu.linkplay.protocol.mediasource.MediaTrack
import io.github.laozhudu.linkplay.protocol.mediasource.SubsonicClient
import io.github.laozhudu.linkplay.protocol.mediasource.SubsonicConfig
import io.github.laozhudu.linkplay.protocol.queue.QueueItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import io.github.laozhudu.linkplay.console.diagnostics.safeLaunch

data class OnlineUiState(
    val config: SubsonicConfig = SubsonicConfig(),
    val editing: Boolean = false,
    val query: String = "",
    val results: List<MediaTrack> = emptyList(),
    val searching: Boolean = false,
    val busy: Boolean = false,
    /** 上一次搜索/播放的说明或错误。 */
    val note: String? = null,
    val log: List<String> = emptyList(),
    /** 配置是否已经 ping 通过（决定界面上是不是"可用"）。 */
    val verified: Boolean = false,
)

/**
 * 在线曲库（Subsonic / tt-music）搜索页。
 *
 * ## 定位
 *
 * 这一页只干两件事：**搜** 和 **投**。内容由 `.124` 上的 tt-music 提供
 * （它是 Navidrome 分支，`/rest` 是标准 Subsonic），播放依旧走我们自己的
 * `startQueueOnDevice` —— 设备侧队列、多房间、声道这些能力全保留。
 *
 * ## 为什么配置放在这一页而不是设置页
 *
 * 第一次用的人不知道"服务地址写什么"。放在这里可以就地填、就地测，
 * 测通了立刻能搜；设置页那种"先去那边填、再回来"的跳转没必要。
 */
class OnlineViewModel(
    app: Application,
    private val deviceIp: String,
    private val location: String?,
    private val deviceUuid: String?,
) : AndroidViewModel(app) {

    private val store = SessionStore.of(app)

    private val _state = MutableStateFlow(
        OnlineUiState(config = SubsonicConfig.decode(runCatching { store.loadSubsonic() }.getOrNull()) ?: SubsonicConfig()),
    )
    val state: StateFlow<OnlineUiState> = _state.asStateFlow()

    private fun client(cfg: SubsonicConfig = _state.value.config): SubsonicClient =
        SubsonicClient(cfg) { url -> httpGet(url) }

    /** 纯 GET，手动跟随重定向（部分反代会 302 到 https）。 */
    private fun httpGet(url: String): String {
        val conn = (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 6_000
            readTimeout = 12_000
            instanceFollowRedirects = true
            useCaches = false
            setRequestProperty("Accept", "application/json")
        }
        return try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299 && body.isBlank()) error("HTTP $code")
            body
        } finally {
            conn.disconnect()
        }
    }

    // ---- 配置 ----

    fun setEditing(on: Boolean) = _state.update { it.copy(editing = on) }

    fun updateConfig(transform: (SubsonicConfig) -> SubsonicConfig) =
        _state.update { it.copy(config = transform(it.config), verified = false) }

    /** 保存并**当场 ping 一次**：存一个连不上的配置等于给自己埋坑。 */
    fun saveConfig() {
        val cfg = _state.value.config.copy(
            // 用户常常只填 IP:端口 —— 这里规范化，避免"缺协议被判非法"（电台那次踩过）
            baseUrl = _state.value.config.baseUrl.trim()
                .let { if (it.isBlank() || it.startsWith("http")) it else "http://$it" },
        )
        _state.update { it.copy(config = cfg, busy = true, note = null) }
        safeLaunch("saveConfig") {
            val started = System.currentTimeMillis()
            SubsonicSourceStatus.configured(cfg.baseUrl, cfg.user)
            val result = withContext(Dispatchers.IO) {
                runCatching { client(cfg).ping() }
            }
            val elapsed = System.currentTimeMillis() - started
            result.onSuccess { ok ->
                SubsonicSourceStatus.pinged(ok, elapsed)
                if (ok) {
                    withContext(Dispatchers.IO) { runCatching { store.saveSubsonic(cfg.encode()) } }
                    _state.update {
                        it.copy(busy = false, editing = false, verified = true, note = "已连接「${cfg.name}」")
                    }
                    log("在线曲库已连接：${cfg.baseUrl}")
                } else {
                    _state.update { it.copy(busy = false, verified = false, note = "连上了但账号不对（ping 返回失败）") }
                }
            }.onFailure { t ->
                SubsonicSourceStatus.pingFailed(t, elapsed)
                _state.update { it.copy(busy = false, verified = false, note = "连不上：${t.message}") }
            }
        }
    }

    // ---- 搜索 ----

    fun setQuery(q: String) = _state.update { it.copy(query = q) }

    fun search() {
        val q = _state.value.query.trim()
        if (q.isEmpty()) return
        if (_state.value.searching) return
        _state.update { it.copy(searching = true, note = null) }
        safeLaunch("search") {
            val started = System.currentTimeMillis()
            val result = withContext(Dispatchers.IO) {
                runCatching { client().search(q, limit = 50) }
            }
            val elapsed = System.currentTimeMillis() - started
            result.onSuccess { list ->
                SubsonicSourceStatus.searched(q, list.size, elapsed)
                _state.update {
                    it.copy(
                        searching = false,
                        results = list,
                        verified = true,
                        note = if (list.isEmpty()) "没搜到「$q」—— 曲库可能还没收录（在 tt-music 里补全/下载一次）" else "找到 ${list.size} 首",
                    )
                }
                log("搜索「$q」→ ${list.size} 首")
            }.onFailure { t ->
                SubsonicSourceStatus.searchFailed(q, t, elapsed)
                _state.update { it.copy(searching = false, results = emptyList(), note = t.message) }
                log("搜索失败：${t.message}")
            }
        }
    }

    /**
     * 投给当前设备：**从这一首开始播整份搜索结果**。
     *
     * 与音乐库点歌行为一致（点第 5 首 → 列表 5 高亮、往下自动播），
     * 走的也是同一个 `startQueueOnDevice`。
     */
    fun play(track: MediaTrack) {
        if (_state.value.busy) return
        val list = _state.value.results
        val start = list.indexOfFirst { it.id == track.id && it.providerId == track.providerId }
            .takeIf { it >= 0 } ?: 0
        _state.update { it.copy(busy = true, note = null) }
        safeLaunch("play") {
            try {
                val c = client()
                // 直链带凭据，音箱裸 GET 就能拉（不需要转发的源）
                val items = withContext(Dispatchers.IO) {
                    list.mapNotNull { t ->
                        runCatching {
                            QueueItem(
                                url = c.resolve(t),
                                title = t.title,
                                artist = t.artist,
                                album = t.album,
                                durationSec = t.durationSec,
                                mimeType = when (t.suffix?.lowercase()) {
                                    "flac" -> "audio/flac"
                                    "m4a", "mp4" -> "audio/mp4"
                                    "aac" -> "audio/aac"
                                    "ogg", "opus" -> "audio/ogg"
                                    "wav" -> "audio/wav"
                                    else -> "audio/mpeg"
                                },
                                albumArtUrl = c.coverUrl(t, 300),
                            )
                        }.getOrNull()
                    }
                }
                if (items.isEmpty()) throw IllegalStateException("这些结果都拿不到直链")
                val startInItems = start.coerceIn(0, items.lastIndex)
                startQueueOnDevice(
                    context = getApplication(),
                    deviceIp = deviceIp,
                    location = location,
                    deviceUuid = deviceUuid,
                    items = items,
                    startAt = startInItems,
                    note = "在线曲库",
                    log = ::log,
                )
                SubsonicSourceStatus.played(track.title, true, null)
                _state.update { it.copy(busy = false, note = "已投给音箱：${track.title}") }
            } catch (t: Throwable) {
                SubsonicSourceStatus.played(track.title, false, t.message)
                _state.update { it.copy(busy = false, note = "投送失败：${t.message}") }
                log("投送失败：${t.message}")
            }
        }
    }

    fun clearNote() = _state.update { it.copy(note = null) }

    private fun log(line: String) = _state.update { it.copy(log = (it.log + line).takeLast(30)) }

    /** 队列现状（播放页那份真相），界面上标出"正在放哪首"。 */
    val queue: StateFlow<io.github.laozhudu.linkplay.console.playback.QueueRunState> get() = QueueBus.state
}
