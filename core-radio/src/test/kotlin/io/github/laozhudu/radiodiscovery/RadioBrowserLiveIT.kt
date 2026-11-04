package io.github.laozhudu.radiodiscovery

import io.github.laozhudu.radiodiscovery.client.JdkHttpClient
import io.github.laozhudu.radiodiscovery.client.RadioBrowserClient
import io.github.laozhudu.radiodiscovery.resolver.StreamResolver
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * 对**真实** Radio Browser API 的联调用例（默认跳过，CI 上没有外网保证）。
 *
 * ```
 * LIVE_RADIOBROWSER=1 ./gradlew :core-radio:test --tests "*RadioBrowserLiveIT*"
 * ```
 *
 * 只做只读请求，不写任何数据。
 *
 * 这几个用例就是 `docs/72` 里两个缺陷的**真机证据**：
 * 1. `searchByPlace("china")` 必须能返回结果（原实现返回 0 条）
 * 2. `resolve()` 对直播流必须快速返回（原实现阻塞到 readTimeout 再抛异常）
 */
class RadioBrowserLiveIT {

    private fun enabled() = System.getenv("LIVE_RADIOBROWSER").orEmpty().isNotBlank()

    private fun client() = RadioBrowserClient()

    @Test
    fun `按国家代码搜中国能出结果`() = runBlocking {
        assumeTrue(enabled())
        val list = client().searchByCountryCode("CN", limit = 20)
        println("countrycode=CN 返回 ${list.size} 条")
        assertTrue("应该能搜到中国的电台", list.isNotEmpty())
    }

    @Test
    fun `缺陷1回归 小写国家名也能出结果`() = runBlocking {
        assumeTrue(enabled())
        // 2026-11-04 实测：直接打 country=china 返回 0 条、country=China 返回 100 条。
        // 修好之后走 titleCase 规范化，小写输入必须也能拿到结果。
        val lower = client().searchByPlace("china", limit = 20)
        val proper = client().searchByPlace("China", limit = 20)
        println("china → ${lower.size} 条，China → ${proper.size} 条")
        assertTrue("小写国家名不该返回空", lower.isNotEmpty())
        assertTrue("大小写两种写法结果数应一致", lower.size == proper.size)
    }

    @Test
    fun `缺陷1回归 小写省份也能出结果`() = runBlocking {
        assumeTrue(enabled())
        val list = client().searchByPlace("china", "guangdong", limit = 20)
        println("china/guangdong → ${list.size} 条")
        assertTrue(list.isNotEmpty())
    }

    @Test
    fun `缺陷2回归 解析直播流不会卡住`() = runBlocking {
        assumeTrue(enabled())
        val list = client().searchByCountryCode("CN", limit = 20)
        val direct = list.firstOrNull { it.url.startsWith("http") }
        assumeTrue("没找到可用于解析的台", direct != null)

        val resolver = StreamResolver(JdkHttpClient(readTimeoutMs = 8000))
        val start = System.currentTimeMillis()
        val resolved = resolver.resolve(direct!!.url)
        val elapsed = System.currentTimeMillis() - start

        println("解析 ${direct.url.take(60)} 耗时 ${elapsed}ms → ${resolved.finalUrl.take(60)}")
        assertTrue("解析不该等到 readTimeout（8s），实际 ${elapsed}ms", elapsed < 5000)
    }

    @Test
    fun `按名字搜中文能出结果`() = runBlocking {
        assumeTrue(enabled())
        val list = client().searchByName("中国之声", limit = 10)
        println("name=中国之声 → ${list.size} 条")
        assertTrue(list.isNotEmpty())
    }
}
