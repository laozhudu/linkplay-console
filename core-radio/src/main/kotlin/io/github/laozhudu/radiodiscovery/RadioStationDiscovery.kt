package io.github.laozhudu.radiodiscovery

import io.github.laozhudu.radiodiscovery.client.HttpClient
import io.github.laozhudu.radiodiscovery.client.JdkHttpClient
import io.github.laozhudu.radiodiscovery.client.RadioBrowserClient
import io.github.laozhudu.radiodiscovery.model.RadioStation
import io.github.laozhudu.radiodiscovery.resolver.ResolvedStream
import io.github.laozhudu.radiodiscovery.resolver.StreamResolver
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

/**
 * 广播电台发现与流解析门面。
 *
 * 接入方只需实例化这个类，调用 [search] / [resolveStream]，
 * 再把 [RadioStation] 交给自己的播放器即可。
 *
 * 模块特点：
 * - 纯 Kotlin / JVM，不依赖 Android 框架
 * - 不绑定 OkHttp / Ktor，默认用 JDK HttpURLConnection
 * - 所有网络请求都是 [suspend] 函数，由调用方决定在哪个协程/线程执行
 *
 * 示例：
 * ```kotlin
 * val discovery = RadioStationDiscovery()
 * val stations = discovery.search("chaozhou", 20)
 * val station = stations.firstOrNull() ?: return
 * val resolved = discovery.resolveStream(station)
 * player.play(resolved.resolvedUrl ?: resolved.url)
 * ```
 */
class RadioStationDiscovery(
    private val source: RadioStationSource = defaultRadioStationSource,
    private val resolver: StreamResolver = StreamResolver(),
    private val httpClient: HttpClient = JdkHttpClient(),
) {

    /**
     * 电台数据源接口。默认实现为 [RadioBrowserClient]。
     * 集成方可替换为自己的数据源（如本地数据库、其他电台目录 API）。
     */
    interface RadioStationSource {
        suspend fun searchByName(query: String, limit: Int): List<RadioStation>
        suspend fun searchByPlace(
            country: String,
            state: String?,
            limit: Int,
        ): List<RadioStation>
        suspend fun getPopular(limit: Int): List<RadioStation>
    }

    /**
     * 按名称搜索电台（不解析流 URL，快速返回列表供 UI 展示）。
     */
    suspend fun search(query: String, limit: Int = 30): List<RadioStation> {
        return source.searchByName(query, limit)
    }

    /**
     * 按国家/省份搜索（例如 country="china", state="guangdong"）。
     */
    suspend fun searchByPlace(
        country: String,
        state: String? = null,
        limit: Int = 50,
    ): List<RadioStation> {
        return source.searchByPlace(country, state, limit)
    }

    /**
     * 获取热门电台（适合做“推荐”页）。
     */
    suspend fun getPopular(limit: Int = 50): List<RadioStation> {
        return source.getPopular(limit)
    }

    /**
     * 为指定电台解析真实流地址。
     * 跟随 HTTP 重定向，返回最终可播放的流地址。
     */
    suspend fun resolveStream(station: RadioStation): RadioStation {
        return withContext(Dispatchers.IO) {
            val resolved = resolver.resolve(station.url)
            station.copy(
                resolvedUrl = if (resolved.isDirectStream) resolved.finalUrl else station.url,
            )
        }
    }

    /**
     * 搜索并立即解析第一条结果的真实流地址。
     * 适合“搜一首就播”的场景。
     */
    suspend fun searchAndResolveFirst(query: String, limit: Int = 5): RadioStation? {
        val stations = search(query, limit)
        return stations.firstOrNull()?.let { station ->
            resolveStream(station)
        }
    }
}

/**
 * 让 [RadioBrowserClient] 实现 [RadioStationSource] 接口，
 * 以便作为 [RadioStationDiscovery] 的默认数据源。
 */
@Suppress("unused")
private fun RadioBrowserClient.asRadioStationSource(): RadioStationDiscovery.RadioStationSource =
    object : RadioStationDiscovery.RadioStationSource {
        override suspend fun searchByName(query: String, limit: Int): List<RadioStation> =
            this@asRadioStationSource.searchByName(query, limit)

        override suspend fun searchByPlace(
            country: String,
            state: String?,
            limit: Int,
        ): List<RadioStation> =
            this@asRadioStationSource.searchByPlace(country, state, limit)

        override suspend fun getPopular(limit: Int): List<RadioStation> =
            this@asRadioStationSource.getPopular(limit)
    }

/**
 * 在 [RadioStationDiscovery] 的默认参数位置使用 [RadioBrowserClient] 作为数据源。
 */
internal val defaultRadioStationSource: RadioStationDiscovery.RadioStationSource =
    RadioBrowserClient().asRadioStationSource()
