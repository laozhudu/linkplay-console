package io.github.laozhudu.linkplay.console.backup

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * 配置备份 —— 把 App 自己的数据目录打成一个 zip，换手机/重装时能整个搬回去。
 *
 * ## 为什么是"整个目录"而不是"逐个列举要备份的东西"
 *
 * 逐个列举一定会漏：这项目已经有过 5 处落盘（设备列表、播放会话、歌单、电台、脚本、
 * 输出选择），下次加第 6 处时没人会记得回来改备份清单 —— 而漏掉的那部分
 * 会在"导入后少了点东西"时表现为一个没人能解释的 bug。
 *
 * 所以反过来：**默认全备，显式排除**。排除清单只有两项（诊断日志、崩溃记录）——
 * 那是"上一次的现场"，搬到新机上只是噪音。
 *
 * ## 为什么核心逻辑吃 `File` 而不是 `Context`
 *
 * 这样单测能在临时目录里跑完整往返（导出 → 清空 → 导入），不依赖真机。
 * Android 那一层只负责把 `filesDir` 传进来。
 */
object ConfigBackup {

    /** 不备份的目录：诊断与崩溃记录属于"上一次的现场"，不是配置。 */
    private val EXCLUDED = setOf("diagnostics", "crash")

    /** zip 里标记格式版本：以后格式变了能认出旧包。 */
    private const val MANIFEST = "backup.txt"
    private const val FORMAT = "linkplay-console-backup v1"

    data class Result(val files: Int, val bytes: Long)

    /**
     * 把 [root] 下除排除项之外的一切打进 [target]。
     *
     * 失败抛异常（由调用方转成给用户看的一句话）。
     */
    fun exportTo(root: File, target: File): Result {
        if (!root.isDirectory) throw IllegalStateException("没有可备份的目录：${root.absolutePath}")
        target.parentFile?.mkdirs()
        var count = 0
        var bytes = 0L
        ZipOutputStream(target.outputStream().buffered()).use { zip ->
            zip.putNextEntry(ZipEntry(MANIFEST))
            zip.write(FORMAT.toByteArray())
            zip.closeEntry()

            root.walkTopDown()
                .onEnter { it == root || it.name !in EXCLUDED }
                .filter { it.isFile }
                .forEach { file ->
                    // 用相对路径当条目名：导入时按同一个相对路径写回去
                    val name = file.relativeTo(root).path.replace(File.separatorChar, '/')
                    zip.putNextEntry(ZipEntry(name))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                    count++
                    bytes += file.length()
                }
        }
        return Result(count, bytes)
    }

    /**
     * 把 [source] 里的内容解压回 [root]。**只覆盖同名文件，不删除**目录里已有的东西 ——
     * 删除是不可逆的，而"多留一个旧文件"最坏只是占点空间。
     */
    fun importFrom(root: File, source: File): Result {
        if (!source.isFile) throw IllegalStateException("找不到备份文件")
        root.mkdirs()
        var count = 0
        var bytes = 0L
        ZipInputStream(source.inputStream().buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory) continue
                val name = entry.name
                if (name == MANIFEST) {
                    val head = zip.readBytes().toString(Charsets.UTF_8)
                    if (!head.startsWith("linkplay-console-backup")) {
                        throw IllegalStateException("这不像声控台的备份包（$head）")
                    }
                    continue
                }
                // 防路径穿越：条目名带 ../ 时不能写到 root 外面去
                val out = File(root, name).canonicalFile
                if (!out.path.startsWith(root.canonicalFile.path + File.separator)) {
                    throw IllegalStateException("备份包里有异常路径：$name")
                }
                out.parentFile?.mkdirs()
                out.outputStream().use { zip.copyTo(it) }
                count++
                bytes += out.length()
            }
        }
        return Result(count, bytes)
    }

    /** 备份时被排除的目录名，供诊断说明"少的是什么"。 */
    fun excludedNames(): Set<String> = EXCLUDED
}
