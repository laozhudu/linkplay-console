package io.github.laozhudu.linkplay.protocol.qplay

import io.github.laozhudu.linkplay.protocol.upnp.DeviceDescription
import io.github.laozhudu.linkplay.protocol.upnp.HttpUpnpTransport
import io.github.laozhudu.linkplay.protocol.upnp.SoapEnvelope
import io.github.laozhudu.linkplay.protocol.upnp.UpnpTransport
import io.github.laozhudu.linkplay.protocol.upnp.UpnpXml

/** QPlay 服务类型（腾讯为 LinkPlay 系设备定制的 DLNA 扩展）。 */
const val QPLAY_SERVICE_TYPE = "urn:schemas-tencent-com:service:QPlay:1"

/** `QPlayAuth` 的返回。 */
data class QPlayAuthResult(val code: String?, val mid: String?, val did: String?)

/**
 * QPlay 控制点（QQ 音乐推歌到音箱用的就是这条通道）。
 *
 * 实机事实（DOSS DS-1831 / 3.6.5310，SCPD 逐字段读出）：
 * - 服务类型 `urn:schemas-tencent-com:service:QPlay:1`，controlURL `/upnp/control/QPlay1`
 * - 9 个动作：`InsertTracks` / `RemoveTracks` / `RemoveAllTracks` / `GetTracksInfo` /
 *   `SetTracksInfo` / `GetTracksCount` / `GetMaxTracks` / `QPlayAuth` / `SetNetwork`
 * - `GetMaxTracks` 实机返回 **200**（队列上限 200 首）
 * - `InsertTracks(QueueID, StartingIndex, TracksMetaData)` 无认证时**返回 200 但空响应体**，
 *   且 `GetTracksCount` 仍为空 —— 即**必须先做 `QPlayAuth` 握手**，否则写入被静默忽略
 *
 * 因为 `QPlayAuth` 需要腾讯侧的种子/密钥协商，这条通道对第三方客户端实际是**关闭的**：
 * 它存在的意义是让 QQ 音乐这类合作方推歌。本项目实现它只为「探测 + 说清为什么不能用」，
 * 不做任何绕过认证的尝试。
 */
class QPlayClient(
    private val description: DeviceDescription,
    private val transport: UpnpTransport = HttpUpnpTransport(),
) {
    private val controlUrl: String? = description.controlUrl(QPLAY_SERVICE_TYPE)

    val isSupported: Boolean get() = controlUrl != null

    /** 队列可容纳的最大曲目数；设备不支持时返回 null。 */
    fun maxTracks(): Int? {
        val r = call("GetMaxTracks", emptyList())
        return UpnpXml.value(r, "MaxTracks")?.trim()?.toIntOrNull()
    }

    /** 当前队列曲目数；无认证时设备回空响应体，返回 null。 */
    fun tracksCount(): Int? {
        val r = call("GetTracksCount", emptyList())
        return UpnpXml.value(r, "NrTracks")?.trim()?.toIntOrNull()
    }

    fun tracksInfo(startingIndex: Int = 0, numberOfTracks: Int = 100): String? {
        val r = call(
            "GetTracksInfo",
            listOf("StartingIndex" to startingIndex.toString(), "NumberOfTracks" to numberOfTracks.toString()),
        )
        return UpnpXml.value(r, "TracksMetaData")
    }

    /** 插入曲目。**未通过 [auth] 时设备静默忽略**（返回 200 但队列不变）。 */
    fun insertTracks(queueId: String, startingIndex: Int, tracksMetaData: String): Int? {
        val r = call(
            "InsertTracks",
            listOf(
                "QueueID" to queueId,
                "StartingIndex" to startingIndex.toString(),
                "TracksMetaData" to tracksMetaData,
            ),
        )
        return UpnpXml.value(r, "NumberOfSuccess")?.trim()?.toIntOrNull()
    }

    fun removeAllTracks(queueId: String): Int? {
        val r = call("RemoveAllTracks", listOf("QueueID" to queueId))
        return UpnpXml.value(r, "NumberOfSuccess")?.trim()?.toIntOrNull()
    }

    /**
     * QPlay 认证握手。需要腾讯下发的种子；第三方拿不到，因此本方法仅用于「验证通道是否活着」。
     * 返回 null 表示设备未返回 Code（多数情况：未授权或被拒）。
     */
    fun auth(seed: String): QPlayAuthResult? {
        val r = call("QPlayAuth", listOf("Seed" to seed))
        val code = UpnpXml.value(r, "Code")
        if (code.isNullOrBlank()) return null
        return QPlayAuthResult(code, UpnpXml.value(r, "MID"), UpnpXml.value(r, "DID"))
    }

    private fun call(action: String, args: List<Pair<String, String>>): String {
        val url = controlUrl ?: throw IllegalStateException("设备没有声明 $QPLAY_SERVICE_TYPE")
        return transport.postSoap(
            url,
            SoapEnvelope.soapAction(QPLAY_SERVICE_TYPE, action),
            SoapEnvelope.build(QPLAY_SERVICE_TYPE, action, args),
        )
    }
}
