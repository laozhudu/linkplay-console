package io.github.laozhudu.linkplay.protocol.playback

import io.github.laozhudu.linkplay.protocol.player.DeviceQueueState
import io.github.laozhudu.linkplay.protocol.queue.QueueItem
import io.github.laozhudu.linkplay.protocol.session.PlaybackSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 重开 App 后列表该显示什么。
 *
 * 这是用户反复反馈的那条：「退出 App 重开后播放列表没有同步设备列表」。
 * 三种情形（有快照且长度一致 / 设备有队列但没快照 / 只有快照）各自的结论都在这里钉死。
 */
class QueueRestoreTest {

    private fun items(n: Int) = (1..n).map { QueueItem(url = "http://nas/$it.mp3", title = "曲目$it") }

    private fun session(n: Int, index: Int = 0, offset: Int = 0) = PlaybackSession(
        deviceUuid = "uuid-1", deviceIp = "10.0.0.5", items = items(n),
        index = index, loopMode = 2, deviceOffset = offset,
    )

    private fun device(count: Int, index: Int) =
        DeviceQueueState(count = count, index = index, playing = true)

    @Test
    fun 设备队列与快照长度一致_用快照标题加设备位置() {
        val plan = QueueRestore.plan(session(5), device(5, 2))!!
        assertEquals(QueueRestore.Source.DEVICE_WITH_SNAPSHOT, plan.source)
        assertEquals("曲目3", plan.items[plan.index].title)
        assertEquals("设备列表 5 首 · 第 3 首", plan.note)
        assertTrue(plan.fromDevice)
    }

    @Test
    fun 列表被旋转过时_设备位置要加回偏移() {
        // 下发时从本地第 3 首起（offset=2），设备现在播它的第 1 首 → 对应本地第 3 首
        val plan = QueueRestore.plan(session(5, index = 2, offset = 2), device(5, 0))!!
        assertEquals(2, plan.index)
        assertEquals("曲目3", plan.items[plan.index].title)
    }

    @Test
    fun 旋转加偏移超过列表长度时回绕() {
        val plan = QueueRestore.plan(session(5, offset = 4), device(5, 3))!!
        assertEquals(2, plan.index)   // (4+3) % 5
    }

    @Test
    fun 设备有队列但没有快照_给占位列表而不是空列表() {
        val plan = QueueRestore.plan(null, device(7, 3))!!
        assertEquals(QueueRestore.Source.DEVICE_PLACEHOLDER, plan.source)
        assertEquals(7, plan.items.size)
        assertEquals(3, plan.index)
        assertTrue(plan.note.contains("本机没存到"))
        assertTrue(QueueRestore.isPlaceholder(plan.items.first()))
    }

    @Test
    fun 快照长度与设备不符_同样给占位并说明() {
        val plan = QueueRestore.plan(session(3), device(8, 1))!!
        assertEquals(QueueRestore.Source.DEVICE_PLACEHOLDER, plan.source)
        assertEquals(8, plan.items.size)
    }

    @Test
    fun 设备没有队列但有快照_展示上次列表并说明() {
        val plan = QueueRestore.plan(session(4, index = 1), DeviceQueueState(0, -1, false))!!
        assertEquals(QueueRestore.Source.SNAPSHOT_ONLY, plan.source)
        assertEquals(1, plan.index)
        assertTrue(!plan.fromDevice)
        assertTrue(plan.note.contains("设备当前没有队列"))
    }

    @Test
    fun 都读不到时返回_null_界面显示空状态而不是假列表() {
        assertNull(QueueRestore.plan(null, DeviceQueueState(0, -1, false)))
        assertNull(QueueRestore.plan(null, null))
    }

    @Test
    fun 设备读不到但快照在_仍展示快照() {
        // 读不到设备 ≠ 设备没队列。此时不能把用户的列表也藏起来
        val plan = QueueRestore.plan(session(2), null)!!
        assertEquals(QueueRestore.Source.SNAPSHOT_ONLY, plan.source)
        assertEquals(2, plan.items.size)
    }

    @Test
    fun 占位项带设备当前曲名_只用于第一项() {
        val list = QueueRestore.placeholderItems(3, "五百年桑田沧海")
        assertEquals("五百年桑田沧海", list[0].title)
        assertEquals("设备上的第 2 首", list[1].title)
    }

    @Test
    fun 设备位置越界时占位列表回落到第零项() {
        val plan = QueueRestore.plan(null, device(3, 9))!!
        assertEquals(0, plan.index)
    }
}
