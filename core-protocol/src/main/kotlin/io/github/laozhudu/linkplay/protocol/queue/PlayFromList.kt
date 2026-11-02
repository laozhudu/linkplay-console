package io.github.laozhudu.linkplay.protocol.queue

/**
 * 「点一首 → 播整个列表」的起播位置计算。
 *
 * 为什么单独抽出来：媒体库、收藏、最近、NAS 目录四处都要做同一件事，
 * 各写一遍必然有一处算错（比如收藏列表里点了第 3 首却从第 1 首播），
 * 而这类错误在真机上要听一阵才发现。抽成纯函数就能被单测锁住。
 */
object PlayFromList {

    /**
     * @param list 当前屏幕上看到的那个列表（顺序就是用户看到的顺序）
     * @param key 被点中那一首的身份
     * @param keyOf 怎么从元素里取身份
     * @return 起播下标；不在列表里时返回 0（宁可从头播，也不要什么都不做）
     */
    fun <T> startIndex(list: List<T>, key: String, keyOf: (T) -> String): Int {
        if (list.isEmpty()) return 0
        val i = list.indexOfFirst { keyOf(it) == key }
        return if (i < 0) 0 else i
    }

    /**
     * 返回「从起播位置开始」的队列。
     *
     * 注意**不是**把前面的曲目丢掉：用户在播放列表里要能看到完整列表并往上翻。
     * 这里只给出下标，队列保留全量。
     */
    fun <T> reorderFrom(list: List<T>, index: Int): List<T> {
        if (list.isEmpty() || index <= 0 || index >= list.size) return list
        return list
    }
}
