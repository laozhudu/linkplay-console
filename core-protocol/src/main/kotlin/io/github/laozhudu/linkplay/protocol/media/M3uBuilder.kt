package io.github.laozhudu.linkplay.protocol.media

import io.github.laozhudu.linkplay.protocol.queue.QueueItem

/**
 * 把队列编成 M3U 播放列表。
 *
 * ## 为什么这条路才是对的
 *
 * 之前我们走的是「客户端排队」：App 自己记住队列，逐首 `SetAVTransportURI` 投给设备。
 * 后果是 **App 一退出队列就断**，而且设备自己的上一首/下一首也用不了。
 *
 * 真机实测（2026-11-03，DS-1831 / fw 3.6.5310）发现设备其实支持**整列表**：
 *
 * ```
 * setPlayerCmd:playlist:http://<手机>:<端口>/pl/xxx.m3u:0
 *   → OK，设备真的来 GET 这个 m3u
 *   → getPlayerStatus: mode=20 status=play plicount=3 plicurr=1   ← 列表长度就是 m3u 的条数
 *   → setPlayerCmd:next / prev 正常工作，plicurr 跟着变
 *   → **把 m3u 服务停掉之后，next/prev/pause/resume 依然全部正常**
 * ```
 *
 * 也就是说设备拿到 URL 列表后自己持有，不需要手机一直在场 —— 这正是原厂的行为。
 *
 * ## 格式细节
 *
 * - `#EXTM3U` 必须第一行；
 * - 每首前面可给 `#EXTINF:<秒>,<标题>`，实测本固件忽略标题（`Title` 仍是 Unknown），
 *   但按规范给出，其他渲染器会用到；时长未知时写 `-1`；
 * - 空行不写（部分老固件对空行敏感）。
 */
object M3uBuilder {

    /** @param items 顺序就是设备侧的播放顺序 */
    fun build(items: List<QueueItem>): String = buildString {
        append("#EXTM3U\n")
        items.forEach { item ->
            val seconds = if (item.durationSec > 0) item.durationSec else -1
            val title = item.title.replace('\n', ' ').replace('\r', ' ')
            append("#EXTINF:").append(seconds).append(',').append(title).append('\n')
            append(item.url).append('\n')
        }
    }

    /**
     * 从第 [start] 首开始重排（旋转），用于「从点中那首起播」。
     *
     * 为什么不用命令里的索引位：**真机实测 `setPlayerCmd:playlist:<url>:<idx>` 只有 idx=0 生效**，
     * 传 1/2 会回 `OK` 但 `plicount` 变成 0（静默失败）。
     * 而 `next` 连发跳到第 N 首也不可靠（实测 5 首里有一次 next 被吞）。
     *
     * 所以改成「把列表转一下，让目标曲排第一」，再以 index=0 下发。
     * 设备侧 `plicurr=1` 就对应原列表的第 [start] 首，映射关系是 `(start + deviceIndex) % size`。
     */
    fun rotateFrom(items: List<QueueItem>, start: Int): List<QueueItem> {
        if (items.size <= 1) return items
        val s = start.coerceIn(0, items.lastIndex)
        if (s == 0) return items
        return items.drop(s) + items.take(s)
    }

    /** 列表需要能被设备取到，所以必须是 http(s) 直链。返回为 false 时调用方应退回客户端排队。 */
    fun allStreamable(items: List<QueueItem>): Boolean =
        items.isNotEmpty() && items.all {
            val u = it.url
            u.startsWith("http://") || u.startsWith("https://")
        }
}
