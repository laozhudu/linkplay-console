package io.github.laozhudu.linkplay.console.content

import io.github.laozhudu.linkplay.console.library.LocalTrack

/**
 * 本地音乐库（MediaStore）作为内容源。
 *
 * ## 为什么 `resolve` 返回 [Resolved.NeedsLocalServer] 而不是地址
 *
 * 本地文件不能被音箱直接取 —— 必须先由 `LocalMediaServer` 把文件转成 HTTP 才能投。
 * 而"启动转流服务"是有副作用的（占端口、起前台服务），**不属于"取地址"**。
 *
 * 所以这里只把**文件路径**交出去，由播放编排决定怎么服务它。
 * 早期版本在这里直接拼 `http://127.0.0.1:PORT/t/ID` 是错的 —— 端口那时还没起。
 *
 * ## 可测性
 *
 * 依赖是 `queryAll` 一个 lambda，不是直接调 `LocalLibrary.query(context)`，
 * 所以这个类能跑纯 JVM 单测（权限分支、空库分支都该被锁住）。
 */
class LocalContentSource(
    private val queryAll: () -> List<LocalTrack>,
    /** 有没有读本地音乐的权限。没有时界面要提示去授权，而不是显示空列表。 */
    private val hasPermission: () -> Boolean,
) : ContentSource {

    override val id: SourceId get() = SourceId.Local

    override fun readiness(): Readiness =
        if (hasPermission()) Readiness.Ready
        else Readiness.Missing("需要「音乐和音频」权限才能读本地音乐")

    override suspend fun search(query: String, limit: Int): List<ContentItem> {
        if (!hasPermission()) return emptyList()
        if (query.isBlank()) return emptyList()
        val q = query.trim()
        return runCatching {
            queryAll()
                .filter { t ->
                    // 标题 / 歌手 / 专辑任一命中即可（本地库通常不大，内存过滤够用）
                    t.title.contains(q, ignoreCase = true) ||
                        t.artist?.contains(q, ignoreCase = true) == true ||
                        t.album?.contains(q, ignoreCase = true) == true
                }
                .take(limit)
                .map { it.toContentItem() }
        }.getOrDefault(emptyList())
    }

    override suspend fun resolve(item: ContentItem): Resolved {
        val track = runCatching { queryAll().firstOrNull { it.id.toString() == item.id } }.getOrNull()
            ?: return Resolved.Failed("这条本地记录已经找不到了（库可能被重建过）")
        // 交给编排层决定怎么服务这个文件
        return Resolved.NeedsLocalServer(track.path)
    }

    override fun diagnoseLine(): String = buildString {
        append("本地=")
        append(if (hasPermission()) "有权限" else "无权限")
        val count = runCatching { queryAll().size }.getOrDefault(0)
        append("  曲目=").append(count)
    }
}

private fun LocalTrack.toContentItem(): ContentItem = ContentItem(
    source = SourceId.Local,
    id = id.toString(),
    title = title,
    subtitle = listOfNotNull(artist, album).firstOrNull { it.isNotBlank() },
    durationSec = durationSec,
    artUrl = null,
    needsResolve = true,
)
