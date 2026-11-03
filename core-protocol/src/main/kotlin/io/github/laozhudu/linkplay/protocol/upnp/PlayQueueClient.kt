package io.github.laozhudu.linkplay.protocol.upnp

import java.io.IOException

/**
 * 设备侧播放队列客户端（原厂 `PlayQueue` 服务）。
 *
 * ## 动作与参数（逐字取自原厂 APK）
 *
 * | 动作 | 参数 | 出处 |
 * |---|---|---|
 * | `CreateQueue` | `QueueContext` | `org.teleal.cling.support.c.a.e` |
 * | `AppendTracksInQueue` | `QueueContext` | `.a` |
 * | `ReplaceQueue` | `QueueContext` | 同族 |
 * | `DeleteQueue` | `QueueName` | 同族 |
 * | `PlayQueueWithIndex` | `QueueName`, `Index` | `.k` |
 * | `GetQueueIndex` | `QueueName` | `com.wifiaudio.g.b.b.e#e` |
 * | `BrowseQueue` | `QueueName` | 原厂 `queryCurrentQueueAndReplaceLocalQueue` 用它读回整份列表 |
 *
 * ## 为什么用这套而不是「客户端逐首投流」
 *
 * 这是原厂的做法，也是唯一能同时满足三件事的方案：
 * 1. 列表在**设备**上（App 退出照播，设备的上一首/下一首原生可用）；
 * 2. 重开 App 可以 `BrowseQueue` **把整份列表读回来**（含曲名），不依赖本地快照；
 * 3. `getPlayerStatus` 的 `plicount`/`plicurr` 与它对齐，界面能显示真实进度。
 *
 * 真机验证（`.153` / fw 3.6.5310）：`CreateQueue` 后 `plicount=4 plicurr=1 mode=10 play`；
 * `BrowseQueue` 返回完整 QueueContext（含 `<dc:title>`）；未知队列名返回 500（可据此判"没有队列"）。
 */
class PlayQueueClient(
    private val description: DeviceDescription,
    private val transport: UpnpTransport = HttpUpnpTransport(),
) {

    private val serviceType = UpnpServices.WIIMU_PLAY_QUEUE

    private val controlUrl: String
        get() = description.controlUrl(serviceType)
            ?: throw IOException("设备没有 PlayQueue 服务（${description.friendlyName ?: description.location}）")

    fun hasService(): Boolean = description.controlUrl(serviceType) != null

    private fun call(action: String, args: List<Pair<String, String>>): String =
        transport.postSoap(
            controlUrl,
            SoapEnvelope.soapAction(serviceType, action),
            SoapEnvelope.build(serviceType, action, args),
        )

    /** 用整份列表**新建**队列。返回设备回执（通常是空响应体）。 */
    fun createQueue(name: String, tracks: List<QueueTrack>): String =
        call("CreateQueue", listOf("QueueContext" to QueueContextXml.build(name, tracks)))

    /** 把更多曲目追加到已有队列。 */
    fun appendTracks(name: String, tracks: List<QueueTrack>): String =
        call("AppendTracksInQueue", listOf("QueueContext" to QueueContextXml.build(name, tracks)))

    /**
     * 从第 [index] 首开始播（**入参 0 基，线上 1 基**）。
     *
     * 真机教训：`Index` 是 **1 基**。传 0 会从第 1 首开始（看起来"正常"），
     * 但传 3 实际播的是第 3 首而不是第 4 首 —— 差一位很容易被忽略。
     * 原厂 `s(service, str, new ad(i), …)` 里 i 也是 1 基（`LastPlayIndex` 同一口径）。
     */
    fun playWithIndex(name: String, index: Int): String =
        call("PlayQueueWithIndex", listOf("QueueName" to name, "Index" to (index.coerceAtLeast(0) + 1).toString()))

    fun deleteQueue(name: String): String = call("DeleteQueue", listOf("QueueName" to name))

    /**
     * 读回整份队列。
     *
     * @return 队列内容；设备没有这个名字的队列时返回 null（实测设备回 500 UPnPError）
     */
    fun browseQueue(name: String): DevicePlaylist? = try {
        val body = call("BrowseQueue", listOf("QueueName" to name))
        val ctx = Regex("<QueueContext>(.*?)</QueueContext>", RegexOption.DOT_MATCHES_ALL)
            .find(body)?.groupValues?.get(1)
        if (ctx == null) null else QueueContextXml.parse(htmlUnescape(ctx))
    } catch (_: IOException) {
        null
    }

    /** 设备报的当前第几首（**1 基**）；读不到返回 -1。 */
    fun currentIndex(name: String): Int = try {
        val body = call("GetQueueIndex", listOf("QueueName" to name))
        Regex("<CurrentIndex>(\\d+)</CurrentIndex>").find(body)?.groupValues?.get(1)?.toIntOrNull() ?: -1
    } catch (_: IOException) {
        -1
    }

    /**
     * 在若干候选队列名里找第一个存在的。
     *
     * 为什么要多名字：本 App 用自己的名字（不覆盖原厂的 `LocalPlayList`），
     * 但用户可能之前用原厂 App 播过 —— 这时候读它的队列反而是对的。
     */
    fun browseFirstAvailable(names: List<String>): DevicePlaylist? {
        for (n in names) {
            browseQueue(n)?.takeIf { !it.isEmpty }?.let { return it }
        }
        return null
    }

    /** SOAP 里的 `<QueueContext>` 是转义文本，解一次；`&amp;` 放最后避免二次转义错乱。 */
    private fun htmlUnescape(s: String): String = s
        .replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace("&apos;", "'")
        .replace("&amp;", "&")
}

/** 本 App 使用的队列名（不抢占原厂的 `LocalPlayList`）。 */
object QueueNames {
    const val OURS = "LinkPlayConsole"

    /** 读回时的候选顺序：先自己的，再原厂的常用名。 */
    val readCandidates: List<String> = listOf(OURS, "LocalPlayList", "USBDiskQueue", "MyFavouriteQueue")
}
