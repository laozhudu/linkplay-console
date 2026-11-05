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
    fun `官方信封里取 data_url`() {
        // 这条 fixture 是**真实抓的**：用 core-lx 的官方 lx-preload.js 驱动 huibq 音源、
        // 向 tx 要 320k，把 preload 发给原生的 response 原文整条抄下来。
        // 之前这里用的是裸字符串（`"result":"https://…"`），所以没抓到
        // 「把整个信封当 URL」那个 bug —— 真机上表现就是
        // 「脚本返回的不是有效地址：{"source":"tx","action":"musicUrl",…}」。
        val r = LxProtocol.parseScriptResult(
            """{"requestKey":"r1","status":true,"result":{"source":"tx","action":"musicUrl","data":{"type":"320k","url":"http://ws.stream.qqmusic.qq.com/M800003Qui1q2u1Zho.mp3?guid=ffffffffc81e6f5affffffffedffeb7f&vkey=1F1A3CF34E9EB86E8D4DB210F9F60C3275A8323DA81EDB61C132E17B93E21FB39F958A5AFDABB5A2E6CE820E974D956EC2B017018CB3D294__v2b9abb9c&uin=2274735704&fromtag=120093"}}}""",
        )
        assertTrue(r.ok)
        assertTrue(r.resultUrl.startsWith("http://ws.stream.qqmusic.qq.com/"))
        assertTrue(r.resultUrl.contains("M800003Qui1q2u1Zho.mp3"))
        // resultText 拿到的是整包 —— 踩过的坑就长这样，固定住它，防止有人又改回去
        assertTrue(r.resultText.contains("\"source\":\"tx\""))
    }

    @Test
    fun `裸字符串也能取到地址`() {
        val r = LxProtocol.parseScriptResult(
            """{"requestKey":"k6","status":true,"result":"https://cdn.example/a.flac"}""",
        )
        assertEquals("https://cdn.example/a.flac", r.resultUrl)
    }

    @Test
    fun `信封里没有 url 时是空串而不是崩`() {
        val r = LxProtocol.parseScriptResult(
            """{"requestKey":"k7","status":true,"result":{"source":"wy","action":"musicUrl","data":{"type":"320k"}}}""",
        )
        assertEquals("", r.resultUrl)
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
