package io.github.laozhudu.diagnostics

/**
 * 诊断数据的持久化出口。
 *
 * ## 为什么是接口
 *
 * 库里不能依赖 Android（`Context.filesDir`）也不能依赖某个具体目录。
 * 接入方给一个实现：Android 用 `FileStore(filesDir)`，桌面用任意目录，测试用 [MemoryStore]。
 *
 * 所有方法**不许抛异常**：它们出现在"记录失败"的路径上，
 * 落盘失败本身不该变成一次崩溃。
 */
interface DiagnosticStore {
    fun write(name: String, text: String): Boolean
    fun read(name: String): String?
    fun delete(name: String): Boolean
    fun exists(name: String): Boolean

    companion object {
        /** 什么都不存（未配置时用）。 */
        val NONE: DiagnosticStore = object : DiagnosticStore {
            override fun write(name: String, text: String) = false
            override fun read(name: String): String? = null
            override fun delete(name: String) = false
            override fun exists(name: String) = false
        }
    }
}

/** 内存存储 —— 单测用，也用于"这次运行不留痕"的场景。 */
class MemoryStore : DiagnosticStore {
    private val files = java.util.concurrent.ConcurrentHashMap<String, String>()

    override fun write(name: String, text: String): Boolean {
        files[name] = text
        return true
    }

    override fun read(name: String): String? = files[name]

    override fun delete(name: String): Boolean = files.remove(name) != null

    override fun exists(name: String): Boolean = files.containsKey(name)
}
