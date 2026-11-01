package io.github.laozhudu.linkplay.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HexCodecTest {
    @Test
    fun `encodes utf8 to lowercase hex without separators`() {
        assertEquals("6c696e6b706c6179", HexCodec.encodeUtf8("linkplay"))
    }

    @Test
    fun `encodes non-ascii ssid`() {
        // "客厅" = E5 AE A2 E5 8E 85
        assertEquals("e5aea2e58e85", HexCodec.encodeUtf8("客厅"))
    }

    @Test
    fun `uppercase variant matches ConnectMasterAp expectation`() {
        assertEquals("4C494E4B504C4159", HexCodec.encodeUtf8Upper("LINKPLAY"))
    }

    @Test
    fun `decode rejects odd length and non hex`() {
        assertNull(HexCodec.decodeOrNull("abc"))
        assertNull(HexCodec.decodeOrNull("zz"))
    }

    @Test
    fun `decodeIfHex keeps plaintext untouched`() {
        assertEquals("My-Access-Point", HexCodec.decodeIfHex("My-Access-Point"))
    }

    @Test
    fun `decodeIfHex converts hex ssid`() {
        assertEquals("linkplay", HexCodec.decodeIfHex("6c696e6b706c6179"))
    }
}
