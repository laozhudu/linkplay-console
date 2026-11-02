package io.github.laozhudu.linkplay.protocol.metadata

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * 用**真机样本**跑 MP4 标签解析。
 *
 * 为什么要有它：合成字节能测帧头布局，但测不出「moov 在文件末尾」这种真实世界的坑
 * （本项目的 NAS 曲目正是这种）。所以留一个由环境变量开关的样本测试：
 *
 * ```
 * LIVE_M4A_HEAD=/tmp/m4a_head.bin LIVE_M4A_TAIL=/tmp/m4a_tail.bin ./gradlew :core-protocol:test
 * ```
 *
 * 两个文件分别是同一个 m4a 的**开头 256KB** 与**末尾 1MB**（用 Range 抓的）。
 * CI 上没有这两个文件 → 自动跳过，不会把环境依赖带进流水线。
 */
class Mp4RealSampleTest {

    @Test
    fun 从真实_m4a_的尾部读出内嵌封面() {
        val tailPath = System.getenv("LIVE_M4A_TAIL")
        assumeTrue("未提供 LIVE_M4A_TAIL，跳过", !tailPath.isNullOrBlank())
        val tail = File(tailPath).readBytes()

        val tags = Mp4Tags.parse(tail)
        assertNotNull("真实 m4a 尾部应能解析出标签", tags)
        assertTrue("应读到内嵌封面", tags!!.hasCover)
        // 封面必须是真实图片魔数，防止把别的 atom 当图片
        val c = tags.cover!!
        val isJpeg = c.size > 3 && c[0] == 0xFF.toByte() && c[1] == 0xD8.toByte()
        val isPng = c.size > 8 && c[0] == 0x89.toByte() && c[1] == 0x50.toByte()
        assertTrue("封面应是 JPEG 或 PNG，实际 mime=${tags.coverMime} size=${c.size}", isJpeg || isPng)
        assertTrue("封面应有合理体积", c.size > 1024)
        println("MP4 真实样本：title=${tags.title} artist=${tags.artist} cover=${c.size}B ${tags.coverMime}")
    }

    @Test
    fun 真实_m4a_的开头不是标签容器() {
        val headPath = System.getenv("LIVE_M4A_HEAD")
        assumeTrue("未提供 LIVE_M4A_HEAD，跳过", !headPath.isNullOrBlank())
        val head = File(headPath).readBytes()
        // 关键事实：m4a 开头是 ftyp+mdat，标签解析必然为 null —— 这就是"只读开头读不到封面"的原因
        assertTrue(Mp4Tags.looksLikeMp4(head))
        assertTrue(TagParser.parse(head) == null)
    }
}
