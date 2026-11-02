package io.github.laozhudu.linkplay.console.library

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.laozhudu.linkplay.console.ui.components.AppCard
import io.github.laozhudu.linkplay.console.ui.components.AppShapesExpressive
import io.github.laozhudu.linkplay.console.ui.components.PageHeader
import io.github.laozhudu.linkplay.console.ui.components.EmptyState
import io.github.laozhudu.linkplay.console.ui.components.SectionTitle
import io.github.laozhudu.linkplay.console.ui.components.VSpace
import io.github.laozhudu.linkplay.console.ui.theme.Spacing
import io.github.laozhudu.linkplay.protocol.playqueue.QueueLoopMode

/**
 * 手机本地音乐推送页。
 *
 * 本轮改掉四个问题：
 *
 * 1. **不再一进页面就弹权限框**。原来 `LaunchedEffect(Unit) { launcher.launch(...) }`，
 *    用户只是想进来看看，就被要求给媒体权限。改为点「扫描本机音乐」时才请求。
 * 2. **点一首就播这一首**，多选改为长按进入（与 NAS 页一致）。
 * 3. **修掉一句误导的技术描述**。原来写着「✓ 支持私有 PlayQueue（可整列表）」，
 *    但真机证明该路径推不动（docs/08 §5.2），现在改走客户端逐首投流，
 *    文案也换成用户能懂的说法。
 * 4. **去掉嵌套滚动** + 循环模式改单按钮 + 「停止共享」加二次确认。
 */
@Composable
fun QueueScreen(
    deviceIp: String,
    location: String?,
    onBack: () -> Unit,
) {
    val app = androidx.compose.ui.platform.LocalContext.current.applicationContext as android.app.Application
    val vm: QueueViewModel = viewModel(
        key = "queue-$deviceIp",
        factory = viewModelFactory { initializer { QueueViewModel(app, location) } },
    )
    val state by vm.state.collectAsState()
    var selecting by remember { mutableStateOf(false) }
    var confirmStop by remember { mutableStateOf(false) }

    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    // 权限只在真的要扫描时请求
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) vm.loadTracks() else Unit }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Spacing.lg,
                end = Spacing.lg,
                top = Spacing.sm,
                bottom = if (selecting) 104.dp else Spacing.xl,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            item {
                PageHeader(
                    title = "手机音乐",
                    subtitle = state.serverUrl?.let { "正在共享：$it" } ?: "手机当文件服务器，音箱自己来拉流",
                    onBack = onBack,
                )
            }

            state.error?.let { err ->
                item {
                    AppCard {
                        Text(err, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            // 还没扫描：只给一个动作，不预先弹权限
            if (state.tracks.isEmpty() && !state.loading) {
                item {
                    EmptyState(
                        title = "还没扫描本机音乐",
                        hint = "点下面的按钮，会给一次读取音频文件的权限，然后列出手机里的歌。",
                    )
                }
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .clip(AppShapesExpressive.pill)
                            .background(MaterialTheme.colorScheme.primary)
                            .clickable { launcher.launch(permission) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "扫描本机音乐",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
            }

            if (state.loading) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(Spacing.sm))
                        Text("正在扫描本地音乐…", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            if (state.tracks.isNotEmpty()) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionTitle(
                            "${state.tracks.size} 首歌",
                            if (selecting) "已选 ${state.selected.size}" else "点一首即播，长按可多选",
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedButton(onClick = { vm.loadTracks() }) { Text("重新扫描") }
                    }
                }

                items(state.tracks, key = { it.id }) { track ->
                    TrackRow(
                        selected = track.id in state.selected,
                        selecting = selecting,
                        title = track.title,
                        subtitle = "${track.artist ?: "未知艺术家"} · ${formatDuration(track.durationSec)}",
                        onClick = {
                            when {
                                selecting -> vm.toggle(track.id)
                                else -> vm.playNow(track)
                            }
                        },
                        onLongClick = {
                            selecting = true
                            vm.toggle(track.id)
                        },
                    )
                }

                item {
                    VSpace(Spacing.sm)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionTitle(
                            "循环模式",
                            QueueLoopMode.label(state.loopMode),
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedButton(onClick = { vm.setLoopMode(nextLoop(state.loopMode)) }) {
                            Text(QueueLoopMode.label(state.loopMode))
                        }
                    }
                }

                if (state.pushedCount > 0) {
                    item {
                        AppCard {
                            Text("已推送 ${state.pushedCount} 首", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "想停下就点下面的「停止共享」——注意这会让音箱侧也停止播放。",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                item {
                    // 危险操作二次确认：停止共享会同时中断正在播的歌
                    if (!confirmStop) {
                        OutlinedButton(onClick = { confirmStop = true }) { Text("停止共享") }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            Button(onClick = { vm.stopServer(); onBack() }) { Text("确认停止并返回") }
                            OutlinedButton(onClick = { confirmStop = false }) { Text("取消") }
                        }
                    }
                }
            }

            item {
                state.log.asReversed().take(4).forEach {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // 多选时底部浮出操作条
        if (selecting) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(Spacing.lg)
                    .clip(AppShapesExpressive.squircle)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Text(
                    "已选 ${state.selected.size}",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(onClick = vm::selectAll) { Text("全选") }
                Button(
                    onClick = { vm.pushSelected(); selecting = false },
                    enabled = state.selected.isNotEmpty() && !state.busy,
                ) { Text("推送") }
                OutlinedButton(onClick = { selecting = false; vm.clearSelection() }) { Text("取消") }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrackRow(
    selected: Boolean,
    selecting: Boolean,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppShapesExpressive.squircle)
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                else MaterialTheme.colorScheme.surfaceVariant,
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = Spacing.md, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selecting) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Text(
                        "✓",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
            Spacer(Modifier.width(Spacing.md))
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Text(
            "▶",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun nextLoop(current: Int): Int = when (current) {
    QueueLoopMode.ALL -> QueueLoopMode.SINGLE
    QueueLoopMode.SINGLE -> QueueLoopMode.SHUFFLE
    QueueLoopMode.SHUFFLE -> QueueLoopMode.NONE
    else -> QueueLoopMode.ALL
}

private fun formatDuration(seconds: Long): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%d:%02d".format(m, s)
}
