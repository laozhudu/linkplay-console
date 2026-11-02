package io.github.laozhudu.linkplay.protocol.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PinyinInitialsTest {

    @Test
    fun `common characters map to the expected initial`() {
        assertEquals('W', PinyinInitials.initialOf('我'))
        assertEquals('A', PinyinInitials.initialOf('爱'))
        assertEquals('N', PinyinInitials.initialOf('你'))
        assertEquals('Z', PinyinInitials.initialOf('张'))
        assertEquals('X', PinyinInitials.initialOf('学'))
        assertEquals('L', PinyinInitials.initialOf('刘'))
        assertEquals('C', PinyinInitials.initialOf('陈'))
    }

    @Test
    fun `latin and digits are not treated as hanzi`() {
        assertNull(PinyinInitials.initialOf('a'))
        assertNull(PinyinInitials.initialOf('1'))
        assertNull(PinyinInitials.initialOf(' '))
    }

    @Test
    fun `full string initials keep latin and drop separators`() {
        assertEquals("zjlyq", PinyinInitials.initialsOf("周杰伦 - 夜曲"))
        assertEquals("abc", PinyinInitials.initialsOf("a.b c"))
        // 中文 + 数字混排
        assertEquals("q1", PinyinInitials.initialsOf("七1"))
    }

    @Test
    fun `hasHanzi detects chinese text`() {
        assertTrue(PinyinInitials.hasHanzi("夜曲"))
        assertFalse(PinyinInitials.hasHanzi("Nocturne 2024"))
    }

    @Test
    fun `empty and punctuation-only inputs are safe`() {
        assertEquals("", PinyinInitials.initialsOf(""))
        assertEquals("", PinyinInitials.initialsOf("!!! ---"))
    }
}
