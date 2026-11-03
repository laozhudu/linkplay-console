package io.github.laozhudu.linkplay.console.diagnostics

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.laozhudu.linkplay.console.ui.components.AppCard
import io.github.laozhudu.linkplay.console.ui.components.PageHeader
import io.github.laozhudu.linkplay.console.ui.components.VSpace
import io.github.laozhudu.linkplay.console.ui.theme.Spacing
import io.github.laozhudu.linkplay.console.ui.theme.StatusColors

/**
 * 诊断页 —— 用户能截图给我的那一页。
 *
 * ## 设计原则
 *
 * 1. **一屏能截完关键信息**：崩溃摘要 + 设备表在最上面，日志在下面。
 * 2. **等宽字体**：堆栈和通道名对齐后一眼能看出差异（截图时尤其重要）。
 * 3. **一键复制**：整份报告进剪贴板，用户可以直接发文字，比截图更准。
 * 4. **可清空**：看过了就清，避免下次启动还弹旧记录。
 *
 * 为什么不做成"日志滚动列表"：截图只能截一屏，滚动列表一截就丢上下文；
 * 纯文本报告天然可分段截、可复制。
 */
@Composable
fun DiagnosticsScreen(
    reportProvider: () -> String,
    onClear: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    // 每次进页面生成一次：报告是快照，不该跟着状态每帧重算
    var report by remember { mutableStateOf(reportProvider()) }

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.systemBars),
    ) {
        PageHeader(
            title = "诊断记录",
            subtitle = "崩溃堆栈 / 设备取数通道 / 运行期问题",
            onBack = onBack,
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = {
                        copyToClipboard(context, report)
                    }) { Text("复制全部") }
                    TextButton(onClick = {
                        // 重新生成：用户可能刚操作过设备，想看到最新通道状态
                        report = reportProvider()
                    }) { Text("刷新") }
                    TextButton(onClick = {
                        onClear()
                        report = reportProvider()
                    }) { Text("清空记录", color = StatusColors.error) }
                }
            }

            item {
                AppCard {
                    Text(
                        "把这一页截图，或点「复制全部」把文本发给我 —— " +
                            "里面有崩溃堆栈和设备取数通道，能直接定位问题，不用再猜。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // 报告主体：整段等宽文本，一次渲染
            // 为什么不用 items 拆行：拆行后行号/缩进在截图上不连续，反而难读
            item {
                AppCard {
                    Text(
                        report,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                        ),
                    )
                }
            }

            item { VSpace(Spacing.xl) }
        }
    }
}

/** 复制到剪贴板。失败只提示，不崩。 */
private fun copyToClipboard(context: Context, text: String) {
    runCatching {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("LinkPlay 诊断报告", text))
        Toast.makeText(context, "已复制，粘贴给我即可", Toast.LENGTH_SHORT).show()
    }.onFailure {
        Toast.makeText(context, "复制失败：${it.message}", Toast.LENGTH_SHORT).show()
    }
}
