package io.github.laozhudu.linkplay.console.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.absoluteValue

/**
 * 视觉基线（Material 3 Expressive 取向，界面全部自绘）。
 *
 * 为什么自绘而不直接用 `media3-ui-compose-material3`：那套组件是「看得过去的标准样式」，
 * 做不出封面渐变、大圆角封面、浮动主控这类层次。取它的架构（Player 接口）可以，
 * 取它的外观则只是「不难看」。
 *
 * 三条硬规矩（后续页面必须遵守，否则改主题时会有一半页面跟不上）：
 * 1. 尺寸只用 [Spacing]，颜色只用 `MaterialTheme.colorScheme` 或 [LocalExtraColors]；
 * 2. 圆角只用 [AppShapes]，不写裸 `RoundedCornerShape(13.dp)`；
 * 3. 状态色用 [StatusColors]，不要用 `colorScheme.error` 表达「需要你动手」。
 */

object Spacing {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
}

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

object StatusColors {
    val ok = Color(0xFF66BB6A)
    /** 需要动手（控制面掉线、需要重启设备）。 */
    val warn = Color(0xFFFFA726)
    val error = Color(0xFFEF5350)
}

/**
 * colorScheme 之外的补充色。
 *
 * 为什么另开一层：播放页需要「盖在封面色上仍然可读的文字色」和「渐变两端」，
 * 标准 colorScheme 没有对应槽位；硬塞进 surface 会让其它页面一起变味。
 */
data class ExtraColors(
    val onBackdrop: Color,
    val onBackdropMuted: Color,
    val backdropTop: Color,
    val backdropBottom: Color,
    val accent: Color,
    val onAccent: Color,
)

private val DarkExtras = ExtraColors(
    onBackdrop = Color(0xFFF3F4F8),
    onBackdropMuted = Color(0xB3F3F4F8),
    backdropTop = Color(0xFF1B2230),
    backdropBottom = Color(0xFF0A0C11),
    accent = Color(0xFF9FD3FF),
    onAccent = Color(0xFF06223A),
)

private val LightExtras = ExtraColors(
    onBackdrop = Color(0xFF14171C),
    onBackdropMuted = Color(0x9914171C),
    backdropTop = Color(0xFFE8EEF7),
    backdropBottom = Color(0xFFFDFDFF),
    accent = Color(0xFF0B5FA5),
    onAccent = Color(0xFFFFFFFF),
)

val LocalExtraColors = staticCompositionLocalOf { DarkExtras }

private val AppTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 30.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.4).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.2).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 19.sp,
        lineHeight = 25.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        letterSpacing = 0.3.sp,
    ),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9FD3FF),
    onPrimary = Color(0xFF06223A),
    primaryContainer = Color(0xFF16324A),
    onPrimaryContainer = Color(0xFFCDE7FF),
    secondary = Color(0xFFB8D8A0),
    onSecondary = Color(0xFF15240A),
    secondaryContainer = Color(0xFF23331A),
    onSecondaryContainer = Color(0xFFD3EDBD),
    tertiary = Color(0xFFFFC48A),
    onTertiary = Color(0xFF2A1600),
    background = Color(0xFF0A0C11),
    onBackground = Color(0xFFE7E9EF),
    surface = Color(0xFF11151C),
    onSurface = Color(0xFFE7E9EF),
    surfaceVariant = Color(0xFF1B2029),
    onSurfaceVariant = Color(0xFFC2C7D2),
    outline = Color(0xFF3A4150),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF0B5FA5),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD3E7FA),
    onPrimaryContainer = Color(0xFF001D33),
    secondary = Color(0xFF4A6B33),
    tertiary = Color(0xFF8C5000),
)

/**
 * 由曲名派生的一组稳定配色。
 *
 * 为什么「派生」：同一首歌每次进来颜色必须一样，否则界面像在乱闪；
 * 也不该每次重组都重算，所以只在 seedOf 的输入变化时算一次。
 * 用曲名而不是随机数/时间——它是最稳定的输入。
 */
data class SeedPalette(
    val top: Color,
    val bottom: Color,
    val accent: Color,
    val onAccent: Color,
)

private val SeedHues = floatArrayOf(204f, 262f, 320f, 12f, 42f, 158f, 186f)

fun seedOf(title: String?, artist: String?): SeedPalette {
    val key = title.orEmpty() + "|" + artist.orEmpty()
    val idx = key.hashCode().absoluteValue % SeedHues.size
    val hue = SeedHues[idx]
    return SeedPalette(
        top = hsl(hue, 0.42f, 0.24f),
        bottom = Color(0xFF0A0C11),
        accent = hsl(hue, 0.62f, 0.72f),
        onAccent = hsl(hue, 0.55f, 0.12f),
    )
}

/** 极简 HSL→RGB，避免为一个渐变引入色彩库。 */
private fun hsl(h: Float, s: Float, l: Float): Color {
    val c = (1f - kotlin.math.abs(2f * l - 1f)) * s
    val hp = h / 60f
    val x = c * (1f - kotlin.math.abs(hp % 2f - 1f))
    val r: Float
    val g: Float
    val b: Float
    when {
        hp < 1f -> { r = c; g = x; b = 0f }
        hp < 2f -> { r = x; g = c; b = 0f }
        hp < 3f -> { r = 0f; g = c; b = x }
        hp < 4f -> { r = 0f; g = x; b = c }
        hp < 5f -> { r = x; g = 0f; b = c }
        else -> { r = c; g = 0f; b = x }
    }
    val m = l - c / 2f
    return Color(r + m, g + m, b + m)
}

@Composable
fun LinkPlayTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalExtraColors provides if (darkTheme) DarkExtras else LightExtras) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
