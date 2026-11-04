package io.github.laozhudu.linkplay.protocol.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 设备身份单一入口 —— 锁死"同一台音箱只能有一条记录"。
 *
 * 反例全部来自真机：`.153` 的 `getStatusEx.uuid` 是 24 位无连字符，
 * 而它的 UDN 是 `uuid:FF31F017-4FE5-C9B2-A2AC-EAE3FF31F017`。
 * 老版本三处各归一化一遍，存出来两条记录，首页出现两台同名设备。
 */
class SavedDeviceIdentityTest {

    private val plain = "FF31F0174FE5C9B2A2ACEAE3"
    private val udn = "uuid:FF31F017-4FE5-C9B2-A2AC-EAE3FF31F017"

    @Test
    fun `三种来源归一出同一个 uuid`() {
        // 手动填 IP 拿 UDN、扫描拿带前缀的、组内从机拿带前缀的 —— 必须全等
        val a = SavedDevice.of(rawId = udn, name = "doss3", ip = "192.168.99.153")
        val b = SavedDevice.of(rawId = plain, name = "doss3", ip = "192.168.99.153")
        val c = SavedDevice.of(rawId = "UUID:${udn.removePrefix("uuid:")}", name = "doss3", ip = "192.168.99.153")

        assertEquals(a.uuid, b.uuid)
        assertEquals(b.uuid, c.uuid)
        // 归一化后是纯小写十六进制，不含冒号和连字符
        assertTrue(a.uuid.all { it.isLetterOrDigit() })
        assertEquals(a.uuid, a.uuid.lowercase())
    }

    @Test
    fun `没有 uuid 时退回 IP`() {
        val d = SavedDevice.of(rawId = null, name = "电视", ip = "192.168.99.175")
        assertEquals("192.168.99.175", d.uuid)
        // 空串也算"没有"
        assertEquals("192.168.99.175", SavedDevice.of(rawId = "  ", name = "x", ip = "192.168.99.175").uuid)
    }

    @Test
    fun `名字只去首尾空白，不动中间`() {
        val d = SavedDevice.of(rawId = plain, name = "  客厅 DOSS  ", ip = "1.2.3.4")
        assertEquals("客厅 DOSS", d.name)
    }

    @Test
    fun `老版本脏数据在 decode 时被归一化`() {
        // 模拟升级前存下的两条：一条 32 位、一条 24 位
        val legacy = """
            ["{\"uuid\":\"FF31F017-4FE5-C9B2-A2AC-EAE3FF31F017\",\"name\":\"doss3\",\"ip\":\"192.168.99.153\",\"seen\":100}",
             "{\"uuid\":\"FF31F0174FE5C9B2A2ACEAE3\",\"name\":\"\",\"ip\":\"192.168.99.164\",\"seen\":200}"]
        """.trimIndent()

        val list = DeviceList.decode(legacy)

        // 合并成一条 —— 用户升级后不该看到两台一样的设备
        assertEquals(1, list.size)
        // 保留最近见过的那条（IP 更新），但名字取非空的（名字是用户设的，比 IP 更不该丢）
        assertEquals("192.168.99.164", list[0].ip)
        assertEquals("doss3", list[0].name)
    }

    @Test
    fun `upsert 用宽容判等去重，不是字符串相等`() {
        var list = DeviceList.upsert(emptyList(), SavedDevice.of(udn, "192.168.99.153", "doss3"))
        list = DeviceList.upsert(list, SavedDevice.of(plain, "192.168.99.153", "doss3"))

        assertEquals(1, list.size)
    }

    @Test
    fun `remove 也能用另一种格式删掉同一条`() {
        val list = listOf(SavedDevice.of(plain, "192.168.99.153", "doss3"))
        assertTrue(DeviceList.remove(list, udn).isEmpty())
    }

    @Test
    fun `不同设备不会被误合并`() {
        val a = SavedDevice.of(plain, "192.168.99.153", "doss3")
        val b = SavedDevice.of("FF310096A5760623673F81F1", "192.168.99.164", "doss1")
        assertEquals(2, DeviceList.upsert(listOf(a), b).size)
    }

    @Test
    fun `往返编码后 uuid 仍是归一化形态`() {
        val d = SavedDevice.of(udn, "192.168.99.153", "doss3", location = "http://192.168.99.153:49152/description.xml")
        val back = DeviceList.decode(DeviceList.encode(listOf(d)))
        assertEquals(1, back.size)
        assertEquals(d.uuid, back[0].uuid)
        assertEquals(d.location, back[0].location)
    }
}
