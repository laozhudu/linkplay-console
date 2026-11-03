package io.github.laozhudu.linkplay.console

import io.github.laozhudu.linkplay.protocol.snapshot.DeviceSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 卡片合并规则 —— 全项目唯一的写卡片路径。
 *
 * 这些用例锁的是两条真实故障：
 * 1. 每 3 秒的心跳只读播放态，却整体覆盖卡片 → 「电量有时有有时没有」
 * 2. 同一次覆盖把声道清回默认 → 「声道切换后界面不变」
 */
class CardMergeTest {

    @Test
    fun `player-only snapshot keeps battery channel and charging flag`() {
        val before = DeviceCard(
            battery = 40,
            charging = false,
            channel = 1,
            title = "旧曲名",
            profile = io.github.laozhudu.linkplay.protocol.dlna.DeviceProfile.LINKPLAY,
        )

        // 心跳这一轮只拿到播放态（没有电量/声道）
        val after = CardMerge.apply(
            before,
            DeviceSnapshot(title = "三打白骨精", playing = true, volume = 34, plicount = 28, plicurr = 3),
        )

        assertEquals(40, after.battery)     // ← 关键：不能被清空
        assertEquals(1, after.channel)      // ← 声道也不能被清回 0
        assertFalse(after.charging)
        assertEquals("三打白骨精", after.title)
        assertEquals(34, after.volume)
        assertTrue(after.playing == true)
        assertEquals(io.github.laozhudu.linkplay.protocol.dlna.DeviceProfile.LINKPLAY, after.profile)
    }

    @Test
    fun `control-info snapshot fills battery and channel`() {
        val after = CardMerge.apply(
            DeviceCard(volume = 10),
            DeviceSnapshot(volume = 41, mute = false, channel = 2, batteryPercent = 40, onBattery = true),
        )
        assertEquals(41, after.volume)
        assertEquals(2, after.channel)
        assertEquals(40, after.battery)
        assertFalse(after.charging)   // 在用电池 → 不是充电态
    }

    @Test
    fun `charging is true when device reports on power`() {
        val after = CardMerge.apply(DeviceCard(), DeviceSnapshot(batteryPercent = 100, onBattery = false))
        assertEquals(100, after.battery)
        assertTrue(after.charging)
    }

    @Test
    fun `blank snapshot changes nothing`() {
        val before = DeviceCard(battery = 40, channel = 1, title = "曲名", volume = 33, playing = true)
        assertEquals(before, CardMerge.apply(before, DeviceSnapshot()))
    }

    @Test
    fun `empty card stays empty rather than inventing values`() {
        val after = CardMerge.apply(DeviceCard(), DeviceSnapshot())
        assertNull(after.battery)
        assertNull(after.channel)
        assertEquals(0, after.volume)
    }

    @Test
    fun `battery recovers on the next successful read`() {
        // 先有电量 → 一轮只拿到播放态（保留）→ 再一轮拿到新电量（更新）
        val first = CardMerge.apply(DeviceCard(), DeviceSnapshot(batteryPercent = 40, onBattery = true))
        val kept = CardMerge.apply(first, DeviceSnapshot(playing = true))
        val updated = CardMerge.apply(kept, DeviceSnapshot(batteryPercent = 39, onBattery = true))
        assertEquals(40, kept.battery)
        assertEquals(39, updated.battery)
    }
}
