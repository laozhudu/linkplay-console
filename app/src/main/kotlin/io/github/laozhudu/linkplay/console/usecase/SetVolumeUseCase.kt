package io.github.laozhudu.linkplay.console.usecase

/**
 * 音量下发的唯一入口。
 *
 * ## 先说清楚：这不是"把两条路合并成一条"
 *
 * 设备音量现在有两条路，**两条都该留着**：
 *
 * | 场景 | 走哪条 | 为什么 |
 * |---|---|---|
 * | 卡片/滑块拖动 | **合并通道**（60ms 取最新值） | 拖动一秒产生几十个值，每个都发 HTTP 会把老固件压垮 |
 * | 音量键 | **直发** | 离散事件，用户按一下就要立刻听到；多等 60ms 只会显得钝 |
 *
 * 所以合并它们是**错的**，那会让按键变钝。
 *
 * ## 那这里收的是什么
 *
 * 收的是「**只要有一个新的音量入口，就必须自己记得做三件事**」这件事：
 * 1. 先写 `VolumeBus`（界面立刻跟手）
 * 2. 再决定走哪条下发通道
 * 3. 并且这个判断不能各写一遍
 *
 * 现在有 4 个入口想改音量（卡片、音量键、播放页定值、播放页拖动）。
 * 每一个都自己写一遍上面三步，就会有某一个漏掉第 1 步 ——
 * 表现就是**界面显示 60、设备实际 40**（真机上撞过一次）。
 *
 * ## 用法
 *
 * ```
 * 拖动过程 → duringDrag()    // 合并下发，界面立刻跟手
 * 物理按键 → discrete()      // 立刻下发，不等合并窗口
 * ```
 */
class SetVolumeUseCase(
    /** 写全局音量（`VolumeBus.publishVolume`）。**两条路都必须先做这一步。** */
    private val publish: (ip: String, volume: Int) -> Unit,
    /** 合并通道（拖动专用）。 */
    private val sendMerged: (ip: String, volume: Int) -> Unit,
    /** 直发通道（离散事件专用）。 */
    private val sendNow: (ip: String, volume: Int) -> Unit,
) {

    /** 拖动中：界面立刻跟手，下发合并成一次。 */
    fun duringDrag(ip: String, volume: Int) {
        val v = volume.coerceIn(0, 100)
        publish(ip, v)
        sendMerged(ip, v)
    }

    /** 离散事件（音量键、语音「音量 +5」）：不等合并窗口，立刻发。 */
    fun discrete(ip: String, volume: Int) {
        val v = volume.coerceIn(0, 100)
        publish(ip, v)
        sendNow(ip, v)
    }

    /**
     * 只更新界面、不下发。
     *
     * 用于"读到设备新音量后同步显示"——那是别人的结果，不是要发命令。
     * 有这个入口是为了**不让调用方自己调 publish**（一旦允许，就会有人用它当前置步骤，
     * 结果重复 publish 两次）。
     */
    fun syncDisplayOnly(ip: String, volume: Int) {
        publish(ip, volume.coerceIn(0, 100))
    }
}
