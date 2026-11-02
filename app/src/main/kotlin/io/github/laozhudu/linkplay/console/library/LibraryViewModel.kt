package io.github.laozhudu.linkplay.console.library

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.laozhudu.linkplay.protocol.LinkPlayCapabilities
import io.github.laozhudu.linkplay.protocol.LinkPlayDevice
import io.github.laozhudu.linkplay.protocol.library.LibrarySearch
import io.github.laozhudu.linkplay.protocol.library.SearchHit
import io.github.laozhudu.linkplay.console.playback.QueueBus
import io.github.laozhudu.linkplay.protocol.library.TrackRecord
import io.github.laozhudu.linkplay.protocol.queue.QueueItem
import io.github.laozhudu.linkplay.protocol.radio.RadioStation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class LibraryTab { SEARCH, FAVORITES, RECENT, RADIO }

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

    fun play(track: TrackRecord) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                val url = withContext(Dispatchers.IO) { resolvePlayableUrl(track) }
                val ok = withContext(Dispatchers.IO) {
                    val device = LinkPlayDevice.open(deviceIp, location)
                    val engine = device.playback()
                        ?: throw IllegalStateException("这台设备没有可用的播放通道")
                    engine.playUrl(url, track.title)
                    true
                }
                if (ok) {
                    // 单曲播放也要进队列：播放页的「播放列表」读的就是 QueueBus，
                    // 不写进去用户就会看到「在播歌但列表是空的」
                    val item = QueueItem(
                        url = url,
                        title = track.title,
                        artist = track.displayArtist,
                        album = null,
                        durationSec = track.durationSec,
                        mimeType = "audio/mpeg",
                        albumArtUrl = track.albumArtUri,
                    )
                    QueueBus.begin(listOf(item))
                    QueueBus.playing(track.title, 0)
                    val recent = withContext(Dispatchers.IO) {
                        store.recents.record(track.copy(playUri = url), System.currentTimeMillis())
                        store.recents.recent()
                    }
                    _state.update {
                        it.copy(busy = false, nowPlaying = track.displayName, recents = recent)
                    }
                    log("正在播放：${track.displayName}")
                }
            } catch (t: Throwable) {
                _state.update { it.copy(busy = false, error = t.message) }
                log("播放失败：${t.message}")
            }
        }
    }

    /** 本地曲目要先把手机 HTTP 服务拉起来，网络流直接用原地址。 */
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

    // ---- 电台 ----

    fun addRadio(name: String, url: String) {
        val invalid = RadioStation.validate(name, url)
        if (invalid != null) {
            _state.update { it.copy(error = invalid) }
            return
        }
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                store.radios.add(
                    RadioStation(
                        name = name.trim(),
                        url = url.trim(),
                        deviceName = deviceName(),
                    ),
                )
            }
            val radios = withContext(Dispatchers.IO) { store.radios.forDevice(deviceName()) }
            _state.update { it.copy(radios = radios, error = null) }
            log("已添加电台：$name")
        }
    }

    fun removeRadio(id: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { store.radios.remove(id) }
            val radios = withContext(Dispatchers.IO) { store.radios.forDevice(deviceName()) }
            _state.update { it.copy(radios = radios) }
        }
    }

    fun playRadio(station: RadioStation) = play(
        TrackRecord(
            playUri = station.url,
            title = station.name,
            sourceType = TrackRecord.SOURCE_RADIO,
        ),
    )

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
