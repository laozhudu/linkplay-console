package io.github.laozhudu.linkplay.console.session

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import io.github.laozhudu.linkplay.protocol.session.NasSession
import io.github.laozhudu.linkplay.protocol.session.PlaybackKey
import io.github.laozhudu.linkplay.protocol.session.PlaybackSession

/**
 * 应用级记录的 kind。单独一类而不是塞进 NAS/Playback：
 * 这三类数据生命周期不同，清播放记录时不该把"上次用哪台设备"一起清掉。
 */
private const val KIND_APP = "app"

/**
 * 极简 KV 持久化，专门放「上次的状态」。
 *
 * 为什么不复用 [io.github.laozhudu.linkplay.console.library.LibraryStore]：那个库的表结构
 * 是照原厂 App 抄的（便于导数据），语义是「音乐库内容」，而这里是「界面会话」，
 * 混在一起以后想清空会话就得动业务表。分开一个文件更安全。
 *
 * 为什么不用 DataStore/SharedPreferences：值本身是 JSON（由 core 层负责编解码，
 * 纯 JVM 可测），这里只需要「按 key 存一段文本」，SQLite 一个表足够，也不引入协程依赖。
 */
class SessionStore private constructor(context: Context) : SQLiteOpenHelper(
    context.applicationContext, DB_NAME, null, DB_VERSION,
) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """create table if not exists tb_session(
                 kind text not null,
                 subject text not null,
                 payload text not null,
                 updated_at long not null,
                 primary key(kind, subject))""",
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // 会话数据可以丢（丢了就重新选目录/重新排队），不做迁移
        db.execSQL("drop table if exists tb_session")
        onCreate(db)
    }

    // ---- 应用级会话（上次用的是哪台设备）----

    // ---- NAS 记忆 ----

    /** 存「这台设备上次连的 NAS 和停在哪一层」。subject 用设备 UUID，换 IP 也不会丢。 */
    fun saveNasSession(deviceUuid: String, session: NasSession) =
        put(KIND_NAS, deviceUuid, session.encode())

    fun loadNasSession(deviceUuid: String): NasSession? =
        NasSession.decode(get(KIND_NAS, deviceUuid))

    /** 同一台设备最近一次连过的服务器（不看路径），用于「上次的 NAS」快捷入口。 */
    fun lastNasServer(deviceUuid: String) = loadNasSession(deviceUuid)?.server

    // ---- 断点续播 ----

    fun savePlayback(deviceUuid: String, session: PlaybackSession) =
        put(KIND_PLAYBACK, deviceUuid, session.encode())

    fun loadPlayback(deviceUuid: String): PlaybackSession? =
        PlaybackSession.decode(get(KIND_PLAYBACK, deviceUuid))

    /** 播完/主动清空时调用，避免下次还提示「继续上次」。 */
    fun clearPlayback(deviceUuid: String) = delete(KIND_PLAYBACK, deviceUuid)

    // ---- 记住的音箱 ----

    /**
     * 已保存设备清单。
     *
     * 存成一段 JSON 而不是一张表：设备数量是个位数，而且「顺序 = 最近使用」
     * 本身就是排序语义，用表反而要维护一个 order 字段。
     */
    /**
     * 上次使用的设备键（uuid 优先，否则 `ip:x`）。
     *
     * 为什么需要：用户要求「重开 App 能读到原来的状态」。光恢复播放列表还不够 ——
     * 得先知道**是哪台设备**，否则播放页还是空的，列表无处可挂。
     */
    fun saveLastDevice(uuid: String?, ip: String) = put(KIND_APP, "lastDevice", PlaybackKey.of(uuid, ip))

    fun loadLastDevice(): String? = get(KIND_APP, "lastDevice")

    /**
     * 在线曲库（Subsonic / tt-music）配置。
     *
     * 存在 `app` 这一类里而不是按设备存：一个家庭只有一套曲库，和设备无关。
     * 密码是明文存在本机 SQLite 里 —— 这里如实说明：它本来就是家里内网服务，
     * 且我们不带任何云同步；要做加密得引 Android Keystore，当前不值得。
     */
    fun saveSubsonic(json: String) = put(KIND_APP, KEY_SUBSONIC, json)

    fun loadSubsonic(): String? = get(KIND_APP, KEY_SUBSONIC)

    fun savedDevices(): List<io.github.laozhudu.linkplay.protocol.session.SavedDevice> =
        io.github.laozhudu.linkplay.protocol.session.DeviceList.decode(get(KIND_DEVICES, SUBJECT_ALL))

    fun saveDevices(list: List<io.github.laozhudu.linkplay.protocol.session.SavedDevice>) =
        put(KIND_DEVICES, SUBJECT_ALL, io.github.laozhudu.linkplay.protocol.session.DeviceList.encode(list))

    fun upsertDevice(device: io.github.laozhudu.linkplay.protocol.session.SavedDevice): List<io.github.laozhudu.linkplay.protocol.session.SavedDevice> {
        val next = io.github.laozhudu.linkplay.protocol.session.DeviceList.upsert(savedDevices(), device)
        saveDevices(next)
        return next
    }

    fun removeDevice(uuid: String): List<io.github.laozhudu.linkplay.protocol.session.SavedDevice> {
        val next = io.github.laozhudu.linkplay.protocol.session.DeviceList.remove(savedDevices(), uuid)
        saveDevices(next)
        return next
    }

    // ---- KV 原语 ----

    private fun put(kind: String, subject: String, payload: String) {
        writableDatabase.execSQL(
            "insert or replace into tb_session(kind, subject, payload, updated_at) values(?,?,?,?)",
            arrayOf(kind, subject, payload, System.currentTimeMillis()),
        )
    }

    private fun get(kind: String, subject: String): String? = readableDatabase.query(
        "tb_session", arrayOf("payload"), "kind=? and subject=?", arrayOf(kind, subject), null, null, null,
    ).use { if (it.moveToFirst()) it.getString(0) else null }

    private fun delete(kind: String, subject: String) {
        writableDatabase.delete("tb_session", "kind=? and subject=?", arrayOf(kind, subject))
    }

    companion object {
        private const val DB_NAME = "linkplay-session.db"
        private const val DB_VERSION = 1
        private const val KEY_SUBSONIC = "subsonic"
        private const val KIND_NAS = "nas"
        private const val KIND_PLAYBACK = "playback"
        private const val KIND_DEVICES = "devices"
        private const val SUBJECT_ALL = "all"

        @Volatile
        private var instance: SessionStore? = null

        fun of(context: Context): SessionStore = instance ?: synchronized(this) {
            instance ?: SessionStore(context).also { instance = it }
        }
    }
}
