package io.github.laozhudu.linkplay.console.diagnostics

import io.github.laozhudu.linkplay.protocol.remote.CommandTrace
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ActionTimingTest {

    @Before
    fun setUp() {
        CommandTrace.DEFAULT.clear()
        ActionTiming.clear()
    }

    @After
    fun tearDown() {
        CommandTrace.DEFAULT.clear()
        ActionTiming.clear()
    }

    @Test
    fun `measure counts the commands issued inside the action`() = runBlocking {
        ActionTiming.measure(ActionTiming.Action.SET_VOLUME) {
            CommandTrace.DEFAULT.record(
                CommandTrace.Entry(
                    atMs = System.currentTimeMillis(),
                    command = "SetVolume(45)",
                    deviceIp = "192.168.99.153",
                    acked = true,
                    failureText = null,
                    elapsedMs = 30,
                ),
            )
        }

        val sample = ActionTiming.ledger.recent().first()
        assertEquals(ActionTiming.Action.SET_VOLUME.id, sample.name)
        assertEquals(1, sample.commands)
        assertEquals(listOf("SetVolume"), sample.commandLabels)
        assertTrue(sample.ok)
    }

    @Test
    fun `an action that never touches a device reports zero commands`() = runBlocking {
        ActionTiming.measure(ActionTiming.Action.RADIO_DISCOVER) { 42 }

        val sample = ActionTiming.ledger.recent().first()
        assertEquals(0, sample.commands)
        assertTrue(ActionTiming.render().contains("不碰设备"))
    }

    @Test
    fun `a failing action is still recorded and the error keeps propagating`() {
        var thrown: Throwable? = null
        runBlocking {
            try {
                ActionTiming.measure(ActionTiming.Action.RADIO_PLAY) { throw IllegalStateException("设备没回状态") }
            } catch (error: Throwable) {
                thrown = error
            }
        }

        assertTrue(thrown is IllegalStateException)
        val sample = ActionTiming.ledger.recent().first()
        assertEquals(ActionTiming.Action.RADIO_PLAY.id, sample.name)
        assertEquals(false, sample.ok)
    }

    @Test
    fun `names are stable and unique so the ledger cannot drift`() {
        assertEquals(ActionTiming.names.size, ActionTiming.names.distinct().size)
        assertTrue(ActionTiming.names.all { it.first().isLowerCase() })
    }
}
