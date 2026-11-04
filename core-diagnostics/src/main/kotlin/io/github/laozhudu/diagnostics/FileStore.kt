package io.github.laozhudu.diagnostics

import java.io.File

/**
 * 落盘到某个目录 —— 纯 `java.io`，不依赖 Android。
 *
 * Android 侧直接 `FileStore(context.filesDir)`；桌面/服务端传任意目录。
 * 所有操作包了 `runCatching`：目录不可写（存储满、权限变更）时返回失败，
 * 绝不让"记日志"这件事把调用方带崩。
 */
class FileStore(private val dir: File) : DiagnosticStore {

    private fun file(name: String) = File(dir, name)

    override fun write(name: String, text: String): Boolean = runCatching {
        if (!dir.exists()) dir.mkdirs()
        file(name).writeText(text)
        true
    }.getOrDefault(false)

    override fun read(name: String): String? = runCatching {
        val f = file(name)
        if (f.isFile && f.length() > 0) f.readText() else null
    }.getOrNull()

    override fun delete(name: String): Boolean =
        runCatching { file(name).delete() }.getOrDefault(false)

    override fun exists(name: String): Boolean =
        runCatching { file(name).isFile && file(name).length() > 0 }.getOrDefault(false)

    /** 列出以 [prefix] 开头的文件，**新→旧**（滚动清理用）。 */
    fun list(prefix: String): List<File> = runCatching {
        dir.listFiles { f -> f.isFile && f.name.startsWith(prefix) }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }.getOrDefault(emptyList())
}
