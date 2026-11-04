package io.github.laozhudu.linkplay.console

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 「组」诊断段的排版。
 *
 * 这一段存在的唯一理由：用户说「加了组退不出来」时，要能立刻分清是
 *
 * - 设备端真的还在组里（从机数 > 0）
 * - 还是**我们不认为它在组里**（`uuid` 对不上 → 卡片上没有「退出同步」入口）
 * - 还是命令发了但设备没照做（`actionConfirmed == false`）
 *
 * 三者的修法完全不同：一台去退组、一台去修映射、一台去换命令。
 */
class GroupSourceStatusTest {

    @Before
    fun setUp() = GroupSourceStatus.reset()

    @After
    fun tearDown() = GroupSourceStatus.reset()

    @Test
    fun `before any read says so`() {
        assertTrue(GroupSourceStatus.render().contains("还没读过组拓扑"))
    }

    @Test
    fun `topology with slaves names master and count`() {
        GroupSourceStatus.topologyRead("doss1", slaves = 2, unmatched = 0, ms = 180)

        val text = GroupSourceStatus.render()
        assertTrue(text.contains("主机=doss1"))
        assertTrue(text.contains("从机=2 台"))
        assertTrue(text.contains("180ms"))
    }

    @Test
    fun `unmatched slaves are called out because they lose the ungroup entry`() {
        GroupSourceStatus.topologyRead("doss1", slaves = 2, unmatched = 1, ms = 200)

        val text = GroupSourceStatus.render()
        assertTrue(text.contains("1 台从机在本地设备记录里找不到对应"))
        // 必须说明后果：否则这句话只是噪音
        assertTrue(text.contains("没有「退出同步」入口"))
    }

    @Test
    fun `confirmed empty topology is reported as dissolved`() {
        GroupSourceStatus.topologyRead("doss1", slaves = 2, unmatched = 0, ms = 150)
        GroupSourceStatus.topologyEmpty(160)

        val text = GroupSourceStatus.render()
        assertTrue(text.contains("当前无组"))
        assertTrue(text.contains("确认解散"))
        assertEquals(0, GroupSourceStatus.snapshot().lastSlaves)
    }

    @Test
    fun `a read failure must not look like an empty topology`() {
        GroupSourceStatus.topologyRead("doss1", slaves = 2, unmatched = 0, ms = 150)
        GroupSourceStatus.topologyFailed("所有设备都读不到 slave_list")

        val s = GroupSourceStatus.snapshot()
        // 关键：失败不改变"上一次真的读到 2 台"这个事实
        assertEquals(2, s.lastSlaves)
        assertTrue(GroupSourceStatus.render().contains("最近读取失败"))
    }

    @Test
    fun `ungroup accepted but not applied is a warning`() {
        GroupSourceStatus.ungroup("解除 doss3", confirmed = false)

        val text = GroupSourceStatus.render()
        assertTrue(text.contains("命令被接受但设备仍在组里"))
        assertTrue(text.contains("解除 doss3"))
    }

    @Test
    fun `ungroup confirmed reads as gone`() {
        GroupSourceStatus.ungroup("解除 doss3", confirmed = true)
        assertTrue(GroupSourceStatus.render().contains("已确认从列表消失"))
    }

    @Test
    fun `ungroup still waiting is neither success nor failure`() {
        GroupSourceStatus.ungroup("解除 doss3", confirmed = null)
        assertTrue(GroupSourceStatus.render().contains("等待确认"))
    }
}
