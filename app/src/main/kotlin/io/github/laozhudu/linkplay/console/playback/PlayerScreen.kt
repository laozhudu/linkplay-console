package io.github.laozhudu.linkplay.console.playback

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
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
import io.github.laozhudu.linkplay.protocol.queue.QueueItem
import io.github.laozhudu.linkplay.protocol.upnp.TransportState

/**
 * 播放页 —— **单层、单滚动、所有控制都在这一页**。
 *
 * ## 上一版为什么还是「两层」，这版怎么改的
 *
 * 上一版把播放列表做成 `ModalBottomSheet`：点按钮 → 盖上半屏面板。那仍然是两层，
 * 而且面板背后还留着滚到一半的页面，返回要按两次。
 *
 * 现在整页只有 **一个 LazyColumn**，从上到下：
 *
 * ```
 * 顶栏（返回 / 设备名 / 设置）
 * 封面（圆形，播放时放大）
 * 曲名 / 艺术家
 * 波浪进度 + 时间
 * 主控：循环 · 上一首 · 播放暂停 · 下一首 · 停止
 * 音量条
 * ─── 播放列表（就在这一页里，直接点、直接删）───
 *   1 ▶  曲名            ✕
 *   2    曲名            ✕
 * ───
 * 高级：投流地址与日志（折叠）
 * ```
 *
 * 这个顺序对齐市面通用播放器（Spotify / Apple Music / 网易云 / Auxio 都是这个骨架）：
 * 上半屏「现在放什么」，往下滑就是「队列里还有什么」，中间没有任何跳转或弹层。
 *
 * ## 三个刻意的选择
 *
 * - **不用 `verticalScroll` + 内嵌列表**：那是两层滚动抢手势。整页一个 LazyColumn，
 *   歌多也不会出现「小窗里滑不动」。
 * - **主按钮用圆形图标而不放「播放」二字**：通用播放器的共识位置与形状，
 *   写成文字按钮反而让人以为是提交表单。
 * - **列表项不做拖拽排序**：设备侧是逐首投流，顺序只在控制端有意义，
 *   但没有实体反馈的拖拽很容易一滑就乱。删除点了立即生效（重新加一次的成本很低，
 *   所以不像「清空队列」那样要求二次确认）。
 */
@Composable
fun PlayerScreen(
    deviceIp: String,
    deviceLocation: String?,
    /**
     * 传 null 表示「本页是一级页面，靠底部导航切换」，不显示返回箭头。
     * 市面播放器（Spotify / Apple Music）的播放页就是这样：没有返回键，
     * 换页面靠底部 Tab —— 这也是「一层」的实质含义。
     */
    onBack: (() -> Unit)? = null,
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
    var showAdvanced by remember { mutableStateOf(false) }

    val playing = state.state == TransportState.PLAYING || state.state == TransportState.TRANSITIONING
    val duration = state.durationSec.coerceAtLeast(0)
    val progress = scrubbing ?: state.progress
    val shownVolume = volumeDraft ?: (state.volume.toFloat() / 100f)

    val artworkScale by animateFloatAsState(
        targetValue = if (playing) 1f else 0.88f,
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
                    listOf(if (playing) palette.top else extras.backdropTop, palette.bottom),
                ),
            ),
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars),
            contentPadding = PaddingValues(
                start = Spacing.xl,
                end = Spacing.xl,
                top = Spacing.sm,
                bottom = Spacing.xxl,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            // ---------- 顶栏 ----------
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (onBack != null) {
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
                    }
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
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onOpenSettings),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("⋯", style = MaterialTheme.typography.titleLarge, color = extras.onBackdrop)
                    }
                }
            }

            // ---------- 封面 ----------
            item {
                Spacer(Modifier.height(Spacing.sm))
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.66f)
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
                        // 有 albumArtURI 就显示真图，没有才退回首字占位。
                        // 投流后设备不回填元数据，所以封面只能来自「浏览时带进队列」的那个值。
                        val art = queue.current?.albumArtUrl?.takeIf { it.isNotBlank() }
                            ?: state.albumArtUrl?.takeIf { it.isNotBlank() }
                        if (art != null) {
                            coil.compose.AsyncImage(
                                model = art,
                                contentDescription = state.title,
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            ArtworkInitial(state.title, palette)
                        }
                    }
                }
            }

            // ---------- 曲名 ----------
            item {
                Column(Modifier.fillMaxWidth()) {
                    Text(
                        state.title ?: if (state.ready) "未在播放" else "准备中…",
                        style = MaterialTheme.typography.headlineMedium,
                        color = extras.onBackdrop,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        state.artist?.takeIf { it.isNotBlank() } ?: "—",
                        style = MaterialTheme.typography.labelMedium,
                        color = extras.onBackdropMuted,
                        maxLines = 1,
                    )
                }
            }

            // ---------- 进度 ----------
            item {
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
                        PlaybackViewModel.format(
                            if (scrubbing != null) (progress * duration).toLong() else state.positionSec,
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = extras.onBackdropMuted,
                    )
                    Text(
                        if (duration > 0) PlaybackViewModel.format(duration) else "直播 / 未知长度",
                        style = MaterialTheme.typography.labelMedium,
                        color = extras.onBackdropMuted,
                    )
                }
            }

            // ---------- 主控 ----------
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    // 循环（含随机）：点一下切一档
                    GlyphTarget(onClick = vm::cycleLoopMode) {
                        LoopGlyph(
                            color = if (state.loopMode == LoopMode.OFF) extras.onBackdropMuted else palette.accent,
                            mode = state.loopMode.wire,
                            modifier = Modifier.size(GlyphSize.medium),
                        )
                    }
                    GlyphTarget(onClick = vm::previous) {
                        SkipGlyph(extras.onBackdrop, forward = false, modifier = Modifier.size(GlyphSize.large))
                    }
                    // 主按钮：圆形大键，通用播放器里这个位置就是它
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .background(palette.accent)
                            .clickable(enabled = state.ready) {
                                if (playing) vm.pause() else vm.play()
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (playing) {
                            PauseGlyph(palette.onAccent, Modifier.size(GlyphSize.hero))
                        } else {
                            PlayGlyph(palette.onAccent, Modifier.size(GlyphSize.hero))
                        }
                    }
                    GlyphTarget(onClick = vm::next) {
                        SkipGlyph(extras.onBackdrop, forward = true, modifier = Modifier.size(GlyphSize.large))
                    }
                    GlyphTarget(onClick = vm::stop) {
                        StopGlyph(extras.onBackdrop, modifier = Modifier.size(GlyphSize.medium))
                    }
                }
            }

            // ---------- 音量 ----------
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
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
                }
            }

            (state.error ?: state.unavailableReason)?.let { msg ->
                item {
                    Text(
                        msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            // ---------- 播放列表：同一页，不是弹层 ----------
            item {
                Spacer(Modifier.height(Spacing.md))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "播放列表",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = extras.onBackdrop,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        if (queue.items.isEmpty()) "空" else "${queue.total} 首",
                        style = MaterialTheme.typography.labelMedium,
                        color = extras.onBackdropMuted,
                    )
                }
            }

            if (queue.items.isEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        PillAction("从 NAS 添加", onOpenNas, Modifier.weight(1f))
                        PillAction("本地音乐", onOpenLocalQueue, Modifier.weight(1f))
                    }
                }
            } else {
                itemsIndexed(queue.items) { index, item ->
                    PlaylistRow(
                        index = index,
                        item = item,
                        isCurrent = index == queue.index,
                        onClick = { QueueBus.skipTo(index) },
                        onRemove = { QueueBus.removeAt(index) },
                    )
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        PillAction("＋ 加更多", onOpenNas, Modifier.weight(1f))
                        PillAction("音乐库", onOpenLibrary, Modifier.weight(1f))
                    }
                }
            }

            // ---------- 高级（折叠，仍在同一页）----------
            item {
                Spacer(Modifier.height(Spacing.sm))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(AppShapesExpressive.pill)
                        .clickable { showAdvanced = !showAdvanced }
                        .padding(vertical = Spacing.sm, horizontal = Spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (showAdvanced) "收起" else "高级：投流地址与设备日志",
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
            }

            if (showAdvanced) {
                item {
                    Column {
                        Text(
                            "投一条直链给音箱（音箱自己去拉）：",
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
                                "需要 Referer（自动本机转发）",
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
            }
        }
    }
}

/** 封面中央的首字：没有真实封面时的占位，冷启动也不闪空白。 */
@Composable
private fun ArtworkInitial(title: String?, palette: SeedPalette) {
    val initial = title?.trim()?.firstOrNull()?.toString()
    Text(
        text = if (initial != null) initial.uppercase() else "♪",
        fontSize = 92.sp,
        lineHeight = 92.sp,
        color = palette.accent.copy(alpha = 0.9f),
        fontWeight = FontWeight.Bold,
    )
}

/**
 * 播放列表的一行：点整行跳播，右侧 ✕ 删除。
 *
 * 删除不做二次确认 —— 代价只是重新加一次；与「清空整个队列」「停止共享」那类
 * 不可撤销动作不是一回事。
 */
@Composable
private fun PlaylistRow(
    index: Int,
    item: QueueItem,
    isCurrent: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    val extras = LocalExtraColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppShapesExpressive.squircle)
            .background(
                if (isCurrent) extras.onBackdrop.copy(alpha = 0.12f) else Color.Transparent,
            )
            .clickable(onClick = onClick)
            .padding(start = Spacing.md, end = Spacing.xs, top = Spacing.sm, bottom = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (isCurrent) "▶" else "${index + 1}",
            style = MaterialTheme.typography.labelMedium,
            color = if (isCurrent) extras.onBackdrop else extras.onBackdropMuted,
            modifier = Modifier.width(26.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                item.title,
                style = MaterialTheme.typography.titleMedium,
                color = extras.onBackdrop,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            item.artist?.takeIf { it.isNotBlank() }?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelMedium,
                    color = extras.onBackdropMuted,
                    maxLines = 1,
                )
            }
        }
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            Text("✕", style = MaterialTheme.typography.titleMedium, color = extras.onBackdropMuted)
        }
    }
}

/** 48dp 触控区的图标按钮。 */
@Composable
private fun GlyphTarget(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** 胶囊动作按钮（添加入口）。 */
@Composable
private fun PillAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val extras = LocalExtraColors.current
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(AppShapesExpressive.pill)
            .background(extras.onBackdrop.copy(alpha = 0.10f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = extras.onBackdrop, maxLines = 1)
    }
}
