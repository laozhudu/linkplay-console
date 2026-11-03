package io.github.laozhudu.linkplay.protocol.player

/**
 * 双通道播放引擎：UPnP 优先，掉线时自动降级到 httpapi（80 端口）。
 *
 * 为什么需要它：这台 DOSS DS-1831 的 UPnP 控制口（49152）会**静默关闭且不自己恢复**
 * （docs/10 §5）。之前只能靠 `reboot` 重启整机等 40 秒，代价是音乐中断。
 * 后来实测发现 `setPlayerCmd:play:<url>` 在 80 端口就能投流（docs/14 §0.1），
 * 于是「重启音箱」从唯一解降级成最后手段。
 *
 * 关键设计：
 * - **降级是自动的、可恢复的**：一次失败不足以判定通道死亡（网络抖动很常见），
 *   连续 [failureThreshold] 次才切；切过去之后每 [retryAfterMs] 试一次原通道，
 *   成功就切回来（UPnP 的状态字段更规范，能拿到 DIDL 元数据）。
 * - **读写共用同一套切换逻辑**：否则会出现「状态从 UPnP 读、命令走 httpapi」的错乱。
 * - **不在备用通道失败时假装切换**：两条都不通就是设备不可达，要如实抛出，
 *   否则用户会看到「已降级」却依然点不动，排查方向完全错。
 * - `now` 注入 → 单测能确定性地验证冷却，不用真等一分钟。
 */
class FallbackPlaybackEngine(
    private val primary: PlaybackEngine,
    private val secondary: PlaybackEngine,
    /** 降级后多久试一次原通道。太短会反复失败，太长则恢复后一直用功能较弱的通道。 */
    private val retryAfterMs: Long = 60_000,
    /**
     * 读失败的阈值。默认 **1**：状态轮询是一次便宜的操作，
     * 让一次超时冒到 UI 上没有任何好处（用户只看到「状态读不到」），立即换通道更好。
     */
    private val readFailureThreshold: Int = 1,
    /**
     * 写失败的阈值。默认 **2**：写操作有实际副作用（投流、改音量），
     * 一次网络抖动就切通道会让音箱收到的命令来源来回变，状态更难对齐。
     */
    private val writeFailureThreshold: Int = 2,
    private val now: () -> Long = { System.currentTimeMillis() },
) : PlaybackEngine {

    /** 当前实际使用的通道。 */
    var active: PlaybackEngine = primary
        private set

    /** 已降级。UI 可据此提示「UPnP 不可用，已切换到 80 端口」。 */
    val degraded: Boolean get() = active !== primary

    private var consecutiveFailures = 0
    private var degradedAt = 0L

    override val channelName: String get() = active.channelName

    override fun snapshot(): PlaybackSnapshot = attempt(readFailureThreshold) { it.snapshot() }

    override fun playUrl(url: String, title: String?) = attempt(writeFailureThreshold) { it.playUrl(url, title) }

    override fun resume() = attempt(writeFailureThreshold) { it.resume() }

    override fun pause() = attempt(writeFailureThreshold) { it.pause() }

    override fun stop() = attempt(writeFailureThreshold) { it.stop() }

    override fun seek(seconds: Long) = attempt(writeFailureThreshold) { it.seek(seconds) }

    override fun setVolume(volume: Int) = attempt(writeFailureThreshold) { it.setVolume(volume) }

    override fun setMute(mute: Boolean) = attempt(writeFailureThreshold) { it.setMute(mute) }

    override fun next() = attempt(writeFailureThreshold) { it.next() }

    override fun previous() = attempt(writeFailureThreshold) { it.previous() }

    // ---- 内部 ----

    /**
     * 在一个通道上执行；失败则累计，达到阈值就切到备用并**重放一次**。
     *
     * 重放是否安全：这里所有操作都是幂等写（投同一个 URL、设同一个音量、暂停已暂停的播放），
     * 所以失败后换通道重试不会造成副作用叠加。
     */
    private inline fun <T> attempt(threshold: Int, op: (PlaybackEngine) -> T): T {
        maybeRecover()
        val used = active
        return try {
            op(used).also { consecutiveFailures = 0 }
        } catch (first: Throwable) {
            consecutiveFailures++
            if (used !== primary) throw first          // 备用通道也失败 → 设备不可达，如实报
            if (consecutiveFailures < threshold) throw first
            active = secondary
            degradedAt = now()
            consecutiveFailures = 0
            op(secondary)                              // 切过去重放一次
        }
    }

    /** 冷却到点就试一次原通道；成功则切回。只读探测，不产生副作用。 */
    private fun maybeRecover() {
        if (active === primary) return
        if (now() - degradedAt < retryAfterMs) return
        runCatching { primary.snapshot() }.onSuccess {
            active = primary
            consecutiveFailures = 0
        }
    }
}
