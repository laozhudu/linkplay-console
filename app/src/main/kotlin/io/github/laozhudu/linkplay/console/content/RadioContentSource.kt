package io.github.laozhudu.linkplay.console.content

import io.github.laozhudu.radiodiscovery.model.RadioStation as DiscoveryStation

/**
 * 网络电台（radio-browser）作为内容源。
 *
 * ## 与本地源的关键差别
 *
 * 电台的地址**必须二次解析**：搜索结果里给的是目录条目，
 * 真正能播的 `url` 可能要再请求一次（而且会拿到更准的 `resolvedUrl`）。
 *
 * 所以 [ContentItem.needsResolve] 对电台恒为 true —— 界面点一下时要先转个圈。
 *
 * ## 可测性
 *
 * 两个依赖都是 lambda，纯 JVM 可测。
 */
class RadioContentSource(
    private val searchStations: suspend (query: String, limit: Int) -> List<DiscoveryStation>,
    private val resolveStation: suspend (DiscoveryStation) -> DiscoveryStation,
) : ContentSource {

    override val id: SourceId get() = SourceId.Radio

    /** 电台目录是公开服务，不需要任何配置。 */
    override fun readiness(): Readiness = Readiness.Ready

    override suspend fun search(query: String, limit: Int): List<ContentItem> {
        if (query.isBlank()) return emptyList()
        return runCatching { searchStations(query, limit) }
            .getOrDefault(emptyList())
            .map { it.toContentItem() }
    }

    override suspend fun resolve(item: ContentItem): Resolved {
        // 搜索结果里可能没有完整条目，用 id 找回：radio-browser 的 stationuuid
        // Radio Browser 的 `id` 就是 stationuuid，跨请求稳定；改地址也认得出来
        val stub = runCatching { searchStations(item.title, 50).firstOrNull { it.id == item.id } }
            .getOrNull()
            ?: return Resolved.Failed("这个电台已经不在目录里了（可能已下线或换了地址）")
        val full = runCatching { resolveStation(stub) }.getOrElse { stub }
        // 解析过一次就拿 resolvedUrl，它比原始 url 更准（改过域名/换了流）
        val url = full.resolvedUrl ?: full.url
        if (url.isBlank()) return Resolved.Failed("这个电台没有可用的流地址")
        return Resolved.Ok(url)
    }

    override fun diagnoseLine(): String = "电台=可用（radio-browser 目录，需要解析一次取流地址）"
}

private fun DiscoveryStation.toContentItem(): ContentItem = ContentItem(
    source = SourceId.Radio,
    id = id,
    title = name.ifBlank { "网络电台" },
    subtitle = listOfNotNull(country, tags.firstOrNull()).firstOrNull(),
    durationSec = null,
    artUrl = favicon,
    needsResolve = true,
)
