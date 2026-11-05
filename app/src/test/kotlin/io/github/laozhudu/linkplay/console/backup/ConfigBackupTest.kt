package io.github.laozhudu.linkplay.console.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** 完整往返：导出 → 清空 → 导入。这是备份功能唯一能自动验证的部分。 */
class ConfigBackupTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun seed(root: File) {
        File(root, "devices.json").apply { parentFile?.mkdirs() }.writeText("[[\"doss1\"]]")
        File(root, "scripts/六音音源.js").apply { parentFile?.mkdirs() }.writeText("/*! @name 六音音源 */")
        File(root, "playback-output.txt").writeText("local")
        // 诊断与崩溃**不该**进备份
        File(root, "diagnostics/issue-log.txt").apply { parentFile?.mkdirs() }.writeText("旧日志")
        File(root, "crash/last.txt").apply { parentFile?.mkdirs() }.writeText("旧崩溃")
    }

    @Test
    fun `export then import restores config and skips diagnostics`() {
        val root = temp.newFolder("files")
        seed(root)
        val zip = File(temp.root, "backup.zip")

        val exported = ConfigBackup.exportTo(root, zip)
        assertEquals("只该备份 3 个配置/脚本文件", 3, exported.files)
        assertTrue(zip.length() > 0)

        // 模拟 pm clear：把整个目录删掉
        root.deleteRecursively()
        root.mkdirs()

        val imported = ConfigBackup.importFrom(root, zip)
        assertEquals(3, imported.files)
        assertEquals("[[\"doss1\"]]", File(root, "devices.json").readText())
        assertEquals("local", File(root, "playback-output.txt").readText())
        assertTrue(File(root, "scripts/六音音源.js").isFile)
        assertTrue("诊断不该被恢复（它是上一次的现场）", !File(root, "diagnostics/issue-log.txt").exists())
    }

    @Test
    fun `import keeps files that are not in the bundle`() {
        val root = temp.newFolder("files2")
        seed(root)
        val zip = File(temp.root, "b2.zip")
        ConfigBackup.exportTo(root, zip)
        File(root, "newer-setting.txt").writeText("导入前才有的东西")

        ConfigBackup.importFrom(root, zip)
        assertTrue("不删除目录里已有的文件", File(root, "newer-setting.txt").isFile)
    }

    @Test
    fun `a foreign zip is rejected instead of half applied`() {
        val root = temp.newFolder("files3")
        root.mkdirs()
        val fake = File(temp.root, "fake.zip")
        java.util.zip.ZipOutputStream(fake.outputStream()).use { z ->
            z.putNextEntry(java.util.zip.ZipEntry("backup.txt"))
            z.write("some other app".toByteArray())
            z.closeEntry()
        }
        val error = runCatching { ConfigBackup.importFrom(root, fake) }.exceptionOrNull()
        assertTrue("应该拒绝，实际=$error", error != null)
    }
}
