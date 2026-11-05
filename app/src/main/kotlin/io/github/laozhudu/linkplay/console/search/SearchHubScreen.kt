package io.github.laozhudu.linkplay.console.search

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.laozhudu.linkplay.console.ui.components.VSpace
import io.github.laozhudu.linkplay.console.ui.skeleton.AppListRow
import io.github.laozhudu.linkplay.console.ui.skeleton.AppPage
import io.github.laozhudu.linkplay.console.ui.skeleton.AppSearchField
import io.github.laozhudu.linkplay.console.ui.skeleton.AppStateCard
import io.github.laozhudu.linkplay.console.ui.skeleton.RowBadge
import io.github.laozhudu.linkplay.console.ui.skeleton.StateKind
import io.github.laozhudu.linkplay.console.ui.theme.Spacing

/**
 * 统一搜索入口 —— 输一个词，四个源各自回一句"有几条"。
 *
 * ## 为什么不把结果合并成一个列表
 *
 * 跨源排序没有意义：本地那首《晴天》和洛雪搜到的《晴天》不是同一个文件，
 * 混在一起按"相关度"排，人只会点错。所以这里只回答**哪个源有**，
 * 点进去在各自页面里选。
 */
@Composable
internal fun SearchHubScreen(
    onBack: () -> Unit,
    onOpenSource: (SourceId) -> Unit,
) {
    val app = LocalContext.current.applicationContext as android.app.Application
    val vm: SearchHubViewModel = viewModel(
        factory = viewModelFactory { initializer { SearchHubViewModel(app) } },
    )
    val state by vm.state.collectAsState()
    var keyword by remember { mutableStateOf("") }

    AppPage(
        title = "找点东西听",
        subtitle = if (state.keyword.isEmpty()) "四个源一起问" else "「${state.keyword}」",
        onBack = onBack,
    ) {
        AppSearchField(
            value = keyword,
            onValueChange = { keyword = it },
            placeholder = "歌名 / 歌手 / 电台名",
            onSubmit = { vm.search(keyword) },
        )
        VSpace(Spacing.xs)

        if (state.keyword.isEmpty()) {
            AppStateCard(
                kind = StateKind.Empty,
                title = "输一个词，四个源一起问",
                hint = "结果不合并 —— 各源分别显示有几条",
            )
        }

        SourceId.entries.forEach { id ->
            val probe = state.probes[id] ?: ProbeState.Idle
            when (probe) {
                ProbeState.Idle -> Unit
                ProbeState.Searching -> AppListRow(
                    title = id.label,
                    subtitle = "正在查…",
                    badge = RowBadge.Loading,
                )

                is ProbeState.Found -> AppListRow(
                    title = id.label,
                    subtitle = if (probe.count == 0) "没搜到" else "找到 ${probe.count} 条 · ${probe.ms}ms",
                    badge = RowBadge.Arrow,
                    onClick = if (probe.count == 0) null else ({ onOpenSource(id) }),
                )

                is ProbeState.Failed -> AppListRow(
                    title = id.label,
                    subtitle = "没成功：${probe.reason}",
                    badge = RowBadge.None,
                )

                is ProbeState.NotReady -> AppListRow(
                    title = id.label,
                    subtitle = probe.hint,
                    badge = RowBadge.None,
                )
            }
            VSpace(Spacing.xs)
        }

        if (state.keyword.isNotEmpty()) {
            TextButton(onClick = { keyword = ""; vm.search("") }) { Text("清空") }
        }
    }
}
