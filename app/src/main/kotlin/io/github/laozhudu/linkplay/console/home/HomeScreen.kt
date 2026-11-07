package io.github.laozhudu.linkplay.console.home

import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import io.github.laozhudu.linkplay.console.ui.apple.InsetGroup
import io.github.laozhudu.linkplay.console.ui.apple.LargeTitle
import io.github.laozhudu.linkplay.console.ui.apple.ListRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.laozhudu.linkplay.console.ConsoleUiState
import io.github.laozhudu.linkplay.console.ConsoleViewModel
import io.github.laozhudu.linkplay.console.diagnostics.NavTrace
import io.github.laozhudu.linkplay.console.ui.components.AppCard
import io.github.laozhudu.linkplay.console.ui.components.AppShapesExpressive
import io.github.laozhudu.linkplay.console.ui.components.EmptyState
import io.github.laozhudu.linkplay.console.ui.components.VSpace
import io.github.laozhudu.linkplay.console.ui.theme.LocalExtraColors
import io.github.laozhudu.linkplay.console.ui.theme.Spacing
import io.github.laozhudu.linkplay.console.ui.theme.StatusColors
import io.github.laozhudu.linkplay.discovery.DiscoveredDevice
import io.github.laozhudu.linkplay.protocol.identity.UuidMatch
import io.github.laozhudu.linkplay.protocol.session.SavedDevice

/**
 * 「设备」页：已保存的音箱 + 添加入口。
 *
 * 只做三件事，按使用频率排：
 * 1. **已保存的音箱**（主入口）—— 打开 App 一眼点开，不用每次等扫描；
 * 2. 添加设备（低频，收在折叠区）；
 * 3. 组网走 ⧉ 勾选（唯一方式，见 Sonos Grouping 菜单的取舍）。
 *
 * 为什么不再把「搜到的所有 UPnP 设备」铺出来：局域网里 NAS、路由、电视都会响应 SSDP，
 * 全列出来分不清哪台是音箱，点错还会往路由器投流。现在只收 LinkPlay 系
 * （判据见 `LinkPlayIdentity`），其余过滤并给出统计。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun HomeScreen(
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
    /** 诊断页（顶栏 ◔ / 崩溃横幅都进这里）。 */
    onOpenDiagnostics: () -> Unit = {},
    onOpenRemote: () -> Unit = {},
    /** 点「本机」行后进播放页（那里才有本机播放的完整控制）。 */
    onOpenPlayer: () -> Unit = {},
) {
    val extras = LocalExtraColors.current
    var showAdd by remember { mutableStateOf(false) }
    var manualIp by remember { mutableStateOf("") }

    // 齿轮点开的设备操作菜单（改名 / 删除 / 进设置）
    var menuTarget by remember { mutableStateOf<SavedDevice?>(null) }
    // 「遥控设备」两组都是占位（码库未接 / 蓝牙未实现），点了给一句实话。
    val context = LocalContext.current
    var remoteHint by remember { mutableStateOf<String?>(null) }

    // 当前播放目标（`docs/96` 模型 A：唯一真值）。设备页每一行据此标「正在用」。
    val targetFlow = io.github.laozhudu.linkplay.console.playback.PlaybackOutputStore.current
        .collectAsState()
    val target = targetFlow.value
    val targetIsLocal = target is io.github.laozhudu.linkplay.console.playback.PlaybackOutput.Local
    val targetIp = (target as? io.github.laozhudu.linkplay.console.playback.PlaybackOutput.Device)
        ?.ip?.takeIf { it.isNotEmpty() }
    var sleepMenu by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<SavedDevice?>(null) }
    var deleteTarget by remember { mutableStateOf<SavedDevice?>(null) }
    /**
     * 把"哪个对话框开着"同步到诊断快照。
     *
     * 优先级有意：菜单/改名/删除/睡眠这类**小窗**排在分组面板前面，
     * 因为它们会盖在分组面板之上；音量浮层不在这里（它在更外层，见 VolumeOsdHost）。
     */
    LaunchedEffect(showAdd, state.groupingOpen, menuTarget, sleepMenu, renameTarget, deleteTarget) {
        NavTrace.overlay = when {
            renameTarget != null -> "改名"
            deleteTarget != null -> "删除确认"
            sleepMenu -> "睡眠定时"
            menuTarget != null -> "设备菜单"
            state.groupingOpen -> "分组面板"
            showAdd -> "添加音箱"
            else -> null
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = Spacing.lg)) {
        VSpace(Spacing.md)
        // 大标题按定稿：**「设备」**，不是「我的音箱」。
        //
        // 原来这里写「我的音箱」，下面分组标题又写一次「我的音箱」—— 一屏出现两遍。
        // 定稿的大标题是格名（设备），分组标题才是设备类别（我的音箱 / 其它播放设备）。
        LargeTitle(
            title = "设备",
            subtitle = when {
                state.busy -> "正在连接…"
                state.saved.isEmpty() -> "还没有添加"
                else -> "${state.saved.count { state.online[it.uuid] != false }} 台在线"
            },
        )

        // ---------- 崩溃横幅 ----------
        // 上次运行崩过就顶一条红卡：点开进诊断页，能看到完整堆栈。
        // 系统那个"因自身空指针问题"的弹窗没有任何定位信息，这条才是能用来改代码的东西。
        state.lastCrashAtStart?.let { crash ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.sm)
                    .clip(AppShapesExpressive.squircle)
                    .background(StatusColors.error.copy(alpha = 0.14f))
                    .clickable(onClick = onOpenDiagnostics)
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("⚠", style = MaterialTheme.typography.titleLarge, color = StatusColors.error)
                Spacer(Modifier.width(Spacing.sm))
                Column(Modifier.weight(1f)) {
                    Text(
                        "上次运行闪退了",
                        style = MaterialTheme.typography.titleSmall,
                        color = StatusColors.error,
                    )
                    // 直接把异常类+消息贴出来：用户截图这一条就够我判断方向
                    Text(
                        crash.lineSequence()
                            .firstOrNull { it.startsWith("异常：") }
                            ?.removePrefix("异常：")
                            ?.let { cls ->
                                val msg = crash.lineSequence().firstOrNull { it.startsWith("消息：") }
                                    ?.removePrefix("消息：").orEmpty()
                                if (msg.isBlank() || msg == "null") cls else "$cls: $msg"
                            }
                            ?: "点开查看完整堆栈",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text("查看 ›", style = MaterialTheme.typography.labelLarge, color = StatusColors.error)
            }
        }


        VSpace(Spacing.md)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            /*
             * 顶部那条红色横幅删掉了。
             *
             * 原因（真机截图）：`ConnectException: Failed to connect to /192.168.99.175:80`
             * 挂在页顶，而用户当前选的是 doss1 —— 那条错误是切去 E900V22C 时留下的。
             * 一条属于某台设备的错误挂在页面顶部，看起来像整个 App 坏了。
             *
             * 现在分两种：
             * - **有归属**（errorIp != null）→ 显示在那台设备行上（红色副标题）
             * - **无归属**（比如"名字不能为空"）→ 一行小字提示，不带卡片样式
             */
            state.error?.takeIf { state.errorIp == null }?.let { err ->
                item {
                    Text(
                        err,
                        style = MaterialTheme.typography.labelMedium,
                        color = StatusColors.error,
                    )
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

            // ---- 按能力分组（docs/92 U2）----
            //
            // 用户原话：「通用 upnp 设备根本就没考虑过与 doss 隔离，反而混在一起」。
            //
            // 这两类能力差一个量级：音箱有设备侧队列、能组网、有电量固件；
            // 通用渲染器（电视盒子）只能 Play/Pause/SetVolume/Seek。
            // 混排的结果是"四台看起来一样的设备"，而其中一台一半的入口点不动。
            //
            // 按**能力**分而不是按品牌分：型号标签会随固件变，能力是探测出来的。
            val byGroup = state.saved.groupBy { dev ->
                (state.cards[dev.uuid]?.capabilities
                    ?: io.github.laozhudu.linkplay.protocol.dlna.DeviceCapabilities.LINKPLAY).group
            }
            io.github.laozhudu.linkplay.protocol.dlna.DeviceGroup.entries.forEach { group ->
                val groupDevices = byGroup[group].orEmpty()
                if (groupDevices.isEmpty()) return@forEach
                // ---- 「本机」行（`docs/96` 模型 A）----
                //
                // 大厂都把本机当成目标列表里**平等的一项**（Spotify 的 "This phone"、
                // AirPlay 的 "This iPhone"）。我们原来没有 —— 本机只存在于输出口枚举里，
                // 设备页根本看不到"手机自己放"这个选项。
                //
                // 插在第一组之前：它不属于"我的音箱"也不属于"其它播放设备"。
                // 「本机」只插一次 —— 在**第一个非空分组**之前
                val firstNonEmpty = io.github.laozhudu.linkplay.protocol.dlna.DeviceGroup.entries
                    .firstOrNull { !byGroup[it].isNullOrEmpty() }
                if (group == firstNonEmpty) {
                    item(key = "local-output") {
                        DeviceRowApple(
                            name = "本机",
                            online = true,
                            statusText = "这台手机自己放",
                            volume = 0,
                            inUse = targetIsLocal,
                            onClick = {
                                io.github.laozhudu.linkplay.console.playback.PlaybackOutputStore.write(
                                    context,
                                    io.github.laozhudu.linkplay.console.playback.PlaybackOutput.Local,
                                )
                                io.github.laozhudu.linkplay.console.playback.PlaybackService
                                    .restart(context)
                                onOpenPlayer()
                            },
                            onVolumeCommit = {},
                            onLongClick = {},
                        )
                    }
                }

                item(key = "hdr-${group.name}") { DeviceGroupHeader(group) }
                items(groupDevices, key = { it.uuid }) { device ->
                    val card = state.cards[device.uuid]
                    val isOnline = state.online[device.uuid]
                    val inGroup = state.groupedUnder[UuidMatch.key(device.uuid)] != null ||
                        (state.groupMasterIp != null && device.ip == state.groupMasterIp)
                    // 一行只放三件事：在不在 / 在放什么 / 音量。
                    // 电量、固件、声道、预设全部进长按菜单或详情页（用户原话：
                    // 「用户不需要知道全部设备信息，只需要能用、好用、简单状态就够」）。
                    DeviceRowApple(
                        name = device.name.ifBlank { device.ip },
                        online = isOnline,
                        statusText = deviceStatusText(card?.title, card?.playing, isOnline, inGroup, card?.battery),
                        volume = card?.volume ?: 0,
                        onClick = { onOpenDevice(device) },
                        onVolumeCommit = { vm.setCardVolume(device, it) },
                        onLongClick = { menuTarget = device },
                        inUse = targetIp == device.ip,
                        // 通用 UPnP 渲染器（电视盒子）常关着，或它的控制端口没开 ——
                        // 那属于**正常状态**，不是故障。用户原话：
                        // 「通用 upnp 的 80 端口没开放，所以永远报错」。
                        // 所以这类设备只在**离线**就够了，不再甩一条 ConnectException 上去。
                        errorText = state.error
                            ?.takeIf { state.errorIp == device.ip }
                            ?.takeIf {
                                state.cards[device.uuid]?.capabilities?.group !=
                                    io.github.laozhudu.linkplay.protocol.dlna.DeviceGroup.OtherPlayer
                            },
                    )
                }
            }

            // ---- 「遥控设备」分组（docs/93 屏 1）----
            //
            // **这是占位。** 红外码库没接（`IrRemoteControl` 只有一条探测码）、蓝牙零代码。
            // 为什么还是画出来：用户明确要求「红外、蓝牙、wifi 等遥控要体现在 UI 上」，
            // 而结构位置本身是信息 —— 他知道以后在哪。
            //
            // 底线是**不骗人**：副标题直写「未接入」，点了给一句实话，
            // 而不是画一个按下去没反应的遥控器（那是本项目最忌的失败模式）。
            item {
                InsetGroup(title = "遥控设备") {
                    ListRow(
                        title = "添加红外设备",
                        subtitle = "码库未接入（电视 / 空调）",
                        leading = {
                            Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) {
                                Text("⌁", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        },
                        showChevron = true,
                        onClick = onOpenRemote,
                    )
                    ListRow(
                        title = "添加蓝牙设备",
                        subtitle = "未实现（遥控 / 耳机）",
                        leading = {
                            Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) {
                                Text("ᛒ", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        },
                        showChevron = true,
                        onClick = {
                            remoteHint = "蓝牙遥控还是零代码（docs/84 只有调研）。\n" +
                                "接口层已经建好（RemoteControl），加实现时遥控页不用改。"
                        },
                        showDivider = false,
                    )
                }
            }

            // ---- 「发现」分组：**常驻**（docs/93 屏 1）----
            //
            // 定稿里它一直在，不用先点"添加音箱"。因为"附近有什么"是设备页的
            // 基本信息，不该藏在一个开关后面。
            item {
                InsetGroup(title = "发现") {
                    if (state.devices.isNotEmpty()) {
                        state.devices.take(3).forEachIndexed { index, d ->
                            ListRow(
                                title = d.friendlyName ?: d.friendlySsid ?: d.ip,
                                // 副标题带上"这是什么东西"：加音箱和加电视盒子，
                                // 后面能用的功能差一个量级，不能让用户猜
                                subtitle = listOfNotNull(
                                    d.ip,
                                    state.scanKinds[d.ip],
                                ).joinToString(" · "),
                                trailing = {
                                    Text(
                                        "添加",
                                        color = MaterialTheme.colorScheme.primary,
                                        style = MaterialTheme.typography.labelLarge,
                                    )
                                },
                                onClick = { onAddDiscovered(d) },
                                showDivider = true,
                            )
                        }
                    }
                    ListRow(
                        title = if (state.scanning) "正在搜索局域网…" else "扫描附近设备",
                        subtitle = when {
                            state.scanning -> null
                            state.devices.isNotEmpty() -> "已发现 ${state.devices.size} 台"
                            else -> "点一下开始扫描"
                        },
                        leading = {
                            Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    if (state.scanning) "…" else "◎",
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.titleMedium,
                                )
                            }
                        },
                        onClick = if (state.scanning) null else onScan,
                        // 下面还有一行「手动填 IP」，所以这里要画分隔线
                        showDivider = true,
                    )
                    // 手动添加 / 配网：定稿图里没有，但它们没有别的地方可去
                    // （原来挂在「添加音箱」那个大按钮后面）。压成发现组里的最后一行，
                    // 频次低但必须存在 —— 一个扫不到的老音箱只能靠手动填 IP。
                    ListRow(
                        title = if (showAdd) "收起手动添加" else "扫不到？手动填 IP / 配网",
                        showChevron = true,
                        onClick = { showAdd = !showAdd },
                        showDivider = false,
                    )
                }
            }

            if (showAdd) {
                if (state.filteredOutCount > 0) {
                    item {
                        Text(
                            "已忽略 ${state.filteredOutCount} 个非音箱设备（电视 / 路由 / NAS）",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 16.dp, top = 6.dp, end = 16.dp),
                        )
                    }
                }
                item {
                    InsetGroup(title = "或") {
                        // 这一行**不能**用 ListRow：它是输入框，不是可点行。
                        // （第一版我改成 ListRow 就把输入能力弄丢了 —— 幸好编译期先撞到图标问题）
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            OutlinedTextField(
                                value = manualIp,
                                onValueChange = { manualIp = it },
                                placeholder = { Text("手动填 IP", style = MaterialTheme.typography.bodyMedium) },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(Modifier.width(Spacing.sm))
                            TextButton(
                                onClick = { onAddManual(manualIp); manualIp = "" },
                                enabled = manualIp.isNotBlank(),
                            ) { Text("添加") }
                        }
                        ListRow(
                            title = "新音箱？走 AP 直连配网",
                            showChevron = true,
                            onClick = onProvision,
                            showDivider = false,
                        )
                    }
                }
            }

            // 原来这里有一个「全部播放」大按钮 —— **按定稿删掉了**。
            //
            // 定稿设备页只管"有哪些设备、各自什么状态"，播放控制在播放页。
            // 而且它和顶上那条聚合播放条、以及底部常驻播放条**三个入口干同一件事**，
            // 用户会犹豫点哪个。现在设备页不再承担播放动作。

            // 多房间卡片已删除：勾选式组网（⧉）能直接成组，再放一张"组起来"的卡片就是两套入口。
            // 组内状态（谁是从机、属哪台）已经画在各自的设备卡片上。
        }
    }

    // ---- 分组面板（Sonos Grouping 菜单：勾选 + 应用）----
    if (state.groupingOpen) {
        val masterIp = state.groupMasterIp ?: state.selectedIp
        val masterName = state.saved.firstOrNull { it.ip == masterIp }?.title ?: "未选主机"
        AlertDialog(
            onDismissRequest = { vm.closeGrouping() },
            // 字体按用户要求放大一档：这个面板是要"读"的（勾选哪几台、谁是主机），
            // 原来的 labelSmall 在手机上基本靠猜
            title = { Text("同步播放", style = MaterialTheme.typography.headlineSmall) },
            text = {
                Column {
                    Text(
                        "主机：$masterName。勾选要一起出声的音箱，应用后主机不变。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    VSpace(Spacing.xs)
                    // 「Everywhere」：Sonos 在 3 台以上才给（两台时"全选"没有额外价值）
                    if (io.github.laozhudu.linkplay.protocol.multiroom.GroupPlan.showEverywhere(state.saved.size)) {
                        TextButton(onClick = { vm.selectEverywhere() }) {
                            Text("全部同步（Everywhere）", style = MaterialTheme.typography.bodyLarge)
                        }
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
                                style = MaterialTheme.typography.headlineSmall,
                                color = if (isMaster) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.width(Spacing.sm))
                            Column(Modifier.weight(1f)) {
                                Text(device.title, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    if (isMaster) "主机（始终同步）" else device.ip,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (state.cards[device.uuid]?.playing == true) {
                                Text("在放", style = MaterialTheme.typography.bodySmall, color = StatusColors.ok)
                            }
                        }
                    }
                    state.groupingProgress?.let {
                        VSpace(Spacing.xs)
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = StatusColors.warn)
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
                    // 组合同步：勾选式组网（Sonos Grouping）。原来是顶上那条聚合播放条里的 ⧉，
                    // 那条按定稿删掉之后这里必须补上 —— 否则「三台一起响」就没有入口了。
                    TextButton(onClick = { vm.openGrouping(); menuTarget = null }) {
                        Text("组合同步（勾选其它音箱）")
                    }
                    // 以下四项原来在卡片上，现在收进长按菜单（苹果的 context menu）
                    val card = state.cards[target.uuid]
                    TextButton(onClick = {
                        vm.toggleDevicePlay(target)
                        menuTarget = null
                    }) { Text(if (card?.playing == true) "暂停这台" else "播放这台") }
                    TextButton(onClick = {
                        vm.setCardChannel(target, nextChannel(state.cards[target.uuid]?.channel ?: 0))
                        menuTarget = null
                    }) {
                        Text("声道：${channelLabel(card?.channel ?: 0)}")
                    }
                    TextButton(onClick = {
                        vm.toggleMask(target)
                        menuTarget = null
                    }) { Text("遮罩（本机静音，不改设备）") }
                    if (state.groupedUnder[UuidMatch.key(target.uuid)] != null) {
                        TextButton(onClick = {
                            vm.ungroupCard(target)
                            menuTarget = null
                        }) { Text("退出同步") }
                    }
                    TextButton(onClick = { onOpenSettings(target); menuTarget = null }) { Text("设备信息 / 设置") }
                }
            },
            confirmButton = { TextButton(onClick = { menuTarget = null }) { Text("关闭") } },
        )
    }

    // 「遥控设备」占位说明 —— 说清为什么现在还不能用，而不是让用户猜
    remoteHint?.let { hint ->
        AlertDialog(
            onDismissRequest = { remoteHint = null },
            title = { Text("还没接好") },
            text = { Text(hint) },
            confirmButton = { TextButton(onClick = { remoteHint = null }) { Text("知道了") } },
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
 * 设备分组标题。
 *
 * 文案写在 UI 层，不写进 `core-protocol` 的枚举 —— 协议模块不该带中文文案。
 */
@Composable
private fun DeviceGroupHeader(group: io.github.laozhudu.linkplay.protocol.dlna.DeviceGroup) {
    val label = when (group) {
        io.github.laozhudu.linkplay.protocol.dlna.DeviceGroup.MySpeaker -> "我的音箱"
        io.github.laozhudu.linkplay.protocol.dlna.DeviceGroup.OtherPlayer -> "其它播放设备"
        io.github.laozhudu.linkplay.protocol.dlna.DeviceGroup.Other -> "其它设备"
    }
    // 只留一个标题。
    //
    // 原来下面还有一行"能收整份列表、能组网"之类的说明 —— 删掉了。
    // 用户原话：「用户不需要知道全部设备信息，用户只需要能用，好用，简单状态就够」。
    // 那一行是**给开发看的**（它解释的是代码里怎么分组的），放在界面上只是噪音。
    Text(
        label,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Spacing.md, bottom = Spacing.xs),
    )
}

/**
 * 一行状态文案。
 *
 * 只有三档信息：在不在 / 在放什么 / 电量（离线时才值得说）。
 * **不写"能组网""支持 xx 协议"** —— 那些是排查信息，进详情页。
 */
private fun deviceStatusText(
    title: String?,
    playing: Boolean?,
    online: Boolean?,
    inGroup: Boolean,
    battery: Int?,
): String = when {
    online == false -> "离线"
    inGroup && title.isNullOrBlank() -> "同步中"
    !title.isNullOrBlank() && playing == true -> "在放《$title》"
    // 暂停时**只写「暂停」**，不拼歌名。
    // 原来写「暂停 · 046【秦】揭竿而起」—— 一行里塞两件事，屏窄时歌名被截成
    // 「046【秦】揭手而…」，既认不出是哪首，也让"暂停"这个状态挤没了。
    // 定稿这里就是两个字。歌名去播放页看。
    !title.isNullOrBlank() -> "暂停"
    inGroup -> "同步中"
    battery != null && battery <= 20 -> "待播放 · 电量 $battery%"
    else -> "待播放"
}

/** 声道三档循环：立体声 → 仅左 → 仅右 → 立体声。 */
private fun nextChannel(current: Int): Int = when (current) {
    0 -> 1
    1 -> 2
    else -> 0
}

private fun channelLabel(channel: Int): String = when (channel) {
    1 -> "仅左"
    2 -> "仅右"
    else -> "立体声"
}
