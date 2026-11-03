package io.github.laozhudu.linkplay.protocol.session

/**
 * 播放快照的存储键。
 *
 * 为什么需要统一：之前 NAS 用 `uuid ?: ip`、服务用 `uuid.ifBlank { ip }`、
 * 音乐库拿 **SSID** 当键 —— 三处不一致，于是"重开 App 恢复上次播放"必然读不到，
 * 因为存的键和读的键根本不是一个。
 *
 * 规则：有 uuid 就用 uuid（换 IP 也不丢），没有就用 `ip:<地址>` 兜底。
 */
object PlaybackKey {
    fun of(uuid: String?, ip: String): String =
        uuid?.takeIf { it.isNotBlank() } ?: "ip:$ip"

    /** 恢复时按优先级尝试的候选键：uuid 优先，其次 IP。 */
    fun candidates(uuid: String?, ip: String): List<String> =
        listOfNotNull(uuid?.takeIf { it.isNotBlank() }, "ip:$ip").distinct()
}
