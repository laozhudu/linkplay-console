package io.github.laozhudu.linkplay.console.diagnostics

/**
 * 用户此刻在界面的哪一处 —— 报告里必须有这一条。
 *
 * ## 为什么界面位置属于"诊断"
 *
 * 用户报问题时的描述是「点了没反应」「按钮不灵」。而**同样的现象在不同页面是不同的问题**：
 * - 在首页点卡片没反应 → 可能是选中设备失败
 * - 在播放页点暂停没反应 → 可能是 PlaybackViewModel 那条链路
 * - 有个对话框开着时点任何地方都没反应 → 对话框挡住了（最常见，也最容易被误报成 bug）
 *
 * 没有这个信息，我只能问「你在哪一页点的」——而这正是用户最不耐烦回答的问题
 * （他觉得"我说了没反应啊"）。
 *
 * ## 为什么不用 Compose 的状态
 *
 * 诊断页和首页不在同一棵 Compose 树里，拿不到对方的 `remember` 状态。
 * 而且报告是**快照**，要的是"此刻在哪儿"，不是"随状态重组"。
 * 所以用一个极简的全局快照，写入方负责在页面变化时更新。
 *
 * ## 线程
 *
 * 写入在 Compose 主线程，读取在诊断页（也是主线程），但报告生成可能来自 IO。
 * 用 `@Volatile` 保证可见性 —— 这里不需要原子性（三个字段各自独立，读到新旧混搭也无妨，
 * 反而比加锁简单）。
 */
object NavTrace {

    /** 当前一级页：设备 / 播放 / 媒体库。 */
    @Volatile
    var tab: String = "设备"

    /** 当前二级页（`null` 表示在一级页上）。 */
    @Volatile
    var subPage: String? = null

    /**
     * 当前开着的对话框 / 浮层（`null` 表示没有）。
     *
     * 这个字段排障价值最高：**对话框开着时点背景任何地方都不响应**，
     * 用户很容易把它描述成"按钮坏了"。
     */
    @Volatile
    var overlay: String? = null

    /** 音量浮层（OSD）是否正在显示 —— 它是全屏遮罩，同样会吃掉点击。 */
    @Volatile
    var volumeOsdVisible: Boolean = false

    fun render(): String = buildString {
        appendLine("  一级页=$tab")
        appendLine("  二级页=" + (subPage ?: "无"))
        // 有浮层时显式标出来，并说清后果 —— 这是最常见的"点了没反应"原因
        appendLine(
            "  浮层=" + (overlay ?: "无") +
                if (overlay != null) "  ← 浮层开着时点背景不会响应" else "",
        )
        if (volumeOsdVisible) appendLine("  音量浮层=显示中  ← 全屏遮罩，会吃掉点击")
    }

    /** 测试用。 */
    fun reset() {
        tab = "设备"
        subPage = null
        overlay = null
        volumeOsdVisible = false
    }
}
