package io.github.laozhudu.linkplay.console.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 「接着听」右侧那一列 `7:45`。
 *
 * 为什么要测一个这么小的函数：**0 和"未知"不是一回事**。
 * `durationSec = 0` 表示设备没报时长，这时候显示 `0:00` 会让人以为播放卡住了
 * （`PlayerUiState` 那边已经踩过同一个坑）。所以 0 必须返回 null，让界面这一列干脆不画。
 */
class DurationFormatTest {

    @Test
    fun `正常的秒数格式化成 分冒号两位秒`() {
        assertEquals("7:45", formatDuration(465))
        assertEquals("0:05", formatDuration(5))
        assertEquals("10:00", formatDuration(600))
    }

    @Test
    fun `0 和负数返回 null 而不是 0 冒号 00`() {
        assertNull(formatDuration(0))
        assertNull(formatDuration(-3))
    }

    @Test
    fun `来源标识翻译成界面文案`() {
        assertEquals("本地", sourceLabelOf("local"))
        assertEquals("电台", sourceLabelOf("radio"))
        assertEquals("在线", sourceLabelOf("subsonic"))
        // 认不出的原样返回，不要瞎猜
        assertEquals("mystery", sourceLabelOf("mystery"))
    }
}
