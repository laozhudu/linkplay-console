package io.github.laozhudu.linkplay.protocol.upnp

import java.io.IOException

/**
 * 设备返回的 SOAP 错误。
 *
 * ## 为什么单独一个异常类型
 *
 * 原来非 2xx 一律抛 `IOException("SOAP 500 for ...")`，这个字符串会一路冒到界面上
 * 变成红色给用户看 —— 用户看到的是
 * `SOAP 500 for "urn:schemas-upnp-org:service:AVTransport:1#Play" ...`，
 * 既看不懂也不知道该干什么（实际原因只是「设备还没加载媒体」）。
 *
 * 现在把 `errorCode` / `errorDescription` 结构化带出来，由 [UpnpFault] 翻译成
 * **能指导动作**的一句话，并把"设备当前是什么状态"告诉调用方，让它决定是否降级。
 */
class SoapFaultException(
    val httpCode: Int,
    /** UPnP 标准错误码（`<errorCode>`），设备没给就是 null。 */
    val upnpCode: Int?,
    /** 设备的 `<errorDescription>`，例如 `play not allowed`。 */
    val description: String?,
    /** 出错的动作名，例如 `Play`。 */
    val action: String,
) : IOException(
    "SOAP $httpCode for $action" + (upnpCode?.let { " (UPnP error $it${description?.let { d -> ": $d" } ?: ""})" } ?: ""),
)

/**
 * UPnP 错误码 → 用户能照做的一句话。
 *
 * 码表来源：UPnP AVTransport/RenderingControl 标准（`upnp:control-1-0`），
 * 只翻译我们用得到的那些；其余给一句"设备拒绝了这条命令"，**绝不把原始报文摊给用户**。
 *
 * 真机依据：`.164` 在没有加载媒体时回 `704 play not allowed`
 * （`GetMediaInfo` 的 `PlayMedium=UNKNOWN`），原厂不会碰到这条，
 * 因为它点播放前一定先下发过 URI/队列。
 */
object UpnpFault {

    const val NO_MEDIA = 704          // Play not allowed —— 设备没加载媒体
    const val TRANSITION_NOT_AVAILABLE = 701
    const val NO_CONTENTS = 702
    const val INVALID_ACTION = 401
    const val INVALID_ARGS = 402
    const val ACTION_FAILED = 501
    const val ILLEGAL_MIME = 715

    /** 这个错误码是否属于"设备现在没东西可放"这一类（可据此提示选歌，而不是报故障）。 */
    fun isNoMedia(code: Int?): Boolean = code == NO_MEDIA || code == NO_CONTENTS

    /** 这个错误码是否属于"当前状态不允许"，可以尝试换一条通道重试。 */
    fun isRetryableOnAnotherChannel(code: Int?): Boolean =
        code == NO_MEDIA || code == TRANSITION_NOT_AVAILABLE || code == ACTION_FAILED

    fun message(code: Int?, description: String? = null, action: String? = null): String = when (code) {
        NO_MEDIA ->
            "这台音箱还没加载媒体。先去「我的音乐」点一首，或按「全部播放」把列表交给它。"
        NO_CONTENTS ->
            "设备报告没有可播放的内容。先去「我的音乐」挑一首。"
        TRANSITION_NOT_AVAILABLE ->
            "设备当前状态不允许这个操作。如果切过输入源，先切回「网络播放」。"
        INVALID_ACTION ->
            "这台固件不支持这个操作" + (action?.let { "（$it）" } ?: "") + "。"
        INVALID_ARGS ->
            "设备不接受这条命令的参数，可能是固件版本差异。"
        ACTION_FAILED ->
            "设备拒绝了这条命令（可能是它正忙或当前没有媒体）。"
        ILLEGAL_MIME ->
            "设备不支持这个音频格式。"
        null -> "设备返回了错误" + (description?.let { "：$it" } ?: "") + "。"
        else -> "设备返回错误 $code" + (description?.let { "（$it）" } ?: "") + "。"
    }

    /** 从任意异常里取出友好文案；不是 SOAP 错误就退回它自己的 message。 */
    fun friendlyOf(t: Throwable): String =
        (t as? SoapFaultException)?.let { message(it.upnpCode, it.description, it.action) }
            ?: t.message.orEmpty().ifBlank { t.javaClass.simpleName }
}
