package io.github.laozhudu.linkplay.console

import io.github.laozhudu.linkplay.protocol.multiroom.DragGrouping
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.mutableStateMapOf
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
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.laozhudu.linkplay.console.device.DeviceContentScreen
import io.github.laozhudu.linkplay.console.device.DeviceSettingsScreen
import io.github.laozhudu.linkplay.console.library.LibraryScreen
import io.github.laozhudu.linkplay.console.library.MyMusicScreen
import io.github.laozhudu.linkplay.console.library.QueueScreen
import io.github.laozhudu.linkplay.console.nas.NasScreen
import io.github.laozhudu.linkplay.console.online.OnlineScreen
import io.github.laozhudu.linkplay.console.playback.PlayerScreen
import io.github.laozhudu.linkplay.console.playback.QueueBus
import io.github.laozhudu.linkplay.console.playback.VolumeBus
import io.github.laozhudu.linkplay.console.playback.VolumeOsd
import io.github.laozhudu.linkplay.console.playback.VolumeOverlay
import io.github.laozhudu.linkplay.console.playback.QueueRunState
import io.github.laozhudu.linkplay.console.provisioning.ProvisioningScreen
import io.github.laozhudu.linkplay.console.provisioning.WpsGuideScreen
import io.github.laozhudu.linkplay.console.radio.RadioScreen
import io.github.laozhudu.linkplay.console.ui.components.AppCard
import io.github.laozhudu.linkplay.console.ui.components.AppShapesExpressive
import io.github.laozhudu.linkplay.console.ui.components.CircleIcon
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
private enum class SubPage { Nas, LocalQueue, Library, Radio, Settings, Provisioning, DeviceContent, Online, Wps }

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

            SubPage.Settings -> DeviceSettingsScreen(
                state = state,
                vm = vm,
                onBack = { sub = null },
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
                    vm = vm,
                    onOpenOnline = { sub = SubPage.Online },
                    onOpenNas = { sub = SubPage.Nas },
                    onOpenLibrary = { sub = SubPage.Library },
                    onOpenLocal = { sub = SubPage.LocalQueue },
                    onOpenRadio = { sub = SubPage.Radio },
                    onOpenSettings = { sub = SubPage.Settings },
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

/**
 * 「媒体库」Tab。
 *
 * 主体是原厂样式的「我的音乐」汇总（几行入口 + 真实数量）；原来那几张来源卡片
 * 收在下方「其它入口」里，功能一个没少，只是不再占着首屏。
 */
@Composable
private fun SourcesScreen(
    state: ConsoleUiState,
    vm: ConsoleViewModel,
    onOpenNas: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenLocal: () -> Unit,
    onOpenRadio: () -> Unit,
    onOpenOnline: () -> Unit,
    onOpenSettings: () -> Unit,
    onGoDevices: () -> Unit,
) {
    MyMusicScreen(
        state = state,
        // 进页面刷一次：本地曲库/歌单/电台都可能是刚在别的页面改过的
        onRefresh = vm::refreshMusicCounts,
        onOpenLocal = onOpenLocal,
        onOpenNas = onOpenNas,
        // 「全部歌单」「最近播放」都落在音乐库页（那里有对应分区），不新开二级页
        onOpenPlaylists = onOpenLibrary,
        onOpenRecent = onOpenLibrary,
        onOpenOnline = onOpenOnline,
        onOpenRadio = onOpenRadio,
        onOpenLibrary = onOpenLibrary,
        onOpenDeviceSettings = onOpenSettings,
        onGoDevices = onGoDevices,
    )
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
    onProvision: () -> Unit,
    /** 齿轮：进这台设备的设置页（原厂卡片右上角就是齿轮）。 */
    onOpenSettings: (SavedDevice) -> Unit,
    /** 齿轮菜单里的「设备内容」（预设键 / 固件升级）。 */
    onOpenContent: () -> Unit = {},
    /** 聚合播放条点一下进播放页。 */
    onOpenPlayer: () -> Unit = {},
) {
    val extras = LocalExtraColors.current
    var showAdd by remember { mutableStateOf(false) }
    var manualIp by remember { mutableStateOf("") }

    // 齿轮点开的设备操作菜单（改名 / 删除 / 进设置）
    var menuTarget by remember { mutableStateOf<SavedDevice?>(null) }
    var sleepMenu by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<SavedDevice?>(null) }
    var deleteTarget by remember { mutableStateOf<SavedDevice?>(null) }

    // 拖拽组网的状态：整页共用一份，卡片只负责上报"我被拖了/我松手命中谁"
    val dragState = remember { DragState() }
    // 每台卡片在**根坐标**里的中心点，用来判断松手命中了哪台。
    // 拖拽期间被拖那台的坐标被冻结（见 DraggableDeviceCard 里的 onGloballyPositioned）
    val centers = remember { mutableStateMapOf<String, Offset>() }
    val density = LocalDensity.current
    // 命中半径：卡片中心到指尖。卡片高约 190dp、纵向间距约 200dp，
    // 130dp 能可靠命中相邻卡片，又不至于把两条之外的也吸过来
    val hitRadiusPx = with(density) { 130.dp.toPx() }

    // 指尖位置 = 被拖卡片（冻结的）中心 + 累计位移；据此算悬停目标
    LaunchedEffect(dragState.draggingUuid, dragState.offset) {
        val dragged = dragState.draggingUuid
        if (dragged == null) {
            dragState.hoverUuid = null
            return@LaunchedEffect
        }
        val origin = centers[dragged] ?: return@LaunchedEffect
        val points = centers.map { (uuid, c) -> Triple(uuid, c.x, c.y) }.toList()
        dragState.hoverUuid = DragGrouping.hitTest(
            points = points,
            x = origin.x + dragState.offset.x,
            y = origin.y + dragState.offset.y,
            draggedUuid = dragged,
            maxDistancePx = hitRadiusPx,
        )
    }

    Column(Modifier.fillMaxSize().padding(horizontal = Spacing.lg)) {
        VSpace(Spacing.md)
        // 顶栏按原厂的「设备列表 + 添加 + 刷新」：这两个动作原来藏在「添加音箱」折叠区里，
        // 用户得先展开才能刷新，属于把常用动作埋在低频入口后面
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                SectionTitle(
                    title = "我的音箱",
                    subtitle = when {
                        state.busy -> "正在连接…"
                        state.saved.isEmpty() -> "还没有添加"
                        else -> "${state.saved.count { state.online[it.uuid] != false }} 台在线"
                    },
                )
            }
            CircleIcon(onClick = { showAdd = !showAdd }, size = 40.dp) {
                Text("+", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(Spacing.xs))
            // 刷新 = 重新探活（不清设备列表、不闪屏），比"再搜一遍"快
            CircleIcon(onClick = { vm.refreshDevices() }, size = 40.dp) {
                Text("↻", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        // ---------- 聚合播放条（Sonos 的 Now Playing 条 / Music Assistant 的 Player Bar）----------
        // 主页最需要但之前一直缺的一块：**现在在放什么、一键停**。
        // 之前要点进播放页才能看到，首页只有一堆卡片。
        if (state.saved.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.sm)
                    .clip(AppShapesExpressive.squircle)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onOpenPlayer() }
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        state.playingTitle ?: if (state.anyPlaying) "正在播放" else "未在播放",
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        buildString {
                            append(state.groupMasterName ?: state.saved.firstOrNull { it.ip == state.selectedIp }?.title ?: "未选设备")
                            if (state.groupSize > 1) append(" · ${state.groupSize} 台同步")
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                // 播放/暂停：与底部大按钮共用同一个判定（state.anyPlaying），
                // 两处各自算一次必然出现"条上说暂停、按钮说播放"
                CircleIcon(onClick = { vm.togglePlayAll() }, size = 44.dp) {
                    Text(
                        if (state.anyPlaying) "⏸" else "▶",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.width(Spacing.xs))
                // 组网入口：勾选式（Sonos Grouping 菜单），拖拽保留但不再是唯一路径
                CircleIcon(onClick = { vm.openGrouping() }, size = 44.dp) {
                    Text("⧉", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (state.saved.size >= 2) {
            Text(
                "点 ⧉ 勾选要同步的音箱；也可以长按卡片拖到另一台上",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }
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
                DraggableDeviceCard(
                    device = device,
                    online = state.online[device.uuid],
                    groupedUnder = state.groupedUnder[device.uuid.lowercase()],
                    card = state.cards[device.uuid],
                    // 拖拽组网：松手时命中了另一台就把"被拖的"挂到"被命中的"上
                    drag = dragState,
                    siblingCount = state.saved.size,
                    isGroupMaster = state.groupMasterIp != null && device.ip == state.groupMasterIp,
                    masked = state.groupMembers.firstOrNull {
                        it.uuid?.equals(device.uuid, ignoreCase = true) == true
                    }?.masked == true,
                    profile = state.cards[device.uuid]?.profile
                        ?: io.github.laozhudu.linkplay.protocol.dlna.DeviceProfile.UNKNOWN,
                    onCenter = { c -> centers[device.uuid] = c },
                    onOpen = { onOpenDevice(device) },
                    onSettings = { onOpenSettings(device) },
                    onVolume = { vm.setCardVolume(device, it) },
                    onChannel = { vm.cycleCardChannel(device) },
                    onUngroup = { vm.ungroupCard(device) },
                    onMask = { vm.toggleMask(device) },
                    onTogglePlay = { vm.toggleDevicePlay(device) },
                    onRename = { menuTarget = device },
                    onDropOn = { targetUuid ->
                        state.saved.firstOrNull { it.uuid == targetUuid }?.let { vm.groupByDrag(device, it) }
                    },
                )
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

            // 底部只留「全部播放」（原厂那个大按钮）。
            // 原来上面还有一行「如何在多音箱同步和独立播放间切换？」—— 用户要求删掉：
            // 有了拖拽，这行字既不是操作也不是链接，占位置而已。
            if (state.saved.isNotEmpty()) {
                item {
                    VSpace(Spacing.md)
                    // 媒体播放器的通行约定：播放/暂停按钮显示**点了会发生什么**，
                    // 不是当前状态（UX StackExchange 上 572 赞的结论，主流播放器全都如此）。
                    // 所以正在放的时候这里必须是「全部暂停」—— 用户反馈的就是这一点。
                    val playing = state.anyPlaying
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .clip(AppShapesExpressive.pill)
                            .background(
                                if (playing) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant,
                            )
                            .clickable(enabled = !state.busy, onClick = { vm.togglePlayAll() }),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            when {
                                state.busy -> "正在准备…"
                                playing -> "⏸  全部暂停"
                                else -> "▶  全部播放"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = if (playing) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }

            // 多房间卡片已删除：拖拽组网能直接成组，再放一张"组起来"的卡片就是两套入口。
            // 组内状态（谁是从机、属哪台）已经画在各自的设备卡片上。
        }
    }

    // ---- 分组面板（Sonos Grouping 菜单：勾选 + 应用）----
    if (state.groupingOpen) {
        val masterIp = state.groupMasterIp ?: state.selectedIp
        val masterName = state.saved.firstOrNull { it.ip == masterIp }?.title ?: "未选主机"
        AlertDialog(
            onDismissRequest = { vm.closeGrouping() },
            title = { Text("同步播放") },
            text = {
                Column {
                    Text(
                        "主机：$masterName。勾选要一起出声的音箱，应用后主机不变。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    VSpace(Spacing.xs)
                    // 「Everywhere」：Sonos 在 3 台以上才给（两台时"全选"没有额外价值）
                    if (io.github.laozhudu.linkplay.protocol.multiroom.GroupPlan.showEverywhere(state.saved.size)) {
                        TextButton(onClick = { vm.selectEverywhere() }) { Text("全部同步（Everywhere）") }
                    }
                    state.saved.forEach { device ->
                        val isMaster = device.ip == masterIp
                        val checked = isMaster || device.uuid.lowercase() in state.groupingSelected
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !isMaster && state.groupingProgress == null) {
                                    vm.toggleGroupingDevice(device.uuid)
                                }
                                .padding(vertical = Spacing.xs),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                if (checked) "☑" else "☐",
                                style = MaterialTheme.typography.titleMedium,
                                color = if (isMaster) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.width(Spacing.sm))
                            Column(Modifier.weight(1f)) {
                                Text(device.title, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    if (isMaster) "主机（始终同步）" else device.ip,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (state.cards[device.uuid]?.playing == true) {
                                Text("在放", style = MaterialTheme.typography.labelSmall, color = StatusColors.ok)
                            }
                        }
                    }
                    state.groupingProgress?.let {
                        VSpace(Spacing.xs)
                        Text(it, style = MaterialTheme.typography.labelSmall, color = StatusColors.warn)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = state.groupingProgress == null,
                    onClick = { vm.applyGrouping() },
                ) { Text(if (state.groupingProgress == null) "应用" else "处理中…") }
            },
            dismissButton = { TextButton(onClick = { vm.closeGrouping() }) { Text("取消") } },
        )
    }

    // ---- 设备操作菜单（齿轮）：改名 / 删除 / 进设置 ----
    menuTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { menuTarget = null },
            title = { Text(target.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            text = {
                Column {
                    TextButton(onClick = { renameTarget = target; menuTarget = null }) { Text("重命名") }
                    TextButton(onClick = {
                        deleteTarget = target
                        menuTarget = null
                    }) { Text("删除这台音箱") }
                    // 设备内容（原厂 DeviceContentActivity）：预设键 + 固件升级
                    TextButton(onClick = { vm.open(target); onOpenContent(); menuTarget = null }) {
                        Text("设备内容（预设键 / 固件）")
                    }
                    // 定时关闭（原厂设备菜单第四项）：纯客户端倒计时，到点由 App 下发停止
                    TextButton(onClick = { sleepMenu = true; menuTarget = null }) {
                        Text(
                            if (state.sleepTimer.active) {
                                "定时关闭（剩 ${state.sleepTimer.remainingText(state.nowMs)}）"
                            } else {
                                "定时关闭"
                            },
                        )
                    }
                    TextButton(onClick = { onOpenSettings(target); menuTarget = null }) { Text("设备信息 / 设置") }
                }
            },
            confirmButton = { TextButton(onClick = { menuTarget = null }) { Text("关闭") } },
        )
    }

    renameTarget?.let { target ->
        var name by remember(target.uuid) { mutableStateOf(target.name.ifBlank { target.title }) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("重命名") },
            text = {
                Column {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        singleLine = true,
                        label = { Text("音箱名字") },
                    )
                    Text(
                        "组内从机会由主机转达改名；改完会回读设备确认。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.renameCard(target, name)
                    renameTarget = null
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("取消") } },
        )
    }

    if (sleepMenu) {
        AlertDialog(
            onDismissRequest = { sleepMenu = false },
            title = { Text("定时关闭") },
            text = {
                Column {
                    Text(
                        if (state.sleepTimer.active) {
                            "当前还剩 ${state.sleepTimer.remainingText(state.nowMs)}"
                        } else {
                            "到点由 App 下发停止（原厂也是客户端倒计时）"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    VSpace(Spacing.xs)
                    io.github.laozhudu.linkplay.protocol.player.SleepTimer.PRESETS.chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            row.forEach { minutes ->
                                TextButton(onClick = {
                                    vm.setSleepTimer(minutes)
                                    sleepMenu = false
                                }) { Text("$minutes 分钟") }
                            }
                        }
                    }
                    if (state.sleepTimer.active) {
                        TextButton(onClick = {
                            vm.cancelSleepTimer()
                            sleepMenu = false
                        }) { Text("取消定时") }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { sleepMenu = false }) { Text("关闭") } },
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除「${target.title}」？") },
            text = { Text("只删本机这条记录，不影响音箱本身。以后可以重新搜索添加。") },
            confirmButton = {
                TextButton(onClick = {
                    vm.forget(target.uuid)
                    deleteTarget = null
                }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("取消") } },
        )
    }
}

/**
 * 首页拖拽组网的共享状态。
 *
 * 为什么放在页面级而不是每张卡片自己维护：松手要判断"命中了哪台**别的**卡片"，
 * 这需要知道所有卡片的位置。卡片只上报自己的中心点与拖拽意图，判定统一在 [ComposeHit] 里做。
 */
private class DragState {
    var draggingUuid by mutableStateOf<String?>(null)
    var offset by mutableStateOf(Offset.Zero)
    var hoverUuid by mutableStateOf<String?>(null)
    /** 拖拽起点时被拖卡片的中心（根坐标）。之后靠 [offset] 累加算出指尖位置。 */
    var origin by mutableStateOf(Offset.Zero)
}

/**
 * 可拖拽的设备卡片外壳。
 *
 * 手势：**长按 300ms 后拖动**（`detectDragGesturesAfterLongPress`）。
 * 为什么不是"直接拖"：设备卡片本身要能点开（点一下进播放页）、音量条要能拖，
 * 直接拖会把这些手势全抢走。
 *
 * 拖起来之后：
 * - 卡片跟着手指移动并轻微放大，明确"它现在被拿起来了"；
 * - 悬停到另一台上时，那台描一圈高亮，告诉用户"松手就挂到它上面"；
 * - 松手时用指尖位置做命中判定（[DragGrouping.hitTest]，见 core-protocol 的单测）。
 *
 * 长按原来绑定的是"移除记录"，现在改到设备设置页 —— 一个手势不能同时是两件事。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DraggableDeviceCard(
    device: SavedDevice,
    online: Boolean?,
    groupedUnder: String?,
    card: DeviceCard?,
    drag: DragState,
    siblingCount: Int,
    /** 这台是不是当前组的主机。 */
    isGroupMaster: Boolean,
    /** 这台是否已被屏蔽（`mask=1`）。 */
    masked: Boolean,
    /** 设备能力档：通用 DLNA 渲染器不显示组网/声道/屏蔽这些私有入口。 */
    profile: io.github.laozhudu.linkplay.protocol.dlna.DeviceProfile,
    onCenter: (Offset) -> Unit,
    onOpen: () -> Unit,
    onSettings: () -> Unit,
    onVolume: (Int) -> Unit,
    onChannel: () -> Unit,
    onUngroup: () -> Unit,
    onMask: () -> Unit,
    onTogglePlay: () -> Unit,
    onRename: () -> Unit,
    onDropOn: (String) -> Unit,
) {
    val dragging = drag.draggingUuid == device.uuid
    val hovered = drag.hoverUuid == device.uuid && !dragging

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                if (dragging) {
                    translationX = drag.offset.x
                    translationY = drag.offset.y
                    scaleX = 1.03f
                    scaleY = 1.03f
                    shadowElevation = 18f
                }
            }
            .onGloballyPositioned { coords ->
                // 拖拽期间**不更新**自己的中心：卡片被 graphicsLayer 平移后会跟着手指走，
                // 若继续上报，指尖位置 = 中心 + 位移 就会重复累加，命中判定立刻错位
                if (!dragging) {
                    val b = coords.boundsInRoot()
                    onCenter(Offset(b.center.x, b.center.y))
                }
            }
            .then(
                if (siblingCount < 2) Modifier else Modifier.pointerInput(device.uuid, siblingCount) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = {
                            drag.draggingUuid = device.uuid
                            drag.offset = Offset.Zero
                            drag.hoverUuid = null
                            // origin 用"卡片中心 - 指尖起点"对齐：拖拽期间卡片跟手，指尖
                            // 相对卡片的位置始终不变，所以 origin 直接用当前中心即可
                            drag.origin = Offset.Zero
                        },
                        onDrag = { change, amount ->
                            change.consume()
                            drag.offset += amount
                        },
                        onDragEnd = {
                            val target = drag.hoverUuid
                            drag.draggingUuid = null
                            drag.hoverUuid = null
                            drag.offset = Offset.Zero
                            if (target != null) onDropOn(target)
                        },
                        onDragCancel = {
                            drag.draggingUuid = null
                            drag.hoverUuid = null
                            drag.offset = Offset.Zero
                        },
                    )
                }
            )
            // 悬停目标高亮：一层描边，不改变布局尺寸（否则会把下面的卡片推走）
            .then(
                if (hovered) Modifier.border(2.dp, StatusColors.ok, AppShapesExpressive.squircle)
                else Modifier,
            ),
    ) {
        DeviceCardRow(
            device = device,
            online = online,
            groupedUnder = groupedUnder,
            card = card,
            dimmed = dragging,
            // 主机自己也算"在组里"（它的 slave_list 非空）
            inGroup = groupedUnder != null || isGroupMaster,
            isMaster = isGroupMaster,
            masked = masked,
            profile = profile,
            onOpen = onOpen,
            onSettings = onSettings,
            onVolume = onVolume,
            onChannel = onChannel,
            onUngroup = onUngroup,
            onMask = onMask,
            onTogglePlay = onTogglePlay,
            onRename = onRename,
        )
    }

}

/**
 * 设备卡片（按原厂样式重做，同时保留我们自己的信息）。
 *
 * 原厂那张卡的构成，从上到下：
 * ```
 * doss1                                                  ⚙
 * ┌────┐  000.凯叔·三国演义·引子
 * │封面 │
 * └────┘
 * 🔊 ──────●──────                              [R]
 * ```
 * 我们额外保留的（原厂没有，但这是我们更好用的地方）：
 * - 设备 IP / 在线状态 / 「同步播放中 · 属 dossX」—— 组内从机连不上时不会误报离线；
 * - 音量条拖动**立刻**改设备音量（原厂也是这个交互，但原厂没有我们的合并通道，
 *   拖动时会打出一串 HTTP 请求）；
 * - 长按移除记录。
 *
 * 封面：设备不回填元数据（`getPlayerStatus` 的 Title 是 HEX，且没有封面地址），
 * 所以这里不假装有封面 —— 用曲名首字 + 派生色块，和无封面时的通用做法一致。
 */
@Composable
private fun DeviceCardRow(
    device: SavedDevice,
    online: Boolean?,
    groupedUnder: String?,
    card: DeviceCard?,
    /** 正被拖起来时压暗，让"另一台上的高亮"更显眼。 */
    dimmed: Boolean = false,
    /** 在组里（主机或从机）—— 声道标签才可点。 */
    inGroup: Boolean = false,
    /** 主机（不能屏蔽、不能改声道）。 */
    isMaster: Boolean = false,
    /** 已被屏蔽（`mask=1`，不出声但留在组里）。 */
    masked: Boolean = false,
    /** 设备能力档（默认 UNKNOWN：按 LinkPlay 显示，保持既有行为不变）。 */
    profile: io.github.laozhudu.linkplay.protocol.dlna.DeviceProfile =
        io.github.laozhudu.linkplay.protocol.dlna.DeviceProfile.UNKNOWN,
    onOpen: () -> Unit,
    onSettings: () -> Unit,
    onVolume: (Int) -> Unit,
    onChannel: () -> Unit = {},
    onUngroup: () -> Unit = {},
    onMask: () -> Unit = {},
    onTogglePlay: () -> Unit = {},
    /** 齿轮：打开这台设备的操作菜单。 */
    onRename: () -> Unit = {},
) {
    val palette = remember(device.uuid) { seedOf(device.name, device.ip) }
    // 拖动时的本地乐观值：不这样会出现"手指在动、旋钮不动"（要等 3 秒轮询回读）
    var dragging by remember(device.uuid) { mutableStateOf<Int?>(null) }
    var confirmUngroup by remember(device.uuid) { mutableStateOf(false) }
    val volume = dragging ?: card?.volume ?: 0
    val channel = card?.channel
    val title = card?.title

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppShapesExpressive.squircle)
            .background(
                Brush.linearGradient(listOf(palette.top, palette.accent.copy(alpha = 0.28f))),
            )
            .graphicsLayer { alpha = if (dimmed) 0.85f else 1f }
            .clickable(onClick = onOpen)
            .padding(Spacing.md),
    ) {
        // 第一行：设备名 + 状态 + 齿轮
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    device.title,
                    style = MaterialTheme.typography.titleLarge,
                    // 卡片底是派生深色渐变，文字必须恒定白：跟主题变会在浅色模式下隐形
                    color = OnDarkPalette.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        listOfNotNull(
                            device.ip,
                            groupedUnder?.let { "属 $it" }
                                ?: when (online) {
                                    true -> "在线"
                                    false -> "离线"
                                    null -> null
                                },
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = OnDarkPalette.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    // 电量（原厂卡片上就有）：独立设备取 getStatusEx.battery_percent，
                    // 组内从机取主机转述的 slave_list[].battery
                    card?.battery?.let { pct ->
                        Spacer(Modifier.width(Spacing.xs))
                        Text(
                            "🔋$pct%",
                            style = MaterialTheme.typography.labelSmall,
                            color = OnDarkPalette.muted,
                            maxLines = 1,
                        )
                    }
                }
            }
            // 类型标签：必须让人一眼看出"这台是音箱还是电视盒子"——
            // 两类设备能做的事不同（盒子没有组网/声道/预设）
            if (profile == io.github.laozhudu.linkplay.protocol.dlna.DeviceProfile.GENERIC_DMR) {
                PillTag(text = "通用 DLNA", color = OnDarkPalette.muted)
                Spacer(Modifier.width(Spacing.xs))
            }
            if (groupedUnder != null) {
                PillTag(text = "组内", color = StatusColors.ok)
                Spacer(Modifier.width(Spacing.xs))
            } else if (online == false) {
                PillTag(text = "离线", color = StatusColors.error)
                Spacer(Modifier.width(Spacing.xs))
            }
            // 齿轮：原厂在卡片右上角。点开是这台设备的操作菜单（改名 / 删除 / 进设置），
            // 对应原厂长按卡片弹出的那个菜单 —— 长按现在是拖拽组网，菜单挪到齿轮上
            CircleIcon(onClick = onRename, size = 36.dp) {
                Text("⚙", style = MaterialTheme.typography.titleMedium, color = OnDarkPalette.text)
            }
        }

        VSpace(Spacing.sm)

        // 第二行：缩略图 + 曲名（原厂的"正在播放"条）
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(AppShapesExpressive.squircle)
                    .background(OnDarkPalette.chip),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    title?.trim()?.firstOrNull()?.toString()?.uppercase() ?: "♪",
                    style = MaterialTheme.typography.titleMedium,
                    color = OnDarkPalette.text,
                )
            }
            Spacer(Modifier.width(Spacing.sm))
            Column(Modifier.weight(1f)) {
                Text(
                    title ?: if (online == false) "设备无响应" else "空闲中 · 点开挑一首",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (title != null) OnDarkPalette.text else OnDarkPalette.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val sub = when {
                    card == null -> null
                    card.plicount > 0 -> "${card.plicount} 首 · 第 ${card.plicurr.coerceAtLeast(1)} 首"
                    else -> "没有播放列表"
                }
                if (sub != null) {
                    Text(
                        sub,
                        style = MaterialTheme.typography.labelSmall,
                        color = OnDarkPalette.muted,
                        maxLines = 1,
                    )
                }
            }
        }

        VSpace(Spacing.xs)

        // 第三行：音量 + 声道（原厂就是「喇叭图标 + 滑条 + R/LR」）
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🔊", style = MaterialTheme.typography.bodySmall, color = OnDarkPalette.muted)
            Spacer(Modifier.width(Spacing.xs))
            Slider(
                value = volume.toFloat(),
                onValueChange = {
                    val v = it.toInt().coerceIn(0, 100)
                    dragging = v
                    onVolume(v)
                },
                onValueChangeFinished = { dragging = null },
                valueRange = 0f..100f,
                modifier = Modifier.weight(1f).height(28.dp),
                colors = SliderDefaults.colors(
                    thumbColor = OnDarkPalette.text,
                    activeTrackColor = OnDarkPalette.text.copy(alpha = 0.85f),
                    inactiveTrackColor = OnDarkPalette.muted.copy(alpha = 0.30f),
                ),
            )
            Spacer(Modifier.width(Spacing.xs))
            Text(
                if (card?.mute == true) "静音" else volume.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = OnDarkPalette.muted,
                modifier = Modifier.width(30.dp),
                textAlign = TextAlign.End,
            )
            // 卡片内的播放/暂停：媒体惯例显示"将发生的动作"。
            // 组内从机不显示 —— 它跟随主机，单独下发是无效命令（点了没反应最伤人）。
            if (!(inGroup && !isMaster)) {
                TextButton(onClick = onTogglePlay, contentPadding = PaddingValues(horizontal = 6.dp)) {
                    Text(
                        if (card?.playing == true) "⏸" else "▶",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (card?.playing == true) StatusColors.ok else OnDarkPalette.text,
                    )
                }
            }
            // 屏蔽 / 解除：只有 LinkPlay 从机才有意义。通用 DLNA 渲染器整段不渲染
            // （项目铁律：不给按了没反应的按钮）
            if (profile != io.github.laozhudu.linkplay.protocol.dlna.DeviceProfile.GENERIC_DMR) {
                if (inGroup && !isMaster) {
                    TextButton(onClick = onMask, contentPadding = PaddingValues(horizontal = 6.dp)) {
                        Text(
                            if (masked) "恢复" else "屏蔽",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (masked) StatusColors.warn else OnDarkPalette.muted,
                        )
                    }
                }
                if (inGroup) {
                    // 解除要二次确认：它会让从机离开当前 Wi-Fi（真机 3 秒后才回来），
                    // 误触一次就是"音箱不见了"的体验
                    TextButton(
                        onClick = { confirmUngroup = true },
                        contentPadding = PaddingValues(horizontal = 6.dp),
                    ) {
                        Text("解除", style = MaterialTheme.typography.labelSmall, color = OnDarkPalette.muted)
                    }
                }
            }
            if (profile == io.github.laozhudu.linkplay.protocol.dlna.DeviceProfile.GENERIC_DMR) {
                // 通用渲染器没有声道概念：这一格留空，而不是画一个假的 LR
                return@Row
            }
            // 声道标签：0 立体声 / 1 仅左 / 2 仅右。
            // **可以点**：组内从机点一下就在 LR → L → R 之间循环，立刻下发
            // `multiroom:SlaveChannel:<从机IP>:<n>`（真机验证过回读生效）。
            // 不在组里的设备点了给一句说明，不装作能改。
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(
                        if (inGroup) OnDarkPalette.text.copy(alpha = 0.20f)
                        else OnDarkPalette.text.copy(alpha = 0.10f),
                    )
                    .clickable(onClick = onChannel),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    when (channel) {
                        1 -> "L"
                        2 -> "R"
                        else -> "LR"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (inGroup) OnDarkPalette.text else OnDarkPalette.muted,
                )
            }
        }
    }

    // 解除确认：从机会离开当前 Wi-Fi（真机约 3 秒回来），误触的代价是"音箱不见了"
    if (confirmUngroup) {
        AlertDialog(
            onDismissRequest = { confirmUngroup = false },
            title = { Text("解除「${device.title}」的同步？") },
            text = {
                Text(
                    if (isMaster) "会把组内所有从机都解除，它们会各自回到原来的 Wi-Fi。"
                    else "这台会退出同步并回到原来的 Wi-Fi，大约 3 秒后重新出现。",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmUngroup = false
                    onUngroup()
                }) { Text("解除") }
            },
            dismissButton = { TextButton(onClick = { confirmUngroup = false }) { Text("取消") } },
        )
    }
}

/**
 * 迷你播放条：显示「在放什么」+ 播放/暂停 + 点开。
 *
 * ## 为什么现在加播放/暂停
 *
 * 原来这里刻意不放控制（"所有控制都在播放页一面"）。但用户从首页按「全部播放」之后
 * 想停一下，必须先进播放页 —— 这条路径太绕。
 *
 * 状态只有一个来源：`QueueBus` / `DeviceCard.playing`，迷你条只读不存，
 * 所以不会出现"两处各记一份、迟早不一致"的问题。
 */
@Composable
private fun MiniPlayerBar(
    queue: QueueRunState,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    /** 当前是否在放（来自设备回报，不本地记）。 */
    playing: Boolean = false,
    onToggle: () -> Unit = {},
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
        // 播放/暂停：与首页「全部播放」配套，不用为了停一下再进播放页
        CircleIcon(onClick = onToggle, size = 40.dp) {
            if (playing) {
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Box(Modifier.size(4.dp, 14.dp).background(MaterialTheme.colorScheme.onSurface))
                    Box(Modifier.size(4.dp, 14.dp).background(MaterialTheme.colorScheme.onSurface))
                }
            } else {
                Text("▶", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            }
        }
        Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
