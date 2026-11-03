package io.github.laozhudu.linkplay.console.device

import io.github.laozhudu.linkplay.console.floating.FloatingControlService
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
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
import io.github.laozhudu.linkplay.console.ui.components.InfoLine
import io.github.laozhudu.linkplay.console.ui.components.PageHeader
import io.github.laozhudu.linkplay.console.ui.components.PillTag
import io.github.laozhudu.linkplay.console.ui.components.SectionTitle
import io.github.laozhudu.linkplay.console.ui.components.VSpace
import io.github.laozhudu.linkplay.console.ui.theme.Spacing
import io.github.laozhudu.linkplay.console.ui.theme.StatusColors

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
        // 统一页头（与其它二级页同一套尺寸与间距）
        PageHeader(
            title = "设备设置",
            subtitle = status?.ssid ?: state.selectedIp,
            onBack = onBack,
            trailing = {
                if (status != null) {
                    PillTag(
                        text = if (status.isOnline) "在线" else "离线",
                        color = if (status.isOnline) StatusColors.ok else StatusColors.error,
                    )
                }
            },
        )

        state.error?.let {
            AppCard {
                Text(it, style = MaterialTheme.typography.bodySmall, color = StatusColors.error)
            }
            VSpace(Spacing.sm)
        }

        // 这里原来有四张「进播放页 / NAS / 音乐库 / 本地音乐」的入口卡片 —— 已删。
        // 底部导航本来就有这三个 Tab，同一目的地在一屏里出现两次，
        // 用户第一反应是"这两个按钮是不是不一样"，纯属干扰。
        VSpace(Spacing.sm)

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

        // ---- 桌面悬浮控制（原厂 FloatingWindowService 的等价）----
        AppCard {
            SectionTitle("桌面悬浮控制", "在桌面或别的 App 上也能暂停/切歌")
            VSpace(Spacing.xs)
            val context = LocalContext.current
            val running by FloatingControlService.running.collectAsState()
            // 权限每次进页面都重读：用户可能在系统设置里刚给过/刚收回
            var canOverlay by remember { mutableStateOf(FloatingControlService.canDrawOverlays(context)) }
            LaunchedEffect(Unit) { canOverlay = FloatingControlService.canDrawOverlays(context) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when {
                        !canOverlay -> "需要「显示在其他应用上层」权限"
                        running -> "正在显示"
                        else -> "未开启"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = when {
                        !canOverlay -> StatusColors.warn
                        running -> StatusColors.ok
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.weight(1f),
                )
                if (!canOverlay) {
                    TextButton(onClick = {
                        // 引导到本 App 的悬浮窗权限页；不加包名会落到设置首页，用户找不到
                        runCatching { context.startActivity(FloatingControlService.overlayPermissionIntent(context)) }
                    }) { Text("去授权") }
                } else {
                    TextButton(onClick = {
                        if (running) {
                            FloatingControlService.stop(context)
                        } else {
                            // 没有权限时 start() 返回 false，这里只会在有权限分支执行
                            FloatingControlService.start(context, state.selectedIp)
                        }
                    }) { Text(if (running) "关闭" else "开启") }
                }
            }
            if (!canOverlay) {
                Text(
                    "悬浮窗要在桌面显示，必须由你在系统设置里授权；App 不能替你点。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

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

        // ---- 多房间：已移到首页（设备层面的事，不该藏在某台设备的设置里）----
        AppCard(onClick = onBack) {
            SectionTitle("多房间", "在首页配置：加/减音箱、每台的音量与声道")
        }

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
                    Text("恢复出厂设置", color = StatusColors.error)
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
