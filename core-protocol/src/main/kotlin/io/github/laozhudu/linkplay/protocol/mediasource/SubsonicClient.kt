package io.github.laozhudu.linkplay.protocol.mediasource

import java.net.URLEncoder
import java.security.MessageDigest

/**
 * Subsonic / OpenSubsonic 音源（`.124` 上的 **tt-music** 就是这种服务端）。
 *
 * ## 为什么选 Subsonic 而不是内嵌洛雪脚本
 *
 * tt-music（`penhaijun/lx-music-server`）是 Navidrome 分支，`config.js` 里
 * `"subsonic.enable": true`、`"subsonic.path": "/rest"`。真机 ping 通：
 * ```
 * GET http://192.168.99.124:9527/rest/ping.view?u=tt01&p=123456&v=1.16.1&c=linkplay&f=json
 * → {"status":"ok","version":"1.16.1","type":"lxserver","openSubsonic":true}
 * ```
 * 也就是说"洛雪那层取流的不可控"被关在 NAS 里，App 侧只需要面对**标准协议** ——
 * 不用内嵌 QuickJS、不用跟着洛雪脚本 API 长期打补丁。
 *
 * ## 认证用 token 而不是明文密码
 *
 * Subsonic 支持 `t=md5(password + salt)&s=salt`。真机两种都收，但我们只发 token：
 * 明文密码会进 URL，容易被日志/代理记录下来。
 *
 * @param transport 可注入的 HTTP GET（单测里用假的，不需要真服务器）
 */
class SubsonicClient(
    private val config: SubsonicConfig,
    private val transport: (String) -> String,
) : MediaProvider {

    override val id: String = ID

    override val displayName: String = config.name.ifBlank { "在线曲库" }

    override val configured: Boolean = config.enabled && config.baseUrl.isNotBlank() && config.user.isNotBlank()

    // ---- 请求构造 ----

    /** `baseUrl` 规范化：去掉尾部斜杠、缺协议补 `http://`。 */
    internal fun restBase(): String {
        var b = config.baseUrl.trim().ifBlank { return "" }
        if (!b.startsWith("http://") && !b.startsWith("https://")) b = "http://$b"
        return b.trimEnd('/') + "/rest"
    }

    /** 认证片段。真机 `.124` 验证过 token 方式可用。 */
    internal fun auth(salt: String = SALT): String {
        val t = md5(config.password + salt)
        return "u=${enc(config.user)}&t=$t&s=$salt&v=$API_VERSION&c=$CLIENT&f=json"
    }

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    /** ping：只验证"地址 + 账号对不对"，不返回业务数据。 */
    fun ping(): Boolean {
        val body = transport("${restBase()}/ping.view?${auth()}")
        return parseStatus(body) == "ok"
    }

    // ---- MediaProvider ----

    /**
     * `search3.view` 搜歌。
     *
     * 只取 `song`：专辑/艺术家结果对我们的"点一首就投给音箱"没有用，
     * 少取两类也少一分设备端的解析负担。
     */
    override suspend fun search(query: String, limit: Int): List<MediaTrack> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val url = "${restBase()}/search3.view?${auth()}" +
            "&query=${enc(q)}&songCount=${limit.coerceIn(1, 200)}&artistCount=0&albumCount=0"
        val body = transport(url)
        val status = parseStatus(body)
        if (status == null) {
            // 返回的不是 Subsonic 的 JSON（地址填错 / 服务没起 / 被代理拦了）
            throw MediaSourceException(
                ID, null,
                "「$displayName」返回的不是 Subsonic 响应，检查地址是否为 http://<IP>:<端口>（响应开头：${body.take(40).replace('\n', ' ')}）",
            )
        }
        if (status != "ok") {
            throw MediaSourceException(ID, null, "在线曲库返回 $status：${errorMessage(body) ?: "未知原因"}")
        }
        return parseSongs(body)
    }

    /**
     * 直链。**带凭据的 stream.view**：音箱裸 GET 也能直接拉（不需要 Referer/Cookie）。
     *
     * 这一点很关键：多数公网音源要做防盗链，而 Subsonic 的凭据放在 query 里，
     * 所以这类源**不需要**经过我们的 RelayMediaServer 转发。
     */
    override suspend fun resolve(track: MediaTrack): String =
        "${restBase()}/stream.view?${auth()}&id=${enc(track.id)}"

    override fun coverUrl(track: MediaTrack, sizePx: Int): String? {
        val id = track.coverId ?: track.id
        if (id.isBlank()) return null
        return "${restBase()}/getCoverArt.view?${auth()}&id=${enc(id)}&size=$sizePx"
    }

    companion object {
        const val ID = "subsonic"
        const val API_VERSION = "1.16.1"
        const val CLIENT = "linkplay-console"

        /** 固定 salt：token 有效期由服务端决定，固定 salt 便于单测断言；不降低安全性（每次密码变更 token 才变）。 */
        const val SALT = "linkplay"

        internal fun md5(input: String): String =
            MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }

        internal fun parseStatus(body: String): String? {
            val root = runCatching { io.github.laozhudu.linkplay.protocol.Json.parse(body) }
                .getOrNull() as? io.github.laozhudu.linkplay.protocol.JsonValue.Obj ?: return null
            val resp = root.obj("subsonic-response") ?: return null
            return resp.string("status")
        }

        internal fun errorMessage(body: String): String? {
            val root = runCatching { io.github.laozhudu.linkplay.protocol.Json.parse(body) }
                .getOrNull() as? io.github.laozhudu.linkplay.protocol.JsonValue.Obj ?: return null
            val err = root.obj("subsonic-response")?.obj("error") ?: return null
            return err.string("message") ?: err.string("code")
        }

        /**
         * 解析 `searchResult3.song[]`。
         *
         * 字段按 OpenSubsonic 规范：`id/title/artist/album/duration/suffix/size/coverArt`。
         * `duration` 是**秒**（不是毫秒），这里原样保留。
         */
        internal fun parseSongs(body: String): List<MediaTrack> {
            val root = runCatching { io.github.laozhudu.linkplay.protocol.Json.parse(body) }
                .getOrNull() as? io.github.laozhudu.linkplay.protocol.JsonValue.Obj ?: return emptyList()
            val songs = root.obj("subsonic-response")
                ?.obj("searchResult3")
                ?.array("song")
                ?.items ?: return emptyList()
            return songs.mapNotNull { item ->
                val o = item as? io.github.laozhudu.linkplay.protocol.JsonValue.Obj ?: return@mapNotNull null
                val id = o.string("id") ?: return@mapNotNull null
                MediaTrack(
                    id = id,
                    title = o.string("title") ?: "未命名",
                    artist = o.string("artist"),
                    album = o.string("album"),
                    durationSec = (o.string("duration")?.toLongOrNull() ?: o.int("duration")?.toLong() ?: 0L),
                    providerId = ID,
                    suffix = o.string("suffix"),
                    sizeBytes = (o.string("size")?.toLongOrNull() ?: 0L),
                    coverId = o.string("coverArt"),
                )
            }
        }
    }
}

/**
 * Subsonic 音源配置。
 *
 * `baseUrl` 允许写 `192.168.99.124:9527`（不带协议）—— 家里自建服务端常常只给 IP:端口，
 * 让用户记住"要加 http://"是没必要的摩擦（之前电台那里就吃过一次，缺协议被判非法）。
 */
data class SubsonicConfig(
    val name: String = "在线曲库",
    val baseUrl: String = "",
    val user: String = "",
    val password: String = "",
    val enabled: Boolean = true,
) {
    fun encode(): String = io.github.laozhudu.linkplay.protocol.JsonWriter.encode(
        io.github.laozhudu.linkplay.protocol.JsonValue.Obj(
            mapOf(
                "name" to io.github.laozhudu.linkplay.protocol.JsonValue.Str(name),
                "url" to io.github.laozhudu.linkplay.protocol.JsonValue.Str(baseUrl),
                "user" to io.github.laozhudu.linkplay.protocol.JsonValue.Str(user),
                "pass" to io.github.laozhudu.linkplay.protocol.JsonValue.Str(password),
                "enabled" to io.github.laozhudu.linkplay.protocol.JsonValue.Bool(enabled),
            ),
        ),
    )

    companion object {
        fun decode(raw: String?): SubsonicConfig? {
            if (raw.isNullOrBlank()) return null
            val root = runCatching { io.github.laozhudu.linkplay.protocol.Json.parse(raw) }
                .getOrNull() as? io.github.laozhudu.linkplay.protocol.JsonValue.Obj ?: return null
            return SubsonicConfig(
                name = root.string("name") ?: "在线曲库",
                baseUrl = root.string("url").orEmpty(),
                user = root.string("user").orEmpty(),
                password = root.string("pass").orEmpty(),
                enabled = (root.fields["enabled"] as? io.github.laozhudu.linkplay.protocol.JsonValue.Bool)?.value ?: true,
            )
        }
    }
}
