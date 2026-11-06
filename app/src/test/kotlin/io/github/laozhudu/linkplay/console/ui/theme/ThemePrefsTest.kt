package io.github.laozhudu.linkplay.console.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 主题偏好的两条规则。
 *
 * 锁的是**「跟随系统」真的跟随**、以及**手动选择能压过系统** ——
 * 这两条写反了，表现就是"我明明选了深色，App 还是白的"，
 * 而那种 bug 只有在用户系统设置正好相反时才会暴露，极难复现。
 */
class ThemePrefsTest {

    @Test
    fun `跟随系统时结果就是系统的深浅`() {
        assertTrue(ThemePrefs.resolve(ThemeMode.System, systemDark = true))
        assertFalse(ThemePrefs.resolve(ThemeMode.System, systemDark = false))
    }

    @Test
    fun `手动选择能压过系统`() {
        // 系统是深色，但用户选浅色 → 必须是浅色
        assertFalse(ThemePrefs.resolve(ThemeMode.Light, systemDark = true))
        // 系统是浅色，但用户选深色 → 必须是深色
        assertTrue(ThemePrefs.resolve(ThemeMode.Dark, systemDark = false))
    }

    @Test
    fun `存坏的或缺失的偏好回落到跟随系统`() {
        assertEquals(ThemeMode.System, ThemeMode.of(null))
        assertEquals(ThemeMode.System, ThemeMode.of(""))
        assertEquals(ThemeMode.System, ThemeMode.of("purple"))
        // 存过的能读回来
        assertEquals(ThemeMode.Dark, ThemeMode.of("dark"))
        assertEquals(ThemeMode.Light, ThemeMode.of("light"))
    }
}
