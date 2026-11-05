package io.github.laozhudu.upnp

/**
 * 通用渲染器的状态快照 —— **只有标准 UPnP 能问到的东西**。
 *
 * 刻意不含：电量、固件、组网从机、声道。那些是 LinkPlay 私有（`ControlDeviceInfo`）才有的，
 * 放到这里会让通用设备永远"缺一半字段"，界面反而要写两套判断。
 */
data class RendererState(
    /** `PLAYING` / `PAUSED_PLAYBACK` / `STOPPED` / `NO_MEDIA_PRESENT` / `TRANSITIONING`。 */
    val transportState: String? = null,
    /** 当前位置（毫秒）。设备不给就是 null —— **不要用 0 冒充"从头开始"**。 */
    val positionMs: Long? = null,
    /** 总时长（毫秒）。直播流通常没有。 */
    val durationMs: Long? = null,
    val volume: Int? = null,
    val mute: Boolean? = null,
    /** 当前曲名（GetPositionInfo 的 TrackMetaData 里，可能为空）。 */
    val title: String? = null,
) {
    val playing: Boolean get() = transportState == "PLAYING"
    val hasMedia: Boolean get() = transportState != null && transportState != "NO_MEDIA_PRESENT"

    companion object {
        /**
         * UPnP 的时间格式是 `H:MM:SS` 或 `H:MM:SS.mmm`，也可能是 `NOT_IMPLEMENTED`。
         *
         * **解析不了返回 null**，不返回 0：暂停时很多设备回一个巨大的垃圾值
         * （真机上见过 28242665ms），照抄到界面就是"位置 7.8 小时"。
         */
        fun parseTime(text: String?): Long? {
            val raw = text?.trim() ?: return null
            if (raw.isEmpty() || raw.equals("NOT_IMPLEMENTED", ignoreCase = true)) return null
            val parts = raw.split(':')
            if (parts.size != 3) return null
            val h = parts[0].toLongOrNull() ?: return null
            val m = parts[1].toLongOrNull() ?: return null
            val s = parts[2].substringBefore('.').toLongOrNull() ?: return null
            // 毫秒别丢：进度条按秒跳会看起来"卡住"
            val ms = parts[2].substringAfter('.', "").take(3).padEnd(3, '0').toLongOrNull() ?: 0L
            if (h < 0 || m !in 0..59 || s !in 0..59) return null
            val total = ((h * 60 + m) * 60 + s) * 1000 + ms
            // 超过 24 小时的"位置"一定不是真时长（真机上的垃圾值就是这种）
            return if (total > 24L * 3600 * 1000) null else total
        }
    }
}
