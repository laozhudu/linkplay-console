package io.github.laozhudu.linkplay.console.provisioning

import io.github.laozhudu.linkplay.protocol.ProvisioningResult
import java.net.ConnectException
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 配网诊断段的排版。
 *
 * 这一组锁的是 `docs/65` 批次 1 验收判据里最容易被忽略的一条：
 * **「命令回 OK」不等于「配好了」**。报告必须把"下发"和"等到 IP"分成两段，
 * 否则看报告的人（包括三个月后的我）会把 OK 读成成功。
 *
 * 说明：`Connected` / `JoinedButOffline` 两个分支需要构造 `LinkPlayStatus`
 * （十几个必填字段），这里没有覆盖到 —— 它们与 `Timeout` 走同一个 `when`，
 * 差异只在文案上，靠真机验证更划算。
 */
class ProvisionSourceStatusTest {

    @Before
    fun setUp() = ProvisionSourceStatus.reset()

    @After
    fun tearDown() = ProvisionSourceStatus.reset()

    @Test
    fun `before entering the page says so`() {
        assertTrue(ProvisionSourceStatus.render().contains("还没进过配网页"))
    }

    @Test
    fun `device and ap count are shown`() {
        ProvisionSourceStatus.located("192.168.99.153")
        ProvisionSourceStatus.scanned(8)

        val text = ProvisionSourceStatus.render()
        assertTrue(text.contains("设备=192.168.99.153"))
        assertTrue(text.contains("扫到 8 个 AP"))
    }

    @Test
    fun `accepted command explicitly says it does not mean connected`() {
        ProvisionSourceStatus.located("192.168.99.153")
        ProvisionSourceStatus.sendStarted("MyWiFi", 6)
        ProvisionSourceStatus.sendAccepted()

        val text = ProvisionSourceStatus.render()
        assertTrue(text.contains("SSID=MyWiFi"))
        assertTrue(text.contains("channel=6"))
        // 这一句是本段的全部价值：把 OK 和"连上了"切开
        assertTrue(text.contains("设备只回 OK，不等于连上了"))
    }

    @Test
    fun `timeout names both the OK and the missing IP`() {
        ProvisionSourceStatus.located("192.168.99.153")
        ProvisionSourceStatus.sendStarted("MyWiFi", 6)
        ProvisionSourceStatus.sendAccepted()
        ProvisionSourceStatus.finished(ProvisioningResult.Timeout(null), probes = 15, elapsedMs = 90_000)

        val text = ProvisionSourceStatus.render()
        assertTrue(text.contains("探测 15 次"))
        assertTrue(text.contains("90.0s"))
        assertTrue(text.contains("命令回了 OK"))
        assertTrue(text.contains("没等到 IP"))
    }

    @Test
    fun `unreachable keeps its classification`() {
        ProvisionSourceStatus.sendStarted("MyWiFi", 6)
        ProvisionSourceStatus.sendAccepted()
        ProvisionSourceStatus.finished(ProvisioningResult.Unreachable("Connection refused"), probes = 2, elapsedMs = 3_000)

        val text = ProvisionSourceStatus.render()
        assertTrue(text.contains("连不上设备"))
        assertTrue(text.contains("连不上"))
    }

    @Test
    fun `send failure is classified before polling starts`() {
        ProvisionSourceStatus.sendStarted("MyWiFi", 6)
        ProvisionSourceStatus.sendFailed(ConnectException("refused"))

        val text = ProvisionSourceStatus.render()
        assertTrue(text.contains("结果=失败"))
        assertTrue(text.contains("连不上"))
    }

    @Test
    fun `a new attempt clears the previous verdict`() {
        ProvisionSourceStatus.sendStarted("A", 1)
        ProvisionSourceStatus.sendAccepted()
        ProvisionSourceStatus.finished(ProvisioningResult.Timeout(null), 5, 90_000)
        assertTrue(ProvisionSourceStatus.snapshot().failure != null)

        ProvisionSourceStatus.sendStarted("B", 11)
        val s = ProvisionSourceStatus.snapshot()
        assertTrue("重新下发要清掉上一轮的结论", s.failure == null)
        assertTrue(s.result == null)
        assertTrue(s.probes == 0)
    }
}
