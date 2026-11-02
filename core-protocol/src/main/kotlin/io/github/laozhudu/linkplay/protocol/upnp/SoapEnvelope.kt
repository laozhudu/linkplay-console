package io.github.laozhudu.linkplay.protocol.upnp

/** 设备会声明的 UPnP 服务类型。 */
object UpnpServices {
    const val AV_TRANSPORT = "urn:schemas-upnp-org:service:AVTransport:1"
    const val RENDERING_CONTROL = "urn:schemas-upnp-org:service:RenderingControl:1"
    const val CONNECTION_MANAGER = "urn:schemas-upnp-org:service:ConnectionManager:1"

    /** 厂商私有播放队列服务（LinkPlay/WiiMu）。 */
    const val WIIMU_PLAY_QUEUE = "urn:schemas-wiimu-com:service:PlayQueue:1"
}

/**
 * SOAP 1.1 请求体构造。
 *
 * UPnP 控制点必须发 `POST` 到设备的 controlURL，带
 * `SOAPACTION: "<serviceType>#<action>"` 头，body 是固定的 SOAP 信封。
 * 这里只做拼装，便于单测精确断言每个动作的参数集——参数名写错设备会直接回 402/500。
 */
object SoapEnvelope {

    private const val NS = "urn:schemas-upnp-org:control-1-0"

    fun soapAction(serviceType: String, action: String): String = "\"$serviceType#$action\""

    fun build(serviceType: String, action: String, arguments: List<Pair<String, String>>): String =
        buildString {
            append("<?xml version=\"1.0\" encoding=\"utf-8\"?>")
            append("<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" ")
            append("s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">")
            append("<s:Body>")
            append("<u:").append(action).append(" xmlns:u=\"").append(serviceType).append("\">")
            for ((name, value) in arguments) {
                append("<").append(name).append(">")
                append(UpnpXml.escape(value))
                append("</").append(name).append(">")
            }
            append("</u:").append(action).append(">")
            append("</s:Body></s:Envelope>")
        }

    fun faultCode(responseXml: String): Int? =
        UpnpXml.value(responseXml, "errorCode")?.trim()?.toIntOrNull()

    fun faultDescription(responseXml: String): String? = UpnpXml.value(responseXml, "errorDescription")

    /** `upnp:control-1-0` 用不到，但保留以标明命名空间来源。 */
    fun controlNamespace(): String = NS
}
