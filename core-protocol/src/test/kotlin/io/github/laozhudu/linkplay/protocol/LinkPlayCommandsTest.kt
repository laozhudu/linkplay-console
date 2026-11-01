package io.github.laozhudu.linkplay.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkPlayCommandsTest {

    @Test
    fun `simple commands keep the exact original spelling`() {
        assertEquals("getStatusEx", LinkPlayCommands.statusEx())
        assertEquals("wlanGetApListEx", LinkPlayCommands.apList())
        assertEquals("restoreToDefault", LinkPlayCommands.restoreToDefault())
        assertEquals("multiroom:getSlaveList", LinkPlayCommands.slaveList())
        assertEquals("EasyLinkResponseStop", LinkPlayCommands.easyLinkResponseStop())
    }

    @Test
    fun `switch mode and key click carry arguments`() {
        assertEquals("setPlayerCmd:switchmode:wifi", LinkPlayCommands.switchMode("wifi"))
        assertEquals("MCUKeyShortClick:4", LinkPlayCommands.keyClick(4))
    }

    @Test
    fun `connectMasterAp encodes ssid and password as uppercase hex`() {
        // "Home" -> 48 6F 6D 65 ; "zebra" -> 7A 65 62 72 61（含 a-f，能验证大写）
        val cmd = LinkPlayCommands.connectMasterAp("Home", 6, "zebra", chext = 0)
        assertEquals(
            "ConnectMasterAp:ssid=486F6D65:ch=6:auth=WPA2PSK:encry=AES:pwd=7A65627261:chext=0",
            cmd,
        )
    }

    @Test
    fun `open network is signalled without password`() {
        val cmd = LinkPlayCommands.connectMasterAp("FreeWiFi", 1, "")
        assertTrue(cmd.contains(":auth=OPEN:encry=NONE:pwd=:chext=0"))
    }

    @Test
    fun `wlanConnectApEx uses lowercase hex`() {
        val cmd = LinkPlayCommands.wlanConnectApEx("Home", 11, "WPA2PSK", "AES", "pw")
        assertEquals("wlanConnectApEx:ssid=486f6d65:ch=11:auth=WPA2PSK:encry=AES:pwd=7077:chext=1", cmd)
    }

    @Test
    fun `multiroom connect keeps the triple-n spelling of ConnectAP`() {
        val cmd = LinkPlayCommands.multiroomConnectAp("192.168.1.20", "Home", 6, "pw")
        assertTrue(cmd.startsWith("multiroom:ConnnectAP:192.168.1.20:ConnectMasterAp:ssid=486f6d65"))
    }

    @Test
    fun `multiroom group commands address slaves by ip`() {
        assertEquals("multiroom:SlaveVolume:192.168.1.21:35", LinkPlayCommands.slaveVolume("192.168.1.21", 35))
        assertEquals("multiroom:SlaveKickout:192.168.1.21", LinkPlayCommands.slaveKickout("192.168.1.21"))
        assertEquals("multiroom:SlaveMask:192.168.1.21", LinkPlayCommands.slaveMask("192.168.1.21"))
    }

    @Test
    fun `device name is url encoded not hex`() {
        assertEquals("setDeviceName:Living+Room", LinkPlayCommands.deviceName("Living Room"))
    }

    @Test
    fun `url builds against device ip`() {
        assertEquals(
            "http://192.168.1.20/httpapi.asp?command=getStatusEx",
            LinkPlayCommands.urlFor("192.168.1.20", LinkPlayCommands.statusEx()),
        )
    }
}
