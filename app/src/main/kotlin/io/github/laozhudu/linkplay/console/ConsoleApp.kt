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
import kotlinx.coroutines.launch

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
    // 内容页搜索框 → 统一搜索页。带一个种子词过去，省掉重新输入。
    var searchSeed by remember { mutableStateOf("") }
    // 「接着听」点一行 = 再投一次，是个挂起调用
    val replayScope = androidx.compose.runtime.rememberCoroutineScope()
    val appContext = androidx.compose.ui.platform.LocalContext.current.applicationContext
    var sub by remember { mutableStateOf<SubPage?>(null) }
    /**
     * 诊断台是从哪进来的。
     *
     * 原来 `onBack` 写死成"回设置页"，但诊断台有三个入口（设备页 / 我 / 设置页），
     * 于是**从「我」页进去，返回会落到「设置」页** —— 这是真 bug（`docs/97` 问题 2）。
     * 记下来路，返回才闭合。
     */
    var diagReturnTo by remember { mutableStateOf<SubPage?>(null) }
    val openDiagnostics: (SubPage?) -> Unit = { from ->
        diagReturnTo = from
        sub = SubPage.Diagnostics
    }


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
    /**
     * 返回键：**只在有二级页时拦截**。
     *
     * ## 原来错在哪
     *
     * 旧逻辑是 `enabled = sub != null || tab != Tab.Devices`，Tab 根上按返回会跳到设备页。
     * 这违反所有平台的惯例 —— Android 标准、iOS、Spotify / Gmail / YouTube 在 Tab 根上
     * 按返回都是**退出 App**（iOS 干脆无效果）。
     *
     * 而且"跳设备页"还会丢掉当前 Tab 的滚动位置和上下文，用户以为点了后退，其实换了个地方。
     *
     * 现在：Tab 上不拦截 → 系统默认行为（退出）；二级页上拦截 → 回 Tab。
     */
    BackHandler(enabled = sub != null) {
        sub = null
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

            SubPage.SearchHub -> io.github.laozhudu.linkplay.console.search.SearchHubScreen(
                initialKeyword = searchSeed,
                onBack = { sub = null; searchSeed = "" },
                onOpenSource = { id ->
                    sub = when (id) {
                        io.github.laozhudu.linkplay.console.search.SourceId.Lx -> SubPage.LxSearch
                        io.github.laozhudu.linkplay.console.search.SourceId.Radio -> SubPage.Radio
                        io.github.laozhudu.linkplay.console.search.SourceId.Local -> SubPage.Library
                        io.github.laozhudu.linkplay.console.search.SourceId.Nas -> SubPage.Nas
                    }
                },
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
                onOpenDiagnostics = { openDiagnostics(SubPage.Settings) },
            )

            // 诊断页：崩溃堆栈 + 每台设备的取数通道 + 运行期问题，可复制/可截图
            SubPage.Diagnostics -> io.github.laozhudu.linkplay.console.diagnostics.DiagnosticsScreen(
                reportProvider = { vm.diagnosticsReport() },
                onClear = { vm.clearDiagnostics() },
                // 回来路（不再写死"回设置页"）
                onBack = { sub = diagReturnTo; diagReturnTo = null },
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
                // 迷你条常驻在导航之上（Spotify 同款位置）。**播放页本身不显示**。
                //
                // ## 显示条件为什么要这么严
                //
                // 原来是 `queue.nowPlaying != null || queue.running`。问题：恢复出来的
                // **占位队列**（`QueueRestore` 记着"上次放到第几首"但内容已经失效）
                // 两条都满足，于是没在放的时候底部挂着一条「未在播放 · 第 1/1 首」，
                // 还挡住了下面最后一行内容。用户截图反馈的就是这个。
                //
                // 大厂不会出现"未在播放"的播放条 —— **没有在放，就没有这条**。
                // 所以三个条件必须同时成立：
                //   1. 有当前曲目，且**不是占位**
                //   2. 真的在放或暂停中（自己投流 / 设备持有 / 设备回报在放）
                //   3. 不在播放页
                val playingNow = state.cards[state.saved.firstOrNull { it.ip == state.selectedIp }?.uuid]?.playing
                    ?: (state.groupMasterIp?.let { m ->
                        state.cards[state.saved.firstOrNull { it.ip == m }?.uuid]?.playing
                    } ?: false)
                val currentItem = queue.current
                val hasRemoteTrack = queue.nowPlaying != null &&
                    currentItem != null &&
                    !io.github.laozhudu.linkplay.protocol.playback.QueueRestore.isPlaceholder(currentItem)
                // **本机播放也要算**：投本机时队列是空的（ExoPlayer 单曲，不持有列表），
                // 只靠 queue 判断的话「用手机听」这一路上永远没有底部条。
                val localUi by io.github.laozhudu.linkplay.console.playback.PlaybackRouter
                    .of(appContext).localState.collectAsState()
                val hasLocalTrack = localUi.hasContent
                val actuallyPlaying = queue.running || queue.deviceOwned || playingNow || hasLocalTrack
                val shouldShowBar = (hasRemoteTrack || hasLocalTrack) && actuallyPlaying && tab != Tab.Player
                if (shouldShowBar) {
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
                    onOpenDiagnostics = { openDiagnostics(null) },
                    onOpenRemote = { tab = Tab.Remote },
                    onOpenPlayer = { tab = Tab.Player },
                )

                Tab.Player -> if (state.selectedIp == null) {
                    NeedDevice(onGoDevices = { tab = Tab.Devices })
                } else {
                    PlayerScreen(
                        deviceIp = state.selectedIp.orEmpty(),
                        deviceLocation = state.selectedLocation,
                        deviceUuid = state.selectedUuid,
                        // 投送状态行要显示 `doss1` 而不是 IP（定稿就是这么写的）
                        deviceName = state.saved.firstOrNull { it.ip == state.selectedIp }?.name,
                        // 不传 onBack：播放页是一级页面，靠底部导航切换
                        onOpenSettings = { sub = SubPage.Settings },
                        onOpenNas = { sub = SubPage.Nas },
                        onOpenLibrary = { sub = SubPage.Library },
                        onOpenLocalQueue = { sub = SubPage.LocalQueue },
                    )
                }

                // 「内容」格 —— 六源合一（定稿第 2 屏）。
                //
                // 原来这里是「洛雪」和「媒体库」两个并列的一级格，
                // 都属于"有什么可放"，按使用场景合成一格，**按源筛选而不是按模块分页**。
                Tab.Content -> io.github.laozhudu.linkplay.console.content.ContentScreen(
                    state = state,
                    onRefresh = vm::refreshMusicCounts,
                    onSearch = { q -> searchSeed = q; sub = SubPage.SearchHub },
                    onOpenLocal = { sub = SubPage.LocalQueue },
                    onOpenNas = { sub = SubPage.Nas },
                    onOpenPlaylists = { sub = SubPage.Library },
                    onOpenRecent = { sub = SubPage.Library },
                    onOpenOnline = { sub = SubPage.Online },
                    onOpenRadio = { sub = SubPage.Radio },
                    onOpenLibrary = { sub = SubPage.Library },
                    onOpenScripts = { sub = SubPage.Scripts },
                    onGoDevices = { tab = Tab.Devices },
                    // 再投一次：优先投给**当时那台**（这是「接着听」的本意），
                    // 旧记录没记设备就投当前选中的那台。
                    onReplay = { play ->
                        val ip = play.deviceIp ?: state.selectedIp
                        if (ip == null) {
                            tab = Tab.Devices
                        } else {
                            replayScope.launch {
                                io.github.laozhudu.linkplay.console.playback.PlaybackRouter
                                    .of(appContext)
                                    .playOne(
                                        url = play.track.playUri,
                                        title = play.track.title,
                                        deviceIp = ip,
                                        location = state.saved.firstOrNull { it.ip == ip }?.location,
                                        deviceUuid = state.saved.firstOrNull { it.ip == ip }?.uuid,
                                    )
                            }
                        }
                    },
                    // 最近加入：电台去电台页，歌单去音乐库
                    onOpenRecentAdd = { add ->
                        sub = if (add.kind == "电台") SubPage.Radio else SubPage.Library
                    },
                )

                // 「遥控」格 —— 红外 / 蓝牙 / WiFi 共用（定稿第 4 屏）。
                // 键由 RemoteControl.keys() 决定，当前只有一个电源键。
                Tab.Remote -> io.github.laozhudu.linkplay.console.remote.RemoteScreen()

                // 「我」格 —— 诊断台是第一项（定稿第 5 屏）。
                Tab.Me -> io.github.laozhudu.linkplay.console.me.MeScreen(
                    onOpenDiagnostics = { openDiagnostics(null) },
                    onOpenSettings = {
                        if (state.selectedIp == null) tab = Tab.Devices else sub = SubPage.Settings
                    },
                    onOpenProvisioning = { sub = SubPage.Provisioning },
                    onOpenScripts = { sub = SubPage.Scripts },
                )
            }
        }
    }
}

/**
 * 底部导航：**五格**（`docs/93` 定稿）。
 *
 * 播放中时「播放」格的字形变成 ▶ —— 唯一一处动态字形，因为
 * 「现在有没有在放」是一眼就该看到的状态，而不是要点进去才知道。
 */
@Composable
internal fun BottomNav(current: Tab, onSelect: (Tab) -> Unit) {
    val haptics = io.github.laozhudu.linkplay.console.ui.apple.rememberAppHaptics()
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
                    .clickable {
                        haptics.tap()
                        onSelect(t)
                    }
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
