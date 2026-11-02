package io.github.laozhudu.linkplay.console.playback

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.laozhudu.linkplay.console.ui.components.GlyphSize
import io.github.laozhudu.linkplay.console.ui.components.PauseGlyph
import io.github.laozhudu.linkplay.console.ui.components.PlayGlyph
import io.github.laozhudu.linkplay.console.ui.theme.Spacing
import io.github.laozhudu.linkplay.protocol.upnp.TransportState

/**
 * 详情页里的播放摘要行。
 *
 * 为什么不再放整套控制：同一套控制同时存在于「详情页卡片」和「全屏播放页」时，
 * 任何一次改动都只改一处，另一处就成了旧样子——这类界面漂移比功能缺失更难发现。
 * 所以详情页只保留「在放什么 + 一键进播放页」，控制集中在 [PlayerScreen]。
 */
@Composable
fun PlaybackSummaryRow(
    vm: PlaybackViewModel,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by vm.state.collectAsState()
    val playing = state.state == TransportState.PLAYING || state.state == TransportState.TRANSITIONING

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen)
                .padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier.size(GlyphSize.large),
                contentAlignment = Alignment.Center,
            ) {
                if (playing) {
                    PauseGlyph(MaterialTheme.colorScheme.onSurfaceVariant, Modifier.size(GlyphSize.medium))
                } else {
                    PlayGlyph(MaterialTheme.colorScheme.onSurfaceVariant, Modifier.size(GlyphSize.medium))
                }
            }
            Spacer(Modifier.width(Spacing.sm))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
            ) {
                Text(
                    state.title ?: if (state.ready) "未在播放" else "准备中…",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
                Text(
                    listOfNotNull(
                        state.artist,
                        state.channel,
                        if (state.volume >= 0) "音量 ${state.volume}" else null,
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Text("打开", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}
