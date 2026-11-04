package io.github.laozhudu.linkplay.protocol.dlna

import io.github.laozhudu.linkplay.protocol.upnp.DeviceDescription
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 能力位 —— **界面渲染的唯一判据**。
 *
 * ## 为什么这些断言重要
 *
 * 改造前，界面用 `if (profile == GENERIC_DMR)` 判断要不要显示「屏蔽 / 声道 / 组网」。
 * 那是按**设备类型**判断。加第三类设备（电视、未来的蓝牙遥控）时，
 * 每个判断点都要加一个分支，而且必然有地方漏 —— 漏掉的表现是"按了没反应"。
 *
 * 改成按能力位之后，新设备只要能力表填对，界面自动正确。
 * 所以这张能力表必须被**逐项锁死**：填错一项 = 界面上多一个假按钮。
 *
 * 断言的是"这台设备能做什么"，不是"某个枚举等于某个值" ——
 * 后者在重构时会变成实现的镜像。
 */
class DeviceCapabilitiesTest {

    @Test
    fun `linkplay has every business capability`() {
        val c = DeviceCapabilities.LINKPLAY
        assertTrue("整单下发", c.supportsPlayQueue)
        assertTrue("多房间", c.supportsMultiroom)
        assertTrue("机身预设键", c.supportsPresets)
        assertTrue("私有 httpapi", c.supportsHttpapi)
        assertTrue("音量", c.supportsVolume)
        assertTrue("跳转", c.supportsSeek)
        assertTrue("上下首", c.supportsNextPrev)
        assertTrue("静音", c.supportsMute)
        assertTrue("声道", c.supportsChannel)
    }

    @Test
    fun `generic dmr has no vendor private capability`() {
        // 这四项是 LinkPlay 私有的。给电视盒子显示这些按钮 = 按了没反应
        val c = DeviceCapabilities.genericDmr(hasRenderingControl = true)
        assertFalse("通用渲染器没有 PlayQueue", c.supportsPlayQueue)
        assertFalse("通用渲染器没有多房间", c.supportsMultiroom)
        assertFalse("通用渲染器没有机身预设键", c.supportsPresets)
        assertFalse("通用渲染器没有 httpapi", c.supportsHttpapi)
        assertFalse("通用渲染器没有声道（实测 AirReceiver 不认 SetChannel）", c.supportsChannel)
    }

    @Test
    fun `generic dmr with rendering control can do volume mute and seek`() {
        val c = DeviceCapabilities.genericDmr(hasRenderingControl = true)
        assertTrue(c.supportsVolume)
        assertTrue(c.supportsMute)
        assertTrue(c.supportsSeek)
    }

    @Test
    fun `generic dmr without rendering control cannot do volume`() {
        // 有的渲染器只有 AVTransport，没有 RenderingControl。这时音量按钮必须消失，
        // 而不是点了之后设备回 500
        val c = DeviceCapabilities.genericDmr(hasRenderingControl = false)
        assertFalse(c.supportsVolume)
        assertFalse(c.supportsMute)
        assertTrue("Seek 是 AVTransport 的必需动作", c.supportsSeek)
    }

    @Test
    fun `generic dmr cannot do next or previous`() {
        // 设备没有自己的队列 → AVTransport.Next 回 701 transition not available。
        // 切歌只能靠我们重投下一首，所以这一位必须是 false
        assertFalse(DeviceCapabilities.genericDmr(hasRenderingControl = true).supportsNextPrev)
    }

    @Test
    fun `none supports nothing`() {
        val c = DeviceCapabilities.NONE
        assertFalse(c.supportsPlayQueue)
        assertFalse(c.supportsMultiroom)
        assertFalse(c.supportsPresets)
        assertFalse(c.supportsHttpapi)
        assertFalse(c.supportsVolume)
        assertFalse(c.supportsSeek)
        assertFalse(c.supportsNextPrev)
        assertFalse(c.supportsMute)
        assertFalse(c.supportsChannel)
    }

    /**
     * 用**生产路径**（`DeviceDescription.parse`）构造描述，而不是手搓 data class。
     *
     * 理由：判档的真实输入是 XML。手搓 data class 会绕开解析器，
     * 万一解析器把 `<serviceList>` 读错，测试照样绿。
     */
    private fun parse(vararg services: String): DeviceDescription {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <root xmlns="urn:schemas-upnp-org:device-1-0">
              <device>
                <deviceType>urn:schemas-upnp-org:device:MediaRenderer:1</deviceType>
                <friendlyName>test-device</friendlyName>
                <UDN>uuid:test-udn</UDN>
                <serviceList>
                  ${services.joinToString("\n")}
                </serviceList>
              </device>
            </root>
        """.trimIndent()
        return DeviceDescription.parse("http://10.0.0.2:3500/", xml)
    }

    private fun svc(type: String, control: String) = """
        <service>
          <serviceType>$type</serviceType>
          <controlURL>$control</controlURL>
        </service>
    """.trimIndent()

    @Test
    fun `detect marks a renderer without play queue as generic dmr`() {
        val desc = parse(
            svc("urn:schemas-upnp-org:service:AVTransport:1", "/upnp/control/rendertransport1"),
            svc("urn:schemas-upnp-org:service:RenderingControl:1", "/upnp/control/rendercontrol1"),
        )
        val (profile, caps) = detect(desc, httpapiReachable = false)
        assertTrue(profile == DeviceProfile.GENERIC_DMR)
        // 关键：判档的同时必须给出能力表，不能只给类型
        assertTrue(caps.supportsVolume)
        assertFalse(caps.supportsMultiroom)
    }

    @Test
    fun `detect never grants multiroom to a device without the private service`() {
        // 这是"加第三类设备不用改界面"的核心保证：
        // 只要描述里没有 wiimu PlayQueue，就绝不会有 multiroom / presets / channel
        val desc = parse(svc("urn:schemas-upnp-org:service:AVTransport:1", "/avt"))
        val caps = detect(desc, httpapiReachable = false).second
        assertFalse(caps.supportsMultiroom)
        assertFalse(caps.supportsPresets)
        assertFalse(caps.supportsChannel)
        assertFalse(caps.supportsPlayQueue)
    }
}
