package io.github.laozhudu.linkplay.console.playback

import io.github.laozhudu.linkplay.protocol.queue.QueueItem
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 播放列表的删除逻辑。
 *
 * 为什么值得单独测：用户反馈「播放列表没法用」。除了界面入口藏得深，
 * 还有一个真 bug —— 服务没在跑（`QueueBus.controller == null`）时，
 * `removeAt` 直接调空对象，**点了叉毫无反应**。
 * 这里把「服务缺席也必须能删」这条锁住。
 */
class QueueBusTest {

    /** 队列归属的固定值：现在所有写入都必须带 deviceIp（编译期强制）。 */
    private val DEV = "192.168.99.153"


    private fun items(vararg names: String) =
        names.mapIndexed { i, n -> QueueItem(url = "http://nas/$i.mp3", title = n) }

    @Before
    fun reset() {
        QueueBus.bind(null)
        QueueBus.clear(DEV)
    }

    @After
    fun tearDown() {
        QueueBus.bind(null)
        QueueBus.clear(DEV)
    }

    @Test
    fun 服务不在时也能删除并保持索引一致() {
        QueueBus.begin(DEV, items("a", "b", "c"))
        QueueBus.playing(DEV, "b", 1)

        QueueBus.removeAt(0)                       // 删掉当前曲之前的那首

        val st = QueueBus.state.value
        assertEquals(listOf("b", "c"), st.items.map { it.title })
        assertEquals(0, st.index)                  // 索引跟着前移，仍指向 b
        assertEquals(2, st.total)
        assertEquals("b", st.current?.title)
    }

    @Test
    fun 删掉当前曲后索引不越界() {
        QueueBus.begin(DEV, items("a", "b", "c"))
        QueueBus.playing(DEV, "c", 2)

        QueueBus.removeAt(2)                       // 删掉最后一首（正在播）

        val st = QueueBus.state.value
        assertEquals(listOf("a", "b"), st.items.map { it.title })
        assertEquals(1, st.index)                  // 收回到最后一首
        assertEquals(2, st.total)
    }

    @Test
    fun 删空队列后索引为负一() {
        QueueBus.begin(DEV, items("only"))
        QueueBus.playing(DEV, "only", 0)

        QueueBus.removeAt(0)

        val st = QueueBus.state.value
        assertTrue(st.items.isEmpty())
        assertEquals(-1, st.index)
        assertEquals(0, st.total)
    }

    @Test
    fun 服务在跑时把删除交给服务而不是改本地() {
        var delegated = -1
        QueueBus.bind(object : QueueController {
            override fun skipTo(index: Int) {}
            override fun removeAt(index: Int) { delegated = index }
        })
        QueueBus.begin(DEV, items("a", "b"))

        QueueBus.removeAt(1)

        assertEquals(1, delegated)
        assertEquals(2, QueueBus.state.value.items.size)   // 本地不动，等服务回推
        assertTrue(QueueBus.hasController)
    }

    @Test
    fun 跳转也走同一个控制器() {
        var jumped = -1
        QueueBus.bind(object : QueueController {
            override fun skipTo(index: Int) { jumped = index }
            override fun removeAt(index: Int) {}
        })
        QueueBus.begin(DEV, items("a", "b", "c"))

        QueueBus.skipTo(2)

        assertEquals(2, jumped)
    }

    @Test
    fun 越界索引不抛出也不改动() {
        QueueBus.begin(DEV, items("a"))
        QueueBus.removeAt(5)
        assertEquals(1, QueueBus.state.value.items.size)
        assertFalse(QueueBus.state.value.items.isEmpty())
    }
}
