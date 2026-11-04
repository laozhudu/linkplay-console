package io.github.laozhudu.linkplay.lx

import io.github.laozhudu.script.ScriptCheck
import io.github.laozhudu.script.ScriptValidator

/**
 * 洛雪自定义源脚本的校验规则 —— `core-script` 那个通用仓库的**一个**实现。
 *
 * ## 为什么放在 core-lx 而不是 core-script
 *
 * "脚本仓库"（存、列、删、启用）是通用的；"洛雪脚本长什么样"是格式知识。
 * 仓库那边只认 [ScriptCheck]，一种格式一个实现。将来红外码库、别的音源格式
 * 各写一个实现即可，仓库一行不改。
 *
 * ## 为什么要自己再判一次，而不是直接用 `LxScriptInfo.parse()`
 *
 * `parse()` 只有两种结果：解析出来 / `null`。但 `null` 有两种完全不同的原因
 * （没有头部注释块 / 有块但缺 `@name`），而这两种对应的**用户下一步完全不同**：
 * 前者说明他拿到的多半是别的软件的脚本，后者说明脚本本身是残的。
 * 混成一句"脚本无效"，他只会反复试同一份。
 */
class LxScriptValidator : ScriptValidator {

    override fun validate(text: String): ScriptCheck {
        if (text.isBlank()) return ScriptCheck.Empty

        val info = LxScriptInfo.parse(text) ?: return diagnoseUnparsable(text)
        return ScriptCheck.Ok(
            name = info.name,
            version = info.version.takeIf { it.isNotBlank() },
            author = info.author.takeIf { it.isNotBlank() },
            description = info.description.takeIf { it.isNotBlank() },
        )
    }

    /**
     * 解析不出来时，说清楚是哪种。
     *
     * 判据必须和 [LxScriptInfo.parse] 一致（开头是块注释、块内有 `@name`），
     * 否则会出现"validate 说缺名字、parse 又能解析"这种自相矛盾。
     */
    private fun diagnoseUnparsable(text: String): ScriptCheck {
        val head = text.removePrefix("\uFEFF").trimStart()
        if (!head.startsWith("/*")) return ScriptCheck.NoHeader
        val block = head.substringBefore("*/")
        if (!block.contains("@name", ignoreCase = true)) return ScriptCheck.NoName
        // 有 @name 却仍解析不出：值可能是空的，或者注释块没闭合
        return ScriptCheck.Rejected("头部写了 @name 但没解析出名字（值可能为空，或注释块缺 */）")
    }
}
