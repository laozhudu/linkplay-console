# 74 · 交接：新增 core-radio 与 core-lx 两个模块（给开发对话）

> 写于 2026-11-04 · 依据 main `37b8442`
> **这份文档是给"负责写代码那个对话"看的。** 只读前三节就能干活。

---

## 零、三行摘要

1. 仓里多了**两个模块**：`core-radio`（网络电台目录）、`core-lx`（洛雪脚本引擎 + 音乐搜索）。
2. **这两个模块你没参与，也不用改**。`app` 目前**还没依赖它们**，所以现在的 APK 行为一点没变。
3. 你要做的只有一件事：**在界面上把它们接进来**（入口、页面、诊断段）。下面第五节是清单。

---

## 一、发生了什么

| PR | 内容 | 状态 |
|---|---|---|
| #93 | 新增 `core-radio` 模块（电台目录） | 已合入 `89744a7` |
| #95 | 新增 `core-lx` 模块（洛雪脚本引擎 + musicSdk） | 已合入 `37b8442` |

两个都是**纯新增**，没有改动任何现有文件的功能逻辑。改动过的现有文件只有两个：

| 文件 | 改了什么 |
|---|---|
| `settings.gradle.kts` | 末尾 `include(...)` 多了两个模块名 |
| `gradle/libs.versions.toml` | 多了 4 个版本号 / 3 个依赖（serialization、coroutines-test、quickjs） |
| `.github/workflows/android.yml` | 单测那行多了 `:core-radio:test` 和 `:core-lx:testDebugUnitTest` |

**`app/` 下的代码我一行没动。** 你手上 `fix/group-alignment` 那 7 个文件的改动不受影响
（已用 `git merge-tree` 预演过，和新 main 零冲突）。

---

## 二、两个模块各能干什么

### `core-radio` —— 从全球电台目录找台

```kotlin
val source = RadioBrowserSource()

// 按名字搜（中英文都行）
val list = source.search("中国之声", limit = 20)

// 按国家代码搜（推荐，大小写不敏感）
val cn = source.searchByCountryCode("CN", limit = 50)

// 热门
val hot = source.popular(limit = 30)

// 解析真实流地址（跟随 302）
val resolved = source.resolve(list.first())
```

结果类型是库自己的 `RadioStation`（带 `country` / `codec` / `isHls` / `votes`）。
要存进收藏，用扩展函数转成我们的模型：

```kotlin
import io.github.laozhudu.linkplay.radio.toFavourite
import io.github.laozhudu.linkplay.radio.playbackHint

val favourite: RadioStation = resolved.toFavourite(deviceName = "doss1")
val hint: String? = resolved.playbackHint()   // HLS 时返回"HLS 流，部分音箱播不了"
```

### `core-lx` —— 搜歌 + 拿播放直链

```kotlin
// 搜索（五个平台：wy / kw / tx / mg / kg）
val sdk = LxMusicSdk(QuickJsEngine(), LxAssets.musicSdk(context))
sdk.load()
val page = sdk.search("wy", "周杰伦", page = 1, limit = 30)
page.value?.tracks?.forEach { println("${it.name} - ${it.singer} / ${it.songmid}") }

// 拿直链（需要用户导入一个洛雪自定义源脚本）
val api = LxUserApi(QuickJsEngine(), LxAssets.preload(context))
val info = LxScriptInfo.parse(scriptText) ?: return   // 头部没有 @name 就不是洛雪脚本
val init = api.load(scriptText, info)
if (init.ok) {
    val url = api.musicUrl("wy", track.toMusicInfoJson(), quality = "320k")
    url.value  // https://...
}
```

**关键分工**：`LxMusicSdk` 只负责**搜**，`LxUserApi` 只负责**出直链**。
洛雪 2.x 把内置取流关掉了，所以两个必须一起用。

---

## 三、三条硬规则（不遵守会出问题）

### 1. 绝对不要用 tt-music 的投送

`/api/upnp/play`、`/api/dlna/playlist` 那些**看着能用**（实测投 DOSS 成功、播完自动下一首），
但一旦用了：

| 后果 | 原因 |
|---|---|
| 绕过 `QueueStarter` / `PlayQueue` / `QueueBus` | 队列跑到服务端去，设备上的队列就空了 |
| 多房间直接失效 | tt-music 不支持 DOSS 组网 |
| 队列归属无法表达 | 我们的 `owner: OutputTarget` 语义它没有 |

**只取「搜索 + 解析直链」，投送永远走我们自己的链路。**

### 2. `core-radio` 的 `RadioStation` 不要和我们的合并

两个同名模型语义不同：

| | `core-protocol/radio/RadioStation` | `core-radio` 里那个 |
|---|---|---|
| 是什么 | **用户存的台**（原厂 `tb_net_radio`） | **目录查到的候选** |
| 字段 | `kept` / `playing` / `invalid` / `deviceName` | `country` / `codec` / `isHls` / `votes` |
| 谁持久化 | `RadioStationStore` | 无（是 DTO） |

转换只走 `toFavourite()` 一个口子。

### 3. `core-protocol` 不许依赖这两个新模块

底座必须保持**纯 JVM 零运行时依赖**（要 publish）。依赖方向只能是：

```
app → core-radio / core-lx → core-protocol
core-protocol ✗→ core-radio / core-lx     ← 禁止
```

---

## 四、我踩过的坑（你可以直接避开）

### `core-radio`

| 坑 | 症状 | 已修 |
|---|---|---|
| 国家名大小写敏感 | `searchByPlace("china")` 返回 **0 条**，界面显示"没找到"而不是报错 | 已自动规范化，两个字母的走 `countrycode` |
| 读直播流阻塞 | 对本来就能直接播的台，卡 **8 秒**然后失败 | `StreamResolver` 改成只读响应头 |
| `hls` 字段是数字 | 真实响应是 `"hls":1`，模型声明 `Boolean?` → **整批搜索结果全挂** | 加了宽容解析器 |

### `core-lx`

| 坑 | 症状 |
|---|---|
| **两个方向都叫 `response` 但形状不同** | 按错的解析 → **永远拿不到结果**。JS→原生是 `{requestKey, status, result}`，原生→JS 是 `{requestKey, error, response}` |
| `AES/CBC/PKCS7Padding` | 标准 JDK 没有（BouncyCastle 的名字），已改 `PKCS5Padding`（对 16 字节块等价） |
| `toBodyJson` 用宽松 Json | 裸文本 `hello` 被当 JSON 解析成功 → JS 侧 `body.xxx` 取不到 |

---

## 五、你要接的东西（清单）

| # | 做什么 | 涉及文件 | 备注 |
|---|---|---|---|
| 1 | `app` 加依赖 `implementation(project(":core-radio"))` / `(":core-lx")` | `app/build.gradle.kts` | 不加就编译不到 |
| 2 | 电台页加"发现电台"入口（搜/热门 → 点一条 → 加进收藏） | `app/radio/RadioScreen.kt` | 用 `toFavourite()` 落地 |
| 3 | 在线曲库页接 `LxMusicSdk.search`（先只做 `wy`） | `app/online/OnlineScreen.kt` / `OnlineViewModel.kt` | 其他平台搜索能出结果但取不到直链 |
| 4 | 加"导入脚本"入口（粘贴文本或从文件选） | 新页面 | 用 `LxScriptInfo.parse()` 校验，头部没 `@name` 就拒 |
| 5 | 诊断段：脚本状态 / 搜索耗时 / 失败原因 | `app/ConsoleDiagnostics.kt` | 用 `LxFailureText.translate()` 翻人话 |
| 6 | 两处空态要能区分"没连上"和"没搜到" | 上述页面 | 铁律：不给假空态 |

**顺序建议**：先 1（依赖）→ 6（空态）→ 2（电台，最独立）→ 3/4/5（在线曲库，依赖脚本）。

---

## 六、我**没有**做的事（别以为已经好了）

| 事项 | 状态 |
|---|---|
| `app` 依赖这两个模块 | ❌ 没做 |
| 任何界面入口 | ❌ 没做 |
| 诊断段 | ❌ 没做 |
| **真机验证** | ❌ **最大的缺口** |

### 真机验证为什么没做

这台机器**没有 Android 设备也没有模拟器**，`wrapper-android` 的 so 只有 Android ABI，
JVM 跑不了 QuickJS。所以：

| 验过的 | 怎么验的 |
|---|---|
| bundle 能搜五个平台 | Node 假桥实跑：wy 277 / kw 3292 / tx 999 / mg 150 / kg 480 条 |
| 协议报文 / 超时 / 错误翻译 | 80 个 JVM 单测（假引擎） |
| 加密与 JDK 标准实现一致 | 交叉验证 |
| `core-radio` 真实 API | 5 个 LiveIT（`LIVE_RADIOBROWSER=1` 才跑） |

| **没验的** | 风险 |
|---|---|
| **QuickJS 里真的能跑** | `pako` / `buffer` 这些 polyfill 在 QuickJS 里可能和 Node 不一样 |
| 搜到歌 → 投送 → 出声 | 整条链路没串过 |

**接完第 1 步就应该先在真机跑一次**：

```kotlin
val sdk = LxMusicSdk(QuickJsEngine(), LxAssets.musicSdk(context))
sdk.load()
val r = sdk.search("wy", "周杰伦")
// 期望：r.ok == true 且 tracks 不为空；失败看 r.error
```

**这一步过了才继续做界面。** 不过就说明 QuickJS 里的 polyfill 有问题，得先修。

---

## 七、怎么验证你没把它改坏

```bash
# 单测（新增两个模块后一共 913 个 @Test）
./gradlew :core-protocol:test :core-diagnostics:test :core-radio:test \
          :core-lx:testDebugUnitTest :app:testDebugUnitTest

# 真实电台 API（需要外网）
LIVE_RADIOBROWSER=1 ./gradlew :core-radio:test --tests "*RadioBrowserLiveIT*"

# bundle 自检（改了 assets 才需要）
node scripts/verify-lx-bundle.js core-lx/src/main/assets/lx-music-sdk.bundle.js
```

---

## 八、文档索引（这几篇是新加的）

| 文档 | 内容 |
|---|---|
| `docs/69-module-boundaries.md` | 开模块的判据（拖第三方依赖才开） |
| `docs/70-userscript-engine.md` | 洛雪脚本引擎可行性 + 照抄清单 |
| `docs/71-lx-as-module.md` | 把洛雪整个做成模块的实测 |
| `docs/72-radio-module.md` | 电台模块方案 + 三个实测缺陷 |
| `docs/73-core-lx-implementation.md` | `core-lx` 落地细节 + 真机缺口 |
| **本文** | 给开发对话的交接 |

---

## 九、一句话

**你不需要理解这两个模块的内部，只需要知道"它们是两个内容源，接进来就行"。**
唯一要盯的是第六节那个真机验证 —— 那一步没过之前，别把界面做得太深。
