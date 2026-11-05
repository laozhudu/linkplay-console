package io.github.laozhudu.linkplay.console.home

import io.github.laozhudu.linkplay.console.playback.PlaybackOutput
import io.github.laozhudu.linkplay.console.playback.PlaybackRouter
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.laozhudu.linkplay.console.playback.QueueBus
import io.github.laozhudu.linkplay.console.playback.QueueRunState
import io.github.laozhudu.linkplay.console.ui.components.AppShapesExpressive
import io.github.laozhudu.linkplay.console.ui.components.CircleIcon
import io.github.laozhudu.linkplay.console.ui.player.PlayerUiState
import io.github.laozhudu.linkplay.console.ui.theme.Spacing

/**
 * 迷你播放条：显示「在放什么」+ 播放/暂停 + 点开。
 *
 * ## 为什么现在加播放/暂停
 *
 * 原来这里刻意不放控制（"所有控制都在播放页一面"）。但用户从首页按「全部播放」之后
 * 想停一下，必须先进播放页 —— 这条路径太绕。
 *
 * 状态只有一个来源：`QueueBus` / `DeviceCard.playing`，迷你条只读不存，
 * 所以不会出现"两处各记一份、迟早不一致"的问题。
 */
@Composable
internal fun MiniPlayerBar(
    queue: QueueRunState,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    /** 当前是否在放（来自设备回报，不本地记）。 */
    playing: Boolean = false,
    onToggle: () -> Unit = {},
) {
    // 界面只认 PlayerUiState：换输出时换掉的是这里的**来源**，
    // 调用方和这个组件的其余部分都不用动（这一层当初就是为此建的）。
    //
    // 之前本机播放时这条还显示音箱的曲名 —— 因为这里只读 QueueBus。
    val barContext = androidx.compose.ui.platform.LocalContext.current
    val routerRef = androidx.compose.runtime.remember { PlaybackRouter.of(barContext) }
    val localUi by routerRef.localState.collectAsState()
    val isLocalOutput = routerRef.output() is PlaybackOutput.Local
    val ui = if (isLocalOutput) {
        localUi
    } else {
        PlayerUiState.fromRemote(
            nowPlaying = queue.nowPlaying,
            running = queue.total > 0,
            playing = playing,
        )
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(AppShapesExpressive.squircle)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onOpen)
            .padding(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                queue.nowPlaying?.trim()?.firstOrNull()?.toString()?.uppercase() ?: "♪",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.width(Spacing.sm))
        Column(Modifier.weight(1f)) {
            Text(
                ui.title ?: "未在播放",
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                when {
                    // 本机播放没有"第几首"的概念（本地播放器目前只播单首）
                    isLocalOutput && ui.hasContent && ui.durationMs > 0 ->
                        "${clock(ui.positionMs)} / ${clock(ui.durationMs)} · 点开操作"
                    isLocalOutput && ui.hasContent -> "本机播放 · 点开操作"
                    queue.index >= 0 && queue.total > 0 -> "第 ${queue.index + 1}/${queue.total} 首 · 点开操作"
                    else -> "点开继续操作"
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // 播放/暂停：与首页「全部播放」配套，不用为了停一下再进播放页
        CircleIcon(onClick = onToggle, size = 40.dp) {
            if (ui.playing) {
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Box(Modifier.size(4.dp, 14.dp).background(MaterialTheme.colorScheme.onSurface))
                    Box(Modifier.size(4.dp, 14.dp).background(MaterialTheme.colorScheme.onSurface))
                }
            } else {
                Text("▶", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            }
        }
        Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 毫秒 → `m:ss`。只给迷你条显示进度用，不做"小时"那一档（本机播放没有那么长的音频）。 */
private fun clock(ms: Long): String {
    val total = (ms / 1000L).coerceAtLeast(0L)
    return "${total / 60}:${(total % 60).toString().padStart(2, '0')}"
}
