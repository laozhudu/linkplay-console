package io.github.laozhudu.linkplay.console.provisioning

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.laozhudu.linkplay.discovery.DiscoveredDevice
import io.github.laozhudu.linkplay.discovery.SsdpDiscovery
import io.github.laozhudu.linkplay.protocol.ApScanItem
import io.github.laozhudu.linkplay.protocol.DeviceHttpClient
import io.github.laozhudu.linkplay.protocol.LinkPlayCommands
import io.github.laozhudu.linkplay.protocol.LinkPlayStatus
import io.github.laozhudu.linkplay.protocol.ProvisioningPoller
import io.github.laozhudu.linkplay.protocol.ProvisioningResult
import io.github.laozhudu.linkplay.protocol.ProvisioningRules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import io.github.laozhudu.linkplay.console.diagnostics.safeLaunch

/** 配网流程的四个步骤，UI 按这个顺序渲染。 */
enum class ProvisionStep { CONNECT_TO_DEVICE_AP, FIND_DEVICE, PICK_NETWORK, ENTER_PASSWORD, SENDING }

data class ProvisionUiState(
    val step: ProvisionStep = ProvisionStep.CONNECT_TO_DEVICE_AP,
    val deviceIp: String = "",
    val manualIp: String = "",
    val finding: Boolean = false,
    val findLog: List<String> = emptyList(),
    val aps: List<ApScanItem> = emptyList(),
    val scanning: Boolean = false,
    val manualSsid: String = "",
    val selectedAp: ApScanItem? = null,
    val password: String = "",
    val isOpenNetwork: Boolean = false,
    val showPassword: Boolean = false,
    val sending: Boolean = false,
    val probeCount: Int = 0,
    val elapsedMs: Long = 0,
    val result: ProvisioningResult? = null,
    val validationError: String? = null,
    val error: String? = null,
)

class ProvisioningViewModel : ViewModel() {

    private val client = DeviceHttpClient()
    private val discovery = SsdpDiscovery(timeoutMs = 2_500)
    private val poller = ProvisioningPoller(intervalMs = 2_000, maxConsecutiveFailures = 4)

    private val _state = MutableStateFlow(ProvisionUiState())
    val state: StateFlow<ProvisionUiState> = _state.asStateFlow()

    fun acknowledgeApConnected() {
        _state.update { it.copy(step = ProvisionStep.FIND_DEVICE) }
    }

    fun setManualIp(value: String) = _state.update { it.copy(manualIp = value, validationError = null) }

    fun useManualIp() {
        val ip = _state.value.manualIp.trim()
        if (ip.isEmpty()) {
            _state.update { it.copy(validationError = "请输入设备地址") }
            return
        }
        onDeviceLocated(ip)
    }

    /** SSDP 找设备；找不到时探测一组「LinkPlay 常见默认地址」（未确证，仅作提示）。 */
    fun findDevice() {
        if (_state.value.finding) return
        _state.update { it.copy(finding = true, error = null, findLog = emptyList()) }
        safeLaunch("findDevice") {
            try {
                val found = withContext(Dispatchers.IO) { discovery.discover() }
                log("SSDP 返回 ${found.size} 个响应")
                if (found.isNotEmpty()) {
                    val ip = pickBest(found)
                    log("选中 ${ip.ip}${ip.server?.let { " ($it)" } ?: ""}")
                    withContext(Dispatchers.IO) { verifyReachable(ip.ip) }
                    onDeviceLocated(ip.ip)
                    return@safeLaunch
                }

                log("SSDP 无响应，尝试常见默认地址…")
                val hit = withContext(Dispatchers.IO) { probeDefaults() }
                if (hit != null) {
                    log("默认地址命中 $hit")
                    onDeviceLocated(hit)
                } else {
                    _state.update {
                        it.copy(
                            finding = false,
                            error = "没找到设备。确认手机已连到音箱热点（LinkPlay_xxxx），或手动输入地址。",
                        )
                    }
                }
            } catch (t: Throwable) {
                _state.update { it.copy(finding = false, error = t.message ?: "查找失败") }
            }
        }
    }

    fun onDeviceLocated(ip: String) {
        _state.update {
            it.copy(step = ProvisionStep.PICK_NETWORK, deviceIp = ip, finding = false, error = null)
        }
        scanNetworks()
    }

    fun scanNetworks() {
        if (_state.value.scanning) return
        val ip = _state.value.deviceIp
        _state.update { it.copy(scanning = true, error = null, aps = emptyList()) }
        safeLaunch("scanNetworks") {
            try {
                val list = withContext(Dispatchers.IO) { client.scanAccessPoints(ip) }
                _state.update { it.copy(scanning = false, aps = list.sortedByDescending { it.rssi ?: -999 }) }
                if (list.isEmpty()) {
                    _state.update { current ->
                        current.copy(error = "设备未扫到 Wi-Fi（可能刚重启）。可手动输入 SSID。")
                    }
                }
            } catch (t: Throwable) {
                _state.update { it.copy(scanning = false, error = "扫描失败：${t.message}") }
            }
        }
    }

    fun setManualSsid(value: String) = _state.update { it.copy(manualSsid = value) }

    fun selectAp(ap: ApScanItem) {
        _state.update {
            it.copy(
                selectedAp = ap,
                isOpenNetwork = ap.isOpen,
                step = ProvisionStep.ENTER_PASSWORD,
                validationError = null,
            )
        }
    }

    fun useManualSsid() {
        val ssid = _state.value.manualSsid.trim()
        if (ssid.isEmpty()) {
            _state.update { it.copy(validationError = "请输入 SSID") }
            return
        }
        _state.update {
            it.copy(
                selectedAp = ApScanItem(ssid, null, null, 0, ProvisioningRules.AUTH_WPA2, ProvisioningRules.ENCRY_AES, null),
                isOpenNetwork = false,
                step = ProvisionStep.ENTER_PASSWORD,
                validationError = null,
                error = null,
            )
        }
    }

    fun setPassword(value: String) = _state.update { it.copy(password = value, validationError = null) }

    fun setOpenNetwork(open: Boolean) = _state.update { it.copy(isOpenNetwork = open, validationError = null) }

    fun toggleShowPassword() = _state.update { it.copy(showPassword = !it.showPassword) }

    fun back() {
        _state.update {
            when (it.step) {
                ProvisionStep.ENTER_PASSWORD -> it.copy(step = ProvisionStep.PICK_NETWORK, result = null, validationError = null)
                ProvisionStep.PICK_NETWORK -> it.copy(step = ProvisionStep.FIND_DEVICE)
                ProvisionStep.FIND_DEVICE -> it.copy(step = ProvisionStep.CONNECT_TO_DEVICE_AP)
                else -> it
            }
        }
    }

    fun submit() {
        val s = _state.value
        val ssid = s.selectedAp?.ssid ?: return
        val password = if (s.isOpenNetwork) "" else s.password

        val invalid = ProvisioningRules.validateCredentials(ssid, password)
        if (invalid != null) {
            _state.update { it.copy(validationError = invalid) }
            return
        }

        _state.update {
            it.copy(
                step = ProvisionStep.SENDING,
                sending = true,
                password = password,
                result = null,
                error = null,
                probeCount = 0,
                elapsedMs = 0,
            )
        }

        safeLaunch("submit") {
            val channel = s.selectedAp.channel.takeIf { it > 0 } ?: 0
            val sent = runCatching {
                withContext(Dispatchers.IO) {
                    client.connectMasterAp(s.deviceIp, ssid, channel, password)
                }
            }
            if (sent.isFailure) {
                _state.update {
                    it.copy(
                        sending = false,
                        error = "下发失败：${sent.exceptionOrNull()?.message}",
                        step = ProvisionStep.ENTER_PASSWORD,
                    )
                }
                return@safeLaunch
            }

            // 设备只回 OK，必须自己轮询确认。用真实时钟，但每轮把进度推给 UI。
            val result = withContext(Dispatchers.IO) {
                val started = System.currentTimeMillis()
                poller.poll(
                    targetSsid = ssid,
                    timeoutMs = 90_000,
                    probe = {
                        _state.update { cur ->
                            cur.copy(
                                probeCount = cur.probeCount + 1,
                                elapsedMs = System.currentTimeMillis() - started,
                            )
                        }
                        runCatching { client.status(s.deviceIp) }.getOrNull()
                    },
                )
            }
            _state.update { it.copy(sending = false, result = result) }
        }
    }

    fun restart() {
        _state.update {
            ProvisionUiState(step = ProvisionStep.FIND_DEVICE, deviceIp = it.deviceIp)
        }
    }

    // ---- 内部 ----

    private fun log(line: String) = _state.update { it.copy(findLog = (it.findLog + line).takeLast(50)) }

    private fun pickBest(found: List<DiscoveredDevice>): DiscoveredDevice =
        found.firstOrNull { it.searchTarget == SsdpDiscovery.ST_WIIMU_PLAYQUEUE }
            ?: found.firstOrNull { it.location != null }
            ?: found.first()

    private fun verifyReachable(ip: String): Boolean =
        runCatching { client.status(ip) }.isSuccess

    private fun probeDefaults(): String? = DEFAULT_DEVICE_ADDRESSES.firstOrNull { verifyReachable(it) }

    companion object {
        /**
         * LinkPlay 系设备在 AP 模式下的常见地址。
         * **未确证**：本样本 APK 里没有硬编码设备 IP（它靠 SSDP 发现），
         * 这里只是给用户一个「大概率能中」的候选列表，命中率取决于固件版本。
         */
        val DEFAULT_DEVICE_ADDRESSES = listOf(
            "192.168.4.1",
            "10.10.10.254",
            "192.168.43.1",
            "192.168.169.1",
        )
    }
}
