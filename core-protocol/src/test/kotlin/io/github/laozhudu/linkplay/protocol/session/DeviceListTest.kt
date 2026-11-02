package io.github.laozhudu.linkplay.protocol.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceListTest {

    private fun dev(uuid: String, ip: String, seen: Long, name: String = "doss") =
        SavedDevice(uuid = uuid, name = name, ip = ip, location = "http://$ip:49152/description.xml", lastSeenMs = seen)

    @Test
    fun `新增设备按最近使用排序`() {
        var list = emptyList<SavedDevice>()
        list = DeviceList.upsert(list, dev("A", "10.0.0.1", 100))
        list = DeviceList.upsert(list, dev("B", "10.0.0.2", 200))
        assertEquals(listOf("B", "A"), list.map { it.uuid })
    }

    @Test
    fun `同一 uuid 再次使用会覆盖而不是重复`() {
        var list = DeviceList.upsert(emptyList(), dev("A", "10.0.0.1", 100))
        list = DeviceList.upsert(list, dev("A", "10.0.0.9", 300, name = "客厅"))
        assertEquals(1, list.size)
        assertEquals("10.0.0.9", list[0].ip, )
        assertEquals("客厅", list[0].name)
    }

    @Test
    fun `DHCP 换 IP 后仍认作同一台`() {
        // 真机发生过 .164 → .153；用 uuid 做键才不会多出一条
        var list = DeviceList.upsert(emptyList(), dev("FF31F0174FE5C9B2A2ACEAE3", "192.168.99.164", 100))
        list = DeviceList.upsert(list, dev("FF31F0174FE5C9B2A2ACEAE3", "192.168.99.153", 200))
        assertEquals(1, list.size)
        assertEquals("192.168.99.153", list[0].ip)
    }

    @Test
    fun `删除后不再出现`() {
        val list = DeviceList.remove(
            listOf(dev("A", "10.0.0.1", 100), dev("B", "10.0.0.2", 200)),
            "A",
        )
        assertEquals(listOf("B"), list.map { it.uuid })
    }

    @Test
    fun `最近使用的那台可以取到`() {
        val list = listOf(dev("A", "10.0.0.1", 100), dev("B", "10.0.0.2", 500))
        assertEquals("B", DeviceList.mostRecent(list)?.uuid)
        assertNull(DeviceList.mostRecent(emptyList()))
    }

    @Test
    fun `完整清单能往返编码`() {
        val list = listOf(dev("A", "10.0.0.1", 100, "客厅 DOSS"), dev("B", "10.0.0.2", 200))
        val back = DeviceList.decode(DeviceList.encode(list))
        assertEquals(2, back.size)
        assertEquals("客厅 DOSS", back.first { it.uuid == "A" }.name)
    }

    @Test
    fun `坏的存储内容退化为空清单而不是崩溃`() {
        assertTrue(DeviceList.decode(null).isEmpty())
        assertTrue(DeviceList.decode("not json").isEmpty())
        assertTrue(DeviceList.decode("""["broken",""").isEmpty())
    }

    @Test
    fun `名字为空时标题退回 IP`() {
        val d = SavedDevice(uuid = "A", name = "  ", ip = "10.0.0.1")
        assertEquals("10.0.0.1", d.title)
    }

    @Test
    fun `清单数量有上限，防止长期使用后无限增长`() {
        var list = emptyList<SavedDevice>()
        repeat(30) { i -> list = DeviceList.upsert(list, dev("U$i", "10.0.0.$i", i.toLong())) }
        assertTrue("实际 ${list.size}", list.size <= 16)
        // 保留的应该是最近的那些
        assertEquals("U29", list.first().uuid)
    }
}
