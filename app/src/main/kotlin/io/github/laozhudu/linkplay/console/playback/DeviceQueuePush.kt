package io.github.laozhudu.linkplay.console.playback

import io.github.laozhudu.linkplay.console.util.Net
import io.github.laozhudu.linkplay.protocol.DeviceHttpClient
import io.github.laozhudu.linkplay.protocol.LinkPlayCommands
import io.github.laozhudu.linkplay.protocol.media.LocalMediaServer
import io.github.laozhudu.linkplay.protocol.media.M3uBuilder
import io.github.laozhudu.linkplay.protocol.player.DeviceQueueState
import io.github.laozhudu.linkplay.protocol.player.PlayerStatus
import io.github.laozhudu.linkplay.protocol.queue.QueueItem
import kotlinx.coroutines.delay

/**
 * 把**整个列表**交给设备（设备侧队列）。
 *
 * ## 为什么这是主路径而不是兜底
 *
 * 原来是我们自己排队（客户端逐首投流）：App 一退出队列就断，设备的上一首/下一首也用不了。
 * 真机实测（2026-11-03，DS-1831 fw 3.6.5310）证明设备自己能持有列表：
 *
 * ```
 * setPlayerCmd:playlist:<m3u url>:<起始序号>   → OK
 * getPlayerStatus → mode=20 status=play plicount=N plicurr=i
 * setPlayerCmd:next/prev/pause/resume 全部正常
 * **把 m3u 服务停掉（≈ App 退出）之后，上述操作依然全部正常**
 * ```
 *
 * 也就是说：设备拿到 URL 清单后自己管理播放，手机可以随时离开 —— 这正是原厂行为，
 * 也是「退出 App 再打开，状态都在设备上」的实现基础。
 *
 * ## 失败就如实失败
 *
 * 只有当所有曲目都是 http(s) 直链、并且设备回报的 `plicount` 真的等于列表长度时才算成功。
 * 命令回 `OK` 但 `plicount` 没变 = 没成功，调用方应退回客户端排队（而不是假装成功）。
 */
object DeviceQueuePush {

    /** 常驻的播放列表服务：只提供内存里的 m3u，不碰文件。 */
    private var server: LocalMediaServer? = null

    private fun ensureServer(): LocalMediaServer? {
        server?.let { if (it.port > 0) return it }
        val host = Net.localIpv4() ?: return null
        return runCatching {
            val created = LocalMediaServer(resolver = { null }, advertiseHost = host)
            created.start()
            server = created
            created
        }.getOrNull()
    }

    /**
     * @param offset 设备侧第 1 首对应本地列表的第几首（因为列表被旋转过）。
     *   界面高亮要用 `(offset + deviceIndex) % size`。
     */
    data class Result(val ok: Boolean, val detail: String, val offset: Int = 0)

    /**
     * @param startIndex 从第几首开始（0 基）
     * @return 成功与否 + 一句给人看的说明
     */
    suspend fun push(
        device: DeviceHttpClient,
        deviceIp: String,
        items: List<QueueItem>,
        startIndex: Int,
        pollTimes: Int = 6,
    ): Result {
        if (items.isEmpty()) return Result(false, "列表为空")
        if (!M3uBuilder.allStreamable(items)) {
            return Result(false, "有曲目不是 http 直链，设备取不到（需要先经本机 HTTP 服务暴露）")
        }
        val srv = ensureServer() ?: return Result(false, "拿不到本机局域网 IP，没法提供列表")
        val start = startIndex.coerceIn(0, items.lastIndex)
        // 设备只认 playlist 命令的 index=0（实测 1/2 会静默失败），所以把列表旋转到目标曲在首位
        val rotated = M3uBuilder.rotateFrom(items, start)
        val token = Integer.toHexString(System.nanoTime().toInt() xor rotated.first().url.hashCode())
        val path = "/pl/$token.m3u"
        srv.publishText(path, M3uBuilder.build(rotated), "audio/x-mpegurl")
        val url = srv.baseUrl() + path

        return try {
            // 切到网络源：不切的话某些输入源下 playlist 命令不生效
            runCatching { device.send(deviceIp, LinkPlayCommands.switchMode("wifi")) }
            delay(200)
            val reply = runCatching {
                // 固定 index=0：旋转已经保证从目标曲开始
                device.send(deviceIp, LinkPlayCommands.playPlaylist(url, 0))
            }.getOrElse { t -> return Result(false, "下发列表失败：${t.message}") }

            // 回读确认：plicount 必须真的等于列表长度
            repeat(pollTimes) {
                delay(700)
                val st = runCatching {
                    PlayerStatus.parse(device.send(deviceIp, LinkPlayCommands.getPlayerStatus()))
                }.getOrNull()
                if (st != null && DeviceQueueState.from(st).matches(items.size)) {
                    return Result(true, "设备已接收 ${items.size} 首（从第 ${start + 1} 首开始）", offset = start)
                }
            }
            Result(false, "设备回了 $reply，但列表长度没变成 ${items.size}")
        } finally {
            // 不立刻撤销：设备可能还要重读；保留 10 分钟由系统回收也无妨（内存很小）
        }
    }
}
