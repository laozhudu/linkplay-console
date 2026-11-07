package io.github.laozhudu.linkplay.console.playback.endpoint

import io.github.laozhudu.linkplay.console.playback.DeviceProfileCache
import io.github.laozhudu.linkplay.protocol.dlna.DeviceCapabilities
import io.github.laozhudu.linkplay.protocol.dlna.DeviceGroup
import io.github.laozhudu.linkplay.protocol.dlna.DeviceProfile
import io.github.laozhudu.linkplay.protocol.dlna.capabilitiesOfProfile
import io.github.laozhudu.linkplay.protocol.session.SavedDevice
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 设备档位怎么定 —— **防的是我犯过三次的那个错**。
 *
 * ## 我错在哪（三个版本，一个根因）
 *
 * 用户 2026-11-07 的三句话，对应我三次翻车：
 *
 * 1. 「通用 upnp 是一个独立模块，doss 体系是另一个模块」→ 我做了个 `transportFor` 查表
 * 2. 「通用 upnp 不一定是 doss，doss 有特殊标识的」→ 我把默认值从 DOSS 改成通用
 * 3. **「不对，现在识别完全错误了」** → 三台音箱全变成"通用播放设备"
 *
 * 根因不是"默认值该选哪边"，是**位置错了**：
 * 设备类型是**设备的属性**，却被我放在**内存缓存的默认值**里。
 * 缓存重启就归零，归零之后只能靠猜 —— 猜哪边都会错一边。
 *
 * ## 现在的设计
 *
 * - 唯一真相：`SavedDevice.profile`（**持久化在设备档案里**）
 * - `DeviceProfileCache`：档案的运行时镜像，`prime()` 时预热
 * - 未命中 = `UNKNOWN`（诚实地说"不知道"），不是猜某一档
 */
class ChannelWhitelistTest {

    @Before
    fun setUp() = DeviceProfileCache.clear()

    @After
    fun tearDown() = DeviceProfileCache.clear()

    // ---- 一、档位本身怎么算 ----

    @Test
    fun `只有同时有私有队列和组网的才算 DOSS`() {
        assertEquals(DeviceGroup.MySpeaker, DeviceCapabilities.LINKPLAY.group)

        assertNotEquals(
            "通用渲染器不是 DOSS",
            DeviceGroup.MySpeaker,
            DeviceCapabilities.genericDmr(hasRenderingControl = true).group,
        )
        assertNotEquals(
            "什么都没探到的设备更不是 DOSS",
            DeviceGroup.MySpeaker,
            DeviceCapabilities.NONE.group,
        )
    }

    @Test
    fun `只声明了队列但不会组网的设备不算 DOSS`() {
        val half = DeviceCapabilities.genericDmr(hasRenderingControl = true)
            .copy(supportsPlayQueue = true, supportsMultiroom = false)

        assertNotEquals("缺了组网这一半，就不是 MySpeaker 档", DeviceGroup.MySpeaker, half.group)
    }

    // ---- 二、缓存没命中时说什么 ----

    @Test
    fun `没预热也没探测过的设备是 UNKNOWN，不是任何一档`() {
        // 这条挡的就是我前两次的做法：拿一个"偏向某档"的默认值顶上去。
        // UNKNOWN 的意思是"我不知道"，界面上就只显示离线/未知，不画任何按钮。
        assertEquals(DeviceProfile.UNKNOWN, DeviceProfileCache.of("10.0.0.99"))
        assertEquals(DeviceCapabilities.NONE, DeviceProfileCache.capabilitiesOf("10.0.0.99"))
        assertFalse(DeviceProfileCache.supportsDeviceQueue("10.0.0.99"))
    }

    // ---- 三、档案是唯一真相 ----

    @Test
    fun `预热之后三台音箱就是音箱，不需要任何默认值`() {
        // 这正是真机场景：冷启动 → reloadSaved → prime → 分组立刻正确。
        // 之前没有 prime，冷启动瞬间全是"未识别"，界面只能猜。
        val devices = listOf(
            saved("A1", "192.168.99.153", DeviceProfile.LINKPLAY),
            saved("A2", "192.168.99.164", DeviceProfile.LINKPLAY),
            saved("A3", "192.168.99.181", DeviceProfile.LINKPLAY),
        )
        DeviceProfileCache.prime(devices)

        listOf("192.168.99.153", "192.168.99.164", "192.168.99.181").forEach { ip ->
            assertTrue("$ip 应该被认出是音箱", DeviceProfileCache.isConfirmedDoss(ip))
        }
    }

    @Test
    fun `预热不会覆盖本轮已经探测到的精确结果`() {
        // 档案是"上次的结论"，探测是"刚才的事实" —— 后者更可信。
        DeviceProfileCache.put(
            "10.0.0.20",
            DeviceProfile.GENERIC_DMR,
            DeviceCapabilities.genericDmr(hasRenderingControl = false),
        )
        DeviceProfileCache.prime(listOf(saved("B1", "10.0.0.20", DeviceProfile.LINKPLAY)))

        assertFalse(
            "档案里的旧结论不该盖掉刚探到的事实",
            DeviceProfileCache.isConfirmedDoss("10.0.0.20"),
        )
    }

    // ---- 四、档位要能存下来 ----

    @Test
    fun `档位写进档案后能原样读回来`() {
        val device = saved("C1", "192.168.99.175", DeviceProfile.GENERIC_DMR)
        val back = SavedDevice.decode(device.encode())

        assertEquals(
            "encode/decode 往返必须保住档位，否则重启就回到未识别",
            DeviceProfile.GENERIC_DMR,
            back?.profile,
        )
    }

    @Test
    fun `老档案没有档位字段时按音箱补（迁移规则，不是默认值）`() {
        // 这个字段出现之前，App 根本没有"添加通用设备"这条路：
        // 扫描只认 LinkPlay，手动填 IP 只试 :49152（LinkPlay 固定路径）。
        // 所以老数据里的每一台**必然**是音箱 —— 这是版本事实，不是猜。
        val legacy = """{"uuid":"D1","name":"doss1","ip":"192.168.99.164","seen":1}"""
        val back = SavedDevice.decode(legacy)

        assertEquals(
            "老数据一定是音箱（那时还没有添加通用设备的功能）",
            DeviceProfile.LINKPLAY,
            back?.profile,
        )
    }

    @Test
    fun `完全没有档位的设备不会冒充音箱`() {
        // 新加的设备一定有档位（扫描时就判了）；真拿到 UNKNOWN 说明还没识别，
        // 这时给 NONE —— 不画按钮，而不是画一堆按不动的。
        assertEquals(DeviceCapabilities.NONE, capabilitiesOfProfile(DeviceProfile.UNKNOWN))
        assertNull(
            "UNKNOWN 派不出任何有用能力",
            capabilitiesOfProfile(DeviceProfile.UNKNOWN).takeIf { it.supportsPlayQueue },
        )
    }

    private fun saved(uuid: String, ip: String, profile: DeviceProfile) =
        SavedDevice.of(rawId = uuid, ip = ip, name = uuid, profile = profile)
}
