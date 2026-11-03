package io.github.laozhudu.linkplay.console

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.viewModels
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.runtime.LaunchedEffect
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
import io.github.laozhudu.linkplay.console.playback.VolumeBus
import io.github.laozhudu.linkplay.console.playback.VolumeOsd
import io.github.laozhudu.linkplay.console.playback.VolumeOverlay
import io.github.laozhudu.linkplay.console.playback.QueueRunState
import io.github.laozhudu.linkplay.console.provisioning.ProvisioningScreen
import io.github.laozhudu.linkplay.console.radio.RadioScreen
import io.github.laozhudu.linkplay.console.room.MultiroomCard
import io.github.laozhudu.linkplay.console.ui.components.AppCard
import io.github.laozhudu.linkplay.console.ui.components.AppShapesExpressive
import io.github.laozhudu.linkplay.console.ui.components.EmptyState
import io.github.laozhudu.linkplay.console.ui.components.PillTag
import io.github.laozhudu.linkplay.console.ui.components.SectionTitle
import io.github.laozhudu.linkplay.console.ui.components.VSpace
import io.github.laozhudu.linkplay.console.ui.theme.LinkPlayTheme
import io.github.laozhudu.linkplay.console.ui.theme.LocalExtraColors
import io.github.laozhudu.linkplay.console.ui.theme.OnDarkPalette
import io.github.laozhudu.linkplay.console.ui.theme.Spacing
import io.github.laozhudu.linkplay.console.ui.theme.StatusColors
import io.github.laozhudu.linkplay.console.ui.theme.seedOf
import io.github.laozhudu.linkplay.discovery.DiscoveredDevice
import io.github.laozhudu.linkplay.protocol.session.SavedDevice

class MainActivity : ComponentActivity() {

    /**
     * 提到 Activity 层是为了接实体音量键 —— 按键事件来自 Activity，不在 Compose 树里。
     */
    private val consoleVm: ConsoleViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LinkPlayTheme {
                ConsoleApp(consoleVm)
            }
        }
    }

    /**
     * 手机音量键 → 音箱音量（原厂 App 的行为）。
     *
     * 两个容易踩的点：
     * 1. **必须在 dispatchKeyEvent 拦**，不能只拦 onKeyDown：系统在 ACTION_UP 上也会调音量；
     * 2. DOWN 和 UP 必须给出**同一个判断结果**，否则会出现"按下改音箱、抬起改手机"。
     *    所以两者都问 [ConsoleViewModel.canHandleVolumeKey]，而不是各自判断。
     *
     * 没选设备/设备离线时一律不拦，让系统照常调手机音量 —— 不能把用户的手机变成砖。
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val isVolUp = event.keyCode == KeyEvent.KEYCODE_VOLUME_UP
        val isVolDown = event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
        if ((isVolUp || isVolDown) && consoleVm.canHandleVolumeKey()) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                // 返回新音量就顺手弹出系统样式的音量条（Android 自己那根已经被我们吞掉了）
                consoleVm.adjustVolumeByKey(if (isVolUp) 1 else -1)?.let { v ->
                    VolumeOsd.show(
                        volume = v,
                        mute = consoleVm.state.value.selectedIp?.let { VolumeBus.volumeOf(it)?.mute } ?: false,
                        deviceName = consoleVm.state.value.saved
                            .firstOrNull { it.ip == consoleVm.state.value.selectedIp }?.title,
                    )
                }
            }
            // DOWN 与 UP 都吞掉，系统就不会再去动手机自己的媒体音量
            return true
        }
        return super.dispatchKeyEvent(event)
    }
}

/**
 * 三个一级页面（底部导航），对应通用播放器的「设备 / 播放 / 媒体库」。
 *
 * 为什么改成底部导航：原来所有页面靠「返回上一级」串起来，从播放页去看 NAS 要
 * 「进 → 出 → 进」，返回键还得数点几次。底部导航是一级入口，切换不需要任何返回操作
 * —— 这也是市面播放器（Spotify / Apple Music / Auxio / Gramophone）的共识结构。
 */
private enum class Tab(val label: String, val glyph: String) {
    Devices("设备", "◍"),
    Player("播放", "▮▮"),
    Sources("媒体库", "≡"),
}

/** 二级页面：从一级页推进去，返回即回一级。 */
private enum class SubPage { Nas, LocalQueue, Library, Radio, Settings, Provisioning }

@Composable
fun ConsoleApp(vm: ConsoleViewModel = viewModel()) {
    /**
     * 整体包一层 Box：音量浮层必须画在所有页面之上（包括二级页），
     * 所以放在这里而不是某个页面里。
     */
    Box(Modifier.fillMaxSize()) {
        ConsoleBody(vm)
        VolumeOsdHost()
    }
}

/** 音量浮层的宿主：读 [VolumeOsd] 状态，1.4 秒无新按键就收起。 */
@Composable
private fun VolumeOsdHost() {
    val osd by VolumeOsd.state.collectAsState()
    val current = osd
    LaunchedEffect(current?.nonce) {
        if (current != null) {
            kotlinx.coroutines.delay(1_400)
            VolumeOsd.hideIfStill(current.nonce)
        }
    }
    VolumeOverlay(
        visible = current != null,
        volume = current?.volume ?: 0,
        mute = current?.mute ?: false,
        deviceName = current?.deviceName,
    )
}

@Composable
private fun ConsoleBody(vm: ConsoleViewModel) {
    val state by vm.state.collectAsState()
    val queue by QueueBus.state.collectAsState()

    var tab by remember { mutableStateOf(Tab.Devices) }
    var sub by remember { mutableStateOf<SubPage?>(null) }

    /**
     * 返回规则（全局唯一，也是 Android 通用规则）：
     * 1. 有二级页 → 退二级；
     * 2. 不在第一个 Tab → 回第一个 Tab；
     * 3. 已在第一个 Tab → 交回系统（退出 App）。
     */
    BackHandler(enabled = sub != null || tab != Tab.Devices) {
        when {
            sub != null -> sub = null
            else -> tab = Tab.Devices
        }
    }

    val currentSub = sub
    if (currentSub != null) {
        // 二级页全屏，不带底部导航：避免「在一个页面里还显示别的 Tab」的割裂感。
        //
        // 必须在这里统一加系统栏内边距：这些页面走的是 Scaffold 之外的早退分支，
        // 原来完全不避让状态栏，于是「顶部内容贴着屏幕边缘」—— 用户反馈的正是这个。
        // 放在这层的好处是每个页面自己不用记，也不会出现某个页面加了、某个没加。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars),
        ) {
            when (currentSub) {
            SubPage.Nas -> NasScreen(
                deviceIp = state.selectedIp.orEmpty(),
                deviceLocation = state.selectedLocation,
                deviceUuid = state.selectedUuid,
                onBack = { sub = null },
            )

            SubPage.LocalQueue -> QueueScreen(
                deviceIp = state.selectedIp.orEmpty(),
                location = state.selectedLocation,
                onBack = { sub = null },
            )

            SubPage.Radio -> RadioScreen(
                deviceIp = state.selectedIp.orEmpty(),
                deviceLabel = state.status?.ssid,
                onBack = { sub = null },
            )

            SubPage.Library -> LibraryScreen(
                deviceIp = state.selectedIp.orEmpty(),
                location = state.selectedLocation,
                deviceLabel = state.status?.ssid,
                onBack = { sub = null },
            )

            SubPage.Settings -> DeviceSettingsScreen(
                state = state,
                vm = vm,
                onBack = { sub = null },
            )

            SubPage.Provisioning -> ProvisioningScreen(
                onDone = { sub = null; tab = Tab.Devices },
                onBack = { sub = null },
            )
            }
        }
        return
    }

    Scaffold(
        bottomBar = {
            Column {
                // 有播放内容时，迷你条常驻在导航之上（Spotify 同款位置）。
                // **播放页本身不显示**：那里已经有完整控制，再叠一条等于凭空多一层，
                // 而且它一出现就顶掉播放页的高度 —— 用户说的「点击是多一层」就是这个。
                if ((queue.nowPlaying != null || queue.running) && tab != Tab.Player) {
                    MiniPlayerBar(
                        queue = queue,
                        onOpen = { tab = Tab.Player },
                        modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.xs),
                    )
                }
                BottomNav(current = tab, onSelect = { tab = it })
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                Tab.Devices -> HomeScreen(
                    state = state,
                    vm = vm,
                    onOpenDevice = { device ->
                        vm.open(device)
                        tab = Tab.Player
                    },
                    onScan = vm::scan,
                    onAddManual = vm::addManual,
                    onAddDiscovered = { vm.add(it); tab = Tab.Player },
                    onForget = vm::forget,
                    onProvision = { sub = SubPage.Provisioning },
                )

                Tab.Player -> if (state.selectedIp == null) {
                    NeedDevice(onGoDevices = { tab = Tab.Devices })
                } else {
                    PlayerScreen(
                        deviceIp = state.selectedIp.orEmpty(),
                        deviceLocation = state.selectedLocation,
                        deviceUuid = state.selectedUuid,
                        // 不传 onBack：播放页是一级页面，靠底部导航切换
                        onOpenSettings = { sub = SubPage.Settings },
                        onOpenNas = { sub = SubPage.Nas },
                        onOpenLibrary = { sub = SubPage.Library },
                        onOpenLocalQueue = { sub = SubPage.LocalQueue },
                    )
                }

                Tab.Sources -> SourcesScreen(
                    state = state,
                    onOpenNas = { sub = SubPage.Nas },
                    onOpenLibrary = { sub = SubPage.Library },
                    onOpenLocal = { sub = SubPage.LocalQueue },
                    onOpenRadio = { sub = SubPage.Radio },
                    onGoDevices = { tab = Tab.Devices },
                )
            }
        }
    }
}

/** 底部导航：三个一级页面；播放中时「播放」图标变成 ▶。 */
@Composable
private fun BottomNav(current: Tab, onSelect: (Tab) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(vertical = Spacing.xs),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        Tab.entries.forEach { t ->
            val active = t == current
            val color = if (active) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant
            val glyph = if (t == Tab.Player) {
                if (QueueBus.state.value.running) "▶" else t.glyph
            } else {
                t.glyph
            }
            Column(
                modifier = Modifier
                    .clip(AppShapesExpressive.squircle)
                    .clickable { onSelect(t) }
                    .padding(horizontal = Spacing.xl, vertical = Spacing.xs),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(glyph, style = MaterialTheme.typography.titleMedium, color = color)
                Text(t.label, style = MaterialTheme.typography.labelMedium, color = color)
            }
        }
    }
}

/** 没选设备时的播放页占位。 */
@Composable
private fun NeedDevice(onGoDevices: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.lg),
        verticalArrangement = Arrangement.Center,
    ) {
        EmptyState(
            title = "还没有选中音箱",
            hint = "先去「设备」页点一台音箱，回来这里就能控制播放。",
        )
        VSpace(Spacing.md)
        Button(onClick = onGoDevices, modifier = Modifier.fillMaxWidth()) { Text("去设备页") }
    }
}

/** 媒体库入口页：三个来源，各自推进去。 */
@Composable
private fun SourcesScreen(
    state: ConsoleUiState,
    onOpenNas: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenLocal: () -> Unit,
    onOpenRadio: () -> Unit,
    onGoDevices: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.lg),
    ) {
        VSpace(Spacing.md)
        SectionTitle(
            "媒体库",
            if (state.selectedIp == null) "先选一台音箱" else "选一个来源，点歌即播",
        )
        VSpace(Spacing.md)

        if (state.selectedIp == null) {
            EmptyState("还没有选中音箱", "媒体库里的歌要投给音箱播，所以先选设备。")
            VSpace(Spacing.sm)
            Button(onClick = onGoDevices, modifier = Modifier.fillMaxWidth()) { Text("去设备页") }
            return
        }

        AppCard(onClick = onOpenNas) {
            SectionTitle("NAS 媒体库", "局域网 DLNA 服务器上的曲目，点一首即播")
        }
        VSpace(Spacing.sm)
        AppCard(onClick = onOpenLocal) {
            SectionTitle("手机本地音乐", "手机里的歌，通过本机 HTTP 服务推给音箱")
        }
        VSpace(Spacing.sm)
        AppCard(onClick = onOpenRadio) {
            SectionTitle("网络电台", "内置可用电台 + 自己粘流地址，点一下即播")
        }
        VSpace(Spacing.sm)
        AppCard(onClick = onOpenLibrary) {
            SectionTitle("音乐库", "搜索 · 收藏 · 最近播放")
        }
    }
}

/**
 * 「设备」页：已保存的音箱 + 添加入口。
 *
 * 只做三件事，按使用频率排：
 * 1. **已保存的音箱**（主入口）—— 打开 App 一眼点开，不用每次等扫描；
 * 2. 添加设备（低频，收在折叠区）；
 * 3. 长按卡片可移除记录。
 *
 * 为什么不再把「搜到的所有 UPnP 设备」铺出来：局域网里 NAS、路由、电视都会响应 SSDP，
 * 全列出来分不清哪台是音箱，点错还会往路由器投流。现在只收 LinkPlay 系
 * （判据见 `LinkPlayIdentity`），其余过滤并给出统计。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeScreen(
    state: ConsoleUiState,
    vm: ConsoleViewModel,
    onOpenDevice: (SavedDevice) -> Unit,
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

    Column(Modifier.fillMaxSize().padding(horizontal = Spacing.lg)) {
        VSpace(Spacing.md)
        SectionTitle(
            title = "我的音箱",
            subtitle = when {
                state.busy -> "正在连接…"
                state.saved.isEmpty() -> "还没有添加"
                else -> "${state.saved.count { state.online[it.uuid] != false }} 台在线"
            },
        )
        VSpace(Spacing.md)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            state.error?.let { err ->
                item {
                    AppCard {
                        Text(
                            err,
                            style = MaterialTheme.typography.bodySmall,
                            color = StatusColors.error,
                        )
                    }
                }
            }

            if (state.saved.isEmpty()) {
                item {
                    EmptyState(
                        title = "先添加你的音箱",
                        hint = "点下面的「添加音箱」搜索，或直接填 IP。添加一次就会记住，以后打开直接点。",
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
                                listOf(palette.top, palette.accent.copy(alpha = 0.28f)),
                            ),
                        )
                        .combinedClickable(
                            onClick = { onOpenDevice(device) },
                            onLongClick = { forgetTarget = device },
                        )
                        .padding(Spacing.lg),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                device.title,
                                style = MaterialTheme.typography.titleLarge,
                                // 卡片底是派生深色渐变，文字必须恒定白：
                                // 跟着主题变会在浅色模式下变成"深字压深底"
                                color = OnDarkPalette.text,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                listOfNotNull(
                                    device.ip,
                                    device.modelName,
                                    // 入组的从机连不上，但不能说"离线" —— 它正在同步播放
                                    state.groupedUnder[device.uuid.lowercase()]?.let { "同步播放中 · 属 $it" }
                                        ?: when (online) {
                                            true -> "在线"
                                            false -> "离线"
                                            null -> null
                                        },
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.labelMedium,
                                color = OnDarkPalette.muted,
                                maxLines = 1,
                            )
                        }
                        val groupedUnder = state.groupedUnder[device.uuid.lowercase()]
                        PillTag(
                            text = when {
                                groupedUnder != null -> "组内"
                                online == false -> "离线"
                                else -> "打开"
                            },
                            color = when {
                                groupedUnder != null -> StatusColors.ok
                                online == false -> StatusColors.error
                                else -> palette.accent
                            },
                        )
                    }
                }
            }

            if (state.saved.isNotEmpty()) {
                item {
                    Text(
                        "长按设备卡片可以移除记录",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

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

            // 多房间放在整个列表**最底部**（添加音箱之后）：
            // 组网是组一次管很久的低频配置，不该占据首屏；设备列表与添加入口优先。
            if (state.saved.size >= 2 || state.groupMembers.size > 1) {
                item {
                    VSpace(Spacing.lg)
                    MultiroomCard(state = state, vm = vm, onOpenDevice = onOpenDevice)
                }
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
 * 迷你播放条：只显示「在放什么」+ 点开。
 *
 * 刻意**不放播放/暂停**：所有控制都在播放页一处。迷你条上再放一个控制，
 * 就意味着两处状态要同步，迟早不一致。
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
