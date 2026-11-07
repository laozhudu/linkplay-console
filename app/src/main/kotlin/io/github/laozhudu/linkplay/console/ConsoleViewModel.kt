package io.github.laozhudu.linkplay.console

import android.app.Application
import android.content.Context
import android.net.wifi.WifiManager
import androidx.lifecycle.AndroidViewModel
import io.github.laozhudu.linkplay.console.session.SessionRestore
import io.github.laozhudu.linkplay.console.session.SessionStore
import io.github.laozhudu.linkplay.discovery.DiscoveredDevice
import io.github.laozhudu.linkplay.discovery.SsdpDiscovery
import io.github.laozhudu.linkplay.protocol.DeviceHttpClient
import io.github.laozhudu.linkplay.protocol.LinkPlayCommands
import io.github.laozhudu.linkplay.protocol.LinkPlayStatus
import io.github.laozhudu.linkplay.protocol.dlna.capabilitiesOfProfile
import io.github.laozhudu.linkplay.protocol.dlna.DescriptionEndpoints
import io.github.laozhudu.linkplay.protocol.dlna.DeviceCapabilities
import io.github.laozhudu.linkplay.protocol.dlna.DeviceProfile
import io.github.laozhudu.linkplay.protocol.dlna.detect
import io.github.laozhudu.linkplay.protocol.identity.LinkPlayIdentity
import io.github.laozhudu.linkplay.protocol.identity.UuidMatch
import io.github.laozhudu.linkplay.protocol.multiroom.GroupPlan
import io.github.laozhudu.linkplay.protocol.multiroom.MultiroomClient
import io.github.laozhudu.linkplay.protocol.snapshot.DeviceSnapshot
import io.github.laozhudu.linkplay.protocol.snapshot.DeviceSnapshotReader
import io.github.laozhudu.linkplay.protocol.snapshot.toSnapshot
import io.github.laozhudu.linkplay.protocol.playback.QueueRestore
import io.github.laozhudu.linkplay.protocol.player.DeviceQueueState
import io.github.laozhudu.linkplay.protocol.session.PlaybackKey
import io.github.laozhudu.linkplay.console.playback.DevicePlaylistGateway
import io.github.laozhudu.linkplay.console.playback.DeviceVolumeControl
import io.github.laozhudu.linkplay.console.playback.DeviceProfileCache
import io.github.laozhudu.linkplay.console.playback.QueueBus
import io.github.laozhudu.linkplay.console.playback.VolumeBus
import io.github.laozhudu.linkplay.console.library.LibraryStore
import io.github.laozhudu.linkplay.console.library.LocalLibrary
import io.github.laozhudu.linkplay.console.library.PlaylistStore
import io.github.laozhudu.linkplay.protocol.mediasource.SubsonicConfig
import io.github.laozhudu.linkplay.protocol.FirmwareUpdate
import io.github.laozhudu.linkplay.protocol.player.PlayerStatus
import io.github.laozhudu.linkplay.protocol.player.SleepTimer
import io.github.laozhudu.linkplay.protocol.session.SavedDevice
import io.github.laozhudu.linkplay.protocol.upnp.UpnpRendererClient
import io.github.laozhudu.upnp.GenericRenderer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import io.github.laozhudu.linkplay.console.crash.CrashGuard
import io.github.laozhudu.linkplay.console.diagnostics.ActionTiming
import io.github.laozhudu.linkplay.console.diagnostics.AppDiagnostics
import io.github.laozhudu.linkplay.console.diagnostics.safeLaunch
import io.github.laozhudu.linkplay.lx.LxAssets
import io.github.laozhudu.linkplay.lx.LxMusicSdk
import io.github.laozhudu.linkplay.lx.QuickJsEngine

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
    /** 是否接着电（`getStatusEx.battery=0`）；false 时电量前显示 ⚡ 而不是 🔋。 */
    val charging: Boolean = false,
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
    /**
     * 这台设备**实际具备**的能力 —— 界面判断"显示哪些入口"的**唯一判据**。
     *
     * ## 为什么有了 profile 还要这个
     *
     * `profile` 是**类型标签**（"这是音箱 / 这是通用 DLNA"），用来显示文字。
     * 但"要不要渲染屏蔽按钮"不该问"它是什么类型"，该问"它有没有 multiroom 能力"。
     *
     * 按类型判断的问题：加第三类设备（电视 / 未来的蓝牙）时，每个判断点都要加一个分支，
     * 而且必然有地方漏。按能力位判断：新设备只要能力表填对，界面自动正确。
     *
     * 这是 HomeKit（Service）/ Matter（Cluster）的通行做法 —— 能力是"最低独立功能单元"，
     * 设备类型只是能力的组合清单。
     *
     * ## 为什么默认 NONE 而不是 LINKPLAY
     *
     * 卡片是探活后才填的。空卡片（还没探测）本来就不该显示任何操作入口。
     * 而界面层 `DeviceCardRow` 的默认值是 `LINKPLAY`（保持"未探测时按功能全开"的既有行为），
     * 两者默认值不同是**有意的**：卡片缺数据 ≠ 界面要降级。
     */
    val capabilities: DeviceCapabilities = DeviceCapabilities.NONE,
    /**
     * 这台设备**答过**的取数通道（累积）。
     *
     * 为什么记在卡片上：诊断报告要回答"某台设备为什么功能没反应" ——
     * 如果 `CONTROL_INFO` 从没出现过，说明它的 UPnP 控制地址读不到，
     * 声道/电量这类只能走 UPnP 的功能必然无效。这比让用户描述现象准得多。
     */
    val answered: Set<DeviceSnapshot.Channel> = emptySet(),
)

/**
 * 「最近加入」一条。
 *
 * 这里刻意用 app 层的简单结构而不是 protocol 的类型：
 * 它拼的是**两个不同的源**（电台 / 歌单），protocol 里没有、也不该有一个
 * 把两者混在一起的类型。
 */
data class RecentAdd(
    val title: String,
    /** 「电台」「歌单」这类来源标签，界面上直接显示。 */
    val kind: String,
    /** 附注，例如「12 首」。没有就给 null。 */
    val detail: String?,
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
    /** 打开 App 时对已保存设备的探活结果（uuid → 是否在线）。 */
    val online: Map<String, Boolean> = emptyMap(),
    val log: List<String> = emptyList(),
    val error: String? = null,
    /**
     * 这条错误是**哪台设备**产生的（IP）。
     *
     * 为什么要区分：真机截图里页顶挂着
     * `ConnectException: Failed to connect to /192.168.99.175:80`，
     * 而用户当前选中的是 doss1 —— 那条错误是**切去 E900V22C 时**探活失败留下的，
     * 一直挂到切回来都没清。
     *
     * 一条属于某台设备的错误挂在页面顶部，等于告诉用户"整个 App 坏了"。
     * 现在按设备归属显示（`null` = 与设备无关，比如"名字不能为空"）。
     */
    val errorIp: String? = null,
    val busy: Boolean = false,
    /** 扫描后有没有被过滤掉的其它 UPnP 设备（用于给用户一句解释，而不是「怎么什么都搜不到」）。 */
    val filteredOutCount: Int = 0,


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
    /**
     * 「接着听」的条目（定稿内容页第一组）。
     *
     * 只取 3 条：这一组是**快捷入口**不是列表页，多了会把下面的源入口挤出首屏。
     * 完整历史在「音乐库 → 最近」里。
     */
    val recentPlays: List<io.github.laozhudu.linkplay.protocol.library.RecentPlay> = emptyList(),
    /**
     * 「最近加入」的条目（定稿内容页第二组）：新收藏的电台 + 新改的歌单。
     *
     * 同样只取 3 条。没有时间戳的数据源（老电台行）按插入顺序倒序近似。
     */
    val recentAdds: List<RecentAdd> = emptyList(),
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
    /**
     * 上次运行留下的崩溃记录摘要（没有则 null）。
     *
     * 为什么放状态里而不是界面自己去读文件：首页横幅、顶栏 ⚠ 图标、诊断页三处都要用，
     * 各自读一次文件就会各自不同步。启动时读一次，之后只在"清空记录"时变。
     */
    val lastCrashAtStart: String? = null,
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
     * 「直接给某台设备设音量」的合并通道（键位 ip、值 0-100）。
     *
     * 发送时要靠 groupMasterIp 中转；而手机音量键调的是**当前设备**，
     * 跟有没有组无关。两条路径混在一起就会出现"没组网时按键没反应"。
     */
    private val deviceVolumeChannel = kotlinx.coroutines.channels.Channel<Pair<String, Int>>(
        kotlinx.coroutines.channels.Channel.CONFLATED,
    )
    private val _state = MutableStateFlow(ConsoleUiState())
    val state: StateFlow<ConsoleUiState> = _state.asStateFlow()

    // ---- 会话恢复 ----
    //
    // 「回到上次那台设备 + 把播放列表也找回来」已经搬到 `SessionRestore`。
    // 为什么整块搬：这两件事必须**按顺序**做，留在两个 private 函数里，
    // 将来谁只调一个都不会报错，只是列表空了 —— 那是最难查的一类问题。
    // 恢复结果（成功 / 为什么没恢复 / 耗时）也留在那边，由诊断页「启动恢复」段读。

    // ---- 已保存设备 ----

    private fun reloadSaved() {
        val list = runCatching { store.savedDevices() }.getOrDefault(emptyList())
        // **预热档位缓存**：档案里存着每台设备是什么，读出来就不用再猜。
        // 不做这一步，冷启动瞬间所有设备都是"未识别"，界面只能拿默认值分组 ——
        // 之前两次识别错误都是这么来的。
        DeviceProfileCache.prime(list)
        _state.update { it.copy(saved = list) }
    }

    /**
     * 探活已保存的设备。
     *
     * 为什么用 httpapi 的 `getStatusEx` 而不是 SSDP：设备可能换了 IP（实测 .164 → .153），
     * SSDP 只能证明「某台设备在」，不能把 uuid 和 IP 对上；`getStatusEx` 直接回 uuid，
     * 顺便能把新 IP 写回记录。
     */
    private fun probeSaved(actionName: ActionTiming.Action? = null) {
        val targets = _state.value.saved
        if (targets.isEmpty()) return
        safeLaunch("probeSaved") {
            // 只有**用户主动触发**才进耗时账本：心跳 3 秒一次会把"跟手"的平均值拉平，账本就废了
            val timing = actionName?.let { ActionTiming.begin(it) }

            // ① 三台**同时**问。实测单台约 90~110ms（CONTROL_INFO 43~54ms + PLAYER_STATUS 18~31ms），
            //    串行三台就是 270~330ms —— 耗时账本会判"偏慢"，用户体感是"点一下要等一下"。
            //    并发度 = 台数（家里三台），每台内部仍是**串行两条**请求，没有对单台设备加压。
            val kinds = coroutineScope {
                targets.map { d ->
                    async {
                        // 单台探活炸了不能带走其它台：并发里一个子协程抛异常会取消整个 scope，
                        // 那就会出现"某一台抽风 → 三台全显示离线" —— 比串行还坏
                        d to runCatching { probeOne(d) }.getOrElse { error ->
                            log("${d.title} 探活异常：${error.message ?: error.javaClass.simpleName}")
                            ProbeKind.UNKNOWN
                        }
                    }
                }.awaitAll()
            }
            val result = mutableMapOf<String, Boolean>()
            kinds.forEach { (d, kind) -> result[d.uuid] = kind != ProbeKind.UNKNOWN }

            // ② 没找到的（可能换了 IP）：**整批只扫一次 SSDP**。
            //    并发时若每台各扫一次，三个发现会互相抢端口，反而更不稳。
            val missing = kinds.filter { it.second == ProbeKind.UNKNOWN }.map { it.first }
            if (missing.isNotEmpty()) {
                val found = withContext(Dispatchers.IO) {
                    runCatching { discovery.discoverLinkPlayDevices() }.getOrDefault(emptyList())
                }
                for (d in missing) {
                    val hit = found.firstOrNull { it.uuid?.contains(d.uuid, ignoreCase = true) == true }
                    if (hit != null) {
                        result[d.uuid] = true
                        touch(d.uuid, hit.ip, null, hit.location)
                        log("${d.title} 换了地址：${d.ip} → ${hit.ip}")
                    }
                }
            }

            // ③ 顺带把「组」读出来：从机不可直连，只能由主机转述（真机实测从机在 10.10.10.x）
            val online = targets.filter { result[it.uuid] == true }
            val slaveLists = coroutineScope {
                online.map { d ->
                    async {
                        d to withContext(Dispatchers.IO) {
                            runCatching { MultiroomClient(client).slaves(d.ip) }.getOrDefault(emptyList())
                        }
                    }
                }.awaitAll()
            }
            val grouped = mutableMapOf<String, String>()
            var masterIp: String? = null
            var masterName: String? = null
            var members: List<GroupMember> = emptyList()
            for ((d, slaves) in slaveLists) {
                if (slaves.isEmpty()) continue
                // 有多台自报有从机时只认第一个当主机：多主机同时成立是不可能的，
                // 而"以第一个为准"至少是稳定的（原逻辑也是这个语义）
                if (masterIp != null) continue
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
                    val saved = UuidMatch.find(sl.uuid, targets) { it.uuid } ?: return@forEach
                    _state.update { st ->
                        val old = st.cards[saved.uuid] ?: DeviceCard()
                        // 只覆盖设备**真的报了值**的字段：主机有时不带 battery/channel，
                        // 直接 `copy(battery = null)` 会把上一轮的电量抹掉 —— 这就是
                        // 「电量有时有有时没有」的直接原因。
                        st.copy(
                            cards = st.cards + (saved.uuid to old.copy(
                                battery = sl.battery ?: old.battery,
                                channel = sl.channel ?: old.channel,
                            )),
                        )
                    }
                }
                val shape = buildGroup(d.ip, d.title, d.uuid, masterVolume, slaves, targets)
                grouped.putAll(shape.first)
                members = shape.second
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
            timing?.end(note = "${targets.size} 台 · 同时问")
        }
    }

    /** 单台的探活结果。三态，因为「是 LinkPlay」「是通用 DLNA 渲染器」「都不是」后续处理完全不同。 */
    private enum class ProbeKind { LINKPLAY, DMR, UNKNOWN }

    /**
     * 探**一台**。只做这一台的事，不参与跨设备决策（谁是主机），所以可以放心并发。
     *
     * 拆出来还有第二个好处："一台怎么探"从此只有一份实现，不会出现某处漏探 DMR 的情况。
     */
    private suspend fun probeOne(d: SavedDevice): ProbeKind {
        // 走唯一的取数入口（最短路径：GetControlDeviceInfo + getPlayerStatus）
        val snap = withContext(Dispatchers.IO) { snapshots.read(d.ip) }
        // uuid 必须宽容比较：设备两种格式（无连字符 24 位 / 带 uuid: 前缀 32 位）永远不"相等"
        if (snap != null && (snap.uuid == null || UuidMatch.same(snap.uuid, d.uuid))) {
            // 名字/固件等来自快照；touch 负责写回本地记录（名字以设备 DeviceName 为准）
            touch(d.uuid, d.ip, snap)
            // 能答 getStatusEx 就是 LinkPlay 的私有 httpapi —— 卡片据此显示类型标签
            putCardProfile(d.uuid, DeviceProfile.LINKPLAY, DeviceCapabilities.LINKPLAY)
            applySnapshot(d.uuid, snap)
            return ProbeKind.LINKPLAY
        }
        // ① 先看它是不是「通用 DLNA 渲染器」：这类设备**永远**答不上 httpapi，
        //    但描述里的 AVTransport 在，一样能投流。不认这一档的话，
        //    电视盒子会被判成"离线"，卡片上全是点不动的入口。
        val (upnpProfile, upnpCaps) = withContext(Dispatchers.IO) { profileOfSaved(d) }
        if (upnpProfile == DeviceProfile.GENERIC_DMR) {
            putCardProfile(d.uuid, DeviceProfile.GENERIC_DMR, upnpCaps)
            return ProbeKind.DMR
        }
        // ② 剩下的可能是「IP 变了」：交给调用方整批扫一次 SSDP（并发时各扫会抢端口）
        return ProbeKind.UNKNOWN
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
     * 唯一的取数入口。所有轮询、所有页面都通过它读设备状态。
     *
     * 最短路径：`GetControlDeviceInfo`（音量/静音/声道/从机/整份 Status，含电量与设备名）
     * + `getPlayerStatus`（曲名/进度/播放态）—— 真机 80ms + 32ms。
     * UPnP 不通时自动退 `getStatusEx`，电量不会因为 UPnP 挂了而消失。
     */
    // 不用 by lazy：DeviceSnapshotReader 构造只存字段（无副作用），
    // 而 lazy 会引入"delegate 尚未初始化就被访问"这一整类故障（见类末尾启动流程的注释）。
    private val snapshots: DeviceSnapshotReader =
        DeviceSnapshotReader(
            http = client,
            controlInfo = { ip -> runCatching { rendererByIp(ip).controlDeviceInfo() }.getOrNull() },
            dmrProbe = { ip -> probeGenericDmr(ip) },
        )

    /** 取一台设备的 UPnP 客户端（按 ip 缓存；描述拉一次就够）。 */
    private fun rendererByIp(ip: String): UpnpRendererClient {
        val key = "lp:$ip"
        dmrClients[key]?.let { return it }
        val c = UpnpRendererClient.ofDevice(ip)
        dmrClients[key] = c
        return c
    }

    /** 通用 DMR 的探测：标准 UPnP 三连，缺项就少项。 */
    /** 通用轨的客户端缓存（key = 描述地址）。描述只拉一次，之后复用。 */
    private val genericRenderers = java.util.concurrent.ConcurrentHashMap<String, GenericRenderer>()

    private fun genericRenderer(location: String?): GenericRenderer? {
        val loc = location?.takeIf { it.isNotBlank() } ?: return null
        genericRenderers[loc]?.let { return it }
        return runCatching { GenericRenderer.describe(loc) }
            .onSuccess { genericRenderers[loc] = it }
            .getOrNull()
    }

    /**
     * 探一台**通用** UPnP 渲染器（电视盒子、别的品牌音箱）。
     *
     * 现在走 `core-upnp` 的 `GenericRenderer`：只发标准动作
     * （AVTransport / RenderingControl），完全不知道 LinkPlay 私有命令的存在。
     *
     * 以前这条路径借用 `UpnpRendererClient` —— 那个类里混着 `ControlDeviceInfo`、
     * `SetChannel` 这些 DOSS 专属命令。混着用的后果是"通用设备明明不支持，
     * 我们却会去发"；分开之后，通用设备永远不会收到它看不懂的报文。
     *
     * 判据与旧实现等价：至少一个服务应答才算在线（描述拉不到 / 全失败 → 不在线）。
     */
    private fun probeGenericDmr(ip: String): DeviceSnapshot? {
        val d = _state.value.saved.firstOrNull { it.ip == ip } ?: return null
        val renderer = genericRenderer(d.location)
            ?: genericRenderer(GenericRenderer.defaultLocation(ip))
            ?: return null
        val state = runCatching { renderer.state() }.getOrNull() ?: return null
        if (state.transportState == null && state.volume == null) return null
        return DeviceSnapshot(
            title = state.title,
            volume = state.volume,
            mute = state.mute,
            playing = state.playing,
            answered = setOf(DeviceSnapshot.Channel.GENERIC_DMR),
        )
    }

    /**
     * 把一份快照写进界面状态 —— **全项目唯一的写卡片路径**。
     *
     * 之前有 4 处各写各的（心跳、设备信息、组刷新、DMR），每处都只填自己知道的字段
     * 然后整体覆盖，所以电量刚写进去就被下一轮清成 null。现在全部收敛到这里。
     */
    private fun applySnapshot(uuid: String, snap: DeviceSnapshot) {
        val ip = _state.value.saved.firstOrNull { it.uuid == uuid }?.ip
        val vol = snap.volume
        if (ip != null && vol != null) {
            VolumeBus.publish(ip, vol, snap.mute ?: false)
        }
        _state.update { st ->
            val old = st.cards[uuid] ?: DeviceCard()
            st.copy(cards = st.cards + (uuid to CardMerge.apply(old, snap)))
        }
    }

    /**
     * 取一台**已保存 LinkPlay 设备**的 UPnP 客户端（按 ip 缓存）。
     *
     * 为什么要 UPnP 客户端：声道与「一次读全」都只有 Wiimu 扩展动作能做
     * （`RenderingControl.SetChannel` / `GetControlDeviceInfo`），httpapi 没有对应命令。
     * 描述文件拉一次就缓存，卡片 3 秒一轮不会反复往返。
     */
    private fun rendererFor(d: SavedDevice): UpnpRendererClient = rendererByIp(d.ip)

    /**
     * 只按 UPnP 描述判一台已保存设备属于哪一档。
     *
     * 探活时用来认「通用 DLNA 渲染器」：它答不上 httpapi 是**正常**的，不是离线。
     * 依次试保存的 LOCATION、再试 LinkPlay 的固定路径（手动填 IP 添加的没有 LOCATION）。
     */
    private fun profileOfSaved(d: SavedDevice): Pair<DeviceProfile, DeviceCapabilities> {
        val urls = buildList {
            d.location?.takeIf { it.isNotBlank() }?.let { add(it) }
            addAll(DescriptionEndpoints.candidates(d.ip, 49152))
        }.distinct()
        for (url in urls) {
            val desc = runCatching { UpnpRendererClient.describe(url) }.getOrNull() ?: continue
            // 这里以前是 `val (profile, _) = detect(...)` —— 能力表被 `_` 丢掉了，
            // 于是 app 层 0 处消费 DeviceCapabilities，界面只能按 profile 判断。
            // 现在两个都要：profile 用于显示标签，capabilities 用于决定渲染哪些入口。
            val detected = detect(desc, httpapiReachable = false)
            if (detected.first != DeviceProfile.UNKNOWN) return detected
        }
        return DeviceProfile.UNKNOWN to DeviceCapabilities.NONE
    }

    /**
     * 把能力档写进卡片（卡片可能还没建，先建一张空的），**同时写进能力缓存**。
     *
     * 为什么两处都要写：卡片是给界面看的（隐藏不支持的入口），
     * 缓存是给起播路径看的（决定要不要走设备侧 PlayQueue）。
     * 只写一处就会出现"界面隐藏了组网、但起播还在硬推 PlayQueue"这种半截状态。
     */
    private fun putCardProfile(
        uuid: String,
        profile: DeviceProfile,
        capabilities: DeviceCapabilities = DeviceCapabilities.NONE,
    ) {
        val existing = _state.value.saved.firstOrNull { it.uuid == uuid }
        existing?.ip?.let { DeviceProfileCache.put(it, profile, capabilities) }
        // **写回档案**：档案是唯一真相，缓存只是它的镜像。
        // 只在档位真的变了时写库 —— 每 3 秒轮询写一次是无谓的 IO。
        if (existing != null && existing.profile != profile) {
            runCatching { store.upsertDevice(existing.copy(profile = profile)) }
                .onSuccess { reloadSaved() }
        }
        _state.update { st ->
            st.copy(
                cards = st.cards + (uuid to (st.cards[uuid] ?: DeviceCard())
                    .copy(profile = profile, capabilities = capabilities)),
            )
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
    private fun touch(uuid: String, ip: String, snap: DeviceSnapshot?, location: String? = null) {
        val existing = _state.value.saved.firstOrNull { it.uuid == uuid } ?: return
        // 名字的优先级（真机实测出来的顺序，别改回去）：
        //   ① getStatusEx.DeviceName —— setDeviceName 之后**立刻**就是新名字
        //   ② description.xml 的 friendlyName —— 同一个值，但要滞后几秒（.153 实测 2s 还是旧名）
        //   ③ 都没有就保留本地值
        // 绝不能落到 ssid（那是设备热点名 `DOSS DS-1831_xxx`，不是用户设的名字）
        val friendly = snap?.deviceName ?: runCatching {
            val loc = location ?: existing.location ?: "http://$ip:49152/description.xml"
            UpnpRendererClient.describe(loc).friendlyName?.takeIf { it.isNotBlank() }
        }.getOrNull()
        val updated = existing.copy(
            ip = ip,
            name = friendly ?: existing.name,
            location = location ?: existing.location,
            modelName = snap?.firmware ?: existing.modelName,
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
                groupedUnder = it.groupedUnder - UuidMatch.key(uuid),
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
            )
        }
        // **点设备 = 同时把它设成播放目标**（`docs/96` 模型 A）。
        //
        // 大厂（Sonos 的房间、Spotify 的 Connect）都只有一个目标真值；
        // 我们原来是"选中的设备"和"输出口"两套，于是会出现
        // 「设备页点了 doss2，声音却从 doss1 出来」。这一行就是在拆掉那个分裂。
        runCatching {
            io.github.laozhudu.linkplay.console.playback.PlaybackOutputStore.write(
                getApplication(),
                io.github.laozhudu.linkplay.console.playback.PlaybackOutput.Device(device.ip, device.name),
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
        safeLaunch("scan") {
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
                // 两种来源各给一个人话标签 —— 用户要能分清"我的音箱"和"别的播放设备"
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
        safeLaunch("addManual") {
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
            // 唯一构造入口：uuid 归一化在 SavedDevice.of 里做（三处各写一遍曾导致设备重影）
            return SavedDevice.of(
                rawId = status.uuid,
                name = status.ssid?.takeIf { it.isNotBlank() } ?: ip,
                ip = ip,
                location = null, // 描述地址探测见下
                modelName = status.hardware,
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
        return SavedDevice.of(
            rawId = found.udn,
            name = found.friendlyName ?: ip,
            ip = ip,
            location = usedUrl,
            modelName = found.modelName,
        )
    }

    private fun save(device: SavedDevice) {
        val next = runCatching { store.upsertDevice(device) }.getOrDefault(_state.value.saved)
        _state.update { it.copy(saved = next, online = it.online + (device.uuid to true)) }
    }

    /**
     * 扫描结果 → 档位。
     *
     * **直接用 `DiscoveredDevice.profile`** —— 发现层在拉描述时就已经判过了
     * （私有 `PlayQueue` 服务 → 音箱；标准 `AVTransport` → 通用播放设备）。
     *
     * 这两条正是用户说的「doss 有特殊标识的」。
     *
     * 我一度在这里又建了一张 `ip → profile` 的 map 想"记住扫描结论" ——
     * 那是**重复造一份已经存在的信息**，而且它还是内存的（重启就没了）。
     * 正确做法是这一个字段 + 写进设备档案。
     */
    private fun profileOfDiscovered(device: DiscoveredDevice): DeviceProfile =
        device.profile ?: DeviceProfile.UNKNOWN

    /** 扫描结果里点「添加」：保存并直接进入。 */
    fun add(device: DiscoveredDevice) {
        // 扫描那一刻就已经判定过了（`discoverLinkPlayDevices` 靠私有 PlayQueue 服务，
        // `discoverMediaRenderers` 靠标准 AVTransport）—— 直接写进档案。
        // 不要"保存后再探一次"：刚入网的设备 httpapi 常常还没起好，
        // 那一探会把它判错；而档案是永久的，错一次就一直错。
        val known = profileOfDiscovered(device)
        val saved = SavedDevice.of(
            rawId = device.uuid,
            name = device.friendlyName ?: device.friendlySsid ?: device.ip,
            ip = device.ip,
            location = device.location,
            profile = known,
        )
        save(saved)
        _state.update { it.copy(devices = it.devices.filterNot { d -> d.ip == device.ip }) }
        putCardProfile(saved.uuid, known, capabilitiesOfProfile(known))
        log("已保存 ${saved.name}（${if (known == DeviceProfile.LINKPLAY) "音箱" else "通用播放设备"}）")
        open(saved)
    }

    // ---- 设备操作 ----

    fun select(ip: String) {
        val location = _state.value.devices.firstOrNull { it.ip == ip }?.location
        _state.update {
            it.copy(selectedIp = ip, selectedLocation = location, status = null)
        }
        // 同 [open]：选中即目标
        runCatching {
            io.github.laozhudu.linkplay.console.playback.PlaybackOutputStore.write(
                getApplication(),
                io.github.laozhudu.linkplay.console.playback.PlaybackOutput.Device(
                    ip,
                    _state.value.saved.firstOrNull { it.ip == ip }?.name ?: ip,
                ),
            )
        }
        refreshStatus()
    }

    /**
     * 把"当前选中的设备"同步成播放目标。
     *
     * 凡是 `selectedIp` 变成一台具体设备的地方都该调它 —— 包括启动恢复。
     * 什么时候**不**调：用户显式选了「本机」。那种情况下 selectedIp 仍然指向某台设备
     * （用来查看状态），但目标就是本机，不该被覆盖回去。
     */
    private fun syncTargetToSelected() {
        val ip = _state.value.selectedIp ?: return
        val name = _state.value.saved.firstOrNull { it.ip == ip }?.name ?: ip
        runCatching {
            io.github.laozhudu.linkplay.console.playback.PlaybackOutputStore.write(
                getApplication(),
                io.github.laozhudu.linkplay.console.playback.PlaybackOutput.Device(ip, name),
            )
        }
    }

    fun refreshStatus() {
        val ip = _state.value.selectedIp ?: return
        run("getStatusEx") {
            val status = client.status(ip)
            // UUID 在这里落定：SSDP 只给 IP/LOCATION，UUID 必须问设备要
            _state.update { it.copy(status = status, selectedUuid = status.uuid) }
            status.uuid?.let { touch(it, ip, status.toSnapshot()) }
        }
    }

    /**
     * 重新探活并刷新组结构（首页下拉/进入时调用）。
     *
     * 与 [refreshDevices] 共用同一个动作名：它们是**同一件事的两条入口**。
     * 重复触发检测会因此报警 —— 那是真实存在的重复，不是噪音，报警是对的。
     */
    fun refreshGroup() = probeSaved(ActionTiming.Action.REFRESH_DEVICES)

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
        probeSaved(ActionTiming.Action.REFRESH_DEVICES)
    }

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
            val member = UuidMatch.find(device.uuid, st.groupMembers) { it.uuid }
            val masterIp = st.groupMasterIp
            val viaSlave = member != null && !member.isMaster && masterIp != null
            if (viaSlave) {
                client.send(masterIp!!, LinkPlayCommands.slaveDeviceName(member!!.ip, name))
            } else {
                client.send(device.ip, LinkPlayCommands.deviceName(name))
            }
            // 回读真值：`getStatusEx.DeviceName` 是立刻生效的那个字段；
            // `description.xml` 的 friendlyName 要滞后几秒（.153 实测 2s 仍是旧名）。
            // 上一版 800ms 就去读 friendlyName，读回旧名又把用户设的名字覆盖回去 —— 表现为"改名不成功"。
            var applied: String? = null
            for (attempt in 0 until 8) {
                delay(if (attempt == 0) 300 else 450)
                applied = withContext(Dispatchers.IO) {
                    if (viaSlave) {
                        runCatching {
                            MultiroomClient(client).slaves(masterIp!!).firstOrNull { it.ip == member!!.ip }?.name
                        }.getOrNull()
                    } else {
                        runCatching { client.status(device.ip).deviceName }.getOrNull()
                    }
                }
                if (applied == name) break
            }
            if (applied == name) {
                // 以设备回报为准写回本地记录（名称只有一个真相源：设备）
                val next = _state.value.saved.map { if (it.uuid == device.uuid) it.copy(name = name) else it }
                runCatching { store.upsertDevice(next.first { it.uuid == device.uuid }) }
                _state.update { it.copy(saved = next, multiroomNote = "已改名为「$name」") }
                log("改名生效：${device.title} → $name")
            } else {
                // 不假装成功：把设备实际报的名字写进提示
                _state.update {
                    it.copy(multiroomNote = "改名没生效：设备仍报「${applied ?: "无响应"}」")
                }
                log("改名未生效：期望 $name，设备报 ${applied ?: "无响应"}")
            }
            probeSaved()
            refreshDevicesInGroup()
        }
    }

    /** 立刻重读一遍组内设备（比等 3 秒心跳快）。 */
    /**
     * 组拓扑心跳：只读主机的 `slave_list`（几百字节），**整体替换**组模型。
     *
     * ## 为什么必须有它
     *
     * 组的认知原来只在 `probeSaved`（启动 / 操作 / 手动刷新）更新。
     * 设备端退组之后界面还停在旧状态 —— 用户点「退出同步」，代码却认为"它不在组里"，
     * 直接 return，一条命令都不发。这就是「加了组退不出来」。
     *
     * ## 判据严格分开
     *
     * - 有设备**成功**读到非空 → 建组（谁是主机由设备真值决定，不看我们缓存）
     * - 所有在线设备都**成功**读到空 → 才清空（一次读失败不算"组没了"）
     * - 全都读不到 → 什么都不改，只记诊断
     *
     * 这三条分开是必须的：把"读失败"当成"没人组网"，会出现组内从机集体掉出组的情况。
     */
    private suspend fun refreshGroupTopology() {
        val st = _state.value
        val saved = st.saved
        if (saved.isEmpty()) return
        val candidates = saved.filter { st.online[it.uuid] != false }
        if (candidates.isEmpty()) return

        var foundMaster: SavedDevice? = null
        var foundSlaves: List<io.github.laozhudu.linkplay.protocol.multiroom.SlaveInfo> = emptyList()
        var succeeded = 0
        val started = System.currentTimeMillis()
        for (d in candidates) {
            val r = withContext(Dispatchers.IO) { runCatching { MultiroomClient(client).slaves(d.ip) } }
            if (r.isFailure) continue
            succeeded++
            val list = r.getOrThrow()
            if (list.isEmpty()) continue
            foundMaster = d
            foundSlaves = list
            break
        }
        val ms = System.currentTimeMillis() - started

        val master = foundMaster
        when {
            master != null -> {
                val shape = buildGroup(
                    masterIp = master.ip,
                    masterName = master.title,
                    masterUuid = master.uuid,
                    masterVolume = st.cards[master.uuid]?.volume,
                    slaves = foundSlaves,
                    saved = saved,
                )
                val unmatched = foundSlaves.count { sl -> UuidMatch.find(sl.uuid, saved) { it.uuid } == null }
                _state.update {
                    it.copy(
                        groupedUnder = shape.first,
                        groupMasterIp = master.ip,
                        groupMasterName = master.title,
                        groupMembers = shape.second,
                    )
                }
                GroupSourceStatus.topologyRead(master.title, foundSlaves.size, unmatched, ms)
            }
            succeeded > 0 -> {
                _state.update {
                    it.copy(
                        groupedUnder = emptyMap(),
                        groupMasterIp = null,
                        groupMasterName = null,
                        groupMembers = emptyList(),
                    )
                }
                GroupSourceStatus.topologyEmpty(ms)
            }
            else -> GroupSourceStatus.topologyFailed("所有设备都读不到 slave_list")
        }
    }

    private suspend fun refreshDevicesInGroup() {
        val st = _state.value
        val ips = buildList {
            st.groupMasterIp?.let { add(it) }
            addAll(st.saved.map { it.ip })
        }.distinct()
        for (ip in ips) {
            val uuid = st.saved.firstOrNull { it.ip == ip }?.uuid ?: continue
            // 与其他轮询共用同一个取数入口与同一条合并规则 —— 这样"谁后跑"不再影响结果
            val snap = withContext(Dispatchers.IO) { snapshots.read(ip) } ?: continue
            applySnapshot(uuid, snap)
        }
    }

    /**
     * 洛雪脚本引擎自检：加载 bundle + 搜一个固定关键词，结果写进诊断。
     *
     * ## 为什么这件事排在界面之前
     *
     * `core-lx` 的 80 个单测全跑在**假引擎**上（`FakeEngine`），
     * 唯一没验证过的环节是「QuickJS 里那些 polyfill 真的能跑」——
     * `pako` / `buffer` 在 QuickJS 与 Node 的行为未必一致（`docs/74` 第六节）。
     *
     * 所以先让 App 自己跑一次并说出结果。这一步不过，做多少界面都是白做。
     */
    private suspend fun runLxSelfCheck() {
        val app = getApplication<Application>()
        LxSourceStatus.started()
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val engine = QuickJsEngine()
                try {
                    val sdk = LxMusicSdk(engine, LxAssets.musicSdk(app))
                    val loadStarted = System.currentTimeMillis()
                    sdk.load()
                    LxSourceStatus.loaded(System.currentTimeMillis() - loadStarted)
                    val searchStarted = System.currentTimeMillis()
                    val page = sdk.search("wy", "周杰伦", limit = 5)
                    page to (System.currentTimeMillis() - searchStarted)
                } finally {
                    runCatching { engine.close() }
                }
            }
        }
        result.onSuccess { (page, ms) ->
            if (page.value != null) {
                LxSourceStatus.searched("wy", "周杰伦", page.value!!.tracks.size, ms)
            } else {
                LxSourceStatus.failed("搜索", page.error)
            }
        }.onFailure { t ->
            LxSourceStatus.failed("引擎或 bundle", t.message ?: t.javaClass.simpleName)
        }
    }

    /**
     * 由主机的 `slave_list` 算出「归属表 + 成员列表」。
     *
     * 抽成一个函数是因为：`probeSaved`（完整探活）和组拓扑心跳（每 3 秒）都要做这件事。
     * 各写一份必然漂移 —— 一边用 uuid 归一化、另一边忘了，就会出现
     * 「设备在组里但卡片上没有归属」，进而**连退出同步的入口都不显示**。
     */
    private fun buildGroup(
        masterIp: String,
        masterName: String,
        masterUuid: String?,
        masterVolume: Int?,
        slaves: List<io.github.laozhudu.linkplay.protocol.multiroom.SlaveInfo>,
        saved: List<SavedDevice>,
    ): Pair<Map<String, String>, List<GroupMember>> {
        val grouped = mutableMapOf<String, String>()
        var unmatched = 0
        val members = ArrayList<GroupMember>()
        members += GroupMember(
            ip = masterIp,
            name = masterName,
            uuid = masterUuid,
            volume = masterVolume,
            isMaster = true,
        )
        slaves.forEach { sl ->
            val target = UuidMatch.find(sl.uuid, saved) { it.uuid }
            if (target == null) unmatched++
            // 归属表用**归一化 key**：设备两种 uuid 格式混着来，任何一处用原始字符串比都会漏
            target?.let { grouped[UuidMatch.key(it.uuid)] = masterName }
            members += GroupMember(
                ip = sl.ip,
                name = sl.name ?: sl.ip,
                uuid = sl.uuid?.removePrefix("uuid:"),
                volume = sl.volume,
                channel = sl.channel,
                isMaster = false,
                masked = sl.masked,
            )
        }
        return grouped to members
    }

    /** 刷新「我的音乐」六个入口的数量。 */
    fun refreshMusicCounts() {
        safeLaunch("refreshMusicCounts") {
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
            // 「接着听」：带设备的读法（定稿那行要显示「投给了哪台」）
            val recentPlays = withContext(Dispatchers.IO) {
                runCatching { LibraryStore.get(app).recents.recentWithDevice(3) }.getOrDefault(emptyList())
            }
            // 「最近加入」：新收藏的电台 + 新改的歌单，各取几条拼一起。
            val recentAdds = withContext(Dispatchers.IO) {
                runCatching {
                    val radios = LibraryStore.get(app).radios.all()
                        .asReversed()
                        .take(2)
                        .map { RecentAdd(title = it.name, kind = "电台", detail = null) }
                    val lists = PlaylistStore.get(app).lists()
                        .take(2)
                        .map {
                            RecentAdd(
                                title = it.name,
                                kind = "歌单",
                                detail = "${it.tracks.size} 首",
                            )
                        }
                    (radios + lists).take(3)
                }.getOrDefault(emptyList())
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
                    recentPlays = recentPlays,
                    recentAdds = recentAdds,
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
        safeLaunch("refreshPlaylistCount") {
            val lists = withContext(Dispatchers.IO) {
                runCatching { PlaylistStore.get(getApplication()).count() }.getOrDefault(0)
            }
            _state.update { it.copy(playlistCount = lists) }
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
    
    fun clearLog() = _state.update { it.copy(log = emptyList()) }

    private fun run(label: String, block: suspend () -> Unit) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, error = null) }
        // 用调用方给的 label 做标签：比 "run" 这种占位词能直接对上"我点了什么"
        safeLaunch(label) {
            try {
                withContext(Dispatchers.IO) { block() }
                log("$label 完成")
            } catch (t: Throwable) {
                // 记下**当时**在操作哪台 —— 之后切设备了，这条错误也不会跟到别的行上
                _state.update {
                    it.copy(
                        error = "${t.javaClass.simpleName}: ${t.message}",
                        errorIp = it.selectedIp,
                    )
                }
                log("$label 失败：${t.message}")
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    private fun log(message: String) = _state.update { it.copy(log = (it.log + message).takeLast(200)) }

    // ---- 多房间：全部委托给 GroupController（见该类注释）----

    /**
     * 控制器共享的能力面实现。
     *
     * 抽成一个字段而不是每个控制器各写一个匿名对象 —— 各写一份必然漂移
     * （同一个能力在两处名字/语义不同，正是本项目一直在治的病）。
     */
    private val consoleHost = object : ConsoleHost {
            override val state get() = _state
            override fun update(transform: (ConsoleUiState) -> ConsoleUiState) = _state.update(transform)
            override fun log(message: String) = this@ConsoleViewModel.log(message)
            override fun run(label: String, block: suspend () -> Unit) = this@ConsoleViewModel.run(label, block)
            override fun launch(source: String, block: suspend CoroutineScope.() -> Unit) {
                safeLaunch(source) { block() }
            }
            override fun http(): DeviceHttpClient = client
            override fun send(ip: String, command: String): String = client.send(ip, command)
            override fun status(ip: String): LinkPlayStatus = client.status(ip)
            override suspend fun refreshAll() {
                probeSaved()
                refreshDevicesInGroup()
            }
            override fun refresh() {
                probeSaved()
                safeLaunch("refreshCards") { refreshDevicesInGroup() }
            }
            override fun renderer(device: SavedDevice): UpnpRendererClient = rendererFor(device)
            override fun rendererForIp(ip: String): UpnpRendererClient = rendererByIp(ip)
        override fun pushDeviceVolume(ip: String, volume: Int) {
            deviceVolumeChannel.trySend(ip to volume)
        }
        override suspend fun refreshCards() = refreshDevicesInGroup()
        override fun loadPlayback(
            uuid: String?,
            ip: String,
        ): io.github.laozhudu.linkplay.protocol.session.PlaybackSession? =
            PlaybackKey.candidates(uuid, ip).firstNotNullOfOrNull { key ->
                runCatching { store.loadPlayback(key) }.getOrNull()
            }

        override fun lastDeviceKey(): String? = runCatching { store.loadLastDevice() }.getOrNull()
    }

    /** 会话恢复（重开 App 回到上次那台设备 + 播放列表）。见 [SessionRestore]。 */
    private val sessionRestore = SessionRestore(consoleHost)

    /** 诊断页的段注册与报告拼装。见 [ConsoleDiagnostics]。 */
    private val diagnostics = ConsoleDiagnostics(app, consoleHost, sessionRestore)

    private val groups = GroupController(consoleHost)

    fun ungroupCard(device: SavedDevice) = groups.ungroupCard(device)
    fun setCardChannel(device: SavedDevice, channel: Int) = groups.setCardChannel(device, channel)
    fun openGrouping() = groups.openGrouping()
    fun closeGrouping() = groups.closeGrouping()
    fun toggleGroupingDevice(uuid: String) = groups.toggleGroupingDevice(uuid)
    fun selectEverywhere() = groups.selectEverywhere()
    fun applyGrouping() = groups.applyGrouping()
    fun toggleMask(device: SavedDevice) = groups.toggleMask(device)

    // ---- 设备动作：全部委托给 DeviceControl（见该类注释）----

    private val control = DeviceControl(consoleHost)

    fun sendCommand(command: String) = control.sendCommand(command)
    fun renameDevice(name: String) = control.renameDevice(name)
    fun switchMode(mode: String) = control.switchMode(mode)
    fun adjustVolumeByKey(delta: Int): Int? = control.adjustVolumeByKey(delta)
    fun canHandleVolumeKey(): Boolean = control.canHandleVolumeKey()
    fun setSleepTimer(minutes: Int) = control.setSleepTimer(minutes)
    fun cancelSleepTimer() = control.cancelSleepTimer()
    fun setCardVolume(device: SavedDevice, volume: Int) = control.setCardVolume(device, volume)
    fun togglePlayAll() = control.togglePlayAll()
    fun toggleDevicePlay(device: SavedDevice) = control.toggleDevicePlay(device)
    fun playAll() = control.playAll()
    fun pauseAll() = control.pauseAll()
    fun pressPreset(uiIndex: Int) = control.pressPreset(uiIndex)
    fun checkFirmware() = control.checkFirmware()
    fun startFirmwareUpdate() = control.startFirmwareUpdate()
    fun restoreToDefault() = control.restoreToDefault()
    fun leaveDevice() = control.leaveDevice()

    // ---- 诊断 ----
    //
    // 段注册与报告拼装已经搬到 `ConsoleDiagnostics`（它只读状态，不改状态）。
    // ViewModel 这边只留"清记录 / 读上次崩溃 / 落盘"三条写操作 —— 它们要改状态。

    /** 诊断页要的那份文本报告（界面只负责显示和复制）。 */
    fun diagnosticsReport(): String = diagnostics.report()


    /** 清掉崩溃记录与问题记录（用户确认"看过了/已解决"时用）。 */
    fun clearDiagnostics() {
        val app = getApplication<Application>()
        runCatching { CrashGuard.clear(app) }
        AppDiagnostics.clear(app)
        _state.update { it.copy(lastCrashAtStart = null) }
        log("已清空诊断记录")
    }

    /** 上次崩溃的原始记录（首页横幅点开时展示）。 */
    fun lastCrash(): String? = runCatching { CrashGuard.lastReport(getApplication()) }.getOrNull()

    /** 把运行期问题落盘，供下次启动查看。 */
    fun flushIssues() = runCatching { AppDiagnostics.flush(getApplication()) }

    private fun acquireMulticastLock(): WifiManager.MulticastLock? = runCatching {
        val wifi = getApplication<Application>().applicationContext
            .getSystemService(Context.WIFI_SERVICE) as WifiManager
        wifi.createMulticastLock("linkplay-discovery").apply {
            setReferenceCounted(false)
            acquire()
        }
    }.getOrNull()
    // ============================================================
    // 启动流程
    //
    // **必须放在所有属性声明之后**。这不是风格问题，是踩过的坑：
    // 之前 init 块在 `snapshots`（by lazy）声明之前，init 里 probeSaved() 启动的协程
    // 经 withContext(Dispatchers.IO) 派发到 IO 线程，抢在主线程跑完 init 之前
    // 访问了 `snapshots` → `Lazy.getValue() on null` → 整个探活协程终止
    // → 三台设备全部"状态=未探测"（诊断报告 2026-11-04 09:45:34 那条 NPE）。
    //
    // Kotlin 按声明顺序初始化字段与 init 块，所以 init 永远放最后。
    // CI 里有静态检查（scripts/check-init-order.sh）防止再犯。
    // ============================================================

    init {
        diagnostics.register()
        // 上一次运行是否崩过：有就在首页顶部给一条可点开的横幅。
        // 这是"用户能截图给我"的入口 —— 系统弹窗只给模板文案，我们给堆栈。
        runCatching {
            val crash = CrashGuard.lastReport(app)
            if (crash != null) _state.update { it.copy(lastCrashAtStart = crash) }
        }
        // 目标（本机/设备）是唯一真值：别的页面把它改到某台设备时，这里跟着切过去。
        // 反方向由 open()/select() 负责 —— 两个方向都收敛到同一个值，不会再出现
        // 「选中 doss2 但投给 doss1」。
        runCatching { io.github.laozhudu.linkplay.console.playback.PlaybackOutputStore.prime(getApplication()) }
        safeLaunch("observePlaybackTarget") {
            io.github.laozhudu.linkplay.console.playback.PlaybackOutputStore.current
                .filterNotNull()
                .collect { target ->
                    val ip = (target as? io.github.laozhudu.linkplay.console.playback.PlaybackOutput.Device)
                        ?.ip?.takeIf { it.isNotEmpty() } ?: return@collect
                    if (_state.value.selectedIp == ip) return@collect
                    val dev = _state.value.saved.firstOrNull { it.ip == ip } ?: return@collect
                    open(dev)
                }
        }

        // 首页一进来就有内容：读已保存设备，并后台探活（不清空界面、不阻塞）
        reloadSaved()
        probeSaved()
        // 恢复必须在**启动时**做，而不是等用户点开播放页：
        // 否则"重开 App 列表是空的"会被当成恢复失效。
        // device 先、queue 后是这个顺序唯一正确的排法，已在 SessionRestore.restore() 里写死。
        sessionRestore.restore()
        // **恢复出来的"上次那台设备"也要成为播放目标。**
        //
        // 漏了这一步的后果（用户截图）：顶栏显示 E900V22C，左下角 cast 却写「本机」——
        // 因为 `SessionRestore` 只改了 `selectedIp`，没改目标，目标还停在上次手动选的「本机」。
        // 这是"两套状态"的最后一处漏网：open()/select() 都同步了，恢复这条路没同步。
        syncTargetToSelected()
        // 首页多房间卡片的低频心跳：不在播放页时也能跟上设备侧/遥控器的音量变化
        safeLaunch("groupVolumeHeartbeat") {
            while (true) {
                delay(io.github.laozhudu.linkplay.console.util.PollCadence.GROUP_VOLUME_MS)
                val masterIp = _state.value.groupMasterIp ?: continue
                withContext(Dispatchers.IO) {
                    runCatching {
                        val st = PlayerStatus.parse(client.send(masterIp, "getPlayerStatus"))
                        VolumeBus.publish(masterIp, st.volume, st.mute)
                    }
                }
            }
        }
        // 洛雪引擎自检：只在启动时跑一次（docs/74 要求先验证引擎，再谈界面）。
        safeLaunch("lxSelfCheck") { runLxSelfCheck() }
        // 组拓扑心跳：3 秒一次，只读 slave_list（几百字节，比完整快照轻得多）。
        // 没有它，组的认知就只在探活时更新 —— 设备退组了界面也不知道，
        // 用户想退也点不到（卡片上"退出同步"按 inGroup 决定显示与否）。
        safeLaunch("groupTopology") {
            while (true) {
                delay(io.github.laozhudu.linkplay.console.util.PollCadence.GROUP_TOPOLOGY_MS)
                if (_state.value.saved.isEmpty()) continue
                refreshGroupTopology()
            }
        }
        // 首页卡片心跳：每 3 秒逐台读 getPlayerStatus（原厂设备列表就显示每台的曲名/音量）。
        // 为什么不用 1 秒：卡片信息变化慢，3 秒够了；设备只有 2 台，但别把老固件打爆。
        safeLaunch("cardHeartbeat") {
            while (true) {
                delay(io.github.laozhudu.linkplay.console.util.PollCadence.CARD_HEARTBEAT_MS)
                val saved = _state.value.saved
                if (saved.isEmpty()) continue
                for (d in saved) {
                    if (_state.value.online[d.uuid] == false) continue
                    // 只轮询"路由器侧够得着"的设备：入组的从机在主机 AP 网段里，
                    // 从这边直接连必然超时（真机实测），跳过去，靠主机的 slave_list 表达它的状态。
                    if (_state.value.groupedUnder.containsKey(UuidMatch.key(d.uuid))) continue
                    // 走**唯一**的取数入口：一次拿全（含电量/声道/设备名），
                    // 再由 applySnapshot 合并进卡片。之前这里自己拼一张全新 DeviceCard 整体覆盖，
                    // 把另一条通道刚写的电量清成 null —— 用户报的"电量时有时无"就是这个。
                    val known = _state.value.cards[d.uuid]?.profile
                    val snap = withContext(Dispatchers.IO) {
                        if (known == DeviceProfile.GENERIC_DMR) snapshots.readGenericDmr(d.ip)
                        else snapshots.read(d.ip)
                    } ?: continue
                    applySnapshot(d.uuid, snap)
                }
            }
        }
        // 定时关闭的时钟：每秒推进一次，到点下发停止。
        safeLaunch("sleepTimerTick") {
            while (true) {
                delay(io.github.laozhudu.linkplay.console.util.PollCadence.SLEEP_TICK_MS)
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
        safeLaunch("deviceVolumeDispatch") {
            for ((ip, v) in deviceVolumeChannel) {
                withContext(Dispatchers.IO) {
                    // 原厂做法：UPnP RenderingControl 优先，httpapi 兜底（见 DeviceVolumeControl）
                    runCatching { DeviceVolumeControl.setVolume(ip, v) }
                        .onFailure { log("调音量失败：${it.message}") }
                }
                delay(60)
            }
        }
    }
}
