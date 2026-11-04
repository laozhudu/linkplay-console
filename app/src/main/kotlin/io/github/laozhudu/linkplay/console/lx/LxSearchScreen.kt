package io.github.laozhudu.linkplay.console.lx

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.laozhudu.linkplay.console.ui.components.AppCard
import io.github.laozhudu.linkplay.console.ui.components.SectionTitle
import io.github.laozhudu.linkplay.console.ui.components.VSpace
import io.github.laozhudu.linkplay.console.ui.theme.Spacing
import io.github.laozhudu.script.ScriptCandidate
import io.github.laozhudu.script.ScriptRepository
import io.github.laozhudu.linkplay.lx.LxTrack

/**
 * 洛雪音乐：**搜一首 → 点一行 → 响**。
 *
 * ## 目标：把步骤压到最少
 *
 * 一次播放只需要两步（输入 → 点一行）。所有能省的都省了：
 * - 它是**一级页面**（底部导航第四个），不再藏在"媒体库 → 其它入口"里；
 * - 进页面光标已经在搜索框里，打完字直接回车；
 * - **整行可点**，不是只能点右边那个小按钮；
 * - 没装脚本时**直接弹导入框**，导完接着放这一首 —— 不用先去别的页面找脚本、再回来重搜。
 *
 * ## 空态四种（不给假空态）
 *
 * 还没搜 / 正在搜 / 搜到 0 条 / 连不上 —— 四句话完全不同，因为下一步完全不同。
 */
@Composable
internal fun LxSearchScreen(
    deviceIp: String,
    location: String?,
    deviceUuid: String?,
    /** 作为一级页面时不显示返回（底部导航已经能切换）。 */
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
    val focus = remember { FocusRequester() }

    // 进页面就能打字：少一次"点一下输入框"
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.lg),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showBack) TextButton(onClick = onBack) { Text("← 返回") }
            Text("洛雪音乐", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = onOpenScripts) { Text("脚本") }
        }

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = keyword,
                onValueChange = { keyword = it },
                modifier = Modifier.weight(1f).focusRequester(focus),
                singleLine = true,
                placeholder = { Text("歌名，回车就搜") },
            )
            TextButton(onClick = { vm.search(keyword) }, enabled = keyword.isNotBlank()) { Text("搜索") }
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
        val note = state.note
        when {
            state.searching -> AppCard { Text("正在搜…", style = MaterialTheme.typography.bodyMedium) }
            error != null -> AppCard {
                SectionTitle("没成功", "原因原样给你，不猜")
                Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            note != null -> AppCard { Text(note, style = MaterialTheme.typography.bodyMedium) }
            state.results.isEmpty() && state.searchedKeyword != null -> AppCard {
                SectionTitle("没搜到「${state.searchedKeyword}」", "换个词，或换一个源试试")
            }
            state.results.isEmpty() -> AppCard {
                SectionTitle("搜一首试试", "点任意一行，歌就从音箱放出来")
            }
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(state.results) { track ->
                // 整行可点：少一次"瞄准右边那个小按钮"
                AppCard(onClick = { if (!state.busy) vm.play(track) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                track.name,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                listOfNotNull(
                                    track.singer.takeIf { it.isNotBlank() },
                                    track.albumName.takeIf { it.isNotBlank() },
                                    track.source,
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(
                            if (state.busy) "…" else "▶",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
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
