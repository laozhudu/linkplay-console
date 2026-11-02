package io.github.laozhudu.linkplay.console.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.laozhudu.linkplay.console.ui.theme.LocalExtraColors
import io.github.laozhudu.linkplay.console.ui.theme.Spacing

/**
 * 列表页/详情页的公共积木。
 *
 * 存在的理由：播放页改成 Expressive 之后，其他页面还是 2024 年那种「Material 默认卡片 + 裸 dp」，
 * 两个页面风格拼在一起会显得像两个 App。这里把卡片、标题、标签、空状态收敛成一套，
 * 后续新增页面只拼装、不再各写一遍间距。
 *
 * 约定：颜色只用 `MaterialTheme.colorScheme` 或 [LocalExtraColors]，间距只用 [Spacing]。
 */

/** 分区标题 + 可选副标题。 */
@Composable
fun SectionTitle(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        subtitle?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 标准卡片：圆角 18dp、surfaceVariant 底、无重阴影（Expressive 靠层级不靠投影）。 */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val base = modifier
        .fillMaxWidth()
        .clip(AppShapesExpressive.squircle)
        .background(MaterialTheme.colorScheme.surfaceVariant)
    Box(if (onClick != null) base.clickable(onClick = onClick) else base) {
        Column(Modifier.padding(Spacing.md)) { content() }
    }
}

/**
 * 主入口卡片：渐变底 + 大标题。
 *
 * 为什么单独一种：详情页里「进播放页」是最常用的动作，用普通卡片会淹没在一堆设置项里。
 * 渐变取曲名派生色的同源函数，保证与播放页观感一致。
 */
@Composable
fun HeroEntryCard(
    title: String,
    subtitle: String,
    topColor: Color,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val extras = LocalExtraColors.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(AppShapesExpressive.squircle)
            .background(Brush.linearGradient(listOf(topColor, accentColor.copy(alpha = 0.45f))))
            .clickable(onClick = onClick)
            .padding(Spacing.lg),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge, color = extras.onBackdrop)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = extras.onBackdropMuted)
            }
            // 右侧小圆箭头，暗示「点进去」
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(extras.onBackdrop.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.foundation.Canvas(Modifier.size(14.dp)) {
                    val p = androidx.compose.ui.graphics.Path().apply {
                        moveTo(size.width * 0.30f, size.height * 0.16f)
                        lineTo(size.width * 0.70f, size.height * 0.50f)
                        lineTo(size.width * 0.30f, size.height * 0.84f)
                    }
                    drawPath(
                        p,
                        extras.onBackdrop,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                            width = size.width * 0.16f,
                            cap = androidx.compose.ui.graphics.StrokeCap.Round,
                        ),
                    )
                }
            }
        }
    }
}

/** 小标签：用于「通道 UPnP」「音量 25」这类次要状态。 */
@Composable
fun PillTag(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    Box(
        modifier = modifier
            .clip(AppShapesExpressive.pill)
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = Spacing.sm, vertical = Spacing.xxs),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = color,
            maxLines = 1,
        )
    }
}

/** 一行「名称 — 值」，详情页的设备信息全靠它，避免每行各写一遍布局。 */
@Composable
fun InfoLine(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xxs),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(96.dp),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

/** 空状态：一句话说明 + 建议动作，不要只留一块空白。 */
@Composable
fun EmptyState(title: String, hint: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(AppShapesExpressive.squircle)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .padding(Spacing.lg),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 设备卡片：圆形首字 + IP + 标签。比原来的纯文本卡片更容易扫读。 */
@Composable
fun DeviceCard(
    ip: String,
    server: String?,
    searchTarget: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(AppShapesExpressive.squircle)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(Spacing.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    ip.substringAfterLast('.').take(2),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                Text(ip, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                val detail = listOfNotNull(server, searchTarget).joinToString(" · ")
                if (detail.isNotEmpty()) {
                    Text(
                        detail,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** 竖直间隔的快捷写法，避免到处 `Spacer(Modifier.height(x.dp))`。 */
@Composable
fun VSpace(dp: androidx.compose.ui.unit.Dp) = Spacer(Modifier.height(dp))
