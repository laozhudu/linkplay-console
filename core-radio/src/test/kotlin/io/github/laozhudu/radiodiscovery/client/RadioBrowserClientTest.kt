package io.github.laozhudu.radiodiscovery.client

import io.github.laozhudu.radiodiscovery.FakeHttpClient
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RadioBrowserClientTest {

    private fun client(fake: FakeHttpClient) = RadioBrowserClient(httpClient = fake)

    // ── 缺陷 1：国家名大小写 ─────────────────────────────────────────────

    @Test
    fun `两个字母的国家代码走 countrycode 而不是 country`() = runTest {
        val fake = FakeHttpClient { FakeHttpClient.json("[]") }
        client(fake).searchByPlace("cn")

        val url = fake.calls.single().url
        assertTrue("应该用 countrycode：$url", url.contains("countrycode=CN"))
        assertTrue("不该出现 country=：$url", !url.contains("country="))
    }

    @Test
    fun `国家代码自动转大写`() = runTest {
        val fake = FakeHttpClient { FakeHttpClient.json("[]") }
        client(fake).searchByCountryCode("cn")

        assertTrue(fake.calls.single().url.contains("countrycode=CN"))
    }

    @Test
    fun `国家名小写会被规范成首字母大写`() = runTest {
        val fake = FakeHttpClient { FakeHttpClient.json("[]") }
        client(fake).searchByPlace("china")

        val url = fake.calls.single().url
        // 实测 country=china 返回 0 条、country=China 返回 100 条
        assertTrue("应该是 China 而不是 china：$url", url.contains("country=China"))
    }

    @Test
    fun `省份小写也会被规范化`() = runTest {
        val fake = FakeHttpClient { FakeHttpClient.json("[]") }
        client(fake).searchByPlace("china", "guangdong")

        val url = fake.calls.single().url
        assertTrue(url.contains("country=China"))
        assertTrue("state 也要规范化：$url", url.contains("state=Guangdong"))
    }

    @Test
    fun `多词国家名每个词首字母都大写`() = runTest {
        val fake = FakeHttpClient { FakeHttpClient.json("[]") }
        client(fake).searchByPlace("united states")

        assertTrue(fake.calls.single().url.contains("country=United+States"))
    }

    @Test
    fun `state 为空时不拼 state 参数`() = runTest {
        val fake = FakeHttpClient { FakeHttpClient.json("[]") }
        client(fake).searchByPlace("China", state = "  ")

        val url = fake.calls.single().url
        assertTrue("不该出现空的 state=：$url", !url.contains("state="))
    }

    @Test
    fun `titleCase 与 Radio Browser 真实国家名格式一致`() {
        // 2026-11-04 拉 /json/countries 核对过：227 个国家里每个词都大写，
        // 且 Côte d'Ivoire 在库里存成 "Coted Ivoire"（没有撇号）。
        assertEquals("China", RadioBrowserClient.titleCase("china"))
        assertEquals("Guangdong", RadioBrowserClient.titleCase("guangdong"))
        assertEquals("United States Of America", RadioBrowserClient.titleCase("united states of america"))
        assertEquals("Coted Ivoire", RadioBrowserClient.titleCase("coted ivoire"))
        assertEquals("Guinea Bissau", RadioBrowserClient.titleCase("guinea bissau"))
        assertEquals("Hong Kong", RadioBrowserClient.titleCase("hong kong"))
        // 已经是正确格式的不该被改动
        assertEquals("The Netherlands", RadioBrowserClient.titleCase("The Netherlands"))
    }

    // ── 搜索与解析 ──────────────────────────────────────────────────────

    @Test
    fun `按名字搜索会 URL 编码中文`() = runTest {
        val fake = FakeHttpClient { FakeHttpClient.json("[]") }
        client(fake).searchByName("中国之声")

        val url = fake.calls.single().url
        assertTrue("中文要编码：$url", url.contains("%E4%B8%AD%E5%9B%BD"))
        assertTrue(url.contains("limit=30"))
    }

    @Test
    fun `搜索结果被正确映射成模型`() = runTest {
        val fake = FakeHttpClient {
            FakeHttpClient.json(FakeHttpClient.stationsJson("http://a/1.mp3", "https://b/2.m3u8"))
        }
        val list = client(fake).searchByName("x", limit = 2)

        assertEquals(2, list.size)
        assertEquals("http://a/1.mp3", list[0].url)
        assertEquals("China", list[0].country)
        assertEquals(listOf("news", "talk"), list[0].tags)
        assertEquals(128, list[0].bitrate)
        assertEquals(false, list[0].isHls)
        assertEquals(true, list[1].isHls)
    }

    @Test
    fun `非 2xx 返回空列表而不是抛异常`() = runTest {
        val fake = FakeHttpClient { FakeHttpClient.json("oops", status = 503) }
        assertTrue(client(fake).searchByName("x").isEmpty())
    }

    @Test
    fun `带 User-Agent 请求头`() = runTest {
        val fake = FakeHttpClient { FakeHttpClient.json("[]") }
        client(fake).searchByName("x")

        assertTrue(fake.calls.single().headers.containsKey("User-Agent"))
    }

    @Test
    fun `搜索类请求要读 body`() = runTest {
        val fake = FakeHttpClient { FakeHttpClient.json("[]") }
        client(fake).searchByName("x")

        assertTrue("JSON 接口必须读 body", fake.calls.single().readBody)
    }
}
