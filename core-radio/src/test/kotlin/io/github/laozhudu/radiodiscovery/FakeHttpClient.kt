package io.github.laozhudu.radiodiscovery

import io.github.laozhudu.radiodiscovery.client.HttpClient
import io.github.laozhudu.radiodiscovery.client.HttpResponse

/**
 * 假 HTTP 客户端：记下每次请求的 URL 与 readBody 开关，返回预置响应。
 *
 * 单测不打真网 —— 打真网会随机失败（Radio Browser 是公益服务），
 * 而且测不出"URL 拼错了"这种问题（拼错也可能返回 200 空列表）。
 */
internal class FakeHttpClient(
    private val respond: (url: String) -> HttpResponse,
) : HttpClient {

    data class Call(val url: String, val headers: Map<String, String>, val readBody: Boolean)

    val calls = mutableListOf<Call>()

    override suspend fun get(
        url: String,
        headers: Map<String, String>,
        readBody: Boolean,
    ): HttpResponse {
        calls += Call(url, headers, readBody)
        return respond(url)
    }

    companion object {
        fun json(body: String, status: Int = 200) = HttpResponse(
            statusCode = status,
            headers = mapOf("Content-Type" to listOf("application/json")),
            body = body,
        )

        fun headersOnly(
            status: Int,
            headers: Map<String, List<String>>,
        ) = HttpResponse(statusCode = status, headers = headers, body = "")

        /** 两条真实结构的电台 JSON（字段按 Radio Browser 实际返回裁剪）。 */
        fun stationsJson(vararg urls: String): String =
            urls.mapIndexed { i, u ->
                """{"name":"台$i","url":"$u","stationuuid":"uuid-$i","country":"China",""" +
                    """"state":"Guangdong","language":"chinese","tags":"news, talk",""" +
                    """"favicon":"https://x/$i.png","bitrate":128,"codec":"MP3","hls":${u.endsWith(".m3u8")},""" +
                    """"lastcheckok":1,"votes":10,"clickcount":5}"""
            }.joinToString(prefix = "[", postfix = "]")
    }
}
