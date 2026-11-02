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
    // 这三个色都要在深色卡片底（0xFF1B2029 附近）上达到 4.5:1，
    // 所以比 Material 默认色提亮一档 —— 之前 0x66BB6A 做小字说明时确实偏暗。
    val ok = Color(0xFF7BD88F)
    /** 需要动手（控制面掉线、需要重启设备）。 */
    val warn = Color(0xFFFFC061)
    val error = Color(0xFFFF8A80)
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

/** internal 是为了让单测能直接量对比度，不靠肉眼。 */
internal val DarkExtras = ExtraColors(
    onBackdrop = Color(0xFFF7F8FC),
    // 原来 0xB3（70%）压在渐变上只有 ~3.9:1，小字看不清；提到 0xD0 后约 6:1
    onBackdropMuted = Color(0xD0F7F8FC),
    backdropTop = Color(0xFF1B2230),
    backdropBottom = Color(0xFF0A0C11),
    accent = Color(0xFF9FD3FF),
    onAccent = Color(0xFF06223A),
)

internal val LightExtras = ExtraColors(
    onBackdrop = Color(0xFF101318),
    // 浅色底上 60% 黑只有 ~3.6:1，提到 0xC4（77%）后约 7:1
    onBackdropMuted = Color(0xC4101318),
    backdropTop = Color(0xFFE8EEF7),
    backdropBottom = Color(0xFFFDFDFF),
    accent = Color(0xFF0B5FA5),
    onAccent = Color(0xFFFFFFFF),
)

val LocalExtraColors = staticCompositionLocalOf { DarkExtras }

/**
 * 恒定压在「深色底」上的文字色。
 *
 * 为什么需要一组不随主题变的色：首页设备卡与播放页的背景是 [seedOf] 派生的**深色渐变**
 * （hsl 亮度 0.24，永远深），而 `ExtraColors` 会跟系统主题走 ——
 * 浅色主题下 `onBackdrop` 变成近黑，压在深渐变上等于隐形。
 * 这就是「文字与背景对比不够」最严重的一处，只在浅色模式暴露。
 */
object OnDarkPalette {
    val text = Color(0xFFF7F8FC)
    /** 次要文字：0xD0 不透明度，压在深渐变上约 10:1。 */
    val muted = Color(0xD0F7F8FC)
    val faint = Color(0x99F7F8FC)
    /** 深色屏上的浮起面板/胶囊底。 */
    val panel = Color(0xFF1B2230)
    val chip = Color(0x1FFFFFFF)
}

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
        lineHeight = 16.sp,
        letterSpacing = 0.2.sp,
    ),
    // 默认的 labelSmall 是 11sp / 常规字重，压在深色卡片上几乎看不见。
    // 这里升到 12sp + Medium，并明确行高 —— 小字也更耐读。
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.2.sp,
    ),
)

internal val DarkColors = darkColorScheme(
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
    onBackground = Color(0xFFF2F4F9),
    surface = Color(0xFF12161E),
    onSurface = Color(0xFFF2F4F9),
    // 卡片底比原来亮一档、正文比原来亮一档：这一对是"看不清"投诉的直接来源
    surfaceVariant = Color(0xFF232A36),
    onSurfaceVariant = Color(0xFFDCE1EA),
    outline = Color(0xFF5A6474),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

internal val LightColors = lightColorScheme(
    primary = Color(0xFF0B5FA5),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD3E7FA),
    onPrimaryContainer = Color(0xFF001D33),
    secondary = Color(0xFF3F5C2B),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDCEBCB),
    onSecondaryContainer = Color(0xFF14200A),
    tertiary = Color(0xFF7A4500),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFF7F9FC),
    onBackground = Color(0xFF101318),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF101318),
    surfaceVariant = Color(0xFFE6EBF3),
    onSurfaceVariant = Color(0xFF3B4250),
    outline = Color(0xFF8891A0),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
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
