package io.github.laozhudu.script

/** 脚本从哪儿来的。只用于跟用户解释与诊断，不影响存储。 */
enum class ScriptOrigin {
    /** 用户粘进文本框。 */
    PASTED,

    /** 用户从文件选。 */
    FILE,

    /** 从网址下载（用户贴一个链接）。 */
    URL,

    /** 剪贴板自动识别（还没有实现，先占位说明将来从哪来）。 */
    CLIPBOARD,

    /** 不知道（比如从磁盘扫到的旧文件）。 */
    UNKNOWN,
}

/**
 * 一支**还没校验、还没入库**的脚本。
 *
 * 为什么要有这一步而不是直接把文本丢给仓库：来源和内容分开之后，
 * 以后加"从网址下载""扫二维码"只需要构造同一个 [ScriptCandidate]，
 * 校验、存盘、诊断全都不用改。
 */
data class ScriptCandidate(
    val text: String,
    val origin: ScriptOrigin = ScriptOrigin.UNKNOWN,
    /** 来源自己知道的名字（比如文件名），只在校验失败时用来给用户指认"是哪一份"。 */
    val label: String? = null,
)
