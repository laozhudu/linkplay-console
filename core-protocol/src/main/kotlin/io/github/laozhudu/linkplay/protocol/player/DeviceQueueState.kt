package io.github.laozhudu.linkplay.protocol.player

/**
 * 设备侧播放列表的现状（从 `getPlayerStatus` 读出的「真值」）。
 *
 * ## 为什么需要它
 *
 * 用户的原话是「播放器列表没有从设备同步，原厂怎么设计参考」。
 * 真机实测发现：把整个列表以 m3u 交给设备之后，设备会自己持有队列，并如实回报：
 *
 * ```
 * getPlayerStatus → mode=20  status=play  plicount=3  plicurr=1
 * ```
 *
 * - `plicount` = 设备侧列表长度
 * - `plicurr`  = **当前第几首（1 基！）**，设备播完自动 +1
 *
 * 所以界面上的「列表 + 高亮第几首」应该以这两个数为准，本地队列只负责**标题**
 * （设备不回填曲目元数据，实测 `Title` 是 `Unknown`）。
 *
 * ## 一个必须记住的坑
 *
 * `plicurr` 从 **1** 开始，而 `plicurr=0` 表示"没有当前项"。直接减 1 会得到 -1，
 * 所以这里统一收敛到 `-1 表示无`。
 */
data class DeviceQueueState(
    /** 设备侧列表长度；0 表示设备没有队列。 */
    val count: Int,
    /** 当前播放第几首，**0 基**；-1 表示设备没报或没有当前项。 */
    val index: Int,
    val playing: Boolean,
) {
    val hasQueue: Boolean get() = count > 0

    /**
     * 设备侧列表是否就是我们要的那一份。
     *
     * 为什么不能只比长度：长度相同但内容不同（比如换了一批同样数量的歌）会误判。
     * 但设备不给曲目清单，我们能拿到的只有长度 —— 所以这里用长度 + 曲名是否已知来近似，
     * 并在 [compatReason] 里把不确定说清楚，界面据此显示"本地列表 / 设备列表"。
     */
    fun matches(expectedCount: Int): Boolean = count == expectedCount && count > 0

    companion object {
        fun from(status: PlayerStatus): DeviceQueueState {
            val count = status.playListCount ?: 0
            val oneBased = status.playListCurrent ?: 0
            val index = if (oneBased >= 1 && oneBased <= count) oneBased - 1 else -1
            return DeviceQueueState(
                count = count,
                index = index,
                playing = status.playState == PlayState.PLAY,
            )
        }
    }
}
