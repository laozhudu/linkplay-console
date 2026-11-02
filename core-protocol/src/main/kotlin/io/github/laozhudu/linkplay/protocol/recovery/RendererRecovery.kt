package io.github.laozhudu.linkplay.protocol.recovery

/**
 * 控制面健康度。
 *
 * 为什么需要分三态而不是「在线/离线」：这台 DOSS DS-1831 实测会**只掉 UPnP 不掉 httpapi**
 * （49152 变 `connection refused`，而 `httpapi.asp` 一直 200）。用户看到的现象是
 * 「设备明明连着 Wi-Fi，就是点不动」，如果不区分，就会误报「设备离线」让用户去查网络。
 */
enum class RendererHealth {
    /** 两条通道都通。 */
    HEALTHY,

    /** httpapi 通但 UPnP 不通：设备在线，控制面挂了。可尝试 `reboot` 恢复。 */
    CONTROL_LOST,

    /** 两条都不通：真的离线（断电/换 IP/掉网）。只能等。 */
    OFFLINE,
}

/** 探活/重启所需的最小能力，抽成接口以便单测注入假实现。 */
interface RendererProbe {
    /** 80 端口的 httpapi 是否可用。 */
    fun httpApiOk(): Boolean

    /** UPnP 控制端点（默认 49152）是否可用。 */
    fun upnpOk(): Boolean

    /** 触发设备重启。返回是否成功下发。 */
    fun reboot(): Boolean
}

/**
 * 控制面自愈。
 *
 * 实测时间线（docs/10 §5.1）：发出裸 `reboot` 后约 **40 秒** 恢复（10s 时还在重启、20~30s ping 不通、
 * 40s 回来且 49152 重新 open）。所以默认超时给 60 秒，且轮询期间不假设「一 ping 通就绪」——
 * 必须等 UPnP 端口也开。
 *
 * 关键设计：
 * - `sleep` / `now` 注入 → 单测用假时钟，不真等 60 秒；
 * - [heal] 只对 `CONTROL_LOST` 动手重启，`OFFLINE` 时重启命令也发不出去，发了只会浪费 40 秒；
 * - 重启后判定标准是 **UPnP 也通**，而不是 httpapi 通就算成功（否则会立即再失败一轮）。
 */
class RendererRecovery(
    private val probe: RendererProbe,
    private val sleep: (Long) -> Unit = { Thread.sleep(it) },
    private val now: () -> Long = { System.currentTimeMillis() },
) {
    fun diagnose(): RendererHealth {
        // 先看 UPnP：通了就是好的，省一次 httpapi 请求
        if (probe.upnpOk()) return RendererHealth.HEALTHY
        return if (probe.httpApiOk()) RendererHealth.CONTROL_LOST else RendererHealth.OFFLINE
    }

    /**
     * 尝试恢复到 [RendererHealth.HEALTHY]。
     *
     * @param timeoutMs 总预算；本轮实测 40s 恢复，默认 60s 留余量
     * @return 恢复成功为 true
     */
    fun heal(timeoutMs: Long = 60_000, pollMs: Long = 2_000): Boolean {
        when (diagnose()) {
            RendererHealth.HEALTHY -> return true
            RendererHealth.OFFLINE -> return false // 发不出重启命令，别浪费预算
            RendererHealth.CONTROL_LOST -> Unit
        }
        if (!probe.reboot()) return false

        val deadline = now() + timeoutMs
        while (now() < deadline) {
            sleep(pollMs)
            // 停止条件是「UPnP 也通」：只看 httpapi 的话，重启刚起来的瞬间就会误判成功
            if (probe.upnpOk()) return true
            if (now() >= deadline) break
        }
        return false
    }
}
