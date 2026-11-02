package io.github.laozhudu.linkplay.protocol.upnp

import java.net.URI

/** 设备描述里声明的单个服务。 */
data class UpnpService(
    val serviceType: String,
    val serviceId: String?,
    /** 已解析为绝对 URL。 */
    val controlUrl: String?,
    val eventSubUrl: String?,
    val scpdUrl: String?,
)

/**
 * 设备描述（`LOCATION` 指向的那个 XML）。
 *
 * SSDP 的 LOCATION 给出描述地址，描述里再给各服务的相对 controlURL；
 * 必须按 LOCATION 的 base 解析成绝对地址，否则 POST 会打到错误的主机。
 */
data class DeviceDescription(
    val location: String,
    val friendlyName: String?,
    val manufacturer: String?,
    val modelName: String?,
    val udn: String?,
    val services: List<UpnpService>,
) {
    fun service(serviceType: String): UpnpService? = services.firstOrNull { it.serviceType == serviceType }

    fun controlUrl(serviceType: String): String? = service(serviceType)?.controlUrl

    val avTransportControlUrl: String? get() = controlUrl(UpnpServices.AV_TRANSPORT)
    val renderingControlUrl: String? get() = controlUrl(UpnpServices.RENDERING_CONTROL)

    companion object {
        fun parse(location: String, xml: String): DeviceDescription {
            val base = runCatching { URI(location) }.getOrNull()
            val services = UpnpXml.blocks(xml, "service").mapNotNull { block ->
                val type = UpnpXml.value(block, "serviceType") ?: return@mapNotNull null
                UpnpService(
                    serviceType = type.trim(),
                    serviceId = UpnpXml.value(block, "serviceId")?.trim(),
                    controlUrl = resolve(base, UpnpXml.value(block, "controlURL")?.trim()),
                    eventSubUrl = resolve(base, UpnpXml.value(block, "eventSubURL")?.trim()),
                    scpdUrl = resolve(base, UpnpXml.value(block, "SCPDURL")?.trim()),
                )
            }
            return DeviceDescription(
                location = location,
                friendlyName = UpnpXml.value(xml, "friendlyName")?.trim(),
                manufacturer = UpnpXml.value(xml, "manufacturer")?.trim(),
                modelName = UpnpXml.value(xml, "modelName")?.trim(),
                udn = UpnpXml.value(xml, "UDN")?.trim(),
                services = services,
            )
        }

        fun resolve(base: URI?, path: String?): String? {
            if (path.isNullOrEmpty()) return null
            if (path.startsWith("http://", ignoreCase = true) || path.startsWith("https://", ignoreCase = true)) {
                return path
            }
            if (base == null) return null
            return runCatching { base.resolve(path).toString() }.getOrNull()
        }
    }
}
