package io.github.laozhudu.linkplay.console.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「源里的一条 → 可投送的一条」的规则。
 *
 * 这些用例锁的是**字段落点**和**失败项处理**，不是实现细节 ——
 * 因为手写 8 遍的真实后果就是"某条路径少个字段"和"一条失败拖垮整批"。
 */
class ToQueueItemsUseCaseTest {

    @Test
    fun `of keeps every field where it belongs`() {
        val item = ToQueueItemsUseCase.of(
            url = "http://nas/1.flac",
            title = "晴天",
            artist = "周杰伦",
            album = "叶惠美",
            durationSec = 269,
            albumArtUrl = "http://nas/cover.jpg",
        )
        assertEquals("http://nas/1.flac", item.url)
        assertEquals("晴天", item.title)
        assertEquals("周杰伦", item.artist)
        assertEquals("叶惠美", item.album)
        assertEquals(269L, item.durationSec)
        assertEquals("http://nas/cover.jpg", item.albumArtUrl)
        // 默认 mimeType 是设备接受度最高的那个，不该被来源改掉
        assertEquals("audio/mpeg", item.mimeType)
    }

    @Test
    fun `resolveAll drops the ones that cannot be resolved`() {
        val tracks = listOf("a", "b", "c")
        val items = kotlinx.coroutines.runBlocking {
            ToQueueItemsUseCase.resolveAll(
                tracks = tracks,
                // b 解析不出来（文件没了 / 服务器不认）
                resolve = { if (it == "b") null else "http://x/$it" },
                toItem = { raw, url -> ToQueueItemsUseCase.of(url = url, title = raw) },
            )
        }
        assertEquals(2, items.size)
        assertEquals(listOf("a", "c"), items.map { it.title })
        // **关键**：失败项直接丢弃，不占位。占位会让设备对空 url 回 400，
        // 表现是"随机有几首放不了"，比少一首难查得多。
        assertTrue(items.none { it.url.isBlank() })
    }

    @Test
    fun `resolveAll survives a resolver that throws`() {
        // 抽出来的真实动机：五处手写循环里，各源都用自己的 runCatching，
        // 有一处直接 !! 会让一条异常拖垮整批
        val items = kotlinx.coroutines.runBlocking {
            ToQueueItemsUseCase.resolveAll(
                tracks = listOf("ok", "boom"),
                resolve = { if (it == "boom") error("服务器 500") else "http://x/$it" },
                toItem = { raw, url -> ToQueueItemsUseCase.of(url = url, title = raw) },
            )
        }
        assertEquals(1, items.size)
        assertEquals("ok", items.first().title)
    }

    @Test
    fun `empty input yields empty output`() {
        val items = kotlinx.coroutines.runBlocking {
            ToQueueItemsUseCase.resolveAll<String>(
                tracks = emptyList(),
                resolve = { null },
                toItem = { _, url -> ToQueueItemsUseCase.of(url = url, title = "") },
            )
        }
        assertTrue(items.isEmpty())
    }

    @Test
    fun `nullable fields stay null instead of empty string`() {
        val item = ToQueueItemsUseCase.of(url = "http://x/1.mp3", title = "无元数据")
        // 空串会让界面显示一个空白行，null 才会走"不显示"
        assertNull(item.artist)
        assertNull(item.album)
        assertNull(item.albumArtUrl)
    }
}
