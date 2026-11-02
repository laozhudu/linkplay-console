package io.github.laozhudu.linkplay.protocol.upnp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceDescriptionTest {

    // 取自原厂 App 内嵌的设备描述模板结构（LinkPlay/WiiMu 白标设备实际会返回同类 XML）
    private val xml = """
        <?xml version="1.0"?>
        <root xmlns="urn:schemas-upnp-org:device-1-0">
          <specVersion><major>1</major><minor>0</minor></specVersion>
          <device>
            <deviceType>urn:schemas-upnp-org:device:MediaRenderer:1</deviceType>
            <friendlyName>LinkPlay_32C8</friendlyName>
            <manufacturer>WiiMu</manufacturer>
            <modelName>WiiMu Media player</modelName>
            <UDN>uuid:7153ef46-1dd2-11b2-a834-9b457c0825ed</UDN>
            <serviceList>
              <service>
                <serviceType>urn:schemas-upnp-org:service:AVTransport:1</serviceType>
                <serviceId>urn:upnp-org:serviceId:AVTransport</serviceId>
                <SCPDURL>/upnp/rendertransportSCPD.xml</SCPDURL>
                <controlURL>/upnp/control/rendertransport1</controlURL>
                <eventSubURL>/upnp/event/rendertransport1</eventSubURL>
              </service>
              <service>
                <serviceType>urn:schemas-upnp-org:service:RenderingControl:1</serviceType>
                <serviceId>urn:upnp-org:serviceId:RenderingControl</serviceId>
                <SCPDURL>/upnp/rendercontrolSCPD.xml</SCPDURL>
                <controlURL>/upnp/control/rendercontrol1</controlURL>
                <eventSubURL>/upnp/event/rendercontrol1</eventSubURL>
              </service>
              <service>
                <serviceType>urn:schemas-wiimu-com:service:PlayQueue:1</serviceType>
                <serviceId>urn:wiimu-com:serviceId:PlayQueue</serviceId>
                <SCPDURL>/upnp/PlayQueueSCPD.xml</SCPDURL>
                <controlURL>/upnp/control/PlayQueue1</controlURL>
                <eventSubURL>/upnp/event/PlayQueue1</eventSubURL>
              </service>
            </serviceList>
          </device>
        </root>
    """.trimIndent()

    @Test
    fun `parses device identity`() {
        val d = DeviceDescription.parse("http://192.168.1.20:49152/description.xml", xml)
        assertEquals("LinkPlay_32C8", d.friendlyName)
        assertEquals("WiiMu", d.manufacturer)
        assertEquals("WiiMu Media player", d.modelName)
        assertEquals("uuid:7153ef46-1dd2-11b2-a834-9b457c0825ed", d.udn)
    }

    @Test
    fun `resolves control urls against the location base`() {
        val d = DeviceDescription.parse("http://192.168.1.20:49152/description.xml", xml)
        assertEquals(3, d.services.size)
        assertEquals("http://192.168.1.20:49152/upnp/control/rendertransport1", d.avTransportControlUrl)
        assertEquals("http://192.168.1.20:49152/upnp/control/rendercontrol1", d.renderingControlUrl)
        assertEquals(
            "http://192.168.1.20:49152/upnp/control/PlayQueue1",
            d.controlUrl(UpnpServices.WIIMU_PLAY_QUEUE),
        )
    }

    @Test
    fun `base resolution respects non root paths`() {
        assertEquals(
            "http://h:1234/upnp/control/x",
            DeviceDescription.resolve(java.net.URI("http://h:1234/dev/desc.xml"), "/upnp/control/x"),
        )
        assertEquals(
            "http://h:1234/dev/control/x",
            DeviceDescription.resolve(java.net.URI("http://h:1234/dev/desc.xml"), "control/x"),
        )
    }

    @Test
    fun `absolute control url is kept as is`() {
        assertEquals(
            "http://other:9/ctl",
            DeviceDescription.resolve(java.net.URI("http://h:1234/d.xml"), "http://other:9/ctl"),
        )
        assertNull(DeviceDescription.resolve(null, "/rel"))
        assertNull(DeviceDescription.resolve(java.net.URI("http://h/d.xml"), null))
    }

    @Test
    fun `device without avtransport is detectable`() {
        val bare = "<root><device><friendlyName>X</friendlyName></device></root>"
        val d = DeviceDescription.parse("http://h/d.xml", bare)
        assertTrue(d.services.isEmpty())
        assertNull(d.avTransportControlUrl)
    }
}
