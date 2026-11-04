package io.github.laozhudu.linkplay.lx

/**
 * JS 引擎的最小接口。
 *
 * 抽出来是为了让 [LxUserApi] / [LxMusicSdk] 的协议逻辑能在 JVM 上单测 ——
 * QuickJS 是 Android 原生库（JNI + .so），单测里跑不了，但协议报文、超时、
 * 错误翻译这些逻辑恰恰是最容易出错的地方。
 */
interface LxEngine {

    /** 设一个字符串全局变量（比如 `__lx_key__`）。 */
    fun setGlobalString(name: String, value: String)

    /**
     * 设一个可从 JS 同步调用的全局函数。
     *
     * 返回值会被 QuickJS 转成 JS 值；返回 String 就是 JS 字符串。
     */
    fun setGlobalFunction(name: String, fn: (Array<Any?>) -> Any?)

    /** 执行一段脚本。 */
    fun evaluate(script: String, filename: String)

    /** 调用 JS 里的全局函数（native → JS）。 */
    fun callGlobal(name: String, vararg args: Any?): Any?

    /** 释放引擎。 */
    fun close()
}
