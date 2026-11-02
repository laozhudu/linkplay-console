package io.github.laozhudu.linkplay.console.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * 配色对比度门禁。
 *
 * 用户反馈「界面文字与背景色对比不够好，字不容易看」—— 这类问题不能靠肉眼调，
 * 所以把 WCAG 的相对亮度算法写进单测：任何一次改色如果掉到阈值以下，CI 直接红。
 *
 * 阈值取 4.5:1（WCAG AA 正文），小字（labelSmall）额外要求 4.5 以上不留余量。
 * 次要文字（muted）在很多设计系统里会放宽到 3:1，但本项目是**遥控器**、
 * 常在暗光下看，所以也按 4.5 卡。
 */
class ThemeContrastTest {

    /** sRGB 相对亮度（WCAG 2.1）。 */
    private fun luminance(c: Color): Double {
        fun ch(v: Float): Double {
            val s = v.toDouble()
            return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * ch(c.red) + 0.7152 * ch(c.green) + 0.0722 * ch(c.blue)
    }

    private fun ratio(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    /** 半透明色先按 [bg] 合成出不透明色，再算对比度 —— 否则量出来的是假数据。 */
    private fun composite(fg: Color, bg: Color): Color {
        val a = fg.alpha
        return Color(
            red = fg.red * a + bg.red * (1 - a),
            green = fg.green * a + bg.green * (1 - a),
            blue = fg.blue * a + bg.blue * (1 - a),
            alpha = 1f,
        )
    }

    private fun assertReadable(label: String, fg: Color, bg: Color, min: Double = 4.5) {
        val r = ratio(composite(fg, bg), bg)
        assertTrue(
            "$label 对比度只有 ${"%.2f".format(r)}:1，要求 ≥ $min:1",
            r >= min,
        )
    }

    @Test
    fun 深色_正文与次要文字在卡片上可读() {
        val card = DarkColors.surfaceVariant
        assertReadable("深色 onSurface / surfaceVariant", DarkColors.onSurface, card)
        assertReadable("深色 onSurfaceVariant / surfaceVariant", DarkColors.onSurfaceVariant, card)
        assertReadable("深色 onSurface / background", DarkColors.onSurface, DarkColors.background)
        assertReadable("深色 onSurfaceVariant / surface", DarkColors.onSurfaceVariant, DarkColors.surface)
    }

    @Test
    fun 深色_播放页渐变上的文字可读() {
        // 播放页是"渐变底 + 白字"，最暗的一端最容易糊
        assertReadable("深色 onBackdrop / 渐变顶", DarkExtras.onBackdrop, DarkExtras.backdropTop)
        assertReadable("深色 onBackdropMuted / 渐变顶", DarkExtras.onBackdropMuted, DarkExtras.backdropTop)
        assertReadable("深色 onBackdrop / 渐变底", DarkExtras.onBackdrop, DarkExtras.backdropBottom)
    }

    @Test
    fun 浅色_正文与次要文字在卡片上可读() {
        val card = LightColors.surfaceVariant
        assertReadable("浅色 onSurface / surfaceVariant", LightColors.onSurface, card)
        assertReadable("浅色 onSurfaceVariant / surfaceVariant", LightColors.onSurfaceVariant, card)
        assertReadable("浅色 onSurface / background", LightColors.onSurface, LightColors.background)
        assertReadable("浅色 onSurfaceVariant / surface", LightColors.onSurfaceVariant, LightColors.surface)
    }

    @Test
    fun 浅色_播放页渐变上的文字可读() {
        assertReadable("浅色 onBackdrop / 渐变顶", LightExtras.onBackdrop, LightExtras.backdropTop)
        assertReadable("浅色 onBackdropMuted / 渐变顶", LightExtras.onBackdropMuted, LightExtras.backdropTop)
        assertReadable("浅色 onBackdrop / 渐变底", LightExtras.onBackdrop, LightExtras.backdropBottom)
    }

    @Test
    fun 主色按钮上的文字可读() {
        assertReadable("深色 onPrimary / primary", DarkColors.onPrimary, DarkColors.primary)
        assertReadable("浅色 onPrimary / primary", LightColors.onPrimary, LightColors.primary)
    }

    @Test
    fun 状态色在深色卡片上可读() {
        val card = DarkColors.surfaceVariant
        assertReadable("ok", StatusColors.ok, card, min = 4.5)
        assertReadable("warn", StatusColors.warn, card, min = 4.5)
        assertReadable("error", StatusColors.error, card, min = 4.5)
    }

    @Test
    fun 曲名派生色的强调色在其深色底上可读() {
        // seedOf 的 accent（浅）用作进度条与高亮，压在自己派生的 top（深）上
        listOf("", "五百年桑田沧海", "夜曲", "Bohemian Rhapsody", "晴天").forEach { title ->
            val p = seedOf(title, "artist")
            assertReadable("派生色 「$title」 accent / top", p.accent, p.top)
        }
    }
}
