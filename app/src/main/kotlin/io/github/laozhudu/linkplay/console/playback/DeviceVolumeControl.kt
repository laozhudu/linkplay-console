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

    /**
     * 客户端工厂。测试注入假传输层用；生产环境走 [UpnpRendererClient.ofDevice]。
     *
     * 做成可替换的不是为了"设计洁癖"：`readVolume` 用哪个 UPnP 动作取数是个**关键决定**
     * （见下面的注释），没有单测锁住，下次有人顺手改回 `volume()` 也没人发现。
     */
    internal var clientFactory: ((String) -> UpnpRendererClient?)? = null

    private fun clientOf(ip: String): UpnpRendererClient? = clients[ip] ?: run {
        val factory = clientFactory ?: { target: String ->
            runCatching { UpnpRendererClient.ofDevice(target) }.getOrNull()
        }
        factory(ip)?.also { clients[ip] = it }
    }

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

    /**
     * 读设备真实音量；UPnP 读不到再问 httpapi。
     *
     * ## 为什么用 `GetControlDeviceInfo` 而不是 `RenderingControl.GetVolume`
     *
     * 两者都能拿到音量，但 `GetControlDeviceInfo` 是**卡片电量的同一个来源**
     * （3112B，一次给全音量+静音+声道+电量+名字+固件）。
     *
     * `docs/65` 批次 2 点名的就是这个：同一个值两个来源迟早打架 ——
     * 界面显示 60（来自控制信息）、音量键调完读回 40（来自 GetVolume），
     * 用户看到的是"音量键不管用"。
     *
     * 代价是请求体大一些（实测 43-54ms vs 18-31ms），换来的是**只有一个真值来源**。
     */
    fun readVolume(ip: String): Int? {
        clientOf(ip)?.let { c ->
            runCatching { c.controlDeviceInfo().volume }.getOrNull()?.let { return it }
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
