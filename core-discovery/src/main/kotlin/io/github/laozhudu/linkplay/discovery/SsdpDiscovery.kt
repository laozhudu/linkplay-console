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
                if (st != null) {
                    found[ip] = DiscoveredDevice(
                        ip = ip,
                        location = headers["location"],
                        usn = headers["usn"],
                        server = headers["server"],
                        searchTarget = st,
                        uuid = SsdpMessages.uuidOf(headers["usn"]),
                    )
                } else if (text.contains("HTTP/1.1 200", ignoreCase = true) && !found.containsKey(ip)) {
                    // 部分固件回包缺 ST，但有 LOCATION，仍然收下。
                    found[ip] = DiscoveredDevice(
                        ip = ip,
                        location = headers["location"],
                        usn = headers["usn"],
                        server = headers["server"],
                        searchTarget = null,
                    )
                }
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
