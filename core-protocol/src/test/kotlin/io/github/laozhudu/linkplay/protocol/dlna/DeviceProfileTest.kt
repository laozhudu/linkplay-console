package io.github.laozhudu.linkplay.protocol.dlna

import io.github.laozhudu.linkplay.protocol.upnp.DeviceDescription
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 能力判档的单测。
 *
 * 样本全部是真实描述：`AIR_RECEIVER_XML` 是 2026-11-03 在真机
 * `curl http://192.168.99.175:3500/` 原样抓下来的（电视盒子 E900V22C + SoftMedia AirReceiver），
 * 其余按真机 `getStatusEx`/描述结构裁剪。用真样本而不是编造，才能保证
 * 「描述里有 QPlay 节点但不是 QPlay 服务」这类坑被测到。
 */
class DeviceProfileTest {

    // ---------------- 真实样本 ----------------

    /** 真机：电视盒子 192.168.99.175:3500（完整原始描述，只去掉了 icon 列表体积）。 */
    private val airReceiverXml = """
        <?xml version="1.0" encoding="UTF-8"?>
        <root configId="16023458" xmlns="urn:schemas-upnp-org:device-1-0" xmlns:dlna="urn:schemas-dlna-org:device-1-0">
          <specVersion><major>1</major><minor>1</minor></specVersion>
          <device>
            <deviceType>urn:schemas-upnp-org:device:MediaRenderer:1</deviceType>
            <friendlyName>E900V22C-175[DLNA]</friendlyName>
            <manufacturer>SoftMedia Inc.</manufacturer>
            <manufacturerURL>http://www.remotetogo.com</manufacturerURL>
            <modelDescription>AirReceiver - Media Renderer</modelDescription>
            <modelName>AirReceiver</modelName>
            <modelNumber>01</modelNumber>
            <UDN>uuid:81adb0b2-ade5-4526-a3cf-adc6e1db9e01</UDN>
            <qq:X_QPlay_SoftwareCapability xmlns:qq="http://www.tencent.com">QPlay:1</qq:X_QPlay_SoftwareCapability>
            <dlna:X_DLNADOC xmlns:dlna="urn:schemas-dlna-org:device-1-0">DMR-1.50</dlna:X_DLNADOC>
            <serviceList>
              <service>
                <serviceType>urn:schemas-upnp-org:service:AVTransport:1</serviceType>
                <serviceId>urn:upnp-org:serviceId:AVTransport</serviceId>
                <SCPDURL>/AVTransport/81adb0b2-ade5-4526-a3cf-adc6e1db9e01/scpd.xml</SCPDURL>
                <controlURL>/AVTransport/81adb0b2-ade5-4526-a3cf-adc6e1db9e01/control.xml</controlURL>
                <eventSubURL>/AVTransport/81adb0b2-ade5-4526-a3cf-adc6e1db9e01/event.xml</eventSubURL>
              </service>
              <service>
                <serviceType>urn:schemas-upnp-org:service:ConnectionManager:1</serviceType>
                <serviceId>urn:upnp-org:serviceId:ConnectionManager</serviceId>
                <SCPDURL>/ConnectionManager/81adb0b2-ade5-4526-a3cf-adc6e1db9e01/scpd.xml</SCPDURL>
                <controlURL>/ConnectionManager/81adb0b2-ade5-4526-a3cf-adc6e1db9e01/control.xml</controlURL>
                <eventSubURL>/ConnectionManager/81adb0b2-ade5-4526-a3cf-adc6e1db9e01/event.xml</eventSubURL>
              </service>
              <service>
                <serviceType>urn:schemas-upnp-org:service:RenderingControl:1</serviceType>
                <serviceId>urn:upnp-org:serviceId:RenderingControl</serviceId>
                <SCPDURL>/RenderingControl/81adb0b2-ade5-4526-a3cf-adc6e1db9e01/scpd.xml</SCPDURL>
                <controlURL>/RenderingControl/81adb0b2-ade5-4526-a3cf-adc6e1db9e01/control.xml</controlURL>
                <eventSubURL>/RenderingControl/81adb0b2-ade5-4526-a3cf-adc6e1db9e01/event.xml</eventSubURL>
              </service>
            </serviceList>
          </device>
        </root>
    """.trimIndent()

    private val airReceiver = DeviceDescription.parse("http://192.168.99.175:3500/", airReceiverXml)

    /** 真机：LinkPlay 音箱（`.164`）的描述结构 —— 私有 PlayQueue + QPlay 服务都在 serviceList 里。 */
    private val linkPlayXml = """
        <?xml version="1.0" encoding="UTF-8"?>
        <root xmlns="urn:schemas-upnp-org:device-1-0">
          <specVersion><major>1</major><minor>0</minor></specVersion>
          <device>
            <deviceType>urn:schemas-upnp-org:device:MediaRenderer:1</deviceType>
            <friendlyName>DOSS DS-1831_400C</friendlyName>
            <manufacturer>LinkPlay</manufacturer>
            <modelName>DS-1831</modelName>
            <UDN>uuid:FF31F0A1-1234-4C9E-9B2A-0E3A1B2C3D4E</UDN>
            <serviceList>
              <service>
                <serviceType>urn:schemas-upnp-org:service:AVTransport:1</serviceType>
                <serviceId>urn:upnp-org:serviceId:AVTransport</serviceId>
                <controlURL>/upnp/control/AVTransport1</controlURL>
                <eventSubURL>/upnp/event/AVTransport1</eventSubURL>
                <SCPDURL>/upnp/AVTransport1.xml</SCPDURL>
              </service>
              <service>
                <serviceType>urn:schemas-upnp-org:service:RenderingControl:1</serviceType>
                <serviceId>urn:upnp-org:serviceId:RenderingControl</serviceId>
                <controlURL>/upnp/control/RenderingControl1</controlURL>
                <eventSubURL>/upnp/event/RenderingControl1</eventSubURL>
                <SCPDURL>/upnp/RenderingControl1.xml</SCPDURL>
              </service>
              <service>
                <serviceType>urn:schemas-wiimu-com:service:PlayQueue:1</serviceType>
                <serviceId>urn:wiimu-com:serviceId:PlayQueue</serviceId>
                <controlURL>/upnp/control/PlayQueue1</controlURL>
                <eventSubURL>/upnp/event/PlayQueue1</eventSubURL>
                <SCPDURL>/upnp/PlayQueue1.xml</SCPDURL>
              </service>
              <service>
                <serviceType>urn:schemas-tencent-com:service:QPlay:1</serviceType>
                <serviceId>urn:tencent-com:serviceId:QPlay</serviceId>
                <controlURL>/upnp/control/QPlay1</controlURL>
                <eventSubURL>/upnp/event/QPlay1</eventSubURL>
                <SCPDURL>/upnp/QPlay1.xml</SCPDURL>
              </service>
            </serviceList>
          </device>
        </root>
    """.trimIndent()

    private val linkPlay = DeviceDescription.parse("http://192.168.99.164:49152/description.xml", linkPlayXml)

    /** 只声明 AVTransport（没有 RenderingControl）的极简渲染器。 */
    private val avOnlyXml = """
        <?xml version="1.0" encoding="UTF-8"?>
        <root xmlns="urn:schemas-upnp-org:device-1-0">
          <device>
            <deviceType>urn:schemas-upnp-org:device:MediaRenderer:1</deviceType>
            <friendlyName>Old DMR</friendlyName>
            <serviceList>
              <service>
                <serviceType>urn:schemas-upnp-org:service:AVTransport:1</serviceType>
                <controlURL>/avt/control</controlURL>
              </service>
            </serviceList>
          </device>
        </root>
    """.trimIndent()

    /** 只有 MediaServer（NAS）：响应 SSDP，但完全不可播。 */
    private val mediaServerXml = """
        <?xml version="1.0" encoding="UTF-8"?>
        <root xmlns="urn:schemas-upnp-org:device-1-0">
          <device>
            <deviceType>urn:schemas-upnp-org:device:MediaServer:1</deviceType>
            <friendlyName>fnOS DLNA</friendlyName>
            <serviceList>
              <service>
                <serviceType>urn:schemas-upnp-org:service:ContentDirectory:1</serviceType>
                <controlURL>/cds/control</controlURL>
              </service>
            </serviceList>
          </device>
        </root>
    """.trimIndent()

    private val mediaServer = DeviceDescription.parse("http://192.168.99.10:8200/description.xml", mediaServerXml)

    // ---------------- 判档 ----------------

    /** 真机事实：AirReceiver 必须判成通用渲染器，且不能声称有 PlayQueue。 */
    @Test
    fun `真实 AirReceiver 描述判定为通用 DLNA`() {
        val (profile, caps) = detect(airReceiver, httpapiReachable = null)
        assertEquals(DeviceProfile.GENERIC_DMR, profile)
        assertFalse(caps.supportsPlayQueue)
    }

    /** 真机描述里有 `<qq:X_QPlay_SoftwareCapability>` 节点，但它不是服务 —— 不能据此判成 LinkPlay。 */
    @Test
    fun `描述里的 QPlay 能力节点不足以判成 LinkPlay`() {
        // 前提：这份 XML 里确实有 QPlay 字样（否则这条测试就失去意义了）
        assertTrue(airReceiverXml.contains("QPlay"))
        val (profile, _) = detect(airReceiver, httpapiReachable = false)
        assertEquals(DeviceProfile.GENERIC_DMR, profile)
    }

    /** httpapi 答得上来就是 LinkPlay 的铁证（白标固件描述里一个字品牌都没有也能认）。 */
    @Test
    fun `httpapi 可达直接判成 LinkPlay`() {
        val (profile, caps) = detect(mediaServer, httpapiReachable = true)
        assertEquals(DeviceProfile.LINKPLAY, profile)
        assertTrue(caps.supportsPlayQueue)
        assertTrue(caps.supportsHttpapi)
    }

    /** 声明了私有 PlayQueue 服务 → LinkPlay，与 httpapi 是否探过无关。 */
    @Test
    fun `声明 PlayQueue 服务即判成 LinkPlay`() {
        val (profile, caps) = detect(linkPlay, httpapiReachable = null)
        assertEquals(DeviceProfile.LINKPLAY, profile)
        // 全能力
        assertTrue(caps.supportsPlayQueue)
        assertTrue(caps.supportsMultiroom)
        assertTrue(caps.supportsPresets)
        assertTrue(caps.supportsHttpapi)
        assertTrue(caps.supportsVolume)
        assertTrue(caps.supportsSeek)
        assertTrue(caps.supportsNextPrev)
        assertTrue(caps.supportsMute)
    }

    /** LinkPlay 的 URL 必须解析成绝对地址（否则 POST 会打到错误主机）。 */
    @Test
    fun `LinkPlay 描述的控制地址解析为绝对地址`() {
        assertEquals(
            "http://192.168.99.164:49152/upnp/control/AVTransport1",
            linkPlay.avTransportControlUrl,
        )
    }

    /** 只有 AVTransport、没有私有服务：通用渲染器，但 PlayQueue/组网/预设/httpapi 全无。 */
    @Test
    fun `只有 AVTransport 的设备能力收敛`() {
        val (profile, caps) = detect(DeviceDescription.parse("http://10.0.0.9:8080/", avOnlyXml), false)
        assertEquals(DeviceProfile.GENERIC_DMR, profile)
        assertFalse(caps.supportsPlayQueue)
        assertFalse(caps.supportsMultiroom)
        assertFalse(caps.supportsPresets)
        assertFalse(caps.supportsHttpapi)
        assertFalse(caps.supportsNextPrev)
    }

    /** 没有 RenderingControl 的渲染器：音量/静音不能装作能用。 */
    @Test
    fun `缺少 RenderingControl 时音量与静音不可用`() {
        val (_, caps) = detect(DeviceDescription.parse("http://10.0.0.9:8080/", avOnlyXml), null)
        assertFalse(caps.supportsVolume)
        assertFalse(caps.supportsMute)
        // Seek 是 AVTransport 的标准动作，仍然可用
        assertTrue(caps.supportsSeek)
    }

    /** AirReceiver 带 RenderingControl：音量与静音可用。 */
    @Test
    fun `带 RenderingControl 的渲染器音量可用`() {
        val (_, caps) = detect(airReceiver, null)
        assertTrue(caps.supportsVolume)
        assertTrue(caps.supportsMute)
    }

    /** 连 AVTransport 都没有（NAS）→ UNKNOWN，能力全 false。 */
    @Test
    fun `没有 AVTransport 的设备判为 UNKNOWN 且能力全无`() {
        val (profile, caps) = detect(mediaServer, httpapiReachable = false)
        assertEquals(DeviceProfile.UNKNOWN, profile)
        assertEquals(DeviceCapabilities.NONE, caps)
    }

    /** 探过 httpapi 且明确不可达，也不能因此把渲染器降级成 UNKNOWN。 */
    @Test
    fun `httpapi 不可达不影响通用渲染器判档`() {
        val (profile, caps) = detect(airReceiver, httpapiReachable = false)
        assertEquals(DeviceProfile.GENERIC_DMR, profile)
        assertTrue(caps.supportsVolume)
    }

    /** 有没有 httpapi 都不改变通用渲染器的私有能力（永远 false）。 */
    @Test
    fun `通用渲染器的私有能力恒为 false`() {
        // 只探「没探过」与「探过但不可达」两种：true 会被判成 LinkPlay，不适用
        for (reachable in listOf(null, false)) {
            val (_, caps) = detect(airReceiver, reachable)
            assertFalse(caps.supportsPlayQueue)
            assertFalse(caps.supportsMultiroom)
            assertFalse(caps.supportsPresets)
            assertFalse(caps.supportsHttpapi)
        }
    }

    /** 服务类型大小写不该影响判档（不同固件写法不一致）。 */
    @Test
    fun `服务类型大小写不影响判档`() {
        val upper = DeviceDescription.parse(
            "http://x/",
            linkPlayXml.replace("urn:schemas-wiimu-com:service:PlayQueue:1", "URN:SCHEMAS-WIIMU-COM:SERVICE:PLAYQUEUE:1"),
        )
        assertEquals(DeviceProfile.LINKPLAY, detect(upper, null).first)
    }

    /** 手动加设备时的探测顺序：先 LinkPlay 的固定路径，再根路径（AirReceiver 只有它管用）。 */
    @Test
    fun `描述地址候选先 description xml 再根路径`() {
        assertEquals(
            listOf("http://192.168.99.175:3500/description.xml", "http://192.168.99.175:3500/"),
            DescriptionEndpoints.candidates("192.168.99.175", 3500),
        )
        assertEquals(
            listOf("http://192.168.99.164:49152/description.xml", "http://192.168.99.164:49152/"),
            DescriptionEndpoints.candidates("192.168.99.164", 49152),
        )
    }

    /**
     * **探活用的候选地址必须窄**。
     *
     * 这条测试存在的原因：我为了"手动填 IP 也能加通用设备"，一度往
     * `candidates()` 里塞了 `:3500` 和 `:80` 的候选。
     *
     * 那个函数被 `probeOne` 每轮对每台设备调用一次 —— 三台音箱 × 每轮 × 4 个
     * 多出来的地址 × 每次可能等超时，探活会明显变慢，而用户根本看不出为什么。
     *
     * 正确做法是分成两个函数：探活用窄的，手动添加用宽的。
     */
    @Test
    fun `探活候选地址只有两个，不许加`() {
        assertEquals(
            "探活每轮每台都要跑，多一个地址就多一次可能超时",
            2,
            DescriptionEndpoints.candidates("192.168.99.164", 49152).size,
        )
    }

    @Test
    fun `手动添加试更多端口以覆盖未知设备`() {
        val urls = DescriptionEndpoints.manualCandidates("192.168.99.175")

        // 用户手上可能是 DOSS（49152）、电视盒子（3500）或国产盒子（80）
        assertTrue("要试 LinkPlay 的 49152", urls.any { it.contains(":49152/") })
        assertTrue("要试 AirReceiver 的 3500", urls.any { it.contains(":3500/") })
        assertTrue("要试常见的 80", urls.any { it.contains(":80/") })
        assertEquals("同一地址不该重复", urls.size, urls.distinct().size)
    }

    /** 档位自带的判断方法（界面用）。 */
    @Test
    fun `档位的便捷判断`() {
        assertTrue(DeviceProfile.LINKPLAY.isLinkPlay)
        assertFalse(DeviceProfile.LINKPLAY.isGenericDmr)
        assertTrue(DeviceProfile.GENERIC_DMR.isGenericDmr)
        assertFalse(DeviceProfile.UNKNOWN.isLinkPlay)
        assertFalse(DeviceProfile.UNKNOWN.isGenericDmr)
    }
}
