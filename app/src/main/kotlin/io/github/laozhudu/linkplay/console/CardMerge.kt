package io.github.laozhudu.linkplay.console

import io.github.laozhudu.linkplay.protocol.snapshot.DeviceSnapshot

/**
 * 把 [DeviceSnapshot] 合并进界面卡片 —— **全项目唯一**的写卡片路径。
 *
 * ## 为什么只有这一处
 *
 * 改造前有 4 个地方各自 `cards + (uuid to 新卡片)`：首页 3 秒心跳、设备信息轮询、
 * 组信息刷新、DMR 探测。每个都只填自己知道的字段，其余留 null 整体覆盖 ——
 * 于是「电量刚由 A 写入，被 B 的下一轮清成 null」。用户看到的就是电量时有时无。
 *
 * 现在所有通道的结果先归一成 [DeviceSnapshot]（core-protocol 的 `DeviceSnapshotReader`，
 * 最短路径是 `GetControlDeviceInfo` + `getPlayerStatus` 两次请求），
 * 再由这一个函数合并进卡片。规则只有一条 —— **快照里没有的字段，保留卡片原值**。
 */
internal object CardMerge {

    fun apply(old: DeviceCard, snap: DeviceSnapshot): DeviceCard = old.copy(
        title = snap.title ?: old.title,
        volume = snap.volume ?: old.volume,
        mute = snap.mute ?: old.mute,
        mode = snap.mode ?: old.mode,
        // 声道/电量是"设备报什么就是什么"的字段：报了就覆盖，没报就保留，
        // 绝不用默认值（0 / null）去覆盖已知的真值
        channel = snap.channel ?: old.channel,
        battery = snap.batteryPercent ?: old.battery,
        charging = snap.onBattery?.let { !it } ?: old.charging,
        plicount = snap.plicount ?: old.plicount,
        plicurr = snap.plicurr ?: old.plicurr,
        playing = snap.playing ?: old.playing,
        // 累积通道：诊断时要看"这台设备的哪条取数路径是通的"
        answered = old.answered + snap.answered,
    )
}
