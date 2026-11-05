package io.github.laozhudu.linkplay.console.lx

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.laozhudu.linkplay.console.LxSourceStatus
import io.github.laozhudu.linkplay.console.playback.PlaybackRouter
import io.github.laozhudu.linkplay.lx.LxScriptInfo
import io.github.laozhudu.linkplay.lx.LxTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 洛雪搜索页的状态。 */
data class LxSearchUiState(
    val sources: List<String> = listOf("wy"),
    val source: String = "wy",
    val keyword: String = "",
    val searching: Boolean = false,
    val results: List<LxTrack> = emptyList(),
    /** 搜索结果为空时用来区分"没搜到"和"没连上" —— 两句完全不同的话。 */
    val searchedKeyword: String? = null,
    val error: String? = null,
    val busy: Boolean = false,
    val note: String? = null,
    /** 点播放时发现没装脚本 —— 界面据此直接弹导入框，而不是让用户自己去找。 */
    val needScript: Boolean = false,
)

/**
 * 洛雪搜索 → 点一首 → 取直链 → 投到音箱。
 *
 * ## 关键：投送**只走** `startQueueOnDevice`
 *
 * 这里是全 App 唯一的"把列表交给音箱"入口（三段降级、m3u 旋转、设备队列都在里面）。
 * 自己拼 URL 直接发命令会绕过队列归属，多房间播放立刻失效（`docs/74` 硬规则 1）。
 *
 * ## 为什么点一首要三跳而不是一跳
 *
 * 搜索结果只有 `source + songmid`，**没有地址**。地址要问用户脚本（`musicUrl`），
 * 而脚本必须先 `load()` 才能问。所以每次换脚本会重建取流引擎，同一支则复用。
 */
class LxSearchViewModel(
    app: Application,
    private val deviceIp: String,
    private val location: String?,
    private val deviceUuid: String?,
) : AndroidViewModel(app) {

    /**
     * 等脚本的用户点的那一首（导完脚本自动接着放）。
     *
     * 必须声明在所有属性最前面：`init` 里起的协程可能先跑起来，
     * 那时声明在 init 之后的属性还是 null —— 这是本项目踩过的坑（docs/55 陷阱表）。
     */
    private var pending: LxTrack? = null

    private val _state = MutableStateFlow(LxSearchUiState())
    val state: StateFlow<LxSearchUiState> = _state.asStateFlow()

    init {
        // 源列表要问引擎（sources 是脚本生态里定义的，不是我们写死的）
        viewModelScope.launch {
            runCatching {
                val sdk = LxSession.sdk(getApplication())
                sdk.sources().value?.takeIf { it.isNotEmpty() }
            }.onSuccess { list ->
                if (list != null) {
                    _state.update { it.copy(sources = list, source = list.first()) }
                }
            }
        }
    }

    /** 用户点"知道了"：把上一次的错误清掉（否则搜到了也会一直顶着红字）。 */
    fun clearError() = _state.update { it.copy(error = null) }

    fun setSource(source: String) = _state.update { it.copy(source = source, results = emptyList(), searchedKeyword = null) }

    fun search(keyword: String) {
        val q = keyword.trim()
        if (q.isEmpty() || _state.value.searching) return
        _state.update { it.copy(searching = true, error = null, note = null) }
        viewModelScope.launch {
            // 裸 launch 会吞异常并可能打死进程，所以整段 runCatching；
            // 失败一律变成界面上的一句话，同时进诊断（铁律一：成功路径也要记）
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val sdk = LxSession.sdk(getApplication())
                    val source = _state.value.source
                    val started = System.currentTimeMillis()
                    val page = sdk.search(source, q, limit = 30)
                    Triple(page, source, System.currentTimeMillis() - started)
                }
            }
            result.onSuccess { (page, source, ms) ->
                val value = page.value
                if (value == null) {
                    LxSourceStatus.failed("搜索", page.error)
                    _state.update { it.copy(searching = false, results = emptyList(), searchedKeyword = q, error = page.error ?: "搜索失败") }
                } else {
                    LxSourceStatus.searched(source, q, value.tracks.size, ms)
                    _state.update { it.copy(searching = false, results = value.tracks, searchedKeyword = q, error = null) }
                }
            }.onFailure { error ->
                LxSourceStatus.failed("搜索", error.message)
                _state.update { it.copy(searching = false, results = emptyList(), searchedKeyword = q, error = error.message ?: error.javaClass.simpleName) }
            }
        }
    }

    /** 用户取消导入：把等待清掉，不然下次点播放会莫名其妙续播旧的那首。 */
    fun cancelPending() {
        pending = null
        _state.update { it.copy(needScript = false, note = null) }
    }

    /** 脚本导入成功：如果刚才是因为没脚本才卡住，现在直接把它放出来。 */
    fun onScriptImported() {
        val track = pending
        pending = null
        _state.update { it.copy(needScript = false) }
        if (track != null) play(track)
    }

    /**
     * 点一首：问脚本要直链，然后走唯一投送入口。
     *
     * **没装脚本不算错误**：直接把导入框弹出来，导完接着放这首。
     * 让用户"点播放 → 看到报错 → 自己去别的页面找脚本 → 回来再点一次"，
     * 那是四个步骤换一件事，而它本来可以是两次点击。
     */
    fun play(track: LxTrack) {
        if (_state.value.busy) return
        val app = getApplication<Application>()
        val hasScript = runCatching { ScriptLibrary.repository(app).active() }.getOrNull() != null
        if (!hasScript) {
            pending = track
            _state.update { it.copy(needScript = true, error = null, note = "先导一支脚本，导完自动播这首") }
            return
        }

        _state.update { it.copy(busy = true, note = null, error = null) }
        viewModelScope.launch {
            val outcome = runCatching {
                val repo = ScriptLibrary.repository(app)
                val active = repo.active() ?: throw IllegalStateException("脚本刚被删掉了")
                val text = repo.textOf(active.fileName) ?: throw IllegalStateException("脚本文件读不出来：${active.name}")
                val info = LxScriptInfo.parse(text) ?: throw IllegalStateException("脚本头解析不出来：${active.name}")

                val api = LxSession.userApi(app, text, info)
                val started = System.currentTimeMillis()
                val url = api.musicUrl(track.source, track.toMusicInfoJson())
                val resolved = url.value ?: throw IllegalStateException(url.error ?: "脚本没给地址")
                LxSourceStatus.resolveOk(track.source, track.name, System.currentTimeMillis() - started)

                val title = track.singer.takeIf { it.isNotBlank() }?.let { "${track.name} - $it" } ?: track.name
                // 本地播 / 推流由 PlaybackRouter 分派 —— 界面和这里都不判断"从哪出声"
                val ok = PlaybackRouter.of(app).playOne(
                    url = resolved,
                    title = title,
                    deviceIp = deviceIp,
                    location = location,
                    deviceUuid = deviceUuid,
                )
                LxSourceStatus.cast(ok, title)
                if (!ok) throw IllegalStateException("地址拿到了，但设备那边没起播")
                title
            }
            outcome.onSuccess { title ->
                _state.update { it.copy(busy = false, note = "正在播「$title」") }
            }.onFailure { error ->
                LxSourceStatus.resolveFailed(track.name, error.message)
                _state.update { it.copy(busy = false, error = error.message ?: error.javaClass.simpleName) }
            }
        }
    }
}
