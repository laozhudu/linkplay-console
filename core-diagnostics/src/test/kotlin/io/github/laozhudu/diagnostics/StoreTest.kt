package io.github.laozhudu.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class StoreTest {

    @Test
    fun `memory store round trips`() {
        val s = MemoryStore()
        assertTrue(s.write("a.txt", "hello"))
        assertEquals("hello", s.read("a.txt"))
        assertTrue(s.exists("a.txt"))
        assertTrue(s.delete("a.txt"))
        assertFalse(s.exists("a.txt"))
        assertNull(s.read("a.txt"))
    }

    @Test
    fun `none store writes nothing and never throws`() {
        val s = DiagnosticStore.NONE
        assertFalse(s.write("a", "b"))
        assertNull(s.read("a"))
        assertFalse(s.exists("a"))
    }

    @Test
    fun `file store writes reads and deletes`() {
        val dir = File(System.getProperty("java.io.tmpdir"), "diag-test-" + System.nanoTime())
        val s = FileStore(dir)
        try {
            assertTrue(s.write("issue.log", "line1\nline2"))
            assertEquals("line1\nline2", s.read("issue.log"))
            assertTrue(s.delete("issue.log"))
            assertNull(s.read("issue.log"))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `file store on unwritable path returns false instead of throwing`() {
        // 存储满 / 权限变更时，记日志这件事不能把调用方带崩
        val file = File(System.getProperty("java.io.tmpdir"), "diag-test-" + System.nanoTime())
        file.writeText("not a dir")
        val s = FileStore(File(file, "sub"))
        try {
            assertFalse(s.write("x.txt", "y"))
            assertNull(s.read("x.txt"))
        } finally {
            file.delete()
        }
    }

    @Test
    fun `file store lists by prefix newest first`() {
        val dir = File(System.getProperty("java.io.tmpdir"), "diag-test-" + System.nanoTime())
        val s = FileStore(dir)
        try {
            s.write("crash-1.txt", "a")
            Thread.sleep(5)
            s.write("crash-2.txt", "b")
            s.write("other.txt", "c")
            val names = s.list("crash-")
            assertEquals(listOf("crash-2.txt", "crash-1.txt"), names.map { it.name })
        } finally {
            dir.deleteRecursively()
        }
    }
}
