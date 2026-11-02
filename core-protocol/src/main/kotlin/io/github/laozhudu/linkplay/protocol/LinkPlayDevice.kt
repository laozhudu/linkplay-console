package io.github.laozhudu.linkplay.protocol

import io.github.laozhudu.linkplay.protocol.player.FallbackPlaybackEngine
import io.github.laozhudu.linkplay.protocol.player.HttpApiPlaybackEngine
import io.github.laozhudu.linkplay.protocol.player.PlaybackEngine
import io.github.laozhudu.linkplay.protocol.player.UpnpPlaybackEngine
import io.github.laozhudu.linkplay.protocol.playqueue.PlayQueueClient
import io.github.laozhudu.linkplay.protocol.upnp.DeviceDescription
import io.github.laozhudu.linkplay.protocol.upnp.HttpUpnpTransport
import io.github.laozhudu.linkplay.protocol.upnp.UpnpRendererClient
import io.github.laozhudu.linkplay.protocol.upnp.UpnpServices
import io.github.laozhudu.linkplay.protocol.upnp.UpnpTransport

/**
 * 一台设备支持哪些控制通道。
 *
 * 对接开源项目时这是**第一个要问的问题**：2015 年的白标固件与 2022 年的 WiiM 固件
 * 能力差很多，不能假设。所有字段都由实际探测得到，不做推测。
 */
data class LinkPlayCapabilities(
    /** httpapi `getStatusEx` 可用（几乎所有固件都有）。 */
    val httpApi: Boolean,
    /** 声明了 AVTransport，可以用标准 UPnP 播放控制。 */
    val upnpPlayback: Boolean,
    /** 声明了私有 PlayQueue，可以整列表推送。 */
    val playQueue: Boolean,
    /** httpapi 的 `getPlayerStatus` 可用（老固件通常没有）。 */
    val httpPlayer: Boolean,
) {
    /** 能否整列表推送（原厂 App 的核心能力）。 */
    val canPushQueue: Boolean get() = playQueue

    /** 能否播放/暂停/调音量。 */
    val canControlPlayback: Boolean get() = upnpPlayback || httpPlayer

    /** 给日志/UI 用的一行描述。 */
    fun summary(): String = buildString {
        append("httpapi=").append(if (httpApi) "✓" else "✗")
        append(" upnp=").append(if (upnpPlayback) "✓" else "✗")
        append(" playqueue=").append(if (playQueue) "✓" else "✗")
        append(" httpPlayer=").append(if (httpPlayer) "✓" else "✗")
    }

    companion object {
        val NONE = LinkPlayCapabilities(false, false, false, false)
    }
}

/**
 * 面向外部对接的统一门面（这是给开源项目用的入口，App 内部也走它）。
 *
 * 为什么需要门面：底层有 4 套通道（httpapi / UPnP AVTransport / UPnP RenderingControl /
 * 私有 PlayQueue），每套的可用性随固件版本漂移。把「探测 → 选通道 → 执行」收在这里，
 * 外部只需要 `LinkPlayDevice.open(...)` 然后问 `capabilities`。
 *
 * 使用约束：
 * - **所有方法都是阻塞 IO**，调用方自行放到后台线程（App 用 Dispatchers.IO）；
 * - 构造不联网，`open()` 会做一次 `getStatusEx` 探活；
 * - `location` 来自 SSDP 的 LOCATION；手动输入 IP 时没有它，此时 UPnP 通道不可用，
 *   只有 httpapi 可用——这个降级是设计的一部分，不是缺陷。
 */
class LinkPlayDevice private constructor(
    val ip: String,
    val location: String?,
    private val httpClient: DeviceHttpClient,
    private val upnpTransport: UpnpTransport,
) {
    @Volatile
    private var description: DeviceDescription? = null

    /** 最近一次探测结果；调用 [detectCapabilities] 后才有效。 */
    @Volatile
    var capabilities: LinkPlayCapabilities = LinkPlayCapabilities.NONE
        private set

    // ---- 探测 ----

    /** 拉取并缓存 UPnP 描述；没有 LOCATION 或拉取失败返回 null。 */
    fun description(): DeviceDescription? {
        description?.let { return it }
        val loc = location ?: return null
        return runCatching { UpnpRendererClient.describe(loc, upnpTransport) }
            .onSuccess { description = it }
            .getOrNull()
    }

    /**
     * 逐项探测能力。**顺序有讲究**：先 httpapi（最通用），拿到描述后再看两个 UPnP 服务，
     * 最后才探 httpPlayer（老固件会超时，放最后避免拖慢整体探测）。
     */
    fun detectCapabilities(probeHttpPlayer: Boolean = true): LinkPlayCapabilities {
        val httpOk = runCatching { httpClient.status(ip) }.isSuccess
        val desc = description()
        val upnpPlayback = desc?.avTransportControlUrl != null
        val playQueue = desc?.controlUrl(UpnpServices.WIIMU_PLAY_QUEUE) != null
        val httpPlayer = probeHttpPlayer &&
            runCatching { HttpApiPlaybackEngine(httpClient, ip).probe() }.getOrDefault(false)
        return LinkPlayCapabilities(httpOk, upnpPlayback, playQueue, httpPlayer).also { capabilities = it }
    }

    // ---- 设备信息与设置 ----

    fun status(): LinkPlayStatus = httpClient.status(ip)

    fun slaveList() = httpClient.slaveList(ip)

    fun rename(name: String): String = httpClient.send(ip, LinkPlayCommands.deviceName(name))

    fun setLanguage(language: String): String = httpClient.send(ip, LinkPlayCommands.language(language))

    fun switchInput(mode: String): String = httpClient.send(ip, LinkPlayCommands.switchMode(mode))

    fun reboot(): String = httpClient.send(ip, LinkPlayCommands.reboot())

    fun rawCommand(command: String): String = httpClient.send(ip, command)

    /**
     * 按下预设键。官方文档给的范围是 1..12；这批老固件的面板只有 6 个（`vpreset`~`vpreset6`）。
     * 这里不限制上限，交给设备判断。
     */
    fun pressPreset(index: Int): String {
        require(index >= 1) { "预设键从 1 开始" }
        return httpClient.send(ip, LinkPlayCommands.keyClick(index))
    }

    // ---- 播放 ----

    /** 取一个可用的播放通道：UPnP 优先，其次 httpapi。都不可用返回 null。 */
    /**
     * 取一个播放通道。
     *
     * **两条都可用时返回 [FallbackPlaybackEngine]**：UPnP 优先（字段规范、能拿 DIDL 元数据），
     * 它掉线就自动降到 httpapi 的 80 端口，不再需要重启音箱（docs/14 §0.1 实测）。
     * 只有一条可用时直接用那一条，不必包一层。
     */
    fun playback(): PlaybackEngine? {
        val upnp = description()?.takeIf { it.avTransportControlUrl != null }
            ?.let { UpnpPlaybackEngine(UpnpRendererClient(it, upnpTransport)) }
        val http = HttpApiPlaybackEngine(httpClient, ip).takeIf {
            runCatching { it.probe() }.getOrDefault(false)
        }
        return when {
            upnp != null && http != null -> FallbackPlaybackEngine(upnp, http)
            upnp != null -> upnp
            else -> http
        }
    }

    // ---- 队列（整列表推送） ----

    /** 没有 PlayQueue 服务时返回 null。 */
    fun queue(): PlayQueueClient? {
        val desc = description() ?: return null
        val client = PlayQueueClient(desc, upnpTransport)
        return client.takeIf { it.isSupported }
    }

    override fun toString(): String = "LinkPlayDevice($ip, location=$location)"

    companion object {
        /**
         * 连接一台设备并探活。`location` 可以传 null（手动输入 IP 的场景）。
         *
         * @throws java.io.IOException 设备不可达
         */
        fun open(
            ip: String,
            location: String? = null,
            httpClient: DeviceHttpClient = DeviceHttpClient(),
            upnpTransport: UpnpTransport = HttpUpnpTransport(),
        ): LinkPlayDevice {
            val device = LinkPlayDevice(ip, location, httpClient, upnpTransport)
            device.httpClient.status(ip) // 探活：不可达直接抛
            return device
        }

        /** 只探测不抛异常，方便批量扫描时逐台判断。 */
        fun probe(
            ip: String,
            location: String? = null,
            httpClient: DeviceHttpClient = DeviceHttpClient(),
            upnpTransport: UpnpTransport = HttpUpnpTransport(),
        ): LinkPlayCapabilities? = runCatching {
            val device = LinkPlayDevice(ip, location, httpClient, upnpTransport)
            device.httpClient.status(ip)
            device.detectCapabilities(probeHttpPlayer = false)
        }.getOrNull()
    }
}
