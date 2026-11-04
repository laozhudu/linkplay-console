package io.github.laozhudu.linkplay.console

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.laozhudu.linkplay.console.device.DeviceContentScreen
import io.github.laozhudu.linkplay.console.device.DeviceSettingsScreen
import io.github.laozhudu.linkplay.console.diagnostics.NavTrace
import io.github.laozhudu.linkplay.console.home.HomeScreen
import io.github.laozhudu.linkplay.console.home.MiniPlayerBar
import io.github.laozhudu.linkplay.console.library.LibraryScreen
import io.github.laozhudu.linkplay.console.library.QueueScreen
import io.github.laozhudu.linkplay.console.nas.NasScreen
import io.github.laozhudu.linkplay.console.online.OnlineScreen
import io.github.laozhudu.linkplay.console.playback.PlayerScreen
import io.github.laozhudu.linkplay.console.playback.QueueBus
import io.github.laozhudu.linkplay.console.playback.VolumeOsd
import io.github.laozhudu.linkplay.console.playback.VolumeOverlay
import io.github.laozhudu.linkplay.console.provisioning.ProvisioningScreen
import io.github.laozhudu.linkplay.console.provisioning.WpsGuideScreen
import io.github.laozhudu.linkplay.console.radio.RadioScreen
import io.github.laozhudu.linkplay.console.ui.components.AppShapesExpressive
import io.github.laozhudu.linkplay.console.ui.components.EmptyState
import io.github.laozhudu.linkplay.console.ui.components.VSpace
import io.github.laozhudu.linkplay.console.ui.theme.Spacing

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
internal fun VolumeOsdHost() {
    val osd by VolumeOsd.state.collectAsState()
    val current = osd
    // 音量浮层是全屏遮罩：它在的时候点任何地方都不响应。
    // 用户很容易把它描述成"按钮坏了"，所以必须进诊断。
    LaunchedEffect(current != null) { NavTrace.volumeOsdVisible = current != null }
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
internal fun ConsoleBody(vm: ConsoleViewModel) {
    val state by vm.state.collectAsState()
    val queue by QueueBus.state.collectAsState()

    var tab by remember { mutableStateOf(Tab.Devices) }
    var sub by remember { mutableStateOf<SubPage?>(null) }

    // 把"用户此刻在哪"同步到诊断快照。
    // 为什么用 LaunchedEffect 而不是直接在 body 里赋值：Compose 的 body 可能因为
    // 任意状态变化而重组，直接赋值等于每帧都写一次全局变量；LaunchedEffect 只在真的变了才跑。
    LaunchedEffect(tab, sub) {
        NavTrace.tab = tab.label
        NavTrace.subPage = sub?.name
    }

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
                deviceUuid = state.selectedUuid,
            )

            SubPage.Library -> LibraryScreen(
                deviceIp = state.selectedIp.orEmpty(),
                location = state.selectedLocation,
                deviceLabel = state.status?.ssid,
                onBack = { sub = null },
                deviceUuid = state.selectedUuid,
            )

            SubPage.DeviceContent -> DeviceContentScreen(
                state = state,
                vm = vm,
                onBack = { sub = null },
            )

            SubPage.Online -> OnlineScreen(
                deviceIp = state.selectedIp.orEmpty(),
                location = state.selectedLocation,
                deviceUuid = state.selectedUuid,
                onBack = { sub = null },
            )

            SubPage.Scripts -> io.github.laozhudu.linkplay.console.lx.ScriptsScreen(
                onBack = { sub = null },
            )

            SubPage.LxSearch -> io.github.laozhudu.linkplay.console.lx.LxSearchScreen(
                deviceIp = state.selectedIp.orEmpty(),
                location = state.selectedLocation,
                deviceUuid = state.selectedUuid,
                onBack = { sub = null },
                onOpenScripts = { sub = SubPage.Scripts },
            )

            SubPage.Settings -> DeviceSettingsScreen(
                state = state,
                vm = vm,
                onBack = { sub = null },
                onOpenDiagnostics = { sub = SubPage.Diagnostics },
            )

            // 诊断页：崩溃堆栈 + 每台设备的取数通道 + 运行期问题，可复制/可截图
            SubPage.Diagnostics -> io.github.laozhudu.linkplay.console.diagnostics.DiagnosticsScreen(
                reportProvider = { vm.diagnosticsReport() },
                onClear = { vm.clearDiagnostics() },
                onBack = { sub = SubPage.Settings },
            )

            SubPage.Provisioning -> ProvisioningScreen(
                onDone = { sub = null; tab = Tab.Devices },
                onBack = { sub = null },
                onOpenWps = { sub = SubPage.Wps },
            )

            SubPage.Wps -> WpsGuideScreen(
                state = state,
                onRescan = { vm.scan() },
                onBack = { sub = SubPage.Provisioning },
                onDone = { sub = null; tab = Tab.Devices },
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
                    val playingNow = state.cards[state.saved.firstOrNull { it.ip == state.selectedIp }?.uuid]?.playing
                        ?: (state.groupMasterIp?.let { m ->
                            state.cards[state.saved.firstOrNull { it.ip == m }?.uuid]?.playing
                        } ?: false)
                    MiniPlayerBar(
                        queue = queue,
                        onOpen = { tab = Tab.Player },
                        modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.xs),
                        playing = playingNow,
                        onToggle = {
                            // 在放就暂停，不在放就「全部播放」—— 组内一起响，和原厂一致
                            if (playingNow) vm.pauseAll() else vm.playAll()
                        },
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
                    onProvision = { sub = SubPage.Provisioning },
                    onOpenSettings = { device ->
                        vm.open(device)
                        sub = SubPage.Settings
                    },
                    onOpenContent = { sub = SubPage.DeviceContent },
                    onOpenPlayer = { tab = Tab.Player },
                    onOpenDiagnostics = { sub = SubPage.Diagnostics },
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

                Tab.Lx -> io.github.laozhudu.linkplay.console.lx.LxSearchScreen(
                    deviceIp = state.selectedIp.orEmpty(),
                    location = state.selectedLocation,
                    deviceUuid = state.selectedUuid,
                    showBack = false,
                    onOpenScripts = { sub = SubPage.Scripts },
                )

                Tab.Sources -> SourcesScreen(
                    state = state,
                    vm = vm,
                    onOpenOnline = { sub = SubPage.Online },
                    onOpenNas = { sub = SubPage.Nas },
                    onOpenLibrary = { sub = SubPage.Library },
                    onOpenLocal = { sub = SubPage.LocalQueue },
                    onOpenRadio = { sub = SubPage.Radio },
                    onOpenSettings = { sub = SubPage.Settings },
                    onOpenScripts = { sub = SubPage.Scripts },
                    onOpenLxSearch = { sub = SubPage.LxSearch },
                    onGoDevices = { tab = Tab.Devices },
                )
            }
        }
    }
}

/** 底部导航：三个一级页面；播放中时「播放」图标变成 ▶。 */
@Composable
internal fun BottomNav(current: Tab, onSelect: (Tab) -> Unit) {
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
                    .padding(horizontal = Spacing.lg, vertical = Spacing.xs),
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
internal fun NeedDevice(onGoDevices: () -> Unit) {
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
