package io.github.laozhudu.linkplay.protocol.remote

import io.github.laozhudu.linkplay.protocol.DeviceHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 翻译表与回包判读 —— 这是整个语义指令层**唯一会漂移的地方**。
 *
 * ## 为什么这些断言必须逐字写死
 *
 * 翻译错了不会编译报错，也不会在别的测试里暴露：`setPlayerCmd:seek:12` 写成
 * `setPlayerCmd:seek=12`，设备回 500，界面上就是"跳转没反应"。
 * 而这类错误只有靠**逐字比对**才能挡住。
 *
 * 断言的是"翻译出来长什么样"，不是"翻译调了哪个函数" ——
 * 后者会让测试变成实现的镜像，重构一次就全红。
 */
class LinkPlayTransportTest {

    private val transport = LinkPlayTransport(DeviceHttpClient())

    // ---------- 翻译表 ----------

    @Test
    fun `playback commands translate to the factory dialect`() {
        assertEquals("setPlayerCmd:play", transport.encode(RemoteCommand.Play))
        assertEquals("setPlayerCmd:pause", transport.encode(RemoteCommand.Pause))
        assertEquals("setPlayerCmd:stop", transport.encode(RemoteCommand.Stop))
        assertEquals("setPlayerCmd:next", transport.encode(RemoteCommand.Next))
        assertEquals("setPlayerCmd:prev", transport.encode(RemoteCommand.Previous))
    }

    @Test
    fun `seek uses colon separator and seconds`() {
        // 分隔符错了设备就 500，界面上是"拖动没反应"
        assertEquals("setPlayerCmd:seek:125", transport.encode(RemoteCommand.Seek(125)))
    }

    @Test
    fun `negative seek is clamped to zero`() {
        // 滑块在起点附近可能算出负数；发负秒设备行为未定义
        assertEquals("setPlayerCmd:seek:0", transport.encode(RemoteCommand.Seek(-5)))
    }

    @Test
    fun `volume is clamped to 0-100`() {
        assertEquals("setPlayerCmd:vol:0", transport.encode(RemoteCommand.SetVolume(-20)))
        assertEquals("setPlayerCmd:vol:100", transport.encode(RemoteCommand.SetVolume(999)))
        assertEquals("setPlayerCmd:vol:45", transport.encode(RemoteCommand.SetVolume(45)))
    }

    @Test
    fun `mute and unmute are 1 and 0`() {
        assertEquals("setPlayerCmd:mute:1", transport.encode(RemoteCommand.Mute))
        assertEquals("setPlayerCmd:mute:0", transport.encode(RemoteCommand.Unmute))
    }

    @Test
    fun `select input passes the device's own id`() {
        // 输入源集合各家不同，我们只负责原样转发
        assertEquals("setPlayerCmd:switchmode:wifi", transport.encode(RemoteCommand.SelectInput("wifi")))
        assertEquals("setPlayerCmd:switchmode:optical", transport.encode(RemoteCommand.SelectInput("optical")))
    }

    @Test
    fun `preset ui index is zero based and converted to one based`() {
        // 界面传 0..5，设备要 1..6。差一在这里转，且只在这里转
        assertEquals("MCUKeyShortClick:1", transport.encode(RemoteCommand.Preset(0)))
        assertEquals("MCUKeyShortClick:6", transport.encode(RemoteCommand.Preset(5)))
    }

    @Test
    fun `preset index out of range is clamped not thrown`() {
        // 界面按钮只有 6 个，但语音/脚本可能传越界值 —— 钳制比抛异常好
        assertEquals("MCUKeyShortClick:1", transport.encode(RemoteCommand.Preset(-3)))
        assertEquals("MCUKeyShortClick:6", transport.encode(RemoteCommand.Preset(99)))
    }

    // ---------- 回包判读 ----------

    @Test
    fun `plain OK is acked`() {
        assertTrue(transport.judge("OK").isAcked)
        assertTrue(transport.judge("OK\n").isAcked)
        assertTrue(transport.judge("ok").isAcked)
    }

    @Test
    fun `empty body counts as acked`() {
        // 部分固件对 pause/stop 就回空 body，实测是成功的
        assertTrue(transport.judge("").isAcked)
        assertTrue(transport.judge("   ").isAcked)
    }

    @Test
    fun `bare numeric reply is a rejection not a success`() {
        // LinkPlay 用 HTTP 200 + 裸错误码表达业务错误。这是最容易误判成成功的形态
        val r = transport.judge("704")
        assertFalse(r.isAcked)
        assertEquals(DispatchFailure.Rejected(704, "704"), (r as DispatchResult.Failed).failure)
    }

    @Test
    fun `Failed and Error prefixes are rejections`() {
        assertFalse(transport.judge("Failed").isAcked)
        assertFalse(transport.judge("Error: no media").isAcked)
        assertFalse(transport.judge("failed to play").isAcked)
    }

    @Test
    fun `error words win over OK appearing later in the text`() {
        // 顺序有意：先认失败词再认 OK。反过来的话 "Failed: OK timeout" 会被误判成成功
        assertFalse(transport.judge("Failed: OK timeout").isAcked)
    }

    @Test
    fun `unrecognised reply is never guessed as success`() {
        // 猜成成功是这类项目最贵的错：用户看到"已发送"但什么都没发生
        val r = transport.judge("<html>proxy error</html>")
        assertFalse(r.isAcked)
        assertTrue((r as DispatchResult.Failed).failure is DispatchFailure.Unparsable)
    }

    @Test
    fun `unparsable keeps the raw text for diagnosis`() {
        val r = transport.judge("<html>proxy error</html>") as DispatchResult.Failed
        assertTrue((r.failure as DispatchFailure.Unparsable).raw.contains("proxy error"))
    }

    // ---------- supports ----------

    @Test
    fun `linkplay supports every business command`() {
        val all = listOf(
            RemoteCommand.Play, RemoteCommand.Pause, RemoteCommand.Stop,
            RemoteCommand.Next, RemoteCommand.Previous, RemoteCommand.Seek(1),
            RemoteCommand.SetVolume(1), RemoteCommand.Mute, RemoteCommand.Unmute,
            RemoteCommand.SelectInput("wifi"), RemoteCommand.Preset(0),
        )
        all.forEach { assertTrue("应支持 $it", transport.supports(it)) }
    }

    // ---------- 网络失败不抛异常 ----------

    @Test
    fun `unreachable device returns Failed instead of throwing`() = kotlinx.coroutines.runBlocking {
        // 127.0.0.1:1 必然拒绝连接。dispatch 必须返回 Failed，不能抛
        val r = transport.dispatch("127.0.0.1", RemoteCommand.Play)
        assertFalse(r.isAcked)
        assertTrue((r as DispatchResult.Failed).failure is DispatchFailure.Unreachable)
    }
}
