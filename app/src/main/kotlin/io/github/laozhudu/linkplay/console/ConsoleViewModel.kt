package io.github.laozhudu.linkplay.console

import android.app.Application
import android.content.Context
import android.net.wifi.WifiManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.laozhudu.linkplay.console.session.SessionStore
import io.github.laozhudu.linkplay.discovery.DiscoveredDevice
import io.github.laozhudu.linkplay.discovery.SsdpDiscovery
import io.github.laozhudu.linkplay.protocol.DeviceHttpClient
import io.github.laozhudu.linkplay.protocol.LinkPlayCommands
import io.github.laozhudu.linkplay.protocol.LinkPlayStatus
import io.github.laozhudu.linkplay.protocol.SlaveDevice
import io.github.laozhudu.linkplay.protocol.identity.LinkPlayIdentity
import io.github.laozhudu.linkplay.protocol.multiroom.MultiroomClient
import io.github.laozhudu.linkplay.protocol.session.SavedDevice
import io.github.laozhudu.linkplay.protocol.upnp.UpnpRendererClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ConsoleUiState(
    val scanning: Boolean = false,
    /** 本次扫描新找到、且**尚未保存**的设备（只含 LinkPlay 系，见 LinkPlayIdentity）。 */
    val devices: List<DiscoveredDevice> = emptyList(),
    /** 已保存的音箱，最近用过的在前 —— 首页主列表。 */
    val saved: List<SavedDevice> = emptyList(),
    val selectedIp: String? = null,
    /** 该设备的 UPnP 描述地址（SSDP 的 LOCATION）；手动输入 IP 时为 null。 */
    val selectedLocation: String? = null,
    /** 设备 UUID（来自 `getStatusEx`）。会话记忆按它做键，所以换 IP 也不会丢。 */
    val selectedUuid: String? = null,
    val status: LinkPlayStatus? = null,
    val slaves: List<SlaveDevice> = emptyList(),
    /** 打开 App 时对已保存设备的探活结果（uuid → 是否在线）。 */
    val online: Map<String, Boolean> = emptyMap(),
    val log: List<String> = emptyList(),
    val error: String? = null,
    val busy: Boolean = false,
    /** 扫描后有没有被过滤掉的其它 UPnP 设备（用于给用户一句解释，而不是「怎么什么都搜不到」）。 */
    val filteredOutCount: Int = 0,
    /** 正在成组（轮询等待中）。 */
    val multiroomBusy: Boolean = false,
    /** 成组轮询已等待的秒数。 */
    val multiroomTick: Long = 0,
    /**
     * 成组时可手填的信道与密码。
     *
     * 为什么要露出来：原厂 App 用 `getNetwork` 取主机热点密码，
     * 但这批固件的 `getNetwork` 只返回 `{"securemode":"0",}`（实测），
     * 于是 auth 只能按 OPEN 走；万一某些机器实际是 WPA2，这里能手动补上。
     */
    val joinChannel: Int = 0,
    val joinPassword: String = "",
)

class ConsoleViewModel(app: Application) : AndroidViewModel(app) {

    private val client = DeviceHttpClient()
    private val discovery = SsdpDiscovery()
    private val store = SessionStore.of(app)
    private val _state = MutableStateFlow(ConsoleUiState())
    val state: StateFlow<ConsoleUiState> = _state.asStateFlow()

    init {
        // 首页一进来就有内容：读已保存设备，并后台探活（不清空界面、不阻塞）
        reloadSaved()
        probeSaved()
    }

    // ---- 已保存设备 ----

    private fun reloadSaved() {
        val list = runCatching { store.savedDevices() }.getOrDefault(emptyList())
        _state.update { it.copy(saved = list) }
    }

    /**
     * 探活已保存的设备。
     *
     * 为什么用 httpapi 的 `getStatusEx` 而不是 SSDP：设备可能换了 IP（实测 .164 → .153），
     * SSDP 只能证明「某台设备在」，不能把 uuid 和 IP 对上；`getStatusEx` 直接回 uuid，
     * 顺便能把新 IP 写回记录。
     */
    private fun probeSaved() {
        val targets = _state.value.saved
        if (targets.isEmpty()) return
        viewModelScope.launch {
            val result = mutableMapOf<String, Boolean>()
            for (d in targets) {
                val status = withContext(Dispatchers.IO) {
                    runCatching { client.status(d.ip) }.getOrNull()
                }
                if (status != null && status.uuid == d.uuid) {
                    result[d.uuid] = true
                    touch(d.uuid, d.ip, status)
                } else {
                    result[d.uuid] = false
                    // IP 变了：扫一次，看能不能按 uuid 找回来
                    val found = withContext(Dispatchers.IO) {
                        runCatching { discovery.discoverLinkPlayDevices() }.getOrDefault(emptyList())
                    }.firstOrNull { it.uuid?.contains(d.uuid, ignoreCase = true) == true }
                    if (found != null) {
                        result[d.uuid] = true
                        touch(d.uuid, found.ip, null, found.location)
                        log("${d.title} 换了地址：${d.ip} → ${found.ip}")
                    }
                }
            }
            _state.update { it.copy(online = it.online + result) }
        }
    }

    private fun touch(uuid: String, ip: String, status: LinkPlayStatus?, location: String? = null) {
        val existing = _state.value.saved.firstOrNull { it.uuid == uuid } ?: return
        val updated = existing.copy(
            ip = ip,
            name = status?.ssid?.takeIf { it.isNotBlank() } ?: existing.name,
            location = location ?: existing.location,
            modelName = status?.hardware ?: existing.modelName,
            lastSeenMs = System.currentTimeMillis(),
        )
        val next = runCatching { store.upsertDevice(updated) }.getOrDefault(_state.value.saved)
        _state.update { it.copy(saved = next) }
    }

    fun forget(uuid: String) {
        val next = runCatching { store.removeDevice(uuid) }.getOrDefault(_state.value.saved)
        _state.update { it.copy(saved = next, online = it.online - uuid) }
        log("已移除设备记录")
    }

    /** 点已保存的设备：带上记下的 IP/LOCATION 直接进，不再走扫描。 */
    fun open(device: SavedDevice) {
        _state.update {
            it.copy(
                selectedIp = device.ip,
                selectedLocation = device.location,
                selectedUuid = device.uuid,
                status = null,
                slaves = emptyList(),
            )
        }
        refreshStatus()
    }

    // ---- 发现新设备 ----

    /**
     * 扫描局域网里的**音箱**（不再是「所有 UPnP 设备」）。
     *
     * 过滤逻辑在 [SsdpDiscovery.discoverLinkPlayDevices]：要把描述拉下来才能定论，
     * 因为真机 SSDP 头里没有品牌字样（`SERVER: Linux/3.x UPnP/1.0`）。
     */
    fun scan() {
        if (_state.value.scanning) return
        _state.update { it.copy(scanning = true, error = null, filteredOutCount = 0) }
        viewModelScope.launch {
            val lock = acquireMulticastLock()
            try {
                val all = withContext(Dispatchers.IO) { runCatching { discovery.discover() }.getOrDefault(emptyList()) }
                val linkplay = withContext(Dispatchers.IO) {
                    runCatching { discovery.discoverLinkPlayDevices() }.getOrDefault(emptyList())
                }
                val savedUuids = _state.value.saved.map { it.uuid.lowercase() }
                val fresh = linkplay.filter { d ->
                    val u = (d.uuid ?: "").lowercase()
                    // 已保存的不再重复出现在「新设备」里
                    d.uuid == null || savedUuids.none { u.contains(it) }
                }
                _state.update {
                    it.copy(
                        scanning = false,
                        devices = fresh,
                        filteredOutCount = (all.size - linkplay.size).coerceAtLeast(0),
                    )
                }
                when {
                    linkplay.isEmpty() -> log("没找到音箱。确认音箱已开机且与手机同一 Wi-Fi；路由器若隔离组播，可直接填 IP。")
                    fresh.isEmpty() -> log("找到 ${linkplay.size} 台，都是已保存过的")
                    else -> log("发现 ${fresh.size} 台新音箱")
                }
                if (_state.value.filteredOutCount > 0) {
                    log("已忽略 ${_state.value.filteredOutCount} 个非音箱的 UPnP 设备（电视/路由/NAS 等）")
                }
            } catch (t: Throwable) {
                _state.update { it.copy(scanning = false, error = t.message ?: "扫描失败") }
            } finally {
                lock?.release()
            }
        }
    }

    /**
     * 手动添加：填 IP。
     *
     * 手动输入是「组播被路由器挡住」时的唯一出路，所以不过滤得太狠 ——
     * 但仍要求它至少是 LinkPlay 系（描述里能看出私有服务/品牌），否则加了也控制不了。
     */
    fun addManual(ipOrUrl: String) {
        val raw = ipOrUrl.trim().removePrefix("http://").removeSuffix("/")
        if (raw.isEmpty()) return
        val ip = raw.substringBefore(':')
        val port = raw.substringAfter(':', "80").toIntOrNull() ?: 80
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            val outcome = withContext(Dispatchers.IO) { runCatching { identify(ip, port) } }
            _state.update { it.copy(busy = false) }
            outcome.onSuccess { found ->
                save(found)
                log("已添加 ${found.name}（${found.ip}）")
            }.onFailure { t ->
                _state.update { it.copy(error = t.message) }
                log("添加失败：${t.message}")
            }
        }
    }

    /** 识别一个 IP：先问 httpapi（最可靠），拿不到再看描述。 */
    private fun identify(ip: String, port: Int): SavedDevice {
        val status = runCatching { client.status(ip) }.getOrNull()
        if (status != null && !status.uuid.isNullOrBlank()) {
            // getStatusEx 能答上来，基本就是 LinkPlay 系（这套 httpapi 是它家专有的）
            return SavedDevice(
                uuid = status.uuid!!,
                name = status.ssid?.takeIf { it.isNotBlank() } ?: ip,
                ip = ip,
                location = status.uuid?.let { null }, // 描述地址探测见下
                modelName = status.hardware,
                lastSeenMs = System.currentTimeMillis(),
            )
        }
        // 没有 httpapi 时退回 UPnP 描述判断
        val desc = UpnpRendererClient.describe("http://$ip:$port/description.xml")
        if (!LinkPlayIdentity.isLinkPlayFamily(desc)) {
            throw IllegalStateException("$ip 不是本类音箱（${LinkPlayIdentity.reasonFor(desc)} 不成立）")
        }
        return SavedDevice(
            uuid = desc.udn?.removePrefix("uuid:") ?: ip,
            name = desc.friendlyName ?: ip,
            ip = ip,
            location = "http://$ip:$port/description.xml",
            modelName = desc.modelName,
            lastSeenMs = System.currentTimeMillis(),
        )
    }

    private fun save(device: SavedDevice) {
        val next = runCatching { store.upsertDevice(device) }.getOrDefault(_state.value.saved)
        _state.update { it.copy(saved = next, online = it.online + (device.uuid to true)) }
    }

    /** 扫描结果里点「添加」：保存并直接进入。 */
    fun add(device: DiscoveredDevice) {
        val saved = SavedDevice(
            uuid = device.uuid?.substringAfterLast(':')?.removePrefix("uuid:") ?: device.ip,
            name = device.friendlyName ?: device.friendlySsid ?: device.ip,
            ip = device.ip,
            location = device.location,
            lastSeenMs = System.currentTimeMillis(),
        )
        save(saved)
        _state.update { it.copy(devices = it.devices.filterNot { d -> d.ip == device.ip }) }
        log("已保存 ${saved.name}")
        open(saved)
    }

    // ---- 设备操作 ----

    fun select(ip: String) {
        val location = _state.value.devices.firstOrNull { it.ip == ip }?.location
        _state.update {
            it.copy(selectedIp = ip, selectedLocation = location, status = null, slaves = emptyList())
        }
        refreshStatus()
    }

    fun refreshStatus() {
        val ip = _state.value.selectedIp ?: return
        run("getStatusEx") {
            val status = client.status(ip)
            val slaves = runCatching { client.slaveList(ip) }.getOrDefault(emptyList())
            // UUID 在这里落定：SSDP 只给 IP/LOCATION，UUID 必须问设备要
            _state.update { it.copy(status = status, slaves = slaves, selectedUuid = status.uuid) }
            status.uuid?.let { touch(it, ip, status) }
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

    // ---- 多房间 ----

    /**
     * 让另一台已保存的音箱接入本机（本机当主机）。
     *
     * 走的是原厂 APK 的真实逻辑（docs/14 §4.7）：
     * 向**从机**发 `multiroom:ConnnectAP:<主机IP>:ConnectMasterAp:...`，
     * 然后每 2 秒读一次主机的 `getSlaveList`，40 秒内看到从机就算成功。
     *
     * 为什么必须轮询确认：`multiroom:*` 命令只要名字认识就回 `OK`，
     * 真机上 `SlaveMask` 各种参数都回 OK 但一台从机都没出现。
     *
     * **风险提示（UI 上也要写）**：这一步会让从机去连主机的热点，
     * 从机可能短暂离开原网段。失败时它会自己连回原来的 Wi-Fi，但要等一会儿。
     */
    /** 成组用的可选项：主机热点信道（0=自动）与密码（我们的设备 securemode=0，一般留空）。 */
    fun setJoinChannel(ch: Int) = _state.update { it.copy(joinChannel = ch.coerceIn(0, 14)) }

    fun setJoinPassword(pwd: String) = _state.update { it.copy(joinPassword = pwd) }

    fun joinSlave(device: SavedDevice) {
        val masterIp = _state.value.selectedIp ?: return
        val masterSsid = _state.value.status?.ssid
        if (masterSsid.isNullOrBlank()) {
            _state.update { it.copy(error = "还读不到本机热点名，先刷新设备信息") }
            return
        }
        if (_state.value.multiroomBusy) return
        _state.update { it.copy(multiroomBusy = true, multiroomTick = 0, error = null) }
        viewModelScope.launch {
            log("开始成组：${device.title}（${device.ip}）→ 主机 $masterIp（$masterSsid）")
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    MultiroomClient(client).joinWithPolling(
                        masterIp = masterIp,
                        masterSsid = masterSsid,
                        slaveIp = device.ip,
                        channel = _state.value.joinChannel,
                        password = _state.value.joinPassword,
                        onTick = { sec ->
                            log("等待从机出现… ${sec}s / 40s")
                            viewModelScope.launch { _state.update { it.copy(multiroomTick = sec) } }
                        },
                        shouldStop = { !_state.value.multiroomBusy },
                    )
                }.getOrElse { t ->
                    log("成组异常：${t.message}")
                    false
                }
            }
            val after = withContext(Dispatchers.IO) { runCatching { client.slaveList(masterIp) }.getOrDefault(emptyList()) }
            _state.update { it.copy(multiroomBusy = false, slaves = after) }
            if (ok) {
                log("成组成功：主机现在有 ${after.size} 台从机")
            } else {
                log("成组未确认：40 秒内主机仍没看到从机（命令已被设备接受，但设备之间没建立连接）")
                log("可以等 1~2 分钟再点「重新读取从机」；若从机暂时离线，它会自己连回原 Wi-Fi")
            }
        }
    }

    fun cancelMultiroom() = _state.update { it.copy(multiroomBusy = false) }

    fun kickSlave(slaveIp: String) {
        val master = _state.value.selectedIp ?: return
        run("踢出 $slaveIp") {
            log("SlaveKickout → " + client.send(master, "multiroom:SlaveKickout:$slaveIp"))
            _state.update { it.copy(slaves = client.slaveList(master)) }
        }
    }

    fun renameSlave(slaveIp: String, name: String) {
        val master = _state.value.selectedIp ?: return
        if (name.isBlank()) return
        run("从机改名") {
            client.send(master, LinkPlayCommands.slaveDeviceName(slaveIp, name))
            _state.update { it.copy(slaves = client.slaveList(master)) }
        }
    }

    fun refreshSlaves() {
        val master = _state.value.selectedIp ?: return
        run("读取从机") {
            _state.update { it.copy(slaves = client.slaveList(master)) }
        }
    }

    fun restoreToDefault() {
        val ip = _state.value.selectedIp ?: return
        run("restoreToDefault") { client.send(ip, LinkPlayCommands.restoreToDefault()) }
    }

    /** 从设备页返回首页：清掉选中态，但保留已保存列表。 */
    fun leaveDevice() {
        _state.update {
            it.copy(
                selectedIp = null,
                selectedLocation = null,
                selectedUuid = null,
                status = null,
                slaves = emptyList(),
                error = null,
            )
        }
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
