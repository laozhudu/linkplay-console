package io.github.laozhudu.linkplay.console.playback

import io.github.laozhudu.linkplay.protocol.dlna.DeviceCapabilities
import io.github.laozhudu.linkplay.protocol.dlna.DeviceProfile

/**
 * 设备能力档缓存（按 IP 索引）。
 *
 * ## 为什么需要它
 *
 * 判断一台设备是 LinkPlay 还是通用 DLNA，要读一次 `description.xml`（一次 HTTP GET）。
 * 但"要不要走设备侧 PlayQueue"这个决定发生在**每次起播**（音乐库、在线曲库、NAS、歌单四个入口），
 * 每次都去拉一遍描述文件既慢又没必要 —— 能力档在一台设备的生命周期里几乎不变。
 *
 * 所以：`ConsoleViewModel` 探活时检测一次，写进这里；起播时读缓存。
 * 缓存里没有时**默认按 LinkPlay 处理**（未知设备不乱降级，保持既有行为不变）。
 *
 * ## 为什么不放进 `SavedDevice`
 *
 * 那是持久化数据，改结构要迁移；而能力档是完全可推导的运行期信息，
 * 换了一台同 IP 的设备就该重新判定。放内存最省事也最不会错。
 */
object DeviceProfileCache {

    private val entries = java.util.concurrent.ConcurrentHashMap<String, Entry>()

    private data class Entry(val profile: DeviceProfile, val capabilities: DeviceCapabilities)

    /**
     * 写缓存。
     *
     * 为什么要一起存 capabilities：起播路径问的是"能不能走设备侧 PlayQueue"，
     * 那**是能力**不是类型。只存 profile 的话，这里又得写一遍
     * `profile != GENERIC_DMR` 的推断 —— 同一个判据两处维护，必然漂移。
     */
    fun put(ip: String, profile: DeviceProfile, capabilities: DeviceCapabilities) {
        if (ip.isBlank()) return
        entries[ip] = Entry(profile, capabilities)
    }

    /**
     * 取能力档。没记录过就返回 [DeviceProfile.LINKPLAY] —— 见类注释，宁可按"功能全开"处理，
     * 也不要因为一次没探测到就把已有的设备侧队列关掉。
     */
    fun of(ip: String): DeviceProfile = entries[ip]?.profile ?: DeviceProfile.LINKPLAY

    /**
     * 取能力表。没记录过就返回 [DeviceCapabilities.LINKPLAY] ——
     * 与 [of] 的默认一致：宁可按功能全开处理，也不要把已有设备的设备侧队列关掉。
     */
    fun capabilitiesOf(ip: String): DeviceCapabilities =
        entries[ip]?.capabilities ?: DeviceCapabilities.LINKPLAY

    /** 这台设备能不能用 LinkPlay 私有的设备侧队列（PlayQueue）。 */
    fun supportsDeviceQueue(ip: String): Boolean = capabilitiesOf(ip).supportsPlayQueue

    /** 测试用：清空缓存，避免用例之间互相污染。 */
    fun clear() = entries.clear()
}
