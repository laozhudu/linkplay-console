package io.github.laozhudu.linkplay.protocol.library

/**
 * 「我的音乐」页的一行入口。
 *
 * @property key 稳定的标识（`local` / `nas` / `playlists` / `recent` / `online` / `radio`），
 *   UI 用它决定点击后去哪一页，**不要**用 title 匹配（文案会改，key 不会）。
 * @property title 左侧条目名。
 * @property countText 右侧数量文案。数字与单位一起给（`3 首` / `1 个` / `已连接`），
 *   这样 UI 不需要再知道单位规则。
 * @property subtitle 可选的一行说明；只在"空状态需要告诉用户下一步"时给，
 *   有内容时不写说明，避免整页都是小字。
 */
data class MyMusicEntry(
    val key: String,
    val title: String,
    val countText: String,
    val subtitle: String? = null,
)

/**
 * 原厂「我的音乐」页的六行汇总（手机本地 / 家庭音乐共享 / 全部歌单 / 最近播放 / 在线曲库 / 网络电台）。
 *
 * ## 为什么这段逻辑放在 core-protocol 而不是 Compose 里
 *
 * 数量文案（单位、「已连接 / 未配置」、空状态提示）是**产品规则**，跟像素无关。
 * 放纯 JVM 层就能用单测把规则钉死 —— 之前在 UI 里拼字符串，改一次页面就得装机肉眼验，
 * 还出过「0 首」和「暂无」两种说法并存的情况。
 *
 * ## 关于 [build] 的 `favorites`
 *
 * 收藏数目前**不单独占一行**（原厂也没有），传进来是为了给「全部歌单」的
 * 空状态留口子：以后要做「N 首收藏可以攒成歌单」时只改这里，不动调用方。
 * 现在它不参与任何文案，测试里也不用断言它。
 */
object MyMusicSummary {

    const val KEY_LOCAL = "local"
    const val KEY_NAS = "nas"
    const val KEY_PLAYLISTS = "playlists"
    const val KEY_RECENT = "recent"
    const val KEY_ONLINE = "online"
    const val KEY_RADIO = "radio"

    /** 六行的顺序：和原厂一致，本地在最前（最常用），电台最后（最少用）。 */
    fun build(
        localTracks: Int,
        nasServers: Int,
        playlists: Int,
        favorites: Int,
        recents: Int,
        radios: Int,
        onlineConfigured: Boolean,
    ): List<MyMusicEntry> {
        // 数量都是"至少 0"：调用方可能把 -1 之类的哨兵值传进来，负数文案会很难看
        val local = localTracks.coerceAtLeast(0)
        val nas = nasServers.coerceAtLeast(0)
        val lists = playlists.coerceAtLeast(0)
        val recent = recents.coerceAtLeast(0)
        val radio = radios.coerceAtLeast(0)

        return listOf(
            MyMusicEntry(
                key = KEY_LOCAL,
                title = "手机本地",
                countText = "$local 首",
                // 0 首时用户不知道"为什么是 0"：没授权还是真没歌。提示放在行内，不用弹窗打断
                subtitle = if (local == 0) "点进去授权后扫描" else null,
            ),
            MyMusicEntry(
                key = KEY_NAS,
                title = "家庭音乐共享",
                countText = "$nas 个",
            ),
            MyMusicEntry(
                key = KEY_PLAYLISTS,
                title = "全部歌单",
                countText = "$lists 个",
                subtitle = if (lists == 0) "在音乐库里把收藏攒成歌单" else null,
            ),
            MyMusicEntry(
                key = KEY_RECENT,
                title = "最近播放",
                countText = "$recent 首",
            ),
            MyMusicEntry(
                key = KEY_ONLINE,
                title = "在线曲库",
                // 在线曲库的数量要联网问服务器，进页面就问一次太慢；这里只表达"配没配"
                countText = if (onlineConfigured) "已连接" else "未配置",
            ),
            MyMusicEntry(
                key = KEY_RADIO,
                title = "网络电台",
                countText = "$radio 个",
            ),
        )
    }
}
