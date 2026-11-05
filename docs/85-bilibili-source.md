# 85 · B 站作为音源/视频源：接入施工文档

> 写于 2026-11-05 · 依据 main `a580bd0`
> 用户：「把做 b 站的调研发 pr 给那边做」· 前提：**仅自用、投通用 UPnP、不依赖 NAS**
>
> **这份文档是给"负责写代码那个对话"看的。只读第零、三、四节就能开工。**
> 第六节是「最优实践 + 最短路径」，开工前先扫一眼。

---

## 零、三行摘要

1. **B 站能做，纯 Kotlin，不开新 Gradle 模块** —— 加一个 `MediaProvider` 实现类（约 300 行）。
2. **但直链不能直接投给音箱**：B 站给的是 **fMP4 分片**，DOSS 一律 `stop`。**必须先 remux**。
3. **remux 用 Media3 Transformer**（Apache-2.0，同版本 1.5.1 已在依赖里）—— **不要引 ffmpeg-kit**。

**真机已验证的闭环**：取直链 → 下载 5.2MB → `ffmpeg -c copy` 重封装 0.5 秒 → 投 `.164` → `status=play`、`totlen=212309`、`curpos` 稳定递增。

---

## 一、为什么必须先 remux（真机实测，不是推测）

### 1.1 排查过程：四个嫌疑全排除

两条直链（B 站 + YouTube）投 `.164` 都是 `load` → 1.5 秒内 `stop`、`curpos` 恒 0。逐一排除：

| 嫌疑 | 实测 | 结论 |
|---|---|---|
| URL 太长 | 同一电台 URL 加到 **1144 字符**仍 `play` | ❌ |
| URL 里的 `&` | `&amp;` 转义 / 裸 `&` / 4 个参数都 `play` | ❌ |
| HTTPS | `https://icecast.radiofrance.fr/…`、`https://ngcdn001.cnr.cn/…m3u8` 都 `play` | ❌ |
| DNS | `.1` 解析正常；`googlevideo.com` 裸域 TLS 报错是**正常现象**（裸域没证书） | ❌ |

### 1.2 根因：fMP4 分片不被支持

用 ffmpeg 在 `.226` 造四个文件，从 `http://192.168.99.226:8000/` 局域网投给音箱（排除一切外网因素）：

| 文件 | 容器 | 结果 |
|---|---|---|
| `t.mp3` | MP3 / `audio/mpeg` | ✅ **play** curpos 4643 totlen 10000 |
| `t.m4a` | 普通 M4A / `audio/mp4` | ✅ **play** curpos 4574 totlen 10023 |
| `t.mp4` | 完整 MP4 / `video/mp4` | ✅ **play** curpos 4481 totlen 10000 |
| **`t_frag.mp4`** | **分片 MP4** | ❌ **stop，totlen 0** |

而 B 站 DASH 音频就是 fMP4：

```
bili_raw.m4s  5281KB  moof(分片标记)=True    ← 播不了
```

**判据是 `moof` box 有没有，不是 `moov` 在文件头还是尾** —— `t.m4a` 的 moov 在末尾照样能播。

### 1.3 remux 之后就能播（真机验证）

```
ffmpeg -i bili_raw.m4s -c copy -movflags +faststart bili_ok.m4a   # 0.5 秒，不重新编码

t= 1.5s status=play  curpos=  578  totlen=212309
t= 3.0s status=play  curpos= 2157  totlen=212309
t= 9.0s status=play  curpos= 8659  totlen=212309
```

`totlen=212309` 正是 212.3 秒全长。

---

## 二、B 站 API 实测契约

### 2.1 取 cid：免鉴权

```
GET https://api.bilibili.com/x/web-interface/view?bvid=BV1GJ411x7h7
→ {"code":0,"data":{"bvid":"…","aid":80433022,"cid":137649199,"title":"…","pic":"…","duration":213,…}}
```

- **不需要 cookie、不需要 wbi 签名、不需要 Referer**
- `bvid` 在 URL 里，`cid` 是后续要用的

### 2.2 取音频直链：DASH

```
GET https://api.bilibili.com/x/player/playurl?bvid=<bvid>&cid=<cid>&fnval=16&fourk=1
→ code 0
   data.dash.audio[] = [
     {id:30216, codecs:"mp4a.40.5", bandwidth: 43k, mimeType:"audio/mp4"},
     {id:30232, codecs:"mp4a.40.2", bandwidth:102k, mimeType:"audio/mp4"},
     {id:30280, codecs:"mp4a.40.2", bandwidth:203k, mimeType:"audio/mp4"},
   ]
```

- **免登录最高 203kbps（id=30280）**，选 `bandwidth` 最大的那条
- 更高码率要大会员 —— 不做

### 2.3 直链的实测特性

| 项 | 值 |
|---|---|
| 拉取状态码 | **206**（支持 Range） |
| `Content-Type` | `application/octet-stream`（**不是** `audio/mp4`） |
| 要 Referer 吗 | ❌ **不要** |
| 要特殊 UA 吗 | ❌ 不要（`curl/7.36.0` 也通） |
| **容器** | **fMP4（`moof` 存在）→ 必须 remux** |
| 有效期 | **120 分钟**（URL 里 `deadline`），**不能缓存，必须现取现用** |

### 2.4 搜索接口被风控（重要能力边界）

```
GET /x/web-interface/search/type?keyword=test      → 返回 HTML 风控页
GET /x/web-interface/wbi/search/type?keyword=test  → {"code":0,"data":{"v_voucher":"…"}}  没有结果
```

**所以这个源只能「输入 BV 号 / 视频链接」用，不能像洛雪那样搜关键词。**
补 buvid + cookie + wbi 签名是另一套工作量，而且 ToS 更敏感 —— **不做**。

### 2.5 视频（投电视盒子用）

```
GET ...playurl?fnval=0&qn=64 → format "mp4720", durl[0].size=51973319, url=…192.mp4
```

标准 MP4（音视频已封装），理论上电视盒子能直接播。
⚠️ 但实测那个 URL **带 Referer 也 403**（链接有时效/防盗链），所以**视频也要走「下载 → 本地服务」**，不要直投。

---

## 三、施工方案（三步）

### 第 1 步 · `Fmp4Remuxer`（约 80 行）

**用 Media3 Transformer，不要 ffmpeg-kit。**

理由（官方文档原文）：

> If the input media format already matches the configurations for audio or video,
> **Transformer automatically switches to transmuxing, that is, copying the compressed samples
> without re-encoding.**

- `media3-transformer:1.5.1` 与现有 `media3 = "1.5.1"` **同版本**，只加一行依赖
- Apache-2.0，**不碰 GPL**
- 体积增量只有 transformer 库本身（远小于 ffmpeg-kit 的 +50MB）

接口形状（按现有风格，纯逻辑 + 可注入）：

```kotlin
/** 把 fMP4 转成普通 M4A/MP4。**不重新编码**，只改容器。 */
interface Remuxer {
    /** @return 输出文件；失败抛 RemuxException（带人话原因） */
    suspend fun toPlayable(input: File, outDir: File, audioOnly: Boolean = true): File
}
```

实现要点：
- `Transformer.Builder(context).setRemoveVideo(audioOnly)` → `start()`
- 输入 fMP4、输出 `.m4a`（音频）/ `.mp4`（视频）
- 用 `Transformer.Listener` 拿成功/失败，转成 `suspendCancellableCoroutine`
- **失败要能区分**：解不出来 / 磁盘满 / 输出为空

### 第 2 步 · `BilibiliProvider`（约 250 行）

按 `MediaProvider` 现有契约实现：

```kotlin
interface MediaProvider {
    val id: String                    // "bilibili"
    val displayName: String           // "B 站"
    val configured: Boolean           // true（免鉴权，不需要配置）
    suspend fun search(query: String, limit: Int = 50): List<MediaTrack>
    suspend fun resolve(track: MediaTrack): String   // ← 返回设备能拉的 URL
    fun coverUrl(track: MediaTrack, sizePx: Int = 300): String?
}
```

**关键：`search()` 只解析 BV 号，不联网搜索。**

```kotlin
// query 可能是：BV1GJ411x7h7 / https://www.bilibili.com/video/BV1GJ411x7h7?p=1 / 完整分享链接
// 提取 bvid + 可选 p 参数 → view API 拿 cid 和标题 → 返回单条 MediaTrack
// 提取不到 BV 号 → 返回 emptyList（不抛异常，界面提示"请粘贴 BV 号或视频链接"）
```

`MediaTrack` 的字段映射：

| 字段 | 值 |
|---|---|
| `id` | `"<bvid>:<cid>"` |
| `title` | view API 的 `title` |
| `artist` | view API 的 `owner.name`（UP 主） |
| `durationSec` | view API 的 `duration` |
| `providerId` | `"bilibili"` |
| `suffix` | `"m4a"` |
| `coverId` | view API 的 `pic`（直接是完整 URL） |

**`resolve()` 是重活，按这个顺序：**

```
1. playurl(fnval=16) 取 audio 里 bandwidth 最大的一条
2. 下载到 cacheDir/bili/<bvid>-<cid>.m4s（3-5MB）
3. remuxer.toPlayable(...) → cacheDir/bili/<bvid>-<cid>.m4a
4. localServer.baseUrl() + "/bili/<bvid>-<cid>.m4a"  ← 返回这个
5. 删掉 .m4s（省磁盘）
```

⚠️ **不要返回 B 站的直链** —— 那是 fMP4，设备播不了。

### 第 3 步 · 界面（约 50 行）

- 一个输入框：粘 BV 号 / 视频链接 → 解析 → 单条结果 → 点播放
- 复用 `docs/83` 的「统一搜索入口」：B 站作为一个源出现在分流里，但**只接受 BV 号**（搜关键词时返回空 + 一句提示）
- 播放走 `PlaybackRouter` / `startQueueOnDevice`，**一行不用改**

---

## 四、必须先确认的两件事（开工前 10 分钟）

### 4.1 `LocalMediaServer` 怎么暴露 remux 后的文件

现有 API（`core-protocol/.../media/LocalMediaServer.kt`）：

```kotlin
class LocalMediaServer(
    private val resolver: MediaResolver,      // fun resolve(path: String): File?
    private val bindAddress: String = "0.0.0.0",
    private val preferredPort: Int = 0,       // 0 = 系统分配
    private val advertiseHost: String? = null,
) {
    fun baseUrl(host: String? = null): String
    fun start(): Int
}
```

**所以只要让 `resolver` 能解析 `/bili/xxx.m4a` 到 `cacheDir/bili/xxx.m4a` 即可**，
不需要新增 API。服务已经支持 `HEAD` / `Range` / `206` / `Accept-Ranges`（`docs` 里写着这是 renderer 兼容性关键）。

### 4.2 谁负责持有 LocalMediaServer

现在 `QueuePlaybackService` 已经在跑本地推流的前台循环。**先确认它是持有 server 的那一方**，
如果是，B 站只是「多一个 resolver 分支」；如果不是，要决定谁来 start/stop。

---

## 五、坑（都会真实撞到）

| 坑 | 说明 |
|---|---|
| **必须 remux** | 忘了这一步 = 界面显示在播、音箱一声不响（`stop` + `totlen=0`） |
| **链接 120 分钟过期** | 不能缓存直链。缓存的是**remux 后的文件**，不是 URL |
| **`Content-Type` 是 `application/octet-stream`** | 已实测不是问题（本地服务可以给正确的 `audio/mp4`） |
| **磁盘** | 一首 3-5MB。**必须有上限 + LRU**（建议 500MB），否则几个月塞满 |
| **remux 耗时未测** | ffmpeg 是 0.5 秒，**Media3 Transformer 在真机上多久没测过**。超过 2 秒要加进度提示 |
| **搜索被风控** | 只能 BV 输入，不要尝试关键词搜索 |
| **视频也要下载** | durl 的 mp4 直链带 Referer 也 403，别直投 |
| **多 P 视频** | `?p=2` 是不同的 cid，要取对应的 cid |
| **投流期间手机是服务器** | 锁屏不能断 —— `QueuePlaybackService` 已在做，但别让它被系统杀掉 |

---

## 六、最优实践 + 最短路径（开工前扫一眼）

### 6.1 这个项目已定下的最优实践（别再发明）

| # | 实践 | 一句话 |
|---|---|---|
| 1 | **先找官方实现** | remux 用 Media3 Transformer（官方 transmux），别自己解析 fMP4 |
| 2 | **单一入口 + 编译期强制** | `resolve()` 是唯一产出 URL 的地方，界面不许自己拼 |
| 3 | **断言对真值** | 判成功看**设备 `totlen > 0`**，不看界面文字 |
| 4 | **fixture 从真实响应抄** | B 站接口的 JSON 要真抓一份进单测，别照文档编（`docs/80` 的教训） |
| 5 | **不给按了没反应的按钮** | 搜关键词返回空时，界面要明说「只能粘 BV 号」，不是转圈 |
| 6 | **诊断台跟着长** | 新增一个 `source-bilibili` 诊断段：上次解析耗时 / 缓存占用 / remux 成功失败数 |

### 6.2 最短路径

| 要做 | ❌ 弯路 | ✅ 最短路径 |
|---|---|---|
| remux | 引 ffmpeg-kit / 自己写 MP4 解析 | `media3-transformer:1.5.1`，`-c copy` 等价物，80 行 |
| 搜索 | 补 buvid + cookie + wbi 签名去破解风控 | **不做**，只接受 BV 号 |
| 本地服务 | 新写一个 HTTP server | 复用 `LocalMediaServer`，只加 resolver 分支 |
| 投送 | 自己拼 SOAP | 走现有 `startQueueOnDevice` / `PlaybackRouter` |
| 缓存 | 引 Room / DataStore 管元数据 | 文件名带 bvid，目录按 LRU 删，不需要数据库 |

### 6.3 明确是弯路的

- 为这个源开新 Gradle 模块（判据一条不命中）
- 用 ffmpeg-kit（+50MB，且 LGPL/GPL 变体）
- 试图做关键词搜索
- 直投 B 站 URL（fMP4，必失败）
- 缓存直链（120 分钟过期）

---

## 七、验收标准

- [ ] 粘 `BV1GJ411x7h7` → 显示标题和 UP 主 → 点播放 → **设备 `totlen > 0` 且 `curpos` 递增**
- [ ] 缓存目录里只有 `.m4a`，没有残留 `.m4s`
- [ ] 同一个视频第二次播放**不重新下载**（命中缓存）
- [ ] 粘一段不是 BV 号的文字 → 明确提示「只能粘 BV 号或视频链接」，不转圈
- [ ] 诊断页能看到 `source-bilibili` 段（解析耗时 / 缓存占用）
- [ ] `./gradlew test` 全绿，且新增用例覆盖：BV 号提取（各种链接形态）、remux 失败路径、缓存命中

---

## 八、没验证的（诚实清单）

| 项 | 状态 |
|---|---|
| **Media3 Transformer 在真机上的 remux 耗时** | ❌ 未测（ffmpeg 是 0.5 秒） |
| **Media3 Transformer 能不能吃 B 站的 fMP4** | ❌ 未测。Media3 有 `FragmentedMp4Extractor`，理论上能，但**必须在真机上验** |
| **投电视盒子（`.175`）** | ❌ 从未真推过流（只做过只读探测） |
| **YouTube** | ❌ 本文不涉及。不依赖 NAS 就得接受 GPL + 200MB，那是另一个决策 |
| **多 P 视频 / 番剧 / 直播** | ❌ 只验证了普通投稿视频 |

**第 2 条是最大风险**：如果 Media3 Transformer 吃不了 B 站的 fMP4，就得退回
`MediaExtractor + MediaMuxer`（Android 框架自带，零依赖，但要自己处理 fMP4）。
**这一步应该先做，10 分钟就能出结论**——拿现成的 `bili_raw.m4s` 喂进去试。

---

## 九、一句话

**加一个 `MediaProvider` 实现类就够（不开模块）；难点不在取直链（免鉴权，206 就通），而在 remux——B 站给的是 fMP4，DOSS 只认普通 MP4/M4A，用 Media3 Transformer 转一道（0.5 秒级，Apache-2.0，同版本已在依赖里）。先花 10 分钟验证 Transformer 能不能吃 fMP4，再写那 300 行。**
