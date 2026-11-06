package io.github.laozhudu.linkplay.console.library

import android.content.Context
import io.github.laozhudu.linkplay.protocol.library.TrackRecord
import io.github.laozhudu.linkplay.protocol.queue.QueueItem

/**
 * 「最近播放」的统一写入点。
 *
 * ## 为什么需要它
 *
 * 之前只有音乐库那一页在起播后写最近播放，于是同一个用户行为在不同入口下
 * 结果不同：从音乐库点 → 最近里有；从 NAS 目录点 → 没有；从歌单点 → 没有。
 * 用户看到的就是「最近播放一直是空的」。
 *
 * 现在所有入口都调这里，规则只有一份：
 *
 * 1. **起播之后**才写（设备拒收时不写 —— 没播过就不算听过）；
 * 2. 按 [LibraryStore] 的 `dedupe_key` 去重（同一首重复播只更新时间）；
 * 3. 上限由 `LibraryStore.recents` 控制，这里不重复实现。
 *
 * ## 为什么 accept 的是 QueueItem 而不是 TrackRecord
 *
 * 各入口手上有的都是 `QueueItem`（NAS 浏览、歌单、队列项）。
 * 让调用方各自转一次 `TrackRecord` 会在三处写出三份略有差异的转换。
 */
object PlayJournal {

    /**
     * 记一条最近播放。
     *
     * @return 是否写入成功（false 表示可能是 IO/权限问题，调用方可以记日志但不该中断播放）
     */
    fun record(
        context: Context,
        item: QueueItem,
        playedAtMs: Long = System.currentTimeMillis(),
        deviceIp: String? = null,
    ): Boolean =
        runCatching {
            val store = LibraryStore.get(context)
            // 带设备写：定稿「接着听」那一行要显示「这首是在哪台设备上放的」。
            // deviceIp 为 null 表示投本机（本机不需要设备名）。
            store.recents.recordWithDevice(item.toRecord(), playedAtMs, deviceIp)
            true
        }.getOrDefault(false)

    /** 批量记（例如「全部播放」一次起播一整个歌单时，只记当前这一首）。 */
    fun recordCurrent(
        context: Context,
        items: List<QueueItem>,
        index: Int,
        deviceIp: String? = null,
    ): Boolean {
        val item = items.getOrNull(index) ?: return false
        return record(context, item, deviceIp = deviceIp)
    }

    /**
     * 队列项 → 库记录。
     *
     * 去重键由 [TrackRecord.dedupeKey] 自己算（`sourceType:playUri`），
     * 这里只负责把字段按同一套口径填进去 —— 不再另写一套判重逻辑，
     * 否则库里的去重键和歌单的去重键会各算一套、互相对不上。
     */
    private fun QueueItem.toRecord(): TrackRecord = TrackRecord(
        playUri = url,
        title = title,
        album = album,
        creator = artist,
        artist = artist,
        albumArtUri = albumArtUrl,
        durationSec = durationSec,
        folderName = null,
        sourceType = "queue",
        songId = 0,
    )
}
