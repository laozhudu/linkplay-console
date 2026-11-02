package io.github.laozhudu.linkplay.protocol.player

/**
 * 手机实体音量键 → 设备音量的换算（纯函数，可单测）。
 *
 * 原厂 App 的行为是「按手机音量键，音箱音量跟着动」。
 * 这里只做换算，不碰 IO —— 边界处理（0 与 100 到底后要不要继续吞事件）
 * 是最容易写错的地方，所以单独钉死。
 */
object VolumeStep {

    /** 每按一下调多少（0-100 刻度）。5 约等于 Android 系统 15 档里的一档。 */
    const val STEP: Int = 5

    /**
     * @param current 当前音量（0-100）；未知时传 null
     * @param delta +1 加一档，-1 减一档
     * @return 目标音量；[current] 为 null 时返回 null（调用方应放弃处理，交给系统）
     */
    fun next(current: Int?, delta: Int): Int? {
        if (current == null) return null
        return (current.coerceIn(0, 100) + delta * STEP).coerceIn(0, 100)
    }

    /**
     * 这一次按键该不该被 App 吞掉（不去改手机自己的音量）。
     *
     * 两种情况都要吞：
     * 1. 能算出目标音量 —— 正常调设备；
     * 2. **已经到 0 或 100** —— 若不吞，用户按到顶后继续按，会突然开始调手机媒体音量，
     *    体验上像"按着按着控制权跑了"。
     */
    fun shouldConsume(current: Int?, delta: Int): Boolean {
        // 只要知道当前音量就吞 —— 包括已经到 0/100 的情况。
        // 之前按"还能不能变"来判断，结果到顶后继续按就把控制权交回系统，
        // 用户会突然听见手机自己的媒体音量在动，非常困惑。
        return current != null && current in 0..100
    }
}
