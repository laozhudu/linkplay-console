package io.github.laozhudu.linkplay.console.ui.skeleton

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import io.github.laozhudu.linkplay.console.ui.components.AppCard
import io.github.laozhudu.linkplay.console.ui.theme.StatusColors

/** 行尾的小状态。 */
enum class RowBadge {
    /** 什么都不显示（行内自己给 trailing）。 */
    None,

    /** 一排列表里的普通行：右侧一个 ▶。 */
    Arrow,

    /** 正在播：绿色 ▶。 */
    Playing,

    /** 正在处理（连接/取地址）：一个 …。 */
    Loading,
}

/**
 * 列表行 —— **整行可点**，右侧只放一个动作。
 *
 * ## 为什么不让页面自己拼
 *
 * 统计过：`AppCard(onClick)` 用了 19 处，每处自己拼「标题 + 副标题 + 右侧动作」。
 * 结果行高、点击反馈、右侧图标（▶ / × / 删除 / 播放）各不相同 ——
 * 用户看着像三套界面。
 *
 * ## 一屏一个主操作
 *
 * 左侧文字区、右侧一个动作。要放第二个操作（删除、启用）就传 `trailing` ——
 * 但**一个**。之前有行塞了「删除 + 播放 + 启用」三个，挤且容易点错。
 */
@Composable
fun AppListRow(
    title: String,
    subtitle: String? = null,
    badge: RowBadge = RowBadge.Arrow,
    subtitleColor: Color? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    AppCard(modifier = modifier, onClick = onClick) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                subtitle?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = subtitleColor ?: MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            trailing?.invoke()
            when (badge) {
                RowBadge.None -> Unit
                RowBadge.Arrow -> Text(
                    "▶",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                RowBadge.Playing -> Text(
                    "▶",
                    style = MaterialTheme.typography.titleMedium,
                    color = StatusColors.ok,
                )
                RowBadge.Loading -> Text("…", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
