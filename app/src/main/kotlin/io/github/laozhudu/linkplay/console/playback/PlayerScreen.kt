package io.github.laozhudu.linkplay.console.playback

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.laozhudu.linkplay.console.ui.components.AppShapesExpressive
import io.github.laozhudu.linkplay.console.ui.components.BackGlyph
import io.github.laozhudu.linkplay.console.ui.components.ChevronGlyph
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.TextButton
import io.github.laozhudu.linkplay.console.ui.components.EmptyState
import io.github.laozhudu.linkplay.console.ui.components.VSpace
import io.github.laozhudu.linkplay.console.ui.components.GlyphSize
import io.github.laozhudu.linkplay.console.ui.components.LoopGlyph
import io.github.laozhudu.linkplay.console.ui.components.PauseGlyph
import io.github.laozhudu.linkplay.console.ui.components.PlayGlyph
import io.github.laozhudu.linkplay.console.ui.components.SkipGlyph
import io.github.laozhudu.linkplay.console.ui.components.StopGlyph
import io.github.laozhudu.linkplay.console.ui.components.VolumeGlyph
import io.github.laozhudu.linkplay.console.ui.components.WaveProgressSlider
import io.github.laozhudu.linkplay.console.ui.theme.LocalExtraColors
import io.github.laozhudu.linkplay.console.ui.theme.SeedPalette
import io.github.laozhudu.linkplay.console.ui.theme.Spacing
import io.github.laozhudu.linkplay.console.ui.theme.seedOf
import io.github.laozhudu.linkplay.protocol.player.LoopMode
import io.github.laozhudu.linkplay.protocol.upnp.TransportState

/**
 * 播放页（遥控器主体）。
 *
 * 视觉语言取自 Rhythm（Material 3 Expressive）：**圆形封面 + 花形侧键 + 大胶囊主键 + 波浪进度**。
 * 实现是独立写的，原因是直接照搬它的 Composable 需要 material3 1.5.x 与 composeBom 2026.09
 * （`MaterialShapes` / `LinearWavyProgressIndicator`），为几个图形升整个 Compose 版本不划算。
 *
 * 布局与取舍：
 * - 封面圆形、宽度占屏 62%，播放时 scale 1.0、暂停时 0.84（Rhythm 同款弹簧动效）——
 *   一眼就能判断在不在播，不用去看按钮状态。
 * - 主控是**带文字的大胶囊**（「播放」/「暂停」），比圆形图标按钮更容易按中，也更符合 Expressive。
 * - 两侧是花形按钮（8 花瓣），视觉重量明显低于主胶囊。
 * - 进度用波浪条：拖动时振幅变大，播放中相位持续流动。
 * - 进度与音量都是**拖动只改本地、松手才下发**：设备侧 seek 会打断缓冲，连发会让曲子一直卡。
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    deviceIp: String,
    deviceLocation: String?,
    onBack: () -> Unit,
    /** 设备设置：改名/输入源/多房间/诊断都在那边，本页只做播放。 */
    onOpenSettings: () -> Unit = {},
    onOpenNas: () -> Unit = {},
    onOpenLibrary: () -> Unit = {},
    onOpenLocalQueue: () -> Unit = {},
) {
    val vm: PlaybackViewModel = viewModel(
        key = "player-$deviceIp",
        factory = viewModelFactory { initializer { PlaybackViewModel(deviceIp, deviceLocation) } },
    )
    val state by vm.state.collectAsState()
    val queue by QueueBus.state.collectAsState()

    val palette = remember(state.title, state.artist) { seedOf(state.title, state.artist) }
    val extras = LocalExtraColors.current

    var scrubbing by remember { mutableStateOf<Float?>(null) }
    var volumeDraft by remember { mutableStateOf<Float?>(null) }
    var showDetails by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }

    val playing = state.state == TransportState.PLAYING || state.state == TransportState.TRANSITIONING
    val duration = state.durationSec.coerceAtLeast(0)
    val progress = scrubbing ?: state.progress
    val shownVolume = volumeDraft ?: (state.volume.toFloat() / 100f)

    // 封面：播放时撑满，暂停时略缩并下沉，形成「呼吸」感
    val artworkScale by animateFloatAsState(
        targetValue = if (playing) 1f else 0.84f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "artwork-scale",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        if (playing) palette.top else extras.backdropTop,
                        palette.bottom,
                    ),
                ),
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // ---- 顶栏 ----
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    BackGlyph(extras.onBackdrop, Modifier.size(GlyphSize.medium))
                }
                Spacer(Modifier.width(Spacing.sm))
                Column(Modifier.weight(1f)) {
                    Text(
                        state.friendlyName ?: deviceIp,
                        style = MaterialTheme.typography.titleMedium,
                        color = extras.onBackdrop,
                        maxLines = 1,
                    )
                    Text(
                        buildString {
                            append(state.channel ?: "连接中")
                            state.sourceLabel?.let { append(" · ").append(it) }
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = extras.onBackdropMuted,
                    )
                }
                // 右上角：设备设置入口（低频动作，但需要一个明确出口）
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(AppShapesExpressive.smallScallop)
                        .background(extras.onBackdrop.copy(alpha = 0.10f))
                        .clickable(onClick = onOpenSettings),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "⋯",
                        style = MaterialTheme.typography.titleLarge,
                        color = extras.onBackdrop,
                    )
                }
            }

            Spacer(Modifier.height(Spacing.xl))

            // ---- 圆形封面 ----
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.62f)
                    .aspectRatio(1f)
                    .graphicsLayer { scaleX = artworkScale; scaleY = artworkScale }
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(palette.top, palette.accent.copy(alpha = 0.55f), palette.top),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                val initial = state.title?.trim()?.firstOrNull()?.toString()
                Text(
                    text = if (initial != null) initial.uppercase() else "♪",
                    fontSize = 88.sp,
                    lineHeight = 88.sp,
                    color = palette.accent.copy(alpha = 0.9f),
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(Modifier.height(Spacing.xl))

            // ---- 曲名 / 艺术家（左对齐，Rhythm 同为左对齐） ----
            Column(Modifier.fillMaxWidth()) {
                Text(
                    state.title ?: if (state.ready) "未在播放" else "准备中…",
                    style = MaterialTheme.typography.headlineMedium,
                    color = extras.onBackdrop,
                    maxLines = 2,
                )
                Text(
                    state.artist?.takeIf { it.isNotBlank() } ?: "—",
                    style = MaterialTheme.typography.labelMedium,
                    color = extras.onBackdropMuted,
                    maxLines = 1,
                )
            }

            Spacer(Modifier.height(Spacing.sm))

            // ---- 波浪进度 ----
            WaveProgressSlider(
                value = progress,
                onValueChange = { scrubbing = it },
                onValueChangeFinished = {
                    val target = ((scrubbing ?: 0f) * duration).toLong()
                    scrubbing = null
                    if (duration > 0) vm.seekTo(target)
                },
                activeColor = palette.accent,
                inactiveColor = extras.onBackdropMuted.copy(alpha = 0.30f),
                thumbColor = extras.onBackdrop,
                animated = playing,
                enabled = duration > 0,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    PlaybackViewModel.format(if (scrubbing != null) (progress * duration).toLong() else state.positionSec),
                    style = MaterialTheme.typography.labelMedium,
                    color = extras.onBackdropMuted,
                )
                Text(
                    if (duration > 0) PlaybackViewModel.format(duration) else "直播 / 未知长度",
                    style = MaterialTheme.typography.labelMedium,
                    color = extras.onBackdropMuted,
                )
            }

            Spacer(Modifier.height(Spacing.lg))

            // ---- 主控：花形上一首 | 大胶囊播放/暂停 | 花形下一首 ----
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                ScallopButton(onClick = vm::previous, size = 58.dp) {
                    SkipGlyph(extras.onBackdrop, forward = false, modifier = Modifier.size(GlyphSize.large))
                }
                Box(
                    modifier = Modifier
                        .height(64.dp)
                        .width(184.dp)
                        .clip(AppShapesExpressive.pill)
                        .background(
                            Brush.linearGradient(
                                listOf(palette.accent, palette.accent.copy(alpha = 0.72f)),
                            ),
                        )
                        .clickable(enabled = state.ready) {
                            if (playing) vm.pause() else vm.play()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (playing) "暂停" else "播放",
                        style = MaterialTheme.typography.titleLarge,
                        color = palette.onAccent,
                    )
                }
                ScallopButton(onClick = vm::next, size = 58.dp) {
                    SkipGlyph(extras.onBackdrop, forward = true, modifier = Modifier.size(GlyphSize.large))
                }
            }

            Spacer(Modifier.height(Spacing.md))

            // ---- 次级操作：音量 / 停止 ----
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable { vm.toggleMute() },
                    contentAlignment = Alignment.Center,
                ) {
                    VolumeGlyph(
                        color = if (state.mute) extras.onBackdropMuted else extras.onBackdrop,
                        muted = state.mute,
                        modifier = Modifier.size(GlyphSize.medium),
                    )
                }
                Spacer(Modifier.width(Spacing.xs))
                WaveProgressSlider(
                    value = shownVolume,
                    onValueChange = { volumeDraft = it },
                    onValueChangeFinished = {
                        val v = ((volumeDraft ?: shownVolume) * 100).toInt().coerceIn(0, 100)
                        volumeDraft = null
                        vm.setVolume(v)
                    },
                    modifier = Modifier.weight(1f),
                    activeColor = extras.onBackdrop,
                    inactiveColor = extras.onBackdropMuted.copy(alpha = 0.30f),
                    thumbColor = palette.accent,
                    animated = false,
                    height = 28.dp,
                )
                Spacer(Modifier.width(Spacing.sm))
                Text(
                    if (state.mute) "静音" else "${state.volume}",
                    style = MaterialTheme.typography.labelMedium,
                    color = extras.onBackdropMuted,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(32.dp),
                )
                Spacer(Modifier.width(Spacing.xs))
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable(onClick = vm::stop),
                    contentAlignment = Alignment.Center,
                ) {
                    StopGlyph(extras.onBackdrop, modifier = Modifier.size(GlyphSize.medium))
                }
            }

            // ---- 播放列表 + 添加来源：市面播放器都把「队列」放在播放页里 ----
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(AppShapesExpressive.pill)
                        .background(extras.onBackdrop.copy(alpha = 0.10f))
                        .clickable { showQueue = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (queue.items.isEmpty()) "播放列表 · 空"
                        else "播放列表 · ${queue.total} 首（第 ${queue.index + 1}）",
                        style = MaterialTheme.typography.labelMedium,
                        color = extras.onBackdrop,
                        maxLines = 1,
                    )
                }
                Spacer(Modifier.width(Spacing.sm))
                Box(
                    modifier = Modifier
                        .height(44.dp)
                        .clip(AppShapesExpressive.pill)
                        .background(extras.onBackdrop.copy(alpha = 0.10f))
                        .clickable(onClick = onOpenNas)
                        .padding(horizontal = Spacing.md),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("＋ 添加", style = MaterialTheme.typography.labelMedium, color = extras.onBackdrop)
                }
            }

            (state.error ?: state.unavailableReason)?.let {
                Spacer(Modifier.height(Spacing.sm))
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.height(Spacing.md))

            // ---- 折叠区：投流 / 设备细节 ----
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(AppShapesExpressive.pill)
                    .clickable { showDetails = !showDetails }
                    .padding(vertical = Spacing.sm, horizontal = Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (showDetails) "收起" else "投流地址与设备详情",
                    style = MaterialTheme.typography.labelMedium,
                    color = extras.onBackdropMuted,
                    modifier = Modifier.weight(1f),
                )
                ChevronGlyph(extras.onBackdropMuted, expanded = showDetails, modifier = Modifier.size(GlyphSize.small))
            }
            AnimatedVisibility(
                visible = showDetails,
                enter = fadeIn() + slideInVertically { it / 3 },
                exit = fadeOut() + slideOutVertically { it / 3 },
            ) {
                Column(Modifier.fillMaxWidth()) {
                    Text(
                        "投流：填一条能直连的音频 URL，音箱自己去拉",
                        style = MaterialTheme.typography.bodySmall,
                        color = extras.onBackdropMuted,
                    )
                    Spacer(Modifier.height(Spacing.xs))
                    OutlinedTextField(
                        value = state.castUrl,
                        onValueChange = vm::setCastUrl,
                        placeholder = { Text("http://…/song.mp3") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    // 防盗链开关：设备只发裸 GET，带 Referer 校验的链接直投必然 403（docs/16）
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = state.castNeedsHeaders,
                            onCheckedChange = { vm.setCastNeedsHeaders(it) },
                        )
                        Text(
                            "这条链接需要 Referer（自动转发）",
                            style = MaterialTheme.typography.bodySmall,
                            color = extras.onBackdrop,
                        )
                    }
                    if (state.castNeedsHeaders) {
                        OutlinedTextField(
                            value = state.castReferer,
                            onValueChange = vm::setCastReferer,
                            placeholder = { Text("https://music.example.com/") },
                            label = { Text("Referer") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(Spacing.xs))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        androidx.compose.material3.Button(onClick = vm::cast, enabled = !state.busy) {
                            Text("投给音箱")
                        }
                        OutlinedButton(onClick = vm::refresh, enabled = !state.busy) {
                            Text("刷新状态")
                        }
                    }
                    Spacer(Modifier.height(Spacing.sm))
                    state.log.asReversed().take(4).forEach {
                        Text(
                            it,
                            style = MaterialTheme.typography.labelMedium,
                            color = extras.onBackdropMuted,
                        )
                    }
                }
            }
            Spacer(Modifier.height(Spacing.xxl))
        }

        // ---------- 播放列表弹层 ----------
        // 用 ModalBottomSheet 而不是在页面里再塞一个列表：这个页面已经有纵向滚动，
        // 再套一层列表就是两层滚动抢手势（上一轮刚从别处清掉这种结构）。
        if (showQueue) {
            ModalBottomSheet(
                onDismissRequest = { showQueue = false },
                containerColor = MaterialTheme.colorScheme.surface,
            ) {
                PlaylistSheet(
                    queue = queue,
                    onSkip = { i ->
                        QueueBus.skipTo(i)
                        showQueue = false
                    },
                    onRemove = { i -> QueueBus.removeAt(i) },
                    onAddSources = {
                        showQueue = false
                        onOpenNas()
                    },
                )
            }
        }
    }
}

/**
 * 播放列表：当前队列 + 点某首跳过去 + 删除。
 *
 * 为什么放在播放页而不是独立页面：主流播放器（Spotify / Apple Music / 网易云）都是
 * 「播放页 → 点队列按钮 → 当前队列」，返回即回到播放页。独立页面会让「我现在放的是第几首、
 * 下一首是什么」这种问题需要多跳两次。
 */
@Composable
private fun PlaylistSheet(
    queue: QueueRunState,
    onSkip: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onAddSources: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("播放列表", style = MaterialTheme.typography.titleLarge)
                Text(
                    if (queue.items.isEmpty()) "队列为空"
                    else "${queue.total} 首 · 正在播第 ${queue.index + 1} 首",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onAddSources) { Text("添加曲目") }
        }
        VSpace(Spacing.sm)

        if (queue.items.isEmpty()) {
            EmptyState(
                title = "还没有曲目",
                hint = "从 NAS 媒体库或手机本地音乐里点一首，或者长按多选一批加进来。",
                modifier = Modifier.padding(horizontal = Spacing.lg),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.xs),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                itemsIndexed(queue.items) { index, item ->
                    val current = index == queue.index
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(AppShapesExpressive.squircle)
                            .background(
                                if (current) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                                else Color.Transparent,
                            )
                            .clickable { onSkip(index) }
                            .padding(start = Spacing.md, end = Spacing.xs, top = Spacing.sm, bottom = Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (current) "▶" else "${index + 1}",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (current) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(28.dp),
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                item.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = if (current) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            item.artist?.takeIf { it.isNotBlank() }?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .clickable { onRemove(index) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "✕",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                item { Spacer(Modifier.height(Spacing.xl)) }
            }
        }
    }
}

/** 花形按钮：8 花瓣，触控区比图形大一圈。 */
@Composable
private fun ScallopButton(
    onClick: () -> Unit,
    size: androidx.compose.ui.unit.Dp,
    content: @Composable () -> Unit,
) {
    val extras = LocalExtraColors.current
    Box(
        modifier = Modifier
            .size(size)
            .clip(AppShapesExpressive.controlScallop)
            .background(extras.onBackdrop.copy(alpha = 0.10f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}
