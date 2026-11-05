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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.laozhudu.linkplay.console.ui.components.AppShapesExpressive
import io.github.laozhudu.linkplay.console.ui.components.PillTag
import io.github.laozhudu.linkplay.console.ui.components.VSpace
import io.github.laozhudu.linkplay.console.ui.skeleton.AppListRow
import io.github.laozhudu.linkplay.console.ui.skeleton.AppPage
import io.github.laozhudu.linkplay.console.ui.skeleton.AppSearchField
import io.github.laozhudu.linkplay.console.ui.skeleton.AppStateCard
import io.github.laozhudu.linkplay.console.ui.skeleton.RowBadge
import io.github.laozhudu.linkplay.console.ui.skeleton.StateKind
import io.github.laozhudu.linkplay.console.ui.theme.Spacing
import io.github.laozhudu.linkplay.console.ui.theme.StatusColors
import io.github.laozhudu.linkplay.protocol.radio.RadioStation
import io.github.laozhudu.linkplay.protocol.radio.VerifiedRadioCatalog
import io.github.laozhudu.linkplay.radio.playbackHint

/**
 * 网络电台 —— 与洛雪音乐同一个骨架（[AppPage] + [AppSearchField] + [AppListRow]）。
 *
 * 原来「我的电台 / 内置可添加 / 发现对话框」三块并排，想听一个台得先想清楚它属于哪一块；
 * 现在只有一条路：上面搜（空着就是热门），下面点。
 *
 * 点一行：收藏里直接播；热门/搜索结果里**存进收藏并立即播**。
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

    AppPage(
        title = "网络电台",
        subtitle = when {
            state.busy -> "正在连接…"
            state.playingUrl == null -> deviceLabel ?: deviceIp
            else -> "正在播放"
        },
        onBack = onBack,
        trailing = {
            if (state.playingUrl != null && !state.busy) {
                PillTag(text = "播放中", color = StatusColors.ok)
                Spacer(Modifier.width(Spacing.xs))
            }
            TextButton(onClick = { showAdd = true }) { Text("添加") }
        },
    ) {
        AppSearchField(
            value = keyword,
            onValueChange = { keyword = it },
            placeholder = "台名，空着就是热门",
            onSubmit = {
                vm.discover(keyword)
                showingDiscover = true
            },
            enabled = !state.discovering,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            FilterChip(
                selected = !showingDiscover,
                onClick = { showingDiscover = false; vm.clearError() },
                label = { Text("我的电台" + if (state.stations.isNotEmpty()) " ${state.stations.size}" else "") },
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
        when {
            showingDiscover && state.discovering -> AppStateCard(StateKind.Busy, "正在查目录…")
            showingDiscover && state.discoverError != null -> AppStateCard(
                kind = StateKind.Error,
                title = "连不上目录",
                hint = "网络或目录服务的问题，不是你搜错了",
                detail = state.discoverError,
                onDismiss = { vm.clearError() },
            )
            error != null -> AppStateCard(StateKind.Error, "没成功", detail = error, onDismiss = { vm.clearError() })
            state.note != null -> AppStateCard(StateKind.Hint, state.note!!)
            showingDiscover && state.discoverResults.isEmpty() && state.discoverQuery.isNotBlank() ->
                AppStateCard(StateKind.Empty, "没搜到「${state.discoverQuery}」", hint = "换个名字试试，或点「热门」")
            showingDiscover && state.discoverResults.isEmpty() ->
                AppStateCard(StateKind.Empty, "热门还没取回来", hint = "点一下「热门」，或直接搜台名")
            state.stations.isEmpty() ->
                AppStateCard(StateKind.Empty, "还没有电台", hint = "搜一个加进来，或点右上「添加」粘流地址")
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            if (showingDiscover) {
                items(state.discoverResults) { station ->
                    AppListRow(
                        title = station.name,
                        subtitle = listOfNotNull(
                            station.country?.takeIf { it.isNotBlank() },
                            station.codec?.takeIf { it.isNotBlank() },
                            // HLS 只有约 37% 能播 —— 按格式提示，不做网络探测
                            station.playbackHint(),
                        ).joinToString(" · "),
                        badge = if (state.busy) RowBadge.Loading else RowBadge.Arrow,
                        onClick = { if (!state.busy) vm.playDiscovered(station) },
                    )
                }
            } else {
                items(state.stations) { station ->
                    val playing = state.playingUrl != null && state.playingUrl == station.url
                    AppListRow(
                        title = station.name,
                        subtitle = if (playing) "正在播放" else station.url,
                        badge = if (playing) RowBadge.Playing else RowBadge.None,
                        subtitleColor = if (playing) StatusColors.ok else null,
                        onClick = { if (!state.busy && !playing) vm.play(station) },
                        trailing = {
                            TextButton(onClick = { pendingDelete = station }) { Text("删除") }
                        },
                    )
                }
            }
        }
    }

    if (showAdd) {
        AddRadioDialog(
            onDismiss = { showAdd = false },
            onAdd = { name, url ->
                vm.add(name, url)
                // 粘了地址就是为了听：跟"点一行即播"保持一致
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
