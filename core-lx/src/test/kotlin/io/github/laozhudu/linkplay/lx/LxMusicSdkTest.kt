package io.github.laozhudu.linkplay.lx

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 用假引擎跑 musicSdk 的调用协议。
 *
 * 真实的 bundle 在 `scripts/verify-lx-bundle.js` 里用 Node 验过（五平台搜索全通）；
 * 这里验的是 **Kotlin 侧怎么发请求、怎么解析回包、怎么处理失败**。
 */
class LxMusicSdkTest {

    private val bundle = "// fake bundle"

    private fun sdk(engine: FakeEngine, http: (LxHttp.Request) -> LxHttp.Result = { LxHttp.Result(200, "OK", emptyMap(), "\"\"") }) =
        LxMusicSdk(engine = engine, bundleScript = bundle, http = http)

    /** 让假引擎在 callGlobal("lxSdkCall") 时按 action 回包 */
    private fun FakeEngine.respondWith(handler: (action: String, payload: String) -> String?) {
        onCallGlobal = { name, args ->
            if (name == "lxSdkCall") {
                val reqId = args[0] as String
                val action = args[1] as String
                val payload = args[2] as String
                handler(action, payload)?.let { data ->
                    fireNativeCall(globals["__lx_key__"], "sdkResponse", """{"reqId":"$reqId","ok":true,"data":$data}""")
                } ?: fireNativeCall(
                    globals["__lx_key__"],
                    "sdkResponse",
                    """{"reqId":"$reqId","ok":false,"error":"不支持的 action: $action"}""",
                )
            }
            null
        }
    }

    @Test
    fun `load 会装桥并执行 bundle`() {
        val engine = FakeEngine()
        sdk(engine).load()

        // 垫片先于 bundle（bundle 顶层就用 Buffer，见 LxBufferPolyfill 的注释）
        assertEquals(
            listOf("lx-console-shim.js", "lx-buffer-polyfill.js", "lx-music-sdk.bundle.js"),
            engine.evaluated.map { it.first },
        )
        assertTrue(engine.functions.containsKey("__lx_crypto__"))
        assertTrue(engine.functions.containsKey("__lx_native_call__"))
    }

    @Test
    fun `load 只执行一次`() {
        val engine = FakeEngine()
        val s = sdk(engine)
        s.load()
        val afterFirst = engine.evaluated.size
        s.load()
        // 第二次不该再 evaluate 任何东西（console 垫片 + Buffer 垫片 + bundle 共三条，且不重复）
        assertEquals(3, afterFirst)
        assertEquals(afterFirst, engine.evaluated.size)
    }

    @Test
    fun `取平台列表`() = runTest {
        val engine = FakeEngine()
        engine.respondWith { action, _ ->
            if (action == "sources") """{"sources":["wy","kw","tx"]}""" else null
        }

        val r = sdk(engine).sources()

        assertTrue(r.ok)
        assertEquals(listOf("wy", "kw", "tx"), r.value)
    }

    @Test
    fun `搜索能解析出歌单结构`() = runTest {
        val engine = FakeEngine()
        engine.respondWith { action, _ ->
            if (action == "search") """
                {"list":[
                  {"source":"wy","songmid":509781655,"name":"想你就写信","singer":"周杰伦","albumName":"专辑A","interval":"03:58","img":"https://p1/x.jpg"},
                  {"source":"wy","songmid":"5257138","name":"屋顶","singer":"周杰伦、温岚"}
                ],"total":277}
            """.trimIndent() else null
        }

        val r = sdk(engine).search("wy", "周杰伦", page = 1, limit = 2)

        assertTrue(r.ok)
        assertEquals(277, r.value!!.total)
        assertEquals(2, r.value!!.tracks.size)
        assertEquals("509781655", r.value!!.tracks[0].songmid)
        assertEquals("周杰伦", r.value!!.tracks[0].singer)
        assertEquals("https://p1/x.jpg", r.value!!.tracks[0].img)
    }

    @Test
    fun `songmid 是数字时也能转成字符串`() = runTest {
        // 网易云的 songmid 是数字，QQ 的是字符串，两种都要能吃
        val engine = FakeEngine()
        engine.respondWith { action, _ ->
            if (action == "search") """{"list":[{"source":"tx","songmid":"0027oMO61wWi55","name":"发如雪"}],"total":1}"""
            else null
        }

        val r = sdk(engine).search("tx", "周杰伦")

        assertEquals("0027oMO61wWi55", r.value!!.tracks[0].songmid)
    }

    @Test
    fun `SDK 回 error 时返回失败`() = runTest {
        val engine = FakeEngine()
        engine.respondWith { _, _ -> null }

        val r = sdk(engine).search("wy", "x")

        assertFalse(r.ok)
        assertTrue(r.error!!.contains("搜索失败"))
    }

    @Test
    fun `SDK 不回包时超时返回失败`() = runTest {
        val engine = FakeEngine()
        engine.onCallGlobal = { _, _ -> null } // 什么都不回

        val r = sdk(engine).search("wy", "x")

        assertFalse(r.ok)
    }

    @Test
    fun `热搜`() = runTest {
        val engine = FakeEngine()
        engine.respondWith { action, _ ->
            if (action == "hotSearch") """{"list":["周杰伦","林俊杰"]}""" else null
        }

        val r = sdk(engine).hotSearch("wy")

        assertTrue(r.ok)
        assertEquals(listOf("周杰伦", "林俊杰"), r.value)
    }

    @Test
    fun `歌单搜索`() = runTest {
        val engine = FakeEngine()
        engine.respondWith { action, _ ->
            if (action == "songListSearch") """
                {"list":[{"id":"p1","name":"华语经典","author":"某人","total":50,"img":"https://x/y.jpg"}]}
            """.trimIndent() else null
        }

        val r = sdk(engine).songListSearch("wy", "华语")

        assertTrue(r.ok)
        assertEquals("p1", r.value!![0].id)
        assertEquals(50, r.value!![0].total)
    }

    @Test
    fun `歌词为空时返回空串而不是失败`() = runTest {
        val engine = FakeEngine()
        engine.respondWith { action, _ ->
            if (action == "lyric") """{}""" else null
        }

        val r = sdk(engine).lyric("wy", """{"songmid":1}""")

        assertTrue(r.ok)
        assertEquals("", r.value)
    }

    @Test
    fun `封面`() = runTest {
        val engine = FakeEngine()
        engine.respondWith { action, _ ->
            if (action == "pic") """{"url":"https://p1.music.126.net/a.jpg"}""" else null
        }

        val r = sdk(engine).pic("wy", """{"songmid":1}""")

        assertTrue(r.ok)
        assertEquals("https://p1.music.126.net/a.jpg", r.value)
    }

    @Test
    fun `musicInfo 能转成脚本要的 JSON`() {
        val track = LxTrack(source = "wy", songmid = "123", name = "歌", singer = "人")
        val json = track.toMusicInfoJson()
        assertTrue(json.contains("\"source\":\"wy\""))
        assertTrue(json.contains("\"songmid\":\"123\""))
        assertTrue(json.contains("\"name\":\"歌\""))
    }

    @Test
    fun `SDK 发 HTTP 时原生代发`() = runTest {
        val engine = FakeEngine()
        var seenUrl: String? = null
        val s = sdk(engine) { req ->
            seenUrl = req.url
            LxHttp.Result(200, "OK", emptyMap(), """{"ok":true}""")
        }
        s.load()

        engine.fireNativeCall(
            engine.globals["__lx_key__"],
            "http",
            """{"id":"h1","url":"https://api.example/q","options":{"method":"get"}}""",
        )

        val deadline = System.currentTimeMillis() + 3000
        while (System.currentTimeMillis() < deadline &&
            engine.callGlobals.none { it.first == "__lx_http_settle__" }
        ) {
            Thread.sleep(20)
        }

        assertEquals("https://api.example/q", seenUrl)
        val settle = engine.callGlobals.first { it.first == "__lx_http_settle__" }
        assertEquals("h1", settle.second[0])
        assertTrue((settle.second[1] as String).contains("statusCode"))
    }

    @Test
    fun `crypto 桥按官方签名分发`() {
        val engine = FakeEngine()
        sdk(engine).load()

        val got = engine.fireUtil("__lx_crypto__", "md5", """["abc"]""") as String
        assertEquals(LxCrypto.md5("abc"), got)
    }
}
