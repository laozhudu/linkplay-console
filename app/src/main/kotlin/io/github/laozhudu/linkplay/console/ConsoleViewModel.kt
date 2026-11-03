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
import io.github.laozhudu.linkplay.protocol.playback.QueueRestore
import io.github.laozhudu.linkplay.protocol.player.DeviceQueueState
import io.github.laozhudu.linkplay.protocol.session.PlaybackKey
import io.github.laozhudu.linkplay.console.playback.QueueBus
import io.github.laozhudu.linkplay.console.playback.VolumeBus
import io.github.laozhudu.linkplay.protocol.player.PlayerStatus
import io.github.laozhudu.linkplay.protocol.player.VolumeStep
import io.github.laozhudu.linkplay.protocol.session.SavedDevice
import io.github.laozhudu.linkplay.protocol.upnp.UpnpRendererClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 多房间组里的一台音箱（主机或从机）。
 *
 * 为什么单独建模而不复用 [SavedDevice]：从机在组里才有意义，
 * 而且它的 IP 是**主机自报的**（主机 AP 网段 `10.10.10.x`），与保存记录里的旧 IP 不同。
 * 用它去下发 `SlaveVolume`/`SlaveChannel` 才对。
 */
data class GroupMember(
    val ip: String,
    val name: String,
    val uuid: String? = null,
    val volume: Int? = null,
    /** 声道：0=立体声 1=仅左 2=仅右。主机固定立体声，为 null。 */
    val channel: Int? = null,
    val isMaster: Boolean = false,
)

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
    /** 正在加入的音箱显示名，给进度条用。 */
    val multiroomTarget: String? = null,
    /**
     * 组网结果的一句话说明。
     *
     * 为什么必须单独一个字段：命令回 `OK` 不代表成功，
     * 失败时若只把按钮变回原样，用户会以为「点了一下什么都没发生」。
     */
    val multiroomNote: String? = null,
    /**
     * 已加入别的设备的组的音箱：`uuid → 主机显示名`。
     *
     * 为什么需要它：入组的从机会被主机拉到主机的 AP 网段（真机实测 `10.10.10.92`），
     * 从路由器这边**根本连不上**，直接探活必然判"离线"。但它其实正在同步播放，
     * 报"离线"是撒谎。这里把「它在我某台音箱的组里」这一事实记下来，界面据此显示"同步播放中"。
     */
    val groupedUnder: Map<String, String> = emptyMap(),

    /** 当前多房间组的主机 IP / 名称。没有组时为 null。 */
    val groupMasterIp: String? = null,
    val groupMasterName: String? = null,
    /** 组成员，第一个固定是主机。没有组时为空。 */
    val groupMembers: List<GroupMember> = emptyList(),
    /** 点了某个从机后要弹的详情框。 */
    val slaveDialog: SlaveDevice? = null,
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
    /**
     * 组音量下发通道（合并式）。
     *
     * 音量条拖动一次会触发几十个回调，逐个发 HTTP 会堵住、也会让设备手忙脚乱。
     * 合并通道只保留"最新值"，下游每 90ms 取一次 —— 手感是连续的，流量是可控的。
     */
    private val groupVolumeChannel = kotlinx.coroutines.channels.Channel<Pair<GroupMember, Int>>(
        kotlinx.coroutines.channels.Channel.CONFLATED,
    )
    /**
     * 「直接给某台设备设音量」的合并通道（键位 ip、值 0-100）。
     *
     * 为什么和 [groupVolumeChannel] 分开：那个通道的语义是"组内成员"，
     * 发送时要靠 groupMasterIp 中转；而手机音量键调的是**当前设备**，
     * 跟有没有组无关。两条路径混在一起就会出现"没组网时按键没反应"。
     */
    private val deviceVolumeChannel = kotlinx.coroutines.channels.Channel<Pair<String, Int>>(
        kotlinx.coroutines.channels.Channel.CONFLATED,
    )
    private val _state = MutableStateFlow(ConsoleUiState())
    val state: StateFlow<ConsoleUiState> = _state.asStateFlow()

    init {
        // 首页一进来就有内容：读已保存设备，并后台探活（不清空界面、不阻塞）
        reloadSaved()
        probeSaved()
        restoreLastDevice()
        // 队列恢复必须在**启动时**做，而不是等用户点开播放页：
        // 否则"重开 App 列表是空的"会被当成恢复失效
        restoreLastQueue()
        // 首页多房间卡片的低频心跳：不在播放页时也能跟上设备侧/遥控器的音量变化
        viewModelScope.launch {
            while (true) {
                delay(2500)
                val masterIp = _state.value.groupMasterIp ?: continue
                withContext(Dispatchers.IO) {
                    runCatching {
                        val st = PlayerStatus.parse(client.send(masterIp, "getPlayerStatus"))
                        VolumeBus.publish(masterIp, st.volume, st.mute)
                    }
                }
            }
        }
        viewModelScope.launch {
            for ((ip, v) in deviceVolumeChannel) {
                withContext(Dispatchers.IO) {
                    runCatching { client.send(ip, LinkPlayCommands.playerVolume(v)) }
                        .onFailure { log("调音量失败：${it.message}") }
                }
                delay(60)
            }
        }
        viewModelScope.launch {
            for ((member, v) in groupVolumeChannel) {
                withContext(Dispatchers.IO) {
                    val master = _state.value.groupMasterIp ?: return@withContext
                    runCatching {
                        if (member.isMaster) client.send(master, LinkPlayCommands.playerVolume(v))
                        else client.send(master, LinkPlayCommands.slaveVolume(member.ip, v))
                    }.onFailure { log("调音量失败：${it.message}") }
                }
                delay(90)
            }
        }
    }

    /**
     * 重开 App 时自动回到上次那台设备。
     *
     * 用户要求「原厂退出后重开能读到原来的状态」。光恢复队列不够 ——
     * 没有"当前设备"，播放页是空的、列表也无处可挂。所以这里按 uuid（其次 IP）找回，
     * 找到了就静默选中，不跳 Tab（用户可能只想看设备列表）。
     */
    private fun restoreLastDevice() {
        val key = runCatching { store.loadLastDevice() }.getOrNull() ?: return
        val target = _state.value.saved.firstOrNull {
            it.uuid == key || "ip:${it.ip}" == key
        } ?: return
        _state.update {
            it.copy(
                selectedIp = target.ip,
                selectedLocation = target.location,
                selectedUuid = target.uuid,
            )
        }
        log("恢复上次设备：${target.title}（${target.ip}）")
    }

    /**
     * 重开 App 时恢复播放列表。
     *
     * 判定逻辑全在 [QueueRestore]（纯函数、有单测）。这里只负责三件事：
     * 1. 读**设备真值**（`plicount`/`plicurr`）—— 列表本来就在设备上，这是权威；
     * 2. 读本地快照（提供曲目标题，设备不回填元数据）；
     * 3. 两者交给 [QueueRestore.plan] 合成，结果写进 [QueueBus]。
     *
     * 为什么先等一小会儿：`restoreLastDevice()` 里才拿到 selectedIp/uuid，
     * 而两者都在同一个 init 里，这里按"当前已选设备"读，拿不到就退回上次的记录。
     */
    private fun restoreLastQueue() {
        viewModelScope.launch {
            delay(150)
            val uuid = _state.value.selectedUuid
            val ip = _state.value.selectedIp ?: return@launch

            val device = withContext(Dispatchers.IO) {
                runCatching {
                    DeviceQueueState.from(PlayerStatus.parse(client.send(ip, "getPlayerStatus")))
                }.getOrNull()
            }
            val snapshot = withContext(Dispatchers.IO) {
                PlaybackKey.candidates(uuid, ip).firstNotNullOfOrNull { key ->
                    runCatching { store.loadPlayback(key) }.getOrNull()
                }
            }
            val plan = QueueRestore.plan(snapshot, device) ?: return@launch
            QueueBus.restore(
                items = plan.items,
                index = plan.index,
                title = plan.items.getOrNull(plan.index)?.title,
                deviceOwned = plan.fromDevice,
                offset = snapshot?.deviceOffset ?: 0,
                note = plan.note,
            )
            log("恢复播放列表：${plan.items.size} 首 · ${plan.note}")
        }
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
            // 顺带把「组」读出来：从机不可直连，只能由主机转述（真机实测从机在 10.10.10.x）
            val grouped = mutableMapOf<String, String>()
            var masterIp: String? = null
            var masterName: String? = null
            var members: List<GroupMember> = emptyList()
            for (d in targets) {
                if (result[d.uuid] != true) continue
                val slaves = withContext(Dispatchers.IO) {
                    runCatching { MultiroomClient(client).slaves(d.ip) }.getOrDefault(emptyList())
                }
                if (slaves.isEmpty()) continue
                slaves.forEach { sl ->
                    sl.uuid?.removePrefix("uuid:")?.lowercase()?.let { grouped[it] = d.title }
                }
                if (masterIp == null) {
                    masterIp = d.ip
                    masterName = d.title
                    // 主机自己的音量在 getPlayerStatus 里（getStatusEx 没有这个字段）
                    val masterVolume = withContext(Dispatchers.IO) {
                        runCatching {
                            PlayerStatus.parse(client.send(d.ip, "getPlayerStatus")).let { st ->
                                VolumeBus.publish(d.ip, st.volume, st.mute)
                                st.volume
                            }
                        }.getOrNull() ?: VolumeBus.volumeOf(d.ip)?.volume
                    }
                    members = listOf(
                        GroupMember(ip = d.ip, name = d.title, uuid = d.uuid, volume = masterVolume, isMaster = true),
                    ) + slaves.map { sl ->
                        GroupMember(
                            ip = sl.ip,
                            name = sl.name ?: sl.ip,
                            uuid = sl.uuid?.removePrefix("uuid:"),
                            volume = sl.volume,
                            channel = sl.channel,
                            isMaster = false,
                        )
                    }
                }
            }
            _state.update {
                it.copy(
                    online = it.online + result,
                    groupedUnder = grouped,
                    groupMasterIp = masterIp,
                    groupMasterName = masterName,
                    groupMembers = members,
                )
            }
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
        runCatching { store.saveLastDevice(device.uuid, device.ip) }
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

    // ---- 多房间组：首页直接操作 ----

    /** 重新探活并刷新组结构（首页下拉/进入时调用）。 */
    fun refreshGroup() = probeSaved()

    /**
     * 调组内某台音箱的音量。
     *
     * 为什么不走 [run]：那里有 `busy` 闸门，音量条连续拖动时第二次就被丢掉，
     * 手感立刻变「不跟手」。这里乐观更新本地值 + 直接下发，失败只记日志。
     */
    fun setGroupVolume(member: GroupMember, volume: Int) {
        val master = _state.value.groupMasterIp ?: return
        val v = volume.coerceIn(0, 100)
        _state.update { st ->
            st.copy(groupMembers = st.groupMembers.map { if (it.ip == member.ip) it.copy(volume = v) else it })
        }
        // 主机音量写进全站总线，播放页那一侧同一帧就跟着动
        if (member.isMaster) {
            VolumeBus.publishVolume(master, v)
            deviceVolumeChannel.trySend(master to v)
            return
        }
        // 主机的音量是它自己的；从机必须由主机代转（从机在主机热点网段，直连不可达）
        groupVolumeChannel.trySend(member to v)
    }

    /**
     * 调组内某台从机的声道（0 立体声 / 1 仅左 / 2 仅右）。
     *
     * 只有从机有这条命令；主机声道由播放源决定，所以主机不做假开关。
     */
    fun setGroupChannel(member: GroupMember, channel: Int) {
        val master = _state.value.groupMasterIp ?: return
        if (member.isMaster) return
        val ch = channel.coerceIn(0, 2)
        _state.update { st ->
            st.copy(groupMembers = st.groupMembers.map { if (it.ip == member.ip) it.copy(channel = ch) else it })
        }
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                runCatching { client.send(master, "multiroom:SlaveChannel:${member.ip}:$ch") }
                    .onFailure { log("调声道失败：${it.message}") }
            }
        }
    }

    /**
     * 手机实体音量键 → 音箱音量（原厂 App 就是这个行为）。
     *
     * @return true 表示"这一下我处理了，别让系统再调手机自己的音量"
     *
     * 为什么必须吞掉事件：不吞的话按一次键会同时改手机媒体音量和音箱音量，
     * 用户会看到手机上的人声/铃声变了，而音箱只动了一点点，非常困惑。
     *
     * 步长按手机惯例 15 档换算成 0-100：每按一下 5，长按连续按也不至于跳变。
     */
    fun adjustVolumeByKey(delta: Int): Int? {
        val ip = _state.value.selectedIp ?: return null
        if (_state.value.online[ip] == false) return null
        val current = VolumeBus.volumeOf(ip)?.volume
            ?: _state.value.groupMembers.firstOrNull { it.ip == ip }?.volume
            ?: return null
        if (!VolumeStep.shouldConsume(current, delta)) return null
        val next = VolumeStep.next(current, delta) ?: return null
        VolumeBus.publishVolume(ip, next)
        // 音量键这条路**不走去抖通道**：用户按一下就要立刻听到变化，
        // 合并通道是为拖动设计的，按键本来就是离散事件，多一次 60ms 等待只会显得钝。
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                runCatching { client.send(ip, LinkPlayCommands.playerVolume(next)) }
                    .onFailure { log("音量键下发失败：${it.message}") }
            }
        }
        log("音量键 → $next")
        return next
    }

    /**
     * 现在能不能接管音量键。
     *
     * 活动必须在 ACTION_DOWN 与 ACTION_UP 上给出**一致的**答案，
     * 否则会出现"按下改了音箱、抬起又改了手机"的双重响应。
     */
    fun canHandleVolumeKey(): Boolean {
        val ip = _state.value.selectedIp ?: return false
        if (_state.value.online[ip] == false) return false
        return VolumeBus.volumeOf(ip) != null || _state.value.groupMembers.any { it.ip == ip }
    }

    /** 把某台从机移出组。 */
    fun removeFromGroup(member: GroupMember) {
        val master = _state.value.groupMasterIp ?: return
        if (member.isMaster) return
        run("移出组 ${member.name}") {
            log("SlaveKickout → " + client.send(master, "multiroom:SlaveKickout:${member.ip}"))
            delay(1500)
        }
        probeSaved()
    }

    /**
     * 把一台已保存的音箱加进组。
     *
     * 主机取当前组的主机；还没有组时取第一台在线的已保存设备。
     * 协议细节见 [MultiroomClient.joinWithPolling]（照原厂：`getNetwork` 的整段返回当密码）。
     */
    fun addToGroup(slave: SavedDevice) {
        val master = _state.value.groupMasterIp
            ?: _state.value.saved.firstOrNull { _state.value.online[it.uuid] == true }?.ip
        if (master == null) {
            _state.update { it.copy(error = "先添加并连上一台音箱") }
            return
        }
        if (_state.value.multiroomBusy) return
        _state.update {
            it.copy(multiroomBusy = true, multiroomTick = 0, multiroomTarget = slave.title, multiroomNote = null)
        }
        viewModelScope.launch {
            val (ok, reason) = withContext(Dispatchers.IO) {
                val ap = runCatching { MultiroomClient(client).readApInfo(master) }.getOrNull()
                    ?: return@withContext false to "读不到主机热点信息"
                val ok = runCatching {
                    MultiroomClient(client).joinWithPolling(
                        masterIp = master,
                        masterSsid = ap.ssid,
                        slaveIp = slave.ip,
                        onTick = { sec -> viewModelScope.launch { _state.update { it.copy(multiroomTick = sec) } } },
                        shouldStop = { !_state.value.multiroomBusy },
                    )
                }.getOrElse { false }
                ok to null
            }
            _state.update {
                it.copy(
                    multiroomBusy = false,
                    multiroomNote = if (ok) "已把「${slave.title}」加入" else (reason ?: "命令已发出，但 40 秒内它没出现在组里"),
                )
            }
            probeSaved()
        }
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
        _state.update {
            it.copy(
                multiroomBusy = true,
                multiroomTick = 0,
                multiroomTarget = device.title,
                multiroomNote = null,
                error = null,
            )
        }
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
            _state.update {
                it.copy(
                    multiroomBusy = false,
                    slaves = after,
                    multiroomNote = if (ok || after.isNotEmpty()) {
                        "已把「${device.title}」加入，现在共 ${after.size} 台同步播放"
                    } else {
                        "命令已经发给音箱，但 40 秒内它没有出现在组里 —— 这一步没成功。"
                    },
                )
            }
            if (ok) {
                log("成组成功：主机现在有 ${after.size} 台从机")
            } else {
                log("成组未确认：40 秒内主机仍没看到从机（命令已被设备接受，但设备之间没建立连接）")
                log("可以等 1~2 分钟再点「重新读取从机」；若从机暂时离线，它会自己连回原 Wi-Fi")
            }
        }
    }

    fun cancelMultiroom() = _state.update {
        it.copy(multiroomBusy = false, multiroomNote = "已停止等待（组网不一定成功，可稍后「重新读取」看看）")
    }

    /** 点从机芯片 → 打开它的单独音量/退出框。 */
    fun setSlaveDialog(slave: SlaveDevice) = _state.update { it.copy(slaveDialog = slave) }

    fun clearSlaveDialog() = _state.update { it.copy(slaveDialog = null) }

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
