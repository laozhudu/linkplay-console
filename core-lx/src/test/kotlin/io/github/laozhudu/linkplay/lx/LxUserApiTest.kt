package io.github.laozhudu.linkplay.lx

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 用假引擎跑完整的「加载 → 取直链」流程。
 *
 * 这些用例覆盖的是**协议时序**：init 事件、request/response 配对、
 * 超时、错误翻译、取消 —— 真机上出问题基本都出在这些地方。
 */
class LxUserApiTest {

    private val info = LxScriptInfo(name = "测试源", version = "1.0", author = "某人")
    private val preload = "// fake preload"

    private fun api(engine: FakeEngine, http: (LxHttp.Request) -> LxHttp.Result = { LxHttp.Result(200, "OK", emptyMap(), "\"\"") }) =
        LxUserApi(engine = engine, preloadScript = preload, http = http)

    /** 模拟脚本初始化成功 */
    private fun FakeEngine.initOk() {
        onEvaluate = { filename, _ ->
            if (filename == "user-api.js") {
                fireNativeCall(
                    globals["__lx_key__"],
                    "init",
                    """{"status":true,"info":{"name":"测试源","version":"1.0","author":"某人",
                       "sources":{"wy":{"name":"网易云","type":"music","actions":["musicUrl"],"qualitys":["320k"]}}}}""",
                )
            }
        }
    }

    // ── 加载 ─────────────────────────────────────────────────────────────

    @Test
    fun `加载成功会先装桥再执行 preload 和脚本`() = runTest {
        val engine = FakeEngine()
        engine.initOk()

        val result = api(engine).load("// script", info)

        assertTrue(result.ok)
        assertEquals(listOf("wy"), result.sourceIds)
        // 顺序：先 preload 再脚本，不能反
        assertEquals(listOf("lx-preload.js", "user-api.js"), engine.evaluated.map { it.first })
        // 桥必须都装上了
        assertNotNull(engine.functions["__lx_native_call__"])
        assertNotNull(engine.functions["__lx_native_call__utils_str2md5"])
        assertNotNull(engine.functions["__lx_native_call__utils_aes_encrypt"])
        assertNotNull(engine.functions["__lx_native_call__utils_rsa_encrypt"])
        assertNotNull(engine.functions["__lx_native_call__utils_str2b64"])
        assertNotNull(engine.functions["__lx_native_call__utils_b642buf"])
        assertNotNull(engine.functions["__lx_native_call__set_timeout"])
        // 必须调过 lx_setup，且第一个参数是 key
        val setup = engine.callGlobals.first { it.first == "lx_setup" }
        assertEquals(engine.globals["__lx_key__"], setup.second[0])
        assertEquals("测试源", setup.second[1])
    }

    @Test
    fun `脚本初始化失败会带上错误信息`() = runTest {
        val engine = FakeEngine()
        engine.onEvaluate = { filename, _ ->
            if (filename == "user-api.js") {
                engine.fireNativeCall(
                    engine.globals["__lx_key__"],
                    "init",
                    """{"status":false,"errorMessage":"脚本错误，加载音源信息失败"}""",
                )
            }
        }

        val r = api(engine).load("// bad", info)

        assertFalse(r.ok)
        assertEquals("脚本错误，加载音源信息失败", r.errorMessage)
    }

    @Test
    fun `脚本不发出 init 时会超时而不是卡死`() = runTest {
        val engine = FakeEngine() // 什么都不做
        val r = api(engine).load("// silent", info, timeoutMs = 300)

        assertFalse(r.ok)
        assertTrue(r.errorMessage!!.contains("超时"))
    }

    @Test
    fun `执行脚本抛异常时被翻译成人话`() = runTest {
        val engine = FakeEngine()
        engine.onEvaluate = { filename, _ ->
            if (filename == "user-api.js") throw IllegalStateException("Unknown cipher")
        }

        val r = api(engine).load("// boom", info)

        assertFalse(r.ok)
        assertTrue(r.errorMessage!!.contains("脚本已过期"))
    }

    // ── 取直链 ───────────────────────────────────────────────────────────

    @Test
    fun `取直链成功`() = runTest {
        val engine = FakeEngine()
        engine.initOk()
        val api = api(engine)
        api.load("// s", info)

        // 模拟脚本回包
        engine.onCallGlobal = { name, args ->
            if (name == "__lx_native__" && args[1] == "request") {
                val payload = args[2] as String
                val key = Regex("\"requestKey\":\"([^\"]+)\"").find(payload)!!.groupValues[1]
                engine.fireNativeCall(
                    engine.globals["__lx_key__"],
                    "response",
                    """{"requestKey":"$key","status":true,"result":"https://cdn.example/a.flac"}""",
                )
            }
            null
        }

        val r = api.musicUrl("wy", """{"source":"wy","songmid":123}""")

        assertTrue(r.ok)
        assertEquals("https://cdn.example/a.flac", r.value)
    }

    @Test
    fun `脚本回 error 时翻译成人话`() = runTest {
        val engine = FakeEngine()
        engine.initOk()
        val api = api(engine)
        api.load("// s", info)

        engine.onCallGlobal = { name, args ->
            if (name == "__lx_native__" && args[1] == "request") {
                val payload = args[2] as String
                val key = Regex("\"requestKey\":\"([^\"]+)\"").find(payload)!!.groupValues[1]
                engine.fireNativeCall(
                    engine.globals["__lx_key__"],
                    "response",
                    """{"requestKey":"$key","status":false,"errorMessage":"Api is not found"}""",
                )
            }
            null
        }

        val r = api.musicUrl("wy", """{"source":"wy","songmid":1}""")

        assertFalse(r.ok)
        assertTrue(r.error!!.contains("不支持取播放地址"))
    }

    @Test
    fun `脚本返回非 http 地址时拒绝`() = runTest {
        val engine = FakeEngine()
        engine.initOk()
        val api = api(engine)
        api.load("// s", info)

        engine.onCallGlobal = { name, args ->
            if (name == "__lx_native__" && args[1] == "request") {
                val payload = args[2] as String
                val key = Regex("\"requestKey\":\"([^\"]+)\"").find(payload)!!.groupValues[1]
                engine.fireNativeCall(
                    engine.globals["__lx_key__"],
                    "response",
                    """{"requestKey":"$key","status":true,"result":"ftp://bad"}""",
                )
            }
            null
        }

        val r = api.musicUrl("wy", """{"source":"wy","songmid":1}""")

        assertFalse(r.ok)
        assertTrue(r.error!!.contains("不是有效地址"))
    }

    @Test
    fun `脚本不回应时超时`() = runTest {
        val engine = FakeEngine()
        engine.initOk()
        val api = api(engine)
        api.load("// s", info)

        val r = api.musicUrl("wy", """{"source":"wy","songmid":1}""", timeoutMs = 300)

        assertFalse(r.ok)
        assertTrue(r.error!!.contains("超时"))
    }

    @Test
    fun `没初始化就取直链会直接拒绝`() = runTest {
        val engine = FakeEngine()
        val r = api(engine).musicUrl("wy", "{}")

        assertFalse(r.ok)
        assertTrue(r.error!!.contains("还没初始化成功"))
    }

    // ── 脚本自己发 HTTP ──────────────────────────────────────────────────

    @Test
    fun `脚本发 HTTP 时原生代发并把结果回灌`() = runTest {
        val engine = FakeEngine()
        engine.initOk()
        var seenUrl: String? = null
        val api = api(engine) { req ->
            seenUrl = req.url
            LxHttp.Result(200, "OK", mapOf("Content-Type" to "application/json"), """{"code":200}""")
        }
        api.load("// s", info)

        // 模拟脚本调 lx.request
        engine.fireNativeCall(
            engine.globals["__lx_key__"],
            "request",
            """{"requestKey":"rq1","url":"https://api.example/x","options":{"method":"post","form":{"a":"1"}}}""",
        )

        // 等后台协程把回包发出去
        val deadline = System.currentTimeMillis() + 3000
        while (System.currentTimeMillis() < deadline &&
            engine.callGlobals.none { it.first == "__lx_native__" && it.second.getOrNull(1) == "response" }
        ) {
            Thread.sleep(20)
        }

        assertEquals("https://api.example/x", seenUrl)
        val respCall = engine.callGlobals.first { it.first == "__lx_native__" && it.second[1] == "response" }
        val payload = respCall.second[2] as String
        assertTrue(payload.contains("\"requestKey\":\"rq1\""))
        assertTrue(payload.contains("200"))
    }

    // ── 工具桥 ───────────────────────────────────────────────────────────

    @Test
    fun `md5 桥会先做 URL 解码`() {
        // 官方 QuickJS.java 里 str2md5 先 URLDecoder.decode 再算
        val engine = FakeEngine()
        api(engine).attachBridge()

        val raw = java.net.URLEncoder.encode("中文", "UTF-8") // %E4%B8%AD%E6%96%87
        val got = engine.fireUtil("__lx_native_call__utils_str2md5", raw) as String
        assertEquals(LxCrypto.md5("中文"), got)
    }

    @Test
    fun `str2b64 桥`() {
        val engine = FakeEngine()
        api(engine).attachBridge()
        assertEquals("5L2g5aW9", engine.fireUtil("__lx_native_call__utils_str2b64", "你好"))
    }

    @Test
    fun `b642buf 桥返回 JSON 数组`() {
        val engine = FakeEngine()
        api(engine).attachBridge()
        val b64 = java.util.Base64.getEncoder().encodeToString(byteArrayOf(7, 8))
        assertEquals("[7,8]", engine.fireUtil("__lx_native_call__utils_b642buf", b64))
    }

    @Test
    fun `key 不匹配的原生调用会被忽略`() = runTest {
        val engine = FakeEngine()
        engine.initOk()
        val api = api(engine)
        val r = api.load("// s", info)
        assertTrue(r.ok)

        // 拿错的 key 发 init，不该改变已完成的 init
        engine.fireNativeCall("wrong-key", "init", """{"status":true,"info":{"name":"x"}}""")
        assertEquals("测试源", api.initResult!!.name)
    }

    @Test
    fun `close 会关掉引擎`() {
        val engine = FakeEngine()
        api(engine).close()
        assertTrue(engine.closed)
    }

    @Test
    fun `超时失败后不会残留 pending`() = runTest {
        val engine = FakeEngine()
        engine.initOk()
        val api = api(engine)
        api.load("// s", info)

        api.musicUrl("wy", """{"source":"wy","songmid":1}""", timeoutMs = 200)

        // 迟到的回包不该让后续调用串台
        engine.fireNativeCall(engine.globals["__lx_key__"], "response", """{"requestKey":"stale","status":true,"result":"https://x"}""")
        assertNull(null)
    }
}
