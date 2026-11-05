package io.github.laozhudu.upnp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceQuirksTest {

    @Test
    fun `normal position passes through`() {
        assertEquals(202_000L, DeviceQuirks.sanitizePositionMs(202_000, 300_000))
        assertEquals(202_000L, DeviceQuirks.sanitizePositionMs(202_000, null))
    }

    @Test
    fun `position beyond the track length is rejected`() {
        // 暂停时真机回 28242665ms，而总时长只有 322 秒 —— 这种必须判垃圾
        assertNull(DeviceQuirks.sanitizePositionMs(28_242_665, 322_000))
    }

    @Test
    fun `small overshoot is tolerated`() {
        // 设备的时间戳常有 1~2 秒抖动，卡得太死会把正常读数也毙掉
        assertEquals(301_000L, DeviceQuirks.sanitizePositionMs(301_000, 300_000))
    }

    @Test
    fun `negative and absurd values are rejected`() {
        assertNull(DeviceQuirks.sanitizePositionMs(-1, 300_000))
        assertNull(DeviceQuirks.sanitizePositionMs(DeviceQuirks.MAX_POSITION_MS + 1, null))
        assertNull(DeviceQuirks.sanitizePositionMs(null, 300_000))
    }

    @Test
    fun `a live stream has no duration and must not be rejected`() {
        // 直播流 TrackDuration 是 0 或缺失，位置本身是有效的
        assertEquals(60_000L, DeviceQuirks.sanitizePositionMs(60_000, 0))
        assertEquals(60_000L, DeviceQuirks.sanitizePositionMs(60_000, null))
    }

    @Test
    fun `linkplay devices report their own quirks`() {
        val ids = DeviceQuirks.of(modelName = "DS-1831", firmware = "3.6.6623", isLinkPlay = true).map { it.id }
        assertTrue(ids.contains("uuid-two-formats"))
        assertTrue(ids.contains("wifi-switch"))
        assertTrue(ids.contains("slave-unreachable"))
        assertTrue("通用那条对所有设备都该有", ids.contains("position-garbage"))
    }

    @Test
    fun `old firmware adds the https radio quirk`() {
        val ids = DeviceQuirks.of(modelName = "DS-1831", firmware = "3.6.5310", isLinkPlay = true).map { it.id }
        assertTrue(ids.contains("https-radio"))
    }

    @Test
    fun `a generic device only gets the universal quirk`() {
        val ids = DeviceQuirks.of(modelName = "Some TV", firmware = "1.0", isLinkPlay = false).map { it.id }
        assertEquals(listOf("position-garbage"), ids)
    }

    @Test
    fun `unknown identity does not invent quirks`() {
        // 什么都拿不到时只报通用那条，不猜品牌
        assertEquals(listOf("position-garbage"), DeviceQuirks.of().map { it.id })
    }
}
