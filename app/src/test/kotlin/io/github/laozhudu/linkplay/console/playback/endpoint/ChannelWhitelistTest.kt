package io.github.laozhudu.linkplay.console.playback.endpoint

import io.github.laozhudu.linkplay.console.playback.DeviceProfileCache
import io.github.laozhudu.linkplay.protocol.dlna.DeviceCapabilities
import io.github.laozhudu.linkplay.protocol.dlna.DeviceGroup
import io.github.laozhudu.linkplay.protocol.dlna.DeviceProfile
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * **通道判定必须是白名单**（用户 2026-11-07 纠正）。
 *
 * > 「doss 系列是适配通用 upnp 的，但通用 upnp 不一定是 doss，doss 有特殊标识的」
 *
 * 这句话的技术含义是：
 *
 * - DOSS **是** UPnP 设备（有 AVTransport + RenderingControl）
 * - 所以"是 UPnP"推不出"是 DOSS"
 * - 判定只能反过来：**有 DOSS 的私有标识**才算 DOSS
 *
 * 上一版写的是 `group == OtherPlayer → UPnP，否则 → DOSS`，也就是**反选**。
 * 它默认"世界上只有两种设备"，于是 `DeviceGroup.Other`（还没探全 / 不可控）
 * 会被塞进 DOSS 分支，一路发 LinkPlay 私有命令 —— 全部静默失败。
 */
class ChannelWhitelistTest {

    @Before
    fun setUp() = DeviceProfileCache.clear()

    @After
    fun tearDown() = DeviceProfileCache.clear()

    @Test
    fun `只有同时有私有队列和组网的才算 DOSS`() {
        // MySpeaker 的判据是 `supportsPlayQueue && supportsMultiroom`，
        // 这两项都是 LinkPlay 私有扩展 —— 通用设备永远不会同时具备。
        assertEquals(DeviceGroup.MySpeaker, DeviceCapabilities.LINKPLAY.group)

        assertNotEquals(
            "通用渲染器不是 DOSS",
            DeviceGroup.MySpeaker,
            DeviceCapabilities.genericDmr(hasRenderingControl = true).group,
        )
        assertNotEquals(
            "什么都没探到的设备更不是 DOSS",
            DeviceGroup.MySpeaker,
            DeviceCapabilities.NONE.group,
        )
    }

    @Test
    fun `只声明了队列但不会组网的设备不算 DOSS`() {
        // 反选逻辑下这类设备会被当成 DOSS。按白名单就不会。
        val half = DeviceCapabilities.genericDmr(hasRenderingControl = true)
            .copy(supportsPlayQueue = true, supportsMultiroom = false)

        assertNotEquals(
            "缺了组网这一半，就不是 MySpeaker 档",
            DeviceGroup.MySpeaker,
            half.group,
        )
    }

    @Test
    fun `没探测过的设备按通用渲染器处理，不按 DOSS`() {
        // 这条挡的是"未知设备功能全开"：未知 ≠ DOSS。
        // 按 DOSS 处理会画出组网/预设键/设备侧队列，全是按了没反应的按钮。
        val unknown = DeviceProfileCache.capabilitiesOf("10.0.0.99")

        assertFalse("未知设备不该被当成 DOSS", unknown.group == DeviceGroup.MySpeaker)
        assertFalse("未知设备不该被认定支持私有队列", unknown.supportsPlayQueue)
        assertFalse("未知设备不该被认定支持组网", unknown.supportsMultiroom)
        assertFalse("未知设备不该被认定有预设键", unknown.supportsPresets)
        assertNotEquals(
            "类型标签也不该写 DOSS",
            DeviceProfile.LINKPLAY,
            DeviceProfileCache.of("10.0.0.99"),
        )
    }

    @Test
    fun `探测确认后立刻按真实能力走`() {
        DeviceProfileCache.put(
            "10.0.0.10",
            DeviceProfile.LINKPLAY,
            DeviceCapabilities.LINKPLAY,
        )

        assertTrue("确认是 DOSS 就该认", DeviceProfileCache.isConfirmedDoss("10.0.0.10"))
        assertTrue(DeviceProfileCache.supportsDeviceQueue("10.0.0.10"))
    }

    @Test
    fun `已确认是音箱的，不会被一次描述推断降级`() {
        // 真机场景（用户 2026-11-07）：
        //   扫描认出了音箱 → 添加 → 立刻重探 → 新入网音箱的 httpapi 还没起好
        //   → 退到描述文件 → 有 AVTransport → 判成"通用播放设备" → 掉出音箱组
        //
        // 修复：描述推断只是**弱结论**，不许把已确认的 DOSS 降级。
        DeviceProfileCache.put("10.0.0.12", DeviceProfile.LINKPLAY, DeviceCapabilities.LINKPLAY)
        assertTrue(DeviceProfileCache.isConfirmedDoss("10.0.0.12"))

        DeviceProfileCache.putWeak(
            "10.0.0.12",
            DeviceProfile.GENERIC_DMR,
            DeviceCapabilities.genericDmr(hasRenderingControl = true),
        )

        assertTrue(
            "一次 httpapi 超时不该把音箱踢出音箱组",
            DeviceProfileCache.isConfirmedDoss("10.0.0.12"),
        )
    }

    @Test
    fun `弱结论可以把未知设备认成通用播放设备`() {
        // 反方向必须允许：从不认识 → 认出是通用设备，这是进步不是降级。
        DeviceProfileCache.putWeak(
            "10.0.0.13",
            DeviceProfile.GENERIC_DMR,
            DeviceCapabilities.genericDmr(hasRenderingControl = true),
        )

        assertFalse(DeviceProfileCache.isConfirmedDoss("10.0.0.13"))
        assertTrue("至少知道它能出声", DeviceProfileCache.capabilitiesOf("10.0.0.13").supportsVolume)
    }

    @Test
    fun `强结论可以纠正弱结论`() {
        // 之前只拉到描述判成通用设备，这次 httpapi 答上来了 → 应该升格成音箱。
        val generic = DeviceCapabilities.genericDmr(hasRenderingControl = true)
        DeviceProfileCache.putWeak("10.0.0.14", DeviceProfile.GENERIC_DMR, generic)
        assertFalse(DeviceProfileCache.isConfirmedDoss("10.0.0.14"))

        DeviceProfileCache.put("10.0.0.14", DeviceProfile.LINKPLAY, DeviceCapabilities.LINKPLAY)

        assertTrue("强结论必须能把它纠正过来", DeviceProfileCache.isConfirmedDoss("10.0.0.14"))
    }

    @Test
    fun `通用设备确认后不冒充 DOSS`() {
        DeviceProfileCache.put(
            "10.0.0.11",
            DeviceProfile.GENERIC_DMR,
            DeviceCapabilities.genericDmr(hasRenderingControl = true),
        )

        assertFalse(DeviceProfileCache.isConfirmedDoss("10.0.0.11"))
        assertFalse("通用设备没有私有队列", DeviceProfileCache.supportsDeviceQueue("10.0.0.11"))
        // 但它是能出声的 —— 别把它当成不可控设备
        assertTrue(DeviceProfileCache.capabilitiesOf("10.0.0.11").supportsVolume)
    }
}
