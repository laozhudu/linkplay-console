package io.github.laozhudu.linkplay.console.provisioning

import io.github.laozhudu.linkplay.console.diagnostics.SourceFailure
import io.github.laozhudu.linkplay.protocol.ProvisioningResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference

/**
 * 配网的最近状态，供诊断页渲染。
 *
 * 形状照抄 `nas/NasSourceStatus.kt`（`docs/65` 批次 1 模板）。
 *
 * ## 这个源是「命令回 OK ≠ 功能生效」最典型的一次
 *
 * `connectMasterAp` 只要命令发出去就回 OK —— 它**不代表**设备连上了新 WiFi。
 * 真正生效的判据是"设备在新网络里能被探测到"。中间那 90 秒里，用户看到的只是转圈。
 *
 * 所以报告必须把两段分开写：
 * ```
 *   下发=SSID=MyWiFi channel=6 结果=OK（← 设备只回 OK，不等于连上了）
 *   等待=探测 15 次 / 90.0s → 超时（设备一直没在新网络里出现）
 * ```
 *
 * 只写"下发成功"会让人以为配网成功了 —— 这个项目已经因为同一类误判坑过四次。
 */
object ProvisionSourceStatus {

    private val ref = AtomicReference(ProvisionStatus())

    fun snapshot(): ProvisionStatus = ref.get()

    private fun mutate(transform: (ProvisionStatus) -> ProvisionStatus) {
        ref.updateAndGet(transform)
    }

    fun reset() = ref.set(ProvisionStatus())

    fun located(ip: String) = mutate { it.copy(deviceIp = ip) }

    fun scanned(count: Int) = mutate { it.copy(apsFound = count) }

    fun sendStarted(ssid: String, channel: Int) = mutate {
        it.copy(
            ssid = ssid,
            channel = channel,
            sentOk = null,
            sendDetail = null,
            result = null,
            probes = 0,
            elapsedMs = 0,
            failure = null,
        )
    }

    fun sendFailed(error: Throwable) = mutate {
        it.copy(
            sentOk = false,
            sendDetail = error.message,
            failure = SourceFailure.of(error).describe("下发失败：${error.message}"),
            failureAt = System.currentTimeMillis(),
        )
    }

    fun sendAccepted() = mutate { it.copy(sentOk = true) }

    fun finished(result: ProvisioningResult, probes: Int, elapsedMs: Long) = mutate {
        val text: String
        val failure: String?
        when (result) {
            is ProvisioningResult.Connected -> {
                text = "成功（设备已在新网络里应答）"
                failure = null
            }
            is ProvisioningResult.JoinedButOffline -> {
                text = "加入了但设备离线"
                failure = SourceFailure.UNREACHABLE.describe("加入成功却探测不到设备（可能没拿到 IP）")
            }
            is ProvisioningResult.Timeout -> {
                text = "超时（${elapsedMs / 1000} 秒没等到设备上线）"
                // 这一句必须写全：命令是回了 OK 的，问题在"没等到 IP"
                failure = SourceFailure.TIMEOUT.describe("命令回了 OK，但 ${elapsedMs / 1000} 秒内没等到 IP")
            }
            is ProvisioningResult.Unreachable -> {
                text = "连不上设备"
                failure = SourceFailure.UNREACHABLE.describe(result.lastError)
            }
        }
        it.copy(
            result = text,
            probes = probes,
            elapsedMs = elapsedMs,
            failure = failure,
            failureAt = if (failure != null) System.currentTimeMillis() else it.failureAt,
        )
    }

    fun render(): String {
        val s = snapshot()
        return buildString {
            if (s.deviceIp == null && s.apsFound < 0 && s.ssid == null) {
                appendLine("  还没进过配网页")
                return@buildString
            }
            s.deviceIp?.let { appendLine("  设备=$it") }
            if (s.apsFound >= 0) appendLine("  扫到 ${s.apsFound} 个 AP")
            if (s.ssid != null) {
                appendLine(
                    "  下发=SSID=${s.ssid}  channel=${s.channel}" +
                        when (s.sentOk) {
                            true -> "  结果=OK（← 设备只回 OK，不等于连上了）"
                            false -> "  结果=失败"
                            null -> "  结果=进行中"
                        },
                )
            }
            s.result?.let { appendLine("  等待=探测 ${s.probes} 次 / ${seconds(s.elapsedMs)} → $it") }
            s.failure?.let { appendLine("  最近失败=${clock(s.failureAt)} $it") }
        }
    }

    private fun seconds(ms: Long): String = when {
        ms <= 0L -> "-"
        ms < 1000 -> "${ms}ms"
        else -> String.format(Locale.US, "%.1fs", ms / 1000.0)
    }

    private fun clock(ms: Long): String =
        if (ms <= 0L) "-" else SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(ms))
}

/** 一次快照。字段都是给人看的，会在诊断页原样出现。 */
data class ProvisionStatus(
    val deviceIp: String? = null,
    /** -1 = 还没扫过。 */
    val apsFound: Int = -1,
    val ssid: String? = null,
    val channel: Int = 0,
    /** null = 还没下发。 */
    val sentOk: Boolean? = null,
    val sendDetail: String? = null,
    val result: String? = null,
    val probes: Int = 0,
    val elapsedMs: Long = 0L,
    val failureAt: Long = 0L,
    val failure: String? = null,
)
