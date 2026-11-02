package io.github.laozhudu.linkplay.console.playback

import android.graphics.BitmapFactory
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
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
 * 播放页 —— **单层、不滚动、无浮层**。
 *
 * ## 为什么又重做一次
 *
 * 上一版把播放列表做成 `ModalBottomSheet`。用户的原话是
 * 「点击是多一层，没在原来的播放器层面播放和暂停，导致正在播放时列表等其他功能点不了」。
 * 也就是说：任何"另开一层"的东西都是错的方向，不管它多像 QQ 音乐。
 *
 * 这一版的硬约束：
 *
 * 1. **整页不滚动**（`Column` 固定，不用 `verticalScroll`）——
 *    滚动会抢手势，而且手指一滑控制键就跑到屏幕外；
 * 2. **没有 Dialog / BottomSheet / 二级页** —— 所有东西都在这一屏里；
 * 3. **只有底部一个可滚动区域**（内容区），靠一个二选一切换「播放列表 / 投流日志」，
 *    所以永远不会有"两个滚动轴打架"或"浮层挡着点不到"；
 * 4. 控制键用 `weight` 分配固定区域，**播放/暂停永远在屏幕内、永远可点**。
 *
 * ## 版式
 *
 * ```
 * 设备名 · 状态                                ⋯
 *        ┌────────── 封面（自适应）──────────┐
 *        曲名
 *        歌手
 *   1:23 ───────●──────── 4:56
 *   循环   ⏮   ▶(72dp)   ⏭   ⟳
 *   🔊 ──────────●───── 60
 *   [播放列表 12] [投流与日志]        ← 二选一，不是浮层
 *   ┌── 这一块是唯一的滚动区 ──┐
 *   │ 1 ▶  曲名            ✕ │
 *   │ 2    曲名            ✕ │
 *   └────────────────────────┘
 * ```
 */
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
    /** 底部内容区显示哪一块。false = 播放列表，true = 投流与日志。 */
    var showAdvanced by remember { mutableStateOf(false) }

    val playing = state.state == TransportState.PLAYING || state.state == TransportState.TRANSITIONING
    val duration = state.durationSec.coerceAtLeast(0)
    val progress = scrubDraft ?: state.progress
    val shownVolume = volDraft ?: (state.volume.toFloat() / 100f)
    val art = state.albumArtUrl?.takeIf { it.isNotBlank() }
        ?: queue.current?.albumArtUrl?.takeIf { it.isNotBlank() }
    val embeddedCover = remember(state.embeddedCover) {
        state.embeddedCover?.let {
            runCatching { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }.getOrNull()
        }
    }
    val title = state.title ?: queue.current?.title
    val artist = state.artist ?: queue.current?.artist

    // 松手才 seek：逐像素 seek 会让设备进度反复回弹
    LaunchedEffect(scrubDraft) {
        val d = scrubDraft ?: return@LaunchedEffect
        if (duration <= 0) return@LaunchedEffect
        vm.seekTo((d * duration).toLong())
    }
    // 换曲后重新读内嵌封面/歌词
    LaunchedEffect(queue.current?.url) {
        queue.current?.url?.let {
            vm.clearEmbeddedTags()
            vm.loadEmbeddedTags(it)
        }
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
                .padding(horizontal = Spacing.lg),
        ) {
            // ---------- 顶栏 ----------
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.xs, bottom = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onBack != null) {
                    CircleIcon(onClick = onBack) {
                        BackGlyph(extras.onBackdrop, Modifier.size(GlyphSize.medium))
                    }
                    Spacer(Modifier.width(Spacing.xs))
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        state.friendlyName ?: deviceIp,
                        style = MaterialTheme.typography.titleSmall,
                        color = extras.onBackdrop,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        buildString {
                            append(if (playing) "正在播放" else state.channel ?: "连接中")
                            state.sourceLabel?.let { append(" · ").append(it) }
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = extras.onBackdropMuted,
                        maxLines = 1,
                    )
                }
                CircleIcon(onClick = onOpenSettings) {
                    Text("⋯", style = MaterialTheme.typography.headlineSmall, color = extras.onBackdrop)
                }
            }

            // ---------- 封面（吃掉剩余空间，保持正方形）----------
            // 用 BoxWithConstraints 取「宽高较小者」当边长：
            // 直接 aspectRatio 在窄屏上会把封面撑出父容器（横向溢出、被裁掉一角）
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                val side = if (maxWidth < maxHeight) maxWidth else maxHeight
                Box(
                    modifier = Modifier
                        .size(side)
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
                    } else if (embeddedCover != null) {
                        Image(
                            bitmap = embeddedCover,
                            contentDescription = "内嵌封面",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Text(
                            text = (title ?: "?").take(1).uppercase(),
                            fontSize = 64.sp,
                            fontWeight = FontWeight.Black,
                            color = extras.onBackdrop.copy(alpha = 0.85f),
                        )
                    }
                }
            }

            // ---------- 曲名 / 歌手 ----------
            Spacer(Modifier.height(Spacing.sm))
            Text(
                text = title ?: "未在播放",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = extras.onBackdrop,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = artist
                    ?: if (state.state == TransportState.NO_MEDIA_PRESENT) "去媒体库点一首" else deviceIp,
                style = MaterialTheme.typography.bodySmall,
                color = extras.onBackdropMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            // ---------- 进度 ----------
            WaveProgressSlider(
                value = progress,
                onValueChange = { scrubDraft = it },
                onValueChangeFinished = { scrubDraft = null },
                modifier = Modifier.fillMaxWidth(),
                activeColor = extras.onBackdrop,
                inactiveColor = extras.onBackdropMuted.copy(alpha = 0.28f),
                thumbColor = palette.accent,
                animated = scrubDraft == null,
                height = 20.dp,
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

            // ---------- 主控：永远在屏幕内 ----------
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
                CircleIcon(onClick = vm::previous, size = 48.dp) {
                    SkipGlyph(extras.onBackdrop, forward = false, modifier = Modifier.size(28.dp))
                }
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(extras.onBackdrop)
                        .clickable { if (playing) vm.pause() else vm.play() },
                    contentAlignment = Alignment.Center,
                ) {
                    if (playing) PauseGlyph(palette.top, Modifier.size(28.dp))
                    else PlayGlyph(palette.top, Modifier.size(28.dp))
                }
                CircleIcon(onClick = vm::next, size = 48.dp) {
                    SkipGlyph(extras.onBackdrop, forward = true, modifier = Modifier.size(28.dp))
                }
                CircleIcon(onClick = vm::stop, size = 44.dp) {
                    Text("■", style = MaterialTheme.typography.titleSmall, color = extras.onBackdropMuted)
                }
            }

            // ---------- 音量（拖动跟手）----------
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircleIcon(onClick = vm::toggleMute, size = 32.dp) {
                    VolumeGlyph(
                        color = if (state.mute) extras.onBackdropMuted else extras.onBackdrop,
                        muted = state.mute,
                        modifier = Modifier.size(GlyphSize.small),
                    )
                }
                Spacer(Modifier.width(Spacing.xs))
                WaveProgressSlider(
                    value = shownVolume,
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
                    height = 18.dp,
                )
                Spacer(Modifier.width(Spacing.xs))
                Text(
                    if (state.mute) "静音" else state.volume.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = extras.onBackdropMuted,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(32.dp),
                )
            }

            (state.error ?: state.unavailableReason)?.let { msg ->
                Text(
                    msg,
                    style = MaterialTheme.typography.labelSmall,
                    color = StatusColors.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // ---------- 底部内容区：二选一，不是浮层 ----------
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Spacing.xs),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SegmentChip(
                    text = if (queue.items.isEmpty()) "播放列表" else "播放列表 ${queue.items.size}",
                    selected = !showAdvanced,
                    onClick = { showAdvanced = false },
                )
                SegmentChip(text = "投流与日志", selected = showAdvanced, onClick = { showAdvanced = true })
                Spacer(Modifier.weight(1f))
                if (queue.items.isEmpty() && !showAdvanced) {
                    Text(
                        "从媒体库点歌",
                        style = MaterialTheme.typography.labelSmall,
                        color = extras.onBackdropMuted,
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.85f)
                    .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                    .background(Color.White.copy(alpha = 0.06f)),
            ) {
                if (showAdvanced) {
                    CastPanel(
                        state = state,
                        vm = vm,
                        onOpenNas = onOpenNas,
                        onOpenLocalQueue = onOpenLocalQueue,
                    )
                } else if (queue.items.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(Spacing.md),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            "队列是空的",
                            style = MaterialTheme.typography.bodyMedium,
                            color = extras.onBackdrop,
                        )
                        Spacer(Modifier.height(Spacing.sm))
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            TextButton(onClick = onOpenNas) { Text("NAS 音乐") }
                            TextButton(onClick = onOpenLocalQueue) { Text("手机音乐") }
                            TextButton(onClick = onOpenLibrary) { Text("音乐库") }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = Spacing.md),
                    ) {
                        itemsIndexed(queue.items) { index, item ->
                            PlaylistRow(
                                index = index,
                                item = item,
                                playing = index == queue.index && queue.running,
                                current = index == queue.index,
                                onClick = { vm.playQueueItem(index) },
                                onRemove = { vm.removeQueueItem(index) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SegmentChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(AppShapesExpressive.pill)
            .background(
                if (selected) Color.White.copy(alpha = 0.20f) else Color.White.copy(alpha = 0.07f),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.md, vertical = 6.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = LocalExtraColors.current.onBackdrop,
        )
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
    val extras = LocalExtraColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = Spacing.md, end = Spacing.xs, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(26.dp), contentAlignment = Alignment.Center) {
            if (playing) {
                EqualizerBars(extras.onBackdrop)
            } else {
                Text(
                    "${index + 1}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (current) extras.onBackdrop else extras.onBackdropMuted,
                )
            }
        }
        Spacer(Modifier.width(Spacing.xs))
        Column(Modifier.weight(1f)) {
            Text(
                item.title,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal,
                color = if (current) extras.onBackdrop else extras.onBackdropMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            item.artist?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = extras.onBackdropMuted,
                    maxLines = 1,
                )
            }
        }
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            Text("✕", style = MaterialTheme.typography.labelSmall, color = extras.onBackdropMuted)
        }
    }
}

/** 正在播的小均衡器条。 */
@Composable
private fun EqualizerBars(color: Color) {
    Canvas(Modifier.size(width = 13.dp, height = 14.dp)) {
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

/** 投流与日志。占用底部内容区，不弹出新层。 */
@Composable
private fun CastPanel(
    state: PlaybackUiState,
    vm: PlaybackViewModel,
    onOpenNas: () -> Unit,
    onOpenLocalQueue: () -> Unit,
) {
    val extras = LocalExtraColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.md),
    ) {
        OutlinedTextField(
            value = state.castUrl,
            onValueChange = vm::setCastUrl,
            label = { Text("要投的音频地址") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "这条链接需要 Referer（自动转发）",
                style = MaterialTheme.typography.labelSmall,
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
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            TextButton(onClick = vm::cast, enabled = !state.busy) { Text("投流") }
            TextButton(onClick = onOpenNas) { Text("从 NAS 选") }
            TextButton(onClick = onOpenLocalQueue) { Text("手机音乐") }
        }
        if (state.lyrics != null) {
            Text(
                "歌词",
                style = MaterialTheme.typography.labelSmall,
                color = extras.onBackdrop,
            )
            Text(
                state.lyrics!!,
                style = MaterialTheme.typography.labelSmall,
                color = extras.onBackdropMuted,
                modifier = Modifier.heightIn(max = 200.dp),
            )
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
