package io.github.laozhudu.linkplay.console.library

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.laozhudu.linkplay.protocol.LinkPlayCapabilities
import io.github.laozhudu.linkplay.protocol.LinkPlayDevice
import io.github.laozhudu.linkplay.protocol.library.LibrarySearch
import io.github.laozhudu.linkplay.protocol.library.SearchHit
import io.github.laozhudu.linkplay.console.playback.QueueBus
import io.github.laozhudu.linkplay.console.playback.startQueueOnDevice
import io.github.laozhudu.linkplay.console.playback.DeviceQueuePush
import io.github.laozhudu.linkplay.protocol.DeviceHttpClient
import io.github.laozhudu.linkplay.console.playback.QueuePlaybackService
import io.github.laozhudu.linkplay.protocol.library.TrackRecord
import io.github.laozhudu.linkplay.protocol.queue.PlayFromList
import io.github.laozhudu.linkplay.protocol.queue.QueueItem
import io.github.laozhudu.linkplay.protocol.queue.ClientSideQueue
import io.github.laozhudu.linkplay.protocol.session.PlaybackKey
import io.github.laozhudu.linkplay.protocol.playlist.PlaylistOps
import io.github.laozhudu.linkplay.protocol.session.PlaybackSession
import io.github.laozhudu.linkplay.protocol.radio.RadioStation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import io.github.laozhudu.linkplay.console.diagnostics.safeLaunch

enum class LibraryTab { SEARCH, FAVORITES, RECENT, PLAYLISTS }

/** `refreshAll` 一次性读回来的四份数据（Kotlin 标准库只有 Triple）。 */
private data class PlaylistBundle<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

data class LibraryUiState(
    val tab: LibraryTab = LibraryTab.SEARCH,
    val query: String = "",
    val loading: Boolean = true,
    val hits: List<SearchHit> = emptyList(),
    val favorites: List<TrackRecord> = emptyList(),
    val recents: List<TrackRecord> = emptyList(),
    val radios: List<RadioStation> = emptyList(),
    /** 歌单（原厂「全部歌单」）。顺序即播放顺序。 */
    val playlists: List<PlaylistOps.Playlist> = emptyList(),
    /** 当前展开的歌单 id；null 表示在看列表。 */
    val openPlaylistId: Long? = null,
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
    /**
     * 设备 uuid。**播放快照必须按它存**，不能再用 SSID：
     * 之前这里存的键是 deviceLabel（SSID），而恢复时按 uuid / `ip:` 找，永远对不上 ——
     * 这就是「重开 App 列表不同步」的最后一个缺口。
     */
    private val deviceUuid: String? = null,
) : AndroidViewModel(app) {

    private val store = LibraryStore.get(app)
    /** 歌单存**独立的库文件**（linkplay_playlist.db）：坏了只丢歌单，收藏不动。 */
    private val playlists = PlaylistStore.get(app)
    /** 播放快照需要另一个库（会话 vs 业务内容分开，见 docs/12） */
    private val sessions = io.github.laozhudu.linkplay.console.session.SessionStore.of(app)
    private val _state = MutableStateFlow(LibraryUiState())
    val state: StateFlow<LibraryUiState> = _state.asStateFlow()

    /** 本地曲库缓存，避免每次搜索都扫 MediaStore。 */
    private var localTracks: List<LocalTrack> = emptyList()

    init {
        refreshAll()
        safeLaunch("LibraryViewModel") { detectCapabilities() }
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
        safeLaunch("LibraryViewModel") {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    localTracks = LocalLibrary.query(getApplication())
                    PlaylistBundle(
                        store.favorites.all(),
                        store.recents.recent(),
                        store.radios.forDevice(deviceName()),
                        playlists.lists(),
                    )
                }
            }
            result.onSuccess { bundle ->
                val favs = bundle.a
                val recents = bundle.b
                val radios = bundle.c
                val lists = bundle.d
                _state.update {
                    it.copy(
                        loading = false,
                        favorites = favs,
                        recents = recents,
                        radios = radios,
                        playlists = lists,
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
        safeLaunch("LibraryViewModel") {
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

                val startAt = start.coerceIn(0, items.lastIndex)
                // 一条路走到底：PlayQueue 优先 → m3u → 客户端投流（与歌单播放共用 startQueue）
                startQueue(items, startAt, "音乐库")
                // 最近播放的写入时机就是**真正起播之后**：设备拒收时不算听过
                val recent = withContext(Dispatchers.IO) {
                    store.recents.record(track, System.currentTimeMillis())
                    store.recents.recent()
                }
                _state.update { it.copy(busy = false, nowPlaying = track.displayName, recents = recent) }
                log(if (items.size > 1) "从「${track.displayName}」开始播放 ${items.size} 首" else "正在播放：${track.displayName}")
            } catch (t: Throwable) {
                _state.update { it.copy(busy = false, error = t.message) }
                log("播放失败：${t.message}")
            }
        }
    }

    // ---- 歌单（原厂「全部歌单」）----

    fun openPlaylist(id: Long?) = _state.update { it.copy(openPlaylistId = id) }

    /** 新建歌单。同名视为同一个（[PlaylistOps.indexOfName] 的规则），不报错。 */
    fun createPlaylist(rawName: String) {
        val name = PlaylistOps.normalizeName(rawName)
        if (name == null) {
            _state.update { it.copy(error = "歌单名不能为空") }
            return
        }
        safeLaunch("LibraryViewModel") {
            val id = withContext(Dispatchers.IO) { playlists.createOrGet(name) }
            if (id <= 0) {
                _state.update { it.copy(error = "歌单数量已到上限（${PlaylistOps.MAX_LISTS} 个）") }
                return@safeLaunch
            }
            val lists = withContext(Dispatchers.IO) { playlists.lists() }
            _state.update { it.copy(playlists = lists, openPlaylistId = id, error = null) }
            log("歌单「$name」已就绪（id=$id）")
        }
    }

    /**
     * 把一批曲目加到歌单（不传歌单名就新建/复用同名）。
     *
     * 地址解析与 [play] 共用 [resolvePlayableUrl] —— 手机本地文件必须先登记到
     * 本机 HTTP 服务才有可投的 URL，否则存进歌单的是一条投不出去的路径。
     */
    fun addToPlaylist(rawName: String, tracks: List<TrackRecord>) {
        if (tracks.isEmpty()) return
        val name = PlaylistOps.normalizeName(rawName) ?: return
        _state.update { it.copy(busy = true, error = null) }
        safeLaunch("LibraryViewModel") {
            try {
                val items = withContext(Dispatchers.IO) {
                    tracks.mapNotNull { t ->
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
                if (items.isEmpty()) throw IllegalStateException("这些曲目都没有可保存的地址")
                val (added, lists) = withContext(Dispatchers.IO) {
                    val before = playlists.lists()
                    val id = playlists.createOrGet(name)
                    val list = playlists.addTracks(id, items)
                    val old = before.firstOrNull { it.id == id }?.size ?: 0
                    (list.size - old) to playlists.lists()
                }
                _state.update { it.copy(busy = false, playlists = lists, error = null) }
                log(
                    if (added <= 0) "「$name」里已经有这些歌了"
                    else "已加入「$name」$added 首（去重后）",
                )
            } catch (t: Throwable) {
                _state.update { it.copy(busy = false, error = t.message) }
            }
        }
    }

    /** 播整个歌单（从第 [startIndex] 首开始）。 */
    fun playPlaylist(listId: Long, startIndex: Int = 0) {
        val list = _state.value.playlists.firstOrNull { it.id == listId } ?: return
        if (list.tracks.isEmpty()) {
            _state.update { it.copy(error = "「${list.name}」里还没有歌") }
            return
        }
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, error = null) }
        safeLaunch("LibraryViewModel") {
            try {
                startQueue(list.tracks, startIndex.coerceIn(0, list.tracks.lastIndex), "歌单「${list.name}」")
                val first = list.tracks.getOrNull(startIndex) ?: list.tracks.first()
                _state.update { it.copy(busy = false, nowPlaying = first.title) }
            } catch (t: Throwable) {
                _state.update { it.copy(busy = false, error = t.message) }
            }
        }
    }

    fun removeFromPlaylist(listId: Long, index: Int) {
        safeLaunch("LibraryViewModel") {
            val lists = withContext(Dispatchers.IO) {
                playlists.removeTrackAt(listId, index)
                playlists.lists()
            }
            _state.update { it.copy(playlists = lists) }
        }
    }

    fun moveInPlaylist(listId: Long, from: Int, to: Int) {
        safeLaunch("LibraryViewModel") {
            val lists = withContext(Dispatchers.IO) {
                playlists.moveTrack(listId, from, to)
                playlists.lists()
            }
            _state.update { it.copy(playlists = lists) }
        }
    }

    fun renamePlaylist(listId: Long, rawName: String) {
        safeLaunch("LibraryViewModel") {
            val ok = withContext(Dispatchers.IO) { playlists.rename(listId, rawName) }
            val lists = withContext(Dispatchers.IO) { playlists.lists() }
            _state.update {
                it.copy(
                    playlists = lists,
                    error = if (ok) null else "改名失败：名字为空或与已有歌单重名",
                )
            }
        }
    }

    fun deletePlaylist(listId: Long) {
        safeLaunch("LibraryViewModel") {
            withContext(Dispatchers.IO) { playlists.delete(listId) }
            val lists = withContext(Dispatchers.IO) { playlists.lists() }
            _state.update {
                it.copy(
                    playlists = lists,
                    openPlaylistId = it.openPlaylistId?.takeIf { id -> id != listId },
                )
            }
        }
    }

    /**
     * 把一份队列交给设备并从 [startAt] 首起播 —— [play] 与 [playPlaylist] 共用。
     *
     * 顺序就是原厂顺序：PlayQueue 优先 → m3u 兜底 → 客户端逐首投流。
     * 抽出来是因为**两条入口必须同一条路**，否则「从音乐库播」和「从歌单播」
     * 会出现设备侧行为不一致（历史上就是不同入口各写一遍才漂移的）。
     */
    /**
     * 整单下发 —— 委托给全 App 唯一入口 [startQueueOnDevice]。
     *
     * 这里不保留任何自己的一套降级逻辑：以前音乐库 / 歌单 / NAS 各写一遍，
     * 设备侧行为就不一致（有的入口有设备队列、有的没有）。
     */
    private suspend fun startQueue(items: List<QueueItem>, startAt: Int, note: String) {
        startQueueOnDevice(
            context = getApplication(),
            deviceIp = deviceIp,
            location = location,
            deviceUuid = deviceUuid,
            items = items,
            startAt = startAt,
            note = note,
            log = ::log,
        )
    }

    /** 当前分区里的曲目列表（点一首时要连带推它所在的那一屏）。 */
    private fun currentList(): List<TrackRecord> = when (_state.value.tab) {
        LibraryTab.SEARCH -> _state.value.hits.map { it.track }
        LibraryTab.FAVORITES -> _state.value.favorites
        LibraryTab.RECENT -> _state.value.recents
        // 歌单分区没有"当前列表"：播放走 playPlaylist，点单首也走整单
        LibraryTab.PLAYLISTS -> emptyList()
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
        safeLaunch("LibraryViewModel") {
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
        safeLaunch("LibraryViewModel") {
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
