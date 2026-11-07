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
 *
 * ## 缓存里没有时按什么处理（用户 2026-11-07 纠正后改了）
 *
 * 原来是 `DeviceCapabilities.LINKPLAY`（功能全开），理由是"未知设备不乱降级"。
 * 那个理由在**家里只有 DOSS** 时成立，加了一台通用渲染器之后就不成立了：
 *
 * > 「doss 系列是适配通用 upnp 的，但通用 upnp 不一定是 doss，doss 有特殊标识的」
 *
 * 未知 = 可能是一台从没见过的通用设备。这时按 LINKPLAY 处理会：
 * ① 界面上画满组网 / 预设键 / 设备侧队列 —— 全是按了没反应的按钮；
 * ② 起播时尝试把整份列表推给设备（`setPlayerCmd:playlist`）—— 通用设备不认，静默失败。
 *
 * 现在按**通用渲染器**处理（最保守但真能用）。DOSS 探测失败时也只是退化成
 * "逐首投"，声音照样出得来 —— 比"整台设备看起来都不响应"好得多。
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
    fun of(ip: String): DeviceProfile = entries[ip]?.profile ?: DeviceProfile.GENERIC_DMR

    /**
     * **确认**这是 DOSS 档吗（有私有 PlayQueue 且能组网）。
     *
     * 名字里带 Confirmed 是刻意的：区分"我们探测确认过"和"没记录、按默认处理"。
     * 播放页选通道只看这个 —— DOSS 有特殊标识，不是"不是别人就是它"。
     */
    fun isConfirmedDoss(ip: String): Boolean =
        entries[ip]?.capabilities?.group == io.github.laozhudu.linkplay.protocol.dlna.DeviceGroup.MySpeaker

    /**
     * 取能力表。没记录过就返回 [DeviceCapabilities.LINKPLAY] ——
     * 与 [of] 的默认一致：宁可按功能全开处理，也不要把已有设备的设备侧队列关掉。
     */
    fun capabilitiesOf(ip: String): DeviceCapabilities =
        entries[ip]?.capabilities ?: DeviceCapabilities.genericDmr(hasRenderingControl = true)

    /**
     * 写入**弱结论**：靠描述文件推断出来的档位。
     *
     * ## 弱在哪里
     *
     * 「描述里有 AVTransport」只能证明它**能放声音**，
     * **不能证明它没有私有 PlayQueue**。
     *
     * 所以有两种结论强度：
     *
     * | 来源 | 强度 | 能证明什么 |
     * |---|---|---|
     * | `getStatusEx` 答上来了 | **强** | 它就是 DOSS（私有 httpapi 是铁证） |
     * | 只拉到描述 | **弱** | 它有 AVTransport；至于有没有私有服务，说不准 |
     *
     * ## 为什么必须区分（真机踩过）
     *
     * 用户：「我刚加了一个音箱，也认出音箱，为什么没收到音箱系列里」。
     *
     * 时序是这样的：
     *
     * ```
     * t=0   扫描 → 靠私有标识认出是音箱（对）
     * t=1   添加 → 建档，然后立刻重新探一次
     * t=2   新入网的音箱 httpapi 还没起好 → getStatusEx 超时
     * t=2   退到描述文件 → 有 AVTransport → 判成"通用播放设备"
     * t=2   这个弱结论**覆盖**了刚才的正确结论 → 掉进别的组
     * ```
     *
     * 规则：**已经确认过是 DOSS 的，弱结论不许把它降级。**
     * 反过来可以（弱结论升格为强结论），下次探测成功自然纠正。
     */
    fun putWeak(ip: String, profile: DeviceProfile, capabilities: DeviceCapabilities) {
        if (ip.isBlank()) return
        val old = entries[ip]
        val wasConfirmedDoss = old?.capabilities?.group ==
            io.github.laozhudu.linkplay.protocol.dlna.DeviceGroup.MySpeaker
        val nowSaysGeneric = capabilities.group !=
            io.github.laozhudu.linkplay.protocol.dlna.DeviceGroup.MySpeaker
        if (wasConfirmedDoss && nowSaysGeneric) return
        entries[ip] = Entry(profile, capabilities)
    }

    /** 这台设备能不能用 LinkPlay 私有的设备侧队列（PlayQueue）。 */
    fun supportsDeviceQueue(ip: String): Boolean = capabilitiesOf(ip).supportsPlayQueue

    /** 测试用：清空缓存，避免用例之间互相污染。 */
    fun clear() = entries.clear()
}
