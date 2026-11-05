package io.github.laozhudu.linkplay.console.playback

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 输出选择的读回规则。**认不出来时必须回退「跟随当前设备」**：
 * 回退到它最多是"没切成"（和以前的行为一样），回退到"本机"会突然从手机外放出声。
 *
 * 输出口从「本机/音箱」二元改成「本机 + 每台设备一个」之后，
 * 老数据 `remote` 必须仍然解析成「跟随当前设备」—— 否则升级后偏好全丢。
 */
class PlaybackOutputStoreTest {

    @Test
    fun `local is recognised`() {
        assertEquals(PlaybackOutput.Local, PlaybackOutputStore.parse("local"))
    }

    @Test
    fun `case and surrounding blanks do not matter`() {
        assertEquals(PlaybackOutput.Local, PlaybackOutputStore.parse("  LOCAL \n"))
    }

    @Test
    fun `anything else falls back to follow`() {
        listOf(null, "", "   ", "remote", "???", "本机").forEach { raw ->
            assertEquals("输入=$raw 应回退到跟随当前设备", PlaybackOutput_follow, PlaybackOutputStore.parse(raw))
        }
    }

    @Test
    fun `device ip is recognised and round trips`() {
        val parsed = PlaybackOutputStore.parse("device:192.168.99.153")
        assertEquals(PlaybackOutput.Device("192.168.99.153"), parsed)
        assertEquals("device:192.168.99.153", PlaybackOutputStore.format(parsed))
    }

    @Test
    fun `empty device falls back to follow and writes the old value`() {
        assertEquals(PlaybackOutput_follow, PlaybackOutputStore.parse("device:"))
        // 回滚到旧版本时读到 remote 仍然是「跟随当前设备」，不会突然变成本机外放
        assertEquals("remote", PlaybackOutputStore.format(PlaybackOutput_follow))
    }
}
