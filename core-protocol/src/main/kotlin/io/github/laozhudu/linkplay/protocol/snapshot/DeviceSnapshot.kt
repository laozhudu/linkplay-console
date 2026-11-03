package io.github.laozhudu.linkplay.protocol.snapshot

import io.github.laozhudu.linkplay.protocol.SlaveDevice

/**
 * 一台设备的**唯一**状态快照。
 *
 * ## 为什么要有这个类（现状问题）
 *
 * 改造前，同一台音箱的状态由 5 条互不相干的通道各写各的：
 * ```
 * 曲名/音量/播放态/列表长度  ← httpapi  getPlayerStatus
 * 电量/设备名/固件          ← httpapi  getStatusEx
 * 声道/静音                 ← UPnP     GetControlDeviceInfo
 * 从机列表                  ← httpapi  multiroom:getSlaveList
 * 通用 DMR 的一切           ← UPnP     标准三连
 * ```
 * 每条通道各自 `cards + (uuid to 新卡片)` 整体覆盖。谁最后跑，谁把别人的字段清成默认值。
 * 表现就是用户报的「电量有时有有时没有」——电量刚写进去，3 秒心跳一跑就没了。
 *
 * ## 最短路径（原厂做法）
 *
 * 反编译原厂可见它**只用一个入口**：`RenderingControl.GetControlDeviceInfo`
 * （`org/teleal/cling/support/d/a/a.java`）。一条请求同时返回：
 * ```
 * CurrentVolume / CurrentMute / CurrentChannel / SlaveMask / SlaveList / Status
 * ```
 * 而 `<Status>` 里嵌的就是整份 `getStatusEx`（含 battery_percent、DeviceName、firmware…）。
 * 真机实测（.153 / .164，各 3 次取均值）：
 * ```
 * GetControlDeviceInfo   80ms   3112B   一次拿到 音量+静音+声道+从机+整份Status
 * getStatusEx            69ms   1375B   只是 Status 那一部分
 * getPlayerStatus        32ms    485B   曲名/进度/播放态（Status 里没有）
 * getSlaveList           51ms     60B   SlaveList 里已有
 * ```
 * 所以最短路径 = **GetControlDeviceInfo（1 次）+ getPlayerStatus（1 次）**，
 * 4~5 次请求降到 2 次，且电量与声道来自同一次响应，不会再互相覆盖。
 *
 * 这就是这个类的意义：**所有通道的结果先归一到这里，再一次性写进界面状态**。
 */
data class DeviceSnapshot(
    // ---- 设备身份 ----
    /** 设备名（`getStatusEx.DeviceName`，改名的真值来源）。 */
    val deviceName: String? = null,
    val firmware: String? = null,
    val uuid: String? = null,

    // ---- 音量 / 声道 ----
    val volume: Int? = null,
    val mute: Boolean? = null,
    /** 0 立体声 / 1 仅左 / 2 仅右（`CurrentChannel`）。 */
    val channel: Int? = null,

    // ---- 电池 ----
    val batteryPercent: Int? = null,
    /** `battery=1` 表示在用电池，`0` 表示接电/充电。 */
    val onBattery: Boolean? = null,

    // ---- 播放 ----
    val title: String? = null,
    val playing: Boolean? = null,
    val mode: Int? = null,
    val plicount: Int? = null,
    val plicurr: Int? = null,

    // ---- 组 ----
    val slaves: List<SlaveDevice> = emptyList(),
    /** 这一轮**确实读到了**从机列表（哪怕是空列表）。false 时不能把已有组信息清掉。 */
    val slavesRead: Boolean = false,

    // ---- 元信息：这一轮哪些通道真的答了（排障与"不假装成功"都用它） ----
    val answered: Set<Channel> = emptySet(),
) {
    /** 这一轮到底问了哪条通道。排障时一眼看出"电量为什么没有"。 */
    enum class Channel {
        /** UPnP `RenderingControl.GetControlDeviceInfo`（原厂主路径，含 Status）。 */
        CONTROL_INFO,

        /** httpapi `getStatusEx`（CONTROL_INFO 不可用时的兜底，数据等价）。 */
        STATUS_EX,

        /** httpapi `getPlayerStatus`（曲名/进度/播放态，Status 里没有）。 */
        PLAYER_STATUS,

        /** httpapi `multiroom:getSlaveList`（CONTROL_INFO 里没有 SlaveList 时用）。 */
        SLAVE_LIST,

        /** 通用 DMR 的标准 UPnP 三连。 */
        GENERIC_DMR,
    }

    val hasBattery: Boolean get() = batteryPercent != null

    /**
     * 合并：**新值里非 null 的字段覆盖旧值，null 保留旧值**。
     *
     * 这是全项目唯一的合并规则。所有通道、所有轮询都必须走它 ——
     * 之前每个轮询各写一份"整体替换"，才会出现电量/声道被反复清空。
     *
     * `slaves` 特殊：只有 [slavesRead] 为 true 时才覆盖，
     * 否则一次失败会把整组设备从界面上抹掉。
     */
    fun mergeWith(fresh: DeviceSnapshot): DeviceSnapshot = DeviceSnapshot(
        deviceName = fresh.deviceName ?: deviceName,
        firmware = fresh.firmware ?: firmware,
        uuid = fresh.uuid ?: uuid,
        volume = fresh.volume ?: volume,
        mute = fresh.mute ?: mute,
        channel = fresh.channel ?: channel,
        batteryPercent = fresh.batteryPercent ?: batteryPercent,
        onBattery = fresh.onBattery ?: onBattery,
        title = fresh.title?.takeIf { it.isNotBlank() } ?: title,
        playing = fresh.playing ?: playing,
        mode = fresh.mode ?: mode,
        plicount = fresh.plicount ?: plicount,
        plicurr = fresh.plicurr ?: plicurr,
        slaves = if (fresh.slavesRead) fresh.slaves else slaves,
        slavesRead = slavesRead || fresh.slavesRead,
        answered = answered + fresh.answered,
    )
}
