package io.github.laozhudu.linkplay.console

import io.github.laozhudu.linkplay.protocol.LinkPlayStatus
import io.github.laozhudu.linkplay.protocol.LinkPlayCommands
import io.github.laozhudu.linkplay.protocol.DeviceHttpClient
import io.github.laozhudu.linkplay.protocol.identity.UuidMatch
import io.github.laozhudu.linkplay.protocol.multiroom.GroupPlan
import io.github.laozhudu.linkplay.protocol.multiroom.MultiroomClient
import io.github.laozhudu.linkplay.protocol.session.SavedDevice
import io.github.laozhudu.linkplay.protocol.upnp.UpnpRendererClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

/**
 * 多房间（组网 / 声道 / 屏蔽 / 分组面板）—— 从 `ConsoleViewModel` 拆出来的第一块。
 *
 * ## 为什么拆
 *
 * 改造前 `ConsoleViewModel` 是 2027 行 / 76 个函数 / 8 个关注点的 god object。
 * 改一个"组网"的小逻辑要在两千行里找，而且很容易碰到无关代码 —— 这几轮已经出过几次。
 *
 * 拆分的边界不是"按文件切"，而是**按关注点切**：这里只有"组"相关的东西。
 * 音量、播放控制、设备发现、会话恢复仍在 ViewModel 里，下一轮继续搬。
 *
 * ## 为什么用 [GroupHost] 而不是直接拿 ViewModel
 *
 * 这块代码要读写共享状态、要发命令、要跑协程、要拿 UPnP 客户端。
 * 直接传 `ConsoleViewModel` 会形成循环依赖（VM → Controller → VM），
 * 而且控制器会顺手用到不该用的东西。所以只暴露它**真正需要**的 10 个操作，
 * 依赖面一目了然，也方便以后单独测试。
 */
internal class GroupController(private val host: GroupHost) {

    /**
     * 宿主能力面：控制器需要的全部外部操作。
     *
     * 刻意保持很小 —— 每多一个方法，控制器就多一条能"顺手改别人"的路。
     */
    internal interface GroupHost {
        val state: StateFlow<ConsoleUiState>
        fun update(transform: (ConsoleUiState) -> ConsoleUiState)
        fun log(message: String)
        /** 带 busy 闸门的动作执行（与 ViewModel 的 run 同一套语义）。 */
        fun run(label: String, block: suspend () -> Unit)
        /** 启动一个协程（异常已被 safeLaunch 收口）。 */
        fun launch(block: suspend CoroutineScope.() -> Unit)
        /** 原始 HTTP 客户端（MultiroomClient 这类协议客户端需要它）。 */
        fun http(): DeviceHttpClient
        fun send(ip: String, command: String): String
        fun status(ip: String): LinkPlayStatus
        /** 探活 + 刷新卡片并**等待完成**（协程内用；原来叫 probeSaved + refreshDevicesInGroup）。 */
        suspend fun refreshAll()
        /** 同上但不等（非协程上下文用，内部自己起协程）。 */
        fun refresh()
        fun renderer(device: SavedDevice): UpnpRendererClient
        fun pushDeviceVolume(ip: String, volume: Int)
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
        val st = host.state.value
        val masterIp = st.groupMasterIp
        val member = UuidMatch.find(device.uuid, st.groupMembers) { it.uuid }
        if (masterIp == null || member == null) {
            host.update { it.copy(multiroomNote = "「${device.title}」不在组里，无需解除") }
            return
        }
        host.run("解除组网 ${device.title}") {
            val targets = if (member.isMaster) {
                st.groupMembers.filter { !it.isMaster }.map { it.ip }
            } else {
                listOf(member.ip)          // 用主机自报的 10.10.10.x，不是保存记录里的旧 IP
            }
            targets.forEach { ip ->
                host.log("SlaveKickout($ip) → " + runCatching { host.send(masterIp, "multiroom:SlaveKickout:$ip") }.getOrDefault("ERR"))
            }
            // 等从机回到原 Wi-Fi 再刷，否则界面会先显示"离线"
            delay(3_500)
            host.refreshAll()
            host.refreshAll()
            host.update {
                it.copy(multiroomNote = if (targets.isEmpty()) "组里没有从机" else "已解除 ${targets.size} 台从机")
            }
            host.log("解除组网完成")
        }
    }



    /**
     * 直接指定声道（界面上的三选一）。
     *
     * 为什么要有这条：成熟产品（Sonos 建立立体声对、M3 的 single-select segmented button）
     * 都是**一步选定**左/右，而不是"点一下轮一个"。循环点击的毛病是：
     * 想从「仅左」回到「立体声」得点两下，中间那一下会真的把声音切到「仅右」。
     */
    fun setCardChannel(device: SavedDevice, channel: Int) {
        val st = host.state.value
        val member = UuidMatch.find(device.uuid, st.groupMembers) { it.uuid }
        if (member != null && !member.isMaster) {
            setGroupChannel(member, channel)
            return
        }
        applyChannel(device, channel)
    }

    /**
     * 给独立设备/主机设声道，并在下发后**回读真实值**。
     *
     * 为什么必须回读：这个项目反复验证过「命令回 OK ≠ 功能生效」。
     * 界面显示的值只能是设备报的值；乐观更新只用于让点击立刻有反馈，随后被真值覆盖。
     */
    private fun applyChannel(device: SavedDevice, channel: Int) {
        val ch = channel.coerceIn(0, 2)
        host.update { st ->
            st.copy(cards = st.cards + (device.uuid to (st.cards[device.uuid] ?: DeviceCard()).copy(channel = ch)))
        }
        host.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { host.renderer(device).setChannel(ch) }.isSuccess
            }
            if (!ok) {
                // 拿不到 UPnP 控制地址（描述读不到）才走到这里；如实说，不假装成功
                host.update { it.copy(multiroomNote = "「${device.title}」读不到控制地址，声道改不了") }
                host.log("设声道失败：${device.title}")
                return@launch
            }
            delay(500)
            val readBack = withContext(Dispatchers.IO) {
                runCatching { host.renderer(device).controlDeviceInfo().channel }.getOrNull()
            }
            if (readBack != null) {
                host.update { st ->
                    st.copy(cards = st.cards + (device.uuid to (st.cards[device.uuid] ?: DeviceCard()).copy(channel = readBack)))
                }
            }
            host.log("声道 → ${readBack ?: ch}（${device.title}）")
        }
    }

    /** 组内从机声道：只能由主机转达 `multiroom:SlaveChannel`，改完回读 `slave_list`。 */
    fun setGroupChannel(member: GroupMember, channel: Int) {
        val master = host.state.value.groupMasterIp ?: return
        if (member.isMaster) return
        val ch = channel.coerceIn(0, 2)
        host.update { st ->
            st.copy(groupMembers = st.groupMembers.map { if (it.ip == member.ip) it.copy(channel = ch) else it })
        }
        host.launch {
            val reply = withContext(Dispatchers.IO) {
                runCatching { host.send(master, "multiroom:SlaveChannel:${member.ip}:$ch") }.getOrNull()
            }
            host.log("SlaveChannel(${member.ip}:$ch) → ${reply ?: "ERR"}")
            delay(700)
            // 回读真值：主机转述的 slave_list 才是这台从机的真实声道
            val real = withContext(Dispatchers.IO) {
                runCatching { MultiroomClient(host.http()).slaves(master) }.getOrDefault(emptyList())
                    .firstOrNull { it.ip == member.ip }?.channel
            }
            if (real != null) {
                host.update { st ->
                    st.copy(groupMembers = st.groupMembers.map { if (it.ip == member.ip) it.copy(channel = real) else it })
                }
                host.log("从机声道回读 = $real")
            }
        }
    }

    /** 打开分组面板：默认勾上"现在在组里的"。 */
    fun openGrouping() {
        val st = host.state.value
        val current = st.groupMembers.filter { !it.isMaster }.mapNotNull { it.uuid?.lowercase() }.toSet()
        host.update { it.copy(groupingOpen = true, groupingSelected = current) }
    }

    fun closeGrouping() = host.update { it.copy(groupingOpen = false, groupingProgress = null) }

    fun toggleGroupingDevice(uuid: String) = host.update { st ->
        val key = uuid.lowercase()
        st.copy(
            groupingSelected = if (key in st.groupingSelected) st.groupingSelected - key
            else st.groupingSelected + key,
        )
    }

    fun selectEverywhere() {
        val st = host.state.value
        val master = st.saved.firstOrNull { it.ip == st.groupMasterIp }?.uuid
        host.update {
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
        val st = host.state.value
        val masterIp = st.groupMasterIp ?: st.selectedIp
        if (masterIp == null) {
            host.update { it.copy(groupingProgress = "先选一台主机") }
            return
        }
        val masterUuid = st.saved.firstOrNull { it.ip == masterIp }?.uuid
        val current = st.groupMembers.filter { !it.isMaster }.mapNotNull { it.uuid }.toSet()
        val plan = GroupPlan.diff(current, st.groupingSelected, masterUuid)
        if (plan.empty) {
            host.update { it.copy(groupingOpen = false, groupingProgress = null, multiroomNote = "分组没有变化") }
            return
        }
        host.update { it.copy(groupingProgress = plan.describe()) }
        host.launch {
            val status = withContext(Dispatchers.IO) { runCatching { host.status(masterIp) }.getOrNull() }
            val ssid = status?.ssid
            if (ssid.isNullOrBlank()) {
                host.update { it.copy(groupingProgress = "读不到主机热点名，先刷新设备") }
                return@launch
            }
            val mroom = MultiroomClient(host.http())
            val ap = withContext(Dispatchers.IO) { runCatching { mroom.readApInfo(masterIp) }.getOrNull() }
            val pwd = withContext(Dispatchers.IO) {
                runCatching { mroom.readJoinPassword(masterIp) }.getOrDefault("")
            }

            // ① 先移出：一台设备只能在一个组里，留在旧组会"取消了还在同步"
            for (uuid in plan.leave) {
                val member = UuidMatch.find(uuid, st.groupMembers) { it.uuid } ?: continue
                host.update { it.copy(groupingProgress = "移出 ${member.name}…") }
                withContext(Dispatchers.IO) {
                    runCatching { host.send(masterIp, LinkPlayCommands.slaveKickout(member.ip)) }
                }
                host.log("分组：移出 ${member.name}（${member.ip}）")
            }
            // ② 再加入
            for (uuid in plan.join) {
                val device = st.saved.firstOrNull { it.uuid.equals(uuid, true) } ?: continue
                host.update { it.copy(groupingProgress = "加入 ${device.title}…") }
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
                host.log("分组：加入 ${device.title}（${device.ip}）")
            }
            // ③ 等设备之间建链（真机约 8 秒），再统一刷新
            host.update { it.copy(groupingProgress = "等待设备建链…") }
            delay(6_000)
            host.refreshAll()
            val after = (host.state.value.groupMembers.size - 1).coerceAtLeast(0)
            host.update {
                it.copy(
                    groupingOpen = false,
                    groupingProgress = null,
                    multiroomNote = if (after > 0) "已同步 $after 台（共 ${after + 1} 台）"
                    else "命令已发出，但没检测到从机",
                )
            }
            host.log("分组完成：${plan.describe()} → 现在 $after 台从机")
        }
    }

    /**
     * 屏蔽/恢复某台从机（`mask=1` 不出声但仍留在组里）。
     *
     * 与「解除」的区别：解除 = 踢出组（它回自己 Wi-Fi）；屏蔽 = 还在组里、只是不出声。
     * 真机：`SlaveMask` → `mask` 0→1；`SlaveUnMask` → 回 0。
     */
    fun toggleMask(device: SavedDevice) {
        val st = host.state.value
        val masterIp = st.groupMasterIp
        val member = UuidMatch.find(device.uuid, st.groupMembers) { it.uuid }
        if (masterIp == null || member == null || member.isMaster) {
            host.update { it.copy(multiroomNote = "只有组内从机能屏蔽") }
            return
        }
        host.run(if (member.masked) "恢复 ${device.title}" else "屏蔽 ${device.title}") {
            val cmd = if (member.masked) LinkPlayCommands.slaveUnMask(member.ip)
            else LinkPlayCommands.slaveMask(member.ip)
            host.log("$cmd → " + runCatching { host.send(masterIp, cmd) }.getOrDefault("ERR"))
            delay(1_500)
            host.refreshAll()
        }
    }

    

    



    







    








}
