package io.github.laozhudu.linkplay.protocol.identity

import io.github.laozhudu.linkplay.protocol.upnp.DeviceDescription
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkPlayIdentityTest {

    /** 真机 192.168.99.153 的描述精简版（服务类型逐字取自实机）。 */
    private fun dossDescription() = DeviceDescription.parse(
        location = "http://192.168.99.153:49152/description.xml",
        xml = """
            <root>
              <device>
                <friendlyName>doss3</friendlyName>
                <manufacturer>DOSS 1010</manufacturer>
                <modelName>DOSS 1010 Player</modelName>
                <UDN>uuid:FF31F0174FE5C9B2A2ACEAE3</UDN>
                <serviceList>
                  <service>
                    <serviceType>urn:schemas-upnp-org:service:AVTransport:1</serviceType>
                    <controlURL>/upnp/control/rendertransport1</controlURL>
                  </service>
                  <service>
                    <serviceType>urn:schemas-upnp-org:service:RenderingControl:1</serviceType>
                    <controlURL>/upnp/control/rendercontrol1</controlURL>
                  </service>
                  <service>
                    <serviceType>urn:schemas-wiimu-com:service:PlayQueue:1</serviceType>
                    <controlURL>/upnp/control/PlayQueue1</controlURL>
                  </service>
                  <service>
                    <serviceType>urn:schemas-tencent-com:service:QPlay:1</serviceType>
                    <controlURL>/upnp/control/QPlay1</controlURL>
                  </service>
                </serviceList>
              </device>
            </root>
        """.trimIndent(),
    )

    /** 一台普通电视/功放：标准 DLNA 渲染器，没有私有服务。 */
    private fun genericRenderer() = DeviceDescription.parse(
        location = "http://192.168.99.60:8080/desc.xml",
        xml = """
            <root><device>
              <friendlyName>Living Room TV</friendlyName>
              <manufacturer>ACME</manufacturer>
              <modelName>TV-4K</modelName>
              <serviceList>
                <service>
                  <serviceType>urn:schemas-upnp-org:service:AVTransport:1</serviceType>
                  <controlURL>/avt</controlURL>
                </service>
                <service>
                  <serviceType>urn:schemas-upnp-org:service:RenderingControl:1</serviceType>
                  <controlURL>/rc</controlURL>
                </service>
              </serviceList>
            </device></root>
        """.trimIndent(),
    )

    /** NAS（MediaServer）：会被搜成「媒体服务器」，但不是播放设备。 */
    private fun nasDescription() = DeviceDescription.parse(
        location = "http://192.168.99.223:8200/rootDesc.xml",
        xml = """
            <root><device>
              <friendlyName>fnos</friendlyName>
              <manufacturer>fnOS</manufacturer>
              <modelName>fnOS</modelName>
              <serviceList>
                <service>
                  <serviceType>urn:schemas-upnp-org:service:ContentDirectory:1</serviceType>
                  <controlURL>/ctl/ContentDir</controlURL>
                </service>
              </serviceList>
            </device></root>
        """.trimIndent(),
    )

    @Test
    fun `真机 DOSS 被识别为 LinkPlay 系`() {
        assertTrue(LinkPlayIdentity.isLinkPlayFamily(dossDescription()))
    }

    @Test
    fun `普通电视不会被当成音箱`() {
        // 这条最关键：原来首页会把这种设备也列出来，点进去投流是错的
        assertFalse(LinkPlayIdentity.isLinkPlayFamily(genericRenderer()))
    }

    @Test
    fun `NAS 不会被当成播放设备`() {
        assertFalse(LinkPlayIdentity.isLinkPlayFamily(nasDescription()))
    }

    @Test
    fun `只有品牌名也能识别（品牌写在描述里的白标固件）`() {
        val desc = DeviceDescription.parse(
            "http://10.0.0.9/x.xml",
            """<root><device><friendlyName>Speaker</friendlyName>
               <manufacturer>LinkPlay Technology Inc.</manufacturer>
               <modelName>WiiMu-A31</modelName></device></root>""",
        )
        assertTrue(LinkPlayIdentity.isLinkPlayFamily(desc))
    }

    @Test
    fun `只有标准服务 + PlayQueue 也认（没有品牌字样时）`() {
        val desc = DeviceDescription.parse(
            "http://10.0.0.9/x.xml",
            """<root><device><friendlyName>Box</friendlyName><serviceList>
               <service><serviceType>urn:schemas-upnp-org:service:AVTransport:1</serviceType>
                 <controlURL>/a</controlURL></service>
               <service><serviceType>urn:schemas-upnp-org:service:RenderingControl:1</serviceType>
                 <controlURL>/r</controlURL></service>
               <service><serviceType>urn:schemas-upnp-org:service:PlayQueue:1</serviceType>
                 <controlURL>/q</controlURL></service>
            </serviceList></device></root>""",
        )
        assertTrue(LinkPlayIdentity.isLinkPlayFamily(desc))
    }

    @Test
    fun `没有 PlayQueue 时只靠标准服务不认`() {
        // 避免把「电视、功放、机顶盒」全算成音箱
        val desc = DeviceDescription.parse(
            "http://10.0.0.9/x.xml",
            """<root><device><friendlyName>Box</friendlyName><serviceList>
               <service><serviceType>urn:schemas-upnp-org:service:AVTransport:1</serviceType>
                 <controlURL>/a</controlURL></service>
               <service><serviceType>urn:schemas-upnp-org:service:RenderingControl:1</serviceType>
                 <controlURL>/r</controlURL></service>
            </serviceList></device></root>""",
        )
        assertFalse(LinkPlayIdentity.isLinkPlayFamily(desc))
    }

    @Test
    fun `SSDP 头出现品牌字样时值得进一步拉描述`() {
        assertTrue(LinkPlayIdentity.looksPromising("Linux/3.x UPnP/1.0 LinkPlay", null, null))
        assertTrue(LinkPlayIdentity.looksPromising(null, "uuid:x::urn:schemas-wiimu-com:service:PlayQueue:1", null))
    }

    @Test
    fun `真机 SSDP 头看不出品牌，所以头筛选不能作为排除依据`() {
        // 实测 SERVER 是 `Linux/3.x UPnP/1.0`，没有品牌字样。
        // 若把它当作排除条件，真机就搜不到了 —— 这正是 must 拉描述的原因。
        assertFalse(LinkPlayIdentity.looksPromising("Linux/3.x UPnP/1.0", "uuid:FF31F0174FE5C9B2A2ACEAE3", "upnp:rootdevice"))
    }

    @Test
    fun `识别原因可读且指向真凭据`() {
        assertEquals("LinkPlay 私有播放列表服务", LinkPlayIdentity.reasonFor(dossDescription()))
    }
}
