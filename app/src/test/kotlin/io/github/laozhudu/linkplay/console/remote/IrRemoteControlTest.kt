package io.github.laozhudu.linkplay.console.remote

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 红外遥控的「能画什么」规则。
 *
 * 这些用例锁的不是编码（编码由 `NecEncoderTest` 锁），而是**界面该不该出现按键** ——
 * 这一条决定了用户会不会看到一个按了没反应的遥控器。
 */
class IrRemoteControlTest {

    private fun control(
        hasEmitter: Boolean = true,
        transmit: (IntArray) -> String? = { null },
        codebook: Map<RemoteKey, Int> = mapOf(RemoteKey.Power to 0x08, RemoteKey.VolumeUp to 0x10),
    ) = IrRemoteControl(
        hasEmitter = { hasEmitter },
        transmit = transmit,
        codebook = codebook,
    )

    @Test
    fun `no emitter means zero keys`() {
        // 没有发射器时界面必须**整块不显示**，而不是画出来再点了没反应
        assertTrue(control(hasEmitter = false).keys().isEmpty())
    }

    @Test
    fun `with emitter keys come from the codebook`() {
        assertEquals(setOf(RemoteKey.Power, RemoteKey.VolumeUp), control().keys())
    }

    @Test
    fun `key missing from codebook is unsupported not failed`() {
        // 「这份码表没有这个键」和「发不出去」是两件事：
        // 前者界面不该画，后者界面该提示原因
        val result = runBlocking { control().send(RemoteKey.Menu) }
        assertEquals(RemoteResult.Unsupported, result)
    }

    @Test
    fun `transmit success reports sent not success`() {
        val result = runBlocking { control().send(RemoteKey.Power) }
        // **红外没有回执**：只能说"已发出"，不能说"已成功"
        assertEquals(RemoteResult.Sent, result)
    }

    @Test
    fun `transmit failure carries the reason`() {
        val result = runBlocking {
            control(transmit = { "载波 38000Hz 不被支持" }).send(RemoteKey.Power)
        }
        assertTrue(result is RemoteResult.Failed)
        assertEquals("载波 38000Hz 不被支持", (result as RemoteResult.Failed).reason)
    }

    @Test
    fun `no emitter send fails with a readable reason`() {
        val result = runBlocking { control(hasEmitter = false).send(RemoteKey.Power) }
        assertTrue(result is RemoteResult.Failed)
        assertFalse((result as RemoteResult.Failed).reason.isBlank())
    }

    @Test
    fun `diagnose line states codebook is not the real library`() {
        val line = control().diagnoseLine()
        // 诊断里必须能看出"码表是示例"，否则排查时会以为码库已经接上了
        assertTrue(line.contains("未接码库"))
        assertTrue(line.contains("回执=无"))
    }

    @Test
    fun `attempt is recorded for diagnostics`() {
        var recorded: Pair<Boolean, String?>? = null
        val c = IrRemoteControl(
            hasEmitter = { true },
            transmit = { null },
            codebook = mapOf(RemoteKey.Power to 0x08),
            onAttempt = { ok, note -> recorded = ok to note },
        )
        runBlocking { c.send(RemoteKey.Power) }
        assertEquals(true, recorded?.first)
    }
}
