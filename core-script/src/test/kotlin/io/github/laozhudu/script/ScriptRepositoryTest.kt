package io.github.laozhudu.script

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * 仓库的规则测试。**全部跑在临时目录上**，不碰真机、不碰 Android ——
 * 这正是把存储做成 [ScriptStorage] 接口的收益。
 */
class ScriptRepositoryTest {

    @get:Rule
    val temp = TemporaryFolder()

    /** 用一支"洛雪风格"的假脚本，校验规则也写成同样的形状。 */
    private val validator = ScriptValidator { text ->
        when {
            text.isBlank() -> ScriptCheck.Empty
            !text.contains("/*!") -> ScriptCheck.NoHeader
            else -> {
                val name = Regex("@name\\s+(.+)").find(text)?.groupValues?.get(1)?.trim()
                val version = Regex("@version\\s+(.+)").find(text)?.groupValues?.get(1)?.trim()
                val author = Regex("@author\\s+(.+)").find(text)?.groupValues?.get(1)?.trim()
                if (name.isNullOrEmpty()) ScriptCheck.NoName
                else ScriptCheck.Ok(name = name, version = version, author = author)
            }
        }
    }

    private fun repo() = ScriptRepository(FileScriptStorage(temp.root), validator)

    private fun script(name: String, version: String = "v1.0.0") = """
        /*!
         * @name $name
         * @version $version
         * @author 某人
         */
        const { EVENT_NAMES } = globalThis.lx
    """.trimIndent()

    @Test
    fun `blank text is rejected as empty not as invalid`() {
        val result = repo().import(ScriptCandidate("   "))
        assertTrue(result is ScriptRepository.ImportResult.Rejected)
        assertEquals(ScriptCheck.Empty, (result as ScriptRepository.ImportResult.Rejected).check)
    }

    @Test
    fun `missing header block is told apart from missing name`() {
        val repository = repo()

        val noHeader = repository.import(ScriptCandidate("console.log(1)"))
        assertEquals(ScriptCheck.NoHeader, (noHeader as ScriptRepository.ImportResult.Rejected).check)

        val noName = repository.import(ScriptCandidate("/*!\n * @version v1\n */\nx"))
        assertEquals(ScriptCheck.NoName, (noName as ScriptRepository.ImportResult.Rejected).check)
    }

    @Test
    fun `first import is stored and switched on automatically`() {
        val result = repo().import(ScriptCandidate(script("六音音源"), ScriptOrigin.PASTED))
        val added = result as ScriptRepository.ImportResult.Added

        assertEquals("六音音源", added.entry.name)
        assertEquals("v1.0.0", added.entry.version)
        assertEquals("某人", added.entry.author)
        assertTrue("第一次导入要自动启用，否则界面上等于没装", added.entry.active)
        assertTrue(added.entry.sizeBytes > 0)
    }

    @Test
    fun `importing the same name again updates and reports the previous version`() {
        val repository = repo()
        repository.import(ScriptCandidate(script("六音音源", "v1.0.0")))
        val second = repository.import(ScriptCandidate(script("六音音源", "v1.2.1")))

        val updated = second as ScriptRepository.ImportResult.Updated
        assertEquals("v1.2.1", updated.entry.version)
        assertEquals("v1.0.0", updated.previousVersion)
        assertEquals(1, repository.list().size)
    }

    @Test
    fun `a name that looks like a path cannot escape the directory`() {
        val repository = repo()
        repository.import(ScriptCandidate(script("../../etc/passwd")))

        val entry = repository.list().single()
        assertFalse(entry.fileName.contains("/"))
        assertFalse(entry.fileName.contains(".."))
        assertTrue(entry.fileName.endsWith(".js"))
        // 目录里只有这一份，没有跑到外面去
        assertEquals(1, temp.root.listFiles()!!.count { it.isFile && !it.name.startsWith("_") })
    }

    @Test
    fun `a different name is a second script, never a rename`() {
        // 脚本名字是脚本自己声明的，我们判断不了"这两支其实是一支"，所以不删用户文件。
        // 乱删的代价远大于列表里多一条（用户随时能自己删）。
        val repository = repo()
        repository.import(ScriptCandidate(script("旧名字")))
        repository.import(ScriptCandidate(script("新名字", "v2")))

        assertEquals(listOf("旧名字", "新名字"), repository.list().map { it.name })
        assertNotNull(repository.textOf(repository.fileNameOf("旧名字")))
    }

    @Test
    fun `active follows the marker and can be switched or cleared`() {
        val repository = repo()
        repository.import(ScriptCandidate(script("甲")))
        repository.import(ScriptCandidate(script("乙")))

        // 第二支导入时已有启用项，不该抢
        assertEquals("甲", repository.active()!!.name)

        repository.setActive(repository.list().first { it.name == "乙" }.fileName)
        assertEquals("乙", repository.active()!!.name)

        repository.setActive(null)
        assertNull(repository.active())
    }

    @Test
    fun `deleting the active script clears the marker too`() {
        val repository = repo()
        repository.import(ScriptCandidate(script("甲")))
        val entry = repository.list().single()

        assertTrue(repository.delete(entry.fileName))
        assertTrue(repository.list().isEmpty())
        assertNull(repository.active())
    }

    @Test
    fun `a script dropped straight onto the disk shows up without restart`() {
        val repository = repo()
        java.io.File(temp.root, "手放的.js").writeText(script("手放的"))

        assertEquals(listOf("手放的"), repository.list().map { it.name })
    }

    @Test
    fun `files that are not scripts are reported instead of silently hidden`() {
        val repository = repo()
        java.io.File(temp.root, "随便什么.js").writeText("这不是脚本")

        assertTrue(repository.list().isEmpty())
        assertEquals(listOf("随便什么.js"), repository.unrecognized())
    }

    @Test
    fun `the active marker itself never shows up as a script`() {
        val repository = repo()
        repository.import(ScriptCandidate(script("甲")))

        assertTrue(temp.root.listFiles()!!.any { it.name == ScriptRepository.ACTIVE_MARKER })
        assertEquals(1, repository.list().size)
    }
}
