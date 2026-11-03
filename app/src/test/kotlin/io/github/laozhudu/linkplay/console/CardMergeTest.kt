package io.github.laozhudu.linkplay.console

import io.github.laozhudu.linkplay.protocol.player.PlayerStatus
import io.github.laozhudu.linkplay.protocol.upnp.ControlDeviceInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 卡片合并规则。
 *
 * 这些用例锁的是两条真实故障：
 * 1. 每 3 秒的播放轮询把电量清空 → 「电量有时有有时没有」
 * 2. 同一轮询把声道清回默认 → 「声道切换后界面不变」
 */
class CardMergeTest {

    private val realGetPlayerStatus = """
        {"type":"0","ch":"2","mode":"10","loop":"0","eq":"0","status":"play",
         "curpos":"12000","totlen":"240000","vol":"34","mute":"0",
         "plicount":"28","plicurr":"3","Title":"e4b889e68993e799bde9aaa8e9aaa8","Artist":"",
         "Album":"","uri":""}
    """.trimIndent()

    private val realDeviceInfo = ControlDeviceInfo(
        volume = 41,
        mute = false,
        channel = 2,
        status = io.github.laozhudu.linkplay.protocol.LinkPlayStatus.parse(
            """{"uuid":"FF31F0174FE5C9B2A2ACEAE3","battery_percent":"40","battery":"1","DeviceName":"doss3"}""",
        ),
    )

    @Test
    fun `player status poll keeps battery and channel from previous card`() {
        // 上一轮已经有电量和声道（由 GetControlDeviceInfo 写入）
        val before = DeviceCard(battery = 40, channel = 1, title = "旧曲名", profile = io.github.laozhudu.linkplay.protocol.dlna.DeviceProfile.LINKPLAY)

        val after = CardMerge.applyPlayerStatus(before, PlayerStatus.parse(realGetPlayerStatus))

        assertEquals(40, after.battery)      // ← 关键：不能被轮询清空
        assertEquals(2, after.channel)       // getPlayerStatus 的 ch 是权威值，允许覆盖
        assertEquals(34, after.volume)
        assertTrue(after.playing)
        assertEquals(28, after.plicount)
        assertEquals(io.github.laozhudu.linkplay.protocol.dlna.DeviceProfile.LINKPLAY, after.profile)
    }

    @Test
    fun `player status poll keeps previous title when device reports Unknown`() {
        val before = DeviceCard(title = "三打白骨精")
        val status = PlayerStatus.parse(realGetPlayerStatus.replace("\"e4b889e68993e799bde9aaa8e9aaa8\"", "\"556E6B6E6F776E\""))
        assertEquals("三打白骨精", CardMerge.applyPlayerStatus(before, status).title)
    }

    @Test
    fun `device info fills channel battery and charging flag`() {
        val before = DeviceCard(volume = 10)
        val after = CardMerge.applyDeviceInfo(before, realDeviceInfo, battery = null)

        assertEquals(2, after.channel)
        assertEquals(41, after.volume)
        assertEquals(40, after.battery)
        // battery="1" → 在用电池 → 不是充电态
        assertFalse(after.charging)
    }

    @Test
    fun `device info without battery keeps previous value instead of nulling it`() {
        val before = DeviceCard(battery = 40, charging = false)
        val noBattery = ControlDeviceInfo(
            volume = 41,
            channel = 0,
            status = io.github.laozhudu.linkplay.protocol.LinkPlayStatus.parse("""{"uuid":"X"}"""),
        )
        val after = CardMerge.applyDeviceInfo(before, noBattery, battery = null)
        assertEquals(40, after.battery)
        assertEquals(0, after.channel)
    }

    @Test
    fun `charging is true when device is on power`() {
        val info = ControlDeviceInfo(
            status = io.github.laozhudu.linkplay.protocol.LinkPlayStatus.parse(
                """{"uuid":"FF310096A5760623673F81F1","battery_percent":"100","battery":"0"}""",
            ),
        )
        assertTrue(CardMerge.applyDeviceInfo(DeviceCard(), info, null).charging)
    }

    @Test
    fun `empty card stays empty rather than inventing values`() {
        val after = CardMerge.applyDeviceInfo(DeviceCard(), ControlDeviceInfo(), null)
        assertNull(after.battery)
        assertNull(after.channel)
        assertEquals(0, after.volume)
    }
}
