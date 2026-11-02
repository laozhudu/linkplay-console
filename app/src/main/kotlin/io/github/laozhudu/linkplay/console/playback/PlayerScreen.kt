package io.github.laozhudu.linkplay.console.playback

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.laozhudu.linkplay.console.ui.components.BackGlyph
import io.github.laozhudu.linkplay.console.ui.components.ChevronGlyph
import io.github.laozhudu.linkplay.console.ui.components.GlyphSize
import io.github.laozhudu.linkplay.console.ui.components.LoopGlyph
import io.github.laozhudu.linkplay.console.ui.components.PauseGlyph
import io.github.laozhudu.linkplay.console.ui.components.PlayGlyph
import io.github.laozhudu.linkplay.console.ui.components.SkipGlyph
import io.github.laozhudu.linkplay.console.ui.components.StopGlyph
import io.github.laozhudu.linkplay.console.ui.components.VolumeGlyph
import io.github.laozhudu.linkplay.console.ui.theme.LocalExtraColors
import io.github.laozhudu.linkplay.console.ui.theme.SeedPalette
import io.github.laozhudu.linkplay.console.ui.theme.Spacing
import io.github.laozhudu.linkplay.console.ui.theme.seedOf
import io.github.laozhudu.linkplay.protocol.player.LoopMode
import io.github.laozhudu.linkplay.protocol.upnp.TransportState

/**
 * 播放页（遥控器主体）。
 *
 * 设计取向：**封面主导 + 渐变背景 + 浮动主控**。理由不是「好看」而已：
 * - 遥控场景下用户离手机远、只看一眼，最需要的是「这是哪首歌、在不在播、多大音量」；
 *   所以曲名/状态/音量占视觉重心，次要信息（通道、设备）降为小字。
 * - 背景色由曲名派生（见 `seedOf`），同一首歌永远同一套配色，不会每进一次就换个颜色。
 * - 主控按钮 76dp 且沉在中间偏下，拇指自然落点。
 *
 * 交互取舍：
 * - 进度条与音量条都**拖动时只改本地状态，松手才下发**。设备侧 seek 会打断音频缓冲，
 *   连续发几十条会让曲子一直卡。
 * - 循环模式点一下切一档，本地先变再下发，不等回读（设备回读有 1 秒延迟，等了就像「点了没反应」）。
 */
@Composable
fun PlayerScreen(
    deviceIp: String,
    deviceLocation: String?,
    onBack: () -> Unit,
) {
    val vm: PlaybackViewModel = viewModel(
        key = "player-$deviceIp",
        factory = viewModelFactory { initializer { PlaybackViewModel(deviceIp, deviceLocation) } },
    )
    val state by vm.state.collectAsState()

    val palette = remember(state.title, state.artist) { seedOf(state.title, state.artist) }
    val extras = LocalExtraColors.current

    // 拖动中的本地值：松手才下发，避免连续 seek/改音量打断播放
    var scrubbing by remember { mutableStateOf<Float?>(null) }
    var volumeDraft by remember { mutableStateOf<Float?>(null) }
    var showDetails by remember { mutableStateOf(false) }

    val playing = state.state == TransportState.PLAYING || state.state == TransportState.TRANSITIONING
    val duration = state.durationSec.coerceAtLeast(0)
    val shownProgress = scrubbing ?: state.progress
    val shownVolume = volumeDraft ?: (state.volume.toFloat() / 100f)

    Surface(color = Color.Transparent) {
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
            ) {
                // ---- 顶栏：返回 + 设备名 + 通道徽标 ----
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
                }

                Spacer(Modifier.height(Spacing.xl))

                // ---- 封面占位（曲名首字 + 派生渐变）----
                ArtworkTile(
                    title = state.title,
                    palette = palette,
                    playing = playing,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(MaterialTheme.shapes.extraLarge),
                )

                Spacer(Modifier.height(Spacing.xl))

                Text(
                    state.title ?: if (state.ready) "未在播放" else "准备中…",
                    style = MaterialTheme.typography.headlineMedium,
                    color = extras.onBackdrop,
                    maxLines = 2,
                    textAlign = TextAlign.Start,
                )
                state.artist?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = extras.onBackdropMuted)
                }

                Spacer(Modifier.height(Spacing.lg))

                // ---- 进度 ----
                Slider(
                    value = shownProgress,
                    onValueChange = { scrubbing = it },
                    onValueChangeFinished = {
                        val target = ((scrubbing ?: 0f) * duration).toLong()
                        scrubbing = null
                        if (duration > 0) vm.seekTo(target)
                    },
                    enabled = duration > 0,
                    colors = SliderDefaults.colors(
                        thumbColor = palette.accent,
                        activeTrackColor = palette.accent,
                        inactiveTrackColor = extras.onBackdropMuted.copy(alpha = 0.28f),
                    ),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        PlaybackViewModel.format(if (scrubbing != null) (shownProgress * duration).toLong() else state.positionSec),
                        style = MaterialTheme.typography.labelMedium,
                        color = extras.onBackdropMuted,
                    )
                    Text(
                        if (duration > 0) PlaybackViewModel.format(duration) else "直播 / 未知长度",
                        style = MaterialTheme.typography.labelMedium,
                        color = extras.onBackdropMuted,
                    )
                }

                Spacer(Modifier.height(Spacing.md))

                // ---- 主控 ----
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    GlyphButton(onClick = vm::cycleLoopMode) {
                        LoopGlyph(
                            color = if (state.loopMode == LoopMode.OFF) extras.onBackdropMuted else palette.accent,
                            mode = state.loopMode.wire,
                            modifier = Modifier.size(GlyphSize.medium),
                        )
                    }
                    GlyphButton(onClick = vm::previous) {
                        SkipGlyph(extras.onBackdrop, forward = false, modifier = Modifier.size(GlyphSize.large))
                    }
                    // 主按钮：56dp 的圆形底 + 32dp 图标，视觉重量明显高于两侧
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
                    GlyphButton(onClick = vm::next) {
                        SkipGlyph(extras.onBackdrop, forward = true, modifier = Modifier.size(GlyphSize.large))
                    }
                    GlyphButton(onClick = vm::stop) {
                        StopGlyph(extras.onBackdrop, modifier = Modifier.size(GlyphSize.medium))
                    }
                }

                Spacer(Modifier.height(Spacing.lg))

                // ---- 音量：独立一行，滑块 + 数值，拖动松手才下发 ----
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
                    Slider(
                        value = shownVolume,
                        onValueChange = { volumeDraft = it },
                        onValueChangeFinished = {
                            val v = ((volumeDraft ?: shownVolume) * 100).toInt().coerceIn(0, 100)
                            volumeDraft = null
                            vm.setVolume(v)
                        },
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = extras.onBackdrop,
                            activeTrackColor = extras.onBackdrop,
                            inactiveTrackColor = extras.onBackdropMuted.copy(alpha = 0.28f),
                        ),
                    )
                    Spacer(Modifier.width(Spacing.sm))
                    Text(
                        if (state.mute) "静音" else "${state.volume}",
                        style = MaterialTheme.typography.labelMedium,
                        color = extras.onBackdropMuted,
                        modifier = Modifier.width(32.dp),
                    )
                }

                // ---- 错误 / 不可用原因 ----
                (state.error ?: state.unavailableReason)?.let {
                    Spacer(Modifier.height(Spacing.sm))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }

                Spacer(Modifier.height(Spacing.lg))

                // ---- 折叠区：投流 URL + 设备细节。默认收起，主屏只留控制 ----
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .clickable { showDetails = !showDetails }
                        .padding(vertical = Spacing.sm),
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
                    Column {
                        Text(
                            "投流：填一条能直连的音频 URL，音箱自己去拉",
                            style = MaterialTheme.typography.bodySmall,
                            color = extras.onBackdropMuted,
                        )
                        Spacer(Modifier.height(Spacing.xs))
                        androidx.compose.material3.OutlinedTextField(
                            value = state.castUrl,
                            onValueChange = vm::setCastUrl,
                            placeholder = { Text("http://…/song.mp3") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(Spacing.xs))
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            androidx.compose.material3.Button(onClick = vm::cast, enabled = !state.busy) {
                                Text("投给音箱")
                            }
                            androidx.compose.material3.OutlinedButton(onClick = vm::refresh, enabled = !state.busy) {
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
        }
    }
}

/** 封面：没有真实图片时用「派生渐变 + 曲名首字」，视觉统一且不需要网络。 */
@Composable
private fun ArtworkTile(
    title: String?,
    palette: SeedPalette,
    playing: Boolean,
    modifier: Modifier = Modifier,
) {
    // 暂停时封面下沉 8dp，播放时回到位：一个极轻的动作就足以表达「在播放」
    val lift by animateDpAsState(
        targetValue = if (playing) 0.dp else 8.dp,
        animationSpec = spring(),
        label = "artwork-lift",
    )
    Box(
        modifier = modifier
            .padding(top = lift)
            .background(
                Brush.linearGradient(
                    listOf(
                        palette.top,
                        palette.accent.copy(alpha = 0.55f),
                        palette.top,
                    ),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        val initial = title?.trim()?.firstOrNull()?.toString()
        if (initial != null) {
            Text(
                text = initial.uppercase(),
                fontSize = 96.sp,
                lineHeight = 96.sp,
                color = palette.accent.copy(alpha = 0.85f),
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** 滑块之外的图标按钮：固定 48dp 触控区，避免手指点不中。 */
@Composable
private fun GlyphButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}
