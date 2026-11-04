package io.github.laozhudu.linkplay.lx

import com.whl.quickjs.android.QuickJSLoader
import com.whl.quickjs.wrapper.QuickJSContext
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 基于 QuickJS 的引擎实现（官方 `wang.harlon.quickjs:wrapper-android`，Apache-2.0）。
 *
 * ## 线程模型（这一块最容易出错）
 *
 * QuickJS 上下文**不是线程安全的**，所有 evaluate / call 必须在同一个线程上。
 * 官方实现用的是 `HandlerThread` + Message；这里用单线程池 + 同步等待，效果一样但更简单。
 *
 * 两个关键点：
 * 1. **可重入**：JS 回调进来时我们已经在引擎线程上，这时再调 `callGlobal`
 *    （比如把 HTTP 结果回灌给 JS）必须**直接执行**，不能再提交一次任务 ——
 *    否则自己等自己，直接死锁。
 * 2. **超时兜底**：外部线程提交任务后最多等 `timeoutMs`，引擎卡死也不会把调用方拖死。
 */
class QuickJsEngine(
    private val timeoutMs: Long = 10_000,
) : LxEngine {

    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "lx-quickjs").apply { isDaemon = true }
    }

    @Volatile
    private var engineThreadId: Long = -1

    private val ctx: QuickJSContext = runOnEngine {
        QuickJSLoader.init()
        engineThreadId = Thread.currentThread().id
        QuickJSContext.create()
    }

    /** 在引擎线程上执行；已经在引擎线程上就直接跑（避免自锁）。 */
    private fun <T> runOnEngine(block: () -> T): T {
        if (Thread.currentThread().id == engineThreadId) return block()
        val future = executor.submit(Callable { block() })
        return try {
            future.get(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (e: Exception) {
            future.cancel(true)
            throw e
        }
    }

    override fun setGlobalString(name: String, value: String) = runOnEngine {
        ctx.globalObject.setProperty(name, value)
    }

    override fun setGlobalFunction(name: String, fn: (Array<Any?>) -> Any?) = runOnEngine {
        ctx.globalObject.setProperty(name) { args -> fn(args) }
    }

    override fun evaluate(script: String, filename: String) = runOnEngine {
        ctx.evaluate(script, filename)
        Unit
    }

    override fun callGlobal(name: String, vararg args: Any?): Any? = runOnEngine {
        ctx.globalObject.getJSFunction(name).call(*args)
    }

    override fun close() {
        runCatching { runOnEngine { ctx.destroy() } }
        executor.shutdownNow()
    }

    private val QuickJSContext.globalObject
        get() = getGlobalObject()
}
