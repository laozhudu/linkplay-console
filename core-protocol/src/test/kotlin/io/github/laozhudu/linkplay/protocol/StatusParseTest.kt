package io.github.laozhudu.linkplay.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StatusParseTest {

    private val sample = """
        {"ssid":"LinkPlay_32C8","hardware":"A31","firmware":"4.2.8820",
         "uuid":"FF9607407DD495DAF88EA665","eth2":"00:22:6C:32:C8:00",
         "apcli0":"00:22:6C:32:C8:01","essid":"6c696e6b706c6179","expired":"0",
         "internet":1,"netstat":1,"language":"zh_CN","Release":"20190101",
         "build":"a31-2019","securemode":"2"}
    """.trimIndent()

    @Test
    fun `parses the documented getStatusEx fields`() {
        val s = LinkPlayStatus.parse(sample)
        assertEquals("LinkPlay_32C8", s.ssid)
        assertEquals("A31", s.hardware)
        assertEquals("4.2.8820", s.firmware)
        assertEquals("FF9607407DD495DAF88EA665", s.uuid)
        assertEquals("00:22:6C:32:C8:00", s.ethernetMac)
        assertEquals("zh_CN", s.language)
        assertEquals("20190101", s.release)
        assertEquals("a31-2019", s.build)
        assertEquals(1, s.internet)
        assertTrue(s.isOnline)
    }

    @Test
    fun `essid hex is decoded to readable text`() {
        assertEquals("linkplay", LinkPlayStatus.parse(sample).essid)
    }

    @Test
    fun `slave list parses mask into inGroup flag`() {
        val json = """
            {"slave_list":[
              {"ip":"192.168.1.21","mask":"1","version":"4.2.8820","uuid":"AAA","name":"Kitchen","ssid":"Home","volume":30},
              {"ip":"192.168.1.22","mask":"0","version":"4.2.8820","uuid":"BBB","name":"Bedroom","ssid":"Home","volume":18}
            ]}
        """.trimIndent()
        val slaves = SlaveDevice.parseList(json)
        assertEquals(2, slaves.size)
        assertTrue(slaves[0].inGroup)
        assertEquals("Kitchen", slaves[0].name)
        assertEquals(30, slaves[0].volume)
        assertTrue(!slaves[1].inGroup)
    }

    @Test
    fun `empty slave list yields empty result`() {
        assertTrue(SlaveDevice.parseList("""{"slave_list":[]}""").isEmpty())
        assertTrue(SlaveDevice.parseList("{}").isEmpty())
    }
}
