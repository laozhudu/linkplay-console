package io.github.laozhudu.linkplay.protocol.identity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * uuid 宽容匹配的回归测试。
 *
 * 反例全部来自真机：`.153` 的 `getStatusEx.uuid` 是 24 位无连字符，
 * 而它在主机 `slave_list` 里的 `uuid` 是 `uuid:FF31F017-4FE5-C9B2-A2AC-EAE3FF31F017`。
 * 之前用 `equals(ignoreCase = true)` 比，永远不相等 —— 组内电量/声道全部匹配不上。
 */
class UuidMatchTest {

    private val plain = "FF31F0174FE5C9B2A2ACEAE3"
    private val upnp = "uuid:FF31F017-4FE5-C9B2-A2AC-EAE3FF31F017"

    @Test
    fun `plain and upnp forms of same device match`() {
        assertTrue(UuidMatch.same(plain, upnp))
        assertTrue(UuidMatch.same(upnp, plain))
    }

    @Test
    fun `key strips prefix dashes and case`() {
        assertEquals("ff31f0174fe5c9b2a2aceae3ff31f017", UuidMatch.key(upnp))
        assertEquals("ff31f0174fe5c9b2a2aceae3", UuidMatch.key(plain))
    }

    @Test
    fun `different devices do not match`() {
        assertFalse(UuidMatch.same(plain, "FF310096A5760623673F81F1"))
    }

    @Test
    fun `blank and short strings never match`() {
        assertFalse(UuidMatch.same(null, plain))
        assertFalse(UuidMatch.same("", ""))
        assertFalse(UuidMatch.same("uuid:", plain))
        // 少于 16 位的公共前缀不认（防止 IP 片段之类的短串误配）
        assertFalse(UuidMatch.same("FF31F017", plain))
    }

    @Test
    fun `find picks the matching candidate keeping its original format`() {
        val candidates = listOf(upnp, "uuid:FF310096-A576-0623-673F-81F1FFFFFFFF")
        val hit = UuidMatch.find(plain, candidates) { it }
        assertEquals(upnp, hit)
    }
}
