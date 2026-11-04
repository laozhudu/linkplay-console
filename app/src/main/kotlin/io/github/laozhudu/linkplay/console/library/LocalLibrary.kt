package io.github.laozhudu.linkplay.console.library

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import io.github.laozhudu.linkplay.protocol.media.MediaResolver
import io.github.laozhudu.linkplay.protocol.playqueue.PlaylistTrack
import java.io.File

/** 一次查询的结果：能用的曲目 + 查到却被跳过的条数。 */
data class LocalQueryResult(val tracks: List<LocalTrack>, val skipped: Int)

/** 本地曲目（来自 MediaStore）。 */
data class LocalTrack(
    val id: Long,
    val title: String,
    val artist: String?,
    val album: String?,
    val durationSec: Long,
    val sizeBytes: Long,
    val mimeType: String,
    val path: String,
) {
    val displayName: String get() = if (artist.isNullOrBlank()) title else "$title · $artist"
}

/**
 * 本地曲库访问。
 *
 * 只用 MediaStore 而不自己遍历目录：Android 10+ 的 Scoped Storage 下直接读
 * `/sdcard/...` 路径已不可靠，MediaStore 是官方通道。代价是：
 * - 需要有权限（Android 13+ 是 `READ_MEDIA_AUDIO`，更低版本是 `READ_EXTERNAL_STORAGE`）；
 * - 拿到的 `path` 在部分设备上为空（例如只上传到云端的条目），这类条目直接跳过。
 */
object LocalLibrary {

    private val SUPPORTED_EXT = setOf("mp3", "m4a", "aac", "flac", "ogg", "oga", "wav", "wma", "ape")

    fun query(context: Context, limit: Int = 2000): List<LocalTrack> =
        queryWithStats(context, limit).tracks

    /**
     * 同 [query]，但把「查到却被跳过多少条」一起带回来。
     *
     * 为什么要这个数字：`扫描 0 首` 有两种完全不同的含义——
     * 手机里真没歌，还是查到了但条目的路径为空（云盘条目）或格式不支持、全被过滤掉了。
     * 界面上两者都是"没有找到音乐"，而一个要你去下歌，一个要你换格式。
     */
    fun queryWithStats(context: Context, limit: Int = 2000): LocalQueryResult {
        val collection: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.DATA,
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val order = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

        val out = ArrayList<LocalTrack>()
        var skipped = 0
        context.contentResolver.query(collection, projection, selection, null, order)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
            val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)

            while (cursor.moveToNext() && out.size < limit) {
                val path = cursor.getString(dataCol)
                if (path.isNullOrBlank()) {
                    skipped++
                    continue
                }
                val ext = path.substringAfterLast('.', "").lowercase()
                if (ext !in SUPPORTED_EXT && !File(path).exists()) {
                    skipped++
                    continue
                }
                out += LocalTrack(
                    id = cursor.getLong(idCol),
                    title = cursor.getString(titleCol) ?: File(path).name,
                    artist = cursor.getString(artistCol)?.takeIf { it != "<unknown>" },
                    album = cursor.getString(albumCol),
                    durationSec = cursor.getLong(durCol) / 1000,
                    sizeBytes = cursor.getLong(sizeCol),
                    mimeType = cursor.getString(mimeCol)
                        ?: io.github.laozhudu.linkplay.protocol.media.LocalMediaServer.contentTypeOf(path),
                    path = path,
                )
            }
        }
        return LocalQueryResult(out, skipped)
    }

    /** 把 id 映射成文件路径的解析器，供 HTTP 服务用；路径形如 `/t/<id>`。 */
    fun resolver(tracks: List<LocalTrack>): MediaResolver {
        val byId = tracks.associateBy { it.id }
        return MediaResolver { path ->
            val id = path.substringAfterLast('/').substringBefore('?').toLongOrNull() ?: return@MediaResolver null
            byId[id]?.let { File(it.path) }
        }
    }

    /** 把本地曲目转成待推送的列表项，`baseUrl` 是本机 HTTP 服务地址。 */
    fun toPlaylist(tracks: List<LocalTrack>, baseUrl: String): List<PlaylistTrack> = tracks.map { t ->
        PlaylistTrack(
            url = "$baseUrl/t/${t.id}",
            title = t.title,
            artist = t.artist,
            album = t.album,
            durationSec = t.durationSec,
            sizeBytes = t.sizeBytes,
            mimeType = t.mimeType,
        )
    }

    /** 某些机型需要 content:// 兜底（DATA 列不可用时）——仅用于展示，不用于推流。 */
    @Suppress("unused")
    fun contentUri(id: Long): Uri = ContentUris.withAppendedId(
        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id,
    )
}
