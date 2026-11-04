package io.github.laozhudu.linkplay.console.radio

import io.github.laozhudu.linkplay.console.diagnostics.ActionTiming
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.laozhudu.linkplay.console.library.LibraryStore
import io.github.laozhudu.linkplay.protocol.DeviceHttpClient
import io.github.laozhudu.linkplay.protocol.LinkPlayCommands
import io.github.laozhudu.linkplay.protocol.player.PlayState
import io.github.laozhudu.linkplay.protocol.player.PlayerStatus
import io.github.laozhudu.linkplay.protocol.radio.RadioStation
import io.github.laozhudu.linkplay.protocol.radio.RadioStationStore
import io.github.laozhudu.linkplay.protocol.session.PlaybackKey
import io.github.laozhudu.linkplay.protocol.session.PlaybackSession
import io.github.laozhudu.linkplay.protocol.radio.VerifiedRadioCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import io.github.laozhudu.linkplay.console.diagnostics.SourceFailure
import io.github.laozhudu.linkplay.radio.RadioBrowserSource
import io.github.laozhudu.linkplay.radio.playbackHint
import io.github.laozhudu.linkplay.radio.toFavourite
import io.github.laozhudu.radiodiscovery.model.RadioStation as DiscoveryStation
import io.github.laozhudu.linkplay.console.diagnostics.safeLaunch

data class RadioUiState(
    val stations: List<RadioStation> = emptyList(),
    val loading: Boolean = true,
    /** 正在播放的电台 id（0 表示"正在播但没入库"的自填地址）。 */
    val playingId: Long = 0,
    val playingUrl: String? = null,
    val busy: Boolean = false,
    val error: String? = null,
    /** 播放结果说明：成功/失败都写清楚，不假装。 */
    val note: String? = null,
    val log: List<String> = emptyList(),
    /** 「发现电台」：目录查询中。 */
    val discovering: Boolean = false,
    val discoverQuery: String = "",
    val discoverResults: List<DiscoveryStation> = emptyList(),
    /** 查不到 / 连不上 —— 与"搜到 0 条"必须分开（铁律：不给假空态）。 */
    val discoverError: String? = null,
) {
    /** 还没添加过的内置电台，用来做「一键添加」。 */
    val missingVerified: List<VerifiedRadioCatalog.Entry>
        get() {
            val have = stations.map { it.url }.toSet()
            return VerifiedRadioCatalog.entries.filter { it.url !in have }
        }
}

/**
 * 网络电台。
 *
 * ## 为什么单独一页、不再塞进音乐库
 *
 * 电台和「手机本地音乐」一样是**一个独立的来源**，不是音乐库里的一个筛选标签。
 * 藏进音乐库的 tab 里，用户根本找不到入口（上一轮用户的反馈就是这个）。
 * 现在它在「媒体库」下与 NAS / 手机本地音乐 / 音乐库同级。
 *
 * ## 放不出声的真实原因（真机查出来的）
 *
 * 旧固件（DS-1831，fw 3.6.5310）直接 `setPlayerCmd:play:<网络流>` 会回 `OK`，
 * 但 `getPlayerStatus` 始终 `status=stop` —— **必须先切到 Wi-Fi 输入源**：
 *
 * ```
 * setPlayerCmd:switchmode:wifi     → OK   (mode 变 0)
 * setPlayerCmd:play:<url>          → OK
 * getPlayerStatus                  → status=play，curpos 持续递增
 * ```
 *
 * 实测通过（2026-11-02，`.164`）：Radio Paradise、NPR、FIP、CNR 中国之声、CNR 音乐之声。
 * SomaFM 这台放不出来（同为明文 http 但固件不认），所以没进内置列表。
 *
 * ## "使用过的地址自动保存"
 *
 * [playUrl] 播放任意地址后，若它不在库里就自动入库（名字取域名）。
 * 所以手动试过的链接不会丢 —— 下次打开就在列表里。
 */
class RadioViewModel(
    app: Application,
    private val deviceIp: String,
    private val deviceName: String?,
    /** 设备 uuid：播放快照按它存（不能只用名字/IP，前者不稳、后者会漂）。 */
    private val deviceUuid: String? = null,
) : AndroidViewModel(app) {

    private val client = DeviceHttpClient()
    private val store: RadioStationStore = LibraryStore.get(app).radios
    private val sessions = io.github.laozhudu.linkplay.console.session.SessionStore.of(app)
    private val _state = MutableStateFlow(RadioUiState())
    val state: StateFlow<RadioUiState> = _state.asStateFlow()

    init {
        reload()
    }

    fun reload() {
        safeLaunch("reload") {
            val list = withContext(Dispatchers.IO) {
                runCatching { store.forDevice(deviceName) }.getOrDefault(emptyList())
            }
            _state.update { it.copy(stations = list, loading = false) }
            RadioSourceStatus.library(list.size)
        }
    }

    /**
     * 添加一条电台。
     *
     * 地址先经 [RadioStation.normalizeUrl] 收拾（缺协议头就补 http://），
     * 所以「只粘域名不粘 http」也能加成功 —— 这是"添加不了"的主因。
     */
    fun add(name: String, rawUrl: String) {
        val url = RadioStation.normalizeUrl(rawUrl)
        if (url == null) {
            RadioSourceStatus.invalid(rawUrl)
            _state.update { it.copy(error = "这个地址不像 http/https 链接，检查一下有没有打错") }
            return
        }
        val finalName = name.trim().ifBlank { RadioStation.suggestName(url) }
        val invalid = RadioStation.validate(finalName, url)
        if (invalid != null) {
            _state.update { it.copy(error = invalid) }
            return
        }
        safeLaunch("add") {
            val id = withContext(Dispatchers.IO) {
                // 同地址不重复入库（用户常连点两次添加）
                val existing = store.forDevice(deviceName).firstOrNull { it.url == url }
                if (existing != null && existing.id != 0L) existing.id
                else runCatching {
                    store.add(RadioStation(name = finalName, url = url, deviceName = deviceName))
                }.getOrElse { -1L }
            }
            if (id < 0) {
                _state.update { it.copy(error = "保存失败，可能是数据库写入异常") }
            } else {
                log("已保存电台：$finalName")
                _state.update { it.copy(error = null, note = "已添加「$finalName」") }
            }
            reload()
        }
    }

    /** 一键添加入厂自带、且真机验证过的电台。 */
    fun addAllVerified() {
        safeLaunch("addAllVerified") {
            val added = withContext(Dispatchers.IO) {
                val have = store.forDevice(deviceName).map { it.url }.toSet()
                VerifiedRadioCatalog.entries
                    .filter { it.url !in have }
                    .sumOf { e ->
                        runCatching { store.add(VerifiedRadioCatalog.toStation(e, deviceName)); 1 }
                            .getOrDefault(0)
                    }
            }
            log("一键添加 $added 个内置电台")
            _state.update { it.copy(note = if (added > 0) "已添加 $added 个电台" else "内置电台都已经在列表里了") }
            reload()
        }
    }

    fun remove(id: Long) {
        safeLaunch("remove") {
            withContext(Dispatchers.IO) { runCatching { store.remove(id) } }
            if (_state.value.playingId == id) _state.update { it.copy(playingId = 0, playingUrl = null) }
            reload()
        }
    }

    /**
     * 播一个已保存的电台。
     *
     * 若设备回 `stop`（说明这下没放起来），把该条目标记 `invalid` 并把真实状态写进 note，
     * 不谎报"正在播放"。
     */
    fun play(station: RadioStation) = playUrl(station.url, station.name, storeId = station.id)

    /**
     * 播任意地址，并在成功后**自动入库**（用户说的"使用过的电台链接自动保存"）。
     */
    fun playUrl(rawUrl: String, name: String? = null, storeId: Long = 0L) {
        val url = RadioStation.normalizeUrl(rawUrl) ?: run {
            _state.update { it.copy(error = "地址不合法") }
            return
        }
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, note = null, error = null) }
        safeLaunch("playUrl") {
            val timing = ActionTiming.begin(ActionTiming.Action.RADIO_PLAY)
            val startedAt = System.currentTimeMillis()
            val outcome = withContext(Dispatchers.IO) {
                try {
                    // 第一步必须先切输入源，否则旧固件只把地址存进 uri 不会真的播
                    client.send(deviceIp, LinkPlayCommands.switchMode("wifi"))
                    delay(300)
                    client.send(deviceIp, LinkPlayCommands.playUrl(url))
                    // 给设备 6 秒：网络流首包慢，太急会误判成失败
                    var playing = false
                    var label = "?"
                    repeat(6) {
                        delay(1000)
                        val st = runCatching {
                            PlayerStatus.parse(client.send(deviceIp, LinkPlayCommands.getPlayerStatus()))
                        }.getOrNull()
                        label = st?.playState?.name ?: "?"
                        if (st?.playState == PlayState.PLAY) {
                            playing = true
                            return@repeat
                        }
                    }
                    // 命令全回了 OK，但状态没变成 PLAY —— https 在老固件上就是这个表现。
                    // 所以这里不能只看"命令成功"，必须看 status。
                    when {
                        playing -> RadioOutcome(true, "status=$label", null)
                        label == "?" -> RadioOutcome(false, "6 秒内取不到播放状态", SourceFailure.TIMEOUT)
                        else -> RadioOutcome(false, "status=$label", SourceFailure.UNSUPPORTED)
                    }
                } catch (t: Throwable) {
                    RadioOutcome(false, t.message ?: t.javaClass.simpleName, SourceFailure.of(t))
                }
            }
            val started = outcome.ok
            val detail = outcome.detail
            val elapsed = System.currentTimeMillis() - startedAt

            if (started) {
                RadioSourceStatus.played(url, detail, elapsed)
                // 电台也是"在设备上播"，重开 App 要让播放页有东西可显示
                withContext(Dispatchers.IO) {
                    runCatching {
                        sessions.savePlayback(
                            PlaybackKey.of(deviceUuid, deviceIp),
                            PlaybackSession(
                                deviceUuid = deviceName ?: deviceIp,
                                deviceIp = deviceIp,
                                items = listOf(
                                    io.github.laozhudu.linkplay.protocol.queue.QueueItem(
                                        url = url,
                                        title = name ?: RadioStation.suggestName(url),
                                    ),
                                ),
                                index = 0,
                                loopMode = 0,
                            ),
                        )
                    }
                }
                val id = if (storeId != 0L) storeId else autoSave(url, name)
                _state.update {
                    it.copy(
                        busy = false,
                        playingId = id,
                        playingUrl = url,
                        note = "正在播放「${name ?: RadioStation.suggestName(url)}」",
                    )
                }
                log("播放成功：$url")
            } else {
                RadioSourceStatus.failed(
                    url,
                    outcome.kind ?: SourceFailure.UNSUPPORTED,
                    detail,
                    elapsed,
                )
                if (storeId != 0L) withContext(Dispatchers.IO) { runCatching { store.markInvalid(storeId, true) } }
                _state.update {
                    it.copy(
                        busy = false,
                        playingId = 0,
                        playingUrl = null,
                        note = "这条流没能播起来（设备返回 $detail）。可能地址失效、或设备到不了该站。",
                    )
                }
                log("播放失败：$url → $detail")
            }
            timing.end(note = "电台「${name ?: RadioStation.suggestName(url)}」", ok = started)
        }
    }

    /**
     * 从全球目录发现电台（空关键词 = 看热门）。
     *
     * 为什么要有这条：库里的台只能靠用户自己粘贴地址，而"想听中国之声"的人
     * 并不知道它现在的流地址。`core-radio` 提供的是**目录查询**，不是播放源 ——
     * 查到的台最终仍然走我们自己的入库 + 投送链路（docs/74 硬规则 1）。
     */
    fun discover(query: String) {
        if (_state.value.discovering) return
        val q = query.trim()
        _state.update { it.copy(discovering = true, discoverQuery = q, discoverError = null) }
        safeLaunch("discoverRadio") {
            val timing = ActionTiming.begin(ActionTiming.Action.RADIO_DISCOVER)
            val started = System.currentTimeMillis()
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val src = RadioBrowserSource()
                    if (q.isBlank()) src.popular(30) else src.search(q, 30)
                }
            }
            val ms = System.currentTimeMillis() - started
            result.onSuccess { list ->
                _state.update { it.copy(discovering = false, discoverResults = list) }
                RadioSourceStatus.discovered(q, list.size, ms)
            }.onFailure { t ->
                _state.update {
                    it.copy(
                        discovering = false,
                        discoverResults = emptyList(),
                        discoverError = t.message ?: "查不到（网络或目录服务不可用）",
                    )
                }
                RadioSourceStatus.discoverFailed(q, t, ms)
            }
            timing.end(note = if (q.isBlank()) "热门" else "「$q」", ok = result.isSuccess)
        }
    }

    /**
     * 目录里的一条 → 存进收藏。
     *
     * 先 `resolve()` 拿真实地址（中间页存下来下次还得再解一次），失败就退回原地址。
     * 然后走**既有的**入库路径 `add()`：同地址不重复、写完自动 reload。
     */
    fun addDiscovered(station: DiscoveryStation) {
        safeLaunch("addDiscovered") {
            ActionTiming.measure(ActionTiming.Action.RADIO_ADD, note = station.name) {
                val resolved = withContext(Dispatchers.IO) {
                    runCatching { RadioBrowserSource().resolve(station) }.getOrDefault(station)
                }
                val fav = resolved.toFavourite(deviceName)
                add(fav.name, fav.url)
            }
        }
    }

    /** 使用过的地址自动入库：同 URL 已在库里就复用它的 id。 */
    private suspend fun autoSave(url: String, name: String?): Long = withContext(Dispatchers.IO) {
        val existing = runCatching { store.forDevice(deviceName).firstOrNull { it.url == url } }.getOrNull()
        if (existing != null) return@withContext existing.id
        val finalName = name?.takeIf { it.isNotBlank() } ?: RadioStation.suggestName(url)
        runCatching {
            store.add(RadioStation(name = finalName, url = url, deviceName = deviceName))
        }.getOrDefault(0L)
    }

    fun stop() {
        safeLaunch("stop") {
            withContext(Dispatchers.IO) { runCatching { client.send(deviceIp, LinkPlayCommands.stopPlayer()) } }
            _state.update { it.copy(playingId = 0, playingUrl = null, note = "已停止") }
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }

    private fun log(line: String) = _state.update { it.copy(log = (it.log + line).takeLast(30)) }
}
