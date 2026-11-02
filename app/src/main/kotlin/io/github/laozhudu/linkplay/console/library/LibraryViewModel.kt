package io.github.laozhudu.linkplay.console.library

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.laozhudu.linkplay.protocol.LinkPlayCapabilities
import io.github.laozhudu.linkplay.protocol.LinkPlayDevice
import io.github.laozhudu.linkplay.protocol.library.LibrarySearch
import io.github.laozhudu.linkplay.protocol.library.SearchHit
import io.github.laozhudu.linkplay.console.playback.QueueBus
import io.github.laozhudu.linkplay.console.playback.QueuePlaybackService
import io.github.laozhudu.linkplay.protocol.library.TrackRecord
import io.github.laozhudu.linkplay.protocol.queue.PlayFromList
import io.github.laozhudu.linkplay.protocol.queue.QueueItem
import io.github.laozhudu.linkplay.protocol.queue.ClientSideQueue
import io.github.laozhudu.linkplay.protocol.session.PlaybackSession
import io.github.laozhudu.linkplay.protocol.radio.RadioStation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class LibraryTab { SEARCH, FAVORITES, RECENT }

data class LibraryUiState(
    val tab: LibraryTab = LibraryTab.SEARCH,
    val query: String = "",
    val loading: Boolean = true,
    val hits: List<SearchHit> = emptyList(),
    val favorites: List<TrackRecord> = emptyList(),
    val recents: List<TrackRecord> = emptyList(),
    val radios: List<RadioStation> = emptyList(),
    val capabilities: LinkPlayCapabilities? = null,
    val busy: Boolean = false,
    val nowPlaying: String? = null,
    val error: String? = null,
    val log: List<String> = emptyList(),
)

/**
 * 音乐库（搜索/收藏/最近/电台）。
 *
 * 这一页刻意**全部走 [LinkPlayDevice] 门面**而不是直接拼命令——
 * 它同时也是「对外集成」的活样例：外部项目照着这一页的调用方式就能接进来。
 */
class LibraryViewModel(
    app: Application,
    private val deviceIp: String,
    private val location: String?,
    /** 设备显示名（用来给电台做「按设备分组」；拿不到就退回 IP）。 */
    private val deviceLabel: String? = null,
) : AndroidViewModel(app) {

    private val store = LibraryStore.get(app)
    private val _state = MutableStateFlow(LibraryUiState())
    val state: StateFlow<LibraryUiState> = _state.asStateFlow()

    /** 本地曲库缓存，避免每次搜索都扫 MediaStore。 */
    private var localTracks: List<LocalTrack> = emptyList()

    init {
        refreshAll()
        viewModelScope.launch { detectCapabilities() }
    }

    private suspend fun detectCapabilities() {
        val caps = withContext(Dispatchers.IO) {
            LinkPlayDevice.probe(deviceIp, location)
        }
        _state.update { it.copy(capabilities = caps) }
        log(caps?.summary() ?: "能力探测失败（设备不可达）")
    }

    fun refreshAll() {
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    localTracks = LocalLibrary.query(getApplication())
                    Triple(
                        store.favorites.all(),
                        store.recents.recent(),
                        store.radios.forDevice(deviceName()),
                    )
                }
            }
            result.onSuccess { (favs, recents, radios) ->
                _state.update {
                    it.copy(
                        loading = false,
                        favorites = favs,
                        recents = recents,
                        radios = radios,
                        error = null,
                    )
                }
                log("本地 ${localTracks.size} 首 / 收藏 ${favs.size} / 最近 ${recents.size} / 电台 ${radios.size}")
            }.onFailure { t ->
                _state.update { it.copy(loading = false, error = t.message) }
            }
            runSearch(_state.value.query)
        }
    }

    fun setTab(tab: LibraryTab) = _state.update { it.copy(tab = tab) }

    fun setQuery(value: String) {
        _state.update { it.copy(query = value) }
        runSearch(value)
    }

    private fun runSearch(query: String) {
        val q = query.trim()
        if (q.isEmpty()) {
            _state.update { it.copy(hits = emptyList()) }
            return
        }
        // 搜索范围 = 本地曲库 + 收藏 + 电台（收藏里可能有已不在本地的条目）
        val pool = (localTracks.map { it.toRecord() } + _state.value.favorites + _state.value.recents)
            .distinctBy { it.dedupeKey }
        val hits = LibrarySearch.search(pool, q, limit = 60)
        _state.update { it.copy(hits = hits) }
    }

    // ---- 播放 ----

    /**
     * 点一首 → **从这首开始播整个列表**。
     *
     * 为什么不再只投一首：原厂 App（以及所有音乐 App）的行为都是
     * 「点第 5 首，列表里 5 高亮，播完自动第 6 首」。只投一首的话，
     * 播完就断，用户还得回列表再点——这就是用户说的「媒体库逻辑不对」。
     *
     * 队列来源按当前分区取：搜索结果是 hits，收藏是 favorites，最近是 recents。
     * 设备侧不能自己排队（固件 CreateQueue 只收空 Tracks，实测），
     * 所以队列交给 [QueuePlaybackService] 逐首投流。
     */
    fun play(track: TrackRecord, siblings: List<TrackRecord>? = null) {
        if (_state.value.busy) return
        val list = (siblings ?: currentList().ifEmpty { listOf(track) })
            .ifEmpty { listOf(track) }
        val start = PlayFromList.startIndex(list, track.dedupeKey) { it.dedupeKey }
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                // 先把整条列表的地址都解析出来：本机文件要登记到 LocalMediaServer 才有可投的 URL
                val items = withContext(Dispatchers.IO) {
                    list.mapNotNull { t ->
                        runCatching { resolvePlayableUrl(t) }.getOrNull()?.let { url ->
                            QueueItem(
                                url = url,
                                title = t.title,
                                artist = t.displayArtist,
                                durationSec = t.durationSec,
                                mimeType = "audio/mpeg",
                                albumArtUrl = t.albumArtUri,
                            )
                        }
                    }
                }
                if (items.isEmpty()) throw IllegalStateException("这些曲目都没有可播的地址")

                val session = PlaybackSession(
                    deviceUuid = deviceLabel ?: deviceIp,
                    deviceIp = deviceIp,
                    items = items,
                    index = start.coerceIn(0, items.lastIndex),
                    loopMode = ClientSideQueue.LOOP_ALL,
                )
                withContext(Dispatchers.IO) {
                    QueuePlaybackService.start(getApplication(), location, session)
                }
                val recent = withContext(Dispatchers.IO) {
                    store.recents.record(track, System.currentTimeMillis())
                    store.recents.recent()
                }
                _state.update {
                    it.copy(busy = false, nowPlaying = track.displayName, recents = recent)
                }
                log(if (items.size > 1) "从「${track.displayName}」开始播放 ${items.size} 首" else "正在播放：${track.displayName}")
            } catch (t: Throwable) {
                _state.update { it.copy(busy = false, error = t.message) }
                log("播放失败：${t.message}")
            }
        }
    }

    /** 当前分区里的曲目列表（点一首时要连带推它所在的那一屏）。 */
    private fun currentList(): List<TrackRecord> = when (_state.value.tab) {
        LibraryTab.SEARCH -> _state.value.hits.map { it.track }
        LibraryTab.FAVORITES -> _state.value.favorites
        LibraryTab.RECENT -> _state.value.recents
    }

    private fun resolvePlayableUrl(track: TrackRecord): String {
        if (track.sourceType != TrackRecord.SOURCE_LOCAL) return track.playUri
        val ctx = getApplication<Application>()
        LocalMediaRegistry.ensureServer(ctx, localTracks)
        MediaServerService.start(ctx)
        val base = LocalMediaRegistry.baseUrl() ?: throw IllegalStateException("拿不到本机局域网 IP")
        return "$base/t/${track.songId}"
    }

    fun toggleFavorite(track: TrackRecord) {
        viewModelScope.launch {
            val isFav = withContext(Dispatchers.IO) { store.favorites.contains(track.dedupeKey) }
            withContext(Dispatchers.IO) {
                if (isFav) store.favorites.remove(track.dedupeKey) else store.favorites.add(track)
            }
            val favs = withContext(Dispatchers.IO) { store.favorites.all() }
            _state.update { it.copy(favorites = favs) }
            log(if (isFav) "已取消收藏：${track.title}" else "已收藏：${track.title}")
        }
    }

    // 电台已迁到 radio/RadioViewModel（独立的媒体库二级页）。
    // 这里保留 `radios` 字段只用于日志统计，不再承担增删改——两套实现一定会漂移。

    // ---- 预设键 ----

    fun pressPreset(index: Int) {
        viewModelScope.launch {
            val failure = runCatching {
                withContext(Dispatchers.IO) {
                    LinkPlayDevice.open(deviceIp, location).pressPreset(index)
                }
            }.exceptionOrNull()
            log(if (failure == null) "预设键 $index 已触发" else "预设键 $index 失败：${failure.message}")
            failure?.let { t -> _state.update { s -> s.copy(error = t.message) } }
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }

    private fun deviceName(): String? = deviceLabel ?: deviceIp

    private fun log(line: String) = _state.update { it.copy(log = (it.log + line).takeLast(40)) }

    private fun LocalTrack.toRecord() = TrackRecord(
        playUri = "local://${id}",
        title = title,
        artist = artist,
        album = album,
        durationSec = durationSec,
        folderName = path.substringBeforeLast('/', ""),
        sourceType = TrackRecord.SOURCE_LOCAL,
        songId = id,
    )
}
