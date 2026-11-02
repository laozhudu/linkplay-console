package io.github.laozhudu.linkplay.protocol.playqueue

import io.github.laozhudu.linkplay.protocol.upnp.DeviceDescription
import io.github.laozhudu.linkplay.protocol.upnp.HttpUpnpTransport
import io.github.laozhudu.linkplay.protocol.upnp.SoapEnvelope
import io.github.laozhudu.linkplay.protocol.upnp.UpnpServices
import io.github.laozhudu.linkplay.protocol.upnp.UpnpTransport
import io.github.laozhudu.linkplay.protocol.upnp.UpnpXml

/**
 * 厂商私有播放队列服务控制点：`urn:schemas-wiimu-com:service:PlayQueue:1`。
 *
 * 为什么必须做它：标准 UPnP 的 AVTransport 只有「设一个 URI」，
 * **没有队列概念**。整列表推送（原厂 App 的核心能力）就靠这套私有动作。
 *
 * 全部动作名与参数名均从原厂 App 的 Cling 动作封装类里逐字段读出（✅ 已确证）：
 *
 * | 动作 | 参数（顺序即 SOAP 顺序） |
 * |---|---|
 * | `CreateQueue` | `QueueContext` |
 * | `DeleteQueue` | `QueueName` |
 * | `BackUpQueue` | `QueueContext` |
 * | `AppendTracksInQueue` | `QueueContext` |
 * | `AppendTracksInQueueEx` | `QueueContext`, `Direction`(ui4), `StartIndex`(ui4) |
 * | `PlayQueueWithIndex` | `QueueName`, `Index`(ui4) |
 * | `GetQueueIndex` | `QueueName` |
 * | `RemoveTracksInQueue` | `QueueName`, `RangStart`(ui4), `RangEnd`(ui4) |
 * | `SetQueueLoopMode` | `LoopMode`(ui4) |
 * | `BrowseQueue` | `QueueName` |
 *
 * 两个必须原样保留的坑：
 * 1. `RangStart` / `RangEnd` 少了字母 `e`（不是 `RangeStart`），是设备端真实参数名；
 * 2. `Direction` 在 SOAP 里排在 `StartIndex` **前面**，尽管构造函数里 StartIndex 先出现。
 */
class PlayQueueClient(
    private val description: DeviceDescription,
    private val transport: UpnpTransport = HttpUpnpTransport(),
) {
    private val controlUrl: String? = description.controlUrl(UpnpServices.WIIMU_PLAY_QUEUE)

    /** 设备是否声明了这个私有服务（没有就不能整列表推送，只能单曲投流）。 */
    val isSupported: Boolean get() = controlUrl != null

    fun createQueue(queueContext: String): String =
        call("CreateQueue", listOf("QueueContext" to queueContext))

    fun deleteQueue(queueName: String): String =
        call("DeleteQueue", listOf("QueueName" to queueName))

    /** 猜测语义：把队列内容「备份」到设备侧存储，用于断电恢复。⚠️ 未实测。 */
    fun backUpQueue(queueContext: String): String =
        call("BackUpQueue", listOf("QueueContext" to queueContext))

    fun appendTracks(queueContext: String): String =
        call("AppendTracksInQueue", listOf("QueueContext" to queueContext))

    /**
     * 带方向的追加。`direction` 语义未确证（推断 0=尾部追加、1=头部插入），
     * 默认 0 与「按顺序往后加」一致。
     */
    fun appendTracksEx(
        queueContext: String,
        startIndex: Int = 0,
        direction: Int = 0,
    ): String = call(
        "AppendTracksInQueueEx",
        listOf(
            "QueueContext" to queueContext,
            "Direction" to direction.toString(),
            "StartIndex" to startIndex.toString(),
        ),
    )

    /** 播放队列中第 index 条（0 基）。原厂就是靠它开始播整张列表。 */
    fun playQueueWithIndex(queueName: String, index: Int = 0): String =
        call("PlayQueueWithIndex", listOf("QueueName" to queueName, "Index" to index.toString()))

    fun getQueueIndex(queueName: String): Int? {
        val response = call("GetQueueIndex", listOf("QueueName" to queueName))
        val raw = UpnpXml.value(response, "Index") ?: UpnpXml.value(response, "QueueIndex")
        return raw?.trim()?.toIntOrNull()
    }

    /** `endIndex` 闭区间；原厂传的是 (start, end)。 */
    fun removeTracks(queueName: String, startIndex: Int, endIndex: Int): String = call(
        "RemoveTracksInQueue",
        listOf(
            "QueueName" to queueName,
            "RangStart" to startIndex.toString(),
            "RangEnd" to endIndex.toString(),
        ),
    )

    /**
     * 列表循环模式。取值与原厂 UI 的四种一致：0 顺序不循环 / 1 单曲循环 /
     * 2 顺序循环 / 3 随机（⚠️ 与 `setPlayerCmd:loopmode` 的 0/1/2/-1 不是同一套编号，勿混用）。
     */
    fun setQueueLoopMode(mode: Int): String =
        call("SetQueueLoopMode", listOf("LoopMode" to mode.toString()))

    /** 读回队列内容（返回 DIDL 或错误）。用于确认整列表是否真的推上去了。 */
    fun browseQueue(queueName: String): String =
        call("BrowseQueue", listOf("QueueName" to queueName))

    private fun call(action: String, args: List<Pair<String, String>>): String {
        val url = controlUrl ?: throw IllegalStateException(
            "设备没有声明 ${UpnpServices.WIIMU_PLAY_QUEUE}，无法使用队列功能",
        )
        return transport.postSoap(
            url,
            SoapEnvelope.soapAction(UpnpServices.WIIMU_PLAY_QUEUE, action),
            SoapEnvelope.build(UpnpServices.WIIMU_PLAY_QUEUE, action, args),
        )
    }
}

/** 队列循环模式常量，避免调用方传魔法数字。 */
object QueueLoopMode {
    const val NONE = 0
    const val SINGLE = 1
    const val ALL = 2
    const val SHUFFLE = 3

    fun label(mode: Int): String = when (mode) {
        NONE -> "不循环"
        SINGLE -> "单曲循环"
        ALL -> "列表循环"
        SHUFFLE -> "随机"
        else -> "未知($mode)"
    }
}
