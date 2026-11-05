# 42 · 六项议题的结论与待处理清单

本轮回答六件事，其中 1~3 已落地代码，4~6 是现状盘点与接入方案（需要你点头再动）。

---

## 1. 诊断页升级为全 App 监测（**已落地**）

### 原来只覆盖一半

第一版 `DiagnosticsReport` 只读 `ConsoleUiState`（设备/电量/声道）+ 崩溃 + 问题日志。
但用户真正会报的问题散在各处：**播放起不来、队列串台、电台不响、在线曲库搜不到、悬浮窗不显示** ——
这些状态都在各自 ViewModel 里，诊断页拿不到，于是又回到"你描述现象我猜"。

### 现在：注册表模式

新增 `diagnostics/DiagnosticsHub`：

```kotlin
// 谁有状态，谁自己渲染一段
DiagnosticsHub.register("playback", "播放") {
    val s = _state.value
    "  通道=${s.channel}  传输态=${s.state}  设备队列=${s.deviceQueueCount} 首…"
}
```

已注册四段：

| 段 | 来源 | 能看到什么 |
|---|---|---|
| 设备 | `ConsoleViewModel` | 每台的在线/组归属/能力档/音量/声道/电量/播放/**取数通道** |
| 播放 | `PlaybackViewModel` | 引擎通道（UPnP/HTTP）、输入源、传输态、设备队列位置、封面来源、歌词长度、起播失败原因 |
| 队列 | `QueueBus` | **归属 IP**、是否设备持有、共几首/第几首、来源说明、偏移、控制丢失 |
| 后台服务 | `ActivityManager` | 队列播放/本地媒体服务/悬浮控制 三个服务**真的在不在跑** |

三条设计约束（都有单测）：

1. **顺序稳定**：按注册先后渲染，截图每次位置一致才能对照。
2. **单段失败不影响其他段**：某个 provider 抛异常只把那一行换成"渲染失败"，其余照常。
   诊断工具自己崩掉，比被诊断的功能崩掉更难查。
3. **必须能注销**：`PlaybackViewModel` 切设备会重建，旧实例残留会显示**上一台**的状态
   （比没有更误导），还会泄漏它持有的 Context。所以在 `onCleared` 里 `unregister`。

### 还差的（下一轮）

- **请求追踪**：在 `DeviceHttpClient` / `UpnpTransport` 上挂 hook，记录最近 50 次
  `命令 / 耗时 / 状态码 / 失败原因`。这是定位"为什么慢、为什么时好时坏"的最后一环。
- **主动自检**：一键对每台设备跑 6 项（ping / 80 / 49152 / description / getStatusEx / slaveList），
  输出 ✅❌ + 耗时。现在只能被动看"哪条通道答了"，自检能主动告诉"哪条不通"。

---

## 2. 拖拽组网已删除（**已落地**）

删干净了，不是隐藏入口：

| 删除项 | 位置 |
|---|---|
| `DragGrouping` 整个对象（判据 + hitTest） | `core-protocol/multiroom/DragGrouping.kt` |
| 它的 11 个单测 | `DragGroupingTest.kt` |
| `ConsoleViewModel.groupByDrag()` | 约 90 行，含"主机由松手位置决定"那套逻辑 |
| `DraggableDeviceCard` 整个外壳（长按 300ms / graphicsLayer 平移放大 / 悬停绿边 / 命中判定） | `MainActivity.kt` |
| `DragState` 类 | 同上 |
| `dragState` / `centers` / `hitRadiusPx` / `LaunchedEffect` 命中计算 | 同上 |
| 6 个不再使用的 import（`pointerInput`、`detectDragGesturesAfterLongPress`、`boundsInRoot`…） | 同上 |

文案同步清理：

- ~~「点 ⧉ 勾选要同步的音箱；也可以长按卡片拖到另一台上」~~ → **「点 ⧉ 勾选要同步的音箱」**
- ~~「长按设备卡片可以移除记录」~~ → 整行删除（长按已不存在，移除在齿轮菜单里）
- 注释里的"拖拽组网能直接成组"改成"勾选式组网（⧉）能直接成组"

`DeviceCardRow` 的 `dimmed` 参数（拖拽时压暗）也一并删掉了 —— 它只服务拖拽。
**唯一的组网方式是首页右上角 ⧉ 的勾选面板**（Sonos Grouping 菜单那套）。

净减约 6.4KB 代码 + 11 个单测。

---

## 3. 应用名：**声控台**（**已落地**）

`app/src/main/res/values/strings.xml` → `<string name="app_name">声控台</string>`

取这个名的理由：

- **声**（音频）+ **控**（遥控）+ **台**（控制台，也指"多台设备"）
- 三个字，中文里好念好记；不会和"某某音乐"混淆 —— 这**不是播放器，是遥控器**
- 与项目代号 `linkplay-console` 的 console 对应，直译就是"声控台"

备选（要换只改这一行）：**一屋声**（强调多房间同步）、**音箱管家**（最直白，但"管家"偏土）。

诊断报告标题也从 `=== LinkPlay Console 诊断报告 ===` 改成 `=== 声控台 诊断报告 ===`。
包名 `io.github.laozhudu.linkplay.console` **不改**（改了就是另一个 App，无法覆盖安装）。

---

## 4. 通用 DLNA 进度：判定和读状态完成，**投流从未真机验证**

| 能力 | 状态 | 说明 |
|---|---|---|
| 判档（LINKPLAY / GENERIC_DMR / UNKNOWN） | ✅ 完成 | `DeviceProfile.detect()`，判据从描述读，不猜型号 |
| 能力位（8 项） | ✅ 完成 | `DeviceCapabilities`：PlayQueue / multiroom / presets / httpapi / volume / seek / nextPrev / … |
| 读状态 | ✅ 完成 | `GetTransportInfo` + `GetPositionInfo` + `GetVolume` 三连，缺项就少项 |
| 音量/静音 | ✅ 完成 | 标准 `RenderingControl` |
| 界面按档收敛 | ✅ 完成 | 盒子不渲染组网/声道/屏蔽/预设（铁律：不给按了没反应的按钮） |
| **投流（SetAVTransportURI）** | ⚠️ **代码在，从未真推过** | `.175` 电视盒子只做了只读探测 —— 推流会让电视真的响，等你点头 |
| 整列表 | ❌ 不支持 | 盒子没有 PlayQueue，只能客户端逐首投流（`QueueStarter` 已按档跳过） |
| 上一首/下一首 | ❌ 不可用 | 设备没有自己的队列，`AVTransport.Next` 只会回 701 |
| 进度条 seek | ⚠️ 未验证 | `AVTransport.Seek` 标准动作，理论上可用 |

**一句话**：判定层和读状态层已经能用了，投流是"写完没跑过"。要验证只需要你一句话
（推一首给 `.175`，电视会出声几秒）。

---

## 5. 音源进度：框架全通，**卡在 tt-music 没扫库**

| 音源 | 状态 | 说明 |
|---|---|---|
| 本地音乐 | ✅ 可用 | 手机媒体库，`LocalLibrary` + 本机 HTTP 服务投流 |
| NAS DLNA | ✅ 可用 | `.223:8200`（fnOS/MiniDLNA）1931 首，真机验证过 |
| 网络电台 | ✅ 可用 | 内置 5 个实测可用 + 手动加 + 播过自动入库 |
| 在线曲库（Subsonic） | ⚠️ **框架通了，没内容** | `MediaProvider` 接口 + `SubsonicClient`（token 认证）完成，LiveIT 通过；但 **tt-music 曲库为空** |

`tt-music`（`192.168.99.124:9527`，`penhaijun/lx-music-server` = Navidrome 分支）：
`ping.view` → `{"status":"ok","version":"1.16.1","openSubsonic":true}`，
但 `getAlbumList2` → `album: []`。

**唯一的阻塞**是在 `.124` 的 tt-music 界面里扫一遍媒体目录（`/vol2/1000/ttmusic/music/local_lib`）。
这不是 App 侧的事，扫完在线曲库立刻可用。

设计上已经定案：**遥控器 + 检索层**（不重写成完整客户端）——
内容交给 Subsonic 服务端，播放仍走我们的设备链路（PlayQueue / 多房间 / 声道全保留）。

---

## 6. 私人仓库的电台模块：**能用**，但有一条硬约束

仓库：`laozhudu/radiostation-discovery`（private，MIT，纯 Kotlin/JVM）

### 它做什么

```
RadioStationDiscovery
├── search(query, limit)              按名称搜
├── searchByPlace(country, state)     按地区搜
├── getPopular(limit)                 热门
└── resolveStream(station)            跟随 302 把中间页解析成真实流地址
```

数据源默认 [Radio Browser](https://www.radio-browser.info) 公开 API；
`RadioStationSource` 接口可换成任何目录。零 Android 依赖、零第三方依赖（用 JDK HttpURLConnection）。

### 我实测了什么

```
https://de1.api.radio-browser.info   → 200 (1.39s)
https://all.api.radio-browser.info   → 200 (1.23s)
https://nl1.api.radio-browser.info   → 000（不通，正常，任一节点可用即可）
```

真实返回（按点击量排的中国电台）：

```
凤凰卫视资讯台     http://playtv-live.ifeng.com/live/06OLEEWQKN4_audio.m3u8   hls=1
CCTV-13新闻伴音    https://piccpndali.v.myalicdn.com/audio/cctv13_2.m3u8     hls=1
怀集音乐之声       https://lhttp.qingting.fm/live/4804/64k.mp3                MP3
```

### ~~硬约束：音箱不认 https~~ ⚠️ **本节结论已推翻，勿再引用**

> **更正（`docs/85`）**：音箱**能播 https**。后续实测 `https://icecast.radiofrance.fr/fip-hifi.aac`、
> `https://ngcdn001.cnr.cn/live/zgzs/index.m3u8` 在 `.164` 上都是 `play`。
> 当时那次 `lhttp.qingting.fm` 的 https 没播，是**那个站的问题**，不是设备不认 https。
> 由此「必须中继转发才能播 https」整套方案作废。
> 保留本节只为记录当时的判断过程，**下面按 scheme 过滤 https 的做法不要再用**。
>
> 另：HLS 本身没问题 —— 音箱播 m3u8 在 `docs/19` 就已实测通过。

`.164` 上逐个投流看 `status`：

| 流 | 结果 |
|---|---|
| `http://ngcdn001.cnr.cn/live/zgzs/index.m3u8` | **play**（curpos 递增） |
| `http://stream.radioparadise.com/mp3-192` | **play** |
| `https://lhttp.qingting.fm/live/4804/64k.mp3` | **pause**（命令回 OK 但没播起来） |

结论：**http 可以（含 m3u8），https 不行**。这与之前实测 SomaFM / BBC https 失败一致。
所以接入时必须按 scheme 过滤或标记，不能把 https 条目直接丢给音箱 ——
否则用户点了"没反应"，正是本项目禁止的体验。

### 怎么接入（三步，工作量约 1 天）

1. **复制模块源码进本仓**（不改 git 依赖）：
   `core-radiodiscovery/` 作为独立 Gradle module。
   理由：私有仓走 JitPack 要额外配 token，而这是你自己的 MIT 代码，复制过来最省事；
   也便于按音箱的约束改（例如加 `playableByDevice` 过滤）。

2. **适配层**：`radiodiscovery.RadioStation` → 本项目 `RadioStation`（本地库模型）
   - `name` ← `name`
   - `url` ← `resolvedUrl ?: url`（**必须先 resolve**，音箱只认直链、不跟 302）
   - 丢掉 https 条目或标注"需代理"
   - 保留 `country / tags / bitrate` 作为展示

3. **界面**：现有电台页加一个「在线搜索」入口（与"内置电台""手动添加"并列），
   搜索结果点一下就 `resolveStream` 然后投流，成功后自动入库（现有 `autoSave` 已经这么干）。

**要注意的两点**：

- `resolveStream` 只跟随重定向，**不解决 https**。https 条目要么跳过，
  要么走我们已有的 `RelayMediaServer`（它能把 https 上游转成 http 给音箱，代码已有，但未接电台）。
- HLS 条目占比很高（搜"中国"前两条都是 m3u8）。好消息是音箱**能播 m3u8**（CNR 已验证），
  坏消息是 https 的 m3u8 不行 —— 过滤条件要 `scheme == http`，不是 `codec != HLS`。

---

## 待处理清单

### P0（下一轮直接做）

| # | 事项 | 依据 |
|---|---|---|
| 1 | 一致性审计 P1：uuid 存储归一 + 队列 owner 强制 | `docs/41`，两个真 bug |
| 2 | 诊断加「请求追踪」+「一键自检」 | 本轮设计已留好位置（Hub） |

### P1（需要你点头）

| # | 事项 | 需要你做什么 |
|---|---|---|
| 3 | 电台模块接入（复制源码 + 适配层 + 在线搜索） | 确认可以复制私有仓代码进本项目 |
| 4 | 通用 DLNA 真机投流验证 | 同意往 `.175` 电视盒子推一首（会出声几秒） |
| 5 | tt-music 扫库 | 在 `.124` 界面里扫 `/vol2/1000/ttmusic/music/local_lib` |

### P2（打磨）

| # | 事项 | 依据 |
|---|---|---|
| 6 | 一致性审计 P3：错误文案翻译 + 时长格式化统一 + 未知语义 | `docs/41` |
| 7 | 音量统一入口（音量键也走 UPnP 优先） | `docs/41` #3 |
| 8 | 能力档只存一份（并进 DeviceSnapshot） | `docs/41` #4 |
