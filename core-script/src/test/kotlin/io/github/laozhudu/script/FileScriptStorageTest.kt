package io.github.laozhudu.script

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FileScriptStorageTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `write then read round trips`() {
        val storage = FileScriptStorage(java.io.File(temp.root, "scripts"))
        storage.write("a.js", "内容")

        assertEquals("内容", storage.read("a.js"))
        assertEquals(1, storage.list().size)
    }

    @Test
    fun `missing file reads as null instead of throwing`() {
        val storage = FileScriptStorage(temp.root)
        assertNull(storage.read("没有这个.js"))
    }

    @Test
    fun `overwriting keeps a single file and no temp leftovers`() {
        val storage = FileScriptStorage(temp.root)
        storage.write("a.js", "第一版")
        storage.write("a.js", "第二版")

        assertEquals("第二版", storage.read("a.js"))
        val names = temp.root.listFiles()!!.map { it.name }
        assertEquals(listOf("a.js"), names)
    }

    @Test
    fun `delete reports whether anything was removed`() {
        val storage = FileScriptStorage(temp.root)
        storage.write("a.js", "x")

        assertTrue(storage.delete("a.js"))
        assertEquals(0, storage.list().size)
    }
}
