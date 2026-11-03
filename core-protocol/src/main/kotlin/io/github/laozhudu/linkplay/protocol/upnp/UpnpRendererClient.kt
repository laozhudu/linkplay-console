package io.github.laozhudu.linkplay.protocol.upnp

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** 传输层抽象：让 UPnP 逻辑可以在单测里用假实现驱动，不需要真设备。 */
interface UpnpTransport {
    /** GET 文本（用于拉取设备描述）。 */
    fun get(url: String): String

    /** POST SOAP（controlURL），返回响应体；HTTP 非 2xx 时抛 [IOException]。 */
    fun postSoap(url: String, soapAction: String, body: String): String
}

/** 默认实现：短连接 + 明确超时；renderer 是嵌入式服务，长连接容易挂住。 */
class HttpUpnpTransport(
    private val connectTimeoutMs: Int = 4_000,
    private val readTimeoutMs: Int = 8_000,
) : UpnpTransport {

    override fun get(url: String): String {
        val conn = open(url, "GET")
        try {
            val body = readBody(conn)
            if (conn.responseCode !in 200..299) throw IOException("HTTP ${conn.responseCode} GET $url")
            return body
        } finally {
            conn.disconnect()
        }
    }

    override fun postSoap(url: String, soapAction: String, body: String): String {
        val conn = open(url, "POST")
        try {
            val bytes = body.toByteArray(Charsets.UTF_8)
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"")
            conn.setRequestProperty("SOAPACTION", soapAction)
            conn.setFixedLengthStreamingMode(bytes.size)
            conn.outputStream.use { it.write(bytes) }
            val response = readBody(conn)
            val code = conn.responseCode
            if (code !in 200..299) {
                val fault = SoapEnvelope.faultCode(response)
                val desc = SoapEnvelope.faultDescription(response)
                throw IOException(
                    "SOAP $code for $soapAction" +
                        (fault?.let { " (UPnP error $it${desc?.let { d -> ": $d" } ?: ""})" } ?: ""),
                )
            }
            return response
        } finally {
            conn.disconnect()
        }
    }

    private fun open(url: String, method: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            instanceFollowRedirects = false
            useCaches = false
            setRequestProperty("Connection", "close")
        }

    private fun readBody(conn: HttpURLConnection): String {
        val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
        return stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
    }
}

/**
 * UPnP 媒体渲染器控制点（AVTransport + RenderingControl）。
 *
 * 设计取舍：**不做 GENA 事件订阅**。订阅要求客户端跑一个可被设备回调的 HTTP 服务
 * （SUBSCRIBE + NOTIFY 到手机端口），在移动网络环境下不可靠、且要额外监听端口；
 * 实测同代设备轮询 `GetTransportInfo` / `GetPositionInfo` 的开销可忽略。
 * 因此这里提供 `refresh()` 供 UI 每秒拉一次，行为可预测、无后台服务、易测。
 */
class UpnpRendererClient(
    private val description: DeviceDescription,
    private val transport: UpnpTransport = HttpUpnpTransport(),
    private val instanceId: String = "0",
    private val channel: String = "Master",
) {
    private val avTransportUrl: String = description.avTransportControlUrl
        ?: throw IllegalArgumentException("设备描述里没有 AVTransport 服务，无法做播放控制")
    private val renderingControlUrl: String? = description.renderingControlUrl

    // ---- AVTransport ----

    fun setAvTransportUri(mediaUrl: String, title: String? = null, mimeType: String = "audio/mpeg") {
        val metadata = buildDidlLite(mediaUrl, title, mimeType)
        call(
            UpnpServices.AV_TRANSPORT,
            avTransportUrl,
            "SetAVTransportURI",
            listOf(
                "InstanceID" to instanceId,
                "CurrentURI" to mediaUrl,
                "CurrentURIMetaData" to metadata,
            ),
        )
    }

    fun play(speed: String = "1") = call(
        UpnpServices.AV_TRANSPORT, avTransportUrl, "Play",
        listOf("InstanceID" to instanceId, "Speed" to speed),
    )

    fun pause() = call(
        UpnpServices.AV_TRANSPORT, avTransportUrl, "Pause",
        listOf("InstanceID" to instanceId),
    )

    fun stop() = call(
        UpnpServices.AV_TRANSPORT, avTransportUrl, "Stop",
        listOf("InstanceID" to instanceId),
    )

    /** `unit` 取 UPnP 标准值：`REL_TIME`（绝对秒数）或 `TRACK_NR`。 */
    /**
     * 下一首 / 上一首 —— 原厂走 **AVTransport 的 Next/Previous**，不是 httpapi 的 `setPlayerCmd:next`。
     *
     * 为什么照原厂：只有走 AVTransport，设备才会按**它自己持有的队列**（PlayQueue）切歌；
     * 用 httpapi 那条在老固件上只会动"当前曲目的播放位置索引"，与队列不同步。
     */
    fun next() = call(UpnpServices.AV_TRANSPORT, avTransportUrl, "Next", instanceArgs())

    fun previous() = call(UpnpServices.AV_TRANSPORT, avTransportUrl, "Previous", instanceArgs())

    fun seek(seconds: Long, unit: String = "REL_TIME") = call(
        UpnpServices.AV_TRANSPORT, avTransportUrl, "Seek",
        listOf(
            "InstanceID" to instanceId,
            "Unit" to unit,
            "Target" to PositionInfo.formatTime(seconds),
        ),
    )

    fun transportInfo(): TransportInfo =
        TransportInfo.parse(
            call(UpnpServices.AV_TRANSPORT, avTransportUrl, "GetTransportInfo", instanceArgs()),
        )

    fun positionInfo(): PositionInfo =
        PositionInfo.parse(
            call(UpnpServices.AV_TRANSPORT, avTransportUrl, "GetPositionInfo", instanceArgs()),
        )

    // ---- RenderingControl ----

    fun volume(): VolumeInfo {
        val url = requireRenderingControl()
        return VolumeInfo.parse(
            call(UpnpServices.RENDERING_CONTROL, url, "GetVolume", listOf("InstanceID" to instanceId, "Channel" to channel)),
        )
    }

    fun setVolume(volume: Int) {
        val url = requireRenderingControl()
        call(
            UpnpServices.RENDERING_CONTROL, url, "SetVolume",
            listOf(
                "InstanceID" to instanceId,
                "Channel" to channel,
                "DesiredVolume" to volume.coerceIn(0, 100).toString(),
            ),
        )
    }

    /** 读静音状态（RenderingControl.GetMute）。 */
    fun mute(): Boolean {
        val url = requireRenderingControl()
        val body = call(
            UpnpServices.RENDERING_CONTROL, url, "GetMute",
            listOf("InstanceID" to instanceId, "Channel" to channel),
        )
        return body.contains("<CurrentMute>1</CurrentMute>")
    }

    fun setMute(mute: Boolean) {
        val url = requireRenderingControl()
        call(
            UpnpServices.RENDERING_CONTROL, url, "SetMute",
            listOf(
                "InstanceID" to instanceId,
                "Channel" to channel,
                "DesiredMute" to if (mute) "1" else "0",
            ),
        )
    }

    private fun requireRenderingControl(): String =
        renderingControlUrl ?: throw IllegalStateException("设备没有 RenderingControl 服务，无法调音量")

    private fun instanceArgs() = listOf("InstanceID" to instanceId)

    private fun call(serviceType: String, url: String, action: String, args: List<Pair<String, String>>): String =
        transport.postSoap(url, SoapEnvelope.soapAction(serviceType, action), SoapEnvelope.build(serviceType, action, args))

    companion object {
        /** 拉取并解析设备描述。 */
        fun describe(location: String, transport: UpnpTransport = HttpUpnpTransport()): DeviceDescription =
            DeviceDescription.parse(location, transport.get(location))

        /**
         * 没有 LOCATION 时（手输 IP、或 SSDP 被隔离）按已知路径探测描述文件。
         *
         * 依据：DOSS DS-1831 实测描述在 `http://<ip>:49152/description.xml`
         * （SSDP 回的 LOCATION 就是它）。LinkPlay 系设备这个端口/路径基本一致，
         * 失败就返回 null 由调用方提示，不猜别的端口。
         */
        fun probeDescription(ip: String, transport: UpnpTransport = HttpUpnpTransport()): DeviceDescription? =
            runCatching { describe("http://$ip:49152/description.xml", transport) }.getOrNull()

        /** 便捷构造：先按已知路径探测描述，失败时抛带说明的异常。 */
        fun ofDevice(ip: String, transport: UpnpTransport = HttpUpnpTransport()): UpnpRendererClient {
            val desc = probeDescription(ip, transport)
                ?: throw IllegalStateException(
                    "无法从 $ip:49152/description.xml 读到设备描述；请先用 SSDP 搜索进入（手动 IP 时拿不到控制地址）",
                )
            return UpnpRendererClient(desc, transport)
        }

        /**
         * 生成最小 DIDL-Lite。多数 renderer 对 metadata 宽容，但部分固件要求 `upnp:class`
         * 与 `res protocolInfo` 同时存在才会播放，所以两者都给。
         */
        fun buildDidlLite(mediaUrl: String, title: String?, mimeType: String): String {
            val safeTitle = UpnpXml.escape(title ?: "LinkPlay Console")
            val safeUrl = UpnpXml.escape(mediaUrl)
            val protocolInfo = UpnpXml.escape("http-get:*:$mimeType:*")
            return buildString {
                append("<DIDL-Lite xmlns=\"urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/\" ")
                append("xmlns:dc=\"http://purl.org/dc/elements/1.1/\" ")
                append("xmlns:upnp=\"urn:schemas-upnp-org:metadata-1-0/upnp/\">")
                append("<item id=\"0\" parentID=\"-1\" restricted=\"1\">")
                append("<dc:title>").append(safeTitle).append("</dc:title>")
                append("<upnp:class>object.item.audioItem.musicTrack</upnp:class>")
                append("<res protocolInfo=\"").append(protocolInfo).append("\">")
                append(safeUrl)
                append("</res>")
                append("</item>")
                append("</DIDL-Lite>")
            }
        }
    }
}
