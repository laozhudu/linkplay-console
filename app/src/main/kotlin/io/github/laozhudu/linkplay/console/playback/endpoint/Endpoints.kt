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

        /*
         * **白名单，不是反选**（用户 2026-11-07 纠正）。
         *
         * 用户的原话：「doss 系列是适配通用 upnp 的，但通用 upnp 不一定是 doss，
         * doss 有特殊标识的」。
         *
         * 上一版写的是 `group == OtherPlayer → UPnP，否则 → DOSS`。这是**反选**，
         * 它默认了"世界上只有 DOSS 和那台电视盒子两种设备"。可是 `group` 有三档：
         *
         * | group | 什么时候 |
         * |---|---|
         * | `MySpeaker` | 有私有 PlayQueue **且**能组网 —— 这才是 DOSS |
         * | `OtherPlayer` | 有 AVTransport 的普通渲染器 |
         * | `Other` | **还没探全 / 不可控** ← 反选会把这一类塞进 DOSS 分支 |
         *
         * 于是"刚扫到、还没探完"或"探测超时"的设备会被当成 DOSS，
         * 然后一路发 LinkPlay 私有命令 —— 全部静默失败。
         *
         * ## 为什么判不出来时要偏向通用通道
         *
         * **DOSS 本身就是 UPnP 设备**（有 AVTransport + RenderingControl）。
         * 所以：
         *
         * - 用通用 SOAP 控 DOSS → **能工作**（只是少了组网/队列/预设这些私有能力）
         * - 用 LinkPlay 私有命令控通用设备 → **静默失败**（连报错都没有）
         *
         * 两个方向错一个能忍、一个不能忍。所以只有**确认**是 `MySpeaker` 才走私有通道。
         */
        return if (capabilities.group == io.github.laozhudu.linkplay.protocol.dlna.DeviceGroup.MySpeaker) {
            LinkPlayEndpoint(vm, ip, input, label) {
                EndpointCapabilitiesCatalog.linkPlay(capabilities)
            }
        } else {
            UpnpEndpoint(vm, ip, input, label)
        }
    }
}
