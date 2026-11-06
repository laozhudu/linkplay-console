package io.github.laozhudu.linkplay.protocol.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [RecentStore] 新加的两个「带设备」方法必须有**能用的默认实现**。
 *
 * 为什么专门测这个：`recentWithDevice` / `recordWithDevice` 是接口上的
 * `default method`，目的是让纯内存实现和第三方实现不用改就能编译。
 * 如果哪天有人把默认实现删掉、改成抽象方法，编译期会报错 ——
 * 但如果默认实现写错（比如忘了回填 `playedAt`），编译不报错，
 * 界面上的「接着听」就会静默变空。那种错误只有这种测试抓得住。
 */
class RecentStoreDefaultTest {

    private class MemoryRecent : RecentStore {
        val rows = mutableListOf<Pair<TrackRecord, Long>>()
        override fun recent(limit: Int): List<TrackRecord> = rows.take(limit).map { it.first }
        override fun record(track: TrackRecord, playedAt: Long) {
            // 遵守契约：recent() 返回的记录里 firstPlayedAt 必须带时间
            // （`TrackRecord.firstPlayedAt` 存在的唯一理由就是给最近播放排序）
            rows.add(0, track.copy(firstPlayedAt = playedAt) to playedAt)
        }
        override fun clear() = rows.clear()
    }

    private fun track(title: String) = TrackRecord(playUri = "http://x/$title", title = title)

    @Test
    fun `默认的 recentWithDevice 降级成 recent 并回填播放时间`() {
        val store = MemoryRecent()
        store.record(track("晴天"), 1_000L)

        val got = store.recentWithDevice()

        assertEquals(1, got.size)
        assertEquals("晴天", got[0].track.title)
        // 关键：设备未知，但**时间不能丢** —— 丢了「接着听」就没法排序
        assertNull(got[0].deviceIp)
        assertEquals(1_000L, got[0].playedAt)
    }

    @Test
    fun `默认的 recordWithDevice 仍然会写进去（只是不记设备）`() {
        val store = MemoryRecent()
        store.recordWithDevice(track("稻香"), 2_000L, deviceIp = "192.168.99.164")

        val got = store.recentWithDevice()
        assertEquals(1, got.size)
        assertEquals("稻香", got[0].track.title)
        // 默认实现拿不到设备列，必须诚实返回 null，而不是编一个
        assertNull(got[0].deviceIp)
    }
}
