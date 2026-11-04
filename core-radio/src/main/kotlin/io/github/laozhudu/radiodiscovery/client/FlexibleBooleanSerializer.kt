package io.github.laozhudu.radiodiscovery.client

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull

/**
 * 宽容的布尔解析器：接受 `true` / `false` / `1` / `0` / `"true"` / `"1"`。
 *
 * ## 为什么需要
 *
 * Radio Browser 的 `hls` 字段**返回的是数字而不是布尔**：
 *
 * ```json
 * {"name":"...","hls":1,"lastcheckok":1,...}
 * ```
 *
 * 而模型里写的是 `val hls: Boolean?`，kotlinx.serialization 遇到 `1` 会抛
 * `JsonDecodingException: Expected valid boolean literal prefix, but had '1'`。
 * 后果不是"这个台被跳过"，而是**整个搜索列表解析失败**——
 * 只要返回结果里有一个 `hls:1` 的台，用户就看到"搜不到任何电台"。
 *
 * 2026-11-04 联调实测撞到的（单测 fixture 写的是 `true`，所以单测没发现）。
 */
object FlexibleBooleanSerializer : KSerializer<Boolean?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("FlexibleBoolean", PrimitiveKind.BOOLEAN)

    override fun deserialize(decoder: Decoder): Boolean? {
        // 只在 JSON 下工作：非 JSON 的 Decoder 拿不到原始 token，按布尔解
        if (decoder !is JsonDecoder) {
            return runCatching { decoder.decodeBoolean() }.getOrNull()
        }
        return when (val element = decoder.decodeJsonElement()) {
            is JsonPrimitive -> when {
                element.isString -> when (element.content.lowercase()) {
                    "true", "1" -> true
                    "false", "0" -> false
                    else -> null
                }
                else -> element.booleanOrNull ?: (element.intOrNull?.let { it != 0 })
            }
            else -> null
        }
    }

    override fun serialize(encoder: Encoder, value: Boolean?) {
        if (value == null) encoder.encodeNull() else encoder.encodeBoolean(value)
    }
}
