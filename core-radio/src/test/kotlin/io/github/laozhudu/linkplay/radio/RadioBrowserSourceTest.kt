package io.github.laozhudu.linkplay.radio

import io.github.laozhudu.radiodiscovery.FakeHttpClient
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RadioBrowserSourceTest {

    @Test
    fun `按国家代码走 countrycode`() = runTest {
        val fake = FakeHttpClient { FakeHttpClient.json(FakeHttpClient.stationsJson("http://a/1.mp3")) }
        val source = RadioBrowserSource(httpClient = fake)

        val list = source.searchByCountryCode("cn")

        assertEquals(1, list.size)
        val url = fake.calls.single().url
        assertTrue("应该用 countrycode：$url", url.contains("countrycode=CN"))
    }

    @Test
    fun `可以换成备用镜像域名`() = runTest {
        val fake = FakeHttpClient { FakeHttpClient.json("[]") }
        val source = RadioBrowserSource(
            baseUrl = "https://all.api.radio-browser.info",
            httpClient = fake,
        )

        source.searchByCountryCode("CN")

        assertTrue(fake.calls.single().url.startsWith("https://all.api.radio-browser.info"))
    }
}
