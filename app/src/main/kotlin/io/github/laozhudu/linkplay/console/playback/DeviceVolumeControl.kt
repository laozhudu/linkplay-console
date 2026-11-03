package io.github.laozhudu.linkplay.console.playback

import io.github.laozhudu.linkplay.protocol.DeviceHttpClient
import io.github.laozhudu.linkplay.protocol.LinkPlayCommands
import io.github.laozhudu.linkplay.protocol.upnp.UpnpRendererClient
import java.util.concurrent.ConcurrentHashMap

/**
 * 音量/静音的统一入口：**UPnP RenderingControl 优先，httpapi 兜底**。
 *
 * ## 为什么改成 UPnP 优先
 *
 * 原厂 APK 的音量走的是 UPnP `RenderingControl.SetVolume`（反编译确认它没有
 * `setPlayerCmd:vol` 这条命令），`getMute/getVolume` 同理。
 * 我们之前一律用 httpapi 的 `setPlayerCmd:vol:<n>` —— 能用，但那是自研组合，
 * 而且在设备 DLNA 渲染器与 httpapi 不同步时会出现"界面 60、实际 40"。
 *
 * ## 为什么要缓存 client
 *
 * 每按一次音量键都去拉一遍 `description.xml` 是不能接受的（多一次 ~50ms 往返）。
 * 这里按 IP 缓存 [UpnpRendererClient]，失效（抛异常）时才重建一次。
 */
object DeviceVolumeControl {

    private val clients = ConcurrentHashMap<String, UpnpRendererClient>()
    private val http = DeviceHttpClient()

    private fun clientOf(ip: String): UpnpRendererClient? =
        clients[ip] ?: runCatching {
            UpnpRendererClient.ofDevice(ip).also { clients[ip] = it }
        }.getOrNull()

    /** 设置音量。返回 true 表示走了 UPnP，false 表示退回 httpapi。 */
    fun setVolume(ip: String, volume: Int): Boolean {
        val v = volume.coerceIn(0, 100)
        val client = clientOf(ip)
        if (client != null) {
            val ok = runCatching { client.setVolume(v) }.isSuccess
            if (ok) return true
            // 设备换了地址/服务消失：丢掉缓存，下次重建
            clients.remove(ip)
        }
        runCatching { http.send(ip, LinkPlayCommands.playerVolume(v)) }
        return false
    }

    fun setMute(ip: String, mute: Boolean): Boolean {
        val client = clientOf(ip)
        if (client != null) {
            if (runCatching { client.setMute(mute) }.isSuccess) return true
            clients.remove(ip)
        }
        runCatching { http.send(ip, LinkPlayCommands.playerMute(mute)) }
        return false
    }

    /** 读设备真实音量；UPnP 读不到再问 httpapi。 */
    fun readVolume(ip: String): Int? {
        clientOf(ip)?.let { c ->
            runCatching { c.volume().volume }.getOrNull()?.let { return it }
            clients.remove(ip)
        }
        return runCatching {
            io.github.laozhudu.linkplay.protocol.player.PlayerStatus
                .parse(http.send(ip, LinkPlayCommands.getPlayerStatus())).volume
        }.getOrNull()
    }

    /** 设备换了 IP 时清缓存，避免长期指向旧地址。 */
    fun forget(ip: String) = clients.remove(ip)
}
