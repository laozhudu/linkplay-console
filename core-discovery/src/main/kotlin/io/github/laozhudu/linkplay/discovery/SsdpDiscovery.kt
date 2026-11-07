package io.github.laozhudu.linkplay.discovery

import io.github.laozhudu.linkplay.protocol.ssdp.SsdpMessages
import io.github.laozhudu.linkplay.protocol.ssdp.SsdpResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import java.util.UUID

/**
 * LinkPlay 设备发现（SSDP M-SEARCH）。
 *
 * 依据：原厂 App 通过 Cling 做 SSDP，组播地址 239.255.255.250:1900；
 * 设备侧还声明了厂商服务 `urn:schemas-wiimu-com:service:PlayQueue:1`，
 * 并且 DEX 里存在 `ssdp:wiimudevice` 目标串，因此这里同时搜索两者，
 * 并以 `ssdp:all` 兜底（部分老固件只回 all）。
 *
 * 注意：Android 需持有 WifiManager.MulticastLock 才能稳定收到组播回包，
 * 该锁必须由调用方（App 层）加，库层不持有 Context，保持纯逻辑。
 */
data class DiscoveredDevice(
    val ip: String,
    val location: String?,
    val usn: String?,
    val server: String?,
    val searchTarget: String?,
    val friendlySsid: String? = null,
    /** 设备 uuid，用于「IP 变了也能认回来」。 */
    val uuid: String? = null,
    /** 来自设备描述的 friendlyName（SSDP 头里没有，要拉描述才知道）。 */
    val friendlyName: String? = null,
    /**
     * 从设备描述判出来的能力档（见 [io.github.laozhudu.linkplay.protocol.dlna.DeviceProfile]）。
     *
     * 为什么放在发现结果里：首页「添加设备」列表要据此把 LinkPlay 标成「音箱」、
     * 把其它 MediaRenderer 标成「渲染器」，而判档需要拉一次描述 —— 那一步已经在这里做了，
     * 不带上就等于让界面再拉一遍。
     */
    val profile: io.github.laozhudu.linkplay.protocol.dlna.DeviceProfile? = null,
)

class SsdpDiscovery(
    private val timeoutMs: Int = 3_000,
    private val maxDevices: Int = 64,
) {
    /** 发现 DLNA 媒体服务器（NAS）。用与设备相同的扫描逻辑，只是把 ST 换成 MediaServer。 */
    suspend fun discoverMediaServers(): List<DiscoveredDevice> =
        discoverWithTargets(listOf(SsdpMessages.ST_MEDIA_SERVER, "ssdp:all"))

    /** 单播找媒体服务器：已知 NAS IP 时用（组播被隔离/VPN 场景）。 */
    suspend fun discoverMediaServersUnicast(hosts: List<String>, timeoutMs: Int = 1_500): List<DiscoveredDevice> =
        discoverUnicast(hosts, timeoutMs, SsdpMessages.ST_MEDIA_SERVER)

    suspend fun discover(): List<DiscoveredDevice> = discoverWithTargets(SEARCH_TARGETS)

    /**
     * 只发现 LinkPlay 系（DOSS/WiiMu/Arylic 这一族）播放设备。
     *
     * 为什么单独一个方法而不是在调用处过滤：判据要**先拉描述**才能确定（SSDP 头里
     * 真机的 SERVER 是 `Linux/3.x UPnP/1.0`，没有任何品牌字样，见 LinkPlayIdentity 的测试），
     * 而拉描述是网络请求 —— 放在 UI 层做会让每个页面各写一份、且容易漏。
     *
     * 返回的 [DiscoveredDevice.friendlyName] 已填好设备名，可直接用于首页展示。
     */
    suspend fun discoverLinkPlayDevices(): List<DiscoveredDevice> = withContext(Dispatchers.IO) {
        discover()
            .filter { it.location != null }
            .mapNotNull { device ->
                val location = device.location ?: return@mapNotNull null
                val desc = runCatching {
                    io.github.laozhudu.linkplay.protocol.upnp.UpnpRendererClient.describe(location)
                }.getOrNull() ?: return@mapNotNull null

                if (!io.github.laozhudu.linkplay.protocol.identity.LinkPlayIdentity.isLinkPlayFamily(desc)) {
                    return@mapNotNull null
                }
                device.copy(
                    friendlySsid = desc.friendlyName,
                    uuid = device.uuid ?: desc.udn,
                    friendlyName = desc.friendlyName,
                )
            }
            .distinctBy { it.uuid ?: it.ip }
    }

    /**
     * 发现局域网里**所有** UPnP 媒体渲染器（不限品牌），并判出能力档。
     *
     * 为什么不再只找 LinkPlay：家里还有电视盒子这类只标准 UPnP 的设备（实测
     * `192.168.99.175:3500`，SoftMedia AirReceiver，DMR-1.50）。它们一样能投流，
     * 只是没有私有 PlayQueue / httpapi，所以界面上要按 [DiscoveredDevice.profile] 区分标签。
     *
     * 判据是**拉描述**而不是 SSDP 头：真机 SERVER 只有 `Linux/3.x UPnP/1.0`，看不出任何品牌。
     * 连 AVTransport 都没有的（路由器 / NAS / 打印机）在这里就被丢掉，不进列表。
     */
    suspend fun discoverMediaRenderers(): List<DiscoveredDevice> = withContext(Dispatchers.IO) {
        discoverWithTargets(listOf(SsdpMessages.ST_MEDIA_RENDERER, SsdpMessages.ST_WIIMU_PLAY_QUEUE, "ssdp:all"))
            .mapNotNull { device ->
                val location = device.location
                if (location.isNullOrBlank()) return@mapNotNull null
                val desc = runCatching {
                    io.github.laozhudu.linkplay.protocol.upnp.UpnpRendererClient.describe(location)
                }.getOrNull()
                /*
                 * **拉不到描述也要把设备列出来**。
                 *
                 * 原来这里 `?: return@mapNotNull null` —— 一次 description 超时就等于
                 * "这台设备不存在"。而局域网里 DMR 的描述拉取失败是常见现象
                 * （控制端口没开、固件慢、防火墙），设备本身照样能投流。
                 *
                 * 用户看到的是"通用 upnp 怎么没有扫出来"，而真相是**扫到了又被自己扔了**。
                 *
                 * 现在：拉不到就 `profile = null`（界面只显示 IP，不猜它是什么），
                 * 添加之后由探活来定档。
                 */
                if (desc == null) return@mapNotNull device.copy(profile = null)

                val (profile, _) = io.github.laozhudu.linkplay.protocol.dlna.detect(desc, httpapiReachable = null)
                // 判 UNKNOWN（连 AVTransport 都没有）也保留：它可能是个媒体服务器之类的误报，
                // 但让用户看见总比静默消失好。`profile = null` 表示"扫描没能定性"。
                device.copy(
                    friendlySsid = desc.friendlyName,
                    uuid = device.uuid ?: desc.udn,
                    friendlyName = desc.friendlyName,
                    profile = profile.takeIf { it != io.github.laozhudu.linkplay.protocol.dlna.DeviceProfile.UNKNOWN },
                )
            }
            .distinctBy { (it.uuid ?: it.ip).lowercase() }
    }

    /**
     * 同一台设备的多次应答怎么合并。
     *
     * 单独抽出来是为了能单测 —— 这段逻辑原来内联在收包循环里，
     * 出错时表现是"扫描结果里少了几台"，从现象完全看不出是这个函数的问题。
     *
     * 规则：**带 LOCATION 的那次说了算**。没带 LOCATION 的应答只用来补空缺字段，
     * 不允许把已有的 LOCATION 抹掉（抹掉就等于这台设备从结果里消失了）。
     */
    internal fun mergeAnswer(
        previous: DiscoveredDevice?,
        ip: String,
        location: String?,
        usn: String?,
        server: String?,
        searchTarget: String?,
    ): DiscoveredDevice {
        if (previous != null && location.isNullOrBlank()) {
            return previous.copy(
                searchTarget = previous.searchTarget ?: searchTarget,
                uuid = previous.uuid ?: SsdpMessages.uuidOf(usn),
                usn = previous.usn ?: usn,
                server = previous.server ?: server,
            )
        }
        return DiscoveredDevice(
            ip = ip,
            location = location ?: previous?.location,
            usn = usn ?: previous?.usn,
            server = server ?: previous?.server,
            searchTarget = searchTarget ?: previous?.searchTarget,
            uuid = SsdpMessages.uuidOf(usn) ?: previous?.uuid,
        )
    }

    private suspend fun discoverWithTargets(targets: List<String>): List<DiscoveredDevice> = withContext(Dispatchers.IO) {
        val found = LinkedHashMap<String, DiscoveredDevice>()
        val address = InetAddress.getByName(MULTICAST_ADDRESS)
        DatagramSocket().use { socket ->
            socket.reuseAddress = true
            socket.soTimeout = 500
            socket.broadcast = true

            targets.forEach { st ->
                val message = buildMSearch(st)
                val packet = DatagramPacket(
                    message.toByteArray(Charsets.UTF_8),
                    message.length,
                    InetSocketAddress(address, SSDP_PORT),
                )
                runCatching { socket.send(packet) }
            }

            val deadline = System.currentTimeMillis() + timeoutMs
            val buffer = ByteArray(4096)
            while (System.currentTimeMillis() < deadline && found.size < maxDevices) {
                val packet = DatagramPacket(buffer, buffer.size)
                try {
                    socket.receive(packet)
                } catch (_: SocketTimeoutException) {
                    continue
                }
                val text = String(packet.data, 0, packet.length, Charsets.UTF_8)
                val headers = parseHeaders(text)
                val ip = packet.address?.hostAddress ?: continue
                val st = headers["st"]
                val location = headers["location"]
                if (st == null && !text.contains("HTTP/1.1 200", ignoreCase = true)) continue

                /*
                 * **一台设备会答好几次**：`ssdp:all` 会让它把 rootdevice、设备类型、
                 * 每个服务类型各答一遍，而这几次里**有的带 LOCATION、有的不带**。
                 *
                 * 原来直接 `found[ip] = 新应答`。如果最后来的一条恰好不带 LOCATION，
                 * 这台设备就被自己的应答"覆盖没了"，后面 `filter { location != null }`
                 * 一过就消失 —— 表现是扫描结果里少了几台，而且完全看不出原因。
                 *
                 * 规则：**带 LOCATION 的那次说了算**，其余只用来补空缺字段。
                 */
                found[ip] = mergeAnswer(
                    previous = found[ip],
                    ip = ip,
                    location = location,
                    usn = headers["usn"],
                    server = headers["server"],
                    searchTarget = st,
                )
            }
        }
        found.values.toList()
    }

    /**
     * **单播发现**：直接把 M-SEARCH 发给已知 IP。
     *
     * 为什么需要：组播不过 VPN/不同网段，而实机（DOSS DS-1831）确认对单播 M-SEARCH 也回 200。
     * 因此「IP 已知但组播不通」的场景（跨网段、AP 隔离、VPN）用这个方法。
     */
    suspend fun discoverUnicast(
        hosts: List<String>,
        timeoutMs: Int = 1_500,
        searchTarget: String = SsdpMessages.ST_MEDIA_RENDERER,
    ): List<DiscoveredDevice> =
        withContext(Dispatchers.IO) {
            val found = LinkedHashMap<String, DiscoveredDevice>()
            hosts.forEach { host ->
                runCatching {
                    DatagramSocket().use { socket ->
                        socket.soTimeout = timeoutMs
                        val msg = SsdpMessages.buildMSearch(searchTarget).toByteArray()
                        socket.send(DatagramPacket(msg, msg.size, InetSocketAddress(host, SsdpMessages.PORT)))
                        val buf = ByteArray(4096)
                        val packet = DatagramPacket(buf, buf.size)
                        socket.receive(packet)
                        val text = String(packet.data, 0, packet.length, Charsets.UTF_8)
                        SsdpMessages.parseResponse(text)?.let { r ->
                            found[host] = DiscoveredDevice(
                                ip = r.hostFromLocation ?: host,
                                location = r.location,
                                usn = r.usn,
                                server = r.server,
                                searchTarget = r.st,
                                uuid = r.uuid,
                            )
                        }
                    }
                }
            }
            found.values.toList()
        }

    /**
     * 按 uuid 重发现：用于「上次记住的设备 IP 变了」。
     *
     * 实机教训：同一台音箱从 `192.168.99.164` 漂到了 `.153`（DHCP 重新租约），
     * 只有 uuid 是稳定的（`getStatusEx.uuid` / SSDP 的 `USN`）。
     */
    suspend fun findByUuid(uuid: String): DiscoveredDevice? {
        if (uuid.isBlank()) return null
        val target = if (uuid.startsWith("uuid:", ignoreCase = true)) uuid else "uuid:$uuid"
        // 扫描超时由构造参数 timeoutMs 决定（组播窗口是类级配置，避免每次调用语义不一致）
        return discover().firstOrNull { it.uuid.equals(target, ignoreCase = true) }
    }

    private fun buildMSearch(st: String): String = SsdpMessages.buildMSearch(st)

    private fun parseHeaders(raw: String): Map<String, String> {
        val map = HashMap<String, String>()
        raw.lineSequence().drop(1).forEach { line ->
            val idx = line.indexOf(':')
            if (idx > 0) {
                map[line.substring(0, idx).trim().lowercase()] = line.substring(idx + 1).trim()
            }
        }
        return map
    }

    companion object {
        const val MULTICAST_ADDRESS = SsdpMessages.MULTICAST_ADDRESS
        const val SSDP_PORT = SsdpMessages.PORT
        const val ST_WIIMU_PLAYQUEUE = SsdpMessages.ST_WIIMU_PLAY_QUEUE
        const val ST_MEDIA_RENDERER = SsdpMessages.ST_MEDIA_RENDERER

        val SEARCH_TARGETS = SsdpMessages.DEFAULT_SEARCH_TARGETS

        fun newSearchId(): String = UUID.randomUUID().toString()
    }
}
