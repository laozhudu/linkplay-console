package io.github.laozhudu.linkplay.console

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import io.github.laozhudu.linkplay.console.library.LibraryScreen
import io.github.laozhudu.linkplay.console.nas.NasScreen
import io.github.laozhudu.linkplay.console.playback.PlayerScreen
import io.github.laozhudu.linkplay.console.ui.theme.LinkPlayTheme
import io.github.laozhudu.linkplay.console.library.QueueScreen
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import io.github.laozhudu.linkplay.console.ui.components.AppShapesExpressive
import io.github.laozhudu.linkplay.console.ui.components.DeviceCard
import io.github.laozhudu.linkplay.console.ui.components.EmptyState
import io.github.laozhudu.linkplay.console.ui.components.HeroEntryCard
import io.github.laozhudu.linkplay.console.ui.components.VSpace
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import io.github.laozhudu.linkplay.console.ui.components.AppCard
import io.github.laozhudu.linkplay.console.ui.components.PillTag
import io.github.laozhudu.linkplay.console.ui.components.SectionTitle
import io.github.laozhudu.linkplay.console.ui.theme.StatusColors
import io.github.laozhudu.linkplay.console.ui.theme.LocalExtraColors
import io.github.laozhudu.linkplay.console.ui.theme.Spacing
import io.github.laozhudu.linkplay.console.ui.theme.seedOf
import io.github.laozhudu.linkplay.console.playback.PlaybackSummaryRow
import io.github.laozhudu.linkplay.console.playback.PlaybackViewModel
import io.github.laozhudu.linkplay.console.provisioning.ProvisioningScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LinkPlayTheme {
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
    var showQueue by remember { mutableStateOf(false) }
    var showLibrary by remember { mutableStateOf(false) }
    var showNas by remember { mutableStateOf(false) }
    var showPlayer by remember { mutableStateOf(false) }

    if (showPlayer) {
        // 全屏播放页：自己管背景与状态栏留白，所以不用 Scaffold
        PlayerScreen(
            deviceIp = state.selectedIp.orEmpty(),
            deviceLocation = state.selectedLocation,
            onBack = { showPlayer = false },
        )
        return
    }

    if (showProvisioning) {
        Scaffold(
            topBar = { TopAppBar(title = { Text("添加音箱（AP 直连配网）", fontWeight = FontWeight.SemiBold) }) },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = Spacing.lg),
            ) {
                ProvisioningScreen(
                    onDone = { showProvisioning = false },
                    onBack = { showProvisioning = false },
                )
            }
        }
        return
    }

    if (showNas) {
        Scaffold(
            topBar = { TopAppBar(title = { Text("NAS 媒体库", fontWeight = FontWeight.SemiBold) }) },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = Spacing.lg),
            ) {
                NasScreen(
                    deviceUuid = state.selectedUuid,
                    deviceIp = state.selectedIp.orEmpty(),
                    deviceLocation = state.selectedLocation,
                    onBack = { showNas = false },
                )
            }
        }
        return
    }

    if (showLibrary) {
        Scaffold(
            topBar = { TopAppBar(title = { Text("音乐库", fontWeight = FontWeight.SemiBold) }) },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = Spacing.lg),
            ) {
                LibraryScreen(
                    deviceIp = state.selectedIp.orEmpty(),
                    location = state.selectedLocation,
                    deviceLabel = state.status?.ssid,
                    onBack = { showLibrary = false },
                )
            }
        }
        return
    }

    if (showQueue) {
        Scaffold(
            topBar = { TopAppBar(title = { Text("整列表推送", fontWeight = FontWeight.SemiBold) }) },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = Spacing.lg),
            ) {
                QueueScreen(
                    deviceIp = state.selectedIp.orEmpty(),
                    location = state.selectedLocation,
                    onBack = { showQueue = false },
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
                .padding(horizontal = Spacing.lg),
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
                DeviceDetailScreen(
                    state = state,
                    vm = vm,
                    onOpenQueue = { showQueue = true },
                    onOpenLibrary = { showLibrary = true },
                    onOpenNas = { showNas = true },
                    onOpenPlayer = { showPlayer = true },
                )
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

    Column(Modifier.fillMaxSize()) {
        // 搜索是列表页的主动作，做成大胶囊；手动输入降为次级，避免两个同等重的入口抢注意力
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(AppShapesExpressive.pill)
                .background(MaterialTheme.colorScheme.primary)
                .clickable(enabled = !state.scanning, onClick = onScan),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (state.scanning) "正在搜索局域网…" else "搜索局域网设备",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
        VSpace(Spacing.md)

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = manualIp,
                onValueChange = { manualIp = it },
                label = { Text("手动输入 IP") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(Spacing.sm))
            Button(onClick = { onAdd(manualIp); manualIp = "" }) { Text("添加") }
        }
        VSpace(Spacing.sm)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            OutlinedButton(onClick = onProvision) { Text("AP 直连配网") }
        }
        VSpace(Spacing.md)

        if (state.devices.isEmpty()) {
            EmptyState(
                title = "还没有设备",
                hint = "搜索依赖 SSDP 组播；路由器隔离组播时，直接在上面填 IP 添加。",
            )
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            items(state.devices) { device ->
                DeviceCard(
                    ip = device.ip,
                    server = device.server,
                    searchTarget = device.searchTarget,
                    onClick = { onSelect(device.ip) },
                )
            }
        }
    }
}

@Composable
private fun DeviceDetailScreen(
    state: ConsoleUiState,
    vm: ConsoleViewModel,
    onOpenQueue: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenNas: () -> Unit,
    onOpenPlayer: () -> Unit,
) {
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
        // 播放页入口：渐变大卡片。这是最常用的动作，用普通卡片会淹没在设置项里
        val entryPalette = remember(playbackVm) { seedOf(null, state.selectedIp) }
        HeroEntryCard(
            title = "播放控制",
            subtitle = "进度 · 音量 · 循环 · 上下曲",
            topColor = entryPalette.top,
            accentColor = entryPalette.accent,
            onClick = onOpenPlayer,
        )
        VSpace(Spacing.md)

        val status = state.status
        AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle("设备信息", modifier = Modifier.weight(1f))
                    if (status != null) {
                        PillTag(
                            text = if (status.isOnline) "在线" else "离线",
                            color = if (status.isOnline) StatusColors.ok else StatusColors.error,
                        )
                    }
                }
                VSpace(Spacing.xs)
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

        Spacer(Modifier.height(10.dp))
        // 详情页不再塞整套控制：同一套控制出现在两处必然漂移。
        // 这里只留「当前在放什么 + 进播放页」一行，控制统一走 PlayerScreen。
        PlaybackSummaryRow(
            vm = playbackVm,
            onOpen = onOpenPlayer,
        )
        VSpace(Spacing.sm)
        // 三个入口做成同构卡片：标题 + 一句说明。比三个一模一样的按钮更容易区分用途
        AppCard(onClick = onOpenNas) {
            SectionTitle("NAS 媒体库", "浏览局域网 DLNA 服务器，选中后整列表推送")
        }
        VSpace(Spacing.sm)
        AppCard(onClick = onOpenLibrary) {
            SectionTitle("音乐库", "搜索 · 收藏 · 最近播放 · 网络电台")
        }
        VSpace(Spacing.sm)
        AppCard(onClick = onOpenQueue) {
            SectionTitle("手机本地音乐", "整个列表推给音箱（本机 HTTP 服务）")
        }
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
