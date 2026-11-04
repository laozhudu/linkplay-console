package io.github.laozhudu.script

/**
 * 一段脚本能不能用。**失败原因分开写**，不合并成一句"脚本无效"。
 *
 * ## 为什么必须分四种
 *
 * 用户拿着一支脚本点"导入"，失败的情形完全不一样，改法也完全不一样：
 *
 * | 情形 | 用户的下一步 |
 * |---|---|
 * | [Empty] 粘了空白 | 他以为粘上了，其实是空 —— 要告诉他"没东西" |
 * | [NoHeader] 没有元数据注释块 | 他拿到的可能是**另外一个软件的脚本**，不是格式不对 |
 * | [NoName] 有注释块但缺 `@name` | 脚本本身残缺，让他回去重新下载 |
 * | [Rejected] 其它 | 原样把原因给他，别自己编一句 |
 *
 * 混成一句"导入失败"，用户只能反复试同一支脚本 —— 这是本项目的铁律「不给假空态」
 * 在脚本这一侧的同一条要求。
 */
sealed interface ScriptCheck {

    /** 能用。[name] 是脚本自己声明的名字，也是列表里显示的名字。 */
    data class Ok(
        val name: String,
        val version: String? = null,
        val author: String? = null,
        val description: String? = null,
    ) : ScriptCheck

    /** 文本是空的（或全是空白）。 */
    data object Empty : ScriptCheck

    /** 找不到元数据注释块 —— 多半不是这个软件的脚本。 */
    data object NoHeader : ScriptCheck

    /** 有注释块，但里面没有名字。脚本不完整。 */
    data object NoName : ScriptCheck

    /** 校验器自己给的其它原因（原样展示给用户）。 */
    data class Rejected(val reason: String) : ScriptCheck
}
