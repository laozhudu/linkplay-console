package io.github.laozhudu.linkplay.protocol.identity

/**
 * 设备 uuid 的宽容比较。
 *
 * ## 为什么需要它（真机证据）
 *
 * 同一台音箱在两个接口上给出的 uuid **格式完全不同**：
 * ```
 * getStatusEx.uuid          → FF31F0174FE5C9B2A2ACEAE3            （24 位，无连字符）
 * getStatusEx.upnp_uuid     → uuid:FF31F017-4FE5-C9B2-A2AC-EAE3FF31F017  （带前缀 + 连字符，且末段多了一截）
 * slave_list[].uuid         → uuid:FF31F017-4FE5-C9B2-A2AC-EAE3FF31F017
 * ```
 *
 * 我们之前用 `it.uuid.equals(saved.uuid, ignoreCase = true)` 直接比字符串，
 * 结果**组内从机的电量、声道全部匹配不上**（表现为「电量有时有有时没有」「声道点了说不在组里」）。
 * 这类比较只能按「去掉所有非十六进制字符后互相是前缀」来判，
 * 而不是按某一种"规范格式"——设备的两种格式本来就不是同一个字符串。
 *
 * 只做归一化 + 前缀判定，不做任何推测性映射：真值以设备回报为准。
 */
object UuidMatch {

    /** 归一化：去掉 `uuid:` 前缀、连字符、空格等一切非十六进制字符，转小写。 */
    fun key(raw: String?): String =
        raw.orEmpty()
            .removePrefix("uuid:")
            .removePrefix("UUID:")
            .filter { it.isLetterOrDigit() }
            .lowercase()

    /**
     * 两个 uuid 是否指向同一台设备。
     *
     * 判据：归一化后相等，或一方是另一方的前缀（且较短一方不少于 [MIN_PREFIX] 位）。
     * 为什么允许前缀：设备的 UPnP 形式会在 24 位之后再多拼一段，
     * 而 `getStatusEx` 只给前 24 位 —— 两者只能靠前缀对上。
     *
     * 长度下限防误判：真实 uuid 至少 16 位，短串（空、`uuid:`、单个 IP 片段）一律不认。
     */
    fun same(a: String?, b: String?): Boolean {
        val ka = key(a)
        val kb = key(b)
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
}
