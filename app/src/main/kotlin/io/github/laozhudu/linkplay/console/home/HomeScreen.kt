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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.laozhudu.linkplay.console.ConsoleUiState
import io.github.laozhudu.linkplay.console.ConsoleViewModel
import io.github.laozhudu.linkplay.console.diagnostics.NavTrace
import io.github.laozhudu.linkplay.console.home.DeviceCardRow
import io.github.laozhudu.linkplay.console.ui.components.AppCard
import io.github.laozhudu.linkplay.console.ui.components.AppShapesExpressive
import io.github.laozhudu.linkplay.console.ui.components.CircleIcon
import io.github.laozhudu.linkplay.console.ui.components.EmptyState
import io.github.laozhudu.linkplay.console.ui.components.SectionTitle
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
    onOpenPlayer: () -> Unit = {},
    /** 诊断页（顶栏 ◔ / 崩溃横幅都进这里）。 */
    onOpenDiagnostics: () -> Unit = {},
) {
    val extras = LocalExtraColors.current
    var showAdd by remember { mutableStateOf(false) }
    var manualIp by remember { mutableStateOf("") }

    // 齿轮点开的设备操作菜单（改名 / 删除 / 进设置）
    var menuTarget by remember { mutableStateOf<SavedDevice?>(null) }
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
            // 诊断入口：**常驻**在首页顶栏。
            // 为什么不只放设置页：闪退之后用户第一件事是回到首页，多一层点击就多一次劝退；
            // 而且有了它，任何"不对劲"都能立刻截图给我，不用描述现象。
            Spacer(Modifier.width(Spacing.xs))
            CircleIcon(onClick = onOpenDiagnostics, size = 40.dp) {
                Text(
                    if (state.lastCrashAtStart != null) "⚠" else "◔",
                    style = MaterialTheme.typography.titleLarge,
                    color = if (state.lastCrashAtStart != null) StatusColors.warn
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
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
                // 组级别的退出入口：以前只有一个藏在每张卡片里的小字「解除」，
                // 而且它的显示条件是"界面认为这台在组里"——认知一错位，入口就彻底消失。
                // 组的信息条本身就该能操作，Sonos/Cast 的组行都是这么做的。
                if (state.groupSize > 1) {
                    val master = state.saved.firstOrNull { it.ip == state.groupMasterIp }
                    TextButton(
                        onClick = { master?.let { vm.ungroupCard(it) } },
                        contentPadding = PaddingValues(horizontal = 8.dp),
                    ) {
                        Text(
                            "退出同步",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
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
                // 组网入口：勾选式（Sonos Grouping 菜单）—— 唯一的组网方式
                CircleIcon(onClick = { vm.openGrouping() }, size = 44.dp) {
                    Text("⧉", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (state.saved.size >= 2) {
            Text(
                "点 ⧉ 勾选要同步的音箱",
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
                DeviceCardRow(
                    device = device,
                    online = state.online[device.uuid],
                    groupedUnder = state.groupedUnder[UuidMatch.key(device.uuid)],
                    card = state.cards[device.uuid],
                    // 主机自己也算"在组里"（它的 slave_list 非空）
                    inGroup = state.groupedUnder[UuidMatch.key(device.uuid)] != null ||
                        (state.groupMasterIp != null && device.ip == state.groupMasterIp),
                    isMaster = state.groupMasterIp != null && device.ip == state.groupMasterIp,
                    masked = UuidMatch.find(device.uuid, state.groupMembers) { it.uuid }?.masked == true,
                    profile = state.cards[device.uuid]?.profile
                        ?: io.github.laozhudu.linkplay.protocol.dlna.DeviceProfile.UNKNOWN,
                    capabilities = state.cards[device.uuid]?.capabilities
                        ?: io.github.laozhudu.linkplay.protocol.dlna.DeviceCapabilities.LINKPLAY,
                    onOpen = { onOpenDevice(device) },
                    onSettings = { onOpenSettings(device) },
                    onVolume = { vm.setCardVolume(device, it) },
                    onSetChannel = { ch -> vm.setCardChannel(device, ch) },
                    onUngroup = { vm.ungroupCard(device) },
                    onMask = { vm.toggleMask(device) },
                    onTogglePlay = { vm.toggleDevicePlay(device) },
                    onRename = { menuTarget = device },
                )
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
