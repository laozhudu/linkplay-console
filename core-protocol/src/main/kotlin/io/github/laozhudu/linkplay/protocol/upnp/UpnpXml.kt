package io.github.laozhudu.linkplay.protocol.upnp

/**
 * 极简 XML 取值工具。
 *
 * 为什么不用 javax.xml / XmlPullParser：这里只需要从固定的机器生成 XML 里取少量文本节点，
 * 完整 DOM 解析要处理命名空间、XXE、实体，代码量和风险都不划算。设备描述与 SOAP 响应
 * 都是扁平结构，按标签名取值足够，且便于单测锁定行为。
 *
 * 局限（已知且刻意）：不处理同名嵌套标签的层级区分；不解析 CDATA 中的标签。
 * 设备返回里两者都不出现。
 */
object UpnpXml {

    /** 取第一个 `<tag>...</tag>` 的文本内容（已做最小反转义），没有返回 null。 */
    fun value(xml: String, tag: String): String? {
        val open = "<$tag"
        var start = xml.indexOf(open)
        while (start >= 0) {
            val gt = xml.indexOf('>', start)
            if (gt < 0) return null
            // 跳过自闭合 <tag .../>
            if (xml[gt - 1] == '/') {
                start = xml.indexOf(open, gt)
                continue
            }
            val close = xml.indexOf("</$tag>", gt)
            if (close < 0) return null
            return unescape(xml.substring(gt + 1, close))
        }
        return null
    }

    /** 取所有同名标签的文本内容，按出现顺序。 */
    fun values(xml: String, tag: String): List<String> {
        val out = ArrayList<String>()
        var cursor = 0
        while (true) {
            val idx = xml.indexOf("<$tag", cursor)
            if (idx < 0) break
            val gt = xml.indexOf('>', idx)
            if (gt < 0) break
            if (xml[gt - 1] == '/') {
                cursor = gt + 1
                continue
            }
            val close = xml.indexOf("</$tag>", gt)
            if (close < 0) break
            out.add(unescape(xml.substring(gt + 1, close)))
            cursor = close + tag.length + 3
        }
        return out
    }

    /** 取 `<service>...</service>` 这类成对块（含内部内容）。 */
    fun blocks(xml: String, tag: String): List<String> {
        val out = ArrayList<String>()
        var cursor = 0
        while (true) {
            val idx = xml.indexOf("<$tag", cursor)
            if (idx < 0) break
            val end = xml.indexOf("</$tag>", idx)
            if (end < 0) break
            out.add(xml.substring(idx, end + tag.length + 3))
            cursor = end + tag.length + 3
        }
        return out
    }

    /** SOAP 参数与 DIDL 文本里会出现 & < > " '，构建时必须转义。 */
    fun escape(text: String): String = buildString(text.length + 16) {
        for (ch in text) {
            when (ch) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&apos;")
                else -> append(ch)
            }
        }
    }

    fun unescape(text: String): String {
        if (!text.contains('&')) return text
        return text
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&#39;", "'")
            .replace("&amp;", "&")
    }
}
