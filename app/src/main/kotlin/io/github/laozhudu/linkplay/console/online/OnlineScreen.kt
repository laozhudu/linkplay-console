package io.github.laozhudu.linkplay.console.online

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.laozhudu.linkplay.console.ui.components.AppCard
import io.github.laozhudu.linkplay.console.ui.components.AppShapesExpressive
import io.github.laozhudu.linkplay.console.ui.components.PageHeader
import io.github.laozhudu.linkplay.console.ui.components.PillButton
import io.github.laozhudu.linkplay.console.ui.components.SectionTitle
import io.github.laozhudu.linkplay.console.ui.components.VSpace
import io.github.laozhudu.linkplay.console.ui.theme.Spacing
import io.github.laozhudu.linkplay.console.ui.theme.StatusColors

/**
 * 在线曲库（tt-music / Subsonic）。
 *
 * 设计上的两个刻意选择：
 * 1. **搜索框是第一视觉重心**：这一页存在的意义就是"想听什么搜什么"，
 *    不像 NAS 页要先翻目录；
 * 2. 没配置时**直接给配置表单**，不做"请先去设置里配置"的跳转 ——
 *    第一次用的人就在这儿一次填完。
 */
@Composable
fun OnlineScreen(
    deviceIp: String,
    location: String?,
    deviceUuid: String?,
    onBack: () -> Unit,
) {
    val app = LocalContext.current.applicationContext as android.app.Application
    val vm: OnlineViewModel = viewModel(
        key = "online-$deviceIp",
        factory = viewModelFactory {
            initializer { OnlineViewModel(app, deviceIp, location, deviceUuid) }
        },
    )
    val state by vm.state.collectAsState()
    val queue by vm.queue.collectAsState()

    if (state.editing || !state.verified) {
        ConfigSheet(state = state, vm = vm, onBack = onBack)
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.lg),
    ) {
        PageHeader(
            title = state.config.name.ifBlank { "在线曲库" },
            subtitle = deviceIp.ifBlank { "先选一台音箱" },
            onBack = onBack,
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = state.query,
                onValueChange = vm::setQuery,
                label = { Text("搜歌 / 歌手 / 专辑") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { vm.search() }),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(Spacing.sm))
            PillButton(
                text = if (state.searching) "搜索中…" else "搜索",
                enabled = !state.searching && state.query.isNotBlank(),
                onClick = vm::search,
            )
        }

        state.note?.let {
            VSpace(Spacing.xs)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (it.startsWith("投送失败") || it.startsWith("连不上")) StatusColors.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { vm.setEditing(true) }) { Text("改配置") }
            }
        }

        VSpace(Spacing.sm)
        if (state.results.isEmpty()) {
            Text(
                "搜索结果显示在这里。点一首 = 从这首开始把整份结果投给音箱（走设备侧队列，App 退出照播）。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        LazyColumn(Modifier.weight(1f)) {
            itemsIndexed(state.results, key = { _, t -> "${t.providerId}:${t.id}" }) { index, track ->
                val playing = queue.deviceOwned &&
                    queue.items.getOrNull(queue.index)?.title == track.title
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(AppShapesExpressive.squircle)
                        .clickable(enabled = !state.busy) { vm.play(track) }
                        .padding(vertical = Spacing.sm, horizontal = Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${index + 1}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(24.dp),
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            track.title,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (playing) StatusColors.ok else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            listOfNotNull(
                                track.artist,
                                track.album,
                                track.suffix?.uppercase(),
                                if (track.durationSec > 0) format(track.durationSec) else null,
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (playing) {
                        Box(
                            Modifier.size(8.dp).clip(CircleShape)
                                .background(StatusColors.ok),
                        )
                    }
                }
            }
        }
    }
}

private fun format(sec: Long): String = "%d:%02d".format(sec / 60, sec % 60)

/**
 * 配置表单。字段就四个，全是最小必需项。
 *
 * 地址一栏的提示直接给真机例子（`.124:9527`），比"请输入服务器地址"有用得多。
 */
@Composable
private fun ConfigSheet(
    state: OnlineUiState,
    vm: OnlineViewModel,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.lg),
    ) {
        PageHeader(title = "在线曲库", subtitle = "Subsonic 兼容服务", onBack = onBack)

        AppCard {
            SectionTitle("连接你的曲库", "支持 Subsonic / OpenSubsonic（含 tt-music、Navidrome）")
            VSpace(Spacing.sm)
            OutlinedTextField(
                value = state.config.name,
                onValueChange = { v -> vm.updateConfig { it.copy(name = v) } },
                label = { Text("名字（随便起，只用于显示）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            VSpace(Spacing.xs)
            OutlinedTextField(
                value = state.config.baseUrl,
                onValueChange = { v -> vm.updateConfig { it.copy(baseUrl = v) } },
                label = { Text("服务地址") },
                placeholder = { Text("192.168.99.124:9527") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "只填 IP:端口就行，会自动补 http://。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            VSpace(Spacing.xs)
            OutlinedTextField(
                value = state.config.user,
                onValueChange = { v -> vm.updateConfig { it.copy(user = v) } },
                label = { Text("用户名") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            VSpace(Spacing.xs)
            OutlinedTextField(
                value = state.config.password,
                onValueChange = { v -> vm.updateConfig { it.copy(password = v) } },
                label = { Text("密码") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            VSpace(Spacing.sm)
            Text(
                "密码只存在手机本地，用 token 方式认证（不会以明文进 URL）。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            VSpace(Spacing.sm)
            Row(verticalAlignment = Alignment.CenterVertically) {
                PillButton(
                    text = if (state.busy) "连接中…" else "连接并保存",
                    enabled = !state.busy &&
                        state.config.baseUrl.isNotBlank() && state.config.user.isNotBlank(),
                    onClick = vm::saveConfig,
                )
                if (state.verified) {
                    Spacer(Modifier.width(Spacing.sm))
                    TextButton(onClick = { vm.setEditing(false) }) { Text("开始搜歌") }
                }
            }
        }

        state.note?.let {
            VSpace(Spacing.sm)
            AppCard {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (it.startsWith("连不上") || it.startsWith("连上了但")) StatusColors.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        VSpace(Spacing.xl)
    }
}
