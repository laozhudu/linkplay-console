package io.github.laozhudu.linkplay.console

import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * bundle 版本必须出现在诊断里 —— 否则"官方已经升级、我这边还是老的"永远无法察觉。
 */
class LxBundleVersionTest {

    @Before
    fun setUp() = LxSourceStatus.reset()

    @Test
    fun `bundle version is shown next to the engine line`() {
        LxSourceStatus.started()
        LxSourceStatus.loaded(ms = 72, bundleVersion = "v1.9.2")

        val text = LxSourceStatus.render()
        assertTrue(text.contains("加载 72ms"))
        assertTrue(text.contains("bundle=v1.9.2"))
    }

    @Test
    fun `missing version says unknown instead of a made up one`() {
        LxSourceStatus.started()
        LxSourceStatus.loaded(ms = 72, bundleVersion = "未知")

        // 承认不知道，好过写一个看起来对的版本号
        assertTrue(LxSourceStatus.render().contains("bundle=未知"))
    }
}
