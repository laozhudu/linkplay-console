package io.github.laozhudu.linkplay.console.device

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.laozhudu.linkplay.console.ConsoleUiState
import io.github.laozhudu.linkplay.console.ConsoleViewModel
import io.github.laozhudu.linkplay.console.ui.components.AppCard
import io.github.laozhudu.linkplay.console.ui.components.AppShapesExpressive
import io.github.laozhudu.linkplay.console.ui.components.PageHeader
import io.github.laozhudu.linkplay.console.ui.components.PillButton
import io.github.laozhudu.linkplay.console.ui.components.SectionTitle
import io.github.laozhudu.linkplay.console.ui.components.VSpace
import io.github.laozhudu.linkplay.console.ui.theme.Spacing
import io.github.laozhudu.linkplay.console.ui.theme.StatusColors

/**
 * 「设备内容」（对应原厂 `DeviceContentActivity`）。
 *
 * 原厂这一页放的都是**设备端**的东西，不是我们手机上的内容：
 * - 六键预设（发 `MCUKeyShortClick:<1..6>`，由音箱自己执行存好的动作）；
 * - 固件升级（先 `getMvRemoteUpdateStart` 查，再读下载/烧写进度）。
 *
 * 为什么单独一页而不塞进「设备设置」：设置页是**改设备配置**（名字/语言/输入源），
 * 这一页是**触发设备动作**。两者混在一起会让设置页越滚越长（之前就踩过，用户说
 * 「设置页面与已有的一些页面功能重叠」）。
 */
@Composable
fun DeviceContentScreen(
    state: ConsoleUiState,
    vm: ConsoleViewModel,
    onBack: () -> Unit,
) {
    val status = state.status
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.lg),
    ) {
        PageHeader(
            title = "设备内容",
            subtitle = status?.ssid ?: state.selectedIp,
            onBack = onBack,
        )

        state.error?.let {
            AppCard { Text(it, style = MaterialTheme.typography.bodySmall, color = StatusColors.error) }
            VSpace(Spacing.sm)
        }
        state.multiroomNote?.let {
            AppCard { Text(it, style = MaterialTheme.typography.bodySmall, color = StatusColors.warn) }
            VSpace(Spacing.sm)
        }

        if (state.selectedIp == null) {
            AppCard { Text("先选一台音箱", style = MaterialTheme.typography.bodyMedium) }
            return@Column
        }

        // ---- 六键预设 ----
        AppCard {
            SectionTitle("六键预设", "音箱机身上那六个键存的动作，点一下就执行")
            VSpace(Spacing.sm)
            val count = state.presetCount.coerceAtLeast(1)
            // 两行三列，与原厂机身排列一致
            for (row in 0 until ((count + 2) / 3)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    for (col in 0 until 3) {
                        val index = row * 3 + col
                        if (index >= count) {
                            Box(Modifier.weight(1f))
                            continue
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant,
                                    AppShapesExpressive.squircle,
                                )
                                .clickable(enabled = !state.busy) { vm.pressPreset(index) }
                                .padding(vertical = Spacing.md),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("键 ${index + 1}", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
                VSpace(Spacing.xs)
            }
        }

        VSpace(Spacing.sm)

        // ---- 固件升级 ----
        AppCard {
            SectionTitle("固件升级", "先让音箱去服务器查，再按设备回报的进度显示")
            VSpace(Spacing.sm)
            val fw = state.firmware
            Text(fw.phase, style = MaterialTheme.typography.bodyMedium)
            Text(
                listOfNotNull(
                    fw.currentVersion?.let { "当前 $it" },
                    fw.newVersion?.let { "新版本 $it" },
                    fw.statusRaw?.let { "设备状态 $it" },
                ).joinToString(" · ").ifBlank { "尚未检查" },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (fw.downloadPercent > 0) {
                VSpace(Spacing.xs)
                LinearProgressIndicator(
                    progress = { fw.downloadPercent / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "下载 ${fw.downloadPercent}%" + if (fw.burnPercent > 0) " · 写入 ${fw.burnPercent}%" else "",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            VSpace(Spacing.sm)
            Row(verticalAlignment = Alignment.CenterVertically) {
                PillButton(text = "检查更新", enabled = !state.busy, onClick = vm::checkFirmware)
                Spacer(Modifier.width(Spacing.sm))
                PillButton(
                    text = "开始升级",
                    enabled = !state.busy && fw.canStart,
                    onClick = vm::startFirmwareUpdate,
                )
            }
            if (!fw.canStart && fw.downloadPercent == 0) {
                Text(
                    "没查到新版本时这里不能点 —— 不会给你一个按了没反应的按钮。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Start,
                )
            }
        }

        VSpace(Spacing.sm)
        AppCard {
            SectionTitle("输入源", "切换会打断当前播放；切走网络后手机可能控制不到音箱")
            VSpace(Spacing.xs)
            // 当前输入源在播放页顶栏显示（那里读的是设备回报的 mode），
            // 这里只做"切到哪一路"，不重复一套当前值判断 —— 两处各算一次必然漂移。
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                listOf("wifi" to "网络", "line-in" to "线路", "bluetooth" to "蓝牙").forEach { (id, label) ->
                    TextButton(onClick = { vm.switchMode(id) }, enabled = !state.busy) { Text(label) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                TextButton(onClick = { vm.switchMode("udisk") }, enabled = !state.busy) { Text("U 盘 / TF 卡") }
                TextButton(onClick = { vm.switchMode("optical") }, enabled = !state.busy) { Text("光纤") }
            }
        }

        VSpace(Spacing.xl)
    }
}
