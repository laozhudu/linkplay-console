package io.github.laozhudu.linkplay.console.content

/** 内容的来源。界面用它在结果里标"这条来自哪个源"。 */
enum class SourceId(val label: String) {
    /** 手机本地音乐库（MediaStore）。 */
    Local("本地"),

    /** 网络电台（radio-browser）。 */
    Radio("电台"),

    /** NAS / DLNA 媒体服务器。 */
    Nas("NAS"),

    /** 在线曲库（Subsonic / Navidrome）。 */
    Online("在线"),

    /** 洛雪音源脚本（第三方曲库）。 */
    Lx("洛雪"),

    /** B 站音源。**未接入**，先占位（`docs/85` 只有调研）。 */
    Bili("B站"),
}

/**
 * 一条"可搜到、可能可播"的内容。
 *
 * ## 为什么字段这么少
 *
 * 六个源的原始类型完全不同（`LocalTrack` / `DiscoveryStation` / `BrowseEntry` /
 * `SubsonicSong` / `LxTrack`）。要统一就得**只留界面真正要显示的字段**，
 * 其余留在各源内部。
 *
 * 加字段的判据：**界面会不会用**。不会用的不进这里。
 */
data class ContentItem(
    val source: SourceId,
    /** 源内唯一 id（用来找回原始对象）。 */
    val id: String,
    val title: String,
    /** 副标题：歌手 / 码率 / 时长，由各源自己决定放什么。 */
    val subtitle: String? = null,
    val durationSec: Long? = null,
    /** 封面（NAS / 在线能给，电台多数没有）。 */
    val artUrl: String? = null,
    /**
     * 是否还需要一次网络请求才能拿到播放地址。
     *
     * 电台、洛雪、Subsonic 都要二次解析；本地文件要登记到转流服务；
     * NAS 浏览时给的 url 通常已经是直链。
     */
    val needsResolve: Boolean = true,
)

/** 取地址的结局。 */
sealed interface Resolved {

    /** 拿到能直接投的地址。 */
    data class Ok(
        val url: String,
        /** 防盗链要带的头（Referer / Cookie）。没有就是空表。 */
        val headers: Map<String, String> = emptyMap(),
    ) : Resolved

    /**
     * 这条要经过本机转流才能投（本地文件必须登记到 `LocalMediaServer`）。
     *
     * **为什么不在 resolve 里直接登记**：登记要启动/复用前台服务、要占用端口，
     * 那是播放编排的事，不是"取地址"的事。放进来会让这个接口有副作用。
     */
    data class NeedsLocalServer(val path: String) : Resolved

    /** 现在取不到，但要用户做一件事（配服务器 / 装脚本）。 */
    data class NeedSetup(val hint: String) : Resolved

    /** 取失败。 */
    data class Failed(val reason: String) : Resolved
}

/** 这个源现在能不能用。 */
sealed interface Readiness {

    data object Ready : Readiness

    /** 缺配置（服务器地址 / 账号）。 */
    data class NotConfigured(val hint: String) : Readiness

    /** 缺一个前置条件（洛雪没装脚本、没给本地音乐权限）。 */
    data class Missing(val hint: String) : Readiness

    val usable: Boolean get() = this is Ready
}
