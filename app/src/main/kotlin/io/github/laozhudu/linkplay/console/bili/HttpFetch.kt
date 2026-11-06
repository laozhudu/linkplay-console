package io.github.laozhudu.linkplay.console.bili

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * 取一段 JSON。
 *
 * 为什么单独抽出来：`BiliClient` 的依赖刻意是一个 lambda（可纯 JVM 单测），
 * 这里就是那个 lambda 的真实实现。放一起能让"测试用的假实现"和"真实的"并排看。
 *
 * B 站这两个接口**不需要任何 Header**（`docs/85` §2.1 实测：
 * 不要 cookie、不要 wbi 签名、不要 Referer），所以这里刻意什么都不加 ——
 * 加了反而容易在以后被误认为"必须带"。
 */
suspend fun httpGetText(url: String): String = withContext(Dispatchers.IO) {
    var conn: HttpURLConnection? = null
    try {
        conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            instanceFollowRedirects = true
        }
        val code = conn.responseCode
        if (code !in 200..299) error("HTTP $code")
        conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
    } finally {
        conn?.disconnect()
    }
}
