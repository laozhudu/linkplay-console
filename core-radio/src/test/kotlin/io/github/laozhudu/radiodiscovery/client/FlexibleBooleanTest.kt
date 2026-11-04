package io.github.laozhudu.radiodiscovery.client

import io.github.laozhudu.radiodiscovery.FakeHttpClient
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 缺陷 3 的回归：Radio Browser 的 `hls` 字段返回**数字**（`1` / `0`），
 * 而模型声明的是 `Boolean?` —— 直接反序列化会抛异常，**整个搜索列表全挂**。
 *
 * 这里的 JSON 片段是从真实 API 响应里抄的（2026-11-04）。
 */
class FlexibleBooleanTest {

    private fun one(body: String): io.github.laozhudu.radiodiscovery.model.RadioStation? {
        val fake = FakeHttpClient { FakeHttpClient.json("[$body]") }
        val client = RadioBrowserClient(httpClient = fake)
        return kotlinx.coroutines.runBlocking { client.searchByName("x") }.firstOrNull()
    }

    private fun raw(hlsField: String) =
        """{"name":"台","url":"http://a/1.mp3","stationuuid":"u","country":"China",""" +
            """"state":"","language":"","tags":"","favicon":"","bitrate":800,"codec":"MP3",""" +
            hlsField + ""","lastcheckok":1,"votes":1,"clickcount":1}"""

    @Test
    fun `hls 为数字 1 时解析成 true 且不抛异常`() {
        // 真实响应长这样：{"hls":1,"bitrate":800,...}
        val s = one(raw("\"hls\":1"))
        assertEquals(true, s?.isHls)
    }

    @Test
    fun `hls 为数字 0 时解析成 false`() {
        assertEquals(false, one(raw("\"hls\":0"))?.isHls)
    }

    @Test
    fun `hls 为布尔时照常解析`() {
        assertEquals(true, one(raw("\"hls\":true"))?.isHls)
        assertEquals(false, one(raw("\"hls\":false"))?.isHls)
    }

    @Test
    fun `hls 为字符串数字时也能解析`() {
        assertEquals(true, one(raw("\"hls\":\"1\""))?.isHls)
        assertEquals(false, one(raw("\"hls\":\"0\""))?.isHls)
    }

    @Test
    fun `hls 字段缺失时退化成 false 而不是报错`() {
        // 模型里 isHls 是非空 Boolean，缺字段时按"不是 HLS"处理，不能抛异常
        assertEquals(false, one(raw("\"other\":1"))?.isHls)
    }

    @Test
    fun `列表里混着 hls 数字也不会让整批解析失败`() = runTest {
        // 这是关键：一个台解析失败不能把整个列表带崩
        val json = "[" +
            """{"name":"A","url":"http://a/1.mp3","stationuuid":"a","country":"China","state":"","language":"","tags":"","favicon":"","bitrate":128,"codec":"MP3","hls":1,"lastcheckok":1,"votes":1,"clickcount":1},""" +
            """{"name":"B","url":"http://a/2.mp3","stationuuid":"b","country":"China","state":"","language":"","tags":"","favicon":"","bitrate":128,"codec":"MP3","hls":0,"lastcheckok":1,"votes":1,"clickcount":1}""" +
            "]"
        val fake = FakeHttpClient { FakeHttpClient.json(json) }
        val list = RadioBrowserClient(httpClient = fake).searchByName("x")

        assertEquals(2, list.size)
        assertTrue(list[0].isHls == true)
        assertTrue(list[1].isHls == false)
    }
}
