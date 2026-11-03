package io.github.laozhudu.linkplay.protocol.player

/**
 * 定时关闭（原厂长按设备菜单里的第四项）。
 *
 * ## 为什么是客户端倒计时
 *
 * 反编译里原厂这条走的是 `AlarmQueue` 之外的本地 `Timer`：
 * 到点由 App 自己下发停止命令。设备端**没有**"播完 N 分钟后自己停"的接口
 * （`AlarmQueue` 是闹钟，到点开始播，语义正好相反）。
 *
 * 做成纯函数是为了能测：时间由调用方传 `nowMs`，不依赖系统时钟。
 *
 * ## 注意一个真机事实
 *
 * 到点只能停"当前这台"。多房间的从机在主机 AP 网段里，App 直接够不着，
 * 停主机即可（主机停 → 从机没有流可放）。
 */
data class SleepTimer(
    /** 预设分钟数，0 表示关闭。 */
    val minutes: Int = 0,
    /** 到期时间戳（ms）。null = 没开。 */
    val endAtMs: Long? = null,
) {
    val active: Boolean get() = endAtMs != null

    fun remainingMs(nowMs: Long): Long {
        val end = endAtMs ?: return 0
        return (end - nowMs).coerceAtLeast(0)
    }

    fun remainingText(nowMs: Long): String {
        if (!active) return ""
        val total = (remainingMs(nowMs) + 999) / 1000      // 向上取整，避免最后一秒显示 0:00 还在放
        val m = total / 60
        val s = total % 60
        return "%d:%02d".format(m, s)
    }

    /** 到点该停了。没开定时器时永远 false（不能因为 endAt 为 null 就误判到期）。 */
    fun isDue(nowMs: Long): Boolean = endAtMs?.let { nowMs >= it } ?: false

    fun start(minutes: Int, nowMs: Long): SleepTimer {
        if (minutes <= 0) return SleepTimer()
        return SleepTimer(minutes = minutes, endAtMs = nowMs + minutes * 60_000L)
    }

    fun cancel(): SleepTimer = SleepTimer()

    companion object {
        /** 原厂菜单里给的档位（分钟）。 */
        val PRESETS: List<Int> = listOf(15, 30, 45, 60, 90, 120)
    }
}
