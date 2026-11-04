package io.github.laozhudu.linkplay.console.diagnostics

import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * 内容源失败的**分类** —— 让诊断报告说准话。
 *
 * 从 `docs/61` 施工图来的第一条：现在各处的失败都是 `t.message` 直出，
 * 诊断报告里写着 `IOException: Connection refused`。我看得懂，但
 *
 * 1. 报告是给用户截图、由用户转述的；
 * 2. 三个月后我自己也未必记得当时是哪个环节；
 * 3. 最要命的是「搜不到歌」和「库里没歌」在界面上长得一模一样。
 *
 * 所以先分类，再写成「分类 + 人话」。
 *
 * ## 为什么 [UNKNOWN] 是必需的
 *
 * 施工图里列了八类，没列「未知」。但异常映射必须有兜底，
 * 否则只能被迫把不认识的异常塞进某一个分类 —— 那就是在报告里说谎。
 * 宁可写「未知错误」让人去查，也不假装知道。
 */
enum class SourceFailure(val label: String) {
    NOT_CONFIGURED("未配置"),
    UNREACHABLE("连不上"),
    AUTH_FAILED("认证失败"),
    EMPTY("连上了但是空的"),
    UNSUPPORTED("设备不支持"),
    PERMISSION_DENIED("没权限"),
    TIMEOUT("超时"),
    PARSE_FAILED("回包看不懂"),
    UNKNOWN("未知错误"),
    ;

    /** 一行写完：「连不上 —— Connection refused」。 */
    fun describe(detail: String?): String =
        if (detail.isNullOrBlank()) label else "$label —— $detail"

    companion object {
        /**
         * 把异常翻成分类。
         *
         * 只看**类型和类名**，不解析 message 文本 —— 后者随系统语言变，不可靠。
         */
        fun of(error: Throwable): SourceFailure {
            val name = error.javaClass.simpleName
            return when {
                error is SocketTimeoutException -> TIMEOUT
                error is UnknownHostException -> UNREACHABLE
                error is ConnectException -> UNREACHABLE
                error is NoRouteToHostException -> UNREACHABLE
                error is SocketException -> UNREACHABLE
                error is SecurityException -> PERMISSION_DENIED
                name.contains("Json", ignoreCase = true) -> PARSE_FAILED
                name.contains("Xml", ignoreCase = true) -> PARSE_FAILED
                name.contains("Parse", ignoreCase = true) -> PARSE_FAILED
                else -> UNKNOWN
            }
        }
    }
}
