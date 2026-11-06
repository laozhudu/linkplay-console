package io.github.laozhudu.linkplay.console.me

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import io.github.laozhudu.linkplay.console.ui.apple.InsetGroup
import io.github.laozhudu.linkplay.console.ui.apple.LargeTitle
import io.github.laozhudu.linkplay.console.ui.apple.ListRow
import io.github.laozhudu.linkplay.console.ui.components.VSpace
import io.github.laozhudu.linkplay.console.ui.theme.Spacing

/**
 * 「我」页（`docs/93` 定稿第 5 屏）。
 *
 * ## 为什么诊断台是第一项
 *
 * 用户原话：「用户不需要知道全部设备信息，用户只需要能用、好用、简单状态就够」
 * —— 那是说**设备页**。反过来，出问题时他需要的恰恰是全量信息，
 * 所以诊断台从「设备设置 → 第四层」提到**这里的第一项**（原来要点四层）。
 *
 * ## 这一页只放"全局一次"的配置
 *
 * `docs/52` 铁律四 §4.4：每台设备的配置必须跟着那台设备走（点设备进详情），
 * 不许塞进这个全局页 —— 否则换个设备要重新找一遍。
 */
@Composable
internal fun MeScreen(
    onOpenDiagnostics: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenProvisioning: () -> Unit,
    onOpenScripts: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var showTheme by remember { mutableStateOf(false) }
    val themeMode by io.github.laozhudu.linkplay.console.ui.theme.ThemePrefs.mode.collectAsState()
    val version = remember(context) {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrNull() ?: "?"
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.lg),
    ) {
        LargeTitle(title = "我")
        VSpace(Spacing.md)

        InsetGroup(title = "出问题了先看这里") {
            ListRow(
                title = "诊断台",
                subtitle = "崩溃记录 · 每台设备的取数通道 · 指令流水",
                showChevron = true,
                onClick = onOpenDiagnostics,
                showDivider = false,
            )
        }

        VSpace(Spacing.md)
        InsetGroup(title = "外观") {
            ListRow(
                title = "主题",
                subtitle = themeMode.label,
                showChevron = true,
                onClick = { showTheme = true },
                showDivider = false,
            )
        }

        VSpace(Spacing.md)
        InsetGroup(title = "配置") {
            ListRow(
                title = "音箱设置",
                subtitle = "当前选中那台的网络 / 名字 / 固件",
                showChevron = true,
                onClick = onOpenSettings,
            )
            ListRow(
                title = "音源脚本",
                subtitle = "洛雪脚本装了什么 / 哪支在用",
                showChevron = true,
                onClick = onOpenScripts,
            )
            ListRow(
                title = "配网",
                subtitle = "新音箱第一次接入 Wi-Fi",
                showChevron = true,
                onClick = onOpenProvisioning,
                showDivider = false,
            )
        }

        VSpace(Spacing.md)
        InsetGroup(title = "关于") {
            ListRow(
                title = "版本",
                subtitle = version,
                showDivider = false,
            )
        }

        VSpace(Spacing.md)
        Text(
            text = "私人音频中枢遥控器 —— 把 NAS、在线曲库、电台里的声音送到家里那几台音箱上。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        VSpace(Spacing.xl)
    }

    if (showTheme) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showTheme = false },
            title = { Text("主题") },
            text = {
                Column {
                    io.github.laozhudu.linkplay.console.ui.theme.ThemeMode.entries.forEach { m ->
                        androidx.compose.material3.TextButton(
                            onClick = {
                                io.github.laozhudu.linkplay.console.ui.theme.ThemePrefs.set(context, m)
                                showTheme = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                // 选中的那个打勾，其余留空格 —— 不用 RadioButton，
                                // 一行文字按钮比一行单选圈更接近苹果的写法
                                (if (m == themeMode) "✓  " else "     ") + m.label,
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showTheme = false }) { Text("取消") }
            },
        )
    }
}
