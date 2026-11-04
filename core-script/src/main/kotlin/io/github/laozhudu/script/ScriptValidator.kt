package io.github.laozhudu.script

/**
 * 判断一段文本是不是一支能用的脚本 —— **唯一的口子**。
 *
 * ## 为什么是接口而不是在这里写死格式
 *
 * 这个模块管的是"脚本仓库"这件事本身（存、列、删、启用），
 * 而"脚本长什么样"是**具体脚本格式**的知识：
 *
 * - 现在只有一种：洛雪自定义源（`/*! @name ... */` 头部注释）；
 * - 将来还会有别的（红外码库、别的音乐源格式）。
 *
 * 所以校验规则由各格式自己提供（`core-lx` 里的 `LxScriptValidator`），
 * 仓库只认 [ScriptCheck]。加一种新格式 = 加一个实现，仓库一行不改。
 *
 * 用 `fun interface` 是为了组装处能写成一行 lambda，例如给最简格式：
 * `ScriptValidator { text -> if (text.isBlank()) ScriptCheck.Empty else ... }`
 */
fun interface ScriptValidator {

    /** 校验。**不允许抛异常** —— 文本是用户给的，什么都可能出现。 */
    fun validate(text: String): ScriptCheck
}
