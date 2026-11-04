package io.github.laozhudu.linkplay.console.playback

import io.github.laozhudu.linkplay.protocol.queue.QueueItem
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 队列归属（多台音箱时的串台回归）。
 *
 * 锁死的是这条真实故障：切到第二台音箱（`.164`）后，播放页仍然显示第一台
 * （`.153`）的「设备列表 122 首 · 第 3 首」—— 因为 QueueBus 是全局单例、
 * 换设备从不清空。用户对着别人的列表按播放，还会连带报 UPnP 704。
 */
class QueueBusOwnershipTest {

    private fun item(url: String) = QueueItem(url = url, title = url)

    @After
    fun tearDown() {
        // QueueBus 是 object，测试之间必须复位
        QueueBus.setOwner("reset")
        QueueBus.clear("reset")
    }

    @Test
    fun `switching owner drops the previous device queue`() {
        QueueBus.setOwner("192.168.99.153")
        QueueBus.restore("192.168.99.153", listOf(item("a"), item("b"), item("c")), 2, "c", deviceOwned = true)
        assertEquals(3, QueueBus.state.value.items.size)
        assertTrue(QueueBus.owns("192.168.99.153"))

        QueueBus.setOwner("192.168.99.164")

        assertEquals("换设备后不能再留着上一台的列表", 0, QueueBus.state.value.items.size)
        assertEquals(-1, QueueBus.state.value.index)
        assertFalse(QueueBus.owns("192.168.99.153"))
        assertTrue(QueueBus.owns("192.168.99.164"))
    }

    @Test
    fun `restoring a queue keeps the owner so sync still works`() {
        QueueBus.setOwner("192.168.99.164")
        QueueBus.restore("192.168.99.164", listOf(item("x")), 0, "x", deviceOwned = true)
        assertEquals("192.168.99.164", QueueBus.state.value.ownerIp)
        QueueBus.syncDeviceIndex(0)
        assertEquals(0, QueueBus.state.value.index)
    }

    @Test
    fun `setting the same owner twice must not wipe the queue`() {
        QueueBus.setOwner("192.168.99.164")
        QueueBus.restore("192.168.99.164", listOf(item("x"), item("y")), 1, "y")
        QueueBus.setOwner("192.168.99.164")
        assertEquals("同一台设备重复声明不应清空列表（首屏组合时会调两次）", 2, QueueBus.state.value.items.size)
    }

    @Test
    fun `begin stamps the current owner`() {
        QueueBus.setOwner("192.168.99.153")
        QueueBus.begin("192.168.99.153", listOf(item("a")))
        assertEquals("192.168.99.153", QueueBus.state.value.ownerIp)
        assertTrue(QueueBus.state.value.running)
    }

    @Test
    fun `unknown owner never claims to own anything`() {
        QueueBus.clear("reset")
        QueueBus.setOwner("192.168.99.153")
        assertFalse(QueueBus.owns(null))
        assertFalse(QueueBus.owns(""))
        assertFalse(QueueBus.owns("192.168.99.164"))
    }
}
