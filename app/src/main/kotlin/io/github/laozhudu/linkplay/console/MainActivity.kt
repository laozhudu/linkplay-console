package io.github.laozhudu.linkplay.console

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.laozhudu.linkplay.console.device.DeviceSettingsScreen
import io.github.laozhudu.linkplay.console.library.LibraryScreen
import io.github.laozhudu.linkplay.console.library.QueueScreen
import io.github.laozhudu.linkplay.console.nas.NasScreen
import io.github.laozhudu.linkplay.console.playback.PlayerScreen
import io.github.laozhudu.linkplay.console.playback.QueueBus
import io.github.laozhudu.linkplay.console.playback.QueueRunState
import io.github.laozhudu.linkplay.console.provisioning.ProvisioningScreen
import io.github.laozhudu.linkplay.console.ui.components.AppCard
import io.github.laozhudu.linkplay.console.ui.components.AppShapesExpressive
import io.github.laozhudu.linkplay.console.ui.components.EmptyState
import io.github.laozhudu.linkplay.console.ui.components.PillTag
import io.github.laozhudu.linkplay.console.ui.components.SectionTitle
import io.github.laozhudu.linkplay.console.ui.components.VSpace
import io.github.laozhudu.linkplay.console.ui.theme.LinkPlayTheme
import io.github.laozhudu.linkplay.console.ui.theme.LocalExtraColors
import io.github.laozhudu.linkplay.console.ui.theme.Spacing
import io.github.laozhudu.linkplay.console.ui.theme.StatusColors
import io.github.laozhudu.linkplay.console.ui.theme.seedOf
import io.github.laozhudu.linkplay.discovery.DiscoveredDevice
import io.github.laozhudu.linkplay.protocol.session.SavedDevice

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

/**
 * 页面栈。
 *
 * 原来用 5 个 `Boolean` 各管一个页面，结果「返回」行为完全不一致：
 * 有的页面点返回直接回桌面、有的要连点两次、播放页返回会落到设备页而不是首页。
 * 改成一个显式的栈 + 一个 [BackHandler]，所有页面共用同一条规则。
 */
private enum class Screen { Home, Player, DeviceSettings, Nas, Library, LocalQueue, Provisioning }

@Composable
fun ConsoleApp(vm: ConsoleViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val queue by QueueBus.state.collectAsState()

    // 栈底永远是首页。
    var stack by remember { mutableStateOf(listOf(Screen.Home)) }
    fun push(s: Screen) {
        if (stack.lastOrNull() != s) stack = stack + s
    }

    fun pop(): Boolean {
        if (stack.size <= 1) return false
        stack = stack.dropLast(1)
        return true
    }

    // 统一返回：有上一层就退一层，在首页才允许退出 App。
    // 页面内的返回箭头也调 pop()，与系统返回键/手势行为一致。
    BackHandler(enabled = stack.size > 1) { pop() }

    when (val current = stack.last()) {
        Screen.Home -> HomeScreen(
            state = state,
            queue = queue,
            onOpenDevice = { device ->
                vm.open(device)
                push(Screen.Player)
            },
            onOpenPlayer = { push(Screen.Player) },
            onScan = vm::scan,
            onAddManual = vm::addManual,
            onAddDiscovered = { vm.add(it); push(Screen.Player) },
            onForget = vm::forget,
            onProvision = { push(Screen.Provisioning) },
        )

        Screen.Player -> PlayerScreen(
            deviceIp = state.selectedIp.orEmpty(),
            deviceLocation = state.selectedLocation,
            onBack = {
                // 从播放页返回：回首页并清掉选中态，避免首页顶部还挂着上一台设备
                if (pop() && stack.last() == Screen.Home) vm.leaveDevice()
            },
            onOpenSettings = { push(Screen.DeviceSettings) },
            onOpenNas = { push(Screen.Nas) },
            onOpenLibrary = { push(Screen.Library) },
            onOpenLocalQueue = { push(Screen.LocalQueue) },
        )

        Screen.DeviceSettings -> DeviceSettingsScreen(
            state = state,
            vm = vm,
            onBack = {
                if (pop() && stack.last() == Screen.Home) vm.leaveDevice()
            },
            onOpenPlayer = { push(Screen.Player) },
            onOpenNas = { push(Screen.Nas) },
            onOpenLibrary = { push(Screen.Library) },
            onOpenLocalQueue = { push(Screen.LocalQueue) },
        )

        Screen.Nas -> NasScreen(
            deviceIp = state.selectedIp.orEmpty(),
            deviceLocation = state.selectedLocation,
            deviceUuid = state.selectedUuid,
            onBack = { pop() },
        )

        Screen.Library -> LibraryScreen(
            deviceIp = state.selectedIp.orEmpty(),
            location = state.selectedLocation,
            deviceLabel = state.status?.ssid,
            onBack = { pop() },
        )

        Screen.LocalQueue -> QueueScreen(
            deviceIp = state.selectedIp.orEmpty(),
            location = state.selectedLocation,
            onBack = { pop() },
        )

        Screen.Provisioning -> ProvisioningScreen(
            onDone = { stack = listOf(Screen.Home) },
            onBack = { pop() },
        )
    }
}

/**
 * 首页 = DOSS 遥控器。
 *
 * 只做三件事，按使用频率排：
 * 1. **已保存的音箱**（主入口）—— 打开 App 一眼点开，不用每次等扫描；
 * 2. 正在播放（有队列时）—— 底部一条迷你播放条，点开进播放页；
 * 3. 添加设备（低频，收在下面折叠区）。
 *
 * 为什么不再把「搜到的所有 UPnP 设备」铺在首页：局域网里 NAS、路由、电视都会响应 SSDP，
 * 全列出来根本分不清哪台是音箱，点错还会往路由器投流。
 * 现在只收 LinkPlay 系（判据见 `LinkPlayIdentity`），其余过滤掉并给出统计。
 */
@Composable
private fun HomeScreen(
    state: ConsoleUiState,
    queue: QueueRunState,
    onOpenDevice: (SavedDevice) -> Unit,
    onOpenPlayer: () -> Unit,
    onScan: () -> Unit,
    onAddManual: (String) -> Unit,
    onAddDiscovered: (DiscoveredDevice) -> Unit,
    onForget: (String) -> Unit,
    onProvision: () -> Unit,
) {
    val extras = LocalExtraColors.current
    var showAdd by remember { mutableStateOf(false) }
    var manualIp by remember { mutableStateOf("") }
    var forgetTarget by remember { mutableStateOf<SavedDevice?>(null) }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.lg, vertical = Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SectionTitle(
                    title = "DOSS 遥控器",
                    subtitle = when {
                        state.busy -> "正在连接设备…"
                        state.saved.isEmpty() -> "还没有添加音箱"
                        else -> "${state.saved.count { state.online[it.uuid] != false }} 台在线"
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = Spacing.lg,
                    end = Spacing.lg,
                    bottom = if (queue.nowPlaying != null) 104.dp else Spacing.xl,
                ),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                state.error?.let { err ->
                    item {
                        AppCard {
                            Text(
                                err,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }

                // ---------- 已保存的音箱 ----------
                if (state.saved.isEmpty()) {
                    item {
                        EmptyState(
                            title = "先添加你的音箱",
                            hint = "点下面的「添加音箱」搜索，或直接填 IP。添加过一次就会记住，以后打开 App 直接点。",
                        )
                    }
                }

                items(state.saved, key = { it.uuid }) { device ->
                    val online = state.online[device.uuid]
                    val palette = remember(device.uuid) { seedOf(device.name, device.ip) }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(AppShapesExpressive.squircle)
                            .background(
                                Brush.linearGradient(
                                    listOf(palette.top, palette.accent.copy(alpha = 0.35f)),
                                ),
                            )
                            .clickable { onOpenDevice(device) }
                            .padding(Spacing.lg),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    device.title,
                                    style = MaterialTheme.typography.titleLarge,
                                    color = extras.onBackdrop,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    listOfNotNull(
                                        device.ip,
                                        device.modelName,
                                        when (online) {
                                            true -> "在线"
                                            false -> "离线"
                                            null -> null
                                        },
                                    ).joinToString(" · "),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = extras.onBackdropMuted,
                                    maxLines = 1,
                                )
                            }
                            PillTag(
                                text = if (online == false) "离线" else "打开",
                                color = if (online == false) StatusColors.error else palette.accent,
                            )
                        }
                    }
                }

                if (state.saved.isNotEmpty()) {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            state.saved.forEach { d ->
                                TextButton(onClick = { forgetTarget = d }) {
                                    Text("移除 ${d.title}", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }

                // ---------- 添加设备 ----------
                item {
                    VSpace(Spacing.sm)
                    AppCard(onClick = { showAdd = !showAdd }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (showAdd) "收起" else "添加音箱",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f),
                            )
                            Text(if (showAdd) "−" else "+", style = MaterialTheme.typography.titleLarge)
                        }
                    }
                }

                if (showAdd) {
                    item {
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
                                if (state.scanning) "正在搜索音箱…" else "搜索局域网音箱",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                    }
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = manualIp,
                                onValueChange = { manualIp = it },
                                label = { Text("手动填 IP（如 192.168.99.153）") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(Modifier.width(Spacing.sm))
                            Button(onClick = { onAddManual(manualIp); manualIp = "" }) { Text("添加") }
                        }
                    }
                    if (state.filteredOutCount > 0) {
                        item {
                            Text(
                                "已忽略 ${state.filteredOutCount} 个非音箱的 UPnP 设备（电视 / 路由 / NAS 等）",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    items(state.devices, key = { it.ip }) { d ->
                        AppCard(onClick = { onAddDiscovered(d) }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        d.friendlyName ?: d.friendlySsid ?: d.ip,
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                    Text(
                                        d.ip,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Text("添加", color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                    item {
                        OutlinedButton(onClick = onProvision, modifier = Modifier.fillMaxWidth()) {
                            Text("新音箱？走 AP 直连配网")
                        }
                    }
                }
            }

            // ---------- 迷你播放条 ----------
            if (queue.nowPlaying != null || queue.running) {
                MiniPlayerBar(
                    queue = queue,
                    onOpen = onOpenPlayer,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(Spacing.lg),
                )
            }
        }
    }

    forgetTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { forgetTarget = null },
            title = { Text("移除 ${target.title}？") },
            text = { Text("只删本机保存的记录，不影响音箱本身。下次可以重新搜索添加。") },
            confirmButton = {
                TextButton(onClick = { onForget(target.uuid); forgetTarget = null }) { Text("移除") }
            },
            dismissButton = { TextButton(onClick = { forgetTarget = null }) { Text("取消") } },
        )
    }
}

/**
 * 迷你播放条：只有「在放什么」和一个可点区域。
 *
 * 这里刻意**不放播放/暂停按钮**：所有控制都在播放页一处（这是本轮改动的主旨），
 * 在迷你条上再放一个控制就意味着两处状态要同步，迟早不一致。
 */
@Composable
private fun MiniPlayerBar(
    queue: QueueRunState,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
                queue.nowPlaying ?: "未在播放",
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (queue.index >= 0 && queue.total > 0) "第 ${queue.index + 1}/${queue.total} 首 · 点开操作"
                else "点开继续操作",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
