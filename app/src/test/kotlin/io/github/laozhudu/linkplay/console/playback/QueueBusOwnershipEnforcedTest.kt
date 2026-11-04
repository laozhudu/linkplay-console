package io.github.laozhudu.linkplay.console.playback

import io.github.laozhudu.linkplay.protocol.queue.QueueItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 队列归属的**编译期强制** —— 写入侧每个方法都必须传 deviceIp。
 *
 * ## 为什么要有这组用例
 *
 * PR #40 修过一次"切设备后队列串台"，但只改了 `QueueStarter` 一条路。
 * 剩下 10 处（NAS / Console / 播放服务内部）仍在直接写 `QueueBus.restore(...)`，
 * 内部读的是"当前 owner"——调用方忘了先 `setOwner` 就把新列表挂到上一台设备名下。
 *
 * 现在签名改成 `restore(deviceIp, …)`，**漏传编译不过**。
 * 这组用例锁的是行为：写入时必须带归属，且归属不同的写入互相不干扰。
 */
class QueueBusOwnershipEnforcedTest {

    private val a = "192.168.99.153"
    private val b = "192.168.99.164"

    private fun item(title: String) = QueueItem(url = "http://x/$title.mp3", title = title)

    @Before
    fun setUp() {
        QueueBus.bind(null)
        QueueBus.clear(a)
        QueueBus.clear(b)
    }

    @Test
    fun `begin 自带归属，不需要先 setOwner`() {
        // 旧 API 的两步写法（setOwner + begin）漏一步就串台；现在一步到位
        QueueBus.begin(b, listOf(item("x")))

        assertEquals(b, QueueBus.state.value.ownerIp)
        assertTrue(QueueBus.owns(b))
        assertFalse(QueueBus.owns(a))
    }

    @Test
    fun `restore 自带归属`() {
        QueueBus.restore(a, listOf(item("x"), item("y")), 1, "y")

        assertEquals(a, QueueBus.state.value.ownerIp)
        assertEquals(2, QueueBus.state.value.items.size)
    }

    @Test
    fun `别的设备的播放进度不改这份队列`() {
        QueueBus.begin(a, listOf(item("x"), item("y")))
        assertEquals(0, QueueBus.state.value.index)

        // b 设备的上报必须被忽略 —— 否则 a 的列表高亮会跳到 b 的位置
        QueueBus.playing(b, "y", 1)
        assertEquals("b 的进度不该改 a 的队列", 0, QueueBus.state.value.index)

        // 自己上报才生效
        QueueBus.playing(a, "y", 1)
        assertEquals(1, QueueBus.state.value.index)
    }

    @Test
    fun `clear 只清自己那台`() {
        QueueBus.begin(a, listOf(item("x")))

        // b 来清，什么都不该发生
        QueueBus.clear(b)
        assertEquals("别人的 clear 不该抹掉 a 的队列", 1, QueueBus.state.value.items.size)

        // a 自己清才生效
        QueueBus.clear(a)
        assertEquals(0, QueueBus.state.value.items.size)
        assertNull(QueueBus.state.value.items.firstOrNull())
    }

    @Test
    fun `切设备时旧队列被整体作废`() {
        QueueBus.begin(a, listOf(item("x"), item("y")))
        assertTrue(QueueBus.owns(a))

        // 切到 b：列表与归属都换掉，不能留着 a 的
        QueueBus.begin(b, listOf(item("z")))
        assertTrue(QueueBus.owns(b))
        assertFalse(QueueBus.owns(a))
        assertEquals(listOf("z"), QueueBus.state.value.items.map { it.title })
    }

    @Test
    fun `同一台设备重复 begin 不会留下上一份的残留`() {
        QueueBus.begin(a, listOf(item("x"), item("y"), item("z")))
        QueueBus.begin(a, listOf(item("only")))

        assertEquals(1, QueueBus.state.value.items.size)
        assertEquals(0, QueueBus.state.value.index)
        assertEquals(1, QueueBus.state.value.total)
    }
}
