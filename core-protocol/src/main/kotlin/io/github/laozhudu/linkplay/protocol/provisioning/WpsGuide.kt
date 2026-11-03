package io.github.laozhudu.linkplay.protocol.provisioning

/**
 * WPS 配网引导的四个状态。
 *
 * 为什么要有 [Cancelled]：原厂 `SlaveSpeakerAddWPSActivity` 在用户退回上一页时会
 * 把「等待中」的界面收起来。若状态机只有 等待/找到/超时，UI 上就会出现
 * 「明明点了返回，进度条还在跑」这种自相矛盾的样子。
 */
enum class WpsStep {
    /** 还在等（音箱应该正处在 WPS 配对模式）。 */
    Waiting,

    /** 发现了新设备，配对成功。 */
    Found,

    /** 等到超时也没出现新设备。 */
    Timeout,

    /** 用户取消，或页面被关掉。 */
    Cancelled,
}

/**
 * WPS 配网的判定（纯 JVM，可单测）。
 *
 * ## 这条路径和 AP 直连的区别
 *
 * AP 直连是「手机连音箱热点 → 下发路由器 SSID/密码」；WPS 是**音箱自己**通过
 * 路由器的 WPS 通道入网，手机只需要在同一个局域网里等它出现（原厂就是
 * 「打开音箱 → 长按 WPS 键 3 秒 → 手机上转圈 → 出设备」）。
 *
 * 所以这里的判定只有两件输入：**等了多久** 和 **看见几台新设备**。
 * 把它抽成纯函数的意义：超时边界（59.9s / 60.0s）、刚好此刻找到设备、
 * 进度条的夹取范围这些，真机上根本没法精确复现，只能靠单测锁死。
 */
object WpsGuide {

    /** 默认等待 60 秒。原厂给的时间也在这个量级，短于 40 秒用户还没来得及去按键。 */
    const val DEFAULT_TIMEOUT_MS: Long = 60_000L

    /** 轮询间隔：每 2 秒扫一次局域网。 */
    const val POLL_INTERVAL_MS: Long = 2_000L

    /**
     * 当前该显示哪个状态。
     *
     * **找到优先于超时**：扫描是 2 秒一次，最后一轮的结果常常在超时判定之后才回来。
     * 若先判超时，用户会看到「60 秒内没发现设备」，而下一帧设备就出现在列表里了 ——
     * 这是最伤人的那种误报。
     *
     * @param elapsedMs 从点击「开始」到现在的毫秒数
     * @param foundCount 本次等待中新出现的设备台数
     */
    fun tick(
        elapsedMs: Long,
        foundCount: Int,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    ): WpsStep = when {
        foundCount > 0 -> WpsStep.Found
        elapsedMs >= timeoutMs -> WpsStep.Timeout
        else -> WpsStep.Waiting
    }

    /**
     * 进度条的值（0..1）。
     *
     * 夹取是必须的：计时用的 `SystemClock` 若被使用者传成负数、或倒计时被外部改小，
     * `LinearProgressIndicator(progress = 3.7f)` 在真机上会直接抛异常（要求 0..1）。
     * `timeoutMs <= 0` 视为「立即完成」，返回 1 而不是除零得到 NaN。
     */
    fun progress(elapsedMs: Long, timeoutMs: Long = DEFAULT_TIMEOUT_MS): Float {
        if (timeoutMs <= 0L) return 1f
        return (elapsedMs.toFloat() / timeoutMs.toFloat()).coerceIn(0f, 1f)
    }
}
