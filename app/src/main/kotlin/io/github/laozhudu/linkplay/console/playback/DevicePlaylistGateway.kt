package io.github.laozhudu.linkplay.console.playback

import io.github.laozhudu.linkplay.protocol.queue.QueueItem
import io.github.laozhudu.linkplay.protocol.upnp.DevicePlaylist
import io.github.laozhudu.linkplay.protocol.upnp.PlayQueueClient
import io.github.laozhudu.linkplay.protocol.upnp.QueueNames
import io.github.laozhudu.linkplay.protocol.upnp.QueueTrack
import io.github.laozhudu.linkplay.protocol.upnp.UpnpRendererClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 设备侧播放列表（**原厂机制**）。
 *
 * ## 为什么是这一套
 *
 * 原厂的做法（反编译 `com.wifiaudio.g.bk` 的 `queryCurrentQueueAndReplaceLocalQueue` 与
 * `org.teleal.cling.support.c.a.l` 的 QueueContext 生成）：
 *
 * 1. 把整份列表编成 QueueContext XML，`CreateQueue` 交给设备的 PlayQueue 服务；
 * 2. `PlayQueueWithIndex(QueueName, Index)` 起播；
 * 3. 重开 App 时 `BrowseQueue(QueueName)` **把整份列表（含曲名）读回来**。
 *
 * 三步都真机验证过（`.153` / fw 3.6.5310）：
 * `plicount=4 plicurr=1 mode=10 play`，`BrowseQueue` 返回完整 QueueContext。
 *
 * ## 与我之前两版的区别（教训）
 *
 * - 客户端逐首投流：App 退出队列就断，设备的上一首/下一首不可用；
 * - m3u 交给 `setPlayerCmd:playlist`：能播，但设备只认 index=0、读不回曲目清单；
 * - **原厂这套**：设备自己握着列表，可读回、可跳位、退出照播。
 *
 * 所以这里以原厂机制为主，m3u 只作为「设备没有 PlayQueue 服务」时的兜底。
 */
object DevicePlaylistGateway {

    /** 描述地址：没有 SSDP 的 LOCATION 时按 LinkPlay 的固定端口拼。 */
    private fun locationOf(ip: String, location: String?): String =
        location?.takeIf { it.isNotBlank() } ?: "http://$ip:49152/description.xml"

    private suspend fun client(ip: String, location: String?): PlayQueueClient? =
        withContext(Dispatchers.IO) {
            runCatching {
                val desc = UpnpRendererClient.describe(locationOf(ip, location))
                PlayQueueClient(desc).takeIf { it.hasService() }
            }.getOrNull()
        }

    data class PushResult(val ok: Boolean, val detail: String)

    /**
     * 把整份列表交给设备并从第 [startIndex] 首起播。
     *
     * @return [PushResult.ok] 只有设备真的接受了列表（随后 `BrowseQueue` 能读回同样条数）才为 true
     */
    suspend fun push(
        ip: String,
        location: String?,
        items: List<QueueItem>,
        startIndex: Int,
        queueName: String = QueueNames.OURS,
    ): PushResult {
        if (items.isEmpty()) return PushResult(false, "列表为空")
        val c = client(ip, location) ?: return PushResult(false, "设备没有 PlayQueue 服务")
        val tracks = items.map { t ->
            QueueTrack(
                url = t.url,
                title = t.title,
                artist = t.artist,
                album = t.album,
                albumArtUri = t.albumArtUrl,
                durationSec = t.durationSec,
            )
        }
        val start = startIndex.coerceIn(0, items.lastIndex)
        return withContext(Dispatchers.IO) {
            try {
                // 同名队列先删掉，避免旧内容被追加（原厂也是"新建"语义）
                runCatching { c.deleteQueue(queueName) }
                c.createQueue(queueName, tracks)
                // 原厂这里 index 传 0；设备用 RealIndex 表示 0 基位置
                c.playWithIndex(queueName, start)
                // 回读确认：能读回同样条数才算成功（命令回 200 不等于列表进去了）
                val readBack = c.browseQueue(queueName)
                if (readBack != null && readBack.tracks.size == tracks.size) {
                    PushResult(true, "设备已接收 ${tracks.size} 首（第 ${start + 1} 首起）")
                } else {
                    PushResult(false, "设备回了成功，但读回是 ${readBack?.tracks?.size ?: 0} 首（期望 ${tracks.size}）")
                }
            } catch (t: Throwable) {
                PushResult(false, t.message ?: t.javaClass.simpleName)
            }
        }
    }

    /** 读回设备上的队列；没有就返回 null。多候选名依次尝试（含原厂用过的名字）。 */
    suspend fun read(ip: String, location: String?): DevicePlaylist? {
        val c = client(ip, location) ?: return null
        return withContext(Dispatchers.IO) {
            runCatching {
                val list = c.browseFirstAvailable(QueueNames.readCandidates) ?: return@runCatching null
                // 当前位置以 GetQueueIndex 为准（1 基 → 0 基）；BrowseQueue 里的 RealIndex 有时是旧值
                val oneBased = c.currentIndex(list.name)
                if (oneBased >= 1) list.copy(currentIndex = oneBased - 1) else list
            }.getOrNull()
        }
    }

    /** 在设备侧跳到第 [index] 首（0 基）。 */
    suspend fun playIndex(ip: String, location: String?, index: Int): Boolean {
        val c = client(ip, location) ?: return false
        return withContext(Dispatchers.IO) {
            runCatching { c.playWithIndex(QueueNames.OURS, index) }.isSuccess
        }
    }

    /** 设备队列里的曲目 → 界面用的队列项（标题由设备提供，不依赖本地快照）。 */
    fun toQueueItems(list: DevicePlaylist): List<QueueItem> = list.tracks.map { t ->
        QueueItem(
            url = t.url,
            title = t.title,
            artist = t.artist,
            album = t.album,
            durationSec = t.durationSec,
            albumArtUrl = t.albumArtUri,
        )
    }
}
