package io.github.laozhudu.linkplay.protocol.session

import io.github.laozhudu.linkplay.protocol.Json
import io.github.laozhudu.linkplay.protocol.JsonValue
import io.github.laozhudu.linkplay.protocol.JsonWriter
import io.github.laozhudu.linkplay.protocol.queue.QueueItem

/**
 * 断点续播快照。
 *
 * 为什么需要：[io.github.laozhudu.linkplay.protocol.queue.ClientSideQueue] 的进度全在内存里，
 * 手机锁屏被系统杀掉、或者用户切走太久，整条队列就没了。存一份快照，重进 App 可以「继续上次」。
 *
 * 存 `index` 而不是逐首存进度：曲子内部的秒数由设备自己维护（设备重启就归零），
 * 我们只需要知道「该从第几首开始」。
 */
data class PlaybackSession(
    val deviceUuid: String,
    val deviceIp: String,
    val items: List<QueueItem>,
    val index: Int,
    val loopMode: Int,
) {
    /** 还能继续播的曲目（从 [index] 起）。 */
    val remaining: List<QueueItem> get() = items.drop(index.coerceIn(0, items.size))

    fun encode(): String = JsonWriter.encode(
        JsonValue.Obj(
            mapOf(
                "uuid" to JsonValue.Str(deviceUuid),
                "ip" to JsonValue.Str(deviceIp),
                "index" to JsonValue.Num(index.toDouble()),
                "loop" to JsonValue.Num(loopMode.toDouble()),
                "items" to JsonValue.Arr(
                    items.map {
                        JsonValue.Obj(
                            buildMap {
                                put("url", JsonValue.Str(it.url))
                                put("title", JsonValue.Str(it.title))
                                it.artist?.let { a -> put("artist", JsonValue.Str(a)) }
                                it.album?.let { a -> put("album", JsonValue.Str(a)) }
                                put("dur", JsonValue.Num(it.durationSec.toDouble()))
                                put("mime", JsonValue.Str(it.mimeType))
                                it.albumArtUrl?.let { a -> put("art", JsonValue.Str(a)) }
                            },
                        )
                    },
                ),
            ),
        ),
    )

    companion object {
        fun decode(text: String?): PlaybackSession? {
            if (text.isNullOrBlank()) return null
            return runCatching {
                val root = Json.parse(text) as? JsonValue.Obj ?: return null
                val uuid = root.string("uuid")?.takeIf { it.isNotBlank() } ?: return null
                val ip = root.string("ip")?.takeIf { it.isNotBlank() } ?: return null
                val items = root.array("items")?.items.orEmpty().mapNotNull { v ->
                    val o = v as? JsonValue.Obj ?: return@mapNotNull null
                    val url = o.string("url") ?: return@mapNotNull null
                    QueueItem(
                        url = url,
                        title = o.string("title").orEmpty(),
                        artist = o.string("artist"),
                        album = o.string("album"),
                        durationSec = o.int("dur")?.toLong() ?: 0L,
                        mimeType = o.string("mime") ?: "audio/mpeg",
                        albumArtUrl = o.string("art"),
                    )
                }
                if (items.isEmpty()) return null
                PlaybackSession(
                    deviceUuid = uuid,
                    deviceIp = ip,
                    items = items,
                    index = (root.int("index") ?: 0).coerceIn(0, items.size - 1),
                    loopMode = root.int("loop") ?: 2,
                )
            }.getOrNull()
        }
    }
}
