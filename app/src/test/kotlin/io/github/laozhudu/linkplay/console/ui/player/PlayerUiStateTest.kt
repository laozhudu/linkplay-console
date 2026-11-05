package io.github.laozhudu.linkplay.console.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerUiStateTest {

    @Test
    fun `empty state has nothing to show and gives a readable title`() {
        val ui = PlayerUiState()
        assertFalse(ui.hasContent)
        assertEquals("没有在播的内容", ui.displayTitle)
    }

    @Test
    fun `blank titles are treated as missing, not as content`() {
        val ui = PlayerUiState.fromRemote(nowPlaying = "   ", running = false, playing = false)
        assertFalse("空格不是内容", ui.hasContent)
        assertEquals(null, ui.title)
    }

    @Test
    fun `paused but queued still counts as content`() {
        val ui = PlayerUiState.fromRemote(nowPlaying = "七里香", running = true, playing = false)
        assertTrue(ui.hasContent)
        assertTrue("暂停时按钮应该显示播放", ui.toggleToPlay)
        assertEquals(PlayerUiState.Source.Remote, ui.source)
    }

    @Test
    fun `playing reports pause next`() {
        val ui = PlayerUiState.fromRemote(nowPlaying = "七里香", running = true, playing = true)
        assertFalse(ui.toggleToPlay)
        assertEquals("七里香", ui.displayTitle)
    }

    @Test
    fun `control lost is carried through so the ui can disable buttons`() {
        val ui = PlayerUiState.fromRemote(nowPlaying = "x", running = true, playing = true, controlLost = true)
        assertTrue(ui.controlLost)
    }
}
