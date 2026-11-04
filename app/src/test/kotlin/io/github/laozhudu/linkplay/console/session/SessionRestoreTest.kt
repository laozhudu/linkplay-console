package io.github.laozhudu.linkplay.console.session

import io.github.laozhudu.linkplay.console.ConsoleHost
import io.github.laozhudu.linkplay.console.ConsoleUiState
import io.github.laozhudu.linkplay.protocol.DeviceHttpClient
import io.github.laozhudu.linkplay.protocol.LinkPlayStatus
import io.github.laozhudu.linkplay.protocol.session.PlaybackSession
import io.github.laozhudu.linkplay.protocol.session.SavedDevice
import io.github.laozhudu.linkplay.protocol.upnp.UpnpRendererClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 会话恢复的判定规则。
 *
 * 这些用例锁的是用户最常报的一句话：「重开 App 列表是空的」。
 * 它背后有三种原因（没读到设备 / 回到设备但没列表 / 都没有），
 * 界面看起来一样，只有 [RestoreStatus] 能分辨 —— 所以先把"哪一种"锁住。
 *
 * 只测**设备恢复**这一段：队列恢复要走网络（设备往返、BrowseQueue），
 * 已经不是纯 JVM 能验的了，靠真机诊断报告的「启动恢复」段确认。
 */
class SessionRestoreTest {

    @Test
    fun `按 uuid 找回上次设备并静默选中`() {
        val d = device()
        val host = FakeHost(listOf(d), key = d.uuid)
        val restore = SessionRestore(host)

        restore.restoreLastDevice()

        assertEquals("192.168.99.164", host.state.value.selectedIp)
        assertEquals(d.uuid, host.state.value.selectedUuid)
        assertEquals("http://192.168.99.164:49152/description.xml", host.state.value.selectedLocation)
        assertNotNull(restore.status.device)
        assertNull(restore.status.deviceSkipped)
        assertTrue(host.logs.any { it.startsWith("恢复上次设备") })
    }

    @Test
    fun `记录是 ip 键时也能找回`() {
        val d = device()
        val host = FakeHost(listOf(d), key = "ip:192.168.99.164")
        val restore = SessionRestore(host)

        restore.restoreLastDevice()

        assertEquals("192.168.99.164", host.state.value.selectedIp)
        assertNotNull(restore.status.device)
    }

    @Test
    fun `根本没有记录时给出原因而不是静默不动`() {
        val host = FakeHost(listOf(device()), key = null)
        val restore = SessionRestore(host)

        restore.restoreLastDevice()

        assertNull(host.state.value.selectedIp)
        assertNull(restore.status.device)
        assertEquals("没有上次设备记录", restore.status.deviceSkipped)
    }

    @Test
    fun `记录对不上任何设备时说明原因`() {
        val host = FakeHost(listOf(device()), key = "FF31DEADBEEFDEADBEEF0000")
        val restore = SessionRestore(host)

        restore.restoreLastDevice()

        assertNull(host.state.value.selectedIp)
        assertNull(restore.status.device)
        assertTrue(restore.status.deviceSkipped!!.startsWith("上次设备不在已保存列表"))
    }

    @Test
    fun `成功恢复一次后失败恢复不会留着上次的成功状态`() {
        val d = device()
        val host = FakeHost(listOf(d), key = d.uuid)
        val restore = SessionRestore(host)
        restore.restoreLastDevice()
        assertNotNull(restore.status.device)

        // 换成一个对不上的键，模拟"记录被改坏"：状态必须反映这一次的结果
        val broken = SessionRestore(FakeHost(listOf(d), key = "ip:10.0.0.9"))
        broken.restoreLastDevice()
        assertNull(broken.status.device)
        assertNotNull(broken.status.deviceSkipped)
    }

    private fun device(
        ip: String = "192.168.99.164",
        name: String = "doss1",
    ) = SavedDevice.of(
        rawId = "FF310096A5760623673F81F1",
        ip = ip,
        name = name,
        location = "http://$ip:49152/description.xml",
    )

    /**
     * 只实现恢复逻辑用到的那几条（`state` / `update` / `log` / `lastDeviceKey`）。
     *
     * 其余方法一律抛异常而不是返回默认值：这个替身**只**服务设备恢复的测试，
     * 将来有人让恢复逻辑去发命令，会立刻在测试里炸掉，而不是悄悄多一条网络调用。
     */
    private class FakeHost(
        saved: List<SavedDevice>,
        private val key: String?,
    ) : ConsoleHost {

        private val flow = MutableStateFlow(ConsoleUiState(saved = saved))

        val logs = mutableListOf<String>()

        override val state: StateFlow<ConsoleUiState> = flow

        override fun update(transform: (ConsoleUiState) -> ConsoleUiState) {
            flow.update(transform)
        }

        override fun log(message: String) {
            logs += message
        }

        override fun lastDeviceKey(): String? = key

        override fun loadPlayback(uuid: String?, ip: String): PlaybackSession? = null

        override fun run(label: String, block: suspend () -> Unit) =
            throw UnsupportedOperationException("恢复不该用 run：$label")

        override fun launch(source: String, block: suspend CoroutineScope.() -> Unit) =
            throw UnsupportedOperationException("本用例不测队列恢复：$source")

        override fun http(): DeviceHttpClient = throw UnsupportedOperationException("不该发 HTTP")

        override fun send(ip: String, command: String): String = throw UnsupportedOperationException("不该发命令")

        override fun status(ip: String): LinkPlayStatus = throw UnsupportedOperationException("不该取状态")

        override suspend fun refreshAll() = throw UnsupportedOperationException("不该刷新")

        override fun refresh() = throw UnsupportedOperationException("不该刷新")

        override fun renderer(device: SavedDevice): UpnpRendererClient =
            throw UnsupportedOperationException("不该要渲染器")

        override fun pushDeviceVolume(ip: String, volume: Int) =
            throw UnsupportedOperationException("不该调音量")

        override suspend fun refreshCards() = throw UnsupportedOperationException("不该刷卡片")
    }
}
