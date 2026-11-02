package io.github.laozhudu.linkplay.console

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.laozhudu.linkplay.console.playback.PlaybackCard
import io.github.laozhudu.linkplay.console.playback.PlaybackViewModel
import io.github.laozhudu.linkplay.console.provisioning.ProvisioningScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                ConsoleApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConsoleApp(vm: ConsoleViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    var showProvisioning by remember { mutableStateOf(false) }

    if (showProvisioning) {
        Scaffold(
            topBar = { TopAppBar(title = { Text("添加音箱（AP 直连配网）", fontWeight = FontWeight.SemiBold) }) },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
            ) {
                ProvisioningScreen(
                    onDone = { showProvisioning = false },
                    onBack = { showProvisioning = false },
                )
            }
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(title = {
                Text(
                    text = if (state.selectedIp == null) {
                        "LinkPlay Console"
                    } else {
                        "设备 ${state.selectedIp}"
                    },
                    fontWeight = FontWeight.SemiBold,
                )
            })
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            state.error?.let {
                Text(
                    text = "错误：$it",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(8.dp))
            }
            if (state.selectedIp == null) {
                DeviceListScreen(
                    state = state,
                    onScan = vm::scan,
                    onAdd = vm::addManualDevice,
                    onSelect = vm::select,
                    onProvision = { showProvisioning = true },
                )
            } else {
                DeviceDetailScreen(state = state, vm = vm)
            }
        }
    }
}

@Composable
private fun DeviceListScreen(
    state: ConsoleUiState,
    onScan: () -> Unit,
    onAdd: (String) -> Unit,
    onSelect: (String) -> Unit,
    onProvision: () -> Unit,
) {
    var manualIp by remember { mutableStateOf("") }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = manualIp,
            onValueChange = { manualIp = it },
            label = { Text("手动输入设备 IP") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Button(onClick = { onAdd(manualIp); manualIp = "" }) { Text("添加") }
    }
    Spacer(Modifier.height(12.dp))
    Button(onClick = onScan, enabled = !state.scanning, modifier = Modifier.fillMaxWidth()) {
        Text(if (state.scanning) "正在搜索…" else "SSDP 搜索局域网设备")
    }
    Spacer(Modifier.height(8.dp))
    OutlinedButton(onClick = onProvision, modifier = Modifier.fillMaxWidth()) {
        Text("新音箱？走 AP 直连配网")
    }
    Spacer(Modifier.height(12.dp))
    if (state.devices.isEmpty()) {
        Text(
            text = "尚无设备。SSDP 依赖组播，若路由器隔离了组播，请手动输入 IP。",
            style = MaterialTheme.typography.bodySmall,
        )
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(state.devices) { device ->
            Card(
                onClick = { onSelect(device.ip) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(device.ip, fontWeight = FontWeight.Medium)
                    device.server?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    device.searchTarget?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }
}

@Composable
private fun DeviceDetailScreen(state: ConsoleUiState, vm: ConsoleViewModel) {
    var command by remember { mutableStateOf("getNetwork") }
    var newName by remember { mutableStateOf("") }
    var confirmReset by remember { mutableStateOf(false) }

    val playbackVm: PlaybackViewModel = viewModel(
        key = "playback-${state.selectedIp}",
        factory = viewModelFactory {
            initializer { PlaybackViewModel(state.selectedIp.orEmpty(), state.selectedLocation) }
        },
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        val status = state.status
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("设备信息", fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(6.dp))
                if (status == null) {
                    Text("未读取（点击下方刷新）", style = MaterialTheme.typography.bodySmall)
                } else {
                    InfoRow("SSID", status.ssid)
                    InfoRow("上游 Wi-Fi", status.essid)
                    InfoRow("硬件", status.hardware)
                    InfoRow("固件", status.firmware)
                    InfoRow("UUID", status.uuid)
                    InfoRow("有线 MAC", status.ethernetMac)
                    InfoRow("无线 MAC", status.wifiMac)
                    InfoRow("联网", if (status.isOnline) "是" else "否")
                    InfoRow("语言", status.language)
                    InfoRow("Build", status.build)
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        PlaybackCard(playbackVm)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = vm::refreshStatus, enabled = !state.busy) { Text("刷新") }
            listOf("wifi", "line-in", "bluetooth", "optical").forEach { mode ->
                TextButton(onClick = { vm.switchMode(mode) }, enabled = !state.busy) { Text(mode) }
            }
        }

        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = newName,
                onValueChange = { newName = it },
                label = { Text("设备改名") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = { vm.renameDevice(newName); newName = "" }, enabled = !state.busy) {
                Text("改名")
            }
        }

        if (state.slaves.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text("多房间从机", fontWeight = FontWeight.Medium)
            state.slaves.forEach { slave ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(slave.name ?: slave.ip)
                        Text(
                            text = "${slave.ip} · ${if (slave.inGroup) "已入组" else "未入组"} · vol ${slave.volume}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Button(
                        onClick = { vm.setSlaveVolume(slave.ip, (slave.volume ?: 0) + 5) },
                        enabled = !state.busy,
                    ) { Text("音量+") }
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        Text("原始命令（httpapi.asp?command=）", fontWeight = FontWeight.Medium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = command,
                onValueChange = { command = it },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = { vm.sendCommand(command) }, enabled = !state.busy) { Text("执行") }
        }

        Spacer(Modifier.height(10.dp))
        TextButton(onClick = { confirmReset = true }) {
            Text("恢复出厂设置", color = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.height(10.dp))
        HorizontalDivider()
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("日志", fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            TextButton(onClick = vm::clearLog) { Text("清空") }
        }
        state.log.asReversed().forEach {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
            )
        }
        Spacer(Modifier.height(24.dp))
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("确认恢复出厂？") },
            text = { Text("该命令会清空设备当前 Wi-Fi 与配置，设备需要重新配网。") },
            confirmButton = {
                TextButton(onClick = { confirmReset = false; vm.restoreToDefault() }) { Text("确认执行") }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String?) {
    if (value.isNullOrEmpty()) return
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(84.dp),
        )
        Text(text = value, style = MaterialTheme.typography.bodySmall)
    }
}
