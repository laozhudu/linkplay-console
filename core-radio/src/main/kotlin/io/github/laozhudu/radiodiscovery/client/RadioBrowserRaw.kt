package io.github.laozhudu.radiodiscovery.client

import io.github.laozhudu.radiodiscovery.model.RadioStation
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Radio Browser 公开 API 返回的原始 JSON 字段映射。
 * 文档：https://www.radio-browser.info/wiki
 *
 * ## 每个字段都给默认值，不是偷懒
 *
 * kotlinx.serialization 里**可空类型不等于可选字段**：`val hls: Boolean?` 依然要求
 * JSON 里必须有这个 key，缺了会抛 `MissingFieldException`。而 Radio Browser 是
 * 公益众包服务，不同镜像 / 不同版本返回的字段并不完全一致（实测 `state`、
 * `language`、`favicon` 都可能缺失或为空串）。
 *
 * 所以全部给默认值 —— 缺字段时退化成 null / 空，**不要让整个列表解析失败**。
 *
 * ## `hls` 用宽容解析器
 *
 * 真实响应里 `hls` 是**数字**（`1` / `0`）不是布尔，直接按 `Boolean` 解会抛异常，
 * 而且会让整批结果全挂。见 [FlexibleBooleanSerializer]。
 */
@Serializable
data class RadioBrowserRaw(
    @SerialName("name") val name: String? = null,
    @SerialName("url") val url: String? = null,
    @SerialName("stationuuid") val stationuuid: String? = null,
    @SerialName("country") val country: String? = null,
    @SerialName("state") val state: String? = null,
    @SerialName("language") val language: String? = null,
    @SerialName("tags") val tags: String? = null,
    @SerialName("favicon") val favicon: String? = null,
    @SerialName("bitrate") val bitrate: Int? = null,
    @SerialName("codec") val codec: String? = null,
    @SerialName("hls") @Serializable(with = FlexibleBooleanSerializer::class) val hls: Boolean? = null,
    @SerialName("lastcheckok") val lastcheckok: Int? = null,
    @SerialName("votes") val votes: Int? = null,
    @SerialName("clickcount") val clickcount: Int? = null,
)

internal fun RadioBrowserRaw.toRadioStation(): RadioStation {
    val id = stationuuid ?: name?.let { it.hashCode().toString() } ?: "unknown"
    val tagList = tags?.let { it.split(",").map { t -> t.trim() }.filter { it.isNotEmpty() } } ?: emptyList()
    return RadioStation(
        id = id,
        name = name ?: "Unknown Station",
        url = url ?: "",
        country = country,
        state = state,
        language = language,
        tags = tagList,
        favicon = favicon,
        bitrate = bitrate,
        codec = codec,
        isHls = hls == true,
        lastCheckOk = lastcheckok?.let { it == 1 },
        votes = votes,
        clickCount = clickcount,
    )
}
