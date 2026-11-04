package io.github.laozhudu.linkplay.lx

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.net.URLDecoder
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 跑一个洛雪「自定义源」脚本。
 *
 * ## 它负责什么
 *
 * 加载官方 preload → 注入 `globalThis.lx` 需要的那 6 个原生函数 → `lx_setup(...)`
 * → 执行脚本 → 等 `init` 事件 → 之后按需发 `request` 拿播放直链。
 *
 * ## 协议（照抄官方 `QuickJS.java` / `user-api-preload.js`）
 *
 * ```
 * JS  -> 原生 : __lx_native_call__(key, 'init',           {info, status, errorMessage})
 * JS  -> 原生 : __lx_native_call__(key, 'request',        {requestKey, url, options})
 * JS  -> 原生 : __lx_native_call__(key, 'cancelRequest',  requestKey)
 * JS  -> 原生 : __lx_native_call__(key, 'showUpdateAlert',{name, log, updateUrl})
 * JS  -> 原生 : __lx_native_call__(key, 'response',       {requestKey, status, result|errorMessage})
 * 原生 -> JS  : __lx_native__(key, 'request',  {requestKey, data:{source, action, info}})
 * 原生 -> JS  : __lx_native__(key, 'response', {requestKey, error, response})
 * 原生 -> JS  : __lx_native__(key, '__set_timeout__', id)
 * ```
 *
 * ## 为什么不自己实现一套
 *
 * preload 里已经写好了返回值校验（`musicUrl` 必须是 http/https 且 ≤2048、
 * `lyric` 必须是对象且 ≤51200）和防脚本乱来的加固（禁 eval、冻结全局）。
 * 这些是官方踩过坑才有的，重写只会更差。
 */
class LxUserApi(
    private val engine: LxEngine,
    private val preloadScript: String,
    private val http: (LxHttp.Request) -> LxHttp.Result = { LxHttp.perform(it) },
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
    private val key: String = UUID.randomUUID().toString(),
) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val initDeferred = CompletableDeferred<LxInitResult>()
    private val pendingResponses = ConcurrentHashMap<String, CompletableDeferred<LxScriptResponse>>()
    private val pendingHttp = ConcurrentHashMap<String, LxHttp.Request>()

    @Volatile
    var info: LxScriptInfo? = null
        private set

    @Volatile
    var initResult: LxInitResult? = null
        private set

    // ── 桥接 ─────────────────────────────────────────────────────────────

    /** 装好原生桥（必须在 evaluate preload 之前调用）。 */
    fun attachBridge() {
        engine.setGlobalString("__lx_key__", key)

        engine.setGlobalFunction("__lx_native_call__") { args ->
            handleNativeCall(args)
            null
        }

        // 官方 preload 里 utils.crypto / utils.buffer 全部转调这几个同步函数。
        // 注意 md5 官方会先做 URL 解码 —— 照抄，否则脚本算出来的签名对不上。
        engine.setGlobalFunction("__lx_native_call__utils_str2b64") { args ->
            LxCrypto.strToBase64(args.firstOrNull() as? String ?: "")
        }
        engine.setGlobalFunction("__lx_native_call__utils_b642buf") { args ->
            LxCrypto.base64ToByteArrayJson(args.firstOrNull() as? String ?: "")
        }
        engine.setGlobalFunction("__lx_native_call__utils_str2md5") { args ->
            val raw = args.firstOrNull() as? String ?: ""
            val decoded = runCatching { URLDecoder.decode(raw, "UTF-8") }.getOrDefault(raw)
            LxCrypto.md5(decoded)
        }
        engine.setGlobalFunction("__lx_native_call__utils_aes_encrypt") { args ->
            LxCrypto.aesEncrypt(
                args.getOrNull(0) as? String ?: "",
                args.getOrNull(1) as? String ?: "",
                args.getOrNull(2) as? String ?: "",
                args.getOrNull(3) as? String ?: "",
            )
        }
        engine.setGlobalFunction("__lx_native_call__utils_rsa_encrypt") { args ->
            LxCrypto.rsaEncrypt(
                args.getOrNull(0) as? String ?: "",
                args.getOrNull(1) as? String ?: "",
                args.getOrNull(2) as? String ?: "",
            )
        }
        // set_timeout：JS 让我们 N 毫秒后回调它
        engine.setGlobalFunction("__lx_native_call__set_timeout") { args ->
            val id = (args.getOrNull(0) as? Number)?.toInt() ?: -1
            val ms = (args.getOrNull(1) as? Number)?.toLong() ?: 0L
            if (id >= 0) {
                scope.launch {
                    kotlinx.coroutines.delay(ms.coerceAtLeast(0))
                    runCatching { engine.callGlobal("__lx_native__", key, "__set_timeout__", id) }
                }
            }
            null
        }
    }

    private fun handleNativeCall(args: Array<Any?>) {
        if ((args.getOrNull(0) as? String) != key) return
        val action = args.getOrNull(1) as? String ?: return
        val data = args.getOrNull(2) as? String
        when (action) {
            "init" -> {
                val result = LxProtocol.parseInit(data.orEmpty()).let { parsed ->
                    // 补上我们自己解析到的头部信息（preload 的 info 可能缺字段）
                    parsed.copy(
                        name = parsed.name.ifBlank { info?.name.orEmpty() },
                        version = parsed.version.ifBlank { info?.version.orEmpty() },
                        author = parsed.author.ifBlank { info?.author.orEmpty() },
                        description = parsed.description.ifBlank { info?.description.orEmpty() },
                    )
                }
                initResult = result
                initDeferred.complete(result)
            }

            "request" -> handleScriptHttpRequest(data.orEmpty())

            "response" -> {
                // 注意：这是脚本对 request 的答复（JS→原生），形状是 {requestKey, status, result}
                val parsed = LxProtocol.parseScriptResult(data.orEmpty())
                pendingResponses.remove(parsed.requestKey)?.complete(parsed)
            }

            "cancelRequest" -> {
                val reqKey = data?.trim()?.trim('"')
                if (reqKey != null) pendingHttp.remove(reqKey)
            }

            "showUpdateAlert" -> Unit // 我们不弹更新提示
        }
    }

    /** 脚本自己要发 HTTP（`lx.request(...)`）。 */
    private fun handleScriptHttpRequest(dataJson: String) {
        val root = runCatching { json.parseToJsonElement(dataJson).jsonObject }.getOrNull() ?: return
        val requestKey = root["requestKey"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val url = root["url"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val optionsJson = root["options"]?.let { if (it is JsonObject) it.toString() else null }

        val request = LxHttp.parse(requestKey, url, optionsJson)
        pendingHttp[requestKey] = request

        scope.launch {
            val result = http(request)
            // 被取消的请求不回灌，免得脚本拿到已作废的结果
            if (pendingHttp.remove(requestKey) == null) return@launch
            val payload = buildJsonObject {
                put("requestKey", requestKey)
                if (result.error != null) {
                    put("error", result.error)
                } else {
                    put("error", kotlinx.serialization.json.JsonNull)
                    put("response", buildJsonObject {
                        put("statusCode", result.statusCode)
                        put("statusMessage", result.statusMessage)
                        put("headers", buildJsonObject {
                            result.headers.forEach { (k, v) -> put(k, v) }
                        })
                        put("body", json.parseToJsonElement(result.bodyJson))
                    })
                }
            }.toString()
            runCatching { engine.callGlobal("__lx_native__", key, "response", payload) }
        }
    }

    // ── 对外 ─────────────────────────────────────────────────────────────

    /**
     * 加载脚本并等 `init`。
     *
     * 失败一律返回 `ok = false`，**不抛异常** —— 脚本坏了是正常业务，
     * 调用方只需要把 [LxInitResult.errorMessage] 显示给用户。
     */
    suspend fun load(
        script: String,
        info: LxScriptInfo,
        timeoutMs: Long = 15_000,
    ): LxInitResult {
        this.info = info
        attachBridge()
        return try {
            engine.evaluate(LxBufferPolyfill.SCRIPT, LxBufferPolyfill.FILENAME)
            engine.evaluate(preloadScript, "lx-preload.js")
            engine.callGlobal(
                "lx_setup",
                key,
                info.name,
                info.description,
                info.version,
                info.author,
                info.homepage,
                script,
            )
            engine.evaluate(script, "user-api.js")
            withTimeoutOrNull(timeoutMs) { initDeferred.await() }
                ?: LxInitResult(ok = false, errorMessage = "脚本初始化超时（${timeoutMs}ms 内没有发出 init 事件）")
        } catch (e: Throwable) {
            LxInitResult(ok = false, errorMessage = LxFailureText.translate(e.message))
        }
    }

    /**
     * 向脚本要一首歌的播放直链。
     *
     * @param musicInfoJson 歌曲信息（洛雪的结构，至少要有 `source` 和 `songmid`）
     */
    suspend fun musicUrl(
        source: String,
        musicInfoJson: String,
        quality: String = "320k",
        timeoutMs: Long = 20_000,
    ): LxResult<String> {
        if (initResult?.ok != true) {
            return LxResult.failure("脚本还没初始化成功")
        }
        val requestKey = UUID.randomUUID().toString()
        val deferred = CompletableDeferred<LxScriptResponse>()
        pendingResponses[requestKey] = deferred

        return try {
            val infoJson = buildJsonObject {
                put("type", quality)
                put("musicInfo", json.parseToJsonElement(musicInfoJson))
            }.toString()
            val payload = LxProtocol.requestPayload(requestKey, source, "musicUrl", json.parseToJsonElement(infoJson))

            engine.callGlobal("__lx_native__", key, "request", payload)

            val resp = withTimeoutOrNull(timeoutMs) { deferred.await() }
            if (resp == null) {
                LxResult.failure("脚本响应超时（${timeoutMs}ms）")
            } else if (!resp.ok) {
                LxResult.failure(LxFailureText.translate(resp.error))
            } else {
                val url = resp.resultText.trim()
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    LxResult.success(url)
                } else {
                    LxResult.failure("脚本返回的不是有效地址：${url.take(60)}")
                }
            }
        } catch (e: Throwable) {
            LxResult.failure(LxFailureText.translate(e.message))
        } finally {
            pendingResponses.remove(requestKey)
        }
    }

    fun close() {
        runCatching { engine.close() }
    }
}

/** 一个「要么成功要么带人话失败原因」的结果。 */
data class LxResult<out T>(
    val value: T? = null,
    val error: String? = null,
) {
    val ok: Boolean get() = error == null

    companion object {
        fun <T> success(v: T) = LxResult<T>(value = v)
        fun <T> failure(msg: String) = LxResult<T>(error = msg)
    }
}
