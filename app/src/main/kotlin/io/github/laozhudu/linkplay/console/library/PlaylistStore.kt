package io.github.laozhudu.linkplay.console.library

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import io.github.laozhudu.linkplay.protocol.playlist.PlaylistOps
import io.github.laozhudu.linkplay.protocol.queue.QueueItem

/**
 * 歌单持久化（原厂「全部歌单」）。
 *
 * ## 为什么用**独立数据库文件**而不是往 `LibraryStore` 里加表
 *
 * `LibraryStore` 里已经有收藏/最近/电台三张表，是用户的真实数据。
 * 往里加表就得走 `onUpgrade`，万一写错版本判断，收藏会一起没。
 * 用户对数据安全的要求很明确（改数据前必须能回滚），所以这里新开一个
 * `linkplay_playlist.db`：坏了只丢歌单，收藏毫发无伤，回滚就是删这一个文件。
 *
 * ## 表结构
 *
 * `tb_playlist`       歌单头（名字唯一，`updated_at` 用于排序）
 * `tb_playlist_item`  歌单里的曲目，`position` 就是播放顺序
 *
 * 曲目字段与 `QueueItem` 一一对应，不额外解释（多一层映射就多一处漂移）。
 */
class PlaylistStore private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DB_NAME, null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """create table if not exists tb_playlist(
                 id integer primary key autoincrement,
                 name text not null unique,
                 updated_at long not null)""",
        )
        db.execSQL(
            """create table if not exists tb_playlist_item(
                 id integer primary key autoincrement,
                 list_id long not null,
                 position integer not null,
                 url text not null,
                 title text, artist text, album text,
                 duration_sec long, mime text, album_art_url text)""",
        )
        db.execSQL("create index if not exists idx_item_list on tb_playlist_item(list_id, position)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // 目前只有 v1。以后加列在这里按 oldVersion 增量补，**不要** drop 表。
    }

    // ---- 读 ----

    fun lists(): List<PlaylistOps.Playlist> {
        val out = ArrayList<PlaylistOps.Playlist>()
        readableDatabase.query(
            "tb_playlist", arrayOf("id", "name", "updated_at"),
            null, null, null, null, "updated_at desc",
        ).use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                out += PlaylistOps.Playlist(
                    id = id,
                    name = c.getString(1),
                    tracks = tracks(id),
                    updatedAt = c.getLong(2),
                )
            }
        }
        return out
    }

    fun tracks(listId: Long): List<QueueItem> {
        val out = ArrayList<QueueItem>()
        readableDatabase.query(
            "tb_playlist_item",
            arrayOf("url", "title", "artist", "album", "duration_sec", "mime", "album_art_url"),
            "list_id = ?", arrayOf(listId.toString()), null, null, "position asc",
        ).use { c ->
            while (c.moveToNext()) {
                out += QueueItem(
                    url = c.getString(0).orEmpty(),
                    title = c.getString(1) ?: "未命名",
                    artist = c.getString(2),
                    album = c.getString(3),
                    durationSec = c.getLong(4),
                    mimeType = c.getString(5) ?: "audio/mpeg",
                    albumArtUrl = c.getString(6),
                )
            }
        }
        return out
    }

    // ---- 写 ----

    /** 新建或取同名歌单，返回 id。名字非法（空白）返回 -1。 */
    fun createOrGet(rawName: String, nowMs: Long = System.currentTimeMillis()): Long {
        val name = PlaylistOps.normalizeName(rawName) ?: return -1
        val existing = readableDatabase.query(
            "tb_playlist", arrayOf("id"), "name = ? collate nocase", arrayOf(name), null, null, null,
        ).use { if (it.moveToFirst()) it.getLong(0) else -1L }
        if (existing > 0) return existing
        if (count() >= PlaylistOps.MAX_LISTS) return -1
        val values = ContentValues().apply {
            put("name", name)
            put("updated_at", nowMs)
        }
        return writableDatabase.insert("tb_playlist", null, values)
    }

    fun count(): Int = readableDatabase
        .rawQuery("select count(*) from tb_playlist", null).use { if (it.moveToFirst()) it.getInt(0) else 0 }

    /** 整份覆盖某歌单的曲目（顺序即入参顺序）。 */
    fun replaceTracks(listId: Long, tracks: List<QueueItem>, nowMs: Long = System.currentTimeMillis()) {
        if (listId <= 0) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("tb_playlist_item", "list_id = ?", arrayOf(listId.toString()))
            tracks.take(PlaylistOps.MAX_TRACKS).forEachIndexed { index, t ->
                db.insert(
                    "tb_playlist_item", null,
                    ContentValues().apply {
                        put("list_id", listId)
                        put("position", index)
                        put("url", t.url)
                        put("title", t.title)
                        put("artist", t.artist)
                        put("album", t.album)
                        put("duration_sec", t.durationSec)
                        put("mime", t.mimeType)
                        put("album_art_url", t.albumArtUrl)
                    },
                )
            }
            db.update("tb_playlist", ContentValues().apply { put("updated_at", nowMs) }, "id = ?", arrayOf(listId.toString()))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** 加曲目（按 [PlaylistOps.addTracks] 的规则去重、限长），返回加完后的整份列表。 */
    fun addTracks(listId: Long, adding: List<QueueItem>, at: Int? = null): List<QueueItem> {
        val merged = PlaylistOps.addTracks(tracks(listId), adding, at)
        replaceTracks(listId, merged)
        return merged
    }

    fun addTracks(rawName: String, adding: List<QueueItem>): List<QueueItem> {
        val id = createOrGet(rawName)
        if (id <= 0) return emptyList()
        return addTracks(id, adding)
    }

    fun removeTrackAt(listId: Long, index: Int): List<QueueItem> {
        val merged = PlaylistOps.removeAt(tracks(listId), index)
        replaceTracks(listId, merged)
        return merged
    }

    fun moveTrack(listId: Long, from: Int, to: Int): List<QueueItem> {
        val merged = PlaylistOps.move(tracks(listId), from, to)
        replaceTracks(listId, merged)
        return merged
    }

    fun rename(listId: Long, rawName: String): Boolean {
        val name = PlaylistOps.normalizeName(rawName) ?: return false
        // 重名就等于「合并」，这里直接拒绝，让用户换个名字（避免悄悄吞掉另一个歌单）
        val clash = readableDatabase.query(
            "tb_playlist", arrayOf("id"), "name = ? collate nocase and id <> ?",
            arrayOf(name, listId.toString()), null, null, null,
        ).use { it.moveToFirst() }
        if (clash) return false
        val rows = writableDatabase.update(
            "tb_playlist", ContentValues().apply { put("name", name) }, "id = ?", arrayOf(listId.toString()),
        )
        return rows > 0
    }

    fun delete(listId: Long): Boolean {
        val db = writableDatabase
        db.beginTransaction()
        return try {
            db.delete("tb_playlist_item", "list_id = ?", arrayOf(listId.toString()))
            val rows = db.delete("tb_playlist", "id = ?", arrayOf(listId.toString()))
            db.setTransactionSuccessful()
            rows > 0
        } finally {
            db.endTransaction()
        }
    }

    companion object {
        const val DB_NAME = "linkplay_playlist.db"
        const val DB_VERSION = 1

        @Volatile
        private var instance: PlaylistStore? = null

        fun get(context: Context): PlaylistStore =
            instance ?: synchronized(this) {
                instance ?: PlaylistStore(context).also { instance = it }
            }
    }
}
