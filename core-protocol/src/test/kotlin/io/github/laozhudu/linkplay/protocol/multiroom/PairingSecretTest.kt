package io.github.laozhudu.linkplay.protocol.multiroom

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 主机热点密码的解析（原厂 `ax.a(String)` 逐字复刻）。
 *
 * 这条是组网成败的关键：`)` 之后走 WPA2PSK、空串走 OPEN，抄错一个字符就组不上，
 * 而设备对这两种都回 `OK` —— 只能靠这个解析对。
 */
class PairingSecretTest {

    @Test
    fun `securemode 0 means empty password so auth falls back to OPEN`() {
        // 真机两台都是这个返回值
        assertEquals("", MultiroomClient.pairingSecret("{\"securemode\":\"0\",}"))
    }

    @Test
    fun `securemode 1 extracts the password`() {
        assertEquals(
            "12345678",
            MultiroomClient.pairingSecret("{\"securemode\":\"1\",\"password\":\"12345678\",}"),
        )
    }

    @Test
    fun `password with dots or spaces is kept verbatim`() {
        assertEquals(
            "a b.c",
            MultiroomClient.pairingSecret("{\"securemode\":\"1\",\"password\":\"a b.c\",}"),
        )
    }

    @Test
    fun `empty or error responses yield empty password`() {
        assertEquals("", MultiroomClient.pairingSecret(""))
        assertEquals("", MultiroomClient.pairingSecret("   "))
        assertEquals("", MultiroomClient.pairingSecret("<ERR>"))
        assertEquals("", MultiroomClient.pairingSecret("unknown command"))
    }

    @Test
    fun `a single field response is treated as no password`() {
        // 有的固件只回 {"securemode":"1"}：split(",") 只有一段，拿不到密码就必须当空处理，
        // 否则会把整个 JSON 当密码发出去（上一版就是这么错的）
        assertEquals("", MultiroomClient.pairingSecret("{\"securemode\":\"1\"}"))
    }

    @Test
    fun `json body never leaks into the password`() {
        val pwd = MultiroomClient.pairingSecret("{\"securemode\":\"0\",}")
        assertEquals(false, pwd.contains("securemode"))
    }
}
