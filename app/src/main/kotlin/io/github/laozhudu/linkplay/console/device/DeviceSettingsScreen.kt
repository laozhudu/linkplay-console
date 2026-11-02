package io.github.laozhudu.linkplay.console.device

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.laozhudu.linkplay.console.ConsoleUiState
import io.github.laozhudu.linkplay.console.ConsoleViewModel
import io.github.laozhudu.linkplay.console.ui.components.AppCard
import io.github.laozhudu.linkplay.console.ui.components.BackGlyph
import io.github.laozhudu.linkplay.console.ui.components.PillTag
import io.github.laozhudu.linkplay.console.ui.components.SectionTitle
import io.github.laozhudu.linkplay.console.ui.components.VSpace
import io.github.laozhudu.linkplay.console.ui.theme.Spacing
import io.github.laozhudu.linkplay.console.ui.theme.StatusColors
import io.github.laozhudu.linkplay.protocol.SlaveDevice
import io.github.laozhudu.linkplay.protocol.session.SavedDevice

/**
 * 设备设置页。
 *
 * 为什么从「详情页」里拆出来：播放控制已经有独立的播放页了，这一页只放「配置设备」
 * 这类低频动作（改名、输入源、多房间、诊断）。两者混在一起的后果是每个动作都要滚半天才能找到。
 *
 * 这一页**不含任何播放控制**（除了一个进播放页的入口）—— 同一套控制只应存在一处。
 */
@Composable
fun DeviceSettingsScreen(
    state: ConsoleUiState,
    saved: List<SavedDevice>,
    vm: ConsoleViewModel,
    onBack: () -> Unit,
    onOpenPlayer: () -> Unit,
    onOpenNas: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenLocalQueue: () -> Unit,
) {
    var command by remember { mutableStateOf("getNetwork") }
    var newName by remember { mutableStateOf("") }
    var confirmReset by remember { mutableStateOf(false) }
    var showAdvanced by remember { mutableStateOf(false) }
    var confirmForget by remember { mutableStateOf(false) }
    val status = state.status

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.lg),
    ) {
        // ---- 顶栏：返回是本页唯一出口，固定在顶部 ----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                BackGlyph(MaterialTheme.colorScheme.onSurface, Modifier.size(22.dp))
            }
            Spacer(Modifier.width(Spacing.sm))
            SectionTitle(
                title = "设备设置",
                subtitle = status?.ssid ?: state.selectedIp,
                modifier = Modifier.weight(1f),
            )
            if (status != null) {
                PillTag(
                    text = if (status.isOnline) "在线" else "离线",
                    color = if (status.isOnline) StatusColors.ok else StatusColors.error,
                )
            }
        }

        state.error?.let {
            AppCard {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            VSpace(Spacing.sm)
        }

        // ---- 进播放页 ----
        AppCard(onClick = onOpenPlayer) {
            SectionTitle("播放控制", "封面 · 进度 · 音量 · 循环 · 播放列表")
        }
        VSpace(Spacing.sm)
        AppCard(onClick = onOpenNas) {
            SectionTitle("NAS 媒体库", "浏览局域网 DLNA 服务器，点一首即播")
        }
        VSpace(Spacing.sm)
        AppCard(onClick = onOpenLibrary) {
            SectionTitle("音乐库", "搜索 · 收藏 · 最近播放 · 网络电台")
        }
        VSpace(Spacing.sm)
        AppCard(onClick = onOpenLocalQueue) {
            SectionTitle("手机本地音乐", "把手机里的歌推给音箱")
        }

        VSpace(Spacing.lg)

        // ---- 设备信息 ----
        AppCard {
            SectionTitle("设备信息")
            VSpace(Spacing.xs)
            if (status == null) {
                Text("未读取（点下面的刷新）", style = MaterialTheme.typography.bodySmall)
            } else {
                InfoLine("SSID", status.ssid)
                InfoLine("上游 Wi-Fi", status.essid)
                InfoLine("硬件", status.hardware)
                InfoLine("固件", status.firmware)
                InfoLine("UUID", status.uuid)
                InfoLine("有线 MAC", status.ethernetMac)
                InfoLine("无线 MAC", status.wifiMac)
                InfoLine("联网", if (status.isOnline) "是" else "否")
                InfoLine("语言", status.language)
                InfoLine("Build", status.build)
            }
            VSpace(Spacing.sm)
            Button(onClick = vm::refreshStatus, enabled = !state.busy) { Text("刷新") }
        }

        VSpace(Spacing.md)

        // ---- 输入源 ----
        AppCard {
            SectionTitle("输入源", "切到对应通道后，音箱放的是那个通道的声音")
            VSpace(Spacing.xs)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                listOf(
                    "wifi" to "Wi-Fi",
                    "bluetooth" to "蓝牙",
                    "line-in" to "线路输入",
                    "optical" to "光纤",
                ).forEach { (mode, label) ->
                    OutlinedButton(onClick = { vm.switchMode(mode) }, enabled = !state.busy) {
                        Text(label)
                    }
                }
            }
        }

        VSpace(Spacing.md)

        // ---- 改名 ----
        AppCard {
            SectionTitle("设备名称", "会写到设备里，也会更新本机保存的名字")
            VSpace(Spacing.xs)
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("新名称") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(Spacing.sm))
                Button(onClick = { vm.renameDevice(newName); newName = "" }, enabled = !state.busy) {
                    Text("改名")
                }
            }
        }

        VSpace(Spacing.md)

        // ---- 多房间 ----
        MultiroomSection(state = state, saved = saved, vm = vm)

        VSpace(Spacing.md)

        // ---- 高级 ----
        AppCard(onClick = { showAdvanced = !showAdvanced }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (showAdvanced) "收起高级选项" else "高级选项（原始命令 / 恢复出厂 / 移除记录）",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(if (showAdvanced) "−" else "+", style = MaterialTheme.typography.titleLarge)
            }
        }
        if (showAdvanced) {
            VSpace(Spacing.sm)
            AppCard {
                Text("原始命令（httpapi.asp?command=）", style = MaterialTheme.typography.titleMedium)
                VSpace(Spacing.xs)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = command,
                        onValueChange = { command = it },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(Spacing.sm))
                    Button(onClick = { vm.sendCommand(command) }, enabled = !state.busy) { Text("执行") }
                }
                VSpace(Spacing.sm)
                OutlinedButton(onClick = { confirmForget = true }) { Text("从首页移除这台设备") }
                VSpace(Spacing.xs)
                OutlinedButton(onClick = { confirmReset = true }) {
                    Text("恢复出厂设置", color = MaterialTheme.colorScheme.error)
                }
            }
        }

        VSpace(Spacing.md)
        HorizontalDivider()
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("日志", fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            TextButton(onClick = vm::clearLog) { Text("清空") }
        }
        state.log.asReversed().take(30).forEach {
            Text(it, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
        }
        Spacer(Modifier.height(Spacing.xxl))
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("确认恢复出厂？") },
            text = { Text("该命令会清空设备当前 Wi-Fi 与配置，设备需要重新配网。") },
            confirmButton = {
                TextButton(onClick = { confirmReset = false; vm.restoreToDefault() }) { Text("确认执行") }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("取消") } },
        )
    }

    if (confirmForget) {
        AlertDialog(
            onDismissRequest = { confirmForget = false },
            title = { Text("移除这台设备？") },
            text = { Text("只删本机保存的记录，不影响音箱本身。下次可以重新搜索添加。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmForget = false
                    state.selectedUuid?.let { vm.forget(it) }
                    onBack()
                }) { Text("移除") }
            },
            dismissButton = { TextButton(onClick = { confirmForget = false }) { Text("取消") } },
        )
    }
}

/**
 * 多房间（LinkPlay multiroom）。
 *
 * ## 这一块为什么长这样
 *
 * 命令格式是从原厂 APK 的 `com.wifiaudio.a.v` 反编译出来的（docs/14 §4.7）：
 * `SlaveVolume:<从机IP>:<vol>` / `SlaveChannel:<从机IP>:<ch>` /
 * `SlaveDeviceName:<从机IP>:<name>` / `SlaveMask` / `SlaveUnMask` / `SlaveKickout`。
 *
 * 但真机实测：两台 DS-1831 同时在线，各自 `multiroom:getSlaveList` 都回 `{"slaves": 0}`，
 * `SlaveMask` 回 `OK` 却不生效 —— 说明**两台设备还没互相发现**。
 * 原厂 App 对应的入口是「按 WPS 键配对」，那一步是硬件动作，软件替代不了。
 *
 * 所以这一块的做法是：
 * - 从机列表真实读取，读到就正常显示并可调音量/踢出；
 * - 读不到时**明确说明原因**，并给一个「邀请另一台已保存音箱」的按钮去试，
 *   试完立刻回读，把设备真实反应写进日志 —— 不假装成功。
 */
@Composable
/**
 * 多房间 —— 一键组网，不抄原厂的拖拽。
 *
 * 为什么不复刻拖拽：拖拽是 2015 年平板上的手势隐喻，在手机上容易误触，
 * 而且「谁拖到谁身上就变成主机」这条规则用户根本看不出来。
 * 这里改成主流智能音箱的做法：**当前设备固定当主机，点「＋」挑一台加入**。
 * 底层命令与原厂完全相同（`multiroom:ConnnectAP:...:chext=1`），只是入口更直白。
 */
private fun MultiroomSection(state: ConsoleUiState, saved: List<SavedDevice>, vm: ConsoleViewModel) {
    var picker by remember { mutableStateOf(false) }
    val others = saved.filter { it.ip != state.selectedIp }
    val grouped = state.slaves.size

    AppCard {
        SectionTitle(
            "多房间",
            when {
                state.multiroomBusy -> "正在组网…"
                grouped > 0 -> "本机带 $grouped 台音箱同步播放"
                else -> "本机单独播放，可再加音箱一起响"
            },
        )
        VSpace(Spacing.sm)

        // ---- 一句话说明，不堆术语 ----
        Text(
            if (grouped > 0) {
                "组内的音箱会和本机播同一首歌。点音箱可以单独调它自己的音量或让它退出。"
            } else {
                "把另一台音箱加到本机下面，两台就同步播放同一首歌。加入时它会短暂离开原来的 Wi-Fi，失败会自己连回来。"
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        VSpace(Spacing.md)

        // ---- 主从关系用「芯片块」表达：主机实心 + 从机描边 + 加号 ----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                selected = true,
                onClick = {},
                label = { Text("本机 · " + (saved.firstOrNull { it.ip == state.selectedIp }?.title ?: "主机")) },
            )
            if (grouped > 0) {
                Text("＋", style = MaterialTheme.typography.titleMedium)
                state.slaves.forEach { slave ->
                    FilterChip(
                        selected = false,
                        onClick = { vm.setSlaveDialog(slave) },
                        label = { Text(slave.name ?: slave.ip) },
                    )
                }
            }
            if (others.isNotEmpty() && !state.multiroomBusy) {
                Text("＋", style = MaterialTheme.typography.titleMedium)
                AssistChip(onClick = { picker = true }, label = { Text("添加音箱") })
            }
        }

        // ---- 组网中：进度条 + 秒数（原厂是转圈 + 40s 超时，这里把秒数显出来） ----
        if (state.multiroomBusy) {
            VSpace(Spacing.md)
            LinearProgressIndicator(
                progress = { (state.multiroomTick.coerceAtMost(40) / 40f) },
                modifier = Modifier.fillMaxWidth(),
            )
            VSpace(Spacing.xs)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "正在把「${state.multiroomTarget ?: "音箱"}」加入… ${state.multiroomTick}s / 40s",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = vm::cancelMultiroom) { Text("停止") }
            }
        }

        // ---- 结果解释：命令回 OK 不等于成功，这里如实说明 ----
        state.multiroomNote?.let { note ->
            VSpace(Spacing.sm)
            Text(
                note,
                style = MaterialTheme.typography.labelMedium,
                color = if (grouped > 0) StatusColors.ok else MaterialTheme.colorScheme.error,
            )
        }

        if (grouped == 0 && others.isEmpty() && !state.multiroomBusy) {
            VSpace(Spacing.sm)
            Text(
                "还没有第二台音箱。先在「设备」页把它找出来并保存，这里就能直接加入。",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    // ---- 选一台加入 ----
    if (picker) {
        AlertDialog(
            onDismissRequest = { picker = false },
            title = { Text("加入哪一台？") },
            text = {
                Column {
                    Text(
                        "选中的音箱会连到本机，之后两台同步播放。",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    VSpace(Spacing.sm)
                    others.forEach { other ->
                        OutlinedButton(
                            onClick = { picker = false; vm.joinSlave(other) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("${other.title}（${other.ip}）") }
                        VSpace(Spacing.xs)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { picker = false }) { Text("取消") } },
        )
    }

    // ---- 从机详情：单独音量 / 退出 ----
    val dialogSlave = state.slaveDialog
    if (dialogSlave != null) {
        AlertDialog(
            onDismissRequest = vm::clearSlaveDialog,
            title = { Text(dialogSlave.name ?: dialogSlave.ip) },
            text = {
                Column {
                    Text("这台音箱单独的音量", style = MaterialTheme.typography.bodySmall)
                    VSpace(Spacing.xs)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = { vm.setSlaveVolume(dialogSlave.ip, (dialogSlave.volume ?: 50) - 10) }) { Text("−") }
                        Text(
                            "  ${dialogSlave.volume ?: "?"}  ",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        OutlinedButton(onClick = { vm.setSlaveVolume(dialogSlave.ip, (dialogSlave.volume ?: 50) + 10) }) { Text("＋") }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.kickSlave(dialogSlave.ip); vm.clearSlaveDialog() }) {
                    Text("让它退出组", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = vm::clearSlaveDialog) { Text("关闭") } },
        )
    }
}

@Composable
private fun SlaveRow(slave: SlaveDevice, busy: Boolean, vm: ConsoleViewModel) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                slave.name ?: slave.ip,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                buildString {
                    append(slave.ip)
                    append(if (slave.inGroup) " · 已入组" else " · 未入组")
                    slave.volume?.let { append(" · 音量 $it") }
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // 音量用 −/+ 而不是滑块：这里是从机，拖动滑块会连续下发命令
        OutlinedButton(
            onClick = { vm.setSlaveVolume(slave.ip, (slave.volume ?: 0) - 5) },
            enabled = !busy,
        ) { Text("−") }
        Spacer(Modifier.width(Spacing.xs))
        OutlinedButton(
            onClick = { vm.setSlaveVolume(slave.ip, (slave.volume ?: 0) + 5) },
            enabled = !busy,
        ) { Text("+") }
        Spacer(Modifier.width(Spacing.xs))
        OutlinedButton(onClick = { vm.kickSlave(slave.ip) }, enabled = !busy) { Text("踢出") }
    }
}

@Composable
private fun InfoLine(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(88.dp),
        )
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}
