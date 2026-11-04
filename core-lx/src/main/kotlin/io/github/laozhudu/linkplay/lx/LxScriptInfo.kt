package io.github.laozhudu.linkplay.lx

/**
 * 脚本头部的元信息（脚本开头那段 `@name` 块注释）。
 *
 * 洛雪导入脚本时会解析这段注释；缺 `@name` 视为非法脚本。
 * 这里照抄官方规则：`@name` 必填，其余可空。
 */
data class LxScriptInfo(
    val name: String,
    val description: String = "",
    val version: String = "",
    val author: String = "",
    val homepage: String = "",
) {
    /** 列表里展示的一行：名字 + 版本 + 作者 */
    fun displayLine(): String = buildString {
        append(name)
        if (version.isNotBlank()) append("  ").append(if (version.startsWith("v")) version else "v$version")
        if (author.isNotBlank()) append("  ").append(author)
    }

    companion object {
        private const val BLOCK_OPEN = "/*"
        private const val BLOCK_CLOSE = "*/"

        /**
         * 从脚本源码解析头部。
         *
         * 只看**文件开头那段块注释**，不扫全文 —— 脚本正文里可能也有 `@name` 之类的字样，
         * 全文扫会误判。找不到返回 null（调用方据此判"这不是一个合法的洛雪脚本"）。
         */
        fun parse(script: String): LxScriptInfo? {
            val head = extractLeadingBlockComment(script) ?: return null
            val fields = mutableMapOf<String, String>()
            for (line in head.lines()) {
                // 每行去掉注释装饰符：单行 `/*! @name x` 和多行 ` * @name x` 都要能处理
                var t = line.trim()
                while (t.startsWith("/*") || t.startsWith("*") || t.startsWith("!")) {
                    t = when {
                        t.startsWith("/*") -> t.removePrefix("/*")
                        t.startsWith("*") -> t.removePrefix("*")
                        else -> t.removePrefix("!")
                    }.trim()
                }
                if (!t.startsWith("@")) continue
                val key = t.substringBefore(' ').removePrefix("@").lowercase()
                val value = t.substringAfter(' ', "").trim()
                if (key.isNotEmpty() && value.isNotEmpty()) fields[key] = value
            }
            val name = fields["name"]?.takeIf { it.isNotBlank() } ?: return null
            return LxScriptInfo(
                name = name,
                description = fields["description"].orEmpty(),
                version = fields["version"].orEmpty(),
                author = fields["author"].orEmpty(),
                homepage = fields["homepage"].orEmpty(),
            )
        }

        /** 取脚本开头（跳过 BOM 和空白）的第一段块注释。 */
        private fun extractLeadingBlockComment(script: String): String? {
            val s = script.removePrefix("\uFEFF").trimStart()
            if (!s.startsWith(BLOCK_OPEN)) return null
            val end = s.indexOf(BLOCK_CLOSE)
            if (end < 0) return null
            return s.substring(0, end)
        }
    }
}
