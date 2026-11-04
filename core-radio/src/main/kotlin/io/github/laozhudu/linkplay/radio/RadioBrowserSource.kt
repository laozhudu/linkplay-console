package io.github.laozhudu.linkplay.radio

import io.github.laozhudu.radiodiscovery.RadioStationDiscovery
import io.github.laozhudu.radiodiscovery.client.HttpClient
import io.github.laozhudu.radiodiscovery.client.JdkHttpClient
import io.github.laozhudu.radiodiscovery.client.RadioBrowserClient
import io.github.laozhudu.radiodiscovery.client.RadioBrowserClient.Companion.DEFAULT_BASE_URL
import io.github.laozhudu.radiodiscovery.model.RadioStation as DiscoveryStation

/**
 * 「从目录里找电台」的唯一入口。
 *
 * ## 它做什么
 *
 * 把 [RadioStationDiscovery] 包一层，对外只暴露本项目需要的三件事：
 * 按名字搜、按国家代码找、看热门。
 *
 * ## 它不做什么
 *
 * **不负责收藏**。收藏仍然是 `core-protocol` 的 `RadioStationStore` ——
 * 那边存的是"用户自己的台"（带 `kept` / `playing` / `invalid` / `deviceName`），
 * 这边给的是"目录里查到的候选"（带 `codec` / `isHls` / `votes`）。
 * 两者语义不同，所以是两套模型，靠 [toFavourite] 转换，不合并。
 *
 * ## 为什么默认走 `countrycode`
 *
 * Radio Browser 的国家名过滤**大小写敏感**：`country=china` 返回 0 条、
 * `country=China` 返回 100 条（2026-11-04 实测）。传小写会静默拿到空列表，
 * 界面上看起来像"这个国家没有电台"。所以默认用不区分大小写的 `countrycode`。
 */
class RadioBrowserSource(
    private val discovery: RadioStationDiscovery = RadioStationDiscovery(),
    baseUrl: String = DEFAULT_BASE_URL,
    httpClient: HttpClient = JdkHttpClient(),
) {

    // 单独建一个 client 是为了让"按国家代码"走 countrycode 而不是 country
    private val client = RadioBrowserClient(httpClient = httpClient, baseUrl = baseUrl)

    /**
     * 按名字搜（中英文都行：`"中国之声"`、`"chaozhou"`、`"jazz"`）。
     *
     * 这是**不解析流地址**的快速搜索，供列表展示用。
     * 要真的播，先调 [resolve]，或者直接用 [searchAndResolve]。
     */
    suspend fun search(query: String, limit: Int = 30): List<DiscoveryStation> =
        discovery.search(query, limit)

    /**
     * 按国家代码找（`CN` / `US` / `JP`），大小写不敏感。
     *
     * 推荐用这个而不是按国家名，见类注释。
     */
    suspend fun searchByCountryCode(countryCode: String, limit: Int = 50): List<DiscoveryStation> =
        client.searchByCountryCode(countryCode, limit)

    /** 热门电台，适合做"推荐"页。 */
    suspend fun popular(limit: Int = 50): List<DiscoveryStation> =
        discovery.getPopular(limit)

    /**
     * 解析真实流地址（跟随 302，把中间页换成真正的 `.mp3` / `.m3u8`）。
     *
     * 返回的 `resolvedUrl` 非空时优先用它，否则用原始 `url`。
     */
    suspend fun resolve(station: DiscoveryStation): DiscoveryStation =
        discovery.resolveStream(station)
}
