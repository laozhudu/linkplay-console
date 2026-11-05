package io.github.laozhudu.linkplay.console.ui.apple

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Apple 风格的三个基础件（用户 2026-11-05：「苹果的 ui 设计就是最好的 ui」）。
 *
 * ## 为什么单独一套，而不是改造现有 `AppCard`
 *
 * 现有 `AppCard` 是 Material 的"卡片堆叠"，全 App 19 处。
 * 一次性全换会同时动 10 个页面，出问题无法定位。
 * 所以先把苹果那套的**模子**建好，页面再一个个搬过来 —— 搬一个测一个。
 *
 * ## 三条不动摇的规则（来自 HIG）
 *
 * 1. **行高 ≥ 44dp**（触控最小可信尺寸，苹果全系如此）
 * 2. **灰阶层级**：正文 label、次要 secondaryLabel、分组标题 tertiary
 *    —— 颜色只留给"状态"和"主操作"，不给文字
 * 3. **行内不放解释文字**：一行一个意思，解释放下一层
 */

// ---- 层级靠「明度」不靠「色相」：三档灰全部从 onSurface 降透明度派生 ----
//
// 一开始我写死了 iOS 的固定灰（0xFF1C1C1E 之类），编译通过、亮色下也对，
// 但**暗色主题下分组块会变成一块白底**（底色我也写死了 White）—— 装上去是刺眼的白屏。
// 改成从主题派生之后，两种主题自动都对。
@Composable
private fun label(): Color = MaterialTheme.colorScheme.onSurface
@Composable
private fun secondaryLabel(): Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
@Composable
private fun tertiaryLabel(): Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)

/**
 * 分组列表容器（iOS 的 inset grouped list）。
 *
 * 视觉：浅灰页面底 + 白色分组块 + 组间留白。
 * **不是卡片** —— 卡片有描边和阴影，分组块只有圆角和底色差。
 *
 * @param title 组标题，iOS 是全大写小字。null 表示不显示。
 * @param footer 组脚注（可选）。**能不放就不放** —— 它是解释文字。
 */
@Composable
fun InsetGroup(
    title: String? = null,
    footer: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (title != null) {
            Text(
                title.uppercase(),
                fontSize = 13.sp,
                color = tertiaryLabel(),
                modifier = Modifier.padding(start = 16.dp, bottom = 6.dp, top = 22.dp),
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surface),
            content = content,
        )
        if (footer != null) {
            Text(
                footer,
                fontSize = 13.sp,
                color = secondaryLabel(),
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 7.dp),
            )
        }
    }
}

/**
 * 标准列表行。
 *
 * 布局：[leading] 图标 | 标题(+副标题) | [trailing] 次信息 | chevron
 *
 * @param showChevron 右边那个 `›`。**它表示"点进去还有一层"**，
 *   不要给"点了就执行"的行加 —— 那是按钮语义，加了会让人以为点不动。
 * @param destructive 红色文字（删除类操作）。
 */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    showChevron: Boolean = false,
    destructive: Boolean = false,
    onClick: (() -> Unit)? = null,
    showDivider: Boolean = true,
) {
    val haptics = LocalHapticFeedback.current
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (onClick != null) {
                        Modifier.clickable {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onClick()
                        }
                    } else {
                        Modifier
                    },
                )
                // 44dp 是苹果全系统的行高底线
                .heightIn(min = 44.dp)
                .padding(horizontal = 16.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leading != null) {
                leading()
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    fontSize = 17.sp,
                    color = if (destructive) Color(0xFFFF3B30) else label(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        subtitle,
                        fontSize = 13.sp,
                        color = secondaryLabel(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (trailing != null) {
                Spacer(Modifier.width(8.dp))
                trailing()
            }
            if (showChevron) {
                Spacer(Modifier.width(6.dp))
                Text("›", fontSize = 20.sp, color = tertiaryLabel())
            }
        }
        if (showDivider) {
            // 分隔线从文字左边缘开始（苹果的缩进分隔线），不是整行通到底
            HorizontalDivider(
                modifier = Modifier.padding(start = if (leading != null) 44.dp else 16.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
            )
        }
    }
}

/**
 * 大标题（iOS Large Title）。
 *
 * 34sp、左对齐、粗体。**滚动时应该收起成导航栏标题** ——
 * 这一版先只做静态的，收起动效等页面搬过来时再加（那时才有滚动容器可观察）。
 */
@Composable
fun LargeTitle(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 10.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                color = label(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(subtitle, fontSize = 15.sp, color = secondaryLabel())
            }
        }
        if (trailing != null) {
            Box(Modifier.padding(bottom = 4.dp)) { trailing() }
        }
    }
}
