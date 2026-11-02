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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
        MultiroomSection(state = state, vm = vm)

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
 * 多房间分组。
 *
 * **状态说明（必须诚实）**：真机只有一台 DS-1831，`getSlaveList` 恒返回 `{"slaves":0}`，
 * 所以「入组/退组」的实际效果**未在真机上验证**。这里的按钮都真实下发了命令，
 * 但因为无从核对，界面不显示「已成功」，只把设备返回写进日志。
 *
 * 已确认可用的部分：从机列表读取、从机音量（`setSlaveVolume`，原厂 App 也是这条命令）。
 */
@Composable
private fun MultiroomSection(state: ConsoleUiState, vm: ConsoleViewModel) {
    AppCard {
        SectionTitle(
            "多房间",
            if (state.slaves.isEmpty()) "还没有其他音箱加入" else "${state.slaves.size} 台从机",
        )
        VSpace(Spacing.xs)
        if (state.slaves.isEmpty()) {
            Text(
                "把另一台音箱接到同一 Wi-Fi 后，它会出现在这里。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "提示：分组与入组命令已接入，但本机只有一台音箱，实际效果尚未在真机验证。",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            state.slaves.forEach { slave ->
                SlaveRow(slave = slave, busy = state.busy, vm = vm)
                HorizontalDivider()
            }
        }
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
