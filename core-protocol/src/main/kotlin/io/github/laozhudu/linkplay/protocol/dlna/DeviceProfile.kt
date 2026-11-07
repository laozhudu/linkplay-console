package io.github.laozhudu.linkplay.protocol.dlna

import io.github.laozhudu.linkplay.protocol.upnp.DeviceDescription
import io.github.laozhudu.linkplay.protocol.upnp.UpnpServices

/**
 * 设备能力档：这台设备是「LinkPlay 系音箱」还是「通用 DLNA 渲染器」。
 *
 * ## 为什么必须有这个概念
 *
 * 项目原来假设对面一定是 LinkPlay 系（DOSS/WiiMu）—— 私有 PlayQueue、httpapi、
 * 多房间、六键预设一应俱全。但家里还有电视盒子这类**只会标准 UPnP** 的设备
 * （实测 `192.168.99.175:3500`，SoftMedia AirReceiver，DMR-1.50）：
 * 它认 AVTransport / RenderingControl，却**没有** PlayQueue 服务、也没有 `httpapi.asp`。
 *
 * 于是同一条「把整列表交给设备」的路径在它身上必然失败两层（PlayQueue → m3u），
 * 最后才掉到客户端逐首投流；界面上的组网/声道/屏蔽/六键按钮更是纯装饰品 ——
 * 按了没反应，这在本项目里是明确禁止的（铁律：不给按了没反应的按钮）。
 *
 * 所以先判档，再按档收敛行为：**能力从描述里读，不靠型号猜**。
 */
enum class DeviceProfile {
    /** LinkPlay/WiiMu 系：私有服务 + httpapi 齐全，功能全开。 */
    LINKPLAY,

    /** 通用 DLNA 渲染器：只有标准 AVTransport（可选 RenderingControl）。 */
    GENERIC_DMR,

    /** 既没有私有服务也没有 AVTransport（例如只响应 SSDP 的路由器/打印机）。 */
    UNKNOWN,
    ;

    /** 是否是 LinkPlay 系（老路径）。 */
    val isLinkPlay: Boolean get() = this == LINKPLAY

    /** 是否是通用 DLNA 渲染器（新支持的一类）。 */
    val isGenericDmr: Boolean get() = this == GENERIC_DMR
}

/**
 * 一台设备**实际具备**的能力，界面据此决定显示哪些入口。
 *
 * 全部字段都是「能不能真的做到」，不是「理论上标准里有」：
 * 例如 `supportsNextPrev` 对通用渲染器为 false —— AVTransport.Next 在设备没有
 * 自己的队列时只会回 701（transition not available），按了就是没反应。
 */
data class DeviceCapabilities(
    /** 私有 PlayQueue：能把整份列表交给设备，App 退出照播。 */
    val supportsPlayQueue: Boolean = false,
    /** 多房间组网（`multiroom:*`）。 */
    val supportsMultiroom: Boolean = false,
    /** 机身六键预设（`keyClick`）。 */
    val supportsPresets: Boolean = false,
    /** 私有 httpapi（`getStatusEx` / `getPlayerStatus` / `setPlayerCmd:*`）。 */
    val supportsHttpapi: Boolean = false,
    /** 音量（UPnP RenderingControl 或 httpapi）。 */
    val supportsVolume: Boolean = false,
    /** 拖动进度条跳转（AVTransport.Seek）。 */
    val supportsSeek: Boolean = false,
    /** 下一首/上一首（依赖设备自己握着队列）。 */
    val supportsNextPrev: Boolean = false,
    /** 静音（RenderingControl.SetMute）。 */
    val supportsMute: Boolean = false,
    /**
     * 声道切换（0 立体声 / 1 仅左 / 2 仅右）。
     *
     * LinkPlay 走 `RenderingControl.SetChannel`（真机实测：不依赖组网，独立设备也能切）；
     * 通用渲染器没有这个动作 → false，界面据此**不渲染声道那一格**，
     * 而不是画一个按了没反应的 LR。
     */
    val supportsChannel: Boolean = false,
) {

    // ---------- 两个「根位」：回答"它是什么"，而不是"它支持哪个动作" ----------
    //
    // 上面那些字段是"能做哪件事"，这两个是**分类判据**。
    // 界面据此分组（docs/92 U2：DOSS 与通用 UPnP 混排的问题）。
    //
    // 为什么不直接写死一张"品牌 → 分组"的表：型号标签会随固件变
    // （`.153` 旧固件 3.6.5310 与 `.164` 新固件 3.6.6623 已实测出字段差异），
    // 而能力是探测出来的、可以随时修正。

    /** 能播出声音（能投流过去）。 */
    val canPlayAudio: Boolean
        get() = supportsPlayQueue || supportsVolume || supportsSeek || supportsNextPrev || supportsHttpapi

    /** 能被遥控（至少有一样控制手段，不是个只能看着的摆设）。 */
    val canBeRemoted: Boolean
        get() = supportsVolume || supportsMute || supportsSeek || supportsNextPrev || supportsPresets

    /**
     * 首页该把它归到哪一组。
     *
     * 三档而不是按品牌分，是因为**同品牌的设备能力也可能不同**，
     * 而异品牌也可能落在同一档。
     */
    val group: DeviceGroup
        get() = when {
            // 有私有队列 + 能组网 = 我的音箱（DOSS 这一档：能收整份列表、App 退出照播）
            supportsPlayQueue && supportsMultiroom -> DeviceGroup.MySpeaker
            // 能放声音但没有队列/组网 = 其它播放设备（电视盒子这类）
            canPlayAudio -> DeviceGroup.OtherPlayer
            // 连声音都放不了 = 其它（可能是传感器、灯，或者只是没探全）
            else -> DeviceGroup.Other
        }

    companion object {
        /** LinkPlay 系：实测全能力（含私有 PlayQueue 与 httpapi）。 */
        val LINKPLAY = DeviceCapabilities(
            supportsPlayQueue = true,
            supportsMultiroom = true,
            supportsPresets = true,
            supportsHttpapi = true,
            supportsVolume = true,
            supportsSeek = true,
            supportsNextPrev = true,
            supportsMute = true,
            supportsChannel = true,
        )

        /** 什么都没有（不可控设备）。 */
        val NONE = DeviceCapabilities()

        /**
         * 通用渲染器的能力：只有标准 UPnP 那几样。
         *
         * - PlayQueue / 多房间 / 预设 / httpapi 一律 false：这些是厂商私有，DMR 不可能有；
         * - 音量与静音取决于是否声明了 RenderingControl（有的渲染器只有 AVTransport）；
         * - Seek 按标准能力给 true（AVTransport 的必需动作之一是 Seek，实测 AirReceiver 支持）；
         * - Next/Previous 给 false：设备没有队列，切歌只能靠我们重投下一首。
         */
        fun genericDmr(hasRenderingControl: Boolean) = DeviceCapabilities(
            supportsPlayQueue = false,
            supportsMultiroom = false,
            supportsPresets = false,
            supportsHttpapi = false,
            supportsVolume = hasRenderingControl,
            supportsSeek = true,
            // **设备自己不会切** —— 实测那台 AirReceiver 对 AVTransport.Next 回
            // 701 transition not available（它没有自己的队列，不知道切到哪）。
            //
            // ⚠️ 但这**不等于"用户不能在我们这儿切歌"**：
            // 我们从自己的列表里重投下一首（`PlaybackViewModel.nextByReplay`），
            // 效果一样。所以界面的切歌按钮是**有**的，判断依据是
            // `EndpointCapabilities.canNext`，不是这一位。
            //
            // 两句话分开记：
            //   supportsNextPrev = 它自己会不会换
            //   canNext          = 我们能不能让它换
            supportsNextPrev = false,
            supportsMute = hasRenderingControl,
            // SetChannel 虽是标准 RenderingControl 的可选动作，实测 AirReceiver 不认；
            // 按"能不能真的做到"给 false，不给按了没反应的按钮
            supportsChannel = false,
        )
    }
}

/**
 * 判定一台设备属于哪一档，并给出它的能力表。
 *
 * @param httpapiReachable `getStatusEx` 是否答得上来。**null 表示没探**（只知道描述）；
 *   只有 `true` 才足以升格为 LinkPlay —— 这套 httpapi 是 LinkPlay 独有的，
 *   能答就是铁证（真机白标固件描述里可能一个品牌字都没有）。
 *
 * 判据顺序（从硬到软）：
 * 1. 描述里声明了 `urn:schemas-wiimu-com:service:PlayQueue:1`，或 httpapi 可达 → LINKPLAY；
 * 2. 有 AVTransport 控制地址 → GENERIC_DMR（哪怕还带了 ConnectionManager / 厂商扩展节点）；
 * 3. 连 AVTransport 都没有 → UNKNOWN。
 *
 * 注意：**不能**用「描述里有 QPlay 字样」判 LinkPlay。实测电视盒子的描述里就有
 * `<qq:X_QPlay_SoftwareCapability>QPlay:1</qq:X_QPlay_SoftwareCapability>`，
 * 但那只是一个能力声明节点，既不是 service 也没有 controlURL，按它判会把这台盒子
 * 误判成音箱，然后一层层发私有命令过去、全部失败。
 */
fun detect(
    description: DeviceDescription,
    httpapiReachable: Boolean? = null,
): Pair<DeviceProfile, DeviceCapabilities> {
    val hasPlayQueue = description.services.any {
        it.serviceType.equals(UpnpServices.WIIMU_PLAY_QUEUE, ignoreCase = true)
    }
    if (hasPlayQueue || httpapiReachable == true) {
        return DeviceProfile.LINKPLAY to DeviceCapabilities.LINKPLAY
    }
    val hasAvTransport = description.avTransportControlUrl != null
    if (hasAvTransport) {
        return DeviceProfile.GENERIC_DMR to
            DeviceCapabilities.genericDmr(hasRenderingControl = description.renderingControlUrl != null)
    }
    return DeviceProfile.UNKNOWN to DeviceCapabilities.NONE
}

/**
 * 手动填 IP 时该按哪些地址去试「设备描述」。
 *
 * 为什么需要多个：LinkPlay 系固定在 `:49152/description.xml`，而通用渲染器习惯把描述
 * 放在根路径（实测 AirReceiver：`http://192.168.99.175:3500/` 直接返回 description，
 * `/description.xml` 反而 404）。两个都试一遍是最省事、也不猜端口之外东西的做法。
 */
object DescriptionEndpoints {
    fun candidates(ip: String, port: Int): List<String> = listOf(
        "http://$ip:$port/description.xml",
        "http://$ip:$port/",
    )
}

/**
 * 首页的设备分组。
 *
 * 用户原话（`docs/92` U2）：「通用 upnp 设备根本就没考虑过与 doss 隔离，反而混在一起」。
 *
 * 按**能力**分，不按品牌分 —— 新加一台设备只要探测到能力，自动落到正确的组，
 * 代码一行不用改。
 */
enum class DeviceGroup {
    /** 我的音箱：有设备侧队列、能组网（DOSS 这一档）。 */
    MySpeaker,

    /** 其它播放设备：能放声音，但没有队列也没有组网（电视盒子/电视自带 DLNA）。 */
    OtherPlayer,

    /** 其它：不可控设备，或还没探全。 */
    Other,
}
