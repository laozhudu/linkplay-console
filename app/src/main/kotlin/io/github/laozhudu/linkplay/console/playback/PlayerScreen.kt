package io.github.laozhudu.linkplay.console.playback

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil.compose.AsyncImage
import io.github.laozhudu.linkplay.console.ui.components.AppShapesExpressive
import io.github.laozhudu.linkplay.console.ui.components.BackGlyph
import io.github.laozhudu.linkplay.console.ui.components.GlyphSize
import io.github.laozhudu.linkplay.console.ui.components.LoopGlyph
import io.github.laozhudu.linkplay.console.ui.components.PauseGlyph
import io.github.laozhudu.linkplay.console.ui.components.PlayGlyph
import io.github.laozhudu.linkplay.console.ui.components.SkipGlyph
import io.github.laozhudu.linkplay.console.ui.components.VolumeGlyph
import io.github.laozhudu.linkplay.console.ui.components.WaveProgressSlider
import io.github.laozhudu.linkplay.console.ui.theme.LocalExtraColors
import io.github.laozhudu.linkplay.console.ui.theme.Spacing
import io.github.laozhudu.linkplay.console.ui.theme.StatusColors
import io.github.laozhudu.linkplay.console.ui.theme.seedOf
import io.github.laozhudu.linkplay.protocol.player.LoopMode
import io.github.laozhudu.linkplay.protocol.queue.QueueItem
import io.github.laozhudu.linkplay.protocol.upnp.TransportState

/**
 * 播放页 —— 按 QQ 音乐的结构做，不再自创。
 *
 * ## 版式（自上而下）
 *
 * ```
 * 设备名 · 状态                          ⋯ 设置
 *
 *         ┌───────────────┐
 *         │   大圆角封面   │   ← 播放时微微放大
 *         └───────────────┘
 *            曲名（大、粗）
 *            歌手
 *     1:23 ─────●──────── 4:56
 *    循环   ⏮   ▶(大)   ⏭    ☰ 列表
 *    🔊 ─────────●──── 60
 *         （折叠）投流与日志
 * ```
 *
 * 用户的意见是「不符合通用操控」，而通用就是这一个：封面上、歌名中、进度下、
 * 一排控制键居中、音量在最底、列表用右侧按钮弹出。上一版把列表直接铺在页面下方，
 * 歌一多就把控制键挤到屏幕外，等于每次都要滑到下面才能按暂停。
 *
 * ## 三个必须跟手的点
 *
 * 1. **音量**：拖动时立刻改本地值 + 走合并通道下发（[PlaybackViewModel.setVolumeLive]）。
 *    旧实现走 `act()` 的 `busy` 闸门，拖动时第二个请求就被丢掉，松手才生效 —— 这就是「不跟手」的根因。
 * 2. **进度**：拖动时只改本地草稿，松手才 seek（逐像素 seek 会让设备进度反复回弹）。
 * 3. **播放列表**：点一首 → 服务在跑就跳过去，不在跑就直接投这一首；删一首同理，
 *    绝不出现「点了没反应」。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    deviceIp: String,
    deviceLocation: String?,
    /** 一级页面时不显示返回箭头（底部 Tab 换页）。 */
    onBack: (() -> Unit)? = null,
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

    var scrubDraft by remember { mutableStateOf<Float?>(null) }
    var volDraft by remember { mutableStateOf<Float?>(null) }
    var showSheet by remember { mutableStateOf(false) }
    var showAdvanced by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val playing = state.state == TransportState.PLAYING || state.state == TransportState.TRANSITIONING
    val duration = state.durationSec.coerceAtLeast(0)
    val progress = scrubDraft ?: state.progress
    val shownVolume = volDraft ?: (state.volume.toFloat() / 100f)
    val art = state.albumArtUrl?.takeIf { it.isNotBlank() }
        ?: queue.current?.albumArtUrl?.takeIf { it.isNotBlank() }
    val title = state.title ?: queue.current?.title
    val artist = state.artist ?: queue.current?.artist

    // 松手才 seek：逐像素 seek 会让设备进度反复回弹
    LaunchedEffect(scrubDraft) {
        val d = scrubDraft ?: return@LaunchedEffect
        if (duration <= 0) return@LaunchedEffect
        vm.seekTo((d * duration).toLong())
    }

    val artworkScale by animateFloatAsState(
        targetValue = if (playing) 1f else 0.94f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "artwork-scale",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(if (playing) palette.top else extras.backdropTop, palette.bottom),
                ),
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.xl),
        ) {
            // ---------- 顶栏 ----------
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onBack != null) {
                    CircleIcon(onClick = onBack) {
                        BackGlyph(extras.onBackdrop, Modifier.size(GlyphSize.medium))
                    }
                    Spacer(Modifier.width(Spacing.sm))
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        state.friendlyName ?: deviceIp,
                        style = MaterialTheme.typography.titleMedium,
                        color = extras.onBackdrop,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        buildString {
                            append(if (playing) "正在播放" else state.channel ?: "连接中")
                            state.sourceLabel?.let { append(" · ").append(it) }
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = extras.onBackdropMuted,
                        maxLines = 1,
                    )
                }
                CircleIcon(onClick = onOpenSettings) {
                    Text("⋯", style = MaterialTheme.typography.headlineSmall, color = extras.onBackdrop)
                }
            }

            // ---------- 封面 ----------
            Spacer(Modifier.height(Spacing.sm))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .graphicsLayer {
                        scaleX = artworkScale
                        scaleY = artworkScale
                    }
                    .clip(AppShapesExpressive.squircle)
                    .background(
                        Brush.linearGradient(listOf(palette.accent.copy(alpha = 0.55f), palette.top)),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (art != null) {
                    AsyncImage(
                        model = art,
                        contentDescription = "专辑封面",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Text(
                        text = (title ?: "?").take(1).uppercase(),
                        fontSize = 96.sp,
                        fontWeight = FontWeight.Black,
                        color = extras.onBackdrop.copy(alpha = 0.85f),
                    )
                }
            }

            // ---------- 曲名 / 歌手 ----------
            Spacer(Modifier.height(Spacing.lg))
            Text(
                text = title ?: "未在播放",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = extras.onBackdrop,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = artist
                    ?: if (state.state == TransportState.NO_MEDIA_PRESENT) "投一首歌试试" else deviceIp,
                style = MaterialTheme.typography.bodyMedium,
                color = extras.onBackdropMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            // ---------- 进度 ----------
            Spacer(Modifier.height(Spacing.md))
            WaveProgressSlider(
                value = progress,
                onValueChange = { scrubDraft = it },
                onValueChangeFinished = { scrubDraft = null },
                modifier = Modifier.fillMaxWidth(),
                activeColor = extras.onBackdrop,
                inactiveColor = extras.onBackdropMuted.copy(alpha = 0.28f),
                thumbColor = palette.accent,
                animated = scrubDraft == null,
                height = 22.dp,
            )
            Row(Modifier.fillMaxWidth()) {
                Text(
                    PlaybackViewModel.format((progress * duration).toLong()),
                    style = MaterialTheme.typography.labelSmall,
                    color = extras.onBackdropMuted,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    PlaybackViewModel.format(duration),
                    style = MaterialTheme.typography.labelSmall,
                    color = extras.onBackdropMuted,
                )
            }

            // ---------- 主控：循环 · 上一首 · 播放 · 下一首 · 列表 ----------
            Spacer(Modifier.height(Spacing.md))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircleIcon(onClick = vm::cycleLoopMode, size = 44.dp) {
                    LoopGlyph(
                        color = if (state.loopMode == LoopMode.OFF) extras.onBackdropMuted else palette.accent,
                        mode = state.loopMode.wire,
                        modifier = Modifier.size(GlyphSize.medium),
                    )
                }
                CircleIcon(onClick = vm::previous, size = 52.dp) {
                    SkipGlyph(extras.onBackdrop, forward = false, modifier = Modifier.size(30.dp))
                }
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .background(extras.onBackdrop)
                        .clickable { if (playing) vm.pause() else vm.play() },
                    contentAlignment = Alignment.Center,
                ) {
                    if (playing) PauseGlyph(palette.top, Modifier.size(32.dp))
                    else PlayGlyph(palette.top, Modifier.size(32.dp))
                }
                CircleIcon(onClick = vm::next, size = 52.dp) {
                    SkipGlyph(extras.onBackdrop, forward = true, modifier = Modifier.size(30.dp))
                }
                CircleIcon(onClick = { showSheet = true }, size = 44.dp) {
                    PlaylistGlyph(
                        color = if (queue.items.isEmpty()) extras.onBackdropMuted else extras.onBackdrop,
                        modifier = Modifier.size(GlyphSize.medium),
                    )
                }
            }

            // ---------- 音量 ----------
            Spacer(Modifier.height(Spacing.md))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircleIcon(onClick = vm::toggleMute, size = 36.dp) {
                    VolumeGlyph(
                        color = if (state.mute) extras.onBackdropMuted else extras.onBackdrop,
                        muted = state.mute,
                        modifier = Modifier.size(GlyphSize.small),
                    )
                }
                Spacer(Modifier.width(Spacing.xs))
                WaveProgressSlider(
                    value = shownVolume,
                    // 拖动中：本地值跟着手走，同时限速下发，松手不停顿
                    onValueChange = {
                        volDraft = it
                        vm.setVolumeLive((it * 100).toInt())
                    },
                    onValueChangeFinished = { volDraft = null },
                    modifier = Modifier.weight(1f),
                    activeColor = extras.onBackdrop,
                    inactiveColor = extras.onBackdropMuted.copy(alpha = 0.28f),
                    thumbColor = palette.accent,
                    animated = false,
                    height = 20.dp,
                )
                Spacer(Modifier.width(Spacing.sm))
                Text(
                    if (state.mute) "静音" else state.volume.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = extras.onBackdropMuted,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(34.dp),
                )
            }

            (state.error ?: state.unavailableReason)?.let { msg ->
                Spacer(Modifier.height(Spacing.sm))
                Text(msg, style = MaterialTheme.typography.bodySmall, color = StatusColors.error)
            }

            // ---------- 高级（折叠）----------
            Spacer(Modifier.height(Spacing.md))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(AppShapesExpressive.pill)
                    .clickable { showAdvanced = !showAdvanced }
                    .padding(vertical = Spacing.sm, horizontal = Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (showAdvanced) "收起" else "投流地址与设备日志",
                    style = MaterialTheme.typography.labelMedium,
                    color = extras.onBackdropMuted,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    if (showAdvanced) "−" else "+",
                    style = MaterialTheme.typography.titleMedium,
                    color = extras.onBackdropMuted,
                )
            }

            if (showAdvanced) {
                CastSection(state = state, vm = vm, onOpenNas = onOpenNas, onOpenLocalQueue = onOpenLocalQueue)
            }

            Spacer(Modifier.height(Spacing.xxl))
        }
    }

    // ---------- 播放列表：从底部升起的整块面板 ----------
    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            PlaylistSheet(
                queue = queue,
                onPlay = { index ->
                    vm.playQueueItem(index)
                    showSheet = false
                },
                onRemove = vm::removeQueueItem,
                onOpenNas = { showSheet = false; onOpenNas() },
                onOpenLocalQueue = { showSheet = false; onOpenLocalQueue() },
                onOpenLibrary = { showSheet = false; onOpenLibrary() },
            )
        }
    }
}

/** 播放列表面板：单列表，不与外层抢手势。 */
@Composable
private fun PlaylistSheet(
    queue: QueueRunState,
    onPlay: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onOpenNas: () -> Unit,
    onOpenLocalQueue: () -> Unit,
    onOpenLibrary: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().fillMaxSize(0.75f)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "播放列表",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Text(
                if (queue.items.isEmpty()) "空" else "${queue.items.size} 首",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (queue.items.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "还没有队列。先从下面挑一个来源，点一首歌就会进来。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(Spacing.md))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    TextButton(onClick = onOpenNas) { Text("NAS 音乐") }
                    TextButton(onClick = onOpenLocalQueue) { Text("手机音乐") }
                    TextButton(onClick = onOpenLibrary) { Text("音乐库") }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(bottom = Spacing.xl),
            ) {
                itemsIndexed(queue.items) { index, item ->
                    PlaylistRow(
                        index = index,
                        item = item,
                        playing = index == queue.index && queue.running,
                        current = index == queue.index,
                        onClick = { onPlay(index) },
                        onRemove = { onRemove(index) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaylistRow(
    index: Int,
    item: QueueItem,
    playing: Boolean,
    current: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.lg, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(28.dp), contentAlignment = Alignment.Center) {
            if (playing) {
                EqualizerBars(accent)
            } else {
                Text(
                    "${index + 1}",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (current) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(Spacing.sm))
        Column(Modifier.weight(1f)) {
            Text(
                item.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal,
                color = if (current) accent else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            item.artist?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "✕",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 正在播的小均衡器条：比一个静态三角更符合播放器习惯。 */
@Composable
private fun EqualizerBars(color: Color) {
    Canvas(Modifier.size(width = 14.dp, height = 16.dp)) {
        val barW = size.width / 5
        listOf(0.55f, 1f, 0.7f).forEachIndexed { i, h ->
            val bh = size.height * h
            val x = barW * (i * 2 + 0.5f)
            drawLine(
                color = color,
                start = Offset(x, (size.height - bh) / 2),
                end = Offset(x, (size.height + bh) / 2),
                strokeWidth = barW,
                cap = StrokeCap.Round,
            )
        }
    }
}

/** 播放列表图标（三条横线 + 小三角）：手绘，省掉 material-icons 的 3MB。 */
@Composable
private fun PlaylistGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val sw = w * 0.09f
        listOf(0.26f, 0.5f, 0.74f).forEachIndexed { i, y ->
            val endX = if (i == 0) w * 0.74f else w * 0.58f
            drawLine(
                color = color,
                start = Offset(w * 0.12f, h * y),
                end = Offset(endX, h * y),
                strokeWidth = sw,
                cap = StrokeCap.Round,
            )
        }
        val p = Path().apply {
            moveTo(w * 0.74f, h * 0.33f)
            lineTo(w * 0.74f, h * 0.67f)
            lineTo(w * 0.95f, h * 0.5f)
            close()
        }
        drawPath(p, color)
    }
}

@Composable
private fun CircleIcon(size: Dp = 40.dp, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** 投流与日志（折叠区）。功能不变，只是不再占据主界面。 */
@Composable
private fun CastSection(
    state: PlaybackUiState,
    vm: PlaybackViewModel,
    onOpenNas: () -> Unit,
    onOpenLocalQueue: () -> Unit,
) {
    val extras = LocalExtraColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .padding(Spacing.md)
            .imePadding(),
    ) {
        OutlinedTextField(
            value = state.castUrl,
            onValueChange = vm::setCastUrl,
            label = { Text("要投的音频地址") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(Spacing.xs))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "这条链接需要 Referer（自动转发）",
                style = MaterialTheme.typography.labelMedium,
                color = extras.onBackdropMuted,
                modifier = Modifier.weight(1f),
            )
            Switch(checked = state.castNeedsHeaders, onCheckedChange = vm::setCastNeedsHeaders)
        }
        if (state.castNeedsHeaders) {
            OutlinedTextField(
                value = state.castReferer,
                onValueChange = vm::setCastReferer,
                label = { Text("Referer") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(Spacing.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            TextButton(onClick = vm::cast, enabled = !state.busy) { Text("投流") }
            TextButton(onClick = onOpenNas) { Text("从 NAS 选") }
            TextButton(onClick = onOpenLocalQueue) { Text("手机音乐") }
        }
        if (state.log.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.xs))
            Text(
                state.log.takeLast(8).joinToString("\n"),
                style = MaterialTheme.typography.labelSmall,
                color = extras.onBackdropMuted,
            )
        }
    }
}
