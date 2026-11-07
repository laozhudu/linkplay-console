package io.github.laozhudu.linkplay.console.playback.endpoint

import io.github.laozhudu.linkplay.console.playback.PlaybackOutput
import io.github.laozhudu.linkplay.console.playback.PlaybackOutputStore
import io.github.laozhudu.linkplay.console.playback.PlaybackRouter
import io.github.laozhudu.linkplay.console.playback.PlaybackViewModel
import io.github.laozhudu.linkplay.protocol.dlna.DeviceCapabilities

/**
 * **唯一允许判断"走哪一路"的地方**。
 *
 * ## 为什么判断必须只在这里
 *
 * 用户 2026-11-07：
 *
 * > 「通用 upnp 是一个独立模块，doss 体系是另一个模块，肯定是你考虑欠缺导致的」
 *
 * 上一轮的错法是在 `DeviceControl` 里加 `transportFor(ip)`，界面里继续写
 * `if (isLocalOutput)`，`PlaybackDispatcher` 里又有 12 个 `when (output())`
 * —— 同一个判断散在**三个层次、三十多处**，而且互相不知道对方存在。
 *
 * 正确做法：判断收进一个**工厂**（这个对象），生产出统一的 [PlaybackEndpoint]。
 * 界面和 dispatcher 拿到的永远是一个 `PlaybackEndpoint`，它没有能力区分
 * "本机/音箱/盒子" —— 这才是"模块"的含义。
 *
 * 将来加蓝牙音箱：这里多一个分支 + 一个新的 Endpoint 类，**上层一行不用改**。
 */
object Endpoints {

    /**
     * 按**当前播放目标**造出对应的 endpoint。
     *
     * @param ip 当前目标设备地址；目标是本机时给 null
     * @param capabilities 这台设备探测出来的能力位（决定 DOSS 老固件支不支持切歌）
     */
    fun current(
        router: PlaybackRouter,
        vm: () -> PlaybackViewModel?,
        ip: String?,
        label: String,
        capabilities: DeviceCapabilities,
        input: () -> DeviceSnapshotInput,
    ): PlaybackEndpoint {
        val isLocal = PlaybackOutputStore.current.value is PlaybackOutput.Local

        // 目标是本机，或者根本没有具体设备 → 本机端点
        if (isLocal || ip.isNullOrEmpty()) {
            return LocalEndpoint(router)
        }

        return if (capabilities.group == io.github.laozhudu.linkplay.protocol.dlna.DeviceGroup.OtherPlayer) {
            UpnpEndpoint(vm, ip, input, label)
        } else {
            LinkPlayEndpoint(vm, ip, input, label) {
                EndpointCapabilities(
                    canNext = capabilities.supportsNextPrev,
                    canPrevious = capabilities.supportsNextPrev,
                    canSeek = capabilities.supportsSeek,
                    canMute = capabilities.supportsMute,
                    hasQueue = true,
                )
            }
        }
    }
}
