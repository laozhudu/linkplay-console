package io.github.laozhudu.linkplay.console.library

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import io.github.laozhudu.linkplay.protocol.library.FavoriteStore
import io.github.laozhudu.linkplay.protocol.library.RecentStore
import io.github.laozhudu.linkplay.protocol.library.TrackRecord
import io.github.laozhudu.linkplay.protocol.radio.RadioStation
import io.github.laozhudu.linkplay.protocol.radio.RadioStationStore

/**
 * 本地持久化。
 *
 * 表名**故意沿用原厂 App 的命名**（`tb_music_collection` / `tb_last_played` / `tb_net_radio`），
 * 这样以后想从旧 App 的数据库导数据，字段名能直接对上（见 docs/05 的表结构清单）。
 *
 * 为什么不用 Room：这三张表都是简单 KV 语义，Room 要引 KSP 插件、加编译期依赖，
 * 对一个「只给自己用」的 App 不划算。用 `SQLiteOpenHelper` + 手写 SQL，逻辑集中在 [LibraryStore]。
 */
class LibraryStore private constructor(context: Context) : SQLiteOpenHelper(
    context.applicationContext, DB_NAME, null, DB_VERSION,
) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """create table if not exists tb_music_collection(
                 id integer primary key autoincrement,
                 dedupe_key text unique,
                 playuri text, title text, album text, creator text, artist text,
                 albumarturi text, duration long, folder_name text, source_type text, song_id long)""",
        )
        db.execSQL(
            """create table if not exists tb_last_played(
                 id integer primary key autoincrement,
                 dedupe_key text unique,
                 playuri text, title text, album text, creator text, artist text,
                 albumarturi text, duration long, folder_name text, source_type text,
                 song_id long, first_played_time long, device_ip text)""",
        )
        db.execSQL(
            """create table if not exists tb_net_radio(
                 id integer primary key autoincrement,
                 soundName text, soundUrl text, isKept integer, isPlaying integer,
                 invalid integer, deviceName text)""",
        )
        db.execSQL("create index if not exists idx_last_played_time on tb_last_played(first_played_time desc)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // 这些表都是「可重建的索引数据」，不做迁移，直接重建（收藏会丢，所以只在结构变更时手动升级）
        if (oldVersion < 2) {
            db.execSQL("alter table tb_net_radio add column invalid integer default 0")
        }
        // v3：最近播放要记「当时投给了哪台设备」（定稿「接着听」那一行显示 doss1）。
        // **加列不重建**：tb_last_played 里是用户的历史，drop 掉等于清空他的记忆。
        // 旧行这一列是 null，读的时候降级成"不显示设备名"，不会崩。
        if (oldVersion < 3) {
            db.execSQL("alter table tb_last_played add column device_ip text")
        }
    }

    // ---- favorites ----

    val favorites: FavoriteStore = object : FavoriteStore {
        override fun all(): List<TrackRecord> = readableDatabase.query(
            "tb_music_collection", null, null, null, null, null, "id desc",
        ).use { c -> buildList { while (c.moveToNext()) add(c.toTrack()) } }

        override fun contains(dedupeKey: String): Boolean = readableDatabase.query(
            "tb_music_collection", arrayOf("id"), "dedupe_key=?", arrayOf(dedupeKey), null, null, null,
        ).use { it.moveToFirst() }

        override fun add(track: TrackRecord): Boolean {
            val values = track.toValues(withDedupe = true)
            val row = writableDatabase.insertWithOnConflict(
                "tb_music_collection", null, values, SQLiteDatabase.CONFLICT_IGNORE,
            )
            return row != -1L
        }

        override fun remove(dedupeKey: String): Boolean =
            writableDatabase.delete("tb_music_collection", "dedupe_key=?", arrayOf(dedupeKey)) > 0

        override fun clear() {
            writableDatabase.delete("tb_music_collection", null, null)
        }
    }

    // ---- recents ----

    val recents: RecentStore = object : RecentStore {
        override fun recent(limit: Int): List<TrackRecord> = readableDatabase.query(
            "tb_last_played", null, null, null, null, null,
            "first_played_time desc", limit.toString(),
        ).use { c -> buildList { while (c.moveToNext()) add(c.toTrack(playedAtColumn = "first_played_time")) } }

        override fun recentWithDevice(limit: Int): List<io.github.laozhudu.linkplay.protocol.library.RecentPlay> =
            readableDatabase.query(
                "tb_last_played", null, null, null, null, null,
                "first_played_time desc", limit.toString(),
            ).use { c ->
                buildList {
                    while (c.moveToNext()) {
                        val ipIdx = c.getColumnIndex("device_ip")
                        add(
                            io.github.laozhudu.linkplay.protocol.library.RecentPlay(
                                track = c.toTrack(playedAtColumn = "first_played_time"),
                                deviceIp = if (ipIdx >= 0) c.getString(ipIdx) else null,
                                playedAt = c.getLong(c.getColumnIndexOrThrow("first_played_time")),
                            ),
                        )
                    }
                }
            }

        override fun recordWithDevice(track: TrackRecord, playedAt: Long, deviceIp: String?) {
            val values = track.toValues(withDedupe = true).apply {
                put("first_played_time", playedAt)
                put("device_ip", deviceIp)
            }
            writableDatabase.insertWithOnConflict(
                "tb_last_played", null, values, SQLiteDatabase.CONFLICT_REPLACE,
            )
        }

        override fun record(track: TrackRecord, playedAt: Long) {
            val values = track.toValues(withDedupe = true).apply {
                put("first_played_time", playedAt)
            }
            // 同一首重播只更新时间，不新增行（原厂是每次插一行）
            writableDatabase.insertWithOnConflict(
                "tb_last_played", null, values, SQLiteDatabase.CONFLICT_REPLACE,
            )
        }

        override fun clear() {
            writableDatabase.delete("tb_last_played", null, null)
        }
    }

    // ---- radio ----

    val radios: RadioStationStore = object : RadioStationStore {
        override fun all(): List<RadioStation> = readableDatabase.query(
            "tb_net_radio", null, null, null, null, null, "id asc",
        ).use { c -> buildList { while (c.moveToNext()) add(c.toStation()) } }

        override fun forDevice(deviceName: String?): List<RadioStation> = all()
            .filter { deviceName == null || it.deviceName == null || it.deviceName == deviceName }
            .sortedWith(compareBy({ it.invalid }, { !it.kept }, { it.name.lowercase() }))

        override fun add(station: RadioStation): Long = writableDatabase.insert(
            "tb_net_radio", null, ContentValues().apply {
                put("soundName", station.name)
                put("soundUrl", station.url)
                put("isKept", if (station.kept) 1 else 0)
                put("isPlaying", if (station.playing) 1 else 0)
                put("invalid", if (station.invalid) 1 else 0)
                put("deviceName", station.deviceName)
            },
        )

        override fun remove(id: Long): Boolean =
            writableDatabase.delete("tb_net_radio", "id=?", arrayOf(id.toString())) > 0

        override fun markInvalid(id: Long, invalid: Boolean) = updateFlag(id, "invalid", invalid)

        override fun setPlaying(id: Long, playing: Boolean) = updateFlag(id, "isPlaying", playing)

        private fun updateFlag(id: Long, column: String, value: Boolean) {
            writableDatabase.update(
                "tb_net_radio", ContentValues().apply { put(column, if (value) 1 else 0) },
                "id=?", arrayOf(id.toString()),
            )
        }

        override fun clear() {
            writableDatabase.delete("tb_net_radio", null, null)
        }
    }

    // ---- mapping ----

    private fun TrackRecord.toValues(withDedupe: Boolean) = ContentValues().apply {
        if (withDedupe) put("dedupe_key", dedupeKey)
        put("playuri", playUri)
        put("title", title)
        put("album", album)
        put("creator", creator)
        put("artist", artist)
        put("albumarturi", albumArtUri)
        put("duration", durationSec)
        put("folder_name", folderName)
        put("source_type", sourceType)
        put("song_id", songId)
    }

    private fun Cursor.toTrack(playedAtColumn: String? = null): TrackRecord = TrackRecord(
        playUri = getString(getColumnIndexOrThrow("playuri")),
        title = getString(getColumnIndexOrThrow("title")) ?: "",
        album = getString(getColumnIndexOrThrow("album")),
        creator = getString(getColumnIndexOrThrow("creator")),
        artist = getString(getColumnIndexOrThrow("artist")),
        albumArtUri = getString(getColumnIndexOrThrow("albumarturi")),
        durationSec = getLong(getColumnIndexOrThrow("duration")),
        folderName = getString(getColumnIndexOrThrow("folder_name")),
        sourceType = getString(getColumnIndexOrThrow("source_type")) ?: TrackRecord.SOURCE_LOCAL,
        songId = getLong(getColumnIndexOrThrow("song_id")),
        firstPlayedAt = playedAtColumn?.let { getLong(getColumnIndexOrThrow(it)) } ?: 0,
    )

    private fun Cursor.toStation(): RadioStation = RadioStation(
        id = getLong(getColumnIndexOrThrow("id")),
        name = getString(getColumnIndexOrThrow("soundName")) ?: "",
        url = getString(getColumnIndexOrThrow("soundUrl")) ?: "",
        kept = getInt(getColumnIndexOrThrow("isKept")) != 0,
        playing = getInt(getColumnIndexOrThrow("isPlaying")) != 0,
        invalid = getInt(getColumnIndexOrThrow("invalid")) != 0,
        deviceName = getString(getColumnIndexOrThrow("deviceName")),
    )

    companion object {
        private const val DB_NAME = "linkplay_console.db"
        private const val DB_VERSION = 3

        @Volatile
        private var instance: LibraryStore? = null

        fun get(context: Context): LibraryStore = instance ?: synchronized(this) {
            instance ?: LibraryStore(context).also { instance = it }
        }
    }
}
