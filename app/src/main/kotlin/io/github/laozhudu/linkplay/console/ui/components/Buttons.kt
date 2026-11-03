package io.github.laozhudu.linkplay.console.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.laozhudu.linkplay.console.ui.theme.Spacing

/**
 * 通用按钮与图标位。
 *
 * ## 为什么集中到这里
 *
 * 一致性审计（docs/23）发现同一个「胶囊按钮」被**三份私有实现**各写了一遍：
 * `MultiroomCard` / `LibraryScreen` / `RadioScreen`，参数名还不一样
 * （`enabled` + `filled` vs 只有 `filled`），导致三处视觉与点击反馈都有细微差别。
 * `InfoLine` 也是两份（Common 有公开的一份，DeviceSettings 又私有写了一份）。
 *
 * 规矩：**跨页面复用的控件只允许有一份**，参数差异用可选参数表达，不再各写一遍。
 */

/** 胶囊按钮。主用作「填充」，次要用作「描边/灰底」。 */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = true,
    enabled: Boolean = true,
) {
    val bg = if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (filled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = modifier
            .clip(AppShapesExpressive.pill)
            .background(if (enabled) bg else bg.copy(alpha = 0.4f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = Spacing.md, vertical = 9.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = if (enabled) fg else fg.copy(alpha = 0.6f))
    }
}

/** 圆形图标位（播放器控制键、返回键、⋯ 等都用它，保证点击热区一致）。 */
@Composable
fun CircleIcon(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}
