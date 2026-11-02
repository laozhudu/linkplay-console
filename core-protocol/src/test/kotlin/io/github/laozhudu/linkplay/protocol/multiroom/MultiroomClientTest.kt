package io.github.laozhudu.linkplay.protocol.multiroom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MultiroomClientTest {

    // ---- 真机返回形状 ----

    @Test
    fun `真机的空从机列表`() {
        // 两台 DS-1831 实测都返回这个
        assertEquals(0, MultiroomClient.parseSlaveList("""{ "slaves": 0 }""").size)
        assertEquals(0, MultiroomClient.parseSlaveList("""{"slaves":"0"}""").size)
    }

    @Test
    fun `数组形式的从机列表`() {
        val raw = """{"slaves":[{"ip":"192.168.99.164","uuid":"FF310096A5760623673F81F1","name":"doss1","vol":"60"}]}"""
        val list = MultiroomClient.parseSlaveList(raw)
        assertEquals(1, list.size)
        assertEquals("192.168.99.164", list[0].ip)
        assertEquals("doss1", list[0].name)
        assertEquals(60, list[0].volume)
    }

    @Test
    fun `slave_list 形状（getStatusEx 用的键名）`() {
        val raw = """{"slave_list":[{"ip":"10.0.0.9","uuid":"u1","vol":30}]}"""
        val list = MultiroomClient.parseSlaveList(raw)
        assertEquals(1, list.size)
        assertEquals(30, list[0].volume)
    }

    @Test
    fun `坏数据返回空列表而不是抛异常`() {
        // 从机列表读不到不该把设置页打挂
        assertTrue(MultiroomClient.parseSlaveList("").isEmpty())
        assertTrue(MultiroomClient.parseSlaveList("OK").isEmpty())
        assertTrue(MultiroomClient.parseSlaveList("{broken").isEmpty())
        assertTrue(MultiroomClient.parseSlaveList("""{"other":1}""").isEmpty())
        // 数组里缺 ip 的条目跳过
        assertTrue(MultiroomClient.parseSlaveList("""{"slaves":[{"name":"x"}]}""").isEmpty())
    }

    @Test
    fun `mask 候选参数包含 ip 与 uuid 并去重`() {
        val slave = SlaveInfo(ip = "192.168.99.164", uuid = "FF31", name = "doss1")
        val candidates = listOf(slave.ip, slave.uuid).filterNotNull().distinct()
        assertEquals(listOf("192.168.99.164", "FF31"), candidates)
        // 没有 uuid 时只剩一个
        val noUuid = SlaveInfo(ip = "192.168.99.164")
        assertEquals(listOf("192.168.99.164"), listOfNotNull(noUuid.ip, noUuid.uuid).distinct())
    }
}
