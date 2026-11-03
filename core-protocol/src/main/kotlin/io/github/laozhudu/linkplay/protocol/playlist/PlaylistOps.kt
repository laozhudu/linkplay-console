package io.github.laozhudu.linkplay.protocol.playlist

import io.github.laozhudu.linkplay.protocol.queue.QueueItem

/**
 * 歌单内核（纯 JVM，无 Android 依赖）。
 *
 * ## 为什么单独一层
 *
 * 原厂「全部歌单」这个入口背后是一张 `tb_music_collection` 表 + 一套增删移的规则。
 * 规则里全是**容易写错又难复现**的边界：加重复曲目、拖到首尾、删到空、
 * 歌单重名、曲目超过上限……这些都不该和 SQLite / Compose 混在一起，
 * 否则只能靠手点验证。这里做成纯函数，单测直接锁死。
 *
 * ## 与原厂一致的几处
 *
 * - 曲目以 **`dedupeKey`** 判重（同一条流地址只存一份），不是按标题；
 * - 歌单名唯一（同名视为同一个歌单，不新建）；
 * - 顺序即播放顺序（列表顺序就是下发给设备 PlayQueue 的顺序）。
 */
object PlaylistOps {

    /** 一个歌单最多存多少首。 */
    const val MAX_TRACKS = 2000

    /** 一个库最多多少个歌单。 */
    const val MAX_LISTS = 200

    data class Playlist(
        val id: Long = 0,
        val name: String,
        val tracks: List<QueueItem> = emptyList(),
        val updatedAt: Long = 0,
    ) {
        val size: Int get() = tracks.size
    }

    /** 歌单名规范化：去首尾空白、压缩内部连续空白。空名返回 null（调用方据此拒绝）。 */
    fun normalizeName(raw: String): String? {
        val cleaned = raw.trim().replace(Regex("\\s+"), " ")
        return cleaned.takeIf { it.isNotEmpty() }?.take(60)
    }

    /**
     * 加曲目。返回**新列表**（不修改入参，便于 Compose 判等）。
     *
     * 三条规则：
     * 1. 已在列表里的 `dedupeKey` 不重复加（静默忽略，不报错 —— 用户点两次不该看到红色提示）；
     * 2. 总量不超过 [MAX_TRACKS]；
     * 3. `at` 越界时按追加处理（拖拽落点常常算错一位，这里容错）。
     */
    fun addTracks(existing: List<QueueItem>, adding: List<QueueItem>, at: Int? = null): List<QueueItem> {
        if (adding.isEmpty()) return existing
        val seen = existing.map { keyOf(it) }.toHashSet()
        val fresh = ArrayList<QueueItem>()
        for (t in adding) {
            // 先判上限再加：原来写成「加完再判」，结果最后一首会越界一格（单测抓到的）
            if (existing.size + fresh.size >= MAX_TRACKS) break
            val k = keyOf(t)
            if (!seen.add(k)) continue
            fresh += t
        }
        if (fresh.isEmpty()) return existing
        val index = at?.coerceIn(0, existing.size) ?: existing.size
        return buildList(existing.size + fresh.size) {
            addAll(existing.subList(0, index))
            addAll(fresh)
            addAll(existing.subList(index, existing.size))
        }
    }

    /** 删一首。按位置删（列表里允许同一首出现多次的场景下，位置才是用户看到的那个）。 */
    fun removeAt(existing: List<QueueItem>, index: Int): List<QueueItem> {
        if (index !in existing.indices) return existing
        return existing.toMutableList().also { it.removeAt(index) }
    }

    /**
     * 移动一首（拖拽排序）。
     *
     * 返回值与入参长度一致 —— 长度变了说明算错了，调用方可以据此判断"没动"。
     * `to` 越界时夹到末尾（落点在列表外松手是常见操作）。
     */
    fun move(existing: List<QueueItem>, from: Int, to: Int): List<QueueItem> {
        if (from !in existing.indices) return existing
        if (from == to) return existing
        val target = to.coerceIn(0, existing.lastIndex)
        if (from == target) return existing
        val list = existing.toMutableList()
        val item = list.removeAt(from)
        list.add(target, item)
        return list
    }

    /**
     * 合并同名歌单（原厂按名字唯一）。
     *
     * 返回 `existing` 索引，找不到就 -1。为什么按名字而不是 id 找：
     * 新建歌单时用户可能连续点两次「新建同名」，两条都得归到同一个。
     */
    fun indexOfName(lists: List<Playlist>, name: String): Int {
        val n = normalizeName(name) ?: return -1
        return lists.indexOfFirst { it.name.equals(n, ignoreCase = true) }
    }

    /**
     * 判重用的键。
     *
     * 优先用流地址：同一个 NAS 文件被不同目录索引到两次时，
     * 标题可能不同、地址一定相同。地址为空才退回标题。
     */
    fun keyOf(item: QueueItem): String =
        item.url.takeIf { it.isNotBlank() }?.let { "u:$it" }
            ?: "t:${item.title}|${item.artist.orEmpty()}"
}
