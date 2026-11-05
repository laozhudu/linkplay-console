package io.github.laozhudu.linkplay.protocol.dlna

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 首页分组判据（`docs/92` U2）。
 *
 * 用户原话：「通用 upnp 设备根本就没考虑过与 doss 隔离，反而混在一起」。
 *
 * 这三组按**能力**分，不按品牌 —— 所以这些用例锁的是"什么能力落在哪一组"，
 * 而不是"哪个型号落在哪一组"。
 */
class DeviceGroupTest {

    @Test
    fun `linkplay is my speaker`() {
        val caps = DeviceCapabilities.LINKPLAY
        assertEquals(DeviceGroup.MySpeaker, caps.group)
        // 音箱既能放声音也能被遥控
        assertTrue(caps.canPlayAudio)
        assertTrue(caps.canBeRemoted)
    }

    @Test
    fun `generic renderer with volume is other player not speaker`() {
        // 电视盒子实测：有 AVTransport + RenderingControl，但没有 PlayQueue、不能组网
        val dmr = DeviceCapabilities(
            supportsVolume = true,
            supportsSeek = true,
            supportsMute = true,
        )
        assertEquals(DeviceGroup.OtherPlayer, dmr.group)
        assertTrue(dmr.canPlayAudio)
        // **关键**：它能放声音，但不能和音箱混成一组 —— 它收不到整份列表
        assertFalse(dmr.supportsPlayQueue)
    }

    @Test
    fun `has queue but no multiroom is still other player`() {
        // 只有队列、不能组网：不能算"我的音箱"（组网是音箱这一档的判据之一）
        val caps = DeviceCapabilities(supportsPlayQueue = true, supportsVolume = true)
        assertEquals(DeviceGroup.OtherPlayer, caps.group)
    }

    @Test
    fun `nothing at all is other`() {
        assertEquals(DeviceGroup.Other, DeviceCapabilities.NONE.group)
        assertFalse(DeviceCapabilities.NONE.canPlayAudio)
        assertFalse(DeviceCapabilities.NONE.canBeRemoted)
    }

    @Test
    fun `presets alone means it can be remoted but not play audio`() {
        // 边界：有按键能力但放不了声音（例如某些只有体质传感器的老设备）
        val caps = DeviceCapabilities(supportsPresets = true)
        assertTrue(caps.canBeRemoted)
        assertFalse(caps.canPlayAudio)
        assertEquals(DeviceGroup.Other, caps.group)
    }
}
