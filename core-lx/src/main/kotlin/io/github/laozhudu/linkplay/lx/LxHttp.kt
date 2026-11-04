package io.github.laozhudu.linkplay.lx

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * 替洛雪脚本和 musicSdk 发 HTTP。
 *
 * ## 为什么要原生发而不是 JS 发
 *
 * QuickJS 里没有 fetch/XHR，也没有 Node 的 http。官方 preload 也是把 `request`
 * 桥到原生（`__lx_native_call__(key, 'request', ...)`）。我们照同一条路走。
 *
 * ## 参数形状
 *
 * 来自洛雪 `httpFetch(url, options)` 的 options：
 * `{ method, headers, form, body, format, timeout, binary }`
 * 其中 `form` 是对象（要转 `a=1&b=2`），`body` 可能是字符串也可能是对象。
 *
 * ## 响应形状
 *
 * 回给 JS 的 `body` **尽量是对象**：洛雪各平台的调用点都写
 * `httpFetch(...).promise.then(({ body }) => body.data...)`，body 必须是解析好的对象。
 * 解析不了才退回字符串。
 */
object LxHttp {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * 判断「这段文本本身是不是 JSON」必须用**严格模式**。
     *
     * 用上面那个 lenient 实例去 parse 裸文本 `hello` 会成功（宽松模式允许无引号的字符串），
     * 结果 body 变成裸值而不是字符串，JS 侧 `body.xxx` 就取不到东西。
     */
    private val strictJson = Json

    private const val DEFAULT_UA =
        "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/69.0.3497.100 Safari/537.36"

    data class Request(
        val id: String,
        val url: String,
        val method: String = "GET",
        val headers: Map<String, String> = emptyMap(),
        val formBody: String? = null,
        val body: String? = null,
        val timeoutMs: Int = 20_000,
    )

    data class Result(
        val statusCode: Int,
        val statusMessage: String,
        val headers: Map<String, String>,
        /** 已经是 JSON 文本：对象/数组原样，纯文本则是一个 JSON 字符串字面量。 */
        val bodyJson: String,
        val error: String? = null,
    ) {
        /** 转成回灌给 JS 的 payload。 */
        fun toJsPayload(id: String): String = buildJsonObject {
            put("id", id)
            if (error != null) {
                put("error", error)
            } else {
                put("statusCode", statusCode)
                put("statusMessage", statusMessage)
                put("headers", buildJsonObject { headers.forEach { (k, v) -> put(k, v) } })
                // bodyJson 已经是合法 JSON 文本，这里直接拼进去而不是当字符串
                put("body", json.parseToJsonElement(bodyJson))
            }
        }.toString()
    }

    /**
     * 从 JS 传来的 `{id, url, options}` 解析出请求。
     */
    fun parse(id: String, url: String, optionsJson: String?): Request {
        val opt = optionsJson?.let {
            runCatching { json.parseToJsonElement(it).jsonObject }.getOrNull()
        } ?: JsonObject(emptyMap())

        val method = opt["method"]?.jsonPrimitive?.contentOrNull?.uppercase() ?: "GET"
        val headers = (opt["headers"] as? JsonObject)?.entries
            ?.mapNotNull { (k, v) -> v.jsonPrimitive.contentOrNull?.let { k to it } }
            ?.toMap()
            .orEmpty()

        // form 优先（洛雪老接口都用 form），其次 body
        val formBody = (opt["form"] as? JsonObject)?.let { form ->
            form.entries.joinToString("&") { (k, v) ->
                "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v.jsonPrimitive.contentOrNull.orEmpty(), "UTF-8")}"
            }
        }
        val rawBody = when (val b = opt["body"]) {
            null -> null
            is JsonPrimitive -> b.contentOrNull
            else -> b.toString()
        }
        val timeout = opt["timeout"]?.jsonPrimitive?.intOrNull ?: 20_000

        return Request(
            id = id,
            url = url,
            method = method,
            headers = headers,
            formBody = formBody,
            body = rawBody,
            timeoutMs = timeout,
        )
    }

    /**
     * 真正发请求。**不抛异常** —— 网络问题一律塞进 [Result.error]，
     * 因为脚本侧拿到 error 会自己决定重试还是放弃（洛雪有多链路回退）。
     */
    fun perform(req: Request): Result {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(req.url).openConnection() as HttpURLConnection).apply {
                requestMethod = req.method
                connectTimeout = req.timeoutMs
                readTimeout = req.timeoutMs
                instanceFollowRedirects = true
                // 默认 UA：很多平台接口不带 UA 会直接 403
                setRequestProperty("User-Agent", DEFAULT_UA)
                req.headers.forEach { (k, v) -> setRequestProperty(k, v) }
                val payload = req.formBody ?: req.body
                if (payload != null && req.method != "GET" && req.method != "HEAD") {
                    doOutput = true
                    if (getRequestProperty("Content-Type") == null) {
                        setRequestProperty(
                            "Content-Type",
                            if (req.formBody != null) "application/x-www-form-urlencoded"
                            else "application/json",
                        )
                    }
                    outputStream.use { it.write(payload.toByteArray()) }
                }
            }
            val code = conn.responseCode
            val text = runCatching {
                (if (code in 200..299) conn.inputStream else conn.errorStream)
                    ?.bufferedReader()?.use { it.readText() }.orEmpty()
            }.getOrDefault("")

            Result(
                statusCode = code,
                statusMessage = conn.responseMessage.orEmpty(),
                headers = conn.headerFields.orEmpty()
                    .filterKeys { it != null }
                    .mapValues { (_, v) -> v.orEmpty().joinToString(", ") },
                bodyJson = toBodyJson(text),
            )
        } catch (e: IOException) {
            Result(0, "", emptyMap(), "\"\"", error = e.message ?: e.javaClass.simpleName)
        } catch (e: Exception) {
            Result(0, "", emptyMap(), "\"\"", error = e.message ?: e.javaClass.simpleName)
        } finally {
            runCatching { conn?.disconnect() }
        }
    }

    /** 能解析成 JSON 就用原文（保证是合法 JSON），否则包成 JSON 字符串字面量。 */
    internal fun toBodyJson(text: String): String {
        if (text.isBlank()) return "\"\""
        val trimmed = text.trimStart()
        // 只有明确像 JSON 的才按 JSON 解析，其余一律当字符串（自动转义引号换行）
        val looksLikeJson = trimmed.startsWith("{") || trimmed.startsWith("[") ||
            trimmed.startsWith("\"") || trimmed == "true" || trimmed == "false" || trimmed == "null" ||
            trimmed.firstOrNull()?.isDigit() == true
        if (looksLikeJson) {
            runCatching { strictJson.parseToJsonElement(text) }.getOrNull()?.let { return it.toString() }
        }
        return JsonPrimitive(text).toString()
    }
}
