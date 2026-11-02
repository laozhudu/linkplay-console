package io.github.laozhudu.linkplay.console.playback

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.laozhudu.linkplay.protocol.upnp.TransportState

/**
 * 设备详情页里的播放控制卡片。
 *
 * 为什么不在这里放一个「本地文件播放」：音箱是渲染器，它自己去拉 URL；
 * 想让手机本地文件出声需要先起一个 HTTP 服务再投 `http://手机IP:端口/xxx`，
 * 那是 Media3 + 本地服务的事，不在这一版范围。
 */
@Composable
fun PlaybackCard(vm: PlaybackViewModel, modifier: Modifier = Modifier) {
    val state by vm.state.collectAsState()

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                "播放控制" + (state.channel?.let { "（$it）" } ?: ""),
                fontWeight = FontWeight.Medium,
            )

            if (state.preparing) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.width(14.dp).height(14.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("正在读取设备描述…", style = MaterialTheme.typography.bodySmall)
                }
                return@Column
            }

            state.unavailableReason?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                return@Column
            }

            Spacer(Modifier.height(6.dp))
            state.friendlyName?.let {
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
            Text(
                text = "状态 " + stateLabel(state.state) +
                    (state.sourceLabel?.let { " · $it" } ?: "") +
                    if (state.durationSec > 0) {
                        " · ${PlaybackViewModel.format(state.positionSec)} / ${PlaybackViewModel.format(state.durationSec)}"
                    } else "",
                style = MaterialTheme.typography.bodySmall,
            )
            state.title?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            }
            state.artist?.let { Text(it, style = MaterialTheme.typography.bodySmall) }

            if (state.durationSec > 0) {
                Spacer(Modifier.height(6.dp))
                Slider(
                    value = state.progress,
                    onValueChange = { frac ->
                        val target = (frac * state.durationSec).toLong()
                        vm.seekTo(target)
                    },
                )
            }

            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.refresh() }, enabled = !state.busy) { Text("刷新") }
                Button(onClick = { vm.play() }, enabled = !state.busy && state.state != TransportState.PLAYING) {
                    Text("播放")
                }
                Button(onClick = { vm.pause() }, enabled = !state.busy) { Text("暂停") }
                OutlinedButton(onClick = { vm.stop() }, enabled = !state.busy) { Text("停止") }
            }

            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("音量", style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(40.dp))
                Slider(
                    value = state.volume.toFloat(),
                    valueRange = 0f..100f,
                    onValueChange = { vm.setVolume(it.toInt()) },
                    enabled = !state.busy,
                    modifier = Modifier.weight(1f),
                )
                Text("${state.volume}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(34.dp))
                TextButton(onClick = { vm.toggleMute() }) { Text(if (state.mute) "取消静音" else "静音") }
            }

            Spacer(Modifier.height(8.dp))
            Text("投流：填一条可直连的音频 URL，音箱自己去拉", style = MaterialTheme.typography.labelSmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = state.castUrl,
                    onValueChange = vm::setCastUrl,
                    singleLine = true,
                    placeholder = { Text("http://…/song.mp3") },
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Button(onClick = { vm.cast() }, enabled = !state.busy) { Text("投给音箱") }
            }

            state.error?.let {
                Spacer(Modifier.height(6.dp))
                Text("· $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            if (state.log.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                state.log.asReversed().take(4).forEach {
                    Text(it, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

private fun stateLabel(state: TransportState): String = when (state) {
    TransportState.PLAYING -> "播放中"
    TransportState.PAUSED_PLAYBACK -> "已暂停"
    TransportState.PAUSED_RECORDING -> "录制暂停"
    TransportState.STOPPED -> "已停止"
    TransportState.NO_MEDIA_PRESENT -> "无媒体"
    TransportState.TRANSITIONING -> "切换中"
    TransportState.RECORDING -> "录制中"
    TransportState.UNKNOWN -> "未知"
}
