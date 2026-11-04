package io.github.laozhudu.linkplay.lx

import android.content.Context

/**
 * 从 assets 读两份 JS 载荷。
 *
 * 分开放的原因：
 * - `lx-preload.js` 是**官方原文**（594 行），一行没改 —— 上游更新时直接覆盖即可；
 * - `lx-music-sdk.bundle.js` 是**构建产物**（由 `scripts/build-lx-bundle.sh` 生成），
 *   仓库里存一份是为了 CI 不用每次 clone 上游 + npm install。
 *
 * 两份都是只读资源，读一次缓存起来。
 */
object LxAssets {

    const val PRELOAD = "lx-preload.js"
    const val MUSIC_SDK = "lx-music-sdk.bundle.js"

    @Volatile
    private var preloadCache: String? = null

    @Volatile
    private var sdkCache: String? = null

    fun preload(context: Context): String =
        preloadCache ?: read(context, PRELOAD).also { preloadCache = it }

    fun musicSdk(context: Context): String =
        sdkCache ?: read(context, MUSIC_SDK).also { sdkCache = it }

    private fun read(context: Context, name: String): String =
        context.assets.open(name).bufferedReader().use { it.readText() }

    /** 单测/排障用：清缓存。 */
    fun clearCache() {
        preloadCache = null
        sdkCache = null
    }
}
