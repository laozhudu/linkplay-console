package io.github.laozhudu.linkplay.console.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * 设计令牌 + 主题。
 *
 * 这一轮只做「骨架级」：把颜色、间距、层级固定下来，让后续每个页面用同一套常量，
 * **不做**视觉打磨（自定义字体、动效、插画都留到功能齐了再做）。
 *
 * 理由：视觉打磨的返工成本几乎为零（改几行颜色），但结构（导航层级、状态呈现方式）一旦
 * 被新功能推翻就要重画。现在做令牌，是为了让后面新加的页面不至于各写各的 dp。
 */

/** 间距刻度。页面内边距统一用 4dp 的倍数，避免出现 13dp、17dp 这种随手值。 */
object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
}

/**
 * 语义色：表达「设备/控制面/播放」三种状态，不直接写 Material 的容器色。
 *
 * 为什么要单独一层：`CONTROL_LOST`（设备在线但 UPnP 掉了）和 `OFFLINE`（整台不通）
 * 是完全不同的处理方式，用同一个 errorContainer 显示会让人误以为要检查网络。
 */
object StatusColors {
    /** 一切正常。 */
    val ok = Color(0xFF2E7D32)

    /** 需要动手（如控制面掉线、需要重启设备）。 */
    val warn = Color(0xFFEF6C00)

    /** 设备不可达。 */
    val error = Color(0xFFC62828)
}

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8AB4F8),
    secondary = Color(0xFF9CCC65),
    tertiary = Color(0xFFFFB74D),
    background = Color(0xFF111418),
    surface = Color(0xFF171B21),
    surfaceVariant = Color(0xFF222932),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF1A73E8),
    secondary = Color(0xFF33691E),
    tertiary = Color(0xFFE65100),
)

@Composable
fun LinkPlayTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
