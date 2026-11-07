package io.github.laozhudu.linkplay.protocol.session

import io.github.laozhudu.linkplay.protocol.Json
import io.github.laozhudu.linkplay.protocol.JsonValue
import io.github.laozhudu.linkplay.protocol.JsonWriter
import io.github.laozhudu.linkplay.protocol.dlna.DeviceProfile
import io.github.laozhudu.linkplay.protocol.identity.UuidMatch

/**
 * 一台被记住的音箱。
 *
 * 为什么要存而不是每次搜：SSDP 组播在部分路由器/手机上收不到（真机调试期就遇到过），
 * 而且「打开 App → 等 3 秒扫描 → 点设备」这三步对每天要用的人来说是纯浪费。
 * 记住之后首页一眼就能点开。
 *
 * 键用 **uuid** 而不是 ip：真机从 `.164` 漂到 `.153` 是实测过的（DHCP 续租），
 * 用 IP 当键会让记录直接失效。
 *
 * ## 为什么构造函数是 private（这条改动的全部理由）
 *
 * 同一台音箱曾在三处被"造"过，各自归一化一遍：
 * ```
 * 手动填 IP    → found.udn?.removePrefix("uuid:")                       → 32 位带连字符
 * 扫描点添加   → device.uuid?.substringAfterLast(':')?.removePrefix(…)  → 32 位带连字符
 * 组内从机     → sl.uuid?.removePrefix("uuid:")                         → 同上
 * 而 getStatusEx 给的是 FF31F0174FE5C9B2A2ACEAE3                        → 24 位无连字符
 * ```
 * 于是**同一台音箱会被存成两条记录**：`DeviceList.upsert` 按 uuid 去重，两条都留下；
 * `SessionStore` 用 deviceUuid 当 subject，播放快照/NAS 记忆也各存一份。
 * 首页出现两台同名设备就是这么来的。
 *
 * 之前只在**查询**侧加了 [UuidMatch]（所以电量/声道能对上），**存储**侧没动 ——
 * 典型的"只修症状没修源头"。现在把构造函数收起来，只有 [of] 能造，
 * 归一化只在那一个地方做，**编译期**就堵死了三套写法。
 */
data class SavedDevice private constructor(
    val uuid: String,
    val name: String,
    /** 最近一次成功通信时的 IP。可能是旧的，用前要探活。 */
    val ip: String,
    /** UPnP 描述地址（`http://ip:49152/description.xml`）。手动输 IP 添加的设备为空。 */
    val location: String? = null,
    val modelName: String? = null,
    val lastSeenMs: Long = 0L,
    /**
     * **这台设备是什么东西** —— 跟着设备档案走，持久化。
     *
     * ## 为什么必须持久化（用户 2026-11-07 的真机问题）
     *
     * 之前这个判断只活在内存缓存（`DeviceProfileCache`）里，重启就归零。
     * 于是每次冷启动，所有设备都处在"还没识别"的状态，界面只能拿一个
     * **默认值**去分组 —— 默认给 DOSS，一台电视盒子就被当成音箱；
     * 默认给通用，三台音箱就全变成"通用播放设备"。
     *
     * 我在那个默认值上来回改过两次，两次都错。根本原因是**位置错了**：
     * 设备的类型是设备的属性，不是缓存的属性。
     *
     * 现在它跟 `ip` / `name` 一样存在档案里：识别一次，永久有效。
     */
    val profile: DeviceProfile = DeviceProfile.UNKNOWN,
) {
    /** 展示用：名字 + IP。名字为空时退回 IP，避免出现空标题。 */
    val title: String get() = name.ifBlank { ip }

    fun encode(): String = JsonWriter.encode(
        JsonValue.Obj(
            buildMap {
                put("uuid", JsonValue.Str(uuid))
                put("name", JsonValue.Str(name))
                put("ip", JsonValue.Str(ip))
                location?.let { put("location", JsonValue.Str(it)) }
                modelName?.let { put("model", JsonValue.Str(it)) }
                put("seen", JsonValue.Num(lastSeenMs.toDouble()))
                put("profile", JsonValue.Str(profile.name))
            },
        ),
    )

    companion object {
        /**
         * **全项目唯一的构造入口**。
         *
         * @param rawId 设备给的原始标识（`getStatusEx.uuid` / UDN / `slave_list[].uuid` 都行），
         *   格式千奇百怪，这里统一过 [UuidMatch.key]。
         *   为空时退回 IP —— 少数通用渲染器没有 uuid，只能这样；
         *   但这至少是一条**确定的**降级规则，不是各处各写一遍。
         */
        fun of(
            rawId: String?,
            ip: String,
            name: String,
            location: String? = null,
            modelName: String? = null,
            lastSeenMs: Long = System.currentTimeMillis(),
            profile: DeviceProfile = DeviceProfile.UNKNOWN,
        ): SavedDevice {
            // canonical 而不是 key：LinkPlay 的 UDN 是 <uuid24><前8位重复>，
            // 只去连字符会得到 32 位，与 getStatusEx 的 24 位仍是两个字符串（见 UuidMatch）
            val normalized = UuidMatch.canonical(rawId).ifBlank { ip.trim() }
            return SavedDevice(
                uuid = normalized,
                // 名字是用户设的，只去首尾空白，不动中间
                name = name.trim(),
                ip = ip.trim(),
                location = location,
                modelName = modelName,
                lastSeenMs = lastSeenMs,
                profile = profile,
            )
        }

        /**
         * 从持久化 JSON 还原。
         *
         * 走 [of] 而不是直接构造：**老版本存下来的脏数据也要被归一化** ——
         * 用户不该因为升级了一次 App 就得手动删掉重加设备。
         */
        /**
         * 档案里没有 `profile` 字段时按**音箱**补。
         *
         * ## 这不是"默认值赌一边"，是一条有依据的迁移规则
         *
         * 在这个字段出现之前，**App 根本没有"添加通用设备"这条路** ——
         * 扫描只认 LinkPlay（`discoverMediaRenderers` 是后来才加的），
         * 手动填 IP 也只试 `:49152/description.xml`（LinkPlay 的固定路径）。
         *
         * 所以老数据里的每一台设备**必然**是音箱。这不是猜，是版本事实。
         *
         * 反过来说：**如果这是个默认值而不是迁移规则，那它就错了** ——
         * 因为新添加的通用设备一定会被误导。所以这段只作用于"字段缺失"，
         * 不作用于"字段为空/UNKNOWN"（那是真的未知，等探测）。
         */
        private fun decodeProfile(raw: String?): DeviceProfile {
            if (raw.isNullOrBlank()) return DeviceProfile.LINKPLAY
            return runCatching { DeviceProfile.valueOf(raw) }.getOrDefault(DeviceProfile.UNKNOWN)
        }

        fun decode(text: String?): SavedDevice? {
            if (text.isNullOrBlank()) return null
            return runCatching {
                val o = Json.parse(text) as? JsonValue.Obj ?: return null
                val ip = o.string("ip")?.takeIf { it.isNotBlank() } ?: return null
                of(
                    rawId = o.string("uuid"),
                    ip = ip,
                    name = o.string("name").orEmpty(),
                    location = o.string("location"),
                    modelName = o.string("model"),
                    lastSeenMs = (o["seen"] as? JsonValue.Num)?.value?.toLong() ?: 0L,
                    profile = decodeProfile(o.string("profile")),
                )
            }.getOrNull()
        }
    }
}
