package io.github.laozhudu.linkplay.console.search

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.laozhudu.linkplay.console.library.LocalLibrary
import io.github.laozhudu.linkplay.console.lx.LxSession
import io.github.laozhudu.linkplay.radio.RadioBrowserSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 声音可能来自哪四个地方。 */
enum class SourceId(val label: String) {
    Lx("洛雪音乐"),
    Radio("网络电台"),
    Local("手机本地"),
    Nas("NAS"),
}

/** 一个源的查询结果。**四种状态分开**，因为下一步动作完全不同。 */
sealed interface ProbeState {
    data object Idle : ProbeState
    data object Searching : ProbeState
    /** 找到了：条数 + 耗时。 */
    data class Found(val count: Int, val ms: Long) : ProbeState
    /** 查了但出错（网络/权限/引擎）。 */
    data class Failed(val reason: String) : ProbeState
    /** 这个源现在用不了（没配置、没连服务器）—— **不是失败**。 */
    data class NotReady(val hint: String) : ProbeState
}

data class SearchHubUiState(
    val keyword: String = "",
    val probes: Map<SourceId, ProbeState> = SourceId.entries.associateWith { ProbeState.Idle },
)

/**
 * 统一入口：**一个输入框，同时问四个源各找到几条**。
 *
 * ## 为什么不做合并排序
 *
 * 跨源排序没有意义：NAS 上的《晴天》和网易云的《晴天》不是同一份东西，
 * 按"相关度"排在一起只会让人点错。所以这里只回答"哪个源有、有几条"，
 * 选哪个源由人决定。
 *
 * ## 为什么四个源并发但互不影响
 *
 * 洛雪要跑 QuickJS 引擎（可能几百毫秒甚至卡住），电台要联网，本地要读 MediaStore，
 * 串起来就是"最慢那个决定全部"。所以四个各自 async + 各自 runCatching：
 * 一个挂了，另外三个照样出结果。
 */
class SearchHubViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(SearchHubUiState())
    val state: StateFlow<SearchHubUiState> = _state.asStateFlow()

    fun search(keyword: String) {
        val q = keyword.trim()
        if (q.isEmpty()) return
        _state.value = SearchHubUiState(
            keyword = q,
            probes = SourceId.entries.associateWith { ProbeState.Searching },
        )
        viewModelScope.launch {
            val results = SourceId.entries
                .map { id -> async { id to probe(id, q) } }
                .awaitAll()
                .toMap()
            _state.update { it.copy(probes = results) }
        }
    }

    private suspend fun probe(id: SourceId, query: String): ProbeState {
        val started = System.currentTimeMillis()
        return runCatching {
            when (id) {
                SourceId.Lx -> {
                    val sdk = LxSession.sdk(getApplication())
                    val page = sdk.search("wy", query, limit = 5)
                    val value = page.value
                    if (value == null) {
                        ProbeState.Failed(page.error ?: "洛雪没返回结果")
                    } else {
                        // total 是平台报的总数；拿不到就退回这次返回的条数
                        val count = if (value.total > 0) value.total else value.tracks.size
                        ProbeState.Found(count, System.currentTimeMillis() - started)
                    }
                }

                SourceId.Radio -> withContext(Dispatchers.IO) {
                    val list = RadioBrowserSource().search(query, limit = 20)
                    ProbeState.Found(list.size, System.currentTimeMillis() - started)
                }

                SourceId.Local -> withContext(Dispatchers.IO) {
                    val all = LocalLibrary.query(getApplication(), limit = 2000)
                    val hit = all.count {
                        it.title.contains(query, ignoreCase = true) ||
                            it.artist?.contains(query, ignoreCase = true) == true ||
                            it.album?.contains(query, ignoreCase = true) == true
                    }
                    if (all.isEmpty()) {
                        ProbeState.NotReady("还没扫到本地音乐（去「手机本地音乐」扫一次）")
                    } else {
                        ProbeState.Found(hit, System.currentTimeMillis() - started)
                    }
                }

                // NAS 要先连上服务器才有目录可查，跨页面编排风险高，
                // 这一版不给假条数 —— 直接说清"去那边搜"。
                SourceId.Nas -> ProbeState.NotReady("在「NAS 媒体库」连着服务器时，到那里搜")
            }
        }.getOrElse { error ->
            ProbeState.Failed(error.message ?: error.javaClass.simpleName)
        }
    }
}
