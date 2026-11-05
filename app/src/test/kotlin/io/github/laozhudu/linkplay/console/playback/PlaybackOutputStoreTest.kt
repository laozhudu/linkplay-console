package io.github.laozhudu.linkplay.console.playback

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 输出选择的读回规则。**认不出来时必须回退「音箱」**：
 * 那是现有主力路径，回退到它最多是"没切成"，回退到"本机"会突然从手机外放出声。
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
    fun `anything else falls back to remote`() {
        listOf(null, "", "   ", "remote", "???", "本机").forEach { raw ->
            assertEquals("输入=$raw 应回退音箱", PlaybackOutput.Remote, PlaybackOutputStore.parse(raw))
        }
    }
}
