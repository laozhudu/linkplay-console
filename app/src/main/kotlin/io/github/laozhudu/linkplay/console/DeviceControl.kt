package io.github.laozhudu.linkplay.console

import io.github.laozhudu.linkplay.console.playback.QueueBus
import io.github.laozhudu.linkplay.console.playback.VolumeBus
import io.github.laozhudu.linkplay.protocol.FirmwareUpdate
import io.github.laozhudu.linkplay.protocol.LinkPlayCommands
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
        host.run("switchMode($mode)") { host.send(ip, LinkPlayCommands.switchMode(mode)) }
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
    fun adjustVolumeByKey(delta: Int): Int? {
        val ip = host.state.value.selectedIp ?: return null
        if (host.state.value.online[ip] == false) return null
        val current = VolumeBus.volumeOf(ip)?.volume
            ?: host.state.value.groupMembers.firstOrNull { it.ip == ip }?.volume
            ?: return null
        if (!VolumeStep.shouldConsume(current, delta)) return null
        val next = VolumeStep.next(current, delta) ?: return null
        VolumeBus.publishVolume(ip, next)
        // 音量键这条路**不走去抖通道**：用户按一下就要立刻听到变化，
        // 合并通道是为拖动设计的，按键本来就是离散事件，多一次 60ms 等待只会显得钝。
        host.launch {
            withContext(Dispatchers.IO) {
                runCatching { host.send(ip, LinkPlayCommands.playerVolume(next)) }
                    .onFailure { host.log("音量键下发失败：${it.message}") }
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
        val ip = device.ip
        VolumeBus.publishVolume(ip, volume)
        host.pushDeviceVolume(ip, volume.coerceIn(0, 100))
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
                    if (playing) UpnpRendererClient.ofDevice(device.ip).pause()
                    else UpnpRendererClient.ofDevice(device.ip).play()
                    true
                }.getOrDefault(false)
            }
            if (!ok) {
                withContext(Dispatchers.IO) {
                    runCatching {
                        host.send(device.ip, if (playing) "setPlayerCmd:pause" else "setPlayerCmd:play")
                    }
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
                    UpnpRendererClient.ofDevice(masterIp).play()
                    true
                }.getOrDefault(false)
            }
            if (!ok) {
                withContext(Dispatchers.IO) { runCatching { host.send(masterIp, "setPlayerCmd:play") } }
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
                        UpnpRendererClient.ofDevice(ip).pause()
                        true
                    }.getOrDefault(false)
                }
                if (!ok) withContext(Dispatchers.IO) { runCatching { host.send(ip, "setPlayerCmd:pause") } }
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
            val reply = host.send(ip, LinkPlayCommands.presetKey(uiIndex))
            host.log("MCUKeyShortClick:${uiIndex + 1} → $reply")
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
