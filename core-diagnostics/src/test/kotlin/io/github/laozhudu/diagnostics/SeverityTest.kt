package io.github.laozhudu.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Test

class SeverityTest {

    @Test
    fun `worst picks the most severe`() {
        assertEquals(Severity.ERROR, Severity.worst(listOf(Severity.INFO, Severity.ERROR, Severity.WARN)))
    }

    @Test
    fun `worst of nothing is info not a crash`() {
        // 空集合不能抛：报告在没有段、没有问题记录时也要能生成
        assertEquals(Severity.INFO, Severity.worst(emptyList()))
    }

    @Test
    fun `fatal outranks error`() {
        assertEquals(Severity.FATAL, Severity.worst(listOf(Severity.ERROR, Severity.FATAL)))
    }

    @Test
    fun `code round trips`() {
        Severity.entries.forEach { assertEquals(it, Severity.ofCode(it.code)) }
    }

    @Test
    fun `unknown code falls back to info instead of throwing`() {
        // 落盘日志被手改过/版本不同，读回来不能崩
        assertEquals(Severity.INFO, Severity.ofCode("?"))
    }
}
