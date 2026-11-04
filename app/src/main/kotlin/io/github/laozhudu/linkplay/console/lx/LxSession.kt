package io.github.laozhudu.linkplay.console.lx

import android.content.Context
import io.github.laozhudu.linkplay.console.LxSourceStatus
import io.github.laozhudu.linkplay.lx.LxAssets
import io.github.laozhudu.linkplay.lx.LxMusicSdk
import io.github.laozhudu.linkplay.lx.LxScriptInfo
import io.github.laozhudu.linkplay.lx.LxUserApi
import io.github.laozhudu.linkplay.lx.QuickJsEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 洛雪引擎的进程级持有者 —— **搜索一个引擎、取流一个引擎**。
 *
 * ## 为什么必须是两个引擎（不是省事）
 *
 * 洛雪 2.x 里这两件事是分开的：SDK 只负责搜，取直链必须跑**用户脚本**。
 * 而用户脚本那份 preload 会冻结 `globalThis` 并禁用 `eval`（官方安全加固），
 * 两者共用一个 QuickJS 上下文时，SDK 一加载就把自己冻死。
 *
 * ## 为什么要缓存
 *
 * 加载 bundle 实测 72ms，看起来不多；但用户每点一次搜索都重来一遍就等于
 * 每次搜索多 72ms + 一次 QuickJS 初始化。引擎是单线程 executor，本来就能复用。
 *
 * ## 换脚本才重建取流引擎
 *
 * 用户换了脚本（或脚本升级）必须重新 load，否则跑的还是上一支。
 * 这里用"名字 + 内容哈希"当键，避免同名不同内容（用户手改了脚本）时仍用旧的。
 */
object LxSession {

    private var sdkEngine: QuickJsEngine? = null
    private var sdk: LxMusicSdk? = null

    private var userEngine: QuickJsEngine? = null
    private var userApi: LxUserApi? = null
    private var userKey: String? = null

    /** 拿搜索用的 SDK（第一次会加载 bundle）。 */
    suspend fun sdk(context: Context): LxMusicSdk = withContext(Dispatchers.IO) {
        sdk?.let { return@withContext it }
        val engine = QuickJsEngine()
        val created = LxMusicSdk(engine, LxAssets.musicSdk(context))
        val started = System.currentTimeMillis()
        created.load()
        LxSourceStatus.loaded(System.currentTimeMillis() - started)
        sdkEngine = engine
        sdk = created
        created
    }

    /**
     * 拿取流用的 API（跑到用户装的那支脚本上）。
     *
     * 失败抛异常，由调用方转成给用户看的一句话 —— 这里不决定"怎么跟用户说"。
     */
    suspend fun userApi(context: Context, scriptText: String, info: LxScriptInfo): LxUserApi =
        withContext(Dispatchers.IO) {
            val key = info.name + "|" + scriptText.hashCode()
            userApi?.let { if (userKey == key) return@withContext it }

            runCatching { userApi?.close() }
            runCatching { userEngine?.close() }
            userApi = null
            userEngine = null
            userKey = null

            val engine = QuickJsEngine()
            val created = LxUserApi(engine, LxAssets.preload(context))
            val init = created.load(scriptText, info)
            if (!init.ok) {
                runCatching { engine.close() }
                throw IllegalStateException(init.errorMessage ?: "脚本初始化失败")
            }
            userEngine = engine
            userApi = created
            userKey = key
            created
        }

    /** 排障用：把引擎都关掉（下次重新加载）。 */
    fun closeAll() {
        runCatching { userApi?.close() }
        runCatching { sdkEngine?.close() }
        userApi = null
        userEngine = null
        userKey = null
        sdk = null
        sdkEngine = null
    }
}
