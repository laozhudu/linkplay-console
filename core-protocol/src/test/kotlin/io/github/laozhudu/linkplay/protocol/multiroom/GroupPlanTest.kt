package io.github.laozhudu.linkplay.protocol.multiroom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 勾选式分组（Sonos Grouping 菜单的做法）。
 *
 * 用户反馈「拖拽组网没那么好用」，本质是拖拽给不了三件事：
 * 一次选多台、看见当前在组里的、应用前取消。这段逻辑就是补这三件事。
 */
class GroupPlanTest {

    @Test
    fun `selecting a new device yields a join`() {
        val plan = GroupPlan.diff(current = emptySet(), selected = setOf("b"), masterUuid = "a")
        assertEquals(listOf("b"), plan.join)
        assertTrue(plan.leave.isEmpty())
        assertEquals("将加入 1 台", plan.describe())
    }

    @Test
    fun `unchecking an existing slave yields a leave`() {
        val plan = GroupPlan.diff(current = setOf("b", "c"), selected = setOf("b"), masterUuid = "a")
        assertTrue(plan.join.isEmpty())
        assertEquals(listOf("c"), plan.leave)
        assertEquals("将移出 1 台", plan.describe())
    }

    @Test
    fun `mixed change reports both sides`() {
        val plan = GroupPlan.diff(current = setOf("b"), selected = setOf("c"), masterUuid = "a")
        assertEquals(listOf("c"), plan.join)
        assertEquals(listOf("b"), plan.leave)
        assertEquals("加入 1 台、移出 1 台", plan.describe())
    }

    @Test
    fun `master is never joined or left`() {
        // 勾选里带上主机是常见操作（界面上主机那行通常也打勾），不能因此算出"把主机加进去"
        val plan = GroupPlan.diff(current = emptySet(), selected = setOf("a", "b"), masterUuid = "a")
        assertEquals(listOf("b"), plan.join)
        assertTrue(plan.leave.isEmpty())
    }

    @Test
    fun `master excluded even if it was somehow in current set`() {
        val plan = GroupPlan.diff(current = setOf("a", "b"), selected = setOf("a"), masterUuid = "a")
        assertTrue(plan.join.isEmpty())
        assertEquals(listOf("b"), plan.leave)
    }

    @Test
    fun `uuid comparison is case insensitive and trimmed`() {
        // 设备回报的 uuid 有时带 uuid: 前缀/大小写不一致，这里统一小写比较
        val plan = GroupPlan.diff(
            current = setOf("UUID:FF31"),
            selected = setOf("  uuid:ff31  "),
            masterUuid = null,
        )
        assertTrue("只差大小写不该算出变化：${plan.describe()}", plan.empty)
    }

    @Test
    fun `no change is described as no change`() {
        val plan = GroupPlan.diff(setOf("b"), setOf("b"), "a")
        assertTrue(plan.empty)
        assertEquals("没有变化", plan.describe())
    }

    @Test
    fun `blank entries are dropped instead of producing a phantom device`() {
        val plan = GroupPlan.diff(emptySet(), setOf("", "  ", "b"), "a")
        assertEquals(listOf("b"), plan.join)
    }

    @Test
    fun `everywhere selects all available except master`() {
        val all = GroupPlan.everywhere(listOf("a", "b", "c"), masterUuid = "a")
        assertEquals(setOf("b", "c"), all)
    }

    @Test
    fun `everywhere button only shows from three devices`() {
        // Sonos 也是 3 台以上才给 Everywhere；两台时"全选"没有额外价值
        assertFalse(GroupPlan.showEverywhere(2))
        assertTrue(GroupPlan.showEverywhere(3))
    }

    @Test
    fun `plan order is stable for reproducible UI`() {
        val plan = GroupPlan.diff(emptySet(), setOf("c", "a", "b"), null)
        assertEquals(listOf("a", "b", "c"), plan.join)
    }
}
