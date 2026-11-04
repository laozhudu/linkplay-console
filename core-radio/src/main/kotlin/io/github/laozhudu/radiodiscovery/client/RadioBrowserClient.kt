package io.github.laozhudu.radiodiscovery.client

import io.github.laozhudu.radiodiscovery.model.RadioStation
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.net.URLEncoder

/**
 * [Radio Browser](https://www.radio-browser.info) 公开 API 客户端。
 *
 * Radio Browser 是一个 crowdsource 的全球网络电台索引，收录了数千到数万个电台，
 * 可按名称、国家、州/省、语言、标签检索，并直接返回可播放的流媒体地址。
 *
 * 示例用法：
 * ```kotlin
 * val client = RadioBrowserClient()
 * val stations = client.searchByName("chaozhou", 20)
 * ```
 */
class RadioBrowserClient(
    private val httpClient: HttpClient = JdkHttpClient(),
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val userAgent: String = "radiostation-discovery/1.0",
) {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * 按名称搜索电台（例如 "chaozhou"、"潮州"、"广东"）。
     */
    suspend fun searchByName(query: String, limit: Int = 30): List<RadioStation> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = "$baseUrl/json/stations/search?name=$encoded&limit=$limit"
        return fetch(url)
    }

    /**
     * 按**国家代码**搜索（`CN` / `US` / `JP`），大小写不敏感。
     *
     * **优先用这个方法，不要用 [searchByPlace]**：Radio Browser 的国家名过滤是
     * **大小写敏感**的字符串精确匹配，`country=china` 返回 0 条、`country=China`
     * 才返回 100 条（2026-11-04 实测）。而 `countrycode` 不区分大小写，也没有
     * "China / 中国 / People's Republic of China" 这类写法歧义。
     */
    suspend fun searchByCountryCode(
        countryCode: String,
        limit: Int = 50,
    ): List<RadioStation> {
        val code = URLEncoder.encode(countryCode.trim().uppercase(), "UTF-8")
        val url = "$baseUrl/json/stations/search?countrycode=$code&limit=$limit"
        return fetch(url)
    }

    /**
     * 按国家/省份搜索。
     *
     * **注意国家名大小写**：Radio Browser 对 `country` / `state` 做的是大小写敏感的
     * 精确匹配 —— `country=china` 返回 0 条、`country=China` 返回 100 条；
     * `state=guangdong` 返回 0 条、`state=Guangdong` 返回 6 条（2026-11-04 实测）。
     *
     * 调用方传小写会**静默拿到空列表**，界面上看起来就是"这个国家没有电台"，
     * 而不是报错。所以这里做了两层兜底：
     * 1. 两个字母的国家（`cn` / `us`）自动改走 [searchByCountryCode]；
     * 2. 其余按单词首字母大写规范化（`china` → `China`、`guangdong` → `Guangdong`）。
     *
     * 想完全避开这个问题，直接调 [searchByCountryCode]。
     */
    suspend fun searchByPlace(
        country: String,
        state: String? = null,
        limit: Int = 50,
    ): List<RadioStation> {
        val trimmedCountry = country.trim()
        if (trimmedCountry.length == 2) return searchByCountryCode(trimmedCountry, limit)

        val countryEnc = URLEncoder.encode(titleCase(trimmedCountry), "UTF-8")
        val stateEnc = state?.trim()?.takeUnless { it.isEmpty() }
            ?.let { URLEncoder.encode(titleCase(it), "UTF-8") }
        val statePart = stateEnc?.let { "&state=$it" } ?: ""
        val url = "$baseUrl/json/stations/search?country=$countryEnc$statePart&limit=$limit"
        return fetch(url)
    }

    /**
     * 获取热门电台（适合做"推荐"页）。
     */
    suspend fun getPopular(limit: Int = 50): List<RadioStation> {
        val url = "$baseUrl/json/stations/top?limit=$limit"
        return fetch(url)
    }

    private suspend fun fetch(url: String): List<RadioStation> {
        val response = httpClient.get(url, mapOf("User-Agent" to userAgent))
        return if (response.statusCode in 200..299) {
            json.decodeFromString<List<RadioBrowserRaw>>(response.body)
                .map { it.toRadioStation() }
        } else {
            emptyList()
        }
    }

    companion object {
        /** `de1` 与 `all` 两个域名 2026-11-04 实测都可用，`all` 作为备用。 */
        const val DEFAULT_BASE_URL = "https://de1.api.radio-browser.info"
        const val FALLBACK_BASE_URL = "https://all.api.radio-browser.info"

        /**
         * 把 `china` 规范化成 `China`、`united states` 规范化成 `United States`。
         *
         * Radio Browser 的国家/省份过滤大小写敏感，不规范化就会静默返回空。
         * 已有的非字母字符（空格、连字符、撇号）保留，只动首字母。
         */
        internal fun titleCase(raw: String): String =
            raw.split(' ').joinToString(" ") { word ->
                word.replaceFirstChar { c -> if (c.isLowerCase()) c.titlecase() else c.toString() }
            }
    }
}
