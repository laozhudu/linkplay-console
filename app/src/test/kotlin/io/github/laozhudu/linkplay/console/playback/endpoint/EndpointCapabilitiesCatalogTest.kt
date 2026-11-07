package io.github.laozhudu.linkplay.console.playback.endpoint

import io.github.laozhudu.linkplay.protocol.dlna.DeviceCapabilities
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 三种后端的能力表 —— **防的是我犯过的那个错**。
 *
 * ## 这份测试在挡什么
 *
 * 我上一版把本机和通用 UPnP 的 `canNext` / `canPrevious` / `hasQueue`
 * 全写成 false，理由分别是"本机播放器只播单曲"和"DLNA 标准里没有这个动作"。
 *
 * 两条都是假的：
 * - ExoPlayer 从来就支持 `setMediaItems` / `seekToNextMediaItem`，是我只调了单数版本
 * - `Next` / `Previous` 就是 AVTransport 的标准动作，是我没写那个调用
 *
 * 用户一句话点破：「通用 upnp 模块是能播放列表能切歌，**包括本机也是**，你的认知错误」。
 *
 * 所以这几个断言存在的意义是：**下次谁把它改回 false，测试立刻红。**
 * 能力表上的 false 一旦写下去，界面上那个按钮就消失了，而且没有任何东西会提醒你补。
 */
class EndpointCapabilitiesCatalogTest {

    @Test
    fun `本机能切歌也能看列表`() {
        val c = EndpointCapabilitiesCatalog.local

        assertTrue("ExoPlayer 有 seekToNextMediaItem", c.canNext)
        assertTrue("ExoPlayer 有 seekToPreviousMediaItem", c.canPrevious)
        assertTrue("ExoPlayer 有 setMediaItems（复数）", c.hasQueue)
    }

    @Test
    fun `本机唯一做不到的是静音`() {
        // 手机只有"音量"这一个旋钮，没有"设备侧静音"这个可读可写的状态。
        // 界面上所以不能画一个静音键 —— 画了就是点了没反应。
        val c = EndpointCapabilitiesCatalog.local

        assertFalse("手机没有设备侧静音状态", c.canMute)
        assertTrue("音量还是有的", c.canSeek)
    }

    @Test
    fun `通用 UPnP 能切歌也能看列表`() {
        val c = EndpointCapabilitiesCatalog.genericUpnp

        // 注意：设备自己不会切（AVTransport.Next 实测回 701），
        // 但**我们能把下一首重投给它** —— 用户要的"能切歌"是这个意思。
        assertTrue("我们能让它换下一首（重投）", c.canNext)
        assertTrue("上一首同理", c.canPrevious)
        assertTrue("列表是我们自己的，跟它持不持有无关", c.hasQueue)
    }

    @Test
    fun `设备自己不会切 但界面照样能切`() {
        // 这两个字段只差一个词，答案相反 —— 混在一起就会把切歌按钮删掉。
        val deviceSide = DeviceCapabilities.genericDmr(hasRenderingControl = true)
        val userSide = EndpointCapabilitiesCatalog.genericUpnp

        assertFalse("它自己不会换（701）", deviceSide.supportsNextPrev)
        assertTrue("但用户在我们这儿能换（重投）", userSide.canNext)
    }

    @Test
    fun `DOSS 的切歌能力跟着固件走，不写死`() {
        val old = DeviceCapabilities.genericDmr(hasRenderingControl = true)
            .copy(supportsNextPrev = false)
        val new = old.copy(supportsNextPrev = true)

        assertFalse("老固件（doss3 3.6.5310）不支持切歌", EndpointCapabilitiesCatalog.linkPlay(old).canNext)
        assertTrue("新固件（3.6.6623）支持", EndpointCapabilitiesCatalog.linkPlay(new).canNext)
        assertTrue("但两端都有列表", EndpointCapabilitiesCatalog.linkPlay(old).hasQueue)
    }

    @Test
    fun `hasQueue 和 supportsPlayQueue 是两件不同的事`() {
        // 通用渲染器**不持有**我们的列表（supportsPlayQueue = false），
        // 但界面**有列表可看**（hasQueue = true）—— 逐首投就行。
        // 上一版就是把这两件事混为一谈，才把播放列表整块删掉。
        val caps = DeviceCapabilities.genericDmr(hasRenderingControl = true)

        assertFalse("设备不持有整份列表", caps.supportsPlayQueue)
        assertTrue("但我们有列表给用户看", EndpointCapabilitiesCatalog.genericUpnp.hasQueue)
    }
}
