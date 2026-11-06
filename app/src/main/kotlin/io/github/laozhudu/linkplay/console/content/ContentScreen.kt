package io.github.laozhudu.linkplay.console.content

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import io.github.laozhudu.linkplay.console.ConsoleUiState
import io.github.laozhudu.linkplay.console.RecentAdd
import io.github.laozhudu.linkplay.console.ui.apple.InsetGroup
import io.github.laozhudu.linkplay.console.ui.apple.LargeTitle
import io.github.laozhudu.linkplay.console.ui.apple.ListRow
import io.github.laozhudu.linkplay.console.ui.components.VSpace
import io.github.laozhudu.linkplay.console.ui.skeleton.AppSearchField
import io.github.laozhudu.linkplay.console.ui.theme.Spacing
import io.github.laozhudu.linkplay.protocol.library.MyMusicSummary

/**
 * 「内容」页 —— `docs/93` 定稿第 2 屏。
 *
 * ## 这一页回答的唯一问题
 *
 * **"有什么可放的？"** 不是"我装了哪些模块"。
 *
 * 所以顺序是：**搜索框 → 源筛选 → 可用内容**。而不是原来的
 * "本地 / NAS / 在线 / 电台 / 歌单 / 脚本 / 搜索中心"七个并列入口 ——
 * 那是按代码模块分，用户脑子里没有这些模块，只有"我想听点什么"。
 *
 * ## 源筛选为什么是筛选而不是分页
 *
 * 分页要求用户先知道"这首歌在哪个源里"，而大多数时候他不知道也不关心。
 * 筛选的语义是"全部先给你，想窄一点再点" —— 与 `docs/94` 的三核心分类一致。
 *
 * 洛雪**没有独立筛选项**：它和「在线曲库」对用户是同一件事（用户原话
 * 「在线和洛雪本来就是同个源」），都归到「在线」。
 *
 * ## 数量从哪来
 *
 * 全部由 [ConsoleUiState] 携带，本页**不查库**（查库是 IO，放进 Composable
 * 会随重组反复触发）。数量文案规则在 `core-protocol` 的 `MyMusicSummary`，有单测。
 */
@Composable
internal fun ContentScreen(
    state: ConsoleUiState,
    onRefresh: () -> Unit,
    onSearch: (String) -> Unit,
    onOpenLocal: () -> Unit,
    onOpenNas: () -> Unit,
    onOpenPlaylists: () -> Unit,
    onOpenRecent: () -> Unit,
    onOpenOnline: () -> Unit,
    onOpenRadio: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenScripts: () -> Unit,
    onGoDevices: () -> Unit,
    /** 点「接着听」里的一行：再投一次。**不是**打开详情。 */
    onReplay: (io.github.laozhudu.linkplay.protocol.library.RecentPlay) -> Unit,
    onOpenRecentAdd: (RecentAdd) -> Unit,
    /**
     * 播一条搜索结果。**由外面实现**（它知道当前设备、本机地址、洛雪脚本）。
     * 内容页只负责"把结果列出来、点了调这个"。
     */
    onPlayHit: (io.github.laozhudu.linkplay.console.search.SearchHit) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 进页面刷一次：数量会随「刚导入的歌 / 刚连的 NAS / 刚建的歌单」变化，
    // 只在 ViewModel init 里刷会导致回这页还显示旧数字。
    LaunchedEffect(Unit) { onRefresh() }

    var keyword by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(SourceFilter.All) }

    // 当前播放目标（`docs/96` 模型 A）。内容页必须显示它 ——
    // 这里是"我要放点什么"的入口，如果不写清"会放到哪"，用户点完才发现投错了设备。
    val targetState = io.github.laozhudu.linkplay.console.playback.PlaybackOutputStore.current
        .collectAsState()
    val target = targetState.value
    // B 站零实现。**画了就不许点着没反应** —— 点一下给一句实话。
    val context = androidx.compose.ui.platform.LocalContext.current
    // 搜索结果**在本页出**（用户明确要求：不要点进去再搜一遍）
    val searchVm: io.github.laozhudu.linkplay.console.search.SearchHubViewModel =
        androidx.lifecycle.viewmodel.compose.viewModel(
            factory = viewModelFactory {
                initializer {
                    io.github.laozhudu.linkplay.console.search.SearchHubViewModel(
                        context.applicationContext as android.app.Application,
                    )
                }
            },
        )
    val searchState by searchVm.state.collectAsState()
    val searching = searchState.keyword.isNotBlank()
    var biliHint by remember { mutableStateOf(false) }

    // 回调**必须**从外面传进来 —— 上一版这里先写成 onClick = {}，
    // 编译能过、界面能看，但点了什么都不会发生。那正是本项目最忌的失败模式。
    val rows = buildRows(state, onOpenLocal, onOpenNas, onOpenPlaylists, onOpenRecent, onOpenOnline, onOpenRadio, onOpenScripts) { biliHint = true }
    val shown = rows.filter { filter.accepts(it.source) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.lg),
    ) {
        LargeTitle(
            title = "内容",
            subtitle = if (state.saved.isEmpty()) "还没有添加设备" else "${state.saved.count { state.online[it.uuid] != false }} 台在线",
        )
        VSpace(Spacing.sm)

        // ---- 投给谁（`docs/96` 模型 A）----
        //
        // 大厂都让目标永远可见：Sonos 顶部房间条、Spotify 底部 Connect 图标。
        // 我们的内容页原来是"点了就投"，投给谁完全不显示 —— 这是"投错设备"的根源。
        TargetRow(
            target = target,
            devices = state.saved,
            onPick = { out ->
                io.github.laozhudu.linkplay.console.playback.PlaybackOutputStore.write(context, out)
            },
        )
        VSpace(Spacing.sm)

        // 搜索框。回车 → 交给统一搜索页（那里一次问四个源，按源分组出结果）。
        // **不在本页出结果**：搜索结果有自己的排序、来源标注、失败说明，
        // 挤在这一页会让"浏览"和"搜索"两种状态互相污染。
        AppSearchField(
            value = keyword,
            onValueChange = { keyword = it },
            placeholder = "歌名、歌手，或「周杰伦」",
            onSubmit = {
                val q = keyword.trim()
                if (q.isNotEmpty()) searchVm.search(q)
            },
            autoFocus = false,
        )

        VSpace(Spacing.sm)

        // 源筛选（横向可滚，窄屏也放得下六个）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            SourceFilter.entries.forEach { f ->
                FilterChip(
                    selected = filter == f,
                    onClick = { filter = f },
                    label = { Text(f.label()) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                )
            }
        }

        // 没选设备时留一行可点提示：浏览不需要设备，投歌才需要。
        if (state.selectedIp == null) {
            VSpace(Spacing.xs)
            Text(
                text = "还没选设备：可以先浏览，投歌前点这里去设备页",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Spacing.xs),
            )
        }

        VSpace(Spacing.md)

        // ---- 搜索结果（有词就顶掉下面的浏览区）----
        if (searching) {
            SearchResultList(
                state = searchState,
                onPlay = onPlayHit,
                onOpenSource = { src ->
                    when (src) {
                        io.github.laozhudu.linkplay.console.search.SourceId.Nas -> onOpenNas()
                        io.github.laozhudu.linkplay.console.search.SourceId.Radio -> onOpenRadio()
                        io.github.laozhudu.linkplay.console.search.SourceId.Local -> onOpenLocal()
                        io.github.laozhudu.linkplay.console.search.SourceId.Lx -> onOpenScripts()
                    }
                },
            )
            VSpace(Spacing.md)
        }

        // ---- 「接着听」：定稿内容页第一组 ----
        //
        // 快捷入口，不是列表页：只放 3 条，多了会把下面挤出首屏。
        // 右侧两列回答「多久」和「投给谁」—— 定稿图里就是 `7:45  doss1`。
        val listening = state.recentPlays
        if (!searching && listening.isNotEmpty()) {
            InsetGroup(title = "接着听") {
                listening.forEachIndexed { i, play ->
                    val deviceName = play.deviceIp?.let { ip ->
                        state.saved.firstOrNull { it.ip == ip }?.name ?: ip
                    }
                    ListRow(
                        title = play.track.title,
                        subtitle = play.track.displayArtist,
                        trailing = {
                            Text(
                                text = listOfNotNull(
                                    formatDuration(play.track.durationSec),
                                    deviceName ?: sourceLabelOf(play.track.sourceType),
                                ).joinToString("   "),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        showDivider = i != listening.lastIndex,
                        // 点一行 = 再投一次。这是「接着听」最该做的事，不是"看详情"。
                        onClick = { onReplay(play) },
                    )
                }
            }
            VSpace(Spacing.md)
        }

        // ---- 「最近加入」：定稿内容页第二组 ----
        val adds = state.recentAdds
        if (!searching && adds.isNotEmpty()) {
            InsetGroup(title = "最近加入") {
                adds.forEachIndexed { i, add ->
                    ListRow(
                        title = add.title,
                        subtitle = add.kind,
                        trailing = add.detail?.let { d ->
                            {
                                Text(
                                    text = d,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                        showDivider = i != adds.lastIndex,
                        onClick = { onOpenRecentAdd(add) },
                    )
                }
            }
            VSpace(Spacing.md)
        }

        // ---- 源入口 ----
        //
        // ⚠️ 定稿图里**没有这一组**（它只画了 搜索 + 筛选 + 接着听 + 最近加入）。
        // 但删掉就没地方进 NAS / 本地曲库 / 歌单 —— 筛选条只能筛列表，不能当导航。
        // 所以保留、压到最后一组、标题统一成「全部来源」。**这条偏差已记在 docs/91。**
        if (!searching && shown.isNotEmpty()) {
            InsetGroup(title = "全部来源") {
                shown.forEachIndexed { i, row ->
                    ListRow(
                        title = row.title,
                        subtitle = row.subtitle,
                        showChevron = true,
                        onClick = row.onClick,
                        showDivider = i != shown.lastIndex,
                    )
                }
            }
            VSpace(Spacing.md)
        }

        VSpace(Spacing.xl)
    }

    if (biliHint) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { biliHint = false },
            title = { Text("还没接") },
            text = {
                Text(
                    "哔哩哔哩只做了筛选位，取流那套（解析音视频分轨 + 拿直链）还没写。\n" +
                        "定稿图里留了位置，是因为它确实会做 —— 但现在点进去是空的，不如直说。",
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { biliHint = false }) { Text("知道了") }
            },
        )
    }
}

/** 内容页的一行。**数据在这里定义，不在界面里 if 判断**。 */
private data class ContentRow(
    val title: String,
    val subtitle: String?,
    val group: ContentGroup,
    val source: SourceFilter,
    val onClick: () -> Unit,
)

private enum class ContentGroup { Mine, Source }

/**
 * 源筛选。
 *
 * 刻意**没有 Lx 和「在线」分开** —— 用户原话「在线和洛雪本来就是同个源」。
 * 也刻意**没有 B 站的独立入口**：它现在零实现，画出来是空的，
 * 但**筛选位保留**（定稿图有，且用户明确说过要体现），点进去会说明未接入。
 */
private enum class SourceFilter {
    All, Nas, Local, Online, Radio, Bili;

    fun label(): String = when (this) {
        All -> "全部"
        Nas -> "NAS"
        Local -> "本地"
        Online -> "在线"
        Radio -> "电台"
        Bili -> "B站"
    }

    /** 「全部」接受一切；其余按源精确匹配。 */
    fun accepts(s: SourceFilter): Boolean = this == All || this == s
}

/**
 * 把 [ConsoleUiState] 里的数量翻译成行。
 *
 * ## 文案不在这里写
 *
 * 数量文案（单位、「已连接 / 未配置」、空状态那句提示）是**产品规则**，
 * 已经由 `core-protocol` 的 [MyMusicSummary] 给出，并且有单测钉着。
 *
 * 本函数只补三件 `MyMusicSummary` 不知道的事：**归哪组 / 算哪个源 / 点了去哪**。
 * 之前我在这页把文案重写了一遍 —— 编译能过、界面也好看，
 * 但那等于绕过已有的测试，以后改文案要改两处。已经改回来了。
 */
private data class RowMeta(
    val group: ContentGroup,
    val source: SourceFilter,
    val onClick: () -> Unit,
)

private fun buildRows(
    state: ConsoleUiState,
    onOpenLocal: () -> Unit,
    onOpenNas: () -> Unit,
    onOpenPlaylists: () -> Unit,
    onOpenRecent: () -> Unit,
    onOpenOnline: () -> Unit,
    onOpenRadio: () -> Unit,
    onOpenScripts: () -> Unit,
    onOpenBili: () -> Unit,
): List<ContentRow> {
    val entries = MyMusicSummary.build(
        localTracks = state.localMusicCount,
        nasServers = state.nasServerCount,
        playlists = state.playlistCount,
        favorites = state.favoriteCount,
        recents = state.recentCount,
        radios = state.radioCount,
        onlineConfigured = state.onlineConfigured,
    )

    val meta = mapOf(
        MyMusicSummary.KEY_LOCAL to RowMeta(ContentGroup.Mine, SourceFilter.Local, onOpenLocal),
        MyMusicSummary.KEY_NAS to RowMeta(ContentGroup.Mine, SourceFilter.Nas, onOpenNas),
        MyMusicSummary.KEY_PLAYLISTS to RowMeta(ContentGroup.Mine, SourceFilter.Local, onOpenPlaylists),
        MyMusicSummary.KEY_RECENT to RowMeta(ContentGroup.Mine, SourceFilter.Local, onOpenRecent),
        MyMusicSummary.KEY_ONLINE to RowMeta(ContentGroup.Source, SourceFilter.Online, onOpenOnline),
        MyMusicSummary.KEY_RADIO to RowMeta(ContentGroup.Source, SourceFilter.Radio, onOpenRadio),
    )

    val mine = entries.mapNotNull { e ->
        val m = meta[e.key] ?: return@mapNotNull null
        ContentRow(
            title = e.title,
            subtitle = listOfNotNull(e.countText, e.subtitle).joinToString(" · "),
            group = m.group,
            source = m.source,
            onClick = m.onClick,
        )
    }

    // 洛雪与 B 站不属于「我的音乐」——它们不是"我的东西"，是源的入口。
    // 洛雪归「在线」（用户原话："在线和洛雪本来就是同个源"）；
    // B 站零实现，但定稿图有筛选位、用户也说过要体现，所以画出来并直说「未接入」。
    return mine + listOf(
        ContentRow(
            title = "洛雪音乐",
            subtitle = "搜歌取直链（脚本）",
            group = ContentGroup.Source,
            source = SourceFilter.Online,
            onClick = onOpenScripts,
        ),
        ContentRow(
            title = "哔哩哔哩",
            subtitle = "未接入",
            group = ContentGroup.Source,
            source = SourceFilter.Bili,
            onClick = onOpenBili,
        ),
    )
}

/** 秒 → `7:45`。0 或负数返回 null —— 不显示 `0:00`，那看起来像卡住。 */
internal fun formatDuration(sec: Long): String? {
    if (sec <= 0) return null
    return "%d:%02d".format(sec / 60, sec % 60)
}

/** 来源标识 → 界面文案。只在**投本机 / 旧数据没记设备**时用作第三列。 */
internal fun sourceLabelOf(sourceType: String): String = when (sourceType) {
    "local" -> "本地"
    "radio" -> "电台"
    "subsonic" -> "在线"
    "queue" -> "在线"
    else -> sourceType
}

/**
 * 「投给谁」一行 —— 和播放页的「从哪出声」是**同一个真值**（`docs/96` 模型 A）。
 *
 * 放在内容页最上面而不是藏进设置，理由就是大厂的做法：
 * Sonos 的房间条常驻顶部、Spotify 的 Connect 图标常驻播放条 —— 目标是**随时可见、随时可改**的。
 */
@Composable
private fun TargetRow(
    target: io.github.laozhudu.linkplay.console.playback.PlaybackOutput?,
    devices: List<io.github.laozhudu.linkplay.protocol.session.SavedDevice>,
    onPick: (io.github.laozhudu.linkplay.console.playback.PlaybackOutput) -> Unit,
) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Text(
            "投给",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val localSelected = target is io.github.laozhudu.linkplay.console.playback.PlaybackOutput.Local
        FilterChip(
            selected = localSelected,
            onClick = { onPick(io.github.laozhudu.linkplay.console.playback.PlaybackOutput.Local) },
            label = { Text("本机") },
        )
        devices.forEach { dev ->
            val sel = (target as? io.github.laozhudu.linkplay.console.playback.PlaybackOutput.Device)
                ?.ip == dev.ip
            FilterChip(
                selected = sel,
                onClick = {
                    onPick(
                        io.github.laozhudu.linkplay.console.playback.PlaybackOutput.Device(
                            dev.ip,
                            dev.name,
                        ),
                    )
                },
                label = { Text(dev.name.ifBlank { dev.ip }) },
            )
        }
    }
}

/**
 * 搜索结果列表 —— **在本页直接列、直接点**（用户明确要求）。
 *
 * 按源分组，每组最多 5 条（`SearchHubViewModel` 已经截好）。
 * 没有合并排序：NAS 上的《晴天》和洛雪的《晴天》不是同一份东西，混排只会让人点错。
 */
@Composable
private fun SearchResultList(
    state: io.github.laozhudu.linkplay.console.search.SearchHubUiState,
    onPlay: (io.github.laozhudu.linkplay.console.search.SearchHit) -> Unit,
    onOpenSource: (io.github.laozhudu.linkplay.console.search.SourceId) -> Unit,
) {
    io.github.laozhudu.linkplay.console.search.SourceId.entries.forEach { src ->
        val probe = state.probes[src] ?: return@forEach
        val items = (probe as? io.github.laozhudu.linkplay.console.search.ProbeState.Found)?.items.orEmpty()

        if (items.isNotEmpty()) {
            InsetGroup(
                title = src.label,
                footer = (probe as? io.github.laozhudu.linkplay.console.search.ProbeState.Found)
                    ?.let { if (it.count > it.items.size) "共 ${it.count} 条，这里显示前 ${it.items.size} 条" else null },
            ) {
                items.forEachIndexed { i, hit ->
                    ListRow(
                        title = hit.title,
                        subtitle = hit.subtitle,
                        showChevron = false,
                        onClick = { onPlay(hit) },
                        showDivider = i != items.lastIndex,
                    )
                }
            }
            VSpace(Spacing.sm)
        } else {
            // 没结果 / 不可用：说清原因，并且给一个"去那边"的出口
            val reason = when (probe) {
                io.github.laozhudu.linkplay.console.search.ProbeState.Searching -> "搜索中…"
                io.github.laozhudu.linkplay.console.search.ProbeState.Idle -> return@forEach
                is io.github.laozhudu.linkplay.console.search.ProbeState.Failed -> probe.reason
                is io.github.laozhudu.linkplay.console.search.ProbeState.NotReady -> probe.hint
                is io.github.laozhudu.linkplay.console.search.ProbeState.Found -> "没找到"
            }
            InsetGroup(title = src.label) {
                ListRow(
                    title = "没有结果",
                    subtitle = reason,
                    showChevron = true,
                    onClick = { onOpenSource(src) },
                    showDivider = false,
                )
            }
            VSpace(Spacing.sm)
        }
    }
}
