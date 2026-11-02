package io.github.laozhudu.linkplay.protocol.upnp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SoapEnvelopeTest {

    @Test
    fun `soap action header quotes the service and action`() {
        assertEquals(
            "\"urn:schemas-upnp-org:service:AVTransport:1#Play\"",
            SoapEnvelope.soapAction(UpnpServices.AV_TRANSPORT, "Play"),
        )
    }

    @Test
    fun `envelope contains service namespace and ordered arguments`() {
        val body = SoapEnvelope.build(
            UpnpServices.AV_TRANSPORT,
            "Seek",
            listOf("InstanceID" to "0", "Unit" to "REL_TIME", "Target" to "0:01:30"),
        )
        assertTrue(body.contains("""<u:Seek xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">"""))
        assertTrue(body.contains("<InstanceID>0</InstanceID><Unit>REL_TIME</Unit><Target>0:01:30</Target>"))
        assertTrue(body.startsWith("<?xml version=\"1.0\" encoding=\"utf-8\"?>"))
        assertTrue(body.endsWith("</s:Envelope>"))
        // 参数顺序必须保留，设备对顺序敏感的情况真实存在
        assertTrue(body.indexOf("InstanceID") < body.indexOf("Unit"))
    }

    @Test
    fun `arguments are xml escaped`() {
        val body = SoapEnvelope.build(
            UpnpServices.AV_TRANSPORT,
            "SetAVTransportURI",
            listOf("CurrentURI" to "http://h/a.mp3?x=1&y=2"),
        )
        assertTrue(body.contains("x=1&amp;y=2"))
        assertTrue(!body.contains("x=1&y=2"))
    }

    @Test
    fun `fault parsing extracts error code and description`() {
        val fault = """
            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/">
              <s:Body><s:Fault>
                <faultcode>s:Client</faultcode>
                <faultstring>UPnPError</faultstring>
                <detail><UPnPError xmlns="urn:schemas-upnp-org:control-1-0">
                  <errorCode>701</errorCode>
                  <errorDescription>Transition not available</errorDescription>
                </UPnPError></detail>
              </s:Fault></s:Body>
            </s:Envelope>
        """.trimIndent()
        assertEquals(701, SoapEnvelope.faultCode(fault))
        assertEquals("Transition not available", SoapEnvelope.faultDescription(fault))
    }

    @Test
    fun `successful response has no fault code`() {
        val ok = """<?xml version="1.0"?><s:Envelope><s:Body><u:PlayResponse xmlns:u="x"/></s:Body></s:Envelope>"""
        assertNull(SoapEnvelope.faultCode(ok))
    }
}
