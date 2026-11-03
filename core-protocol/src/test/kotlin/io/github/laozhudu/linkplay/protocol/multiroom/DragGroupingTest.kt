package io.github.laozhudu.linkplay.protocol.multiroom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 拖拽组网判据。
 *
 * 这些拒绝条件在界面上都表现为"拖过去没反应"，用户分不清是手势没识别
 * 还是设备不允许 —— 所以每一条都必须有自己的那句话。
 */
class DragGroupingTest {

    private fun node(
        uuid: String,
        name: String = uuid,
        online: Boolean = true,
        groupedUnder: String? = null,
    ) = DragGrouping.Node(uuid = uuid, name = name, online = online, groupedUnder = groupedUnder)

    @Test
    fun `dragging one onto another groups the target as master`() {
        val d = DragGrouping.resolve(node("a", "doss1"), node("b", "doss3"))
        assertTrue(d is DragGrouping.Decision.Group)
        d as DragGrouping.Decision.Group
        assertEquals("松手的那台必须是主机（它要维持热点）", "doss3", d.masterName)
        assertEquals("被拖的那台是从机", "doss1", d.slaveName)
    }

    @Test
    fun `dropping on itself is rejected`() {
        val d = DragGrouping.resolve(node("a"), node("a"))
        assertTrue(d is DragGrouping.Decision.Reject)
        assertTrue((d as DragGrouping.Decision.Reject).reason.contains("自己"))
    }

    @Test
    fun `offline target cannot be master`() {
        val d = DragGrouping.resolve(node("a"), node("b", online = false))
        assertTrue(d is DragGrouping.Decision.Reject)
        assertTrue((d as DragGrouping.Decision.Reject).reason.contains("连不上"))
    }

    @Test
    fun `offline dragged device is rejected when it is not already in a group`() {
        val d = DragGrouping.resolve(node("a", online = false), node("b"))
        assertTrue(d is DragGrouping.Decision.Reject)
        assertTrue((d as DragGrouping.Decision.Reject).reason.contains("连不上"))
    }

    @Test
    fun `a slave cannot become a master`() {
        val d = DragGrouping.resolve(node("a"), node("b", groupedUnder = "doss9"))
        assertTrue(d is DragGrouping.Decision.Reject)
        val reason = (d as DragGrouping.Decision.Reject).reason
        assertTrue("要告诉用户先从哪台移出：$reason", reason.contains("doss9"))
        assertTrue(reason.contains("移出"))
    }

    @Test
    fun `a device already in a group is not re-dragged`() {
        val d = DragGrouping.resolve(node("a", groupedUnder = "doss1"), node("c"))
        assertTrue(d is DragGrouping.Decision.Reject)
        assertTrue((d as DragGrouping.Decision.Reject).reason.contains("已在"))
    }

    @Test
    fun `unknown target is rejected with a readable message`() {
        val d = DragGrouping.resolve(node("a"), null)
        assertTrue(d is DragGrouping.Decision.Reject)
        assertTrue((d as DragGrouping.Decision.Reject).reason.isNotBlank())
    }

    // ---- 命中判定 ----

    @Test
    fun `hit test picks the nearest card`() {
        val points = listOf(
            Triple("a", 100f, 100f),
            Triple("b", 100f, 500f),
            Triple("c", 100f, 900f),
        )
        assertEquals("b", DragGrouping.hitTest(points, 105f, 480f, "a", 300f))
    }

    @Test
    fun `hit test never returns the dragged card itself`() {
        val points = listOf(Triple("a", 100f, 100f), Triple("b", 100f, 400f))
        assertEquals("b", DragGrouping.hitTest(points, 100f, 100f, "a", 400f))
    }

    @Test
    fun `hit test returns null beyond max distance`() {
        val points = listOf(Triple("b", 100f, 400f))
        assertNull("拖到空白处不能随机挂到最远那台", DragGrouping.hitTest(points, 900f, 900f, "a", 200f))
    }

    @Test
    fun `hit test with no other cards returns null`() {
        assertNull(DragGrouping.hitTest(listOf(Triple("a", 0f, 0f)), 10f, 10f, "a", 500f))
    }
}
