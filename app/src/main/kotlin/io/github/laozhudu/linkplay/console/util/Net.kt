package io.github.laozhudu.linkplay.console.util

import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * 本机在局域网里的 IPv4 地址。
 *
 * 为什么单独放 util：之前它写在 `library/MediaServerService.kt` 里（`LocalMediaRegistry`），
 * 结果 `playback/DeviceQueuePush` 要用它就得反向依赖 `library` 包 —— 分层反了。
 * 播放列表服务（playback）和本地媒体服务（library）都只是"某个 HTTP 服务的宿主机地址"，
 * 这属于基础能力，放 util 才对。
 */
object Net {
    fun localIpv4(): String? = try {
        NetworkInterface.getNetworkInterfaces().toList()
            .asSequence()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList().asSequence() }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { !it.isLoopbackAddress }
            ?.hostAddress
    } catch (_: Exception) {
        null
    }
}
