package io.github.laozhudu.linkplay.lx

/**
 * 假引擎：把「JS 侧」用 Kotlin 模拟出来。
 *
 * 用途：QuickJS 是 Android 原生库，JVM 单测里跑不了。但 [LxUserApi] / [LxMusicSdk]
 * 真正容易错的地方不是引擎本身，而是**协议报文、超时、错误翻译** ——
 * 这些用假引擎就能完整验证。
 */
internal class FakeEngine : LxEngine {

    val globals = mutableMapOf<String, Any?>()
    val functions = mutableMapOf<String, (Array<Any?>) -> Any?>()
    val evaluated = mutableListOf<Pair<String, String>>()
    val callGlobals = mutableListOf<Pair<String, Array<out Any?>>>()

    /** evaluate 时的钩子：(filename, script) -> Unit */
    var onEvaluate: ((String, String) -> Unit)? = null

    /** callGlobal 时的钩子，可返回自定义值 */
    var onCallGlobal: ((String, Array<out Any?>) -> Any?)? = null

    var closed = false
        private set

    /** 从 Kotlin 侧模拟「JS 调用原生桥」 */
    fun fireNativeCall(vararg args: Any?) {
        functions["__lx_native_call__"]?.invoke(args.toList().toTypedArray())
    }

    fun fireUtil(name: String, vararg args: Any?): Any? =
        functions[name]?.invoke(args.toList().toTypedArray())

    override fun setGlobalString(name: String, value: String) {
        globals[name] = value
    }

    override fun setGlobalFunction(name: String, fn: (Array<Any?>) -> Any?) {
        functions[name] = fn
    }

    override fun evaluate(script: String, filename: String) {
        evaluated += filename to script
        onEvaluate?.invoke(filename, script)
    }

    override fun callGlobal(name: String, vararg args: Any?): Any? {
        callGlobals += name to args.copyOf()
        return onCallGlobal?.invoke(name, args)
    }

    override fun close() {
        closed = true
    }
}
