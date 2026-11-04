package io.github.laozhudu.script

import java.io.File

/**
 * 把脚本存在一个目录里。目录由调用方给（Android 传 `filesDir/scripts`）。
 *
 * ## 为什么不做缓存
 *
 * 缓存会让"磁盘上实际有什么"和"仓库以为有什么"出现分歧。用户完全可能
 * 用文件管理器把脚本拷进来 —— 那时缓存就是错的，而且错到下次重启才被发现。
 *
 * 读全文的成本：一支脚本几十 KB，家里几支。列表是用户点开才发生，不是每秒一次。
 */
class FileScriptStorage(private val dir: File) : ScriptStorage {

    override fun list(): List<StoredScript> {
        val files = dir.listFiles() ?: return emptyList()
        return files.filter { it.isFile }.mapNotNull { file ->
            // 读不出来的文件跳过，不让它把整次列举搞崩
            runCatching {
                StoredScript(
                    fileName = file.name,
                    text = file.readText(),
                    lastModifiedMs = file.lastModified(),
                )
            }.getOrNull()
        }
    }

    override fun read(fileName: String): String? {
        val file = File(dir, fileName)
        if (!file.isFile) return null
        return runCatching { file.readText() }.getOrNull()
    }

    override fun write(fileName: String, text: String) {
        if (!dir.isDirectory && !dir.mkdirs()) {
            throw java.io.IOException("建目录失败：${dir.absolutePath}")
        }
        // 先写临时文件再改名：写到一半断电/被杀，不会留下半支脚本（半支脚本会被当成"脚本坏了"）
        val target = File(dir, fileName)
        val temp = File(dir, "$fileName.tmp")
        temp.writeText(text)
        if (!temp.renameTo(target)) {
            // 某些文件系统上 rename 到已存在文件会失败 —— 退化成直接覆盖
            target.writeText(text)
            temp.delete()
        }
    }

    override fun delete(fileName: String): Boolean = File(dir, fileName).delete()
}
