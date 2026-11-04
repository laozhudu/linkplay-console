package io.github.laozhudu.linkplay.lx

import io.github.laozhudu.script.ScriptCheck
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LxScriptValidatorTest {

    private val validator = LxScriptValidator()

    private val good = """
        /*!
         * @name 六音音源
         * @description v1.2.1 如失效请前往下载最新版本
         * @version v1.2.1
         * @author 六音
         */
        const { EVENT_NAMES } = globalThis.lx
    """.trimIndent()

    @Test
    fun `a real script yields name version and author`() {
        val check = validator.validate(good) as ScriptCheck.Ok
        assertEquals("六音音源", check.name)
        assertEquals("v1.2.1", check.version)
        assertEquals("六音", check.author)
    }

    @Test
    fun `blank is empty not invalid`() {
        assertEquals(ScriptCheck.Empty, validator.validate("   \n  "))
    }

    @Test
    fun `a script from another app is reported as no header`() {
        assertEquals(ScriptCheck.NoHeader, validator.validate("console.log('hello')"))
    }

    @Test
    fun `a header without name is reported as missing name, not as no header`() {
        val noName = """
            /*!
             * @version v1.0
             * @author 某人
             */
            x()
        """.trimIndent()
        assertEquals(ScriptCheck.NoName, validator.validate(noName))
    }

    @Test
    fun `an empty name value is not silently accepted`() {
        val emptyName = """
            /*!
             * @name
             * @version v1.0
             */
            x()
        """.trimIndent()
        val check = validator.validate(emptyName)
        assertTrue("空名字不能当成合法脚本", check !is ScriptCheck.Ok)
    }
}
