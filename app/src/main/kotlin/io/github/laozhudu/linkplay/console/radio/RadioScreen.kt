package io.github.laozhudu.linkplay.console.radio

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.laozhudu.linkplay.console.ui.components.AppCard
import io.github.laozhudu.linkplay.console.ui.components.AppShapesExpressive
import io.github.laozhudu.linkplay.console.ui.components.PillTag
import io.github.laozhudu.linkplay.console.ui.components.SectionTitle
import io.github.laozhudu.linkplay.console.ui.components.VSpace
import io.github.laozhudu.linkplay.console.ui.theme.Spacing
import io.github.laozhudu.linkplay.console.ui.theme.StatusColors
import io.github.laozhudu.linkplay.protocol.radio.RadioStation
import io.github.laozhudu.linkplay.protocol.radio.VerifiedRadioCatalog
import io.github.laozhudu.linkplay.radio.playbackHint
import io.github.laozhudu.radiodiscovery.model.RadioStation as DiscoveryStation

/**
 * 网络电台 —— **和洛雪音乐同一个模子**：搜索框 + 一排切换 + 列表，点一行就响。
 *
 * ## 为什么改成这个样子
 *
 * 原来是"我的电台 + 内置可添加 + 发现对话框"三块并排，想听一个台要先想清楚
 * "它是在我的收藏里，还是得先去发现里找"。同一件事（找一个台听）被拆成三条路。
 *
 * 现在只有一条：上面搜，下面点。收藏和发现只是**同一个列表的两种来源**，
 * 用一排 chip 切，位置不变、手势不变。
 *
 * ## 点一行会发生什么
 *
 * - 「我的电台」里：直接播（已经存过）。
 * - 「热门 / 搜索结果」里：**存进收藏 + 立即播**。不会先说"已加入收藏，请再点一次" ——
 *   用户点了就是要听，收藏顺带完成。
 *
 * ## 空态分开（不给假空态）
 *
 * 没收藏 / 正在查 / 连不上目录 / 查到 0 条，四句话各不相同。
 */
@Composable
fun RadioScreen(
    deviceIp: String,
    deviceLabel: String?,
    onBack: () -> Unit,
    deviceUuid: String? = null,
) {
    val app = LocalContext.current.applicationContext as android.app.Application
    val vm: RadioViewModel = viewModel(
        key = "radio-$deviceIp",
        factory = viewModelFactory { initializer { RadioViewModel(app, deviceIp, deviceLabel, deviceUuid) } },
    )
    val state by vm.state.collectAsState()

    var keyword by remember { mutableStateOf("") }
    var showingDiscover by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<RadioStation?>(null) }
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
            TextButton(onClick = onBack) { Text("← 返回") }
            Text("网络电台", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (state.busy) {
                PillTag(text = "连接中", color = MaterialTheme.colorScheme.secondary)
                Spacer(Modifier.width(Spacing.xs))
            }
            TextButton(onClick = { showAdd = true }) { Text("添加") }
        }

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = keyword,
                onValueChange = { keyword = it },
                modifier = Modifier.weight(1f).focusRequester(focus),
                singleLine = true,
                placeholder = { Text("台名，空着就是热门") },
            )
            TextButton(
                onClick = {
                    vm.discover(keyword)
                    showingDiscover = true
                },
                enabled = !state.discovering,
            ) { Text("搜索") }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            FilterChip(
                selected = !showingDiscover,
                onClick = { showingDiscover = false; vm.clearError() },
                label = { Text("我的电台${if (state.stations.isNotEmpty()) " ${state.stations.size}" else ""}") },
            )
            FilterChip(
                selected = showingDiscover,
                onClick = {
                    keyword = ""
                    vm.discover("")
                    showingDiscover = true
                },
                label = { Text("热门") },
            )
        }

        VSpace(Spacing.xs)

        val error = state.error
        val note = state.note
        when {
            // 发现模式下先看"正在查"，否则用户以为按钮没反应
            showingDiscover && state.discovering -> AppCard {
                Text("正在查目录…", style = MaterialTheme.typography.bodyMedium)
            }
            showingDiscover && state.discoverError != null -> AppCard {
                SectionTitle("连不上目录", "网络或目录服务的问题，不是你搜错了")
                Text(
                    state.discoverError!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            error != null -> AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        SectionTitle("没成功", "原因原样给你，不猜")
                        Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = { vm.clearError() }) { Text("知道了") }
                }
            }
            note != null -> AppCard { Text(note, style = MaterialTheme.typography.bodyMedium) }
            // 下面两个是"真的空"，但空的原因不同
            showingDiscover && state.discoverResults.isEmpty() && state.discoverQuery.isNotBlank() -> AppCard {
                SectionTitle("没搜到「${state.discoverQuery}」", "换个名字试试，或点「热门」")
            }
            showingDiscover && state.discoverResults.isEmpty() -> AppCard {
                SectionTitle("热门还没取回来", "点一下「热门」，或直接搜台名")
            }
            state.stations.isEmpty() -> AppCard {
                SectionTitle("还没有电台", "搜一个加进来，或点右上「添加」粘流地址")
            }
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            if (showingDiscover) {
                items(state.discoverResults) { station ->
                    // 整行可点：点一下就存进收藏并且立刻播
                    AppCard(onClick = { if (!state.busy) vm.playDiscovered(station) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    station.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    listOfNotNull(
                                        station.country?.takeIf { it.isNotBlank() },
                                        station.codec?.takeIf { it.isNotBlank() },
                                        // HLS 只有约 37% 能播 —— 按格式提示，不做网络探测
                                        station.playbackHint(),
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
            } else {
                items(state.stations) { station ->
                    val playing = state.playingUrl != null && state.playingUrl == station.url
                    AppCard(onClick = { if (!state.busy && !playing) vm.play(station) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    station.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    if (playing) "正在播放" else station.url,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (playing) StatusColors.ok else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            TextButton(onClick = { pendingDelete = station }) { Text("删除") }
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        AddRadioDialog(
            onDismiss = { showAdd = false },
            onAdd = { name, url ->
                vm.add(name, url)
                // 粘了地址就是为了听：跟"点一行即播"保持一致，不要多按一次播放
                vm.playUrl(url, name)
                showAdd = false
                showingDiscover = false
            },
        )
    }

    pendingDelete?.let { station ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除这个电台？") },
            text = { Text(station.name) },
            confirmButton = {
                TextButton(onClick = {
                    vm.remove(station.id)
                    pendingDelete = null
                }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun AddRadioDialog(onDismiss: () -> Unit, onAdd: (String, String) -> Unit) {
    var url by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    val normalized = RadioStation.normalizeUrl(url)
    val showHint = url.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加网络电台") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("流地址") },
                    placeholder = { Text("例如 stream.radioparadise.com/mp3-192") },
                    singleLine = true,
                    supportingText = {
                        Text(
                            when {
                                !showHint -> "不用写 http://，会自动补上"
                                normalized != null -> "将使用：$normalized"
                                else -> "这个地址不合法，检查有没有打错字符"
                            },
                            style = MaterialTheme.typography.labelSmall,
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                VSpace(Spacing.xs)
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称（留空自动取域名）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                VSpace(Spacing.sm)
                Text(
                    "不想自己找？下面这些在本机实测能播：",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                VerifiedRadioCatalog.entries.forEach { entry ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(AppShapesExpressive.squircle)
                            .clickable { url = entry.url; name = entry.name }
                            .padding(vertical = Spacing.xs, horizontal = Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PillTag(text = "已实测", color = StatusColors.ok)
                        Spacer(Modifier.width(Spacing.xs))
                        Text(entry.name, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onAdd(name, url) }) { Text("添加") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
