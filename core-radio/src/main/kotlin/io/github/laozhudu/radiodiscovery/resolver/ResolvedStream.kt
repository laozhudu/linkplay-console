package io.github.laozhudu.radiodiscovery.resolver

/**
 * 解析后的流媒体元信息。
 */
data class ResolvedStream(
    /** 用户原始输入的 URL */
    val originalUrl: String,
    /** 经过 HTTP 重定向后的最终地址 */
    val finalUrl: String,
    /** HTTP 响应的 Content-Type（如 audio/mpeg, application/vnd.apple.mpegurl） */
    val mimeType: String?,
    val isHls: Boolean,
    /** 是否为可直接给播放器播放的流（有可识别的流媒体后缀或 MIME） */
    val isDirectStream: Boolean,
)
