package io.github.laozhudu.linkplay.protocol.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MyMusicSummaryTest {

    private fun build(
        local: Int = 0,
        nas: Int = 0,
        playlists: Int = 0,
        favorites: Int = 0,
        recents: Int = 0,
        radios: Int = 0,
        online: Boolean = false,
    ) = MyMusicSummary.build(local, nas, playlists, favorites, recents, radios, online)

    private fun List<MyMusicEntry>.byKey(key: String): MyMusicEntry =
        first { it.key == key }

    @Test
    fun `全 0 时六行都在且数量都是 0`() {
        val rows = build()
        assertEquals(6, rows.size)
        assertEquals("0 首", rows.byKey(MyMusicSummary.KEY_LOCAL).countText)
        assertEquals("0 个", rows.byKey(MyMusicSummary.KEY_NAS).countText)
        assertEquals("0 个", rows.byKey(MyMusicSummary.KEY_PLAYLISTS).countText)
        assertEquals("0 首", rows.byKey(MyMusicSummary.KEY_RECENT).countText)
        assertEquals("0 个", rows.byKey(MyMusicSummary.KEY_RADIO).countText)
    }

    @Test
    fun `顺序固定 与原厂一致`() {
        val keys = build().map { it.key }
        assertEquals(
            listOf("local", "nas", "playlists", "recent", "online", "radio"),
            keys,
        )
        // 标题顺序也钉住：UI 只按返回顺序渲染，改这块就是改页面
        assertEquals(
            listOf("手机本地", "家庭音乐共享", "全部歌单", "最近播放", "在线曲库", "网络电台"),
            build().map { it.title },
        )
    }

    @Test
    fun `部分有内容时只对应行变 且顺序不乱`() {
        val rows = build(local = 1, playlists = 2)
        assertEquals("1 首", rows.byKey(MyMusicSummary.KEY_LOCAL).countText)
        assertEquals("2 个", rows.byKey(MyMusicSummary.KEY_PLAYLISTS).countText)
        assertEquals("0 个", rows.byKey(MyMusicSummary.KEY_NAS).countText)
        assertEquals("0 首", rows.byKey(MyMusicSummary.KEY_RECENT).countText)
    }

    @Test
    fun `全部有内容时数量原样透出`() {
        val rows = build(local = 128, nas = 2, playlists = 5, favorites = 30, recents = 12, radios = 9)
        assertEquals("128 首", rows.byKey(MyMusicSummary.KEY_LOCAL).countText)
        assertEquals("2 个", rows.byKey(MyMusicSummary.KEY_NAS).countText)
        assertEquals("5 个", rows.byKey(MyMusicSummary.KEY_PLAYLISTS).countText)
        assertEquals("12 首", rows.byKey(MyMusicSummary.KEY_RECENT).countText)
        assertEquals("9 个", rows.byKey(MyMusicSummary.KEY_RADIO).countText)
    }

    @Test
    fun `单位文案 曲目用首 服务器歌单电台用个`() {
        val rows = build(local = 1, nas = 1, playlists = 1, recents = 1, radios = 1)
        // 中文没有单复数变化，但"首 / 个"不能串：串了看着就是没本地化
        assertEquals("1 首", rows.byKey(MyMusicSummary.KEY_LOCAL).countText)
        assertEquals("1 个", rows.byKey(MyMusicSummary.KEY_NAS).countText)
        assertEquals("1 个", rows.byKey(MyMusicSummary.KEY_PLAYLISTS).countText)
        assertEquals("1 首", rows.byKey(MyMusicSummary.KEY_RECENT).countText)
        assertEquals("1 个", rows.byKey(MyMusicSummary.KEY_RADIO).countText)
    }

    @Test
    fun `手机本地为 0 时提示授权 有歌时不提示`() {
        assertEquals(
            "点进去授权后扫描",
            build(local = 0).byKey(MyMusicSummary.KEY_LOCAL).subtitle,
        )
        assertNull(build(local = 1).byKey(MyMusicSummary.KEY_LOCAL).subtitle)
    }

    @Test
    fun `没有歌单时提示把收藏攒成歌单 有歌单时不提示`() {
        assertEquals(
            "在音乐库里把收藏攒成歌单",
            build(playlists = 0).byKey(MyMusicSummary.KEY_PLAYLISTS).subtitle,
        )
        assertNull(build(playlists = 1).byKey(MyMusicSummary.KEY_PLAYLISTS).subtitle)
    }

    @Test
    fun `在线曲库未配置显示未配置`() {
        assertEquals(
            "未配置",
            build(online = false).byKey(MyMusicSummary.KEY_ONLINE).countText,
        )
        // 未配置时不能因为它而影响别的行的副标题
        assertNull(build().byKey(MyMusicSummary.KEY_ONLINE).subtitle)
    }

    @Test
    fun `在线曲库已配置显示已连接`() {
        assertEquals(
            "已连接",
            build(online = true).byKey(MyMusicSummary.KEY_ONLINE).countText,
        )
    }

    @Test
    fun `负数被夹到 0 不出现负数量`() {
        val rows = build(local = -1, nas = -3, playlists = -1, recents = -2, radios = -9)
        assertEquals("0 首", rows.byKey(MyMusicSummary.KEY_LOCAL).countText)
        assertEquals("0 个", rows.byKey(MyMusicSummary.KEY_NAS).countText)
        assertEquals("0 个", rows.byKey(MyMusicSummary.KEY_PLAYLISTS).countText)
        assertEquals("0 首", rows.byKey(MyMusicSummary.KEY_RECENT).countText)
        assertEquals("0 个", rows.byKey(MyMusicSummary.KEY_RADIO).countText)
    }

    @Test
    fun `收藏数不改变任何一行文案`() {
        // 收藏目前不单独占一行；这条锁住"传了也不影响展示"，防止以后误把 fav 当成歌单数
        val without = build(playlists = 0, favorites = 0).map { it.countText }
        val with = build(playlists = 0, favorites = 50).map { it.countText }
        assertEquals(without, with)
    }
}
