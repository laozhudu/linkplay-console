package io.github.laozhudu.radiodiscovery.model

import kotlinx.serialization.Serializable

/**
 * 标准化的广播电台模型，屏蔽第三方 API（Radio Browser / 其他目录）的字段差异。
 *
 * 设计目标：被任何播放器项目直接消费，不依赖 Android 框架，不依赖特定播放器引擎。
 */
@Serializable
data class RadioStation(
    /** 电台唯一标识。Radio Browser 里是 stationuuid，其他来源可自行生成。 */
    val id: String,
    val name: String,
    /** 原始流地址（可能是中间页，需经过 [StreamResolver] 解析）。 */
    val url: String,
    /** 解析后的真实流地址（可选，由 [StreamResolver] 填充）。 */
    val resolvedUrl: String? = null,
    val country: String? = null,
    val state: String? = null,
    val language: String? = null,
    val tags: List<String> = emptyList(),
    val favicon: String? = null,
    val bitrate: Int? = null,
    val codec: String? = null,
    val isHls: Boolean = false,
    val lastCheckOk: Boolean? = null,
    val votes: Int? = null,
    val clickCount: Int? = null,
)

/**
 * 搜索结果分页包装。
 */
@Serializable
data class SearchResult(
    val stations: List<RadioStation>,
    val total: Int,
    val query: String? = null
)
