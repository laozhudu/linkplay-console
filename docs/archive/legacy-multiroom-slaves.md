# 归档：旧多房间模型（已删除，需要时可抽用）

**归档日期**：2026-11-04
**删除提交**：见本文件同 PR（`refactor: 删除旧多房间模型与死代码`）
**恢复方式**：见文末

---

## 为什么删

这个项目里曾经并存**两套多房间模型**：

```
旧  state.slaves : List<SlaveDevice>
    ├─ 只有 ip / name / volume / ssid
    ├─ 无 isMaster、无 masked、无 battery、无 channel
    └─ 来源：PR #24「多房间一键组网」时代

新  state.groupMembers : List<GroupMember>
    ├─ 有 isMaster / masked / battery / channel / uuid
    ├─ 支持主机/从机区分、屏蔽、声道、电量
    └─ 来源：PR #48「勾选式分组」+ PR #49「声道/电量/改名」
```

新模型上线后，旧的 14 个函数**一次都没被 UI 调用**（逐个核对过 `MainActivity` 与
`DeviceSettingsScreen` / `DeviceContentScreen` 的 `vm.xxx(` 调用点）。
但它们一直留在 `ConsoleViewModel` 里，导致：

1. 读代码时要先判断"这个 `slaves` 是新的还是旧的"；
2. 改一个组网逻辑要在两千行里找，容易改到旧的那份（这几轮出过几次）；
3. `state` 里挂着 7 个只有死代码读写的字段，`ConsoleUiState` 越来越难读。

**这不是"写法不一致"，是"模型并存"** —— docs/41 一致性审计里那类问题的另一种形态。
用户决定：**归档后全删，以后发现有用时再抽用。**

---

## 被删的函数（14 个）

全部来自 `GroupController`（该类是 PR #56 从 `ConsoleViewModel` 拆出来的）：

| 函数 | 用途（旧模型语义） | 为什么没人用 |
|---|---|---|
| `setGroupVolume(member, volume)` | 组内某台音量（走合并通道） | 音量滑块已统一走 `setCardVolume(device, volume)` |
| `cycleCardChannel(device)` | 点一下循环 LR→L→R | 已改为三选一面板 `setCardChannel` |
| `removeFromGroup(member)` | 移出组 | 已由 `ungroupCard(device)` 取代 |
| `addToGroup(slave)` | 加入组（40 秒轮询） | 已由勾选式 `applyGrouping()` 取代 |
| `setSlaveVolume(slaveIp, volume)` | `multiroom:SlaveVolume` | 同上，且 `slaveIp` 是旧模型字段 |
| `setJoinChannel(ch)` | 成组时手填信道 | 现在自动读主机 `WifiChannel` |
| `setJoinPassword(pwd)` | 成组时手填密码 | 现在按 `securemode` 自动判定 |
| `joinSlave(device)` | 一键组网（轮询 40 秒） | 已由 `applyGrouping()` 取代 |
| `cancelMultiroom()` | 停止等待 | 随 `joinSlave` 一起废弃 |
| `setSlaveDialog(slave)` | 打开从机详情框 | 详情已内联到首页卡片 |
| `clearSlaveDialog()` | 关闭详情框 | 同上 |
| `kickSlave(slaveIp)` | 踢出（旧签名） | 已由 `ungroupCard` 取代 |
| `renameSlave(slaveIp, name)` | 从机改名（旧签名） | 已由 `renameCard(device, name)` 取代 |
| `refreshSlaves()` | 刷新 `state.slaves` | 探活已统一走 `probeSaved()` → `groupMembers` |

## 被删的 state 字段（7 个）

只有上面那些死函数读写，UI 一次没读：

```kotlin
    val slaves: List<SlaveDevice> = emptyList(),

    /** 正在成组（轮询等待中）。 */
    val multiroomBusy: Boolean = false,

    /** 成组轮询已等待的秒数。 */
    val multiroomTick: Long = 0,

    /** 正在加入的音箱显示名，给进度条用。 */
    val multiroomTarget: String? = null,

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
```

## 被删的合并通道

`setGroupVolume` 专用，其它地方不写：

```kotlin
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
```

以及它的消费者协程：

```kotlin
                delay(60)
            }
        }
        safeLaunch("ConsoleViewModel") {
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

```

---

## 完整源码（恢复时直接粘回 `GroupController` 类体内）

```kotlin
FUNCS
/**
     * 调组内某台音箱的音量。
     *
     * 为什么不走 [run]：那里有 `busy` 闸门，音量条连续拖动时第二次就被丢掉，
     * 手感立刻变「不跟手」。这里乐观更新本地值 + 直接下发，失败只记日志。
     */
    fun setGroupVolume(member: GroupMember, volume: Int) {
        val master = host.state.value.groupMasterIp ?: return
        val v = volume.coerceIn(0, 100)
        host.update { st ->
            st.copy(groupMembers = st.groupMembers.map { if (it.ip == member.ip) it.copy(volume = v) else it })
        }
        // 主机音量写进全站总线，播放页那一侧同一帧就跟着动
        if (member.isMaster) {
            VolumeBus.publishVolume(master, v)
            host.pushDeviceVolume(master, v)
            return
        }
        // 主机的音量是它自己的；从机必须由主机代转（从机在主机热点网段，直连不可达）
        host.pushGroupVolume(member, v)
    }

    fun cycleCardChannel(device: SavedDevice) {
        val st = host.state.value
        // 组内从机匹配必须用宽容 uuid：设备在 slave_list 里给的是 `uuid:FF31F017-…`（32 位带连字符），
        // 我们保存的是 `FF31F0174FE5C9B2A2ACEAE3`（24 位）。用 equals 永远匹配不上，
        // 于是点一下只会得到"不在组里"—— 这就是上一版"声道又不能用"的真因。
        val member = UuidMatch.find(device.uuid, st.groupMembers) { it.uuid }
            ?: UuidMatch.find(device.ip, st.groupMembers) { it.ip }
        if (member != null && !member.isMaster) {
            // 组内从机在主机 AP 网段（10.10.10.x），我们连不上，只能由主机转达
            val next = ((member.channel ?: 0) + 1) % 3
            setGroupChannel(member, next)
            return
        }
        // 独立设备 / 主机：走 UPnP RenderingControl.SetChannel（原厂 master 分支）。
        // 真机实测 .153 未组网时 SetChannel 1/2/0 均 200 且回读一致 —— 声道不依赖组网。
        val next = ((st.cards[device.uuid]?.channel ?: 0) + 1) % 3
        applyChannel(device, next)
    }

/** 把某台从机移出组。 */
    fun removeFromGroup(member: GroupMember) {
        val master = host.state.value.groupMasterIp ?: return
        if (member.isMaster) return
        host.run("移出组 ${member.name}") {
            host.log("SlaveKickout → " + host.send(master, "multiroom:SlaveKickout:${member.ip}"))
            delay(1500)
        }
        host.refresh()
    }

/**
     * 把一台已保存的音箱加进组。
     *
     * 主机取当前组的主机；还没有组时取第一台在线的已保存设备。
     * 协议细节见 [MultiroomClient.joinWithPolling]（照原厂：`getNetwork` 的整段返回当密码）。
     */
    fun addToGroup(slave: SavedDevice) {
        val master = host.state.value.groupMasterIp
            ?: host.state.value.saved.firstOrNull { host.state.value.online[it.uuid] == true }?.ip
        if (master == null) {
            host.update { it.copy(error = "先添加并连上一台音箱") }
            return
        }
        if (host.state.value.multiroomBusy) return
        host.update {
            it.copy(multiroomBusy = true, multiroomTick = 0, multiroomTarget = slave.title, multiroomNote = null)
        }
        host.launch {
            val (ok, reason) = withContext(Dispatchers.IO) {
                val ap = runCatching { MultiroomClient(host.http()).readApInfo(master) }.getOrNull()
                    ?: return@withContext false to "读不到主机热点信息"
                val ok = runCatching {
                    MultiroomClient(host.http()).joinWithPolling(
                        masterIp = master,
                        masterSsid = ap.ssid,
                        slaveIp = slave.ip,
                        onTick = { sec -> host.launch { host.update { it.copy(multiroomTick = sec) } } },
                        shouldStop = { !host.state.value.multiroomBusy },
                    )
                }.getOrElse { false }
                ok to null
            }
            host.update {
                it.copy(
                    multiroomBusy = false,
                    multiroomNote = if (ok) "已把「${slave.title}」加入" else (reason ?: "命令已发出，但 40 秒内它没出现在组里"),
                )
            }
            host.refreshAll()
        }
    }

    fun setSlaveVolume(slaveIp: String, volume: Int) {
        val ip = host.state.value.selectedIp ?: return
        host.run("SlaveVolume($slaveIp=$volume)") {
            host.send(ip, LinkPlayCommands.slaveVolume(slaveIp, volume.coerceIn(0, 100)))
        }
    }

/** 成组用的可选项：主机热点信道（0=自动）与密码（我们的设备 securemode=0，一般留空）。 */
    fun setJoinChannel(ch: Int) = host.update { it.copy(joinChannel = ch.coerceIn(0, 14)) }

    fun setJoinPassword(pwd: String) = host.update { it.copy(joinPassword = pwd) }

    fun joinSlave(device: SavedDevice) {
        val masterIp = host.state.value.selectedIp ?: return
        val masterSsid = host.state.value.status?.ssid
        if (masterSsid.isNullOrBlank()) {
            host.update { it.copy(error = "还读不到本机热点名，先刷新设备信息") }
            return
        }
        if (host.state.value.multiroomBusy) return
        host.update {
            it.copy(
                multiroomBusy = true,
                multiroomTick = 0,
                multiroomTarget = device.title,
                multiroomNote = null,
                error = null,
            )
        }
        host.launch {
            host.log("开始成组：${device.title}（${device.ip}）→ 主机 $masterIp（$masterSsid）")
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    MultiroomClient(host.http()).joinWithPolling(
                        masterIp = masterIp,
                        masterSsid = masterSsid,
                        slaveIp = device.ip,
                        channel = host.state.value.joinChannel,
                        password = host.state.value.joinPassword,
                        onTick = { sec ->
                            host.log("等待从机出现… ${sec}s / 40s")
                            host.launch { host.update { it.copy(multiroomTick = sec) } }
                        },
                        shouldStop = { !host.state.value.multiroomBusy },
                    )
                }.getOrElse { t ->
                    host.log("成组异常：${t.message}")
                    false
                }
            }
            val after = withContext(Dispatchers.IO) { runCatching { host.slaveList(masterIp) }.getOrDefault(emptyList()) }
            host.update {
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
                host.log("成组成功：主机现在有 ${after.size} 台从机")
            } else {
                host.log("成组未确认：40 秒内主机仍没看到从机（命令已被设备接受，但设备之间没建立连接）")
                host.log("可以等 1~2 分钟再点「重新读取从机」；若从机暂时离线，它会自己连回原 Wi-Fi")
            }
        }
    }

    fun cancelMultiroom() = host.update {
        it.copy(multiroomBusy = false, multiroomNote = "已停止等待（组网不一定成功，可稍后「重新读取」看看）")
    }

/** 点从机芯片 → 打开它的单独音量/退出框。 */
    fun setSlaveDialog(slave: SlaveDevice) = host.update { it.copy(slaveDialog = slave) }

    fun clearSlaveDialog() = host.update { it.copy(slaveDialog = null) }

    fun kickSlave(slaveIp: String) {
        val master = host.state.value.selectedIp ?: return
        host.run("踢出 $slaveIp") {
            host.log("SlaveKickout → " + host.send(master, "multiroom:SlaveKickout:$slaveIp"))
            host.update { it.copy(slaves = host.slaveList(master)) }
        }
    }

    fun renameSlave(slaveIp: String, name: String) {
        val master = host.state.value.selectedIp ?: return
        if (name.isBlank()) return
        host.run("从机改名") {
            host.send(master, LinkPlayCommands.slaveDeviceName(slaveIp, name))
            host.update { it.copy(slaves = host.slaveList(master)) }
        }
    }

    fun refreshSlaves() {
        val master = host.state.value.selectedIp ?: return
        host.run("读取从机") {
            host.update { it.copy(slaves = host.slaveList(master)) }
        }
    }
```

---

## 恢复方法

### 方式一：从 git 历史取（推荐）

```bash
# 找到删除这个文件的那次提交
git log --oneline --all -- docs/archive/legacy-multiroom-slaves.md

# 取删除前的版本
git show <commit>^:app/src/main/kotlin/io/github/laozhudu/linkplay/console/GroupController.kt

# 或直接取本文件
git show <commit>:docs/archive/legacy-multiroom-slaves.md
```

### 方式二：从本文件复制

上面的「完整源码」段落就是原样代码，粘回 `GroupController` 类体内即可。

**但要注意**：这些函数用的是 `host.xxx`（`GroupHost` 接口），不是 `_state`。
如果将来 `GroupHost` 变了，需要同步调整。恢复前先看当时的 `GroupHost` 定义。

### 恢复前必读

这 14 个函数**依赖旧的 `state.slaves` 模型**（`SlaveDevice` 只有 ip/name/volume）。
如果只是想要其中某一个功能，**不建议整段恢复**，按需重写更快：

- 想要"组内单台音量" → 现在 `setCardVolume(device, volume)` 已能覆盖
- 想要"从机详情框" → 首页卡片上已经有音量/声道/电量/屏蔽，不需要弹框
- 想要"手填信道/密码" → 只有 `getNetwork` 不返回密码的固件才需要，目前两台都不需要

---

## 附：删除前后的规模

| | 删除前 | 删除后 |
|---|---|---|
| `ConsoleViewModel.kt` | 1677 行 | **1613 行**（-64） |
| `GroupController.kt` | 506 行 | **332 行**（-174） |
| `ConsoleUiState` 字段数 | 40 | **33**（-7） |
| 合计删除 | | **255 行代码** |

单测：623 用例 0 失败（与删除前完全一致，一个没改）。

另外顺手清掉的东西（也记录在案）：

- `GroupHost.pushGroupVolume` —— 只服务 `setGroupVolume`
- `GroupHost.slaveList` —— 只服务 `refreshSlaves` / `kickSlave` / `renameSlave`
- `SlaveDevice` 的 import（两处）—— 旧模型的类型，已无引用
- 三个失效 import（`VolumeBus`、`viewModelScope`、`PlayState`）
