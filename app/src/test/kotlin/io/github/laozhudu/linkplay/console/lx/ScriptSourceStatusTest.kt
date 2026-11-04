package io.github.laozhudu.linkplay.console.lx

import io.github.laozhudu.script.ScriptCheck
import io.github.laozhudu.script.ScriptEntry
import io.github.laozhudu.script.ScriptRepository
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 锁住**报告里那句话**，不是内部字段 —— 诊断报告是唯一给用户看的东西。
 * 字段对了但排版丢了（"0 支"和"没装"看起来一样）等于没补。
 */
class ScriptSourceStatusTest {

    @After
    fun tearDown() = ScriptSourceStatus.clear()

    private fun entry(name: String, active: Boolean = false, version: String? = "v1.0.0") = ScriptEntry(
        name = name,
        fileName = "$name.js",
        version = version,
        author = "某人",
        sizeBytes = 2048,
        importedAtMs = 1_700_000_000_000,
        active = active,
    )

    @Test
    fun `empty library says what it means for playback`() {
        val text = ScriptSourceStatus.render(emptyList(), emptyList(), null)
        assertTrue(text.contains("库存=空"))
        // 这句话是重点：让用户明白"没脚本"不是"没装个东西"，而是"搜到也放不了"
        assertTrue(text.contains("搜到也放不了"))
    }

    @Test
    fun `installed scripts are listed with the active one marked`() {
        val text = ScriptSourceStatus.render(
            entries = listOf(entry("六音音源", active = true), entry("备用源", version = null)),
            unrecognized = emptyList(),
            activeName = "六音音源",
        )
        assertTrue(text.contains("库存=2 支"))
        assertTrue(text.contains("启用=六音音源"))
        assertTrue(text.contains("✓ 六音音源 v1.0.0"))
        assertTrue(text.contains("备用源 -"), )
    }

    @Test
    fun `files that are not scripts are named, not silently dropped`() {
        val text = ScriptSourceStatus.render(emptyList(), listOf("旧脚本.js"), null)
        assertTrue(text.contains("另有 1 个文件不是合规脚本"))
        assertTrue(text.contains("旧脚本.js"))
    }

    @Test
    fun `a rejected import shows the reason in plain words`() {
        ScriptSourceStatus.record(
            ScriptRepository.ImportResult.Rejected(ScriptCheck.NoName),
        )
        val text = ScriptSourceStatus.render(emptyList(), emptyList(), null)
        assertTrue(text.contains("最近导入="))
        assertTrue(text.contains("失败"))
        assertTrue(text.contains("@name"))
        assertTrue("失败要能被诊断段判成 WARN", ScriptSourceStatus.lastFailed())
    }

    @Test
    fun `a successful import clears the failure flag`() {
        ScriptSourceStatus.record(ScriptRepository.ImportResult.Rejected(ScriptCheck.Empty))
        assertTrue(ScriptSourceStatus.lastFailed())

        ScriptSourceStatus.record(
            ScriptRepository.ImportResult.Added(entry("六音音源", active = true)),
        )
        assertFalse(ScriptSourceStatus.lastFailed())
        assertTrue(ScriptSourceStatus.render(emptyList(), emptyList(), null).contains("成功"))
    }
}
