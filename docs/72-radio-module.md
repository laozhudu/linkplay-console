# 72 · 网络电台也做成独立模块：能，而且比洛雪简单

> 2026-11-04 · 依据 `laozhudu/radiostation-discovery` v1.0.0 源码 + 实测
> 本文是**模块方案 + 三个实测缺陷**；模块已在 `core-radio` 落地（见文末「已实施」）

---

## 零、结论

**能做，而且不用像洛雪那样造运行时。**

| | 洛雪（`docs/71`） | 电台（本文） |
|---|---|---|
| 原物是什么 | React Native App（Hermes 字节码） | **纯 Kotlin/JVM 库** |
| 能不能直接当依赖 | ❌ 不能，只能抄源码 | ✅ **本来就是库** |
| 要不要造运行时 | ✅ 要（QuickJS + 10 个 shim） | ❌ **不用** |
| 模块里放什么 | 引擎 + 桥 + 两个 JS 载荷 | **源码 + 一个适配层** |
| 工期 | 2-4 周 | **2-3 天** |

**但"独立成模块"这个判断是对的，理由和之前不一样** —— 见第二节。

---

## 一、先认一件事：我 `docs/69` 说的"移植两个类进 core-protocol"是错的

`docs/69` 我写的是：

> 电台不用开模块，把 `StreamResolver` + `RadioBrowserClient` 移植进 `core-protocol/radio/`

**错在这个地方**：`core-protocol` 是**零运行时依赖**的纯 JVM 底座（`build.gradle.kts` 里协程都只放在 `testImplementation`）。
而这个库带两个真依赖：

```kotlin
implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
```

**只要 `core-protocol` 引了它，底座就不纯了** —— 和 `docs/69` 里 Media3 那个判据完全同一回事。

**所以它确实该独立成模块。** 你这条判断是对的，我上一轮的结论要改。

---

## 二、判据重算

| 候选 | 判据1（拖依赖） | 判据2（第二消费者） | 判据3（能独立发版） | 结论 |
|---|---|---|---|---|
| `core-playback`（Media3） | ✅ Android 库 | ⚠️ | ❌ | 开（`docs/69`） |
| `core-lx`（QuickJS） | ✅ JNI + .so | ❌ | ❌ | 开（`docs/71`） |
| **`core-radio`** | ✅ **serialization + coroutines** | ⚠️ 独立仓库本身就是消费者 | ✅ 本来就是独立库 | **开** |
| tt-music 客户端 | ❌ 只要 JDK | ❌ | ❌ | 不开 |

**三个模块，三条不同的判据 1**：

```
core-playback  ← Android SDK + Media3
core-lx        ← QuickJS（JNI + .so）
core-radio     ← kotlinx-serialization + kotlinx-coroutines
```

共同点：**它们都拖不进零依赖的底座**。

---

## 三、模块形状

```
core-radio   ★新   纯 JVM（不带 Android）
├── io/github/laozhudu/radiodiscovery/      ← 库源码原样放，包名不动
│   ├── RadioStationDiscovery.kt
│   ├── client/{RadioBrowserClient, RadioBrowserRaw, HttpClient}.kt
│   ├── resolver/{StreamResolver, ResolvedStream}.kt
│   └── model/RadioStation.kt
└── io/github/laozhudu/linkplay/radio/
    └── RadioBrowserSource.kt               ← 适配层：库模型 → 我们的 RadioStation
```

依赖：

```
core-radio → kotlinx-serialization-json
core-radio → kotlinx-coroutines-core
core-radio → core-protocol（只要 RadioStation 那个数据类）
core-radio → core-diagnostics（可选，记搜索失败）
```

**包名不动**，源码零改动 —— 这是"洛雪方法"的精髓：**原样放，别改写**。
唯一新增的是适配层一个文件。

### 为什么要有适配层（两个 `RadioStation`）

| | `core-protocol/radio/RadioStation` | 库的 `RadioStation` |
|---|---|---|
| 语义 | **用户存的台**（原厂 `tb_net_radio`） | **目录查到的台**（还没入库） |
| 字段 | `name / url / kept / playing / invalid / deviceName / id` | `id / name / url / resolvedUrl / country / codec / tags / favicon / bitrate / isHls / votes` |
| 谁持久化 | `RadioStationStore` | 无 |

**两个模型都该存在，不是重复**：一个是"我的收藏"，一个是"搜索结果的 DTO"。
适配层只做一件事：把搜索结果转成能加进收藏的形状。

```kotlin
// RadioBrowserSource.kt（约 60 行）
fun toStation(lib: LibRadioStation): RadioStation =
    RadioStation(
        name = lib.name,
        url = lib.resolvedUrl ?: lib.url,   // 优先用解析后的真实地址
        deviceName = null,
    )
```

---

## 四、三个接法对比

| 接法 | 做法 | 优点 | 缺点 |
|---|---|---|---|
| **A. 源码放进 `core-radio`**（推荐） | 库源码复制进模块，包名不动 | CI 不依赖私有仓；改动可回推上游 | 两份拷贝，要手动同步 |
| B. `includeBuild` 复合构建 | 本地 clone 路径引用 | 单一真相 | CI 要能拉到私有仓（token + submodule） |
| C. 发布到 Maven | 给库加 `maven-publish`，发 GitHub Packages | 最干净 | 要多维护一条发布链 |

**现在库还没有 `maven-publish` 插件**（`build.gradle.kts` 里没有），所以 C 要先补。

**建议 A**：项目是个人自用、CI 在 GitHub Actions 上，私有仓拉取会带来 token 配置；
先把源码放进来跑通，等库稳定了再考虑 B/C。

**同步纪律**：`core-radio` 里每个文件头加一行
`// 来自 laozhudu/radiostation-discovery@<commit>，改动请回推上游`，
否则半年后没人知道哪份是正的。

---

## 五、实测发现的两个缺陷（采用前必须处理）

### 缺陷 1：`searchByPlace` 国家名大小写敏感 → 小写直接返回 0

实测（Radio Browser 真实 API）：

| 请求 | 结果 |
|---|---|
| `country=china` | **0 条** |
| `country=China` | 100 条 |
| `countrycode=CN` | 100 条 |
| `country=china&state=guangdong` | **0 条** |
| `country=China&state=Guangdong` | 6 条 |

库的 `searchByPlace` 是**原样拼参数**：

```kotlin
val url = "$baseUrl/json/stations/search?country=$countryEnc&state=$stateEnc&limit=$limit"
```

所以调用方传小写就静默返回空 —— **界面会显示"没有找到电台"，而不是报错**。
这正是项目铁律里"不许给假空态"要防的情况。

**修法**：适配层改成用 `countrycode`（大小写不敏感、无歧义），或者调用前做首字母大写。

### 缺陷 2：`JdkHttpClient` 用 `readText()` 读直播流 → 阻塞 8 秒后抛异常

```kotlin
val body = if (connection.inputStream != null) {
    connection.inputStream.bufferedReader().readText()   // ← 直播流永远没有 EOF
} else ""
```

实测（Java 17 复刻同样逻辑）：

```
URL: http://stream.radioparadise.com/mp3-192
  首行耗时 730ms，长度 65 → 流是无限长的
URL: https://lhttp.qtfm.cn/live/5022308/64k.mp3
  首行耗时 1262ms，长度 16 → 流是无限长的
```

**后果很讽刺**：`StreamResolver` 对**需要解析的跳转页**能正常工作，
但对**本来就能直接播的直播流**会读到 `readTimeout`（8 秒）然后抛异常。

也就是说：**点了本来不用解析的台，反而卡 8 秒然后失败。**

**修法**（三选一）：
- 用 `HEAD` 请求（大多数流服务器支持）
- 或者只读 header，`getInputStream()` 之后立刻 `disconnect()`，不读 body
- 或者 `readNBytes(4096)` 限长读

**推荐第二条**：`StreamResolver` 只关心 `Location` 和 `Content-Type`，根本不需要 body。

---

## 六、施工顺序

| 步 | 做什么 | 验收 |
|---|---|---|
| 1 | 建 `core-radio` 模块，源码原样放进去，加 2 个依赖 | `./gradlew :core-radio:test` 能跑（库自带 4 个测试文件） |
| 2 | 修缺陷 2（只读 header 不读 body） | 对直连流调用 `resolveStream` 不再卡 8 秒 |
| 3 | 写 `RadioBrowserSource` 适配层，用 `countrycode` | 搜"中国"能出结果，搜"china"也能出 |
| 4 | 界面接"发现电台"入口（现有 `RadioScreen` 加一栏） | 搜到的台能一键加进收藏 |
| 5 | 诊断段：搜索耗时/条数/失败原因 | 报告里能看出是网络挂了还是查无结果 |

**第 2、3 步是必须的**，不做就是带着两个已知缺陷上线。

---

## 七、和现有电台代码的关系

现在电台分两处：

| 位置 | 是什么 | 动吗 |
|---|---|---|
| `core-protocol/radio/RadioStation.kt` | 模型 + 校验 + 内置 5 个已验证台 | **不动** |
| `app/radio/`（VM 295 + Screen 405 + Status 155） | 界面 + 状态 | 加"发现"入口 |

`core-radio` 只提供**"从目录找台"**这一个能力，不碰现有收藏/播放逻辑。

**现有 5 个内置台回归 5/5 全过**（2026-11-04 实测），改完要重跑这 5 条。

---

## 八、模块总数更新

```
core-protocol     纯 JVM 零依赖      ← 底座，不动
core-diagnostics  纯 JVM 零依赖
core-discovery    纯 JVM 零依赖
core-radio      ★ 纯 JVM + 2 依赖     ← 本文
core-lx         ★ Android + QuickJS   ← docs/71
core-playback   ★ Android + Media3    ← docs/69
app                                   ← 界面
```

**从 4 个到 7 个**，正好落在 `docs/48` 定的"7 个而非 20+"上。

**每个新增模块都有明确的判据 1**，不是拍脑袋。

---

## 九、风险

| 风险 | 影响 | 缓解 |
|---|---|---|
| 两份源码漂移 | `core-radio` 里的和上游不一致 | 文件头标 commit；改动回推上游 |
| 45% 的台是 HLS，只有 37% 能播 | 搜到的台一半点不动 | 界面上标"可能不支持"，或点播前探测（`docs/68` 已定） |
| Radio Browser 是公益服务 | 可能限流/挂 | 缓存 + 多镜像（`de1` / `all` 两个域名都可用，已实测） |
| 库没有 `maven-publish` | 想走方案 C 要先补 | 先用方案 A |

---

## 十、一句话

**能，而且这次是"直接搬"——库本来就是纯 Kotlin，不像洛雪那样要造运行时。**

但**搬之前有两个实测缺陷要修**：
1. `searchByPlace` 国家名大小写敏感，小写静默返回空（改成 `countrycode`）
2. `JdkHttpClient` 读直播流会阻塞 8 秒（只读 header 别读 body）

修完这两条，两三天就能接上。


---

## 十一、已实施（`core-radio` 模块）

方案已按本文落地为 `core-radio` 模块，**43 个单测 + 5 个真实联调用例全绿**。

### 落地时又撞到第三个缺陷

联调（真打 Radio Browser API）时发现：

```json
{"name":"...","hls":1,"lastcheckok":1,...}
```

`hls` 返回的是**数字** `1` / `0`，而模型声明的是 `Boolean?`。kotlinx.serialization 遇到 `1` 抛

```
JsonDecodingException: Expected valid boolean literal prefix, but had '1'
```

**后果不是"这个台被跳过"，而是整批搜索结果解析失败** —— 只要列表里有一个 `hls:1` 的台，
用户就看到"搜不到任何电台"。

单测没抓到，因为 fixture 写的是 `true`（照文档猜的，没照真实响应抄）。**教训：fixture 必须从真实响应里抄。**

修法：
- `FlexibleBooleanSerializer` 接受 `true`/`false`/`1`/`0`/`"1"`/`"0"`
- `RadioBrowserRaw` **所有字段都给默认值** —— kotlinx 里"可空"不等于"可选"，缺 key 照样抛
  `MissingFieldException`，而公益众包 API 的字段并不保证齐全

### 真实联调证据（`LIVE_RADIOBROWSER=1`）

```
china → 20 条，China → 20 条          ← 缺陷 1 修复前：china 是 0 条
countrycode=CN 返回 20 条
name=中国之声 → 6 条
china/guangdong → 6 条
解析 https://vip1.fhbf9.com/.../index.m3u8 耗时 1288ms   ← 缺陷 2 修复前：8000ms 超时抛异常
```

### 模块结构（实际）

```
core-radio  纯 JVM，43 单测
├── io/github/laozhudu/radiodiscovery/        库源码（包名不动）
│   ├── client/{HttpClient, RadioBrowserClient, RadioBrowserRaw, FlexibleBooleanSerializer}
│   ├── resolver/{StreamResolver, ResolvedStream}
│   └── model/RadioStation
└── io/github/laozhudu/linkplay/radio/
    ├── RadioBrowserSource.kt                  门面（可注入 HttpClient 便于测试）
    └── RadioStationMapping.kt                 toFavourite / streamKindOf / playbackHint
```

### 三个缺陷的回归测试都在

| 缺陷 | 回归测试 |
|---|---|
| 1 国家名大小写 | `RadioBrowserClientTest` 4 个 + `RadioBrowserLiveIT` 2 个 |
| 2 读直播流阻塞 | `StreamResolverTest.解析时不读 body` + `JdkHttpClientTest` 3 个（本地假直播流服务器） |
| 3 `hls` 是数字 | `FlexibleBooleanTest` 6 个 |
