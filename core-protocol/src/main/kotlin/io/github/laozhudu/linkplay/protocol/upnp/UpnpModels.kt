package io.github.laozhudu.linkplay.protocol.upnp

/** AVTransport 的传输状态（UPnP 标准枚举值）。 */
enum class TransportState(val wire: String) {
    PLAYING("PLAYING"),
    PAUSED_PLAYBACK("PAUSED_PLAYBACK"),
    PAUSED_RECORDING("PAUSED_RECORDING"),
    STOPPED("STOPPED"),
    NO_MEDIA_PRESENT("NO_MEDIA_PRESENT"),
    TRANSITIONING("TRANSITIONING"),
    RECORDING("RECORDING"),
    UNKNOWN("UNKNOWN");

    val isPlaying: Boolean get() = this == PLAYING

    companion object {
        fun from(wire: String?): TransportState =
            entries.firstOrNull { it.wire.equals(wire?.trim(), ignoreCase = true) } ?: UNKNOWN
    }
}

/** `GetTransportInfo` 的结果。 */
data class TransportInfo(
    val state: TransportState,
    val status: String?,
    val speed: String?,
) {
    companion object {
        fun parse(xml: String): TransportInfo = TransportInfo(
            state = TransportState.from(UpnpXml.value(xml, "CurrentTransportState")),
            status = UpnpXml.value(xml, "CurrentTransportStatus")?.trim(),
            speed = UpnpXml.value(xml, "CurrentSpeed")?.trim(),
        )
    }
}

/** DIDL-Lite 里我们关心的那点信息。 */
data class TrackMetadata(
    val title: String?,
    val artist: String?,
    val album: String?,
    /** `res` 上的 protocolInfo，决定 renderer 是否接受这个流。 */
    val protocolInfo: String?,
    /** `res` 的文本内容（实际媒体 URL）。 */
    val resUrl: String?,
)

/**
 * `GetPositionInfo` 的结果。
 *
 * `RelTime` / `TrackDuration` 是 `H:MM:SS` 文本（老固件偶尔给 `HH:MM:SS`），
 * 解析为秒；`00:00:00` 与空串都按 0 处理，不代表"未知"——设备无法区分这两者。
 */
data class PositionInfo(
    val track: Int?,
    val trackDurationSec: Long,
    val relTimeSec: Long,
    val trackUri: String?,
    val trackMetadata: TrackMetadata?,
    val transportState: TransportState,
) {
    val progress: Float
        get() = if (trackDurationSec <= 0) 0f
        else (relTimeSec.toFloat() / trackDurationSec.toFloat()).coerceIn(0f, 1f)

    companion object {
        fun parse(xml: String): PositionInfo {
            val duration = parseTime(UpnpXml.value(xml, "TrackDuration"))
            val rel = parseTime(UpnpXml.value(xml, "RelTime"))
            val metadataXml = UpnpXml.value(xml, "TrackMetaData").orEmpty()
            return PositionInfo(
                track = UpnpXml.value(xml, "Track")?.trim()?.toIntOrNull(),
                trackDurationSec = duration,
                relTimeSec = rel,
                trackUri = UpnpXml.value(xml, "TrackURI")?.trim()?.takeIf { it.isNotEmpty() && it != "NOT_IMPLEMENTED" },
                trackMetadata = parseMetadata(metadataXml),
                transportState = TransportState.from(UpnpXml.value(xml, "CurrentTransportState")),
            )
        }

        fun parseMetadata(didl: String): TrackMetadata? {
            if (didl.isBlank()) return null
            // res 带属性，先取属性串再取文本
            val resBlock = UpnpXml.value(didl, "res")
            val protocolInfo = PROTOCOL_INFO.find(didl)?.groupValues?.get(1)
            return TrackMetadata(
                title = UpnpXml.value(didl, "dc:title")?.trim()?.takeIf { it.isNotEmpty() },
                artist = UpnpXml.value(didl, "upnp:artist")?.trim()?.takeIf { it.isNotEmpty() },
                album = UpnpXml.value(didl, "upnp:album")?.trim()?.takeIf { it.isNotEmpty() },
                protocolInfo = protocolInfo,
                resUrl = resBlock?.trim()?.takeIf { it.isNotEmpty() },
            )
        }

        private val PROTOCOL_INFO = Regex("""protocolInfo\s*=\s*"([^"]*)"""")

        /** `H:MM:SS` / `HH:MM:SS` / `MM:SS` → 秒；无法解析返回 0。 */
        fun parseTime(raw: String?): Long {
            val text = raw?.trim().orEmpty()
            if (text.isEmpty()) return 0
            val parts = text.split(':')
            if (parts.size < 2 || parts.size > 3) return 0
            val nums = parts.map { it.trim().toLongOrNull() ?: return 0 }
            return when (nums.size) {
                2 -> nums[0] * 60 + nums[1]
                else -> nums[0] * 3600 + nums[1] * 60 + nums[2]
            }
        }

        fun formatTime(seconds: Long): String {
            val s = seconds.coerceAtLeast(0)
            val h = s / 3600
            val m = (s % 3600) / 60
            val sec = s % 60
            return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
        }
    }
}

/** `GetVolume` 的结果。 */
data class VolumeInfo(val volume: Int, val mute: Boolean) {
    companion object {
        fun parse(xml: String): VolumeInfo = VolumeInfo(
            volume = UpnpXml.value(xml, "CurrentVolume")?.trim()?.toIntOrNull() ?: 0,
            mute = UpnpXml.value(xml, "CurrentMute")?.trim()?.let { it == "1" || it.equals("true", true) } ?: false,
        )
    }
}
