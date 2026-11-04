package io.github.laozhudu.linkplay.lx

import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LxProtocolTest {

    // ── parseInit ───────────────────────────────────────────────────────

    private val initOk = """
        {"status":true,"info":{"name":"全豆要[聚合音源]","description":"聚合源","version":"9.3",
         "author":"全豆要","homepage":"",
         "sources":{
           "wy":{"name":"网易云音乐","type":"music","actions":["musicUrl"],"qualitys":["128k","320k","flac"]},
           "tx":{"name":"QQ音乐","type":"music","actions":["musicUrl"],"qualitys":["320k"]}
         }}}
    """.trimIndent()

    @Test
    fun `解析成功的初始化`() {
        val r = LxProtocol.parseInit(initOk)
        assertTrue(r.ok)
        assertNull(r.errorMessage)
        assertEquals("全豆要[聚合音源]", r.name)
        assertEquals("9.3", r.version)
        assertEquals("全豆要", r.author)
        assertEquals(listOf("wy", "tx"), r.sourceIds)
        assertEquals(listOf("128k", "320k", "flac"), r.sources["wy"]!!.qualitys)
        assertTrue(r.sources["wy"]!!.supportsMusicUrl)
    }

    @Test
    fun `解析失败的初始化会带上错误`() {
        val r = LxProtocol.parseInit("""{"status":false,"errorMessage":"脚本错误，加载音源信息失败"}""")
        assertFalse(r.ok)
        assertEquals("脚本错误，加载音源信息失败", r.errorMessage)
        assertTrue(r.sources.isEmpty())
    }

    @Test
    fun `失败但没给错误信息时给兜底文案`() {
        val r = LxProtocol.parseInit("""{"status":false}""")
        assertFalse(r.ok)
        assertEquals("脚本初始化失败", r.errorMessage)
    }

    @Test
    fun `非法 JSON 不会抛异常`() {
        val r = LxProtocol.parseInit("not json at all")
        assertFalse(r.ok)
        assertEquals("初始化返回的不是合法 JSON", r.errorMessage)
    }

    @Test
    fun `没有 sources 时是空表而不是崩`() {
        val r = LxProtocol.parseInit("""{"status":true,"info":{"name":"x"}}""")
        assertTrue(r.ok)
        assertTrue(r.sources.isEmpty())
    }

    // ── parseScriptResult（JS→原生，形状是 status/result）────────────────

    @Test
    fun `解析脚本成功答复`() {
        val r = LxProtocol.parseScriptResult(
            """{"requestKey":"k1","status":true,"result":"https://cdn.example/a.flac"}""",
        )
        assertEquals("k1", r.requestKey)
        assertTrue(r.ok)
        assertNull(r.error)
        assertEquals("https://cdn.example/a.flac", r.resultText)
    }

    @Test
    fun `解析脚本失败答复`() {
        val r = LxProtocol.parseScriptResult(
            """{"requestKey":"k2","status":false,"errorMessage":"Network request failed"}""",
        )
        assertEquals("k2", r.requestKey)
        assertFalse(r.ok)
        assertEquals("Network request failed", r.error)
    }

    @Test
    fun `status 为 true 但没给 result 时结果为空串`() {
        val r = LxProtocol.parseScriptResult("""{"requestKey":"k3","status":true}""")
        assertTrue(r.ok)
        assertEquals("", r.resultText)
    }

    @Test
    fun `result 是对象时也能取出来`() {
        val r = LxProtocol.parseScriptResult(
            """{"requestKey":"k4","status":true,"result":{"lyric":"abc"}}""",
        )
        assertTrue(r.resultText.contains("abc"))
    }

    @Test
    fun `答复不是合法 JSON 时不会抛异常`() {
        val r = LxProtocol.parseScriptResult("garbage")
        assertFalse(r.ok)
        assertEquals("回包不是合法 JSON", r.error)
    }

    // ── requestPayload ──────────────────────────────────────────────────

    @Test
    fun `构造的 request 报文符合官方结构`() {
        val payload = LxProtocol.requestPayload(
            requestKey = "rk",
            source = "wy",
            action = "musicUrl",
            info = kotlinx.serialization.json.buildJsonObject { },
        )
        val obj = kotlinx.serialization.json.Json.parseToJsonElement(payload).jsonObject
        assertEquals("rk", obj["requestKey"]!!.jsonPrimitive.content)
        val data = obj["data"]!!.jsonObject
        assertEquals("wy", data["source"]!!.jsonPrimitive.content)
        assertEquals("musicUrl", data["action"]!!.jsonPrimitive.content)
        assertTrue(data.containsKey("info"))
    }

    // ── parseSdkResponse ────────────────────────────────────────────────

    @Test
    fun `解析 SDK 成功回包`() {
        val r = LxProtocol.parseSdkResponse("""{"reqId":"r1","ok":true,"data":{"total":3}}""")
        assertEquals("r1", r.reqId)
        assertTrue(r.ok)
        assertEquals(3, r.data!!.jsonObject["total"]!!.jsonPrimitive.content.toInt())
    }

    @Test
    fun `解析 SDK 失败回包`() {
        val r = LxProtocol.parseSdkResponse("""{"reqId":"r2","ok":false,"error":"不支持"}""")
        assertFalse(r.ok)
        assertEquals("不支持", r.error)
    }
}
