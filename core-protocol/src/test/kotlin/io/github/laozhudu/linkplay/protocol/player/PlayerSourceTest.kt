package io.github.laozhudu.linkplay.protocol.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 输入源映射。
 *
 * 两个方向都要对：
 * - 正向：界面给设备发 `switchmode:<id>`，id 必须逐字正确（固件不认识的会被静默忽略）；
 * - 反向：设备回报的 `mode` 数字要能反查出"现在在哪一路上"，界面才能标出来。
 *
 * mode 数字含义来自官方表（见 PlayerStatus.modeLabel 的注释），不是猜的。
 */
class PlayerSourceTest {

    @Test
    fun 五个可切换源的命令名逐字正确() {
        assertEquals(
            listOf("wifi", "line-in", "bluetooth", "udisk", "optical"),
            PlayerSource.all.map { it.id },
        )
    }

    @Test
    fun 反向映射按官方_mode_表() {
        assertEquals("wifi", PlayerSource.currentIdOf(2))       // 第三方 DLNA
        assertEquals("wifi", PlayerSource.currentIdOf(1))       // AirPlay
        assertEquals("wifi", PlayerSource.currentIdOf(10))      // WiiMu 播放列表默认
        assertEquals("wifi", PlayerSource.currentIdOf(31))      // Spotify Connect
        assertEquals("udisk", PlayerSource.currentIdOf(11))     // U 盘
        assertEquals("udisk", PlayerSource.currentIdOf(16))     // TF 卡
        assertEquals("udisk", PlayerSource.currentIdOf(42))     // 外置存储
        assertEquals("line-in", PlayerSource.currentIdOf(40))   // AUX-In
        assertEquals("bluetooth", PlayerSource.currentIdOf(41))
        assertEquals("optical", PlayerSource.currentIdOf(43))   // 光纤
    }

    @Test
    fun 未知_mode_不瞎猜() {
        assertNull(PlayerSource.currentIdOf(null))
        assertNull(PlayerSource.currentIdOf(999))
    }

    @Test
    fun 描述里同时给出输入源与模式细节() {
        assertEquals("网络播放 · 本地播放列表", PlayerSource.describe(10))
        assertEquals("蓝牙", PlayerSource.describe(41))          // 输入源与模式同名时只留一个，不重复
    }

    @Test
    fun 除网络播放外都标记为有风险() {
        // 切走 Wi-Fi 之后手机可能控制不到设备，UI 必须提示
        assertTrue(PlayerSource.all.first { it.id == "wifi" }.risky.not())
        PlayerSource.all.filter { it.id != "wifi" }.forEach {
            assertTrue("${it.id} 应标记 risky", it.risky)
        }
    }

    @Test
    fun 按_id_查得到也能处理空值() {
        assertEquals("网络播放", PlayerSource.byId("wifi")?.label)
        assertNull(PlayerSource.byId(null))
        assertNull(PlayerSource.byId("nope"))
    }
}
