package io.github.laozhudu.linkplay.protocol

/**
 * 配网（AP 直连）的客户端侧逻辑。
 *
 * 为什么要单独一个文件：设备对 `ConnectMasterAp` **只回一个空/OK 响应**，
 * 原厂 App 收到就打个 log 完事，**从不校验是否真的连上**。重写时把「下发 → 轮询确认」
 * 做成显式状态机，用户才能看到「正在连」「连上了」「超时了」，而不是黑盒等。
 */
object ProvisioningRules {

    const val SSID_MAX_BYTES = 32
    const val WPA2_PASS_MIN = 8
    const val WPA2_PASS_MAX = 63

    const val AUTH_WPA2 = "WPA2PSK"
    const val AUTH_OPEN = "OPEN"
    const val ENCRY_AES = "AES"
    const val ENCRY_NONE = "NONE"

    /** 客户端侧预校验。返回 null 表示合法，否则返回给用户看的原因。 */
    fun validateCredentials(ssid: String, password: String): String? {
        if (ssid.isEmpty()) return "SSID 不能为空"
        if (ssid.toByteArray(Charsets.UTF_8).size > SSID_MAX_BYTES) {
            return "SSID 超过 $SSID_MAX_BYTES 字节（UTF-8），设备会截断"
        }
        if (ssid.any { it.code < 0x20 }) return "SSID 含控制字符"
        if (password.isEmpty()) return null // OPEN 网络
        if (password.length < WPA2_PASS_MIN || password.length > WPA2_PASS_MAX) {
            return "WPA2 密码需 $WPA2_PASS_MIN-$WPA2_PASS_MAX 位，当前 ${password.length} 位"
        }
        return null
    }

    /** 按密码是否为空推断 auth/encry 组合（与原厂一致）。 */
    fun authFor(password: String): String = if (password.isEmpty()) AUTH_OPEN else AUTH_WPA2

    fun encryFor(password: String): String = if (password.isEmpty()) ENCRY_NONE else ENCRY_AES
}

/** 配网轮询的终态。 */
sealed interface ProvisioningResult {
    /** 设备已连上目标 SSID 且报告联网。 */
    data class Connected(val status: LinkPlayStatus) : ProvisioningResult

    /** 设备已切到目标 SSID，但还没拿到 IP / 未联网。 */
    data class JoinedButOffline(val status: LinkPlayStatus) : ProvisioningResult

    /** 等满超时仍未连上目标 SSID；带上最后一次观测到的状态便于排障。 */
    data class Timeout(val lastSeen: LinkPlayStatus?) : ProvisioningResult

    /** 探测过程中设备彻底不可达（掉线/换 IP）。 */
    data class Unreachable(val lastError: String?) : ProvisioningResult
}

/**
 * 轮询器：注入 `probe` / `sleep` / `now`，因此完全是纯函数式的，可在单测里用假时钟验证
 * 「轮询几次、何时放弃」，不需要真机也不需要真等待。
 *
 * 设备在配网成功后会从 AP 模式切到 STA 模式，**原 AP 地址随即失效**，所以轮询时
 * 一旦探测失败不应立刻判失败，而是允许连续若干次失败（设备正在重启网络栈）。
 */
class ProvisioningPoller(
    private val intervalMs: Long = 2_000,
    private val maxConsecutiveFailures: Int = 3,
) {
    fun poll(
        targetSsid: String,
        timeoutMs: Long = 60_000,
        probe: () -> LinkPlayStatus?,
        sleep: (Long) -> Unit = { Thread.sleep(it) },
        now: () -> Long = { System.currentTimeMillis() },
    ): ProvisioningResult {
        val deadline = now() + timeoutMs
        var lastSeen: LinkPlayStatus? = null
        var lastError: String? = null
        var consecutiveFailures = 0

        while (now() < deadline) {
            val status = try {
                probe()
            } catch (t: Throwable) {
                lastError = t.message ?: t.javaClass.simpleName
                null
            }

            if (status == null) {
                consecutiveFailures++
                if (consecutiveFailures > maxConsecutiveFailures) {
                    return ProvisioningResult.Unreachable(lastError)
                }
            } else {
                consecutiveFailures = 0
                lastSeen = status
                val joined = status.essid != null && status.essid == targetSsid
                if (joined && status.isOnline) return ProvisioningResult.Connected(status)
                if (joined) {
                    // 已切到目标 SSID，继续等它拿到 IP；不再受 maxConsecutiveFailures 影响。
                    if (now() + intervalMs >= deadline) {
                        return ProvisioningResult.JoinedButOffline(status)
                    }
                }
            }

            sleep(intervalMs)
        }
        return ProvisioningResult.Timeout(lastSeen)
    }
}
