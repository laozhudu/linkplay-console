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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.laozhudu.linkplay.console.ui.components.AppCard
import io.github.laozhudu.linkplay.console.ui.components.SectionTitle
import io.github.laozhudu.linkplay.console.ui.components.VSpace
import io.github.laozhudu.linkplay.console.ui.theme.Spacing
import io.github.laozhudu.linkplay.lx.LxTrack

/**
 * 洛雪搜索页：搜一个词，点一首歌，声音从音箱出来。
 *
 * ## 空态分四种（不给假空态）
 *
 * | 情况 | 界面 |
 * |---|---|
 * | 还没搜 | 请输入歌名搜一下 |
 * | 正在搜 | 正在搜… |
 * | 搜到了但 0 条 | 没搜到「X」—— 换个词，或换一个源 |
 * | 连不上 / 引擎坏了 | 原样把原因给他 |
 *
 * 后两条必须分开：前者该换词，后者该查网络或换脚本，混在一起用户只能瞎试。
 */
@Composable
internal fun LxSearchScreen(
    deviceIp: String,
    location: String?,
    deviceUuid: String?,
    onBack: () -> Unit,
    onOpenScripts: () -> Unit,
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.lg),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("← 返回") }
            Text("洛雪音乐", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = onOpenScripts) { Text("脚本") }
        }

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = keyword,
                onValueChange = { keyword = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text("歌名，例如 周杰伦") },
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
                SectionTitle("搜一首试试", "搜到之后点一下，歌会从音箱放出来")
                Text(
                    "注意：取播放地址要靠你装的那支脚本，没装的话点不动。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(state.results) { track ->
                AppCard {
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
                        TextButton(onClick = { vm.play(track) }, enabled = !state.busy) { Text("播放") }
                    }
                }
            }
        }
    }
}
