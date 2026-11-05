package io.github.laozhudu.linkplay.console.lx

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
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
import io.github.laozhudu.linkplay.console.playback.PlaybackOutput
import io.github.laozhudu.linkplay.console.playback.PlaybackRouter
import io.github.laozhudu.linkplay.console.ui.theme.Spacing
import androidx.compose.material3.MaterialTheme
import io.github.laozhudu.script.ScriptCandidate
import io.github.laozhudu.script.ScriptRepository

/**
 * 洛雪音乐：**搜一首 → 点一行 → 响**。
 *
 * 骨架用 [AppPage]（与设备设置、音乐库同一套页头与内边距），
 * 所以从别的页面切进来不会有"跳一下"的感觉。
 *
 * 空态四种（[AppStateCard] 的四种语气）：没搜 / 正在搜 / 搜到 0 条 / 连不上。
 */
@Composable
internal fun LxSearchScreen(
    deviceIp: String,
    location: String?,
    deviceUuid: String?,
    showBack: Boolean = true,
    onBack: () -> Unit = {},
    onOpenScripts: () -> Unit = {},
) {
    val app = LocalContext.current.applicationContext as android.app.Application
    val vm: LxSearchViewModel = viewModel(
        key = "lx-$deviceIp",
        factory = viewModelFactory {
            initializer { LxSearchViewModel(app, deviceIp, location, deviceUuid) }
        },
    )
    val state by vm.state.collectAsState()
    var keyword by remember { mutableStateOf("") }
    var importError by remember { mutableStateOf<String?>(null) }
    // 声音从哪出来：存在进程级路由里（下次进来还是同一个选择）
    val router = remember { PlaybackRouter.of(app) }
    var output by remember { mutableStateOf(router.output()) }

    AppPage(
        title = "洛雪音乐",
        subtitle = if (state.busy) "正在取地址…" else null,
        onBack = if (showBack) onBack else null,
        trailing = { TextButton(onClick = onOpenScripts) { Text("脚本") } },
    ) {
        AppSearchField(
            value = keyword,
            onValueChange = { keyword = it },
            placeholder = "歌名，回车就搜",
            onSubmit = { vm.search(keyword) },
            enabled = !state.searching,
        )

        // 输出选择：本地播 / 推流。**界面上只有这一处**，两边共用同一个播放入口。
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Text("从哪出声", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FilterChip(
                selected = output == PlaybackOutput.Local,
                onClick = {
                    router.setOutput(PlaybackOutput.Local)
                    output = PlaybackOutput.Local
                },
                label = { Text("本机") },
            )
            FilterChip(
                selected = output == PlaybackOutput.Remote,
                onClick = {
                    router.setOutput(PlaybackOutput.Remote)
                    output = PlaybackOutput.Remote
                },
                label = { Text("音箱") },
            )
        }

        if (state.sources.size > 1) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                state.sources.take(6).forEach { s ->
                    FilterChip(
                        selected = s == state.source,
                        onClick = { vm.setSource(s) },
                        label = { Text(s) },
                    )
                }
            }
        }

        VSpace(Spacing.xs)

        val error = state.error
        when {
            state.searching -> AppStateCard(StateKind.Busy, "正在搜…")
            error != null -> AppStateCard(
                kind = StateKind.Error,
                title = "没成功",
                detail = error,
                onDismiss = { vm.clearError() },
            )
            state.note != null -> AppStateCard(StateKind.Hint, state.note!!)
            state.results.isEmpty() && state.searchedKeyword != null ->
                AppStateCard(StateKind.Empty, "没搜到「${state.searchedKeyword}」", hint = "换个词，或换一个源试试")
            state.results.isEmpty() ->
                AppStateCard(StateKind.Empty, "搜一首试试", hint = "点任意一行，歌就从音箱放出来")
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(state.results) { track ->
                AppListRow(
                    title = track.name,
                    subtitle = listOfNotNull(
                        track.singer.takeIf { it.isNotBlank() },
                        track.albumName.takeIf { it.isNotBlank() },
                        track.source,
                    ).joinToString(" · "),
                    badge = if (state.busy) RowBadge.Loading else RowBadge.Arrow,
                    onClick = { if (!state.busy) vm.play(track) },
                )
            }
        }
    }

    // 没脚本：就地弹导入框，导完自动放这首（不用跳页面、不用重搜）
    if (state.needScript) {
        ImportScriptDialog(
            onDismiss = {
                importError = null
                vm.cancelPending()
            },
            errorText = importError,
            onImport = { text, origin ->
                val result = ScriptLibrary.repository(app).import(ScriptCandidate(text, origin))
                ScriptSourceStatus.record(result)
                if (result is ScriptRepository.ImportResult.Added || result is ScriptRepository.ImportResult.Updated) {
                    importError = null
                    vm.onScriptImported()
                } else {
                    importError = ScriptLibrary.explain(result)
                }
            },
        )
    }
}
