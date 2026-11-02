package io.github.laozhudu.linkplay.protocol

/**
 * 极小的 JSON 输出器，配合 [Json] 的解析器使用。
 *
 * 为什么需要它：会话（上次连的 NAS、上次浏览到哪、队列）要落盘，而曲目名里有中文、引号、
 * 甚至换行和制表符。用 `|` 或 `\t` 拼字符串一定会被某首歌名弄坏，所以宁可写 30 行转义。
 * 只覆盖 [JsonValue] 已有的类型，不追求完整 JSON 规范。
 */
object JsonWriter {

    fun encode(value: JsonValue): String = StringBuilder().also { write(it, value) }.toString()

    private fun write(sb: StringBuilder, value: JsonValue) {
        when (value) {
            is JsonValue.Obj -> {
                sb.append('{')
                var first = true
                for ((k, v) in value.fields) {
                    if (!first) sb.append(',')
                    first = false
                    writeString(sb, k)
                    sb.append(':')
                    write(sb, v)
                }
                sb.append('}')
            }

            is JsonValue.Arr -> {
                sb.append('[')
                value.items.forEachIndexed { i, v ->
                    if (i > 0) sb.append(',')
                    write(sb, v)
                }
                sb.append(']')
            }

            is JsonValue.Str -> writeString(sb, value.value)

            is JsonValue.Num -> {
                // 整数值不要输出成 1.0：设备侧/人读都更自然，也避免跨语言往返丢精度
                val d = value.value
                if (d == d.toLong().toDouble()) sb.append(d.toLong()) else sb.append(d)
            }

            is JsonValue.Bool -> sb.append(if (value.value) "true" else "false")

            JsonValue.Null -> sb.append("null")
        }
    }

    private fun writeString(sb: StringBuilder, s: String) {
        sb.append('"')
        for (c in s) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                else -> if (c < ' ') sb.append("\\u%04x".format(c.code)) else sb.append(c)
            }
        }
        sb.append('"')
    }
}
