package io.github.laozhudu.linkplay.lx

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 跑打包好的洛雪 musicSdk（`assets/lx-music-sdk.bundle.js`）。
 *
 * ## 和 [LxUserApi] 的分工
 *
 * | | 干什么 | 有没有搜索 |
 * |---|---|---|
 * | [LxUserApi]（用户脚本） | 把 songmid 变成播放直链 | ❌ 脚本只给直链 |
 * | [LxMusicSdk]（本文） | 搜索 / 热搜 / 歌单 / 歌词 / 封面 | ✅ 五个平台 |
 *
 * 洛雪 2.x 把**内置取流**关掉了（`api-source-info.ts` 是空数组），
 * 但**内置搜索**还在。所以两者必须一起用：SDK 搜歌 → 脚本出直链。
 *
 * ## 通道
 *
 * ```
 * Kotlin -> JS : lxSdkCall(reqId, action, payloadJson)
 * JS -> Kotlin : __lx_native_call__(key, 'sdkResponse', {reqId, ok, data|error})
 * JS -> Kotlin : __lx_native_call__(key, 'http',        {id, url, options})
 * Kotlin -> JS : __lx_http_settle__(id, payloadJson)   （HTTP 结果回灌）
 * ```
 *
 * ## 为什么要单独一个引擎实例
 *
 * 用户脚本那份 preload 会**冻结 globalThis 并禁用 eval**（官方安全加固），
 * 如果和 SDK 共用一个上下文，SDK 一加载就废。所以两者必须是两个 QuickJSContext。
 */
class LxMusicSdk(
    private val engine: LxEngine,
    private val bundleScript: String,
    private val http: (LxHttp.Request) -> LxHttp.Result = { LxHttp.perform(it) },
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
    private val key: String = UUID.randomUUID().toString(),
) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val pending = ConcurrentHashMap<String, CompletableDeferred<LxSdkResponse>>()
    private val pendingHttp = ConcurrentHashMap<String, LxHttp.Request>()

    @Volatile
    private var loaded = false

    /** 装桥 + 执行 bundle。**必须在使用任何查询前调用一次。** */
    fun load() {
        if (loaded) return
        engine.setGlobalString("__lx_key__", key)

        engine.setGlobalFunction("__lx_crypto__") { args ->
            LxCrypto.call(
                args.getOrNull(0) as? String ?: "",
                args.getOrNull(1) as? String ?: "[]",
            )
        }

        engine.setGlobalFunction("__lx_native_call__") { args ->
            if ((args.getOrNull(0) as? String) == key) {
                when (args.getOrNull(1) as? String) {
                    "sdkResponse" -> {
                        val parsed = LxProtocol.parseSdkResponse(args.getOrNull(2) as? String ?: "")
                        pending.remove(parsed.reqId)?.complete(parsed)
                    }
                    "http" -> handleHttp(args.getOrNull(2) as? String ?: "")
                }
            }
            null
        }

        // 必须在 bundle 之前：kw 的歌词解码直接用 Buffer（Node 全局），QuickJS 没有。
        // 真机报错：'Buffer' is not defined at lx-music-sdk.bundle.js:7948
        engine.evaluate(LxBufferPolyfill.SCRIPT, LxBufferPolyfill.FILENAME)
        engine.evaluate(bundleScript, "lx-music-sdk.bundle.js")
        loaded = true
    }

    private fun handleHttp(dataJson: String) {
        val root = runCatching { json.parseToJsonElement(dataJson).jsonObject }.getOrNull() ?: return
        val id = root["id"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val url = root["url"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val optionsJson = root["options"]?.let { if (it is JsonObject) it.toString() else null }

        val request = LxHttp.parse(id, url, optionsJson)
        pendingHttp[id] = request

        scope.launch {
            val result = http(request)
            if (pendingHttp.remove(id) == null) return@launch
            runCatching { engine.callGlobal("__lx_http_settle__", id, result.toJsPayload(id)) }
        }
    }

    // ── 查询 ─────────────────────────────────────────────────────────────

    /** 支持的平台（由 bundle 自己报，不写死）。 */
    suspend fun sources(): LxResult<List<String>> {
        val data = call("sources", buildJsonObject { }) ?: return LxResult.failure("取平台列表失败")
        val arr = (data as? JsonObject)?.get("sources") as? JsonArray
            ?: return LxResult.failure("平台列表格式不对")
        return LxResult.success(arr.mapNotNull { it.jsonPrimitive.contentOrNull })
    }

    /** 按歌名搜。 */
    suspend fun search(source: String, name: String, page: Int = 1, limit: Int = 30): LxResult<LxSearchPage> {
        val data = call("search", buildJsonObject {
            put("source", source)
            put("name", name)
            put("page", page)
            put("limit", limit)
        }) ?: return LxResult.failure("搜索失败")

        val obj = data as? JsonObject ?: return LxResult.failure("搜索结果格式不对")
        val list = (obj["list"] as? JsonArray)?.map { it.jsonObject }.orEmpty()
        return LxResult.success(
            LxSearchPage(
                tracks = list.map(::toTrack),
                total = obj["total"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: list.size,
            ),
        )
    }

    /** 热搜词。 */
    suspend fun hotSearch(source: String): LxResult<List<String>> {
        val data = call("hotSearch", buildJsonObject { put("source", source) })
            ?: return LxResult.failure("取热搜失败")
        val arr = (data as? JsonObject)?.get("list") as? JsonArray
            ?: return LxResult.failure("热搜格式不对")
        return LxResult.success(arr.mapNotNull { it.jsonPrimitive.contentOrNull })
    }

    /** 歌单搜索。 */
    suspend fun songListSearch(source: String, name: String, page: Int = 1, limit: Int = 30): LxResult<List<LxPlaylistBrief>> {
        val data = call("songListSearch", buildJsonObject {
            put("source", source)
            put("name", name)
            put("page", page)
            put("limit", limit)
        }) ?: return LxResult.failure("歌单搜索失败")
        val arr = (data as? JsonObject)?.get("list") as? JsonArray
            ?: return LxResult.failure("歌单格式不对")
        return LxResult.success(
            arr.map { it.jsonObject }.map { o ->
                LxPlaylistBrief(
                    id = o.str("id"),
                    name = o.str("name"),
                    author = o.str("author"),
                    total = o.int("total"),
                    img = o.str("img").ifBlank { null },
                )
            },
        )
    }

    /** 歌词。返回空串表示这个平台没给。 */
    suspend fun lyric(source: String, musicInfoJson: String): LxResult<String> {
        val data = call("lyric", buildJsonObject {
            put("source", source)
            put("musicInfo", json.parseToJsonElement(musicInfoJson))
        }) ?: return LxResult.failure("取歌词失败")
        val lyric = (data as? JsonObject)?.str("lyric").orEmpty()
        return LxResult.success(lyric)
    }

    /** 封面地址。 */
    suspend fun pic(source: String, musicInfoJson: String): LxResult<String> {
        val data = call("pic", buildJsonObject {
            put("source", source)
            put("musicInfo", json.parseToJsonElement(musicInfoJson))
        }) ?: return LxResult.failure("取封面失败")
        val url = (data as? JsonObject)?.str("url").orEmpty()
        return LxResult.success(url)
    }

    // ── 内部 ─────────────────────────────────────────────────────────────

    private suspend fun call(action: String, payload: JsonObject, timeoutMs: Long = 20_000): JsonElement? {
        if (!loaded) load()
        val reqId = UUID.randomUUID().toString()
        val deferred = CompletableDeferred<LxSdkResponse>()
        pending[reqId] = deferred
        return try {
            engine.callGlobal("lxSdkCall", reqId, action, payload.toString())
            val resp = withTimeoutOrNull(timeoutMs) { deferred.await() } ?: return null
            if (resp.ok) resp.data else null
        } catch (e: Throwable) {
            null
        } finally {
            pending.remove(reqId)
        }
    }

    private fun toTrack(o: JsonObject): LxTrack = LxTrack(
        source = o.str("source"),
        songmid = o.str("songmid"),
        name = o.str("name"),
        singer = o.str("singer"),
        albumName = o.str("albumName"),
        albumId = o.str("albumId"),
        interval = o.str("interval"),
        img = o.str("img").ifBlank { null },
    )

    private fun JsonObject.str(key: String): String =
        (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()

    private fun JsonObject.int(key: String): Int =
        (this[key] as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: 0

    fun close() {
        runCatching { engine.close() }
    }
}

/** 一页搜索结果。 */
data class LxSearchPage(val tracks: List<LxTrack>, val total: Int)

/**
 * 洛雪结构的一首歌。
 *
 * `songmid` 是**各平台自己的 id**（网易云是数字、QQ 是字符串），
 * 用户脚本要靠 `source + songmid` 才能解析直链，所以两个都不能丢。
 */
data class LxTrack(
    val source: String,
    val songmid: String,
    val name: String,
    val singer: String = "",
    val albumName: String = "",
    val albumId: String = "",
    val interval: String = "",
    val img: String? = null,
) {
    /** 转成脚本 `musicUrl` 需要的 musicInfo。 */
    fun toMusicInfoJson(): String = buildJsonObject {
        put("source", source)
        put("songmid", songmid)
        put("name", name)
        put("singer", singer)
        put("albumName", albumName)
        put("albumId", albumId)
        put("interval", interval)
    }.toString()
}

/** 歌单摘要。 */
data class LxPlaylistBrief(
    val id: String,
    val name: String,
    val author: String = "",
    val total: Int = 0,
    val img: String? = null,
)
