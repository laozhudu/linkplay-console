package io.github.laozhudu.linkplay.console.library

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.laozhudu.linkplay.protocol.playqueue.QueueLoopMode

/**
 * 本地音乐整列表推送页。
 *
 * 设计取舍：这里只做「选曲 → 起服务 → 推整队列 → 设循环」，
 * 不做播放进度/暂停（那是设备详情页播放卡片的事），避免两处状态互相打架。
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

    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) vm.loadTracks() else Unit }

    LaunchedEffect(Unit) {
        launcher.launch(permission)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Text("本地音乐 · 整列表推送", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            "把手机里的歌推成设备上的一个播放队列（原厂 App 的做法：手机当文件服务器，音箱自己拉流）。",
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(8.dp))

        state.error?.let {
            Text("· $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(6.dp))
        }

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.padding(12.dp)) {
                Text("设备：${state.deviceName ?: deviceIp}", style = MaterialTheme.typography.bodySmall)
                when (state.queueSupported) {
                    null -> Text("正在读取设备能力…", style = MaterialTheme.typography.bodySmall)
                    true -> Text("✓ 支持私有 PlayQueue（可整列表）", style = MaterialTheme.typography.bodySmall)
                    false -> Text(
                        "✗ 设备未声明 PlayQueue 服务，整列表推送不可用（可回到播放卡片单曲投流）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                state.serverUrl?.let {
                    Text("本机媒体服务：$it", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = vm::loadTracks) { Text("重新扫描") }
            TextButton(onClick = vm::selectAll) { Text("全选") }
            TextButton(onClick = vm::clearSelection) { Text("清空") }
            Text(
                "已选 ${state.selected.size}/${state.tracks.size}",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Spacer(Modifier.height(6.dp))
        Text("循环模式", style = MaterialTheme.typography.labelSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                QueueLoopMode.ALL to "列表循环",
                QueueLoopMode.SINGLE to "单曲",
                QueueLoopMode.SHUFFLE to "随机",
                QueueLoopMode.NONE to "不循环",
            ).forEach { (mode, label) ->
                val active = state.loopMode == mode
                if (active) {
                    Button(onClick = { vm.setLoopMode(mode) }) { Text(label) }
                } else {
                    OutlinedButton(onClick = { vm.setLoopMode(mode) }) { Text(label) }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Button(
            onClick = vm::pushSelected,
            enabled = !state.busy && state.selected.isNotEmpty() && state.queueSupported == true,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state.busy) {
                CircularProgressIndicator(Modifier.width(16.dp).height(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }
            Text(if (state.pushedCount > 0) "重推整个列表（${state.selected.size} 首）" else "推送整个列表")
        }

        Spacer(Modifier.height(8.dp))
        if (state.loading) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.width(14.dp).height(14.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("正在扫描本地音乐…", style = MaterialTheme.typography.bodySmall)
            }
        } else {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                    items(state.tracks, key = { it.id }) { track ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = track.id in state.selected,
                                onCheckedChange = { vm.toggle(track.id) },
                            )
                            Column(Modifier.weight(1f)) {
                                Text(track.title, style = MaterialTheme.typography.bodySmall)
                                Text(
                                    "${track.artist ?: "未知艺术家"} · ${formatDuration(track.durationSec)}",
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                    }
                }
            }
        }

        state.log.asReversed().take(6).forEach {
            Text(it, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
        }

        Spacer(Modifier.height(12.dp))
        TextButton(onClick = { vm.stopServer(); onBack() }, modifier = Modifier.fillMaxWidth()) {
            Text("停止共享并返回")
        }
        Spacer(Modifier.height(24.dp))
    }
}

private fun formatDuration(seconds: Long): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%d:%02d".format(m, s)
}
