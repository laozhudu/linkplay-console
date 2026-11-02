package io.github.laozhudu.linkplay.console.nas

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
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
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
import io.github.laozhudu.linkplay.console.ui.components.PillTag
import io.github.laozhudu.linkplay.console.ui.components.SectionTitle
import io.github.laozhudu.linkplay.console.ui.components.VSpace
import io.github.laozhudu.linkplay.console.ui.theme.LocalExtraColors
import io.github.laozhudu.linkplay.console.ui.theme.Spacing
import io.github.laozhudu.linkplay.console.ui.theme.seedOf
import io.github.laozhudu.linkplay.protocol.queue.ClientSideQueue
import io.github.laozhudu.linkplay.protocol.upnp.BrowseEntry

/**
 * NAS 媒体库：浏览 DLNA 服务器并把曲目推给音箱。
 *
 * 本轮按「人的操作顺序」重排，改掉四个反直觉的地方：
 *
 * 1. **点一行就播这一首**。原来要「勾选 → 加入队列 → 推送」三步才听到一首歌；
 *    多选改成**长按进入**，只服务「攒一批再播」这种次要场景。
 * 2. **整行可点**。原来目录是一个 `TextButton("📁 标题")`，只有文字那一点能点，
 *    行尾空白点了没反应；现在整行可点并在行尾给明确指向（目录 ›／曲目 ▶）。
 * 3. **去掉嵌套滚动**。原来是 `verticalScroll(Column)` 里套 `LazyColumn(max 300dp)`，
 *    两根滚动轴抢手势，手机上常常滑不动中间那块。现在整页只有一个 `LazyColumn`。
 * 4. **主动作固定可见**。多选时底部浮出操作条（已选 N / 全选 / 加入队列 / 取消），
 *    不用滚到页面底部去找；循环模式从三个并排按钮改成一个循环切换按钮。
 *
 * 「加入队列」而非「直接推整列表」的原因是本固件不支持音箱侧排队（docs/08 §5.2），
 * 得先在控制端攒好列表再逐首投流。
 */
@Composable
fun NasScreen(
    deviceIp: String,
    deviceLocation: String?,
    /** 设备 UUID：记忆（上次的 NAS、断点续播）按它存，换 IP 也不丢。 */
    deviceUuid: String?,
    onBack: () -> Unit,
) {
    val app = LocalContext.current.applicationContext as android.app.Application
    val vm: NasViewModel = viewModel(
        key = "nas-$deviceIp",
        factory = viewModelFactory { initializer { NasViewModel(app, deviceIp, deviceLocation, deviceUuid) } },
    )
    val state by vm.state.collectAsState()
    val extras = LocalExtraColors.current
    var confirmClear by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Spacing.lg,
                end = Spacing.lg,
                top = Spacing.sm,
                // 底部留白给浮动操作条，否则最后一条会被挡住
                bottom = if (state.selecting) 104.dp else Spacing.xl,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            // ---------- 头部 ----------
            item {
                PageHeader(
                    title = "NAS 音乐",
                    subtitle = state.connectedServer ?: "DLNA 服务器上的曲目，点一首即播",
                    onBack = onBack,
                    trailing = {
                        if (state.connectedServer != null) {
                            PillTag(text = "已连接", color = MaterialTheme.colorScheme.primary)
                        }
                    },
                )
            }

            state.error?.let { err ->
                item {
                    AppCard {
                        Text(err, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        VSpace(Spacing.xs)
                        OutlinedButton(onClick = vm::clearError) { Text("知道了") }
                    }
                }
            }

            // ---------- 未连接：上次的服务器 / 发现 / 手动 ----------
            if (state.connectedServer == null) {
                state.rememberedServer?.let { server ->
                    item {
                        val palette = remember(server.label) { seedOf(server.friendlyName, server.label) }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(AppShapesExpressive.squircle)
                                .background(
                                    Brush.linearGradient(listOf(palette.top, palette.accent.copy(alpha = 0.45f))),
                                )
                                .clickable(onClick = vm::resumeRemembered)
                                .padding(Spacing.lg),
                        ) {
                            Column {
                                Text(
                                    "上次：${server.friendlyName ?: server.label}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = extras.onBackdrop,
                                )
                                Text(
                                    if (state.rememberedPath.isEmpty()) server.label + " · 根目录"
                                    else server.label + " · " +
                                        state.rememberedPath.joinToString(" / ") { it.title },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = extras.onBackdropMuted,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    "点一下直接回去",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = extras.onBackdropMuted,
                                )
                            }
                        }
                    }
                }

                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .clip(AppShapesExpressive.pill)
                            .background(MaterialTheme.colorScheme.primary)
                            .clickable(enabled = !state.scanning, onClick = vm::scan),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (state.scanning) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                                Spacer(Modifier.width(Spacing.sm))
                                Text("正在搜索…", color = MaterialTheme.colorScheme.onPrimary)
                            }
                        } else {
                            Text(
                                "搜索 DLNA 服务器",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                    }
                }

                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = state.manualHost,
                            onValueChange = vm::setManualHost,
                            label = { Text("手动：IP 或 IP:端口") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(Spacing.sm))
                        Button(onClick = vm::connectManual) { Text("连接") }
                    }
                }

                items(state.servers, key = { it.ip }) { server ->
                    AppCard(onClick = { vm.connect(server) }) {
                        Text(server.ip, style = MaterialTheme.typography.titleMedium)
                        Text(
                            server.server ?: "媒体服务器",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (state.servers.isEmpty() && !state.scanning && state.rememberedServer == null) {
                    item {
                        EmptyState(
                            title = "还没连过服务器",
                            hint = "点上面的搜索，或直接填 NAS 地址（例如 192.168.99.223:8200）。",
                        )
                    }
                }
            }

            // ---------- 已连接：路径 + 目录 ----------
            if (state.connectedServer != null) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = vm::up, enabled = state.path.size > 1) { Text("上一级") }
                        Spacer(Modifier.width(Spacing.sm))
                        Text(
                            state.path.joinToString(" / ") { it.title }.ifEmpty { "根目录" },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                if (state.loadingDir) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(Spacing.sm))
                            Text("读取目录…", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                if (state.entries.isEmpty() && !state.loadingDir) {
                    item { EmptyState("这一层没有内容", "回上一级换个目录看看。") }
                }

                items(state.entries, key = { it.id }) { entry ->
                    EntryRow(
                        entry = entry,
                        selected = entry.id in state.selected,
                        selecting = state.selecting,
                        onClick = {
                            when {
                                entry.isContainer -> vm.openDir(entry.id, entry.title)
                                state.selecting -> vm.toggleEntry(entry)
                                // 平时点一下就播，不再要求先勾选
                                else -> vm.playNow(entry)
                            }
                        },
                        onLongClick = {
                            if (!entry.isContainer) {
                                vm.setSelecting(true)
                                vm.toggleEntry(entry)
                            }
                        },
                    )
                }

                // ---------- 队列区 ----------
                item {
                    VSpace(Spacing.sm)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionTitle(
                            "待播队列",
                            if (state.picked.isEmpty()) "还没攒曲目"
                            else "${state.picked.size} 首" + (state.nowPlaying?.let { " · 正在播：$it" } ?: ""),
                            modifier = Modifier.weight(1f),
                        )
                        // 循环模式：一个按钮循环切换，不再并排三个抢空间
                        OutlinedButton(onClick = { vm.setLoopMode(nextLoop(state.loopMode)) }) {
                            Text(loopLabel(state.loopMode))
                        }
                    }
                }

                state.resumable?.let { saved ->
                    if (!state.playing) {
                        item {
                            AppCard(onClick = vm::resumePlayback) {
                                Text("继续上次播放", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "从第 ${saved.index + 1} 首开始，剩余 ${saved.remaining.size} 首：" +
                                        (saved.remaining.firstOrNull()?.title ?: ""),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Button(
                            onClick = vm::startQueue,
                            enabled = !state.playing && state.picked.isNotEmpty(),
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(if (state.playing) "推送中（已播 ${state.playedCount}）" else "开始推送给音箱")
                        }
                        OutlinedButton(onClick = vm::stopQueue, enabled = state.playing) { Text("停止") }
                    }
                    if (state.picked.isNotEmpty()) {
                        VSpace(Spacing.xs)
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            if (!confirmClear) {
                                OutlinedButton(onClick = { confirmClear = true }) { Text("清空队列") }
                            } else {
                                // 二次确认：误触会丢掉攒了半天的列表，且无法撤销
                                Button(onClick = { vm.clearQueue(); confirmClear = false }) { Text("确认清空") }
                                OutlinedButton(onClick = { confirmClear = false }) { Text("取消") }
                            }
                        }
                    }
                }
            }

            // ---------- 控制面掉线自救 ----------
            if (state.controlLost) {
                item {
                    AppCard {
                        Text("音箱控制服务掉线（设备仍在线）", fontWeight = FontWeight.Medium)
                        Text(
                            "UPnP 端口 49152 关了。重启音箱可恢复，实测约 40 秒。",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        VSpace(Spacing.sm)
                        Button(onClick = vm::restartRenderer, enabled = !state.healing) {
                            if (state.healing) {
                                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(Spacing.sm))
                            }
                            Text(if (state.healing) "重启并等待恢复…" else "重启音箱")
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
                VSpace(Spacing.sm)
            }
        }

        // ---------- 浮动操作条：多选时固定在底部 ----------
        if (state.selecting) {
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
                OutlinedButton(onClick = vm::selectAllPlayable) { Text("全选") }
                Button(
                    onClick = { vm.addSelectedToQueue(); vm.setSelecting(false) },
                    enabled = state.selected.isNotEmpty(),
                ) { Text("加入队列") }
                OutlinedButton(onClick = { vm.setSelecting(false) }) { Text("取消") }
            }
        }
    }
}

/**
 * 一行目录/曲目：整行可点，长按进多选。
 *
 * 多选模式下才显示勾选圈 —— 平时每行挂一个 checkbox 会让「点一下播放」这件事变得含糊，
 * 用户会以为必须先勾。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EntryRow(
    entry: BrowseEntry,
    selected: Boolean,
    selecting: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val container = entry.isContainer
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
        if (selecting && !container) {
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
                entry.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val sub = buildList {
                entry.artist?.takeIf { it.isNotBlank() }?.let { add(it) }
                if (entry.durationSec > 0) add(NasViewModel.formatDuration(entry.durationSec))
                if (container) add("目录")
            }.joinToString(" · ")
            if (sub.isNotEmpty()) {
                Text(
                    sub,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }

        // 行尾给明确指向：目录是「进去」，曲目是「播放」
        Text(
            if (container) "›" else "▶",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun nextLoop(current: Int): Int = when (current) {
    ClientSideQueue.LOOP_ALL -> ClientSideQueue.LOOP_SINGLE
    ClientSideQueue.LOOP_SINGLE -> ClientSideQueue.LOOP_NONE
    else -> ClientSideQueue.LOOP_ALL
}

private fun loopLabel(mode: Int): String = when (mode) {
    ClientSideQueue.LOOP_ALL -> "列表循环"
    ClientSideQueue.LOOP_SINGLE -> "单曲循环"
    else -> "顺序播放"
}
