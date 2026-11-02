package io.github.laozhudu.linkplay.protocol.player

import io.github.laozhudu.linkplay.protocol.Json
import io.github.laozhudu.linkplay.protocol.JsonValue

/** `getPlayerStatus` 里的播放状态。 */
enum class PlayState(val wire: String) {
    STOP("stop"),
    PLAY("play"),
    LOADING("loading"),
    PAUSE("pause"),
    UNKNOWN("unknown");

    companion object {
        fun from(wire: String?): PlayState =
            entries.firstOrNull { it.wire.equals(wire?.trim(), ignoreCase = true) } ?: UNKNOWN
    }
}

/**
 * `getPlayerStatus` 的返回模型（依据 WiiM 官方 HTTP API 文档 v1.2 §2.3.1）。
 *
 * 注意：这批字段**全部是字符串**（`"vol":"39"`、`"mute":"0"`），不是数字，
 * 解析时必须按字符串再转，直接当 Int 读会拿不到值。
 */
data class PlayerStatus(
    /** 0 = 主机/独立设备，1 = 编组里的从机。 */
    val type: Int?,
    /** 0 立体声 / 1 左 / 2 右。 */
    val channel: Int?,
    /** 输入源/播放模式，见 [modeLabel]。 */
    val mode: Int?,
    val loopMode: Int?,
    val eq: Int?,
    val playState: PlayState,
    /** 播放位置，毫秒。 */
    val positionMs: Long,
    /** 曲目总长，毫秒；直播流常为 0。 */
    val durationMs: Long,
    val volume: Int,
    val mute: Boolean,
    val playListCount: Int?,
    val playListCurrent: Int?,
) {
    val positionSec: Long get() = positionMs / 1000
    val durationSec: Long get() = durationMs / 1000

    val progress: Float
        get() = if (durationMs <= 0) 0f else (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)

    companion object {
        /**
         * 官方文档给出的 mode 取值：
         * 0 无 / 1 AirPlay / 2 第三方 DLNA / 10-19 WiiMu 播放列表（10 默认，11 U 盘，16 TF 卡）/
         * 20-30 保留 / 31 Spotify Connect / 32 TIDAL Connect / 40 AUX-In / 41 蓝牙 /
         * 42 外置存储 / 43 光纤输入 / 50 镜像 / 60 语音留言 / 99 从机。
         */
        fun modeLabel(mode: Int?): String = when (mode) {
            null -> "未知"
            0 -> "空闲"
            1 -> "AirPlay"
            2 -> "DLNA"
            in 10..19 -> "本地播放列表"
            in 20..30 -> "保留"
            31 -> "Spotify Connect"
            32 -> "TIDAL Connect"
            40 -> "AUX-In"
            41 -> "蓝牙"
            42 -> "外置存储"
            43 -> "光纤输入"
            50 -> "镜像"
            60 -> "语音留言"
            99 -> "从机"
            else -> "未知($mode)"
        }

        fun parse(json: String): PlayerStatus {
            val root = Json.parse(json) as? JsonValue.Obj ?: throw IllegalArgumentException("getPlayerStatus 返回非 JSON 对象")
            fun int(key: String): Int? = root.string(key)?.trim()?.toIntOrNull() ?: root.int(key)
            fun long(key: String): Long = root.string(key)?.trim()?.toLongOrNull() ?: (root.int(key)?.toLong() ?: 0L)
            return PlayerStatus(
                type = int("type"),
                channel = int("ch"),
                mode = int("mode"),
                loopMode = int("loop"),
                eq = int("eq"),
                playState = PlayState.from(root.string("status")),
                positionMs = long("curpos"),
                durationMs = long("totlen"),
                volume = int("vol") ?: 0,
                mute = (int("mute") ?: 0) != 0,
                playListCount = int("plicount"),
                playListCurrent = int("plicurr"),
            )
        }
    }
}

/** `getMetaInfo` 的返回模型（官方文档 §2.9）。 */
data class PlayerMetadata(
    val title: String?,
    val artist: String?,
    val album: String?,
    val albumArtUrl: String?,
    val sampleRate: Int?,
    val bitDepth: Int?,
) {
    companion object {
        fun parse(json: String): PlayerMetadata? {
            val root = runCatching { Json.parse(json) }.getOrNull() as? JsonValue.Obj ?: return null
            val meta = root.obj("metaData") ?: root
            // 官方文档里这两个键带尾随空格（"albumArtURI "、"sampleRate "），两种都试
            fun str(vararg keys: String): String? =
                keys.firstNotNullOfOrNull { meta.string(it)?.trim()?.takeIf { v -> v.isNotEmpty() } }
            fun num(vararg keys: String): Int? =
                keys.firstNotNullOfOrNull { meta.string(it)?.trim()?.toIntOrNull() }

            return PlayerMetadata(
                title = str("title"),
                artist = str("artist"),
                album = str("album"),
                albumArtUrl = str("albumArtURI", "albumArtURI ", "albumArtURI\\u0020"),
                sampleRate = num("sampleRate", "sampleRate "),
                bitDepth = num("bitDepth"),
            )
        }
    }
}
