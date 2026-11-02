package io.github.laozhudu.linkplay.console.nas

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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.laozhudu.linkplay.protocol.queue.ClientSideQueue

/**
 * NAS 浏览 + 列表推送页。
 *
 * 交互：发现/连接服务器 → 逐层浏览目录 → 勾选曲目「加入队列」（可跨目录累积）→ 推送。
 * 之所以用「加入队列」而不是直接推，是因为本固件不支持音箱侧排队（docs/08 §5.2），
 * 需要先在客户端攒好列表再逐首推。
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Text("NAS 媒体库（DLNA）", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            "通过 ContentDirectory 浏览局域网里的 DLNA 服务器，选中曲目后推给音箱。",
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(8.dp))

        state.error?.let {
            Text("· $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = vm::clearError) { Text("知道了") }
        }

        // ---- 连接区 ----
        if (state.connectedServer == null) {
            // 上次连过的 NAS：一步回到上次的库和目录，省掉重复搜索
            state.rememberedServer?.let { server ->
                Card(
                    onClick = vm::resumeRemembered,
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                ) {
                    Column(Modifier.padding(10.dp)) {
                        Text(
                            "上次：${server.friendlyName ?: server.label}",
                            fontWeight = FontWeight.Medium,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            if (state.rememberedPath.isEmpty()) server.label + " · 根目录"
                            else server.label + " · " + state.rememberedPath.joinToString(" / ") { it.title },
                            style = MaterialTheme.typography.labelSmall,
                        )
                        Text("点一下直接回去", style = MaterialTheme.typography.labelSmall)
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
            Button(
                onClick = vm::scan,
                enabled = !state.scanning,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.scanning) {
                    CircularProgressIndicator(Modifier.width(14.dp).height(14.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (state.scanning) "正在搜索…" else "搜索 DLNA 服务器")
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = state.manualHost,
                    onValueChange = vm::setManualHost,
                    label = { Text("手动：NAS地址（如 192.168.99.223:8200）") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Button(onClick = vm::connectManual) { Text("连") }
            }
            Spacer(Modifier.height(8.dp))
            state.servers.forEach { server ->
                Card(
                    onClick = { vm.connect(server) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Column(Modifier.padding(10.dp)) {
                        Text(server.ip, fontWeight = FontWeight.Medium)
                        Text(server.server ?: "", style = MaterialTheme.typography.labelSmall)
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
            TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("返回") }
            return@Column
        }

        // ---- 已连接：路径 + 目录内容 ----
        Text(
            "服务器：${state.connectedServer}",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
        )
        Text(
            "路径：" + state.path.joinToString(" / ") { it.title },
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
        )
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick = vm::up, enabled = state.path.size > 1) { Text("上一级") }
            TextButton(onClick = vm::selectAllPlayable) { Text("全选可播") }
            TextButton(onClick = vm::clearSelection) { Text("清空选择") }
            Text("已选 ${state.selected.size}", style = MaterialTheme.typography.labelSmall)
        }

        Spacer(Modifier.height(6.dp))
        if (state.loadingDir) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.width(14.dp).height(14.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("读取目录…", style = MaterialTheme.typography.bodySmall)
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                items(state.entries, key = { it.id }) { entry ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (entry.isContainer) {
                            TextButton(onClick = { vm.openDir(entry.id, entry.title) }) {
                                Text("📁 ${entry.title}")
                            }
                        } else {
                            Checkbox(
                                checked = entry.id in state.selected,
                                onCheckedChange = { vm.toggleEntry(entry) },
                            )
                            Column(Modifier.weight(1f)) {
                                Text(entry.title, style = MaterialTheme.typography.bodySmall)
                                Text(
                                    "${entry.artist ?: ""} ${NasViewModel.formatDuration(entry.durationSec)}",
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Button(onClick = vm::addSelectedToQueue, modifier = Modifier.fillMaxWidth()) {
            Text("加入队列（已选 ${state.selected.size}）")
        }

        // ---- 队列区 ----
        Spacer(Modifier.height(10.dp))
        Text(
            "待播队列：${state.picked.size} 首" + (state.nowPlaying?.let { " · 正在播：$it" } ?: ""),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf(
                ClientSideQueue.LOOP_ALL to "列表循环",
                ClientSideQueue.LOOP_SINGLE to "单曲",
                ClientSideQueue.LOOP_NONE to "顺序",
            ).forEach { (mode, label) ->
                if (state.loopMode == mode) Button(onClick = { vm.setLoopMode(mode) }) { Text(label) }
                else OutlinedButton(onClick = { vm.setLoopMode(mode) }) { Text(label) }
            }
        }
        Spacer(Modifier.height(6.dp))
        // 上次没播完的队列：锁屏被系统杀掉、或切走太久丢了进度时，从断点接着播
        state.resumable?.let { saved ->
            if (!state.playing) {
                Card(
                    onClick = vm::resumePlayback,
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                ) {
                    Column(Modifier.padding(10.dp)) {
                        Text("继续上次播放", fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodySmall)
                        Text(
                            "从第 ${saved.index + 1} 首开始，剩余 ${saved.remaining.size} 首：" +
                                (saved.remaining.firstOrNull()?.title ?: ""),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = vm::startQueue,
                enabled = !state.playing && state.picked.isNotEmpty(),
                modifier = Modifier.weight(1f),
            ) { Text(if (state.playing) "推送中（已播 ${state.playedCount}）" else "推送给音箱") }
            OutlinedButton(onClick = vm::stopQueue, enabled = state.playing) { Text("停止") }
            TextButton(onClick = vm::clearQueue) { Text("清空") }
        }

        // 控制面掉线时的自救入口：设备在线但 UPnP 控制口关了，重启才能恢复
        if (state.controlLost) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
            ) {
                Column(Modifier.padding(10.dp)) {
                    Text(
                        "音箱控制服务掉线（设备仍在线）",
                        fontWeight = FontWeight.Medium,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        "UPnP 端口 49152 关了。重启音箱可恢复，实测约 40 秒。",
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Spacer(Modifier.height(6.dp))
                    Button(onClick = vm::restartRenderer, enabled = !state.healing) {
                        if (state.healing) {
                            CircularProgressIndicator(Modifier.width(14.dp).height(14.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(if (state.healing) "重启并等待恢复…" else "重启音箱")
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
        }

        state.log.asReversed().take(6).forEach {
            Text(it, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
        }

        Spacer(Modifier.height(10.dp))
        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("返回") }
        Spacer(Modifier.height(24.dp))
    }
}
