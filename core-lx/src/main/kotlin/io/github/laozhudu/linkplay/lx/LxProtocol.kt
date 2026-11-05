package io.github.laozhudu.linkplay.lx

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * 洛雪自定义源的事件名与动作名。
 *
 * 这些字符串是**协议常量**，和官方 preload 一一对应，不能改名：
 * preload 里 `EVENT_NAMES = { request, inited, updateAlert }`，
 * 平台动作目前只有 `musicUrl` / `lyric` / `pic`。
 */
object LxEvents {
    const val REQUEST = "request"
    const val INITED = "inited"
    const val UPDATE_ALERT = "updateAlert"

    // 原生 → JS 的内部动作（不是洛雪协议的一部分，是我们自己加的）
    const val SDK_RESPONSE = "sdkResponse"
    const val HTTP = "http"
    const val LOG = "log"
}

/** 脚本声明的平台能力（来自 `send(inited, {sources})`）。 */
data class LxSourceInfo(
    val id: String,
    val name: String,
    val actions: List<String> = emptyList(),
    val qualitys: List<String> = emptyList(),
) {
    /** 这个平台能不能出播放直链。 */
    val supportsMusicUrl: Boolean get() = actions.contains("musicUrl")
}

/** 脚本初始化结果。 */
data class LxInitResult(
    val ok: Boolean,
    val errorMessage: String? = null,
    val name: String = "",
    val version: String = "",
    val author: String = "",
    val description: String = "",
    val sources: Map<String, LxSourceInfo> = emptyMap(),
) {
    val sourceIds: List<String> get() = sources.keys.toList()
}

/**
 * 脚本对一次 `request` 的答复。
 *
 * 注意方向和形状：**这是 JS→原生的报文**，形状是
 * `{requestKey, status, result?}` 或 `{requestKey, status:false, errorMessage}`。
 * 别和原生→JS 那条 `{requestKey, error, response}` 搞混 ——
 * 两个方向用了同一个动作名 `response`，形状完全不同（这里踩过坑，单测抓出来的）。
 */
data class LxScriptResponse(
    val requestKey: String,
    val ok: Boolean = false,
    val error: String? = null,
    val result: JsonElement? = null,
) {
    /** 结果原样转成文本。**不要拿它当 URL 用** —— 官方回包是包了一层的，见 [resultUrl]。 */
    val resultText: String
        get() = when (val r = result) {
            null -> ""
            is JsonPrimitive -> r.contentOrNull.orEmpty()
            else -> r.toString()
        }

    /**
     * `musicUrl` 的播放地址。
     *
     * 为什么不能直接用 [resultText]：官方 preload 的 `handleRequest` 会把脚本返回的字符串
     * **再包一层**才发回原生侧（`user-api-preload.js`）：
     *
     * ```
     * result = { source, action, data: { type, url } }
     * ```
     *
     * 所以地址在 `result.data.url`。直接读 `result` 会拿到整个对象，
     * 表现为「脚本返回的不是有效地址：{"source":"tx","action":"musicUrl",…}」——
     * 那是报文，不是脚本的错。
     *
     * 同时兼容裸字符串：pic/lyric 这类动作、以及自己写的宿主可能是直接给字符串。
     */
    val resultUrl: String
        get() = when (val r = result) {
            null -> ""
            is JsonPrimitive -> r.contentOrNull.orEmpty()
            is JsonObject -> when (val d = r["data"]) {
                null -> ""
                is JsonPrimitive -> d.contentOrNull.orEmpty()
                is JsonObject -> (d["url"] as? JsonPrimitive)?.contentOrNull.orEmpty()
                else -> ""
            }
            else -> ""
        }
}

/** 解析 preload 发回来的各种报文。 */
object LxProtocol {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * 解析 `init` 动作的 payload。
     *
     * 官方结构：
     * ```
     * { status: Boolean, errorMessage?: String, info: {
     *     name, description, version, author, homepage, rawScript,
     *     sources: { kw|kg|tx|wy|mg|xm|local: { name, type, actions[], qualitys[] } }
     * } }
     * ```
     */
    fun parseInit(payload: String): LxInitResult {
        val root = runCatching { json.parseToJsonElement(payload).jsonObject }.getOrNull()
            ?: return LxInitResult(ok = false, errorMessage = "初始化返回的不是合法 JSON")
        val status = root["status"]?.jsonPrimitive?.booleanOrNull ?: false
        val err = root["errorMessage"]?.jsonPrimitive?.contentOrNull?.takeUnless { it.isBlank() }
        val info = root["info"] as? JsonObject

        val sources = mutableMapOf<String, LxSourceInfo>()
        val sourcesObj = info?.get("sources") as? JsonObject
        sourcesObj?.forEach { (id, value) ->
            val o = value as? JsonObject ?: return@forEach
            sources[id] = LxSourceInfo(
                id = id,
                name = o["name"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                actions = o.stringList("actions"),
                qualitys = o.stringList("qualitys"),
            )
        }

        return LxInitResult(
            ok = status,
            errorMessage = if (status) null else (err ?: "脚本初始化失败"),
            name = info?.get("name")?.jsonPrimitive?.contentOrNull.orEmpty(),
            version = info?.get("version")?.jsonPrimitive?.contentOrNull.orEmpty(),
            author = info?.get("author")?.jsonPrimitive?.contentOrNull.orEmpty(),
            description = info?.get("description")?.jsonPrimitive?.contentOrNull.orEmpty(),
            sources = sources,
        )
    }

    /**
     * 解析脚本对 `request` 的答复（JS→原生）。
     *
     * 官方 preload 发的是：
     * ```
     * { requestKey, status: true,  result: "https://..." }      // 成功
     * { requestKey, status: false, errorMessage: "..." }        // 失败
     * ```
     */
    fun parseScriptResult(payload: String): LxScriptResponse {
        val root = runCatching { json.parseToJsonElement(payload).jsonObject }.getOrNull()
            ?: return LxScriptResponse(requestKey = "", ok = false, error = "回包不是合法 JSON")
        val key = root["requestKey"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val ok = root["status"]?.jsonPrimitive?.booleanOrNull ?: false
        val err = root["errorMessage"]?.jsonPrimitive?.contentOrNull?.takeUnless { it.isBlank() }
        return LxScriptResponse(
            requestKey = key,
            ok = ok && err == null,
            error = if (ok) null else (err ?: "脚本没有返回结果"),
            result = root["result"],
        )
    }

    /** 解析 musicSdk 的回包：`{reqId, ok, data|error}`。 */
    fun parseSdkResponse(payload: String): LxSdkResponse {
        val root = runCatching { json.parseToJsonElement(payload).jsonObject }.getOrNull()
            ?: return LxSdkResponse(reqId = "", ok = false, error = "回包不是合法 JSON")
        return LxSdkResponse(
            reqId = root["reqId"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            ok = root["ok"]?.jsonPrimitive?.booleanOrNull ?: false,
            error = root["error"]?.jsonPrimitive?.contentOrNull?.takeUnless { it.isBlank() },
            data = root["data"],
        )
    }

    /** 构造发给脚本的 `request` 报文。 */
    fun requestPayload(
        requestKey: String,
        source: String,
        action: String,
        info: JsonElement,
    ): String = buildJsonObject {
        put("requestKey", requestKey)
        put("data", buildJsonObject {
            put("source", source)
            put("action", action)
            put("info", info)
        })
    }.toString()

    /**
     * `response.body` 可能是字符串、对象、数组（洛雪按需返回），统一转成字符串。
     *
     * 这一步不能省：脚本自己 `JSON.parse` 时依赖 body 是**字符串**，
     * 而有些脚本直接把对象塞进 body。
     */
    private fun bodyToText(e: JsonElement): String = when (e) {
        is JsonPrimitive -> e.contentOrNull ?: e.toString()
        else -> e.toString()
    }

    private fun JsonObject.stringList(key: String): List<String> =
        (this[key] as? kotlinx.serialization.json.JsonArray)
            ?.mapNotNull { it.jsonPrimitive.contentOrNull }
            ?: emptyList()
}

/** musicSdk 一次调用的回包。 */
data class LxSdkResponse(
    val reqId: String,
    val ok: Boolean,
    val error: String? = null,
    val data: JsonElement? = null,
)

/**
 * 把脚本/SDK 抛出来的原始错误翻成人话。
 *
 * 为什么需要：preload 内部把大量不合规返回值统一抛成 `failed`，
 * 直接显示给用户就是一句没信息量的 "failed"。
 */
object LxFailureText {

    fun translate(raw: String?): String {
        val msg = raw?.trim().orEmpty()
        if (msg.isEmpty()) return "脚本没有返回结果"
        val lower = msg.lowercase()
        return when {
            msg.contains("Api is not found") ->
                "这个脚本不支持取播放地址（洛雪 2.x 已关掉内置取流）"
            lower.contains("action not support") ->
                "脚本不支持这个操作"
            lower.contains("timeout") || lower.contains("timed out") ->
                "脚本响应超时"
            lower.contains("unknown cipher") ->
                "脚本已过期（平台的加密算法变了），需要换新脚本"
            lower.contains("is not a function") ->
                "脚本内部出错（可能被裁剪或版本不匹配）"
            msg == "failed" ->
                "脚本返回的数据不合规（可能是空链接或格式不对）"
            lower.contains("request failed") || lower.contains("network") ->
                "网络请求失败"
            else -> msg
        }
    }
}
