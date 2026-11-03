package io.github.laozhudu.linkplay.console.provisioning

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.laozhudu.linkplay.console.ConsoleUiState
import io.github.laozhudu.linkplay.console.ui.components.AppCard
import io.github.laozhudu.linkplay.console.ui.components.PageHeader
import io.github.laozhudu.linkplay.console.ui.components.PillButton
import io.github.laozhudu.linkplay.console.ui.components.SectionTitle
import io.github.laozhudu.linkplay.console.ui.components.VSpace
import io.github.laozhudu.linkplay.console.ui.theme.Spacing
import io.github.laozhudu.linkplay.console.ui.theme.StatusColors
import io.github.laozhudu.linkplay.protocol.provisioning.WpsGuide
import io.github.laozhudu.linkplay.protocol.provisioning.WpsStep
import kotlinx.coroutines.delay

/**
 * WPS 配网引导（原厂 `SlaveSpeakerAddWPSActivity` 的等价实现）。
 *
 * ## 这条流程和设备侧协议无关
 *
 * WPS 是**音箱和路由器之间**的事：用户按一下音箱的 WPS 键，音箱自己去找路由器配对。
 * App 在这里做不了任何"下发"，只能**等**它出现（SSDP 扫到）。
 * 所以这一页是纯引导 + 轮询，没有一条命令要发给设备 —— 这点必须写清楚，
 * 否则用户会以为"点一下这个按钮就把音箱配好了"。
 *
 * ## 为什么每 2 秒扫一次
 *
 * 原厂 `pagesmsccontent.bb` 也是 2 秒轮询 + 35~40 秒超时。
 * 扫得太快没有意义（音箱配对要几秒），太慢则用户以为卡住了。
 * 这里的超时放在 [WpsGuide] 里（60 秒，比原厂宽一点：WPS 按键 + 路由器握手常常更慢）。
 */
@Composable
fun WpsGuideScreen(
    state: ConsoleUiState,
    onRescan: () -> Unit,
    onBack: () -> Unit,
    onDone: () -> Unit,
) {
    var elapsed by remember { mutableLongStateOf(0L) }
    var startedAt by remember { mutableStateOf(System.currentTimeMillis()) }
    var baseline by remember { mutableStateOf(state.devices.size) }
    var attempt by remember { mutableStateOf(0) }

    // 新出现的设备 = 配对成功（数量变多即视为发现）
    val found = (state.devices.size - baseline).coerceAtLeast(0)
    val step = WpsGuide.tick(elapsed, found)

    LaunchedEffect(attempt) {
        startedAt = System.currentTimeMillis()
        elapsed = 0L
        baseline = state.devices.size
        while (true) {
            delay(2_000)
            elapsed = System.currentTimeMillis() - startedAt
            onRescan()
            if (WpsGuide.tick(elapsed, (state.devices.size - baseline).coerceAtLeast(0)) != WpsStep.Waiting) break
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.lg),
    ) {
        PageHeader(
            title = "WPS 配网",
            subtitle = "让音箱自己去连路由器",
            onBack = onBack,
        )

        AppCard {
            SectionTitle("按顺序做三件事", "App 只负责等它出现，不会下发任何命令")
            VSpace(Spacing.sm)
            listOf(
                "1. 给音箱插电、等它开机（指示灯常亮或缓慢闪烁）",
                "2. 长按机身 WPS 键约 3 秒，直到指示灯快闪",
                "3. 手机保持连着家里 2.4G Wi-Fi，不要切走",
            ).forEach {
                Text(it, style = MaterialTheme.typography.bodyMedium)
                VSpace(Spacing.xs)
            }
        }

        VSpace(Spacing.sm)
        AppCard {
            when (step) {
                WpsStep.Found -> {
                    Text("发现新设备", style = MaterialTheme.typography.titleMedium, color = StatusColors.ok)
                    Text(
                        "共发现 ${state.devices.size} 台候选，去设备列表添加它。",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    VSpace(Spacing.sm)
                    PillButton(text = "去设备列表", enabled = true, onClick = onDone)
                }
                WpsStep.Timeout -> {
                    Text("60 秒内没发现新设备", style = MaterialTheme.typography.titleMedium, color = StatusColors.warn)
                    Text(
                        "常见原因：手机连的是 5G Wi-Fi（这类老固件只支持 2.4G）、" +
                            "音箱没进 WPS 模式（指示灯没快闪）、或路由器关了 WPS。\n" +
                            "也可以改用「AP 直连配网」——那条路更稳。",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    VSpace(Spacing.sm)
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        PillButton(text = "再试一次", enabled = true, onClick = { attempt++ })
                        TextButton(onClick = onBack) { Text("改用 AP 直连") }
                    }
                }
                else -> {
                    Text("正在等待音箱出现…", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "已等待 ${elapsed / 1000} 秒 / ${WpsGuide.DEFAULT_TIMEOUT_MS / 1000} 秒 · 目前扫到 ${state.devices.size} 台",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    VSpace(Spacing.sm)
                    LinearProgressIndicator(
                        progress = { WpsGuide.progress(elapsed, WpsGuide.DEFAULT_TIMEOUT_MS) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    VSpace(Spacing.xs)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PillButton(text = "取消", enabled = true, onClick = onBack)
                    }
                }
            }
        }

        VSpace(Spacing.sm)
        Text(
            "提示：WPS 只在 2.4GHz 上有效。音箱连上后本机会自动扫到它；" +
                "如果一直扫不到，用 AP 直连配网最靠谱。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        VSpace(Spacing.xl)
    }
}
