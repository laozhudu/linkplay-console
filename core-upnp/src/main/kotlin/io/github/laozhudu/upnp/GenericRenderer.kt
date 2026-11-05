package io.github.laozhudu.upnp

import io.github.laozhudu.linkplay.protocol.upnp.DeviceDescription
import io.github.laozhudu.linkplay.protocol.upnp.SoapEnvelope
import io.github.laozhudu.linkplay.protocol.upnp.UpnpServices
import io.github.laozhudu.linkplay.protocol.upnp.UpnpTransport
import io.github.laozhudu.linkplay.protocol.upnp.UpnpXml

/**
 * 一台**标准 DLNA 渲染器**的控制点：只做 UPnP 规定的那几个动作。
 *
 * ## 与 LinkPlay 客户端的分工
 *
 * | | 这里 | LinkPlay（core-protocol） |
 * |---|---|---|
 * | 播放控制 | AVTransport 标准动作 | 同一套 + `SwitchMode` 等私有命令 |
 * | 音量 | RenderingControl | 同一套 |
 * | 电量/固件/组网 | **没有**（标准里没有） | `ControlDeviceInfo` + `slave_list` |
 * | 换源 | **没有**（不需要） | `SwitchMode("wifi")` 否则老固件不播 |
 *
 * 所以这不是"替代 LinkPlay"，是给非 DOSS 设备一条能走的路。
 *
 * ## 不做事件订阅（GENA）
 *
 * 订阅需要手机开端口等设备回调，移动网络下不可靠（`docs/32` 已有结论），
 * 所以状态一律靠轮询 `GetTransportInfo` / `GetPositionInfo`。
 */
class GenericRenderer(
    private val description: DeviceDescription,
    private val transport: UpnpTransport,
    private val instanceId: String = "0",
) {

    val capabilities: RendererCapabilities = RendererCapabilities.of(description)

    val deviceName: String get() = description.friendlyName.orEmpty()

    // ── 播放控制 ─────────────────────────────────────────────────────────

    /** 把地址交给设备。**只设置不播** —— UPnP 规定要先 SetAVTransportURI 再 Play。 */
    fun setAvTransportUri(mediaUrl: String, title: String? = null, mimeType: String = "audio/mpeg") {
        val metadata = if (title.isNullOrBlank()) {
            ""
        } else {
            didl(title = title, url = mediaUrl, mimeType = mimeType)
        }
        call(
            UpnpServices.AV_TRANSPORT,
            "SetAVTransportURI",
            listOf("InstanceID" to instanceId, "CurrentURI" to mediaUrl, "CurrentURIMetaData" to metadata),
        )
    }

    fun play(speed: String = "1") =
        call(UpnpServices.AV_TRANSPORT, "Play", listOf("InstanceID" to instanceId, "Speed" to speed))

    fun pause() = call(UpnpServices.AV_TRANSPORT, "Pause", instanceArgs())

    fun stop() = call(UpnpServices.AV_TRANSPORT, "Stop", instanceArgs())

    fun seek(seconds: Long, unit: String = "REL_TIME") = call(
        UpnpServices.AV_TRANSPORT,
        "Seek",
        listOf("InstanceID" to instanceId, "Unit" to unit, "Target" to timeText(seconds)),
    )

    // ── 状态 ────────────────────────────────────────────────────────────

    fun transportState(): String? =
        UpnpXml.value(
            call(UpnpServices.AV_TRANSPORT, "GetTransportInfo", instanceArgs()),
            "CurrentTransportState",
        )

    fun position(): Pair<Long?, Long?> {
        val xml = call(UpnpServices.AV_TRANSPORT, "GetPositionInfo", instanceArgs())
        return RendererState.parseTime(UpnpXml.value(xml, "RelTime")) to
            RendererState.parseTime(UpnpXml.value(xml, "TrackDuration"))
    }

    fun state(): RendererState {
        val state = runCatching { transportState() }.getOrNull()
        val (pos, dur) = runCatching { position() }.getOrDefault(null to null)
        val volume = runCatching { volume() }.getOrNull()
        val mute = runCatching { mute() }.getOrNull()
        return RendererState(transportState = state, positionMs = pos, durationMs = dur, volume = volume, mute = mute)
    }

    // ── 音量 ────────────────────────────────────────────────────────────

    fun volume(): Int? {
        if (!capabilities.volume) return null
        val xml = call(UpnpServices.RENDERING_CONTROL, "GetVolume", listOf("InstanceID" to instanceId, "Channel" to "Master"))
        return UpnpXml.value(xml, "CurrentVolume")?.toIntOrNull()
    }

    fun setVolume(volume: Int) {
        require(volume in 0..100) { "音量必须在 0..100：$volume" }
        call(
            UpnpServices.RENDERING_CONTROL,
            "SetVolume",
            listOf("InstanceID" to instanceId, "Channel" to "Master", "DesiredVolume" to volume.toString()),
        )
    }

    fun mute(): Boolean? {
        if (!capabilities.volume) return null
        val xml = call(UpnpServices.RENDERING_CONTROL, "GetMute", listOf("InstanceID" to instanceId, "Channel" to "Master"))
        return UpnpXml.value(xml, "CurrentMute")?.let { it == "1" || it.equals("true", ignoreCase = true) }
    }

    fun setMute(mute: Boolean) {
        call(
            UpnpServices.RENDERING_CONTROL,
            "SetMute",
            listOf("InstanceID" to instanceId, "Channel" to "Master", "DesiredMute" to if (mute) "1" else "0"),
        )
    }

    // ── 内部 ────────────────────────────────────────────────────────────

    private fun instanceArgs() = listOf("InstanceID" to instanceId)

    /**
     * 发一个动作。四个失败原因必须分开翻译（`docs/52`：命令回 OK ≠ 功能生效，
     * 但反过来"回异常"也要说清是哪一种，别一律"设备无响应"）。
     */
    fun call(serviceType: String, action: String, arguments: List<Pair<String, String>>): String {
        val url = description.controlUrl(serviceType)
            ?: throw RendererException.Unsupported("设备没有声明 $serviceType，不能执行 $action")
        val body = SoapEnvelope.build(serviceType, action, arguments)
        return try {
            transport.postSoap(url, SoapEnvelope.soapAction(serviceType, action), body)
        } catch (error: Throwable) {
            throw RendererException.of(action, error)
        }
    }

    private fun timeText(seconds: Long): String {
        val s = seconds.coerceAtLeast(0)
        return "%d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60)
    }

    /** DIDL-Lite 元数据；很多设备不认空 metadata，但对标题显示有用。 */
    private fun didl(title: String, url: String, mimeType: String): String = buildString {
        append("<DIDL-Lite xmlns=\"urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/\" ")
        append("xmlns:dc=\"http://purl.org/dc/elements/1.1/\" ")
        append("xmlns:upnp=\"urn:schemas-upnp-org:metadata-1-0/upnp/\">")
        append("<item id=\"0\" parentID=\"-1\" restricted=\"1\">")
        append("<dc:title>").append(UpnpXml.escape(title)).append("</dc:title>")
        append("<upnp:class>object.item.audioItem.musicTrack</upnp:class>")
        append("<res protocolInfo=\"http-get:*:").append(mimeType).append(":*\">")
        append(UpnpXml.escape(url)).append("</res>")
        append("</item></DIDL-Lite>")
    }
}

/** 通用渲染器的失败分类 —— 让界面能说人话，而不是把 SOAP 报文怼给用户。 */
sealed class RendererException(message: String) : Exception(message) {

    /** 设备没声明这个服务（能力位为假）。 */
    class Unsupported(message: String) : RendererException(message)

    /** 设备说不认识这个动作/参数（UPnP 的 401/402）。 */
    class InvalidAction(message: String) : RendererException(message)

    /** 设备明确拒绝（501 等）。 */
    class Rejected(message: String) : RendererException(message)

    /** 连不上、超时。 */
    class Unreachable(message: String) : RendererException(message)

    companion object {
        fun of(action: String, error: Throwable): RendererException {
            val message = error.message ?: error.javaClass.simpleName
            return when {
                message.contains("401") || message.contains("Invalid Action") ->
                    InvalidAction("$action：设备不认识这个动作（401）")
                message.contains("402") ->
                    InvalidAction("$action：参数不被接受（402）")
                message.contains("501") || message.contains("Action Failed") ->
                    Rejected("$action：设备执行失败（501）")
                else -> Unreachable("$action：${message.take(120)}")
            }
        }
    }
}
