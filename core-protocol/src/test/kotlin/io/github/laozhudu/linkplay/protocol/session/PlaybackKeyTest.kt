package io.github.laozhudu.linkplay.protocol.session

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 播放快照的存储键。
 *
 * 这条以前是三套写法（NAS 用 `uuid ?: ip`、服务用 `uuid.ifBlank { ip }`、音乐库用 **SSID**），
 * 存的和读的键不是同一个，所以"重开 App 恢复上次播放"一直读不到。这里钉死唯一规则。
 */
class PlaybackKeyTest {

    @Test
    fun 有_uuid_就用_uuid() {
        assertEquals("FF310096", PlaybackKey.of("FF310096", "192.168.99.164"))
    }

    @Test
    fun uuid_为空时退回_ip_前缀() {
        assertEquals("ip:192.168.99.164", PlaybackKey.of(null, "192.168.99.164"))
        assertEquals("ip:192.168.99.164", PlaybackKey.of("", "192.168.99.164"))
        assertEquals("ip:192.168.99.164", PlaybackKey.of("   ", "192.168.99.164"))
    }

    @Test
    fun 恢复时按_uuid_优先_ip_兜底的顺序尝试() {
        assertEquals(
            listOf("uuid-1", "ip:10.0.0.5"),
            PlaybackKey.candidates("uuid-1", "10.0.0.5"),
        )
    }

    @Test
    fun 没有_uuid_时只有一个候选() {
        assertEquals(listOf("ip:10.0.0.5"), PlaybackKey.candidates(null, "10.0.0.5"))
    }

    @Test
    fun 同一个设备换_IP_后候选键里_uuid_不变() {
        val before = PlaybackKey.candidates("uuid-1", "10.0.0.5")
        val after = PlaybackKey.candidates("uuid-1", "10.0.0.9")
        assertEquals(before.first(), after.first())
    }
}
