package io.github.laozhudu.linkplay.console.playback

import android.content.Context
import io.github.laozhudu.linkplay.console.library.LibraryStore
import io.github.laozhudu.linkplay.protocol.DeviceHttpClient
import io.github.laozhudu.linkplay.protocol.queue.ClientSideQueue
import io.github.laozhudu.linkplay.protocol.queue.QueueItem
import io.github.laozhudu.linkplay.protocol.session.PlaybackKey
import io.github.laozhudu.linkplay.protocol.session.PlaybackSession
import io.github.laozhudu.linkplay.console.session.SessionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 「把一整份列表交给音箱并从第 N 首起播」—— **全 App 唯一入口**。
 *
 * ## 为什么必须只有一份实现
 *
 * 这条路有三段降级（PlayQueue → m3u → 客户端逐首投流），每段都有自己的坑：
 * - `PlayQueueWithIndex` 的 Index 是 **1 基**；
 * - m3u 只认 `index=0`，所以要把列表旋转；
 * - 客户端投流要用前台服务，`App` 退出也要能续。
 *
 * 以前音乐库、歌单、NAS、在线搜索各写一遍，结果设备侧行为不一致（有的入口有设备队列、
 * 有的没有），"重开 App 列表不同步"就是这么来的。现在统一走这里。
 *
 * @param deviceUuid 设备 uuid（快照按它存，换 IP 也能恢复）；拿不到就传 null
 * @param note 给界面看的一句话来源说明，例如 "歌单「通勤」"
 */
suspend fun startQueueOnDevice(
    context: Context,
    deviceIp: String,
    location: String?,
    deviceUuid: String?,
    items: List<QueueItem>,
    startAt: Int,
    note: String,
    /** 写日志用：调用方把每一行接到自己的 log 里。 */
    log: (String) -> Unit = {},
): Boolean {
    if (items.isEmpty()) {
        log("队列为空，什么都没做")
        return false
    }
    val sessions = SessionStore.of(context)
    val index = startAt.coerceIn(0, items.lastIndex)
    val session = PlaybackSession(
        deviceUuid = deviceUuid ?: deviceIp,
        deviceIp = deviceIp,
        items = items,
        index = index,
        loopMode = ClientSideQueue.LOOP_ALL,
    )

    // ① 原厂机制：CreateQueue + PlayQueueWithIndex（设备握着列表，App 退出照播）
    val upstream = DevicePlaylistGateway.push(deviceIp, location, items, index)
    if (upstream.ok) {
        withContext(Dispatchers.IO) {
            runCatching { sessions.savePlayback(PlaybackKey.of(deviceUuid, deviceIp), session) }
        }
        QueueBus.setOwner(deviceIp)
        QueueBus.restore(
            items, index, items.getOrNull(index)?.title,
            deviceOwned = true,
            note = "$note ${items.size} 首 · 第 ${index + 1} 首",
        )
        log(upstream.detail + "（设备侧队列 · 原厂 PlayQueue）")
        // 最近播放：起播之后才记（设备拒收时不算听过）
        io.github.laozhudu.linkplay.console.library.PlayJournal.recordCurrent(context, items, index)
        return true
    }
    log("PlayQueue 未接受：${upstream.detail}")

    // ② 兜底：m3u + setPlayerCmd:playlist
    val pushed = withContext(Dispatchers.IO) {
        DeviceQueuePush.push(DeviceHttpClient(), deviceIp, items, index)
    }
    withContext(Dispatchers.IO) {
        runCatching {
            sessions.savePlayback(
                PlaybackKey.of(deviceUuid, deviceIp),
                session.copy(deviceOffset = pushed.offset),
            )
        }
    }
    if (pushed.ok) {
        QueueBus.setOwner(deviceIp)
        QueueBus.restore(items, index, items.getOrNull(index)?.title, deviceOwned = true, offset = pushed.offset)
        QueueBus.markDeviceOwned(pushed.offset)
        log(pushed.detail + "（设备侧队列）")
        io.github.laozhudu.linkplay.console.library.PlayJournal.recordCurrent(context, items, index)
        return true
    }

    // ③ 最后兜底：客户端逐首投流（前台服务保活）
    log("设备未接收整列表：${pushed.detail} —— 退回客户端逐首投流")
    QueueBus.setOwner(deviceIp)
    withContext(Dispatchers.IO) { QueuePlaybackService.start(context, location, session) }
    io.github.laozhudu.linkplay.console.library.PlayJournal.recordCurrent(context, items, index)
    return true
}

/** 供 [startQueueOnDevice] 之后刷新「最近播放」列表用（音乐库页需要）。 */
suspend fun recentTracks(context: Context) = withContext(Dispatchers.IO) {
    LibraryStore.get(context).recents.recent()
}
