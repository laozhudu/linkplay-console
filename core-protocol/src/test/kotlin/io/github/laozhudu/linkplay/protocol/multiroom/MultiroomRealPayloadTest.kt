package io.github.laozhudu.linkplay.protocol.multiroom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 用真机成组后的**原始**回报做回归。
 *
 * 这份 JSON 是 2026-11-02 从 `.164` 抓的，未经任何改写 —— 用真实样本锁住字段名，
 * 防止以后有人按「常见文档」把 `volume` 改成 `vol`（我第一次就写成了 `vol`，音量在 UI 上恒为 null）。
 */
class MultiroomRealPayloadTest {

    private val real = """{ "slaves": 1, "slave_list": [ { "name": "doss3",
        "ssid": "DOSS DS-1831_ADB4", "mask": 0, "volume": 60, "mute": 0, "channel": 0,
        "battery": 70, "ip": "10.10.10.92", "version": "3.6.5310",
        "uuid": "uuid:FF31F017-4FE5-C9B2-A2AC-EAE3FF31F017" } ] }"""

    @Test
    fun 解析真机成组回报_音量与地址都正确() {
        val list = MultiroomClient.parseSlaveList(real)
        assertEquals(1, list.size)
        val s = list[0]
        assertEquals("10.10.10.92", s.ip)          // 从机在主机热点网段
        assertEquals("doss3", s.name)
        assertEquals(60, s.volume)                  // 字段名是 volume
        assertTrue(s.uuid!!.startsWith("uuid:"))    // 带前缀，比对前要归一化
        assertTrue(s.inGroup)                       // 在 slave_list 里就是组内（mask=0 不代表没入组）
        assertEquals(0, s.channel)                  // 声道字段真机有返回，默认 0=立体声
    }

    @Test
    fun 空组时不把_slaves_1_误判为空() {
        assertTrue(MultiroomClient.parseSlaveList("""{ "slaves": 1 }""").isEmpty())
    }
}
