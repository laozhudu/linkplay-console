package io.github.laozhudu.linkplay.console.radio

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.laozhudu.linkplay.console.ui.components.AppCard
import io.github.laozhudu.linkplay.console.ui.components.AppShapesExpressive
import io.github.laozhudu.linkplay.console.ui.components.EmptyState
import io.github.laozhudu.linkplay.console.ui.components.PageHeader
import io.github.laozhudu.linkplay.console.ui.components.PillButton
import io.github.laozhudu.linkplay.console.ui.components.PillTag
import io.github.laozhudu.linkplay.console.ui.components.SectionTitle
import io.github.laozhudu.linkplay.console.ui.components.VSpace
import io.github.laozhudu.linkplay.console.ui.theme.Spacing
import io.github.laozhudu.linkplay.console.ui.theme.StatusColors
import io.github.laozhudu.linkplay.protocol.radio.RadioStation
import io.github.laozhudu.linkplay.protocol.radio.VerifiedRadioCatalog

/**
 * 网络电台（媒体库 → 与「手机本地音乐」同级的一个来源）。
 *
 * 三层结构，用户打开就能懂：
 * 1. 顶部一句状态（在播什么 / 空闲）；
 * 2. 我的电台：点一行即播，行尾 ✕ 删；
 * 3. 内置可用的电台：真机验证过的，一个「全部添加」按钮就能用起来。
 *
 * 添加电台不再要求懂 URL —— 缺协议头会自动补 `http://`（见 `RadioStation.normalizeUrl`），
 * 提交按钮永远可点，出错时给具体原因。
 */
@Composable
fun RadioScreen(
    deviceIp: String,
    deviceLabel: String?,
    onBack: () -> Unit,
) {
    val app = LocalContext.current.applicationContext as android.app.Application
    val vm: RadioViewModel = viewModel(
        key = "radio-$deviceIp",
        factory = viewModelFactory { initializer { RadioViewModel(app, deviceIp, deviceLabel) } },
    )
    val state by vm.state.collectAsState()

    var showAdd by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<RadioStation?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.lg),
    ) {
        PageHeader(
            title = "网络电台",
            subtitle = if (state.playingUrl != null) "正在播放" else (deviceLabel ?: deviceIp),
            onBack = onBack,
            trailing = {
                if (state.busy) {
                    PillTag(text = "连接中", color = MaterialTheme.colorScheme.secondary)
                } else if (state.playingUrl != null) {
                    PillTag(text = "播放中", color = StatusColors.ok)
                }
            },
        )

        // ---------- 当前状态 / 结果说明 ----------
        state.note?.let { note ->
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        note,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (state.playingUrl != null) StatusColors.ok else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    if (state.playingUrl != null) {
                        TextButton(onClick = vm::stop) { Text("停止") }
                    }
                }
            }
            VSpace(Spacing.sm)
        }

        state.error?.let { err ->
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        err,
                        style = MaterialTheme.typography.bodySmall,
                        color = StatusColors.error,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = vm::clearError) { Text("知道了") }
                }
            }
            VSpace(Spacing.sm)
        }

        // ---------- 我的电台 ----------
        AppCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionTitle(
                    title = "我的电台",
                    subtitle = if (state.stations.isEmpty()) "还没有，先从下面添加" else "${state.stations.size} 个 · 点一行即播",
                    modifier = Modifier.weight(1f),
                )
                PillButton("＋ 添加", filled = true, onClick = { showAdd = true })
            }

            if (state.stations.isEmpty()) {
                VSpace(Spacing.sm)
                Text(
                    "还没有电台。点「添加」粘一条流地址，或者直接添加下面内置好的。",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                VSpace(Spacing.xs)
                state.stations.forEach { station ->
                    val active = state.playingUrl == station.url
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(AppShapesExpressive.squircle)
                            .clickable { vm.play(station) }
                            .padding(start = Spacing.xs, end = Spacing.xs, top = Spacing.xs, bottom = Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(
                                    if (active) StatusColors.ok.copy(alpha = 0.18f)
                                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                if (active) "❚❚" else "◉",
                                style = MaterialTheme.typography.labelLarge,
                                color = if (active) StatusColors.ok else MaterialTheme.colorScheme.primary,
                            )
                        }
                        Spacer(Modifier.width(Spacing.sm))
                        Column(Modifier.weight(1f)) {
                            Text(
                                station.name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                if (station.invalid) "上次没播起来：${station.url}" else station.url,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (station.invalid) StatusColors.error
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .clickable { pendingDelete = station },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "✕",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        // ---------- 内置电台（真机验证过）----------
        if (state.missingVerified.isNotEmpty()) {
            VSpace(Spacing.sm)
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle(
                        title = "内置可用电台",
                        subtitle = "都在本机实测能放出来，可直接添加",
                        modifier = Modifier.weight(1f),
                    )
                    PillButton("全部添加", filled = false, onClick = vm::addAllVerified)
                }
                VSpace(Spacing.xs)
                state.missingVerified.forEach { entry ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(AppShapesExpressive.squircle)
                            .clickable { vm.add(entry.name, entry.url) }
                            .padding(vertical = Spacing.xs, horizontal = Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("＋", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
                        }
                        Spacer(Modifier.width(Spacing.sm))
                        Column(Modifier.weight(1f)) {
                            Text(entry.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                            Text(
                                entry.note,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }

        // ---------- 手动播放任意地址（播过就自动存）----------
        if (state.stations.isNotEmpty() || state.missingVerified.isEmpty()) {
            VSpace(Spacing.sm)
            OneOffPlayCard(vm)
        }

        // ---------- 诊断 ----------
        if (state.log.isNotEmpty()) {
            VSpace(Spacing.sm)
            AppCard {
                Text(
                    state.log.takeLast(6).joinToString("\n"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        VSpace(Spacing.xxl)
    }

    if (showAdd) {
        AddRadioDialog(
            onDismiss = { showAdd = false },
            onAdd = { name, url ->
                vm.add(name, url)
                showAdd = false
            },
        )
    }

    pendingDelete?.let { station ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删掉「${station.name}」？") },
            text = { Text("只是从本机列表移除，不影响电台本身。") },
            confirmButton = {
                TextButton(onClick = {
                    vm.remove(station.id)
                    pendingDelete = null
                }) { Text("删除", color = StatusColors.error) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("取消") } },
        )
    }
}

/** 直接贴一条地址就播；播成功会自动进「我的电台」，这正是用户要的"用过的自动保存"。 */
@Composable
private fun OneOffPlayCard(vm: RadioViewModel) {
    var url by remember { mutableStateOf("") }
    AppCard {
        SectionTitle("直接播一条地址", "播成功的会自动存进「我的电台」")
        VSpace(Spacing.xs)
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                placeholder = { Text("粘贴流地址，不带 http:// 也行") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(Spacing.xs))
            PillButton("播放", filled = true, onClick = { vm.playUrl(url) })
        }
    }
}

/**
 * 添加电台对话框。
 *
 * 关键改动：**提交按钮不再因为格式问题禁用**。
 * 之前只要地址少了 `http://` 按钮就变灰写着"填个有效地址"，用户点不动 —— 这就是"无法添加链接"。
 * 现在无论填什么都能点，提交时自动补协议头，真的不合法才报具体原因。
 */
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
