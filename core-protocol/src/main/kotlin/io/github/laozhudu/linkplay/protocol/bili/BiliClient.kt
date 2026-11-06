package io.github.laozhudu.linkplay.protocol.bili

import io.github.laozhudu.linkplay.protocol.Json
import io.github.laozhudu.linkplay.protocol.JsonValue

/**
 * B 站取流（`docs/85` 第 2 节的实测契约，全部免鉴权）。
 *
 * ## 只能按 BV 号 / 链接用，**不能搜关键词**
 *
 * `docs/85` §2.4 实测：`/x/web-interface/search/type` 直接返回 **HTML 风控页**，
 * 带 wbi 签名的版本返回空 `v_voucher`。要做得补 buvid + cookie + wbi 签名，
 * 那是另一套工作量，而且 ToS 更敏感。
 *
 * 所以这个源在界面上表现为「**粘 BV 号或视频链接**」，不是搜索框。
 *
 * ## 直链的三个硬约束（都测过）
 *
 * 1. **有效期约 120 分钟**（URL 里的 `deadline`）→ **不能缓存，必须现取现用**
 * 2. 返回的容器是 **fMP4 分片**，DOSS 一律 `stop` → **必须先 remux**（见 `Fmp4Remuxer`）
 * 3. `Content-Type` 是 `application/octet-stream`，不需要 Referer、不需要特殊 UA
 *
 * ## 为什么依赖是两个 lambda
 *
 * 和 `IrRemoteControl` 同一个理由：让它**能跑纯 JVM 单测**。
 * 「哪个音轨该选」和「输入串怎么解析出 BV 号」这两条规则最容易写错，
 * 而它们恰恰不需要网络就能验。
 */
class BiliClient(
    /** 取一段 JSON：`(url) -> 响应体`；失败抛异常。 */
    private val fetchJson: suspend (String) -> String,
) {

    /** 一次取流的结果。 */
    data class Stream(
        val bvid: String,
        val cid: Long,
        val title: String,
        /** 音频直链（fMP4，**必须 remux 之后才能投**）。 */
        val audioUrl: String,
        val durationSec: Long,
        /** 选中的音轨 id（30280 = 免登录最高 203kbps）。 */
        val audioQualityId: Int,
    )

    /**
     * 从用户输入里认出 BV 号。
     *
     * 支持三种写法（`docs/85` 里用户实际会粘的）：
     * - `BV1GJ411x7h7`
     * - `https://www.bilibili.com/video/BV1GJ411x7h7?p=1`
     * - `https://b23.tv/xxxxx`（短链，**这里认不出，需要先展开**）
     *
     * 认不出返回 null —— 界面据此提示"粘 BV 号或完整链接"。
     */
    fun parseBvid(input: String): String? {
        val t = input.trim()
        if (t.isEmpty()) return null
        // 直接的 BV 号
        BV_REGEX.find(t)?.let { return it.value }
        return null
    }

    /** 取 cid / 标题 / 时长（`/x/web-interface/view`，免鉴权）。 */
    suspend fun fetchStream(bvid: String): Stream {
        val view = fetchJson("$API_VIEW?bvid=$bvid")
        val data = obj(Json.parse(view), "data") ?: error("B 站没返回 data")
        // B 站的 cid / duration 是**数字**不是字符串 —— `string()` 取不到，
        // 必须走 `int()`。第一版就是栽在这，测试直接给了 IllegalStateException。
        val cid = data.long("cid") ?: error("没有 cid")
        val title = data.string("title") ?: bvid
        val duration = data.long("duration") ?: 0L

        val playurl = fetchJson("$API_PLAYURL?bvid=$bvid&cid=$cid&fnval=16&fourk=1")
        val dash = obj(Json.parse(playurl), "data")?.obj("dash") ?: error("没有 dash（可能该视频无音轨）")
        val audios = dash.array("audio")?.items.orEmpty()

        // 选带宽最大的那条：免登录最高就是 30280（203kbps）
        val best = audios
            .mapNotNull { it as? JsonValue.Obj }
            .maxByOrNull { it.long("bandwidth") ?: 0L }
            ?: error("dash 里没有音频轨")

        val url = best.string("baseUrl") ?: best.string("base_url") ?: error("音轨没有地址")
        val qid = best.long("id")?.toInt() ?: 0

        return Stream(
            bvid = bvid,
            cid = cid,
            title = title,
            audioUrl = url,
            durationSec = duration,
            audioQualityId = qid,
        )
    }

    private fun obj(v: JsonValue, key: String): JsonValue.Obj? = (v as? JsonValue.Obj)?.obj(key)

    /** 数字字段：B 站有的地方给数字、有的给字符串，两边都认。 */
    private fun JsonValue.Obj.long(key: String): Long? =
        int(key)?.toLong() ?: string(key)?.toLongOrNull()

    companion object {
        const val API_VIEW = "https://api.bilibili.com/x/web-interface/view"
        const val API_PLAYURL = "https://api.bilibili.com/x/player/playurl"

        /** `BV` + 10 位（大小写数字混合）。 */
        val BV_REGEX = Regex("BV[0-9A-Za-z]{10}")
    }
}
