package io.github.laozhudu.linkplay.protocol.snapshot

import io.github.laozhudu.linkplay.protocol.DeviceHttpClient
import io.github.laozhudu.linkplay.protocol.SlaveDevice
import io.github.laozhudu.linkplay.protocol.upnp.ControlDeviceInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 快照合并规则 + 最短路径读取。
 *
 * 这些用例锁的是用户报的「电量有时有有时没有」：
 * 3 秒心跳（只读播放态）与设备信息轮询（读电量）交替跑，
 * 任何一方整体覆盖都会让电量闪没。合并规则必须"没报就保留"。
 */
class DeviceSnapshotTest {

    private val withBattery = DeviceSnapshot(
        batteryPercent = 40,
        onBattery = true,
        deviceName = "doss3",
        channel = 1,
        volume = 41,
        answered = setOf(DeviceSnapshot.Channel.CONTROL_INFO),
    )

    @Test
    fun `player-only poll does not erase battery or channel`() {
        val playerOnly = DeviceSnapshot(
            title = "三打白骨精",
            playing = true,
            volume = 34,
            answered = setOf(DeviceSnapshot.Channel.PLAYER_STATUS),
        )

        val merged = withBattery.mergeWith(playerOnly)

        assertEquals(40, merged.batteryPercent)
        assertEquals("doss3", merged.deviceName)
        assertEquals(1, merged.channel)
        assertEquals(34, merged.volume)      // 音量是真值，允许覆盖
        assertEquals("三打白骨精", merged.title)
        assertTrue(merged.playing == true)
    }

    @Test
    fun `status poll does not erase title`() {
        val withTitle = DeviceSnapshot(title = "三打白骨精", playing = true)
        val statusOnly = DeviceSnapshot(batteryPercent = 40, deviceName = "doss3")

        val merged = withTitle.mergeWith(statusOnly)

        assertEquals("三打白骨精", merged.title)
        assertEquals(true, merged.playing)
        assertEquals(40, merged.batteryPercent)
    }

    @Test
    fun `blank title never overwrites a real one`() {
        val merged = DeviceSnapshot(title = "真曲名").mergeWith(DeviceSnapshot(title = "  "))
        assertEquals("真曲名", merged.title)
    }

    @Test
    fun `slave list only overwrites when actually read`() {
        val grouped = DeviceSnapshot(
            slaves = listOf(SlaveDevice(ip = "10.10.10.92", inGroup = true, version = null, uuid = null, name = "doss3", ssid = null, volume = 48)),
            slavesRead = true,
        )

        // 一次失败的读取（没读到列表）不能把整组抹掉
        val failedRead = DeviceSnapshot(slaves = emptyList(), slavesRead = false)
        assertEquals(1, grouped.mergeWith(failedRead).slaves.size)

        // 真的读到空列表（组解散了）才清
        assertEquals(0, grouped.mergeWith(DeviceSnapshot(slavesRead = true)).slaves.size)
    }

    @Test
    fun `channels accumulate so we can tell who answered`() {
        val merged = DeviceSnapshot(answered = setOf(DeviceSnapshot.Channel.CONTROL_INFO))
            .mergeWith(DeviceSnapshot(answered = setOf(DeviceSnapshot.Channel.PLAYER_STATUS)))
        assertEquals(
            setOf(DeviceSnapshot.Channel.CONTROL_INFO, DeviceSnapshot.Channel.PLAYER_STATUS),
            merged.answered,
        )
    }

    @Test
    fun `empty snapshot is a no-op`() {
        val merged = withBattery.mergeWith(DeviceSnapshot())
        assertEquals(withBattery, merged)
    }
}

/** 最短路径：一次读取里各通道被问了几次、电量能不能拿到。 */
class DeviceSnapshotReaderTest {

    /** 记录被问过哪些 httpapi 命令；`upnp` 为 null 模拟"没有 UPnP 描述"。 */
    private class FakeHttp(
        private val answers: Map<String, String> = emptyMap(),
    ) : DeviceHttpClient() {
        val asked = ArrayList<String>()

        override fun get(deviceIp: String, command: String, path: String, readTimeoutMs: Int): String {
            asked.add(command)
            return answers[command] ?: throw java.io.IOException("no answer for $command")
        }
    }

    private val controlInfoBody = """
        <CurrentVolume>41</CurrentVolume><CurrentMute>0</CurrentMute><CurrentChannel>1</CurrentChannel>
        <SlaveList>{ &quot;slaves&quot;: 0 }</SlaveList>
        <Status>{ &quot;uuid&quot;: &quot;FF31F0174FE5C9B2A2ACEAE3&quot;,
        &quot;battery_percent&quot;: &quot;40&quot;, &quot;battery&quot;: &quot;1&quot;,
        &quot;DeviceName&quot;: &quot;doss3&quot;, &quot;firmware&quot;: &quot;3.6.5310&quot; }</Status>
    """.trimIndent()

    private val playerBody = """
        {"type":"0","ch":"1","mode":"10","status":"play","curpos":"0","totlen":"0",
         "vol":"41","mute":"0","plicount":"3","plicurr":"1","Title":"e4b889"}
    """.trimIndent()

    @Test
    fun `happy path uses two requests and gets battery from control info`() {
        val http = FakeHttp(mapOf("getPlayerStatus" to playerBody))
        val reader = DeviceSnapshotReader(http, { ControlDeviceInfo.parse(controlInfoBody) })

        val snap = reader.read("192.168.99.153")!!

        assertEquals(40, snap.batteryPercent)
        assertEquals("doss3", snap.deviceName)
        assertEquals(1, snap.channel)
        assertEquals(41, snap.volume)
        assertTrue(snap.playing == true)
        // 主路径给了 SlaveList，就不该再问 getSlaveList；Status 已有电量，不该再问 getStatusEx
        assertEquals(listOf("getPlayerStatus"), http.asked)
        assertEquals(
            setOf(DeviceSnapshot.Channel.CONTROL_INFO, DeviceSnapshot.Channel.PLAYER_STATUS),
            snap.answered,
        )
    }

    @Test
    fun `without upnp it falls back to getStatusEx and still has battery`() {
        val http = FakeHttp(
            mapOf(
                "getStatusEx" to """{"uuid":"FF31F0174FE5C9B2A2ACEAE3","battery_percent":"40","battery":"1","DeviceName":"doss3"}""",
                "getPlayerStatus" to playerBody,
                "multiroom:getSlaveList" to """{ "slaves": 0 }""",
            ),
        )
        val reader = DeviceSnapshotReader(http, { null })

        val snap = reader.read("192.168.99.153")!!

        assertEquals(40, snap.batteryPercent)
        assertEquals("doss3", snap.deviceName)
        assertTrue(snap.slavesRead)
        assertTrue(DeviceSnapshot.Channel.STATUS_EX in snap.answered)
    }

    @Test
    fun `both channels down returns null so caller can mark offline`() {
        val reader = DeviceSnapshotReader(FakeHttp(), { null })
        assertNull(reader.read("10.0.0.1"))
    }

    @Test
    fun `control info without battery still falls back for the missing field`() {
        // 固件偶尔不带 battery_percent，这时补一次 getStatusEx —— 但不要重复问已知字段
        val body = controlInfoBody.replace("&quot;battery_percent&quot;: &quot;40&quot;,", "")
        val http = FakeHttp(
            mapOf(
                "getStatusEx" to """{"uuid":"X","battery_percent":"40","DeviceName":"doss3"}""",
                "getPlayerStatus" to playerBody,
            ),
        )
        val snap = DeviceSnapshotReader(http, { ControlDeviceInfo.parse(body) }).read("1.2.3.4")!!
        assertEquals(40, snap.batteryPercent)
        assertEquals("doss3", snap.deviceName)   // 这条来自主路径，未被覆盖
        assertTrue("getStatusEx" in http.asked)
    }
}
