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
import io.github.laozhudu.linkplay.protocol.dlna.DescriptionEndpoints
import io.github.laozhudu.linkplay.protocol.dlna.DeviceProfile
import io.github.laozhudu.linkplay.protocol.dlna.detect
import io.github.laozhudu.linkplay.protocol.identity.LinkPlayIdentity
import io.github.laozhudu.linkplay.protocol.multiroom.DragGrouping
import io.github.laozhudu.linkplay.protocol.multiroom.GroupPlan
import io.github.laozhudu.linkplay.protocol.multiroom.MultiroomClient
import io.github.laozhudu.linkplay.protocol.playback.QueueRestore
import io.github.laozhudu.linkplay.protocol.player.DeviceQueueState
import io.github.laozhudu.linkplay.protocol.session.PlaybackKey
import io.github.laozhudu.linkplay.console.playback.DevicePlaylistGateway
import io.github.laozhudu.linkplay.console.playback.DeviceVolumeControl
import io.github.laozhudu.linkplay.console.playback.QueueBus
import io.github.laozhudu.linkplay.console.playback.VolumeBus
import io.github.laozhudu.linkplay.console.library.LibraryStore
import io.github.laozhudu.linkplay.console.library.LocalLibrary
import io.github.laozhudu.linkplay.console.library.PlaylistStore
import io.github.laozhudu.linkplay.protocol.mediasource.SubsonicConfig
import io.github.laozhudu.linkplay.protocol.FirmwareUpdate
import io.github.laozhudu.linkplay.protocol.player.PlayState
import io.github.laozhudu.linkplay.protocol.player.PlayerStatus
import io.github.laozhudu.linkplay.protocol.player.SleepTimer
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
    /** 是否被屏蔽（`slave_list[].mask`）：屏蔽后不出声但仍在组里。 */
    val masked: Boolean = false,
)

/**
 * 首页设备卡片的实时信息（原厂设备列表就是这么显示的）。
 *
 * 为什么要单独一份：原厂卡片上每台音箱都显示「现在放的是哪首 + 音量 + 声道」，
 * 而不只是设备名。这些必须**逐台轮询**（不同设备内容不同），
 * 所以不能复用只针对当前选中设备的 `status`。
 */
data class DeviceCard(
    val title: String? = null,
    val volume: Int = 0,
    val mute: Boolean = false,
    /** 设备回报的 mode（判断在哪一路上播）。 */
    val mode: Int? = null,
    /** 声道：0 立体声 / 1 仅左 / 2 仅右（`getPlayerStatus` 的 `ch`）。 */
    val channel: Int? = null,
    /**
     * 电量百分比。两类来源：
     * - 独立设备/主机 → `getStatusEx.battery_percent`
     * - 组内从机 → `multiroom:getSlaveList` 的 `battery`（从机在主机 AP 网段，我们连不上）
     */
    val battery: Int? = null,
    /** 设备侧队列长度/当前序号（`plicount`/`plicurr`），0 表示没有列表。 */
    val plicount: Int = 0,
    val plicurr: Int = 0,
    /** 是否正在播放（status=play）。 */
    val playing: Boolean = false,
    /**
     * 这台设备的能力档（LinkPlay / 通用 DLNA / 未知）。
     *
     * 为什么放在卡片上：卡片上要显示类型标签，并据此**隐藏**组网/声道/屏蔽这些入口——
     * 给一台电视盒子摆「声道 LR」按钮，按了必然没反应（它没有 multiroom 服务），
     * 这是项目铁律明确禁止的。判档要拉描述，所以由探活时算一次、之后随轮询带下去。
     */
    val profile: DeviceProfile = DeviceProfile.UNKNOWN,
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

    /** 首页卡片：`uuid → 实时信息`。逐台轮询出来的，见 [DeviceCard]。 */
    val cards: Map<String, DeviceCard> = emptyMap(),

    /** 定时关闭（原厂设备菜单第四项）。到点由 App 自己下发停止。 */
    val sleepTimer: SleepTimer = SleepTimer(),

    /** 定时器显示用的"现在"，每秒刷新一次，让倒计时能走。 */
    val nowMs: Long = 0L,

    /** 分组面板是否打开（Sonos 式勾选 + 应用）。 */
    val groupingOpen: Boolean = false,
    /** 面板里已勾选的设备 uuid（含主机时也会被 GroupPlan 剔除）。 */
    val groupingSelected: Set<String> = emptySet(),
    /** 应用分组的进度文案（null = 空闲）。 */
    val groupingProgress: String? = null,

    /** 固件升级状态（原厂「设备升级」页）。 */
    val firmware: FirmwareUpdate = FirmwareUpdate(),
    /** 预设键数量（`getStatusEx.preset_key`，真机 6）；读不到按 6 显示。 */
    val presetCount: Int = 6,

    /** 「我的音乐」页要显示的数量。 */
    val localMusicCount: Int = 0,
    val nasServerCount: Int = 0,
    val favoriteCount: Int = 0,
    val recentCount: Int = 0,
    /** 歌单数（原厂「全部歌单」，存在独立的 linkplay_playlist.db）。 */
    val playlistCount: Int = 0,
    /** 电台数（内置台 + 用户自己粘的流地址）。 */
    val radioCount: Int = 0,
    /**
     * 在线曲库是否已经配过。
     *
     * 为什么只表达"配没配"而不显示曲目数：在线曲库的曲目数是服务器侧的事，
     * 进页面就问一次会把「我的音乐」拖成网络页；配置本身是本地会话里的一个 JSON。
     */
    val onlineConfigured: Boolean = false,
) {

    /**
     * 聚合播放态：组内（或单台）现在是否在响。
     *
     * 为什么放在 state 里而不是界面各自算：主页聚合条、「全部播放/暂停」按钮、
     * 迷你播放条三处都要用。各自算一次必然出现"按钮显示暂停、条上显示播放"。
     *
     * 判定优先用**主机**（组的锚点）：从机的播放态由主机代放，自己不一定报得准。
     */
    val anyPlaying: Boolean
        get() {
            val masterUuid = saved.firstOrNull { it.ip == groupMasterIp }?.uuid
            if (masterUuid != null) return cards[masterUuid]?.playing == true
            return cards.values.any { it.playing }
        }

    /** 聚合播放态对应的曲名（主页聚合条用）。 */
    val playingTitle: String?
        get() {
            val masterUuid = saved.firstOrNull { it.ip == groupMasterIp }?.uuid
            return (masterUuid?.let { cards[it] } ?: cards.values.maxByOrNull { it.plicount })
                ?.title
                ?.takeIf { it.isNotBlank() }
        }

    /** 组内共几台（含主机）；0 表示没有组。 */
    val groupSize: Int get() = groupMembers.size
}

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
        // 首页卡片心跳：每 3 秒逐台读 getPlayerStatus（原厂设备列表就显示每台的曲名/音量）。
        // 为什么不用 1 秒：卡片信息变化慢，3 秒够了；设备只有 2 台，但别把老固件打爆。
        viewModelScope.launch {
            while (true) {
                delay(3_000)
                val saved = _state.value.saved
                if (saved.isEmpty()) continue
                for (d in saved) {
                    if (_state.value.online[d.uuid] == false) continue
                    // 只轮询"路由器侧够得着"的设备：入组的从机在主机 AP 网段里，
                    // 从这边直接连必然超时（真机实测），跳过去，靠主机的 slave_list 表达它的状态。
                    if (_state.value.groupedUnder.containsKey(d.uuid.lowercase())) continue
                    val ip = d.ip
                    val known = _state.value.cards[d.uuid]?.profile
                    val card = if (known == DeviceProfile.GENERIC_DMR) {
                        // 通用渲染器不认 httpapi（没有 getPlayerStatus），只能按标准 UPnP 读
                        withContext(Dispatchers.IO) { runCatching { pollDmrCard(d) }.getOrNull() }
                    } else {
                        withContext(Dispatchers.IO) {
                            runCatching {
                                val st = PlayerStatus.parse(client.send(ip, "getPlayerStatus"))
                                DeviceCard(
                                    title = st.title?.takeIf { it.isNotBlank() && it != "Unknown" },
                                    volume = st.volume,
                                    mute = st.mute,
                                    mode = st.mode,
                                    channel = st.channel,
                                    plicount = st.playListCount ?: 0,
                                    plicurr = st.playListCurrent ?: 0,
                                    playing = st.playState == PlayState.PLAY,
                                    // 能答 getPlayerStatus 就是 LinkPlay 的私有 httpapi
                                    profile = known ?: DeviceProfile.LINKPLAY,
                                    // 注意：这里不写缓存 —— 只有真正探测过的才写，避免把"默认"当结论
                                )
                            }.getOrNull()
                        }
                    } ?: continue
                    VolumeBus.publish(ip, card.volume, card.mute)
                    _state.update { it.copy(cards = it.cards + (d.uuid to card)) }
                }
            }
        }
        // 定时关闭的时钟：每秒推进一次，到点下发停止。
        viewModelScope.launch {
            while (true) {
                delay(1_000)
                val now = System.currentTimeMillis()
                val timer = _state.value.sleepTimer
                if (now - _state.value.nowMs < 900 && !timer.active) continue
                _state.update { it.copy(nowMs = now) }
                if (timer.isDue(now)) {
                    val ip = _state.value.selectedIp
                        ?: _state.value.saved.firstOrNull()?.ip
                    _state.update { it.copy(sleepTimer = SleepTimer()) }
                    if (ip != null) {
                        withContext(Dispatchers.IO) {
                            runCatching { client.send(ip, "setPlayerCmd:stop") }
                        }
                        log("定时关闭到点：已停止 $ip")
                        QueueBus.stopped()
                    }
                }
            }
        }
        viewModelScope.launch {
            for ((ip, v) in deviceVolumeChannel) {
                withContext(Dispatchers.IO) {
                    // 原厂做法：UPnP RenderingControl 优先，httpapi 兜底（见 DeviceVolumeControl）
                    runCatching { DeviceVolumeControl.setVolume(ip, v) }
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
                        if (member.isMaster) DeviceVolumeControl.setVolume(master, v)
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

            // ① 原厂做法：BrowseQueue 把设备上的整份列表（含曲名）读回来 —— 这才是"同步设备列表"
            val deviceList = DevicePlaylistGateway.read(ip, _state.value.selectedLocation)
            if (deviceList != null && !deviceList.isEmpty) {
                val items = DevicePlaylistGateway.toQueueItems(deviceList)
                val idx = deviceList.currentIndex.coerceIn(0, items.lastIndex)
                QueueBus.restore(
                    items = items,
                    index = idx,
                    title = items.getOrNull(idx)?.title,
                    deviceOwned = true,
                    note = "设备列表 ${items.size} 首 · 第 ${idx + 1} 首",
                )
                log("从设备读回播放列表：${items.size} 首（队列名 ${deviceList.name}）")
                return@launch
            }

            // ② 设备没有队列 → 用本地快照兜底（并说明设备当前是空的）
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
                    // 能答 getStatusEx 就是 LinkPlay 的私有 httpapi —— 卡片据此显示类型标签
                    putCardProfile(d.uuid, DeviceProfile.LINKPLAY)
                    // 电量：独立设备/主机只能从 getStatusEx 拿
                    status.batteryPercent?.let { pct ->
                        _state.update { st ->
                            st.copy(cards = st.cards + (d.uuid to (st.cards[d.uuid] ?: DeviceCard()).copy(battery = pct)))
                        }
                    }
                } else {
                    result[d.uuid] = false
                    // ① 先看它是不是「通用 DLNA 渲染器」：这类设备**永远**答不上 httpapi，
                    //    但描述里的 AVTransport 在，一样能投流。不认这一档的话，
                    //    电视盒子会被判成"离线"，卡片上全是点不动的入口。
                    val upnpProfile = withContext(Dispatchers.IO) { profileOfSaved(d) }
                    if (upnpProfile == DeviceProfile.GENERIC_DMR) {
                        result[d.uuid] = true
                        putCardProfile(d.uuid, DeviceProfile.GENERIC_DMR)
                        continue
                    }
                    // ② IP 变了：扫一次，看能不能按 uuid 找回来
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
                    // 从机电量/声道只有主机知道（从机在 10.10.10.x，直连必超时）
                    slaves.forEach { sl ->
                        val su = sl.uuid?.removePrefix("uuid:")?.lowercase() ?: return@forEach
                        val savedUuid = _state.value.saved.firstOrNull { it.uuid.equals(su, ignoreCase = true) }?.uuid
                            ?: return@forEach
                        _state.update { st ->
                            st.copy(
                                cards = st.cards + (savedUuid to (st.cards[savedUuid] ?: DeviceCard()).copy(
                                    battery = sl.battery,
                                    channel = sl.channel,
                                )),
                            )
                        }
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
                            masked = sl.masked,
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

    /**
     * 通用渲染器的 UPnP 客户端缓存（按 LOCATION）。
     *
     * 为什么缓存：首页卡片每 3 秒轮询一次，每次都重新拉 `description.xml` 就是白花一次往返。
     * 设备换地址/服务消失时读取会抛异常，上层按「本轮读不到」处理，下次自然重建。
     */
    private val dmrClients = java.util.concurrent.ConcurrentHashMap<String, UpnpRendererClient>()

    private fun dmrClient(location: String?): UpnpRendererClient? {
        val loc = location?.takeIf { it.isNotBlank() } ?: return null
        return dmrClients[loc] ?: runCatching {
            UpnpRendererClient(UpnpRendererClient.describe(loc))
        }.onSuccess { dmrClients[loc] = it }.getOrNull()
    }

    /**
     * 按标准 UPnP 读一台通用渲染器的「在放什么 / 音量 / 是否在播」。
     *
     * 为什么只有这一条路：这类设备没有 `getPlayerStatus`，卡片上的信息只能靠
     * `GetTransportInfo` + `GetPositionInfo` + `GetVolume` 三个标准动作凑出来。
     * 三个都是尽力而为：某个服务缺失（例如没有 RenderingControl）只丢那一项，不整张卡作废。
     */
    private fun pollDmrCard(d: SavedDevice): DeviceCard {
        val c = dmrClient(d.location) ?: throw IllegalStateException("读不到设备描述")
        val transport = runCatching { c.transportInfo() }.getOrNull()
        val position = runCatching { c.positionInfo() }.getOrNull()
        val volume = runCatching { c.volume() }.getOrNull()
        return DeviceCard(
            title = position?.trackMetadata?.title?.takeIf { it.isNotBlank() },
            volume = volume?.volume ?: 0,
            mute = volume?.mute ?: false,
            playing = transport?.state?.isPlaying == true,
            profile = DeviceProfile.GENERIC_DMR,
        )
    }

    /**
     * 只按 UPnP 描述判一台已保存设备属于哪一档。
     *
     * 探活时用来认「通用 DLNA 渲染器」：它答不上 httpapi 是**正常**的，不是离线。
     * 依次试保存的 LOCATION、再试 LinkPlay 的固定路径（手动填 IP 添加的没有 LOCATION）。
     */
    private fun profileOfSaved(d: SavedDevice): DeviceProfile {
        val urls = buildList {
            d.location?.takeIf { it.isNotBlank() }?.let { add(it) }
            addAll(DescriptionEndpoints.candidates(d.ip, 49152))
        }.distinct()
        for (url in urls) {
            val desc = runCatching { UpnpRendererClient.describe(url) }.getOrNull() ?: continue
            val (profile, _) = detect(desc, httpapiReachable = false)
            if (profile != DeviceProfile.UNKNOWN) return profile
        }
        return DeviceProfile.UNKNOWN
    }

    /**
     * 把能力档写进卡片（卡片可能还没建，先建一张空的），**同时写进能力缓存**。
     *
     * 为什么两处都要写：卡片是给界面看的（隐藏不支持的入口），
     * 缓存是给起播路径看的（决定要不要走设备侧 PlayQueue）。
     * 只写一处就会出现"界面隐藏了组网、但起播还在硬推 PlayQueue"这种半截状态。
     */
    private fun putCardProfile(uuid: String, profile: DeviceProfile) {
        _state.value.saved.firstOrNull { it.uuid == uuid }?.ip?.let { ip ->
            io.github.laozhudu.linkplay.console.playback.DeviceProfileCache.put(ip, profile)
        }
        _state.update { st ->
            st.copy(cards = st.cards + (uuid to (st.cards[uuid] ?: DeviceCard()).copy(profile = profile)))
        }
    }

    /**
     * 更新一条已保存设备。
     *
     * ## 一个真 bug（本轮修掉）
     *
     * 原来这里 `name = status?.ssid ?: existing.name` —— 每次探活都把**用户设的名字**
     * 覆盖成设备热点名（`DOSS DS-1831_400C`）。所以改名能下发成功，但一刷新就"变回去"。
     *
     * 原厂的设备名来自 **UPnP friendlyName**（`setDeviceName` 改的也是它），
     * 与热点 SSID 是两回事。这里改成：拿描述里的 friendlyName 同步名字，
     * 只在它非空且与本地不同时写回 —— 这样"在别处改的名字"也能同步过来。
     */
    private fun touch(uuid: String, ip: String, status: LinkPlayStatus?, location: String? = null) {
        val existing = _state.value.saved.firstOrNull { it.uuid == uuid } ?: return
        // 名字：UPnP friendlyName 优先（原厂口径）；读不到才保留原值，绝不用 SSID 顶替
        val friendly = runCatching {
            val loc = location ?: existing.location ?: "http://$ip:49152/description.xml"
            UpnpRendererClient.describe(loc).friendlyName?.takeIf { it.isNotBlank() }
        }.getOrNull()
        val updated = existing.copy(
            ip = ip,
            name = friendly ?: existing.name,
            location = location ?: existing.location,
            modelName = status?.hardware ?: existing.modelName,
            lastSeenMs = System.currentTimeMillis(),
        )
        val next = runCatching { store.upsertDevice(updated) }.getOrDefault(_state.value.saved)
        _state.update { it.copy(saved = next) }
    }

    /**
     * 移除一条已保存设备（原厂长按菜单的「删除」/ 我们的设置页「移除这台设备」）。
     *
     * 只删本机记录，不动音箱本身。同时清掉卡片状态、在线状态、组内归属 ——
     * 否则列表里走了、组里还挂着一台幽灵设备。
     */
    fun forget(uuid: String) {
        val removed = _state.value.saved.firstOrNull { it.uuid == uuid }
        val next = runCatching { store.removeDevice(uuid) }.getOrDefault(_state.value.saved)
        _state.update {
            it.copy(
                saved = next,
                online = it.online - uuid,
                cards = it.cards - uuid,
                groupedUnder = it.groupedUnder - uuid.lowercase(),
                // 删的正好是当前选中/组主机，就把选中态一并清掉，避免播放页对着空气操作
                selectedUuid = if (it.selectedUuid == uuid) null else it.selectedUuid,
                selectedIp = if (it.selectedUuid == uuid) null else it.selectedIp,
                selectedLocation = if (it.selectedUuid == uuid) null else it.selectedLocation,
            )
        }
        log("已移除设备记录${removed?.let { "：${it.title}" } ?: ""}")
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
                // 通用 DLNA 渲染器（电视盒子等）：一样能投流，只是没有私有服务。
                // 与 LinkPlay 结果按 IP 去重（同一台设备两边都会命中，优先按 LinkPlay 展示）。
                val renderers = withContext(Dispatchers.IO) {
                    runCatching { discovery.discoverMediaRenderers() }.getOrDefault(emptyList())
                }.filter { r -> linkplay.none { it.ip == r.ip } }
                val savedUuids = _state.value.saved.map { it.uuid.lowercase() }
                fun isNew(d: DiscoveredDevice): Boolean {
                    val u = (d.uuid ?: "").lowercase()
                    // 已保存的不再重复出现在「新设备」里
                    return d.uuid == null || savedUuids.none { u.contains(it) }
                }
                val fresh = linkplay.filter(::isNew) + renderers.filter(::isNew)
                _state.update {
                    it.copy(
                        scanning = false,
                        devices = fresh,
                        filteredOutCount = (all.size - linkplay.size - renderers.size).coerceAtLeast(0),
                    )
                }
                when {
                    fresh.isEmpty() && linkplay.isEmpty() && renderers.isEmpty() ->
                        log("没找到音箱。确认设备已开机且与手机同一 Wi-Fi；路由器若隔离组播，可直接填 IP。")
                    fresh.isEmpty() -> log("找到 ${linkplay.size + renderers.size} 台，都是已保存过的")
                    else -> log("发现 ${fresh.size} 台新设备")
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

    /**
     * 识别一个 IP：先问 httpapi（最可靠），拿不到再看描述。
     *
     * 描述这一档分两种身份：
     * - LinkPlay 系 → 原来的记录字段（uuid 用描述 UDN，location 存 `:49152/description.xml`）；
     * - **通用 DLNA 渲染器**（电视盒子等）→ 也收下。它们没有 httpapi，但 AVTransport 在，
     *   投流与音量都走标准 UPnP，能控就是能控，没有必要因为它们"不是本类音箱"就拒绝添加。
     */
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
        // 没有 httpapi 时退回 UPnP 描述判断。
        // 两个候选地址都试：LinkPlay 在 `/description.xml`，通用渲染器常把描述放在根路径
        // （实测 AirReceiver：`http://192.168.99.175:3500/` 就是描述，`/description.xml` 404）。
        var desc: io.github.laozhudu.linkplay.protocol.upnp.DeviceDescription? = null
        var usedUrl: String? = null
        for (url in DescriptionEndpoints.candidates(ip, port)) {
            val d = runCatching { UpnpRendererClient.describe(url) }.getOrNull() ?: continue
            if (d.avTransportControlUrl != null) {
                desc = d
                usedUrl = url
                break
            }
        }
        val found = desc ?: throw IllegalStateException(
            "$ip:$port 上没有可用的 UPnP 描述（既没有 httpapi，也没有带 AVTransport 的描述文件）",
        )
        val (profile, _) = detect(found, httpapiReachable = false)
        if (profile == DeviceProfile.UNKNOWN) {
            throw IllegalStateException("$ip:$port 不是可播放设备（${LinkPlayIdentity.reasonFor(found)} 不成立）")
        }
        return SavedDevice(
            uuid = found.udn?.removePrefix("uuid:") ?: ip,
            name = found.friendlyName ?: ip,
            ip = ip,
            location = usedUrl,
            modelName = found.modelName,
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
     * 首页顶栏的刷新：重新探活已保存的设备（并顺带读一次卡片状态）。
     *
     * 与 [scan] 的区别：scan 会重新做 SSDP 发现、可能弹出"已忽略 N 个非音箱设备"，
     * 适合"添加新设备"。刷新只想知道"现在还在不在"，不该有副作用。
     */
    fun refreshDevices() {
        if (_state.value.saved.isEmpty()) {
            scan()
            return
        }
        probeSaved()
    }

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
    /**
     * 首页卡片点声道：0 立体声 → 1 仅左 → 2 仅右 循环，**立刻下发**并在 3 秒轮询里回读确认。
     *
     * 只有组内从机能改声道：主机的声道由硬件（左右两个单元）决定，`SlaveChannel` 也只认从机。
     * 从机的 IP 必须用**主机自报的那个**（`10.10.10.x`），保存记录里的旧 IP 在组里已经不通。
     *
     * 真机验证（2026-11-03，`.164` 主机 + `10.10.10.92` 从机）：
     * `multiroom:SlaveChannel:10.10.10.92:1` → 回读 `channel=1`；`:2` → `2`；`:0` → `0`。
     */
    /**
     * 给某台设备改名（原厂「重命名」）。
     *
     * 两条通道，按设备身份选：
     * - 独立设备 / 主机 → `setDeviceName:<urlenc>` 直发自己；
     * - 组内从机 → 必须由**主机**转达 `multiroom:SlaveDeviceName:<从机IP>:<urlenc>`，
     *   因为从机已经掉到主机 AP 网段（`10.10.10.x`），我们连不上。
     *
     * 改完立刻回读一次（`getStatusEx` 的 UPnP friendlyName / 主机的 `slave_list`），
     * 用设备回报覆盖本地记录 —— 命令回 OK 不等于名字真变了（本项目反复吃过这个亏）。
     */
    fun renameCard(device: SavedDevice, rawName: String) {
        val name = rawName.trim()
        if (name.isEmpty()) {
            _state.update { it.copy(error = "名字不能为空") }
            return
        }
        run("改名 ${device.title} → $name") {
            val st = _state.value
            val member = st.groupMembers.firstOrNull { it.uuid?.equals(device.uuid, true) == true }
            val masterIp = st.groupMasterIp
            if (member != null && !member.isMaster && masterIp != null) {
                client.send(masterIp, LinkPlayCommands.slaveDeviceName(member.ip, name))
            } else {
                client.send(device.ip, LinkPlayCommands.deviceName(name))
            }
            delay(800)
            // 回读：从机看主机的 slave_list，独立设备看 UPnP friendlyName
            probeSaved()
            refreshDevicesInGroup()
            log("改名已下发：$name")
        }
    }

    /**
     * 解除这台设备的组网（原厂拖出去 / 长按菜单「解除」）。
     *
     * 只对**从机**有意义：`multiroom:SlaveKickout:<从机IP>` 发给主机，
     * 从机会自己回到原来的 Wi-Fi。真机实测：3 秒后主机 `slaves=0`，从机回到 `192.168.99.153`。
     *
     * 主机被踢（解散整组）依赖 `SlaveKickout` 对每台从机分别下发 —— 这里对组内所有从机各发一次。
     */
    fun ungroupCard(device: SavedDevice) {
        val st = _state.value
        val masterIp = st.groupMasterIp
        val member = st.groupMembers.firstOrNull { it.uuid?.equals(device.uuid, true) == true }
        if (masterIp == null || member == null) {
            _state.update { it.copy(multiroomNote = "「${device.title}」不在组里，无需解除") }
            return
        }
        run("解除组网 ${device.title}") {
            val targets = if (member.isMaster) {
                st.groupMembers.filter { !it.isMaster }.map { it.ip }
            } else {
                listOf(member.ip)          // 用主机自报的 10.10.10.x，不是保存记录里的旧 IP
            }
            targets.forEach { ip ->
                log("SlaveKickout($ip) → " + runCatching { client.send(masterIp, "multiroom:SlaveKickout:$ip") }.getOrDefault("ERR"))
            }
            // 等从机回到原 Wi-Fi 再刷，否则界面会先显示"离线"
            delay(3_500)
            probeSaved()
            refreshDevicesInGroup()
            _state.update {
                it.copy(multiroomNote = if (targets.isEmpty()) "组里没有从机" else "已解除 ${targets.size} 台从机")
            }
            log("解除组网完成")
        }
    }

    fun cycleCardChannel(device: SavedDevice) {
        val st = _state.value
        val member = st.groupMembers.firstOrNull { it.uuid?.equals(device.uuid, ignoreCase = true) == true }
            ?: st.groupMembers.firstOrNull { it.ip == device.ip }
        if (member == null) {
            _state.update { it.copy(multiroomNote = "「${device.title}」不在组里，声道只能对组内从机设置") }
            return
        }
        if (member.isMaster) {
            _state.update { it.copy(multiroomNote = "主机是左右两个单元，不单独设声道") }
            return
        }
        val next = ((member.channel ?: 0) + 1) % 3
        setGroupChannel(member, next)
        log("声道 → $next（${member.name}）")
    }

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

    /** 开定时关闭（分钟）。0 = 取消。原厂设备菜单第四项。 */
    fun setSleepTimer(minutes: Int) {
        val timer = if (minutes <= 0) SleepTimer().cancel()
        else SleepTimer().start(minutes, System.currentTimeMillis())
        _state.update { it.copy(sleepTimer = timer, nowMs = System.currentTimeMillis()) }
        log(if (timer.active) "定时关闭：${minutes} 分钟后停止" else "定时关闭：已取消")
    }

    fun cancelSleepTimer() = setSleepTimer(0)

    /**
     * 首页卡片上拖音量（原厂卡片就是这个交互）。
     *
     * 走 [deviceVolumeChannel] 合并通道：拖动会连续产生几十个值，
     * 每个都发一次 HTTP 会把老固件压垮，合并只保留最新值。
     */
    fun setCardVolume(device: SavedDevice, volume: Int) {
        val ip = device.ip
        VolumeBus.publishVolume(ip, volume)
        deviceVolumeChannel.trySend(ip to volume.coerceIn(0, 100))
    }

    // ---- 勾选式分组（Sonos Grouping 菜单）----

    /** 打开分组面板：默认勾上"现在在组里的"。 */
    fun openGrouping() {
        val st = _state.value
        val current = st.groupMembers.filter { !it.isMaster }.mapNotNull { it.uuid?.lowercase() }.toSet()
        _state.update { it.copy(groupingOpen = true, groupingSelected = current) }
    }

    fun closeGrouping() = _state.update { it.copy(groupingOpen = false, groupingProgress = null) }

    fun toggleGroupingDevice(uuid: String) = _state.update { st ->
        val key = uuid.lowercase()
        st.copy(
            groupingSelected = if (key in st.groupingSelected) st.groupingSelected - key
            else st.groupingSelected + key,
        )
    }

    fun selectEverywhere() {
        val st = _state.value
        val master = st.saved.firstOrNull { it.ip == st.groupMasterIp }?.uuid
        _state.update {
            it.copy(groupingSelected = GroupPlan.everywhere(st.saved.map { d -> d.uuid }, master))
        }
    }

    /**
     * 应用分组（勾选 → 逐台执行 → 回执）。
     *
     * 逐台**串行**而不是并发：成组命令会让从机离开当前 Wi-Fi，
     * 同时给两台下发时第二台可能正好在重连，命令直接失败。
     *
     * 与 [joinSlave] 的区别：这里不跑 40 秒轮询（那会把界面卡住），
     * 全部下发完统一等 6 秒再刷一次设备与组 —— 真机实测 8 秒左右建成。
     */
    fun applyGrouping() {
        val st = _state.value
        val masterIp = st.groupMasterIp ?: st.selectedIp
        if (masterIp == null) {
            _state.update { it.copy(groupingProgress = "先选一台主机") }
            return
        }
        val masterUuid = st.saved.firstOrNull { it.ip == masterIp }?.uuid
        val current = st.groupMembers.filter { !it.isMaster }.mapNotNull { it.uuid }.toSet()
        val plan = GroupPlan.diff(current, st.groupingSelected, masterUuid)
        if (plan.empty) {
            _state.update { it.copy(groupingOpen = false, groupingProgress = null, multiroomNote = "分组没有变化") }
            return
        }
        _state.update { it.copy(groupingProgress = plan.describe()) }
        viewModelScope.launch {
            val status = withContext(Dispatchers.IO) { runCatching { client.status(masterIp) }.getOrNull() }
            val ssid = status?.ssid
            if (ssid.isNullOrBlank()) {
                _state.update { it.copy(groupingProgress = "读不到主机热点名，先刷新设备") }
                return@launch
            }
            val mroom = MultiroomClient(client)
            val ap = withContext(Dispatchers.IO) { runCatching { mroom.readApInfo(masterIp) }.getOrNull() }
            val pwd = withContext(Dispatchers.IO) {
                runCatching { mroom.readJoinPassword(masterIp) }.getOrDefault("")
            }

            // ① 先移出：一台设备只能在一个组里，留在旧组会"取消了还在同步"
            for (uuid in plan.leave) {
                val member = st.groupMembers.firstOrNull { it.uuid?.equals(uuid, true) == true } ?: continue
                _state.update { it.copy(groupingProgress = "移出 ${member.name}…") }
                withContext(Dispatchers.IO) {
                    runCatching { client.send(masterIp, LinkPlayCommands.slaveKickout(member.ip)) }
                }
                log("分组：移出 ${member.name}（${member.ip}）")
            }
            // ② 再加入
            for (uuid in plan.join) {
                val device = st.saved.firstOrNull { it.uuid.equals(uuid, true) } ?: continue
                _state.update { it.copy(groupingProgress = "加入 ${device.title}…") }
                withContext(Dispatchers.IO) {
                    runCatching {
                        mroom.joinGroup(
                            masterIp = masterIp,
                            masterSsid = ssid,
                            slaveIp = device.ip,
                            channel = ap?.channel ?: 0,
                            password = pwd,
                        )
                    }
                }
                log("分组：加入 ${device.title}（${device.ip}）")
            }
            // ③ 等设备之间建链（真机约 8 秒），再统一刷新
            _state.update { it.copy(groupingProgress = "等待设备建链…") }
            delay(6_000)
            probeSaved()
            refreshDevicesInGroup()
            val after = (_state.value.groupMembers.size - 1).coerceAtLeast(0)
            _state.update {
                it.copy(
                    groupingOpen = false,
                    groupingProgress = null,
                    multiroomNote = if (after > 0) "已同步 $after 台（共 ${after + 1} 台）"
                    else "命令已发出，但没检测到从机",
                )
            }
            log("分组完成：${plan.describe()} → 现在 $after 台从机")
        }
    }

    /**
     * 播放/暂停切换（媒体播放器惯例：按钮显示**将发生的动作**）。
     *
     * 主页聚合条、底部大按钮都调它 —— 共用一处判定，才不会"按钮说暂停、其实还在放"。
     */
    fun togglePlayAll() {
        if (_state.value.anyPlaying) pauseAll() else playAll()
    }

    /**
     * 单台设备的播放/暂停（卡片上的小按钮）。
     *
     * 组内从机不单独控制：它跟随主机，单独下发只会得到一条无效命令。
     * 界面已对从机隐藏该按钮，这里再兜一层。
     */
    fun toggleDevicePlay(device: SavedDevice) {
        val st = _state.value
        val member = st.groupMembers.firstOrNull { it.uuid?.equals(device.uuid, true) == true }
        if (member != null && !member.isMaster) {
            _state.update { it.copy(multiroomNote = "「${device.title}」跟随主机播放，请在主机上控制") }
            return
        }
        val playing = st.cards[device.uuid]?.playing == true
        run(if (playing) "暂停 ${device.title}" else "播放 ${device.title}") {
            val ok: Boolean = withContext(Dispatchers.IO) {
                runCatching {
                    if (playing) UpnpRendererClient.ofDevice(device.ip).pause()
                    else UpnpRendererClient.ofDevice(device.ip).play()
                    true
                }.getOrDefault(false)
            }
            if (!ok) {
                withContext(Dispatchers.IO) {
                    runCatching {
                        client.send(device.ip, if (playing) "setPlayerCmd:pause" else "setPlayerCmd:play")
                    }
                }
            }
            delay(800)
            refreshDevicesInGroup()
        }
    }

    /**
     * 全部播放：让「组内所有音箱一起响」。
     *
     * 原厂的语义就是这样 —— 组已经建立后，只要主机开始播放，从机自动跟着响，
     * 所以这里不需要对每台设备下发命令，**只对主机下发**即可。
     *
     * 真机验证（2026-11-03）：主机 `.164`（`plicount=28, pause`）收到
     * `AVTransport.Play` 后 `status=play`，返回 200。
     */
    fun playAll() {
        run("全部播放") {
            val st = _state.value
            val masterIp = st.groupMasterIp ?: st.selectedIp ?: st.saved.firstOrNull()?.ip
            if (masterIp == null) {
                _state.update { it.copy(error = "还没有音箱") }
                return@run
            }
            val uuid = st.saved.firstOrNull { it.ip == masterIp }?.uuid
            val card = uuid?.let { st.cards[it] }
            if ((card?.plicount ?: 0) <= 0) {
                // 设备没队列：从本地快照恢复（和设备侧一样只存一份真相）
                val restored = PlaybackKey.candidates(uuid, masterIp).firstNotNullOfOrNull { key ->
                    runCatching { store.loadPlayback(key) }.getOrNull()
                }
                if (restored == null || restored.items.isEmpty()) {
                    _state.update { it.copy(error = "音箱上还没有列表，先去「我的音乐」点一首") }
                    return@run
                }
                QueueBus.restore(
                    items = restored.items,
                    index = restored.index.coerceIn(0, restored.items.lastIndex),
                    title = restored.items.getOrNull(restored.index)?.title,
                    deviceOwned = false,
                    note = "从上次播放继续",
                )
            }
            val ok: Boolean = withContext(Dispatchers.IO) {
                runCatching {
                    UpnpRendererClient.ofDevice(masterIp).play()
                    true
                }.getOrDefault(false)
            }
            if (!ok) {
                withContext(Dispatchers.IO) { runCatching { client.send(masterIp, "setPlayerCmd:play") } }
            }
            log("全部播放 → ${st.groupMasterName ?: masterIp}（组内 ${st.groupMembers.size.coerceAtLeast(1)} 台）")
            // 让界面的"在放什么"立刻刷新，不用等 3 秒心跳
            delay(1200)
            refreshDevicesInGroup()
        }
    }

    /** 全部暂停（组内一起停）。 */
    fun pauseAll() {
        run("全部暂停") {
            val st = _state.value
            val targets = buildList {
                st.groupMasterIp?.let { add(it) }
                if (st.groupMasterIp == null) addAll(st.saved.map { it.ip })
            }.distinct()
            targets.forEach { ip ->
                val ok: Boolean = withContext(Dispatchers.IO) {
                    runCatching {
                        UpnpRendererClient.ofDevice(ip).pause()
                        true
                    }.getOrDefault(false)
                }
                if (!ok) withContext(Dispatchers.IO) { runCatching { client.send(ip, "setPlayerCmd:pause") } }
            }
            log("全部暂停 → ${targets.size} 台")
        }
    }

    /** 立刻重读一遍组内设备（比等 3 秒心跳快）。 */
    private suspend fun refreshDevicesInGroup() {
        val st = _state.value
        val ips = buildList {
            st.groupMasterIp?.let { add(it) }
            addAll(st.saved.map { it.ip })
        }.distinct()
        for (ip in ips) {
            val uuid = st.saved.firstOrNull { it.ip == ip }?.uuid ?: continue
            val card = withContext(Dispatchers.IO) {
                runCatching {
                    val p = PlayerStatus.parse(client.send(ip, "getPlayerStatus"))
                    DeviceCard(
                        title = p.title?.takeIf { it.isNotBlank() && it != "Unknown" },
                        volume = p.volume,
                        mute = p.mute,
                        mode = p.mode,
                        channel = p.channel,
                        plicount = p.playListCount ?: 0,
                        plicurr = p.playListCurrent ?: 0,
                        playing = p.playState == PlayState.PLAY,
                    )
                }.getOrNull()
            } ?: continue
            VolumeBus.publish(ip, card.volume, card.mute)
            _state.update { it.copy(cards = it.cards + (uuid to card)) }
        }
    }

    /**
     * 按第 [uiIndex] 个预设键（0 基）。
     *
     * 原厂 `DeviceContentActivity` 的预设列表就是发 `MCUKeyShortClick:<i+1>`，
     * 真机 `.164` 回 `OK`。它触发**设备端**存好的动作（电台/歌单），
     * 所以我们不需要知道内容，只把索引发过去。
     */
    fun pressPreset(uiIndex: Int) {
        val ip = _state.value.selectedIp ?: return
        run("预设键 ${uiIndex + 1}") {
            val reply = client.send(ip, LinkPlayCommands.presetKey(uiIndex))
            log("MCUKeyShortClick:${uiIndex + 1} → $reply")
            delay(800)
            refreshDevicesInGroup()
        }
    }

    /** 查固件更新：照原厂顺序四条命令读一遍，结果如实展示。 */
    fun checkFirmware() {
        val ip = _state.value.selectedIp ?: return
        run("检查固件更新") {
            val fw = withContext(Dispatchers.IO) {
                runCatching {
                    client.send(ip, LinkPlayCommands.firmwareUpdateStart())
                    delay(1_500)
                    val remote = client.send(ip, LinkPlayCommands.firmwareUpdateStatus())
                    val dl = runCatching { client.send(ip, LinkPlayCommands.firmwareDownloadStatus()) }.getOrNull()
                    val burn = runCatching { client.send(ip, LinkPlayCommands.firmwareBurnPercent()) }.getOrNull()
                    FirmwareUpdate.of(remote, dl, burn)
                }.getOrElse { t ->
                    log("检查固件失败：${t.message}")
                    FirmwareUpdate()
                }
            }
            _state.update { it.copy(firmware = fw) }
            log("固件：${fw.phase}（status=${fw.statusRaw}）")
        }
    }

    /** 开始升级（界面只在 [FirmwareUpdate.canStart] 时放开这个按钮）。 */
    fun startFirmwareUpdate() {
        val ip = _state.value.selectedIp ?: return
        run("开始升级固件") {
            log("开始下载固件…")
            client.send(ip, "setMvRemoteUpdateStart")
            repeat(40) {
                delay(3_000)
                val dl = runCatching { client.send(ip, LinkPlayCommands.firmwareDownloadStatus()) }.getOrNull()
                val burn = runCatching { client.send(ip, LinkPlayCommands.firmwareBurnPercent()) }.getOrNull()
                val fw = FirmwareUpdate.of(_state.value.firmware.statusRaw?.toString(), dl, burn)
                _state.update { it.copy(firmware = fw) }
                log("固件：${fw.phase}")
                if (fw.burnPercent >= 100) return@run
            }
        }
    }

    /**
     * 屏蔽/恢复某台从机（`mask=1` 不出声但仍留在组里）。
     *
     * 与「解除」的区别：解除 = 踢出组（它回自己 Wi-Fi）；屏蔽 = 还在组里、只是不出声。
     * 真机：`SlaveMask` → `mask` 0→1；`SlaveUnMask` → 回 0。
     */
    fun toggleMask(device: SavedDevice) {
        val st = _state.value
        val masterIp = st.groupMasterIp
        val member = st.groupMembers.firstOrNull { it.uuid?.equals(device.uuid, true) == true }
        if (masterIp == null || member == null || member.isMaster) {
            _state.update { it.copy(multiroomNote = "只有组内从机能屏蔽") }
            return
        }
        run(if (member.masked) "恢复 ${device.title}" else "屏蔽 ${device.title}") {
            val cmd = if (member.masked) LinkPlayCommands.slaveUnMask(member.ip)
            else LinkPlayCommands.slaveMask(member.ip)
            log("$cmd → " + runCatching { client.send(masterIp, cmd) }.getOrDefault("ERR"))
            delay(1_500)
            probeSaved()
        }
    }

    /** 刷新「我的音乐」六个入口的数量。 */
    fun refreshMusicCounts() {
        viewModelScope.launch {
            val app = getApplication<Application>()
            val local = withContext(Dispatchers.IO) {
                runCatching { LocalLibrary.query(app).size }.getOrDefault(0)
            }
            val fav = withContext(Dispatchers.IO) {
                runCatching { LibraryStore.get(app).favorites.all().size }.getOrDefault(0)
            }
            val recent = withContext(Dispatchers.IO) {
                runCatching { LibraryStore.get(app).recents.recent(200).size }.getOrDefault(0)
            }
            val radios = withContext(Dispatchers.IO) {
                runCatching { LibraryStore.get(app).radios.all().size }.getOrDefault(0)
            }
            // 家庭音乐共享 = 记住的 DLNA **服务器**数，而不是"当前设备有没有"。
            // 原来按 selectedUuid 判 1/0，换个设备看就变成 0，跟"家里有台 NAS"这个事实不符。
            // 同一台 NAS 可能被多台音箱各记一份会话，所以按 host:port 去重。
            val nas = withContext(Dispatchers.IO) {
                runCatching {
                    _state.value.saved
                        .mapNotNull { store.loadNasSession(it.uuid)?.server }
                        .map { "${it.host}:${it.port}" }
                        .distinct()
                        .size
                }.getOrDefault(0)
            }
            // 在线曲库：只要本地存过一份能解析出地址的配置就算"已连接"（不 ping，避免进页面就卡网络）
            val online = withContext(Dispatchers.IO) {
                runCatching {
                    val cfg = SubsonicConfig.decode(store.loadSubsonic())
                    cfg != null && cfg.baseUrl.isNotBlank() && cfg.enabled
                }.getOrDefault(false)
            }
            val lists = withContext(Dispatchers.IO) {
                runCatching { PlaylistStore.get(app).count() }.getOrDefault(0)
            }
            _state.update {
                it.copy(
                    localMusicCount = local,
                    nasServerCount = nas,
                    favoriteCount = fav,
                    recentCount = recent,
                    radioCount = radios,
                    onlineConfigured = online,
                    playlistCount = lists,
                )
            }
        }
    }

    /**
     * 只刷歌单数。
     *
     * 为什么要单独一个：歌单是在「音乐库」页里就地增删的，从那里返回「我的音乐」时
     * 只需要更新这一个数字，没必要把本地曲库（要扫 MediaStore）和电台一起重读一遍。
     * 与 [refreshMusicCounts] 并存，不改动已有调用方。
     */
    fun refreshPlaylistCount() {
        viewModelScope.launch {
            val lists = withContext(Dispatchers.IO) {
                runCatching { PlaylistStore.get(getApplication()).count() }.getOrDefault(0)
            }
            _state.update { it.copy(playlistCount = lists) }
        }
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

    /**
     * 拖拽组网（原厂交互）：把「从机」拖到「主机」上松手。
     *
     * 与 [joinSlave] 的区别只有一个，但很关键：**主机由松手位置决定**，
     * 而不是当前选中的设备。主机要维持热点（`ra0=10.10.10.254`），从机接入后掉到
     * `10.10.10.x` 从路由器侧消失 —— 方向错了整组都不通。
     *
     * 判据在 [DragGrouping.resolve]（纯函数，单测里逐条锁死），这里只负责：
     * 1. 按判据给出"能做/不能做"的一句话；
     * 2. 现读主机状态拿到热点名（`ssid`）与信道，再下发成组命令；
     * 3. 轮询主机 `slave_list` 等它真的出现（命令回 OK ≠ 生效，这条吃过亏）。
     */
    fun groupByDrag(dragged: SavedDevice, target: SavedDevice) {
        if (_state.value.multiroomBusy) return
        val nodes = _state.value.saved.associate { d ->
            d.uuid to DragGrouping.Node(
                uuid = d.uuid,
                name = d.title,
                online = _state.value.online[d.uuid] != false,
                groupedUnder = _state.value.groupedUnder[d.uuid.lowercase()],
            )
        }
        when (val decision = DragGrouping.resolve(nodes[dragged.uuid], nodes[target.uuid])) {
            is DragGrouping.Decision.Reject -> {
                _state.update { it.copy(multiroomNote = decision.reason, error = null) }
                log("拖拽组网：${decision.reason}")
                return
            }
            is DragGrouping.Decision.Group -> Unit
        }

        _state.update {
            it.copy(
                multiroomBusy = true,
                multiroomTick = 0,
                multiroomTarget = dragged.title,
                multiroomNote = null,
                error = null,
            )
        }
        viewModelScope.launch {
            log("拖拽组网：${dragged.title} → 主机 ${target.title}（${target.ip}）")
            // 主机热点名要现读：SavedDevice 里没有 ssid，且设备改名后旧值会失效
            val status = withContext(Dispatchers.IO) { runCatching { client.status(target.ip) }.getOrNull() }
            val ssid = status?.ssid
            if (ssid.isNullOrBlank()) {
                _state.update {
                    it.copy(multiroomBusy = false, multiroomNote = "读不到「${target.title}」的热点名，先刷新设备再试")
                }
                return@launch
            }
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    MultiroomClient(client).joinWithPolling(
                        masterIp = target.ip,
                        masterSsid = ssid,
                        slaveIp = dragged.ip,
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
            val after = withContext(Dispatchers.IO) {
                runCatching { client.slaveList(target.ip) }.getOrDefault(emptyList())
            }
            _state.update {
                it.copy(
                    multiroomBusy = false,
                    slaves = after,
                    groupMasterIp = target.ip,
                    groupMasterName = target.title,
                    multiroomNote = if (ok || after.isNotEmpty()) {
                        "「${dragged.title}」已加入「${target.title}」，现在共 ${after.size} 台同步播放"
                    } else {
                        "命令已发给音箱，但 40 秒内它没出现在组里 —— 这一步没成功。可等 1~2 分钟再刷新。"
                    },
                )
            }
            log(if (ok) "拖拽成组成功" else "拖拽成组未确认")
            refreshSlaves()
        }
    }

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
