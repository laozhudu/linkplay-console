package io.github.laozhudu.linkplay.protocol.upnp

import io.github.laozhudu.linkplay.protocol.LinkPlayStatus
import io.github.laozhudu.linkplay.protocol.SlaveDevice

/**
 * Wiimu 自定义的 RenderingControl 动作 `GetControlDeviceInfo` 的响应模型。
 *
 * ## 为什么这条动作是"原厂口径"
 *
 * 反编译 `org/teleal/cling/support/d/a/a.java`（原厂自带的一份 Cling 扩展）可见：
 * ```
 * GetControlDeviceInfo  参数：InstanceID=0
 * SetChannel            参数：InstanceID=0, Channel=Master, DesiredChannel=<0|1|2>
 * SetVolume             参数：InstanceID=0, Channel=Single,  DesiredVolume=<n>
 * ```
 * 原厂 `com/wifiaudio/g/au.java` 从这条动作的返回里取
 * `CurrentVolume / CurrentChannel / SlaveList / BatteryPercent / VerUpdateFlag …`。
 *
 * **一次调用同时拿到**音量、静音、声道、从机列表、以及整份 `getStatusEx`（嵌在 `<Status>` 里），
 * 这正是我们之前"电量有时有有时没有""声道读不到当前值"的结构性原因：
 * 我们分别调 `getPlayerStatus` / `getStatusEx` / `getSlaveList` 三次，再各自覆盖卡片，
 * 任何一次失败或返回缺字段就把已有的值冲掉。
 *
 * 真机实测（`.153` 与 `.164` 固件 3.6.5310 / 3.6.6623）都支持这条动作，HTTP 200。
 */
data class ControlDeviceInfo(
    /** 音量 0-100（`CurrentVolume`）。 */
    val volume: Int? = null,
    /** 静音（`CurrentMute` 非 0）。 */
    val mute: Boolean = false,
    /** 当前声道：0 立体声 / 1 仅左 / 2 仅右（`CurrentChannel`）。 */
    val channel: Int? = null,
    /** 从机列表（`SlaveList` 里嵌的 JSON，与 `multiroom:getSlaveList` 同格式）。 */
    val slaves: List<SlaveDevice> = emptyList(),
    /** `<Status>` 里嵌的整份 `getStatusEx`；解析失败为 null。 */
    val status: LinkPlayStatus? = null,
    /** 是否被屏蔽（`SlaveMask`）。 */
    val slaveMask: Boolean = false,
) {
    companion object {
        /**
         * 解析 SOAP 响应体。
         *
         * 注意 `<Status>` 与 `<SlaveList>` 的内容是**二次转义**的 JSON（`&quot;`），
         * 由 [UpnpXml.value] 反转义一次后即可直接交给 JSON 解析器。
         */
        fun parse(xml: String): ControlDeviceInfo {
            val slaveJson = UpnpXml.value(xml, "SlaveList")
            val statusJson = UpnpXml.value(xml, "Status")
            return ControlDeviceInfo(
                volume = UpnpXml.value(xml, "CurrentVolume")?.trim()?.toIntOrNull(),
                mute = UpnpXml.value(xml, "CurrentMute")?.trim()?.let { it != "0" } ?: false,
                channel = UpnpXml.value(xml, "CurrentChannel")?.trim()?.toIntOrNull(),
                slaves = slaveJson?.let { runCatching { SlaveDevice.parseList(it) }.getOrDefault(emptyList()) }
                    .orEmpty(),
                status = statusJson?.let { runCatching { LinkPlayStatus.parse(it) }.getOrNull() },
                slaveMask = UpnpXml.value(xml, "SlaveMask")?.trim()?.let { it != "0" } ?: false,
            )
        }
    }
}
