package io.github.laozhudu.linkplay.protocol.identity

/**
 * 设备 uuid 的归一化与宽容比较。
 *
 * ## 为什么需要它（真机证据）
 *
 * 同一台音箱在两个接口上给出的 uuid **格式完全不同**：
 * ```
 * getStatusEx.uuid       → FF31F0174FE5C9B2A2ACEAE3                       （24 位）
 * getStatusEx.upnp_uuid  → uuid:FF31F017-4FE5-C9B2-A2AC-EAE3FF31F017     （带前缀 + 连字符，32 位）
 * slave_list[].uuid      → uuid:FF31F017-4FE5-C9B2-A2AC-EAE3FF31F017
 * ```
 *
 * 之前用 `equals(ignoreCase = true)` 直接比，结果组内从机的电量/声道全部匹配不上
 * （表现为「电量有时有有时没有」「声道点了说不在组里」）。
 *
 * ## 32 位那个不是"另一种写法"，是拼出来的
 *
 * 把真机 UDN 去连字符后逐段对齐：
 * ```
 * ff31f0174fe5c9b2a2aceae3 ff31f017
 * └──── 24 位真 uuid ────┘ └ 前 8 位重复 ┘
 * ```
 * 后 8 位正好等于前 8 位，前 24 位正好等于 `getStatusEx.uuid`。
 * 所以 LinkPlay 的 UDN 格式是 **`<uuid24><前8位重复>`**，
 * [canonical] 按这条规则把它还原成 24 位。
 *
 * 标准 GUID（例如 Windows 设备报的 32 位）后 8 位等于前 8 位的概率可忽略，
 * 所以这条规则不会误伤它们 —— 判据是"结构吻合"，不是"长度等于 32 就截断"。
 */
object UuidMatch {

    /** 归一化：去掉 `uuid:` 前缀、连字符、空格等一切非字母数字字符，转小写。 */
    fun key(raw: String?): String =
        raw.orEmpty()
            .removePrefix("uuid:")
            .removePrefix("UUID:")
            .filter { it.isLetterOrDigit() }
            .lowercase()

    /**
     * **规范形式** —— 持久化只能用这个值。
     *
     * 在 [key] 基础上再做一步：把 LinkPlay 的 `<uuid24><前8位重复>` 还原成 24 位。
     * 这是「同一台音箱只有一条记录」的根据；只做 [key] 不够，
     * 因为 24 位与 32 位归一化后仍然是两个不同字符串。
     */
    fun canonical(raw: String?): String {
        val k = key(raw)
        // 结构吻合才截断：32 位、且末 8 位 == 前 8 位
        if (k.length == LINKPLAY_UDN_LEN && k.substring(24) == k.substring(0, 8)) {
            return k.substring(0, 24)
        }
        return k
    }

    /**
     * 两个 uuid 是否指向同一台设备。
     *
     * 判据：规范形式相等，或一方是另一方的前缀（且较短一方不少于 [MIN_PREFIX] 位）。
     * 前缀判据保留着做兜底 —— 万一某个固件拼的不是"前 8 位重复"而是别的尾巴，
     * 查询侧仍然能对上（存储侧则会各自保留一条，这是可接受的降级）。
     *
     * 长度下限防误判：短串（空、`uuid:`、单个 IP 片段）一律不认。
     */
    fun same(a: String?, b: String?): Boolean {
        val ka = canonical(a)
        val kb = canonical(b)
        if (ka.isEmpty() || kb.isEmpty()) return false
        if (ka == kb) return true
        val shorter = if (ka.length <= kb.length) ka else kb
        val longer = if (ka.length <= kb.length) kb else ka
        if (shorter.length < MIN_PREFIX) return false
        return longer.startsWith(shorter)
    }

    /** 在候选集合里找与 [target] 指向同一台设备的那个元素（保留候选侧原始格式）。 */
    fun <T> find(target: String?, candidates: Collection<T>, uuidOf: (T) -> String?): T? =
        candidates.firstOrNull { same(target, uuidOf(it)) }

    private const val MIN_PREFIX = 16
    private const val LINKPLAY_UDN_LEN = 32
}
