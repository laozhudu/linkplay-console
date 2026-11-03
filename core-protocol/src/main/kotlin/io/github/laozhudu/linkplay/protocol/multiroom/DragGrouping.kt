package io.github.laozhudu.linkplay.protocol.multiroom

/**
 * 拖拽组网的判据（纯 JVM，可单测）。
 *
 * ## 原厂的交互
 *
 * 设备列表里把一台音箱**拖到另一台上面**松手 → 后者成为主机、被拖的那台成为从机。
 * 方向不能反：主机要维持热点（`ra0=10.10.10.254`）并持有组，从机接入主机的 AP 后
 * 会掉到 `10.10.10.x`，从路由器侧再也直连不到 —— 所以"谁是主机"必须由用户明确指定，
 * 不能像一键组网那样默认用当前选中的设备。
 *
 * ## 为什么判据要单独一层
 *
 * 这些拒绝条件（拖到自己身上、目标离线、目标本身已经是别人的从机……）在界面上
 * 都表现为"拖过去没反应"，用户无法区分是手势没识别还是设备不允许。
 * 抽成纯函数就能一次测全，并在松手时立刻给出一句人话。
 */
object DragGrouping {

    /** 拖拽松手后要执行的动作，或者拒绝原因。 */
    sealed interface Decision {
        /** 可以把 [slaveName] 挂到 [masterName] 上。 */
        data class Group(val masterName: String, val slaveName: String) : Decision

        /** 拒绝，[reason] 是可以直接显示给人看的一句话。 */
        data class Reject(val reason: String) : Decision
    }

    /** 参与判定的设备视图（只取判据用得到的字段，避免依赖 Android 的 SavedDevice）。 */
    data class Node(
        val uuid: String,
        val name: String,
        val online: Boolean,
        /** 已被谁拉进组：`uuid → 主机名`。null 表示不在任何组里。 */
        val groupedUnder: String? = null,
    )

    /**
     * @param dragged 被拖起来（将要成为从机）的那台
     * @param target 松手时悬停（将要成为主机）的那台
     */
    fun resolve(dragged: Node?, target: Node?): Decision {
        if (dragged == null || target == null) {
            return Decision.Reject("没识别到目标音箱")
        }
        if (dragged.uuid == target.uuid) {
            return Decision.Reject("拖到自己身上了")
        }
        if (!target.online) {
            return Decision.Reject("「${target.name}」当前连不上，不能当主机")
        }
        if (!dragged.online && dragged.groupedUnder == null) {
            return Decision.Reject("「${dragged.name}」当前连不上")
        }
        // 目标本身是别人的从机：它没有热点可以被接入（从机的 ra0 不起作用）
        if (target.groupedUnder != null) {
            return Decision.Reject(
                "「${target.name}」已经在「${target.groupedUnder}」的组里，先从那边移出再试",
            )
        }
        // 已经在同一个组里：不要重复下发（命令会把从机重新清一遍，白等 40 秒）
        if (dragged.groupedUnder != null) {
            return Decision.Reject("「${dragged.name}」已在「${dragged.groupedUnder}」的组里")
        }
        return Decision.Group(masterName = target.name, slaveName = dragged.name)
    }

    /**
     * 松手时命中哪一台。
     *
     * 用矩形中心距离判断，而不是"必须完全覆盖"：手指盖住的面积远小于卡片，
     * 要求完全覆盖会让用户觉得怎么拖都不中。
     *
     * @param points 每台设备的中心点（屏幕坐标）到其 uuid
     * @param draggedUuid 被拖的那台（永不作为命中结果）
     * @param maxDistancePx 超过这个距离不算命中（避免拖到空白处却挂到最远那台）
     */
    fun hitTest(
        points: List<Triple<String, Float, Float>>,
        x: Float,
        y: Float,
        draggedUuid: String,
        maxDistancePx: Float,
    ): String? {
        var best: String? = null
        var bestDistance = maxDistancePx
        for ((uuid, px, py) in points) {
            if (uuid == draggedUuid) continue
            val dx = px - x
            val dy = py - y
            val distance = kotlin.math.sqrt(dx * dx + dy * dy)
            if (distance <= bestDistance) {
                bestDistance = distance
                best = uuid
            }
        }
        return best
    }
}
