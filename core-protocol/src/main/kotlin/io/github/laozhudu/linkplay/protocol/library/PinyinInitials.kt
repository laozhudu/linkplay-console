package io.github.laozhudu.linkplay.protocol.library

/**
 * 汉字取**拼音首字母**（用于中文曲库的首字母搜索，如输入 `zy` 命中「周杰伦·夜曲」）。
 *
 * 实现原理：GB2312 的区位表本身就是按拼音排序的，所以把单字编码成 GB2312 两字节、
 * 换算成有符号码位后，对照 23 段边界即可得到首字母。这段边界表是社区通行的做法，无需任何依赖。
 *
 * 明确的能力边界（不要误用）：
 * - **只给首字母，不给完整拼音**（完整拼音需要 2 万字音表，本库不内置）；
 * - 只在 GB2312 覆盖的常用汉字上有效（生僻字、繁体、扩展区返回 null）；
 * - 多音字按编码给一个默认首字母（和原厂 pinyin4j 的默认行为一致，不保证语义正确）。
 */
object PinyinInitials {

    private val BOUNDARIES: List<Pair<Int, Int>> = listOf(
        -20319 to -20284, // A
        -20283 to -19776, // B
        -19775 to -19219, // C
        -19218 to -18711, // D
        -18710 to -18527, // E
        -18526 to -18240, // F
        -18239 to -17923, // G
        -17922 to -17418, // H
        -17417 to -16475, // J
        -16474 to -16213, // K
        -16212 to -15641, // L
        -15640 to -15166, // M
        -15165 to -14923, // N
        -14922 to -14915, // O
        -14914 to -14631, // P
        -14630 to -14150, // Q
        -14149 to -14091, // R
        -14090 to -13319, // S
        -13318 to -12839, // T
        -12838 to -12557, // W
        -12556 to -11848, // X
        -11847 to -11056, // Y
        -11055 to -10247, // Z
    )

    /** 与 [BOUNDARIES] 一一对应的首字母，注意没有 I / U / V（GB2312 里不存在）。 */
    private const val LETTERS = "ABCDEFGHJKLMNOPQRSTWXYZ"

    private val GB2312 = runCatching { charset("GB2312") }
        .recoverCatching { charset("GBK") }
        .getOrNull()

    /** 单字首字母；非汉字或查不到返回 null。 */
    fun initialOf(ch: Char): Char? {
        if (ch.code < 0x3400) return null // 非 CJK 统一汉字（拉丁/数字/标点）
        val charset = GB2312 ?: return null
        val bytes = runCatching { ch.toString().toByteArray(charset) }.getOrNull() ?: return null
        if (bytes.size != 2) return null // 单字节 = 不在 GB2312 表里；三字节 = UTF 退化的异常情况
        val code = ((bytes[0].toInt() and 0xFF) shl 8) + (bytes[1].toInt() and 0xFF) - 65536
        if (code < BOUNDARIES.first().first || code > BOUNDARIES.last().second) return null
        BOUNDARIES.forEachIndexed { index, (from, to) ->
            if (code in from..to) return LETTERS[index]
        }
        return null
    }

    /**
     * 整串取首字母：汉字取首字母，字母数字原样保留并转小写，其它字符丢弃。
     * 例：`周杰伦 - 夜曲` → `zjlyq`
     */
    fun initialsOf(text: String): String = buildString {
        for (ch in text) {
            when {
                ch.isLetterOrDigit() && ch.code < 128 -> append(ch.lowercaseChar())
                ch.code >= 0x3400 -> initialOf(ch)?.let { append(it.lowercaseChar()) }
                else -> Unit
            }
        }
    }

    /** 是否含汉字，用于决定要不要建首字母索引。 */
    fun hasHanzi(text: String): Boolean = text.any { it.code >= 0x3400 }
}
