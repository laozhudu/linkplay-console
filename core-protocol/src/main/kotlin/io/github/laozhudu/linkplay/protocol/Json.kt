package io.github.laozhudu.linkplay.protocol

/**
 * 极小 JSON 解析器。
 *
 * 为什么不用 kotlinx.serialization / org.json：核心协议模块是纯 JVM 库，
 * 不依赖 Android 也不引入反射或编译器插件，保证单元测试秒级跑完、体积为零。
 * 只支持设备实际返回的 JSON 子集（对象 / 数组 / 字符串 / 数字 / 布尔 / null）。
 */
sealed class JsonValue {
    data class Obj(val fields: Map<String, JsonValue>) : JsonValue() {
        operator fun get(key: String): JsonValue? = fields[key]
        fun string(key: String): String? = (fields[key] as? Str)?.value
        fun int(key: String): Int? = (fields[key] as? Num)?.let { it.value.toInt() }
        fun obj(key: String): Obj? = fields[key] as? Obj
        fun array(key: String): Arr? = fields[key] as? Arr
    }

    data class Arr(val items: List<JsonValue>) : JsonValue()

    data class Str(val value: String) : JsonValue()

    data class Num(val value: Double) : JsonValue()

    data class Bool(val value: Boolean) : JsonValue()

    data object Null : JsonValue()
}

class JsonParseException(message: String) : Exception(message)

object Json {
    fun parse(text: String): JsonValue = Parser(text).run {
        val value = parseValue()
        skipWhitespace()
        if (!atEnd()) throw JsonParseException("trailing content at $pos")
        value
    }

    private class Parser(private val src: String) {
        var pos = 0

        fun atEnd() = pos >= src.length

        fun skipWhitespace() {
            while (pos < src.length && src[pos].isWhitespace()) pos++
        }

        fun parseValue(): JsonValue {
            skipWhitespace()
            if (atEnd()) throw JsonParseException("unexpected end of input")
            return when (val c = src[pos]) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"' -> JsonValue.Str(parseString())
                't', 'f' -> parseBoolean()
                'n' -> parseNull()
                else -> if (c == '-' || c.isDigit()) parseNumber()
                else throw JsonParseException("unexpected character '$c' at $pos")
            }
        }

        private fun parseObject(): JsonValue.Obj {
            expect('{')
            val fields = LinkedHashMap<String, JsonValue>()
            skipWhitespace()
            if (peek() == '}') {
                pos++
                return JsonValue.Obj(fields)
            }
            while (true) {
                skipWhitespace()
                val key = parseString()
                skipWhitespace()
                expect(':')
                fields[key] = parseValue()
                skipWhitespace()
                when (peek()) {
                    ',' -> pos++
                    '}' -> {
                        pos++
                        return JsonValue.Obj(fields)
                    }
                    else -> throw JsonParseException("expected ',' or '}' at $pos")
                }
            }
        }

        private fun parseArray(): JsonValue.Arr {
            expect('[')
            val items = ArrayList<JsonValue>()
            skipWhitespace()
            if (peek() == ']') {
                pos++
                return JsonValue.Arr(items)
            }
            while (true) {
                items.add(parseValue())
                skipWhitespace()
                when (peek()) {
                    ',' -> pos++
                    ']' -> {
                        pos++
                        return JsonValue.Arr(items)
                    }
                    else -> throw JsonParseException("expected ',' or ']' at $pos")
                }
            }
        }

        private fun parseString(): String {
            expect('"')
            val sb = StringBuilder()
            while (true) {
                if (atEnd()) throw JsonParseException("unterminated string")
                when (val c = src[pos++]) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        if (atEnd()) throw JsonParseException("unterminated escape")
                        when (val e = src[pos++]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                if (pos + 4 > src.length) throw JsonParseException("bad unicode escape")
                                sb.append(src.substring(pos, pos + 4).toInt(16).toChar())
                                pos += 4
                            }
                            else -> throw JsonParseException("bad escape '\\$e'")
                        }
                    }
                    else -> sb.append(c)
                }
            }
        }

        private fun parseNumber(): JsonValue.Num {
            val start = pos
            if (peek() == '-') pos++
            while (!atEnd() && (src[pos].isDigit() || src[pos] in ".eE+-")) pos++
            val raw = src.substring(start, pos)
            return JsonValue.Num(raw.toDoubleOrNull() ?: throw JsonParseException("bad number '$raw'"))
        }

        private fun parseBoolean(): JsonValue.Bool = when {
            src.startsWith("true", pos) -> {
                pos += 4
                JsonValue.Bool(true)
            }
            src.startsWith("false", pos) -> {
                pos += 5
                JsonValue.Bool(false)
            }
            else -> throw JsonParseException("bad literal at $pos")
        }

        private fun parseNull(): JsonValue {
            if (!src.startsWith("null", pos)) throw JsonParseException("bad literal at $pos")
            pos += 4
            return JsonValue.Null
        }

        private fun peek(): Char =
            if (atEnd()) throw JsonParseException("unexpected end of input") else src[pos]

        private fun expect(c: Char) {
            if (atEnd() || src[pos] != c) throw JsonParseException("expected '$c' at $pos")
            pos++
        }
    }
}
