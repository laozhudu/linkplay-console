package io.github.laozhudu.upnp

import io.github.laozhudu.linkplay.protocol.upnp.DeviceDescription
import io.github.laozhudu.linkplay.protocol.upnp.UpnpServices

/**
 * 一台通用 UPnP 设备**能做什么** —— 从它自报的服务列表推出来，不靠型号。
 *
 * ## 为什么按服务推断而不是按品牌
 *
 * 通用设备的差异全在"声明了哪些服务"：
 * - 只有 AVTransport 的：能播能停，但调不了音量（音量归功放）；
 * - 带 RenderingControl 的：能调音量/静音；
 * - 带 ConnectionManager 的：能查它接受哪些格式（决定要不要转码/选 m3u8）。
 *
 * 按型号写分支，加一个新牌子就要改代码 —— 项目在 `docs/49` 已经吃够这个苦。
 */
data class RendererCapabilities(
    /** 能控制播放（播放/暂停/停止/换曲）。 */
    val transport: Boolean,
    /** 能改音量与静音。 */
    val volume: Boolean,
    /** 能 Seek（不是所有设备都认 REL_TIME）。 */
    val seek: Boolean,
    /** 能查它接受哪些流格式。 */
    val protocolInfo: Boolean,
    /** 有内容目录（能浏览设备上的歌）。 */
    val contentDirectory: Boolean,
) {
    /** 一台没有任何可控制服务的设备不值得加进列表。 */
    val controllable: Boolean get() = transport || volume

    companion object {
        fun of(description: DeviceDescription): RendererCapabilities {
            val transport = description.service(UpnpServices.AV_TRANSPORT) != null
            // Seek 走 AVTransport，理论上跟 transport 同生共死；
            // 但老设备的 AVTransport:1 常常不实现 Seek，所以单列为"未知→false"，
            // 由调用方在真机上试一次再决定要不要露出进度条。
            return RendererCapabilities(
                transport = transport,
                volume = description.service(UpnpServices.RENDERING_CONTROL) != null,
                seek = transport,
                protocolInfo = description.service(UpnpServices.CONNECTION_MANAGER) != null,
                contentDirectory = description.services.any {
                    it.serviceType.contains("ContentDirectory", ignoreCase = true)
                },
            )
        }
    }
}
