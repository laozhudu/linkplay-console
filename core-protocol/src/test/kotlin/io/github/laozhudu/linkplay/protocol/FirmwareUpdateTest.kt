package io.github.laozhudu.linkplay.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 固件升级状态解析 + 预设键/屏蔽命令格式。
 *
 * 真机原文（`.164`，2026-11-03）：
 * ```
 * getMvRemoteUpdateStart  → OK
 * getMvRemoteUpdateStatus → 0
 * getMvRomDownloadStatus  → {"status":"0","count":"0","progress":"0","name":"","ver":"0","new_ver":"0",…}
 * getMvRomBurnPrecent     → {"status":"0","progress":"0"}
 * ```
 */
class FirmwareUpdateTest {

    private val idleDl = """{"status":"0","count":"0","progress":"0","name":"","ver":"0","new_ver":"0"}"""
    private val idleBurn = """{"status":"0","progress":"0"}"""

    @Test
    fun `no update when device reports zero`() {
        val f = FirmwareUpdate.of("0", idleDl, idleBurn)
        assertFalse(f.hasUpdate)
        assertEquals("没有可用更新", f.phase)
        assertFalse(f.canStart)
    }

    @Test
    fun `non zero remote status means an update exists`() {
        // 真机 .164 早先返回过 22，后来变 0 —— 非 0 一律视为"有可用版本"，不在界面猜含义
        val f = FirmwareUpdate.of("22", idleDl, idleBurn)
        assertTrue(f.hasUpdate)
        assertEquals("发现新版本，等待开始下载", f.phase)
        assertTrue(f.canStart)
    }

    @Test
    fun `download progress wins before burn`() {
        val f = FirmwareUpdate.of(
            "22",
            """{"status":"1","progress":"42","name":"rom.bin","ver":"3.6.5310","new_ver":"3.6.6623"}""",
            idleBurn,
        )
        assertEquals("正在下载固件 42%", f.phase)
        assertEquals("3.6.6623", f.newVersion)
        assertEquals("rom.bin", f.romName)
        assertFalse("下载中就不要再点开始", f.canStart)
    }

    @Test
    fun `burn progress takes over once download finished`() {
        val f = FirmwareUpdate.of(
            "22",
            """{"status":"1","progress":"100"}""",
            """{"status":"1","progress":"30"}""",
        )
        assertEquals("正在写入固件 30%", f.phase)
    }

    @Test
    fun `completed burn is reported as done`() {
        val f = FirmwareUpdate.of("22", """{"progress":"100"}""", """{"progress":"100"}""")
        assertEquals("固件写入完成", f.phase)
    }

    @Test
    fun `garbage json does not crash and reports unknown`() {
        val f = FirmwareUpdate.of("junk", "<html>500</html>", null)
        assertFalse(f.hasUpdate)
        assertEquals("没有可用更新", f.phase)
    }

    @Test
    fun `progress is clamped to 0 to 100`() {
        val f = FirmwareUpdate.of("22", """{"progress":"250"}""", null)
        assertEquals(100, f.downloadPercent)
    }

    @Test
    fun `preset key index is 1 based and clamped`() {
        // 反编译 com.wifiaudio.view.pagesdevcenter.i：命令里用的是 i + 1
        assertEquals("MCUKeyShortClick:1", LinkPlayCommands.presetKey(0))
        assertEquals("MCUKeyShortClick:6", LinkPlayCommands.presetKey(5))
        assertEquals("MCUKeyShortClick:6", LinkPlayCommands.presetKey(99))
        assertEquals("MCUKeyShortClick:1", LinkPlayCommands.presetKey(-3))
    }

    @Test
    fun `slave mask and unmask commands`() {
        assertEquals("multiroom:SlaveMask:10.10.10.92", LinkPlayCommands.slaveMask("10.10.10.92"))
        assertEquals("multiroom:SlaveUnMask:10.10.10.92", LinkPlayCommands.slaveUnMask("10.10.10.92"))
    }

    @Test
    fun `direct setNetwork encodes whether a password is present`() {
        assertEquals("setNetwork:0:MyWifi", LinkPlayCommands.setNetworkDirect("MyWifi", ""))
        assertEquals("setNetwork:1:MyWifi:secret", LinkPlayCommands.setNetworkDirect("MyWifi", "secret"))
    }
}
