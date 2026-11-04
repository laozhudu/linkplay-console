package io.github.laozhudu.linkplay.lx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LxScriptInfoTest {

    private val script = """
        /*!
         * @name 六音音源
         * @description v1.2.1 如失效请前往 www.sixyin.com 下载最新版本
         * @version v1.2.1
         * @author 六音
         * @homepage www.sixyin.com
         */
        const { EVENT_NAMES } = globalThis.lx
    """.trimIndent()

    @Test
    fun `解析完整的头部`() {
        val info = LxScriptInfo.parse(script)!!
        assertEquals("六音音源", info.name)
        assertEquals("v1.2.1 如失效请前往 www.sixyin.com 下载最新版本", info.description)
        assertEquals("v1.2.1", info.version)
        assertEquals("六音", info.author)
        assertEquals("www.sixyin.com", info.homepage)
    }

    @Test
    fun `缺 name 视为非法脚本`() {
        val bad = """
            /*!
             * @version 1.0
             * @author 某人
             */
        """.trimIndent()
        assertNull(LxScriptInfo.parse(bad))
    }

    @Test
    fun `没有头部注释时返回 null`() {
        assertNull(LxScriptInfo.parse("console.log('hi')"))
    }

    @Test
    fun `头部注释没闭合时返回 null`() {
        assertNull(LxScriptInfo.parse("/*! @name 坏的"))
    }

    @Test
    fun `跳过 BOM 和前导空白`() {
        val withBom = "\uFEFF\n\n  " + script
        assertEquals("六音音源", LxScriptInfo.parse(withBom)?.name)
    }

    @Test
    fun `只解析开头那段注释 不扫正文`() {
        // 正文里也有 @name，不能误抓
        val s = """
            /*!
             * @name 真的
             */
            // @name 假的
            const x = 1
        """.trimIndent()
        assertEquals("真的", LxScriptInfo.parse(s)?.name)
    }

    @Test
    fun `没有版本和作者也能解析`() {
        val info = LxScriptInfo.parse("/*! @name 只有名字 */")!!
        assertEquals("只有名字", info.name)
        assertTrue(info.version.isBlank())
        assertTrue(info.author.isBlank())
    }

    @Test
    fun `展示行会补上 v 前缀`() {
        val info = LxScriptInfo(name = "六音音源", version = "1.2.1", author = "六音")
        assertEquals("六音音源  v1.2.1  六音", info.displayLine())
    }

    @Test
    fun `版本已经带 v 就不重复加`() {
        val info = LxScriptInfo(name = "x", version = "v9.3")
        assertEquals("x  v9.3", info.displayLine())
    }

    @Test
    fun `解析真实的全豆要脚本头`() {
        val real = """
            /*!
             * @name 全豆要[聚合音源]
             * @description 迭代9.3版本，聚合 星海/溯音/念心/长青/歌一刀专属汽水音乐，多链路自动回退
             * @version 9.3 93特供版 DeepSeek优化
             * @author 全豆要 and Gemini优化 Toskysun去混淆 TZB679兼容性处理
             * @contribution DeepSeek优化
             */
        """.trimIndent()
        val info = LxScriptInfo.parse(real)!!
        assertEquals("全豆要[聚合音源]", info.name)
        assertEquals("9.3 93特供版 DeepSeek优化", info.version)
        assertTrue(info.author.startsWith("全豆要"))
        // @contribution 不是标准字段，不该被当成已知字段
        assertFalse(info.description.contains("DeepSeek"))
    }
}
