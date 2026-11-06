package io.github.laozhudu.linkplay.console

import io.github.laozhudu.linkplay.console.playback.QueueBus
import io.github.laozhudu.linkplay.console.playback.VolumeBus
import io.github.laozhudu.linkplay.protocol.FirmwareUpdate
import io.github.laozhudu.linkplay.protocol.LinkPlayCommands
import io.github.laozhudu.linkplay.protocol.remote.DispatchResult
import io.github.laozhudu.linkplay.protocol.remote.LinkPlayTransport
import io.github.laozhudu.linkplay.protocol.remote.RemoteCommand
import io.github.laozhudu.linkplay.protocol.remote.RemoteTransport
import io.github.laozhudu.linkplay.protocol.remote.describe
import io.github.laozhudu.linkplay.protocol.remote.describeForLog
import io.github.laozhudu.linkplay.protocol.identity.UuidMatch
import io.github.laozhudu.linkplay.protocol.player.SleepTimer
import io.github.laozhudu.linkplay.protocol.player.VolumeStep
import io.github.laozhudu.linkplay.protocol.session.SavedDevice
import io.github.laozhudu.linkplay.protocol.upnp.UpnpRendererClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * 设备动作：音量 / 播放 / 预设键 / 固件升级 / 输入源 / 定时关闭。
 *
 * 从 `ConsoleViewModel`（2029 行 / 76 函数）拆出来的第二块。
 *
 * ## 边界怎么划的
 *
 * 这里只放**对设备下发的动作**。判断"能不能做"的状态（谁在线、谁在组里、谁有能力）
 * 仍在 `ConsoleUiState` 里，通过 [ConsoleHost.state] 读 —— 控制器不维护状态副本，
 * 避免出现两份真相。
 *
 * ## 与 GroupController 的分工
 *
 * - `GroupController`：组的结构（谁和谁一组、声道、屏蔽）
 * - `DeviceControl`（本类）：设备的动作（音量、播放、预设、固件）
 *
 * 交叉点是"全部播放"这类**作用于整组**的动作 —— 它们按 `groupMasterIp` 找主机，
 * 但本质是播放动作，所以留在这里。
 */
internal class DeviceControl(private val host: ConsoleHost) {

    /**
     * 语义指令的出口。
     *
     * 为什么在这里 new 一个：`host.http()` 返回的是同一个 [DeviceHttpClient] 实例
     * （ConsoleViewModel 里只有一个），所以这里不会多开连接池。
     *
     * 为什么不让调用方直接拼字符串：见 [RemoteCommand] 的类注释 ——
     * 调用点知道方言，加第二类设备时每个点都要加分支。
     */
    // 类型显式声明为接口：RemoteTransport 的注释写着「调用方只能通过 dispatch 发指令，
    // 拿不到拼字符串的能力」—— 声明成具体类时那句约束就落不了地。
    // 将来接蓝牙/红外只是多一个实现，这里一行不用改（docs/49、docs/60）。
    private val transport: RemoteTransport = LinkPlayTransport(host.http())

    /**
     * 下发一条语义指令；**失败时把原因翻译成人话写进 error**。
     *
     * 为什么这里要 throw：`host.run` 的 catch 就是"把异常显示成界面红字"的通道。
     * dispatch 本身不抛（设备睡了是正常业务），但**界面上必须看得见**——
     * 否则用户点「光纤」什么都没发生，就是铁律禁止的"按了没反应的按钮"。
     */
    private suspend fun dispatch(ip: String, command: RemoteCommand) {
        val result = transport.dispatch(ip, command)
        if (result is DispatchResult.Failed) throw java.io.IOException(result.failure.describe())
    }

    fun sendCommand(command: String) {
        val ip = host.state.value.selectedIp ?: return
        if (command.isBlank()) return
        host.run(command) {
            val raw = host.send(ip, command)
            host.log("返回：" + raw.take(400))
        }
    }


    fun renameDevice(name: String) {
        val ip = host.state.value.selectedIp ?: return
        if (name.isBlank()) return
        host.run("setDeviceName") {
            host.send(ip, LinkPlayCommands.deviceName(name))
            host.log("已请求改名为 $name")
        }
    }


    fun switchMode(mode: String) {
        val ip = host.state.value.selectedIp ?: return
        host.run("switchMode($mode)") { dispatch(ip, RemoteCommand.SelectInput(mode)) }
    }

    // ---- 多房间组：首页直接操作 ----


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
    /**
     * 音量的唯一入口（`docs/95` 第 3 项）。
     *
     * 两条通道的**语义不同**，这里收的是"共同步骤"而不是"合并语义"：
     * 拖动走合并、按键走直发 —— 都先写 `VolumeBus`，再走各自的通道。
     *
     * 为什么必须收：现在有 4 个地方想改音量，每个自己写一遍这三步，
     * 就会有某个漏掉"先写 VolumeBus"，表现是**界面 60、设备 40**（真机撞过）。
     */
    private val volumeUseCase = io.github.laozhudu.linkplay.console.usecase.SetVolumeUseCase(
        publish = { ip, v -> VolumeBus.publishVolume(ip, v) },
        sendMerged = { ip, v -> host.pushDeviceVolume(ip, v) },
        sendNow = { ip, v ->
            host.launch("volumeKey") {
                withContext(Dispatchers.IO) {
                    val result = transport.dispatch(ip, RemoteCommand.SetVolume(v))
                    if (result is DispatchResult.Failed) {
                        host.log("音量下发失败：${result.failure.describe()}")
                    }
                }
            }
        },
    )

    fun adjustVolumeByKey(delta: Int): Int? {
        val ip = host.state.value.selectedIp ?: return null
        if (host.state.value.online[ip] == false) return null
        val current = VolumeBus.volumeOf(ip)?.volume
            ?: host.state.value.groupMembers.firstOrNull { it.ip == ip }?.volume
            ?: return null
        if (!VolumeStep.shouldConsume(current, delta)) return null
        val next = VolumeStep.next(current, delta) ?: return null
        // 按键是离散事件：写 VolumeBus + **立刻下发**（不走合并通道）
        volumeUseCase.discrete(ip, next)
        // 音量键这条路**不走去抖通道**：用户按一下就要立刻听到变化，
        // 合并通道是为拖动设计的，按键本来就是离散事件，多一次 60ms 等待只会显得钝。
        host.launch("volumeKey") {
            withContext(Dispatchers.IO) {
                val result = transport.dispatch(ip, RemoteCommand.SetVolume(next))
                if (result is DispatchResult.Failed) host.log("音量键下发失败：${result.failure.describe()}")
            }
        }
        host.log("音量键 → $next")
        return next
    }


    /**
     * 现在能不能接管音量键。
     *
     * 活动必须在 ACTION_DOWN 与 ACTION_UP 上给出**一致的**答案，
     * 否则会出现"按下改了音箱、抬起又改了手机"的双重响应。
     */
    fun canHandleVolumeKey(): Boolean {
        val ip = host.state.value.selectedIp ?: return false
        if (host.state.value.online[ip] == false) return false
        return VolumeBus.volumeOf(ip) != null || host.state.value.groupMembers.any { it.ip == ip }
    }


    /** 开定时关闭（分钟）。0 = 取消。原厂设备菜单第四项。 */
    fun setSleepTimer(minutes: Int) {
        val timer = if (minutes <= 0) SleepTimer().cancel()
        else SleepTimer().start(minutes, System.currentTimeMillis())
        host.update { it.copy(sleepTimer = timer, nowMs = System.currentTimeMillis()) }
        host.log(if (timer.active) "定时关闭：${minutes} 分钟后停止" else "定时关闭：已取消")
    }


    fun cancelSleepTimer() = setSleepTimer(0)


    /**
     * 首页卡片上拖音量（原厂卡片就是这个交互）。
     *
     * 走 host.pushDeviceVolume 的合并通道：拖动会连续产生几十个值，
     * 每个都发一次 HTTP 会把老固件压垮，合并只保留最新值。
     */
    fun setCardVolume(device: SavedDevice, volume: Int) {
        // 拖动是连续事件：写 VolumeBus + 走**合并通道**（否则一秒几十次 HTTP 会压垮老固件）
        volumeUseCase.duringDrag(device.ip, volume)
    }

    // ---- 勾选式分组（Sonos Grouping 菜单）----

    







    


    /**
     * 播放/暂停切换（媒体播放器惯例：按钮显示**将发生的动作**）。
     *
     * 主页聚合条、底部大按钮都调它 —— 共用一处判定，才不会"按钮说暂停、其实还在放"。
     */
    fun togglePlayAll() {
        if (host.state.value.anyPlaying) pauseAll() else playAll()
    }


    /**
     * 单台设备的播放/暂停（卡片上的小按钮）。
     *
     * 组内从机不单独控制：它跟随主机，单独下发只会得到一条无效命令。
     * 界面已对从机隐藏该按钮，这里再兜一层。
     */
    fun toggleDevicePlay(device: SavedDevice) {
        val st = host.state.value
        val member = UuidMatch.find(device.uuid, st.groupMembers) { it.uuid }
        if (member != null && !member.isMaster) {
            host.update { it.copy(multiroomNote = "「${device.title}」跟随主机播放，请在主机上控制") }
            return
        }
        val playing = st.cards[device.uuid]?.playing == true
        host.run(if (playing) "暂停 ${device.title}" else "播放 ${device.title}") {
            val ok: Boolean = withContext(Dispatchers.IO) {
                runCatching {
                    // 走宿主缓存的客户端：原来每次 ofDevice 都要重新拉一次 description.xml
                    if (playing) host.renderer(device).pause()
                    else host.renderer(device).play()
                    true
                }.getOrDefault(false)
            }
            if (!ok) {
                withContext(Dispatchers.IO) {
                    transport.dispatch(device.ip, if (playing) RemoteCommand.Pause else RemoteCommand.Play)
                }
            }
            delay(800)
            host.refreshCards()
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
        host.run("全部播放") {
            val st = host.state.value
            val masterIp = st.groupMasterIp ?: st.selectedIp ?: st.saved.firstOrNull()?.ip
            if (masterIp == null) {
                host.update { it.copy(error = "还没有音箱") }
                return@run
            }
            val uuid = st.saved.firstOrNull { it.ip == masterIp }?.uuid
            val card = uuid?.let { st.cards[it] }
            if ((card?.plicount ?: 0) <= 0) {
                // 设备没队列：从本地快照恢复（和设备侧一样只存一份真相）
                val restored = host.loadPlayback(uuid, masterIp)
                if (restored == null || restored.items.isEmpty()) {
                    host.update { it.copy(error = "音箱上还没有列表，先去「我的音乐」点一首") }
                    return@run
                }
                QueueBus.restore(
                    deviceIp = masterIp,
                    items = restored.items,
                    index = restored.index.coerceIn(0, restored.items.lastIndex),
                    title = restored.items.getOrNull(restored.index)?.title,
                    deviceOwned = false,
                    note = "从上次播放继续",
                )
            }
            val ok: Boolean = withContext(Dispatchers.IO) {
                runCatching {
                    host.rendererForIp(masterIp).play()
                    true
                }.getOrDefault(false)
            }
            if (!ok) {
                withContext(Dispatchers.IO) { transport.dispatch(masterIp, RemoteCommand.Play) }
            }
            host.log("全部播放 → ${st.groupMasterName ?: masterIp}（组内 ${st.groupMembers.size.coerceAtLeast(1)} 台）")
            // 让界面的"在放什么"立刻刷新，不用等 3 秒心跳
            delay(1200)
            host.refreshCards()
        }
    }


    /** 全部暂停（组内一起停）。 */
    fun pauseAll() {
        host.run("全部暂停") {
            val st = host.state.value
            val targets = buildList {
                st.groupMasterIp?.let { add(it) }
                if (st.groupMasterIp == null) addAll(st.saved.map { it.ip })
            }.distinct()
            targets.forEach { ip ->
                val ok: Boolean = withContext(Dispatchers.IO) {
                    runCatching {
                        host.rendererForIp(ip).pause()
                        true
                    }.getOrDefault(false)
                }
                if (!ok) withContext(Dispatchers.IO) { transport.dispatch(ip, RemoteCommand.Pause) }
            }
            host.log("全部暂停 → ${targets.size} 台")
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
        val ip = host.state.value.selectedIp ?: return
        host.run("预设键 ${uiIndex + 1}") {
            val result = transport.dispatch(ip, RemoteCommand.Preset(uiIndex))
            host.log("预设键 ${uiIndex + 1} → ${result.describeForLog()}")
            delay(800)
            host.refreshCards()
        }
    }


    /** 查固件更新：照原厂顺序四条命令读一遍，结果如实展示。 */
    fun checkFirmware() {
        val ip = host.state.value.selectedIp ?: return
        host.run("检查固件更新") {
            val fw = withContext(Dispatchers.IO) {
                runCatching {
                    host.send(ip, LinkPlayCommands.firmwareUpdateStart())
                    delay(1_500)
                    val remote = host.send(ip, LinkPlayCommands.firmwareUpdateStatus())
                    val dl = runCatching { host.send(ip, LinkPlayCommands.firmwareDownloadStatus()) }.getOrNull()
                    val burn = runCatching { host.send(ip, LinkPlayCommands.firmwareBurnPercent()) }.getOrNull()
                    FirmwareUpdate.of(remote, dl, burn)
                }.getOrElse { t ->
                    host.log("检查固件失败：${t.message}")
                    FirmwareUpdate()
                }
            }
            host.update { it.copy(firmware = fw) }
            host.log("固件：${fw.phase}（status=${fw.statusRaw}）")
        }
    }


    /** 开始升级（界面只在 [FirmwareUpdate.canStart] 时放开这个按钮）。 */
    fun startFirmwareUpdate() {
        val ip = host.state.value.selectedIp ?: return
        host.run("开始升级固件") {
            host.log("开始下载固件…")
            host.send(ip, "setMvRemoteUpdateStart")
            repeat(40) {
                delay(3_000)
                val dl = runCatching { host.send(ip, LinkPlayCommands.firmwareDownloadStatus()) }.getOrNull()
                val burn = runCatching { host.send(ip, LinkPlayCommands.firmwareBurnPercent()) }.getOrNull()
                val fw = FirmwareUpdate.of(host.state.value.firmware.statusRaw?.toString(), dl, burn)
                host.update { it.copy(firmware = fw) }
                host.log("固件：${fw.phase}")
                if (fw.burnPercent >= 100) return@run
            }
        }
    }

    


    fun restoreToDefault() {
        val ip = host.state.value.selectedIp ?: return
        host.run("restoreToDefault") { host.send(ip, LinkPlayCommands.restoreToDefault()) }
    }


    /** 从设备页返回首页：清掉选中态，但保留已保存列表。 */
    fun leaveDevice() {
        host.update {
            it.copy(
                selectedIp = null,
                selectedLocation = null,
                selectedUuid = null,
                status = null,
                error = null,
            )
        }
    }

}
