package io.github.laozhudu.linkplay.lx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Buffer 垫片的**注入时机**与内容。
 *
 * 真机 0.9.98 的报错：`'Buffer' is not defined at lx-music-sdk.bundle.js:7948`。
 * 修法是在 bundle 之前先 evaluate 一份最小 Buffer —— 顺序错了等于没修
 * （bundle 里的 `var buf_key = Buffer.from("yeelion")` 是**模块顶层**就执行的）。
 *
 * 垫片本身的语义正确性在 Node 里和真实 Buffer 对照验过（15 组：UTF-8 中文往返、
 * base64 与 Node 输出逐字节一致、subarray、indexOf 定位 CRLF、kw 歌词解码全流程）；
 * 这里只能验 JVM 侧看得见的东西：注入顺序 + 脚本内容。
 */
class LxBufferPolyfillTest {

    @Test
    fun `polyfill is evaluated before the bundle`() {
        val engine = FakeEngine()
        val sdk = LxMusicSdk(engine, "/* bundle */")

        sdk.load()

        val names = engine.evaluated.map { it.first }
        assertEquals(
            "垫片必须排在 bundle 前面，否则模块顶层的 Buffer.from 照样炸",
            listOf(LxBufferPolyfill.FILENAME, "lx-music-sdk.bundle.js"),
            names,
        )
    }

    @Test
    fun `polyfill installs a global Buffer and keeps it idempotent`() {
        val script = LxBufferPolyfill.SCRIPT
        assertTrue("必须挂到全局", script.contains("g.Buffer = BufferShim"))
        // 已经有 Buffer 时直接返回：QuickJS 将来若内置 Buffer，不能把它覆盖掉
        assertTrue(script.contains("if (g.Buffer) return;"))
    }

    @Test
    fun `polyfill covers the methods the bundle actually uses`() {
        val script = LxBufferPolyfill.SCRIPT
        listOf("from:", "isBuffer:", "alloc:", "concat:").forEach {
            assertTrue("缺少 $it", script.contains(it))
        }
        listOf("toString = function", "subarray = function", "indexOf = function").forEach {
            assertTrue("缺少 $it", script.contains(it))
        }
    }

    @Test
    fun `user script engine also gets the polyfill before preload`() {
        val engine = FakeEngine()
        val api = LxUserApi(engine, "/* preload */")

        // load() 是 suspend 且会等 init 事件；这里只关心"第一个 evaluate 是什么"，
        // 超时分支返回 ok=false 无所谓
        // timeoutMs 给很小：这个用例只关心注入顺序，不想等 15 秒的 init 超时
        runCatching {
            kotlinx.coroutines.runBlocking {
                api.load("/* user */", LxScriptInfo("t"), timeoutMs = 50)
            }
        }

        assertTrue(engine.evaluated.isNotEmpty())
        assertEquals(LxBufferPolyfill.FILENAME, engine.evaluated.first().first)
    }
}
