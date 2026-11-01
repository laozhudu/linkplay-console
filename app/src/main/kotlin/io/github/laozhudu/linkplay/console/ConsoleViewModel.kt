package io.github.laozhudu.linkplay.console

import android.app.Application
import android.content.Context
import android.net.wifi.WifiManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.laozhudu.linkplay.discovery.DiscoveredDevice
import io.github.laozhudu.linkplay.discovery.SsdpDiscovery
import io.github.laozhudu.linkplay.protocol.DeviceHttpClient
import io.github.laozhudu.linkplay.protocol.LinkPlayCommands
import io.github.laozhudu.linkplay.protocol.LinkPlayStatus
import io.github.laozhudu.linkplay.protocol.SlaveDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ConsoleUiState(
    val scanning: Boolean = false,
    val devices: List<DiscoveredDevice> = emptyList(),
    val selectedIp: String? = null,
    val status: LinkPlayStatus? = null,
    val slaves: List<SlaveDevice> = emptyList(),
    val log: List<String> = emptyList(),
    val error: String? = null,
    val busy: Boolean = false,
)

class ConsoleViewModel(app: Application) : AndroidViewModel(app) {

    private val client = DeviceHttpClient()
    private val discovery = SsdpDiscovery()
    private val _state = MutableStateFlow(ConsoleUiState())
    val state: StateFlow<ConsoleUiState> = _state.asStateFlow()

    fun scan() {
        if (_state.value.scanning) return
        _state.update { it.copy(scanning = true, error = null) }
        viewModelScope.launch {
            val lock = acquireMulticastLock()
            try {
                val found = withContext(Dispatchers.IO) { discovery.discover() }
                _state.update { current ->
                    current.copy(
                        scanning = false,
                        devices = (current.devices + found).distinctBy { it.ip },
                    )
                }
                if (found.isEmpty()) {
                    log("SSDP 未发现设备：确认与音箱在同一网段，或手动输入 IP。")
                } else {
                    log("发现 ${found.size} 台设备：" + found.joinToString { it.ip })
                }
            } catch (t: Throwable) {
                _state.update { it.copy(scanning = false, error = t.message ?: "扫描失败") }
            } finally {
                lock?.release()
            }
        }
    }

    fun addManualDevice(ip: String) {
        val trimmed = ip.trim()
        if (trimmed.isEmpty()) return
        _state.update { current ->
            current.copy(devices = (current.devices + DiscoveredDevice(trimmed, null, null, null, null))
                .distinctBy { it.ip })
        }
        log("手动添加 $trimmed")
    }

    fun select(ip: String) {
        _state.update { it.copy(selectedIp = ip, status = null, slaves = emptyList()) }
        refreshStatus()
    }

    fun refreshStatus() {
        val ip = _state.value.selectedIp ?: return
        run("getStatusEx") {
            val status = client.status(ip)
            val slaves = runCatching { client.slaveList(ip) }.getOrDefault(emptyList())
            _state.update { it.copy(status = status, slaves = slaves) }
        }
    }

    fun sendCommand(command: String) {
        val ip = _state.value.selectedIp ?: return
        if (command.isBlank()) return
        run(command) {
            val raw = client.send(ip, command)
            log("返回：" + raw.take(400))
        }
    }

    fun renameDevice(name: String) {
        val ip = _state.value.selectedIp ?: return
        if (name.isBlank()) return
        run("setDeviceName") {
            client.send(ip, LinkPlayCommands.deviceName(name))
            log("已请求改名为 $name")
        }
    }

    fun switchMode(mode: String) {
        val ip = _state.value.selectedIp ?: return
        run("switchMode($mode)") { client.send(ip, LinkPlayCommands.switchMode(mode)) }
    }

    fun setSlaveVolume(slaveIp: String, volume: Int) {
        val ip = _state.value.selectedIp ?: return
        run("SlaveVolume($slaveIp=$volume)") {
            client.send(ip, LinkPlayCommands.slaveVolume(slaveIp, volume.coerceIn(0, 100)))
        }
    }

    fun restoreToDefault() {
        val ip = _state.value.selectedIp ?: return
        run("restoreToDefault") { client.send(ip, LinkPlayCommands.restoreToDefault()) }
    }

    fun clearLog() = _state.update { it.copy(log = emptyList()) }

    private fun run(label: String, block: suspend () -> Unit) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { block() }
                log("$label 完成")
            } catch (t: Throwable) {
                _state.update { it.copy(error = "${t.javaClass.simpleName}: ${t.message}") }
                log("$label 失败：${t.message}")
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    private fun log(message: String) = _state.update { it.copy(log = (it.log + message).takeLast(200)) }

    private fun acquireMulticastLock(): WifiManager.MulticastLock? = runCatching {
        val wifi = getApplication<Application>().applicationContext
            .getSystemService(Context.WIFI_SERVICE) as WifiManager
        wifi.createMulticastLock("linkplay-discovery").apply {
            setReferenceCounted(false)
            acquire()
        }
    }.getOrNull()
}
