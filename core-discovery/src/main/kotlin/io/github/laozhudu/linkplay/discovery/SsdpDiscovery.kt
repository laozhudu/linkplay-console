package io.github.laozhudu.linkplay.discovery

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
)

class SsdpDiscovery(
    private val timeoutMs: Int = 3_000,
    private val maxDevices: Int = 64,
) {
    suspend fun discover(): List<DiscoveredDevice> = withContext(Dispatchers.IO) {
        val found = LinkedHashMap<String, DiscoveredDevice>()
        val address = InetAddress.getByName(MULTICAST_ADDRESS)
        DatagramSocket().use { socket ->
            socket.reuseAddress = true
            socket.soTimeout = 500
            socket.broadcast = true

            SEARCH_TARGETS.forEach { st ->
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

    private fun buildMSearch(st: String): String = buildString {
        append("M-SEARCH * HTTP/1.1\r\n")
        append("HOST: ").append(MULTICAST_ADDRESS).append(':').append(SSDP_PORT).append("\r\n")
        append("MAN: \"ssdp:discover\"\r\n")
        append("MX: 2\r\n")
        append("ST: ").append(st).append("\r\n")
        append("USER-AGENT: Android/1.0 UPnP/1.0 LinkPlayConsole/1.0\r\n")
        append("\r\n")
    }

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
        const val MULTICAST_ADDRESS = "239.255.255.250"
        const val SSDP_PORT = 1900
        const val ST_WIIMU_PLAYQUEUE = "urn:schemas-wiimu-com:service:PlayQueue:1"
        const val ST_MEDIA_RENDERER = "urn:schemas-upnp-org:device:MediaRenderer:1"

        val SEARCH_TARGETS = listOf(ST_WIIMU_PLAYQUEUE, ST_MEDIA_RENDERER, "ssdp:all")

        fun newSearchId(): String = UUID.randomUUID().toString()
    }
}
