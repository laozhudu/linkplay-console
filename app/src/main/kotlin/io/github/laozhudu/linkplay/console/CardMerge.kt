package io.github.laozhudu.linkplay.console

import io.github.laozhudu.linkplay.protocol.player.PlayState
import io.github.laozhudu.linkplay.protocol.player.PlayerStatus
import io.github.laozhudu.linkplay.protocol.upnp.ControlDeviceInfo

/**
 * 设备卡片的**合并**规则（纯函数，可单测）。
 *
 * ## 为什么必须合并而不是替换
 *
 * 首页卡片的信息来自两条完全不同的通道：
 * - `getPlayerStatus`（httpapi）→ 曲名 / 音量 / 播放状态 / 列表长度；
 * - `GetControlDeviceInfo`（UPnP，Wiimu 扩展）→ 声道 / 电量 / 静音。
 *
 * 之前两处都是 `cards + (uuid to 全新DeviceCard)`，谁后跑谁把对方写的字段清成默认值：
 * 每 3 秒的播放轮询一跑，电量就消失、声道标签回到"立体声"。
 * 用户看到的就是「电量有时有有时没有」「声道切换后显示不变」。
 *
 * 规则只有一条：**设备没报的字段，保留上一轮的值**；报了才覆盖。
 */
internal object CardMerge {

    /** 播放态轮询（`getPlayerStatus`）：只覆盖它真正提供的字段。 */
    fun applyPlayerStatus(old: DeviceCard, fresh: PlayerStatus): DeviceCard = old.copy(
        // 设备不回填元数据时 Title 是 "Unknown" 或空，这时保留控制端自己记的曲名
        title = fresh.title?.takeIf { it.isNotBlank() && it != "Unknown" } ?: old.title,
        volume = fresh.volume,
        mute = fresh.mute,
        mode = fresh.mode,
        channel = fresh.channel ?: old.channel,
        plicount = fresh.playListCount ?: 0,
        plicurr = fresh.playListCurrent ?: 0,
        playing = fresh.playState == PlayState.PLAY,
    )

    /** 设备信息（`GetControlDeviceInfo`）：声道/音量/静音/电量/充电态。 */
    fun applyDeviceInfo(old: DeviceCard, info: ControlDeviceInfo, battery: Int?): DeviceCard {
        val pct = battery ?: info.status?.batteryPercent ?: old.battery
        return old.copy(
            volume = info.volume ?: old.volume,
            mute = info.mute,
            channel = info.channel ?: old.channel,
            battery = pct,
            charging = info.status?.onBattery?.let { !it } ?: old.charging,
        )
    }
}
