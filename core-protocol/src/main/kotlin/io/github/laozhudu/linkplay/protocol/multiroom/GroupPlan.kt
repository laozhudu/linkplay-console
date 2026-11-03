package io.github.laozhudu.linkplay.protocol.multiroom

/**
 * 勾选式分组（照 Sonos 的 Grouping 菜单做）。
 *
 * ## 为什么不再只有拖拽
 *
 * Sonos 的官方分组交互是**勾选房间 → 应用**，拖拽从来不是主路径：
 * 勾选式有三个拖拽给不了的东西 —— 能一次选多台、能看见"当前在哪些组里"、
 * 能在应用前取消。用户反馈"拖拽不好用"就是缺这三样。
 *
 * 这里只做**差异计算**：给定"现在在组里的"和"用户勾完的"，算出要加入谁、要移出谁。
 * 执行（发命令、轮询、等从机回来）在 ViewModel 里，这样这段能被单测完全覆盖。
 *
 * ## 一条硬约束
 *
 * 一台设备只能在**一个**组里。所以应用新选择时，被取消勾选的设备必须先移出，
 * 否则它会留在旧组里 —— 界面上表现为"取消了但还在同步"。
 */
object GroupPlan {

    /**
     * @param current 当前组内的设备 uuid（不含主机本身，或含都行，见 [normalize]）
     * @param selected 用户勾选的设备 uuid（同样可以含主机）
     * @param masterUuid 主机 uuid；勾选里没有它时以它为准（主机不会因为没勾就不当主机）
     */
    data class Plan(
        /** 需要加入组的从机 uuid。 */
        val join: List<String>,
        /** 需要移出组的从机 uuid。 */
        val leave: List<String>,
    ) {
        val empty: Boolean get() = join.isEmpty() && leave.isEmpty()

        /** 给用户看的一句话（应用前预览、应用后回执都用它）。 */
        fun describe(): String = when {
            empty -> "没有变化"
            join.isNotEmpty() && leave.isEmpty() -> "将加入 ${join.size} 台"
            join.isEmpty() && leave.isNotEmpty() -> "将移出 ${leave.size} 台"
            else -> "加入 ${join.size} 台、移出 ${leave.size} 台"
        }
    }

    /**
     * 计算差异。**主机不参与 join/leave**（它是组的锚点）。
     *
     * @param current 当前从机集合
     * @param selected 勾选集合
     * @param masterUuid 主机（从两侧剔除）
     */
    fun diff(current: Set<String>, selected: Set<String>, masterUuid: String?): Plan {
        val cur = normalize(current, masterUuid)
        val sel = normalize(selected, masterUuid)
        return Plan(
            join = (sel - cur).sorted(),
            leave = (cur - sel).sorted(),
        )
    }

    /** 剔除空值、大小写归一、去掉主机自己。 */
    fun normalize(uuids: Set<String>, masterUuid: String?): Set<String> {
        val master = masterUuid?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
        return uuids.mapNotNull { it.trim().lowercase().takeIf { s -> s.isNotEmpty() } }
            .filter { it != master }
            .toSet()
    }

    /**
     * 「Everywhere」（Sonos 在 3 台以上时给的一键全选）。
     *
     * 只在设备数 ≥ 3 时提供 —— 两台的时候"全选"就等于"都加上"，没有必要多一个按钮。
     */
    fun everywhere(available: Collection<String>, masterUuid: String?): Set<String> =
        normalize(available.toSet(), masterUuid)

    fun showEverywhere(deviceCount: Int): Boolean = deviceCount >= 3
}
