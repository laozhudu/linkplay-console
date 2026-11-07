package io.github.laozhudu.linkplay.console.playback

import io.github.laozhudu.linkplay.protocol.dlna.DeviceCapabilities
import io.github.laozhudu.linkplay.protocol.dlna.DeviceProfile
import io.github.laozhudu.linkplay.protocol.dlna.capabilitiesOfProfile
import io.github.laozhudu.linkplay.protocol.session.SavedDevice

/**
 * 设备档位的**运行时镜像**。
 *
 * ## 唯一真相在哪
 *
 * 在 [SavedDevice.profile] —— **持久化在设备档案里**。这个对象只是它的内存副本，
 * 为的是避免每次起播/每次重组都去读一次 SQLite。
 *
 * ## 为什么从"全靠内存"改成"档案为准"
 *
 * 用户 2026-11-07 的真机问题：「我刚加了一个音箱，也认出音箱，为什么没收到音箱系列里」，
 * 接着是「不对，现在识别完全错误了」。
 *
 * 两次都是同一个根因：**档位只活在内存里，重启就归零**。
 * 归零之后界面只能拿**默认值**分组，于是：
 *
 * | 默认值 | 后果 |
 * |---|---|
 * | DOSS | 电视盒子被当成音箱，画满按不动的组网/预设/队列 |
 * | 通用 | **三台音箱全变成"通用播放设备"**，组网、设备侧队列、切歌全没 |
 *
 * 我在这个默认值上来回改过两次，两次都在真机上翻车。
 * 根本原因不是选哪边，而是**位置错了**：设备类型是设备的属性，不是缓存的属性。
 *
 * 现在：[加载档案] → [prime 预热] → 缓存**永远有值** → 没有"默认值"这回事。
 *
 * ## 关于 UNKNOWN
 *
 * 只有两种设备会是 UNKNOWN：
 * ① 刚手动填 IP、还没探完的；
 * ② 老版本 App 存的档案（那些存量数据由 [SavedDevice.decodeProfile] 的迁移规则补）。
 *
 * 对这两种，给 `DeviceCapabilities.NONE` —— 界面只显示"离线/未知"，
 * **不画任何按不动的按钮**。这是诚实，不是保守。
 */
object DeviceProfileCache {

    private val entries = java.util.concurrent.ConcurrentHashMap<String, Entry>()

    private data class Entry(val profile: DeviceProfile, val capabilities: DeviceCapabilities)

    /**
     * 用设备档案**预热**缓存（启动、以及每次档案变更后调用）。
     *
     * 用 `putIfAbsent`：本轮已经探到的精确结果不应该被档案里的旧值覆盖。
     * （档案是"上次的结论"，探测是"刚才的事实"，后者更可信。）
     */
    fun prime(devices: List<SavedDevice>) {
        devices.forEach { d ->
            if (d.ip.isBlank()) return@forEach
            entries.putIfAbsent(d.ip, Entry(d.profile, capabilitiesOfProfile(d.profile)))
        }
    }

    /**
     * 写入本轮探测的结论。
     *
     * **调用方要同时把档位写回 [SavedDevice.profile]** —— 那是唯一真相。
     * 只写这里的话，重启后又回到"未识别"。
     */
    fun put(ip: String, profile: DeviceProfile, capabilities: DeviceCapabilities) {
        if (ip.isBlank()) return
        entries[ip] = Entry(profile, capabilities)
    }

    /**
     * 取档位。
     *
     * 未命中 = **真的不知道**（没预热过也没探过），返回 [DeviceProfile.UNKNOWN]。
     * 这里刻意不给任何"偏向某一档"的默认 —— 那种默认就是前两次翻车的来源。
     */
    fun of(ip: String): DeviceProfile = entries[ip]?.profile ?: DeviceProfile.UNKNOWN

    /** 取能力表。未命中时按 [DeviceProfile.UNKNOWN] 派生（= NONE，什么都不给）。 */
    fun capabilitiesOf(ip: String): DeviceCapabilities =
        entries[ip]?.capabilities ?: capabilitiesOfProfile(of(ip))

    /** 这台设备能不能用 LinkPlay 私有的设备侧队列（PlayQueue）。 */
    fun supportsDeviceQueue(ip: String): Boolean = capabilitiesOf(ip).supportsPlayQueue

    /** 确认这是 DOSS 档吗（有私有 PlayQueue 且能组网）。 */
    fun isConfirmedDoss(ip: String): Boolean =
        capabilitiesOf(ip).group == io.github.laozhudu.linkplay.protocol.dlna.DeviceGroup.MySpeaker

    /** 测试用：清空缓存，避免用例之间互相污染。 */
    fun clear() = entries.clear()
}
