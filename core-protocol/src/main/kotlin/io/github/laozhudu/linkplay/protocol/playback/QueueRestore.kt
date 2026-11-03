package io.github.laozhudu.linkplay.protocol.playback

import io.github.laozhudu.linkplay.protocol.player.DeviceQueueState
import io.github.laozhudu.linkplay.protocol.queue.QueueItem
import io.github.laozhudu.linkplay.protocol.session.PlaybackSession

/**
 * 「重开 App 后播放列表该显示什么」的**唯一判定逻辑**。
 *
 * ## 为什么单独抽出来
 *
 * 用户反复反馈「退出 App 重开后播放列表没有同步设备列表」。查下来是三条独立的原因叠在一起：
 *
 * 1. **有的路径根本不存快照**（音乐库、电台只播歌不落盘），所以重开必然空；
 * 2. 恢复逻辑只看本地快照，**不看设备**——设备明明在放第 3 首，界面却不知道有队列；
 * 3. 列表在设备上被"旋转"过（因为设备只认 `playlist` 的 index=0），
 *    设备报的 `plicurr` 直接拿来用会错位。
 *
 * 这三点各写在各处就必然漏，所以收敛成一个纯函数：
 * 输入「设备真值 + 本地快照」，输出「该显示的列表与高亮位置」。
 *
 * ## 优先级
 *
 * - 设备有队列且**长度与快照一致** → 用快照的标题 + 设备的当前位置（最理想）
 * - 设备有队列但**没有快照/长度不符** → 造占位列表，长度取设备值，让用户至少看到"设备上有 N 首、第 i 首"
 * - 设备没有队列 → 只有快照时展示快照（并标注"设备当前没有队列"）
 * - 都没有 → null（界面显示空状态，这是如实的）
 */
object QueueRestore {

    enum class Source {
        /** 设备握着队列，标题来自本地快照。 */
        DEVICE_WITH_SNAPSHOT,

        /** 设备握着队列，但本地没有对应快照 —— 只能给出占位标题。 */
        DEVICE_PLACEHOLDER,

        /** 设备当前没有队列，展示的是上次的快照。 */
        SNAPSHOT_ONLY,
    }

    data class Plan(
        val items: List<QueueItem>,
        val index: Int,
        val source: Source,
        /** 给界面用的一句话（说明这份列表从哪来、是否可信）。 */
        val note: String,
    ) {
        val fromDevice: Boolean get() = source != Source.SNAPSHOT_ONLY
    }

    /**
     * @param snapshot 本地快照（可能为 null：用户可能从没在 NAS 里播过）
     * @param device 设备侧现状；`null` 表示读不到设备（别把它当成"设备没队列"）
     * @param deviceOffset 下发时把列表旋转了多少（来自快照）
     * @param titleHint 设备的当前曲名；设备通常回 `Unknown`，有值时才用作占位标题
     */
    fun plan(
        snapshot: PlaybackSession?,
        device: DeviceQueueState?,
        deviceOffset: Int = snapshot?.deviceOffset ?: 0,
        titleHint: String? = null,
    ): Plan? {
        val count = device?.count ?: 0
        val deviceIndex = device?.index ?: -1
        val snapshotMatches = snapshot != null && count > 0 && snapshot.items.size == count

        if (snapshotMatches) {
            val items = snapshot!!.items
            // 设备位置 + 旋转偏移 = 本地列表位置
            val mapped = if (deviceIndex >= 0) {
                ((deviceOffset.coerceAtLeast(0) + deviceIndex) % items.size + items.size) % items.size
            } else {
                snapshot.index.coerceIn(0, items.lastIndex)
            }
            return Plan(
                items = items,
                index = mapped,
                source = Source.DEVICE_WITH_SNAPSHOT,
                note = "设备列表 ${count} 首 · 第 ${deviceIndex + 1} 首",
            )
        }

        if (count > 0) {
            // 设备上有队列，但我们没有它的曲目单（或长度对不上）
            val items = placeholderItems(count, titleHint)
            val idx = if (deviceIndex in items.indices) deviceIndex else 0
            return Plan(
                items = items,
                index = idx,
                source = Source.DEVICE_PLACEHOLDER,
                note = "设备上有 $count 首正在播，但本机没存到这份曲目单（只显示顺序）",
            )
        }

        if (snapshot != null && snapshot.items.isNotEmpty()) {
            return Plan(
                items = snapshot.items,
                index = snapshot.index.coerceIn(0, snapshot.items.lastIndex),
                source = Source.SNAPSHOT_ONLY,
                note = "设备当前没有队列，这里是上次播放的列表",
            )
        }
        return null
    }

    /** 设备有队列但没有曲目单时，造一个能点的占位列表（索引与设备一致，才能「点第 N 首」）。 */
    fun placeholderItems(count: Int, titleHint: String? = null): List<QueueItem> =
        (1..count).map { n ->
            QueueItem(
                // 没有真实地址：点它只会走"跳到设备侧第 N 首"，不会误投一个坏 URL
                url = "device://track/$n",
                title = if (n == 1 && !titleHint.isNullOrBlank()) titleHint else "设备上的第 $n 首",
            )
        }

    /** 占位项不可投流，界面据此改为发 `next`/`prev` 跳位。 */
    fun isPlaceholder(item: QueueItem?): Boolean = item?.url?.startsWith("device://") == true
}
