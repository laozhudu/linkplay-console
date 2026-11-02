package io.github.laozhudu.linkplay.console.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 全站音量总线。
 *
 * 用户反馈「主页音量应该和播放器的音量调节及时同步，目前不够及时」——
 * 根因是两处各存一份状态。这里锁住"同一份状态、双向可见"这条契约。
 */
class VolumeBusTest {

    @Test
    fun 一处写入另一处立刻可读() {
        VolumeBus.publish("192.168.99.164", 42, false)
        assertEquals(42, VolumeBus.volumeOf("192.168.99.164")?.volume)
        assertEquals(false, VolumeBus.volumeOf("192.168.99.164")?.mute)
    }

    @Test
    fun 只改音量时静音状态保持不变() {
        VolumeBus.publish("192.168.99.153", 30, true)
        VolumeBus.publishVolume("192.168.99.153", 55)
        val v = VolumeBus.volumeOf("192.168.99.153")!!
        assertEquals(55, v.volume)
        assertEquals(true, v.mute)     // 拖动音量不应该顺手解除静音
    }

    @Test
    fun 越界音量被夹到_0_100() {
        VolumeBus.publishVolume("ip-a", 999)
        assertEquals(100, VolumeBus.volumeOf("ip-a")?.volume)
        VolumeBus.publishVolume("ip-a", -5)
        assertEquals(0, VolumeBus.volumeOf("ip-a")?.volume)
    }

    @Test
    fun 空地址不写入() {
        VolumeBus.publish("", 50, false)
        assertNull(VolumeBus.volumeOf(""))
    }

    @Test
    fun 不同设备互不影响() {
        VolumeBus.publish("ip-x", 10, false)
        VolumeBus.publish("ip-y", 90, false)
        assertEquals(10, VolumeBus.volumeOf("ip-x")?.volume)
        assertEquals(90, VolumeBus.volumeOf("ip-y")?.volume)
    }
}
