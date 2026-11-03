package io.github.laozhudu.linkplay.protocol.snapshot

import io.github.laozhudu.linkplay.protocol.DeviceHttpClient
import io.github.laozhudu.linkplay.protocol.LinkPlayStatus
import io.github.laozhudu.linkplay.protocol.SlaveDevice
import io.github.laozhudu.linkplay.protocol.player.PlayState
import io.github.laozhudu.linkplay.protocol.player.PlayerStatus
import io.github.laozhudu.linkplay.protocol.upnp.ControlDeviceInfo
import io.github.laozhudu.linkplay.protocol.upnp.UpnpRendererClient

/**
 * 读一台设备的完整状态 —— **全项目唯一**的取数入口。
 *
 * ## 最短路径
 *
 * 一次 [read] 最多两次请求：
 * 1. UPnP `RenderingControl.GetControlDeviceInfo`（真机 80ms）
 *    → 音量 / 静音 / 声道 / 从机列表 / `<Status>`（整份 getStatusEx：电量、设备名、固件）
 * 2. httpapi `getPlayerStatus`（真机 32ms）→ 曲名 / 进度 / 播放态 / 列表长度
 *
 * 原厂就是这么读的：`GetControlDeviceInfo` 是它唯一的设备信息入口，
 * 我们之前拆成 4~5 条各写各的，才出现"电量刚写就被别的轮询清掉"。
 *
 * ## 降级
 *
 * - 拿不到 UPnP 描述（手输 IP、设备还没起 UPnP）→ 退 `getStatusEx`，数据等价（就是 `<Status>` 那段）。
 * - 设备没有 PlayQueue/AVTransport（通用 DMR）→ 走标准 UPnP 三连。
 *
 * 所有结果都归一成 [DeviceSnapshot]；合并规则只有 [DeviceSnapshot.mergeWith] 一份。
 */
class DeviceSnapshotReader(
    private val http: DeviceHttpClient,
    /**
     * UPnP `GetControlDeviceInfo` 的取数器（调用方负责缓存客户端）。
     * 返回 null 表示这台没有 UPnP 控制地址。
     *
     * 为什么用函数而不是直接收 [UpnpRendererClient]：这个类要能在单测里用假数据驱动，
     * 而 `UpnpRendererClient` 是 final 且需要一个假传输层才能造出来 —— 收一个函数最省事，
     * 调用方一行 `{ ip -> rendererFor(ip)?.controlDeviceInfo() }` 就接上。
     */
    private val controlInfo: (String) -> ControlDeviceInfo?,
    /** 通用 DMR 的探测（标准 UPnP 三连）；默认不探测。 */
    private val dmrProbe: (String) -> DeviceSnapshot? = { null },
) {

    /**
     * 读一台 LinkPlay 设备。
     *
     * @param ip 设备当前 IP
     * @return 快照；两条通道**都失败**时返回 null（调用方据此判离线）
     */
    fun read(ip: String): DeviceSnapshot? {
        var snap = DeviceSnapshot()
        var anyAnswer = false

        // ---- ① 主路径：GetControlDeviceInfo（音量/静音/声道/从机/整份 Status）----
        val info = runCatching { controlInfo(ip) }.getOrNull()
        if (info != null) {
            anyAnswer = true
            snap = snap.mergeWith(
                DeviceSnapshot(
                    volume = info.volume,
                    mute = info.mute,
                    channel = info.channel,
                    batteryPercent = info.status?.batteryPercent,
                    onBattery = info.status?.onBattery,
                    deviceName = info.status?.deviceName,
                    firmware = info.status?.firmware,
                    uuid = info.status?.uuid,
                    // SlaveList 与 multiroom:getSlaveList 同格式；读到了就算"已知"
                    slaves = info.slaves,
                    slavesRead = true,
                    answered = setOf(DeviceSnapshot.Channel.CONTROL_INFO),
                ),
            )
        }

        // ---- ② 兜底：getStatusEx（与 <Status> 等价；UPnP 不通时电量也不会丢）----
        if (snap.batteryPercent == null || snap.deviceName == null) {
            val raw = runCatching { http.send(ip, "getStatusEx") }.getOrNull()
            val status = raw?.let { runCatching { LinkPlayStatus.parse(it) }.getOrNull() }
            if (status != null) {
                anyAnswer = true
                snap = snap.mergeWith(status.toSnapshot())
            }
        }

        // ---- ③ 播放态：getPlayerStatus（曲名/进度/是否在播，Status 里没有这些）----
        val playerRaw = runCatching { http.send(ip, "getPlayerStatus") }.getOrNull()
        val player = playerRaw?.let { runCatching { PlayerStatus.parse(it) }.getOrNull() }
        if (player != null) {
            anyAnswer = true
            snap = snap.mergeWith(
                DeviceSnapshot(
                    volume = player.volume,
                    mute = player.mute,
                    channel = player.channel,
                    title = player.title?.takeIf { it.isNotBlank() && it != "Unknown" },
                    playing = player.playState == PlayState.PLAY,
                    mode = player.mode,
                    plicount = player.playListCount,
                    plicurr = player.playListCurrent,
                    answered = setOf(DeviceSnapshot.Channel.PLAYER_STATUS),
                ),
            )
        }

        // ---- ④ 组信息：主路径没给 SlaveList 时补一次 ----
        if (!snap.slavesRead && anyAnswer) {
            val raw = runCatching { http.send(ip, "multiroom:getSlaveList") }.getOrNull()
            if (raw != null) {
                val list = runCatching { SlaveDevice.parseList(raw) }.getOrNull()
                if (list != null) {
                    snap = snap.mergeWith(
                        DeviceSnapshot(
                            slaves = list,
                            slavesRead = true,
                            answered = setOf(DeviceSnapshot.Channel.SLAVE_LIST),
                        ),
                    )
                }
            }
        }

        return snap.takeIf { anyAnswer }
    }

    /**
     * 读一台通用 DLNA 渲染器（没有 httpapi）。
     *
     * 只能用标准 UPnP：`GetTransportInfo` + `GetPositionInfo` + `GetVolume`，
     * 三项都是尽力而为 —— 缺哪项就少哪项，不整张卡作废。
     */
    fun readGenericDmr(ip: String): DeviceSnapshot? = runCatching { dmrProbe(ip) }.getOrNull()
}

/** `getStatusEx` → 快照。抽成扩展函数，兜底路径与主路径共用同一套字段映射。 */
fun LinkPlayStatus.toSnapshot(): DeviceSnapshot = DeviceSnapshot(
    deviceName = deviceName,
    firmware = firmware,
    uuid = uuid,
    batteryPercent = batteryPercent,
    onBattery = onBattery,
    answered = setOf(DeviceSnapshot.Channel.STATUS_EX),
)
