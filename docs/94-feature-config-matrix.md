# 盘点 · 全功能 × 全配置矩阵（UI 设计的前置）

> **用户 2026-11-05 指令**：「停一下，把软件全功能和全配置做个矩阵，再考虑 ui 设计和交互」
>
> **本文全部数据从代码扫出来的**（`grep` 提取，不是凭记忆）。
> 依据提交：`d5cfad5` 之后（含 Apple 风格基础件）

---

## 零、规模先说清

```
10 个 ViewModel
147 个公开动作（不含 private）
17 个页面
7 处持久化（3 个 SQLite + 1 个文本 + 3 个目录）
19 类诊断段
```

**147 个动作**，而页面只有 17 个 —— 这是"功能和界面对不上"的量化来源。

---

## 一、功能矩阵（按三个核心归类）

### A · 播放器（22 个动作）

| 动作 | 做什么 | 现在入口 | UI 状态 |
|---|---|---|---|
| `play` / `pause` / `stop` | 播放控制 | 播放页 + 悬浮窗 + 语音 | ✅ |
| `next` / `previous` | 切歌 | 播放页 + 悬浮窗 + 语音 | ✅ |
| `seekTo` | 拖进度 | 播放页 | ✅ |
| `setVolume` / `setVolumeLive` | 音量（定值/实时） | 播放页 + 音量键 | ⚠️ **两套，未统一** |
| `toggleMute` | 静音 | 播放页 | ✅ |
| `cycleLoopMode` | 循环模式 | 播放页 | ✅ |
| `cast` / `setCastUrl` / `setCastNeedsHeaders` / `setCastReferer` | 投流（含防盗链） | 播放页 | ✅ |
| `playQueueItem` / `removeQueueItem` | 队列操作 | 播放页 | ✅ |
| `loadEmbeddedTags` / `clearEmbeddedTags` | 读内嵌封面/歌词 | 自动 | ✅（不可见） |
| `switchSource` | 切输入源 | 播放页 | ⚠️ 设备侧，不确定用户用不用 |
| `switchTo` | 换设备 | 首页点设备 | ✅ |
| `toUiState` | 状态导出 | 内部 | — |

**缺口**：音量有 `setVolume` + `setVolumeLive` 两条路（`docs/90` S3.1 要收）。

### B · 内容源（65 个动作）

#### B1 · NAS（21）

`scan` `setManualHost` `connect` `connectManual` `resumeRemembered` `openDir` `up` `toggleEntry`
`selectAllPlayable` `clearSelection` `addSelectedToQueue` `clearQueue` `setLoopMode` `startQueue`
`playNow` `setSelecting` `resumePlayback` `restartRenderer` `stopQueue` `clearError`

**现在入口**：媒体库 → NAS（三层）

#### B2 · 本地曲库（15 = 8 播放 + 7 歌单）

`refreshAll` `setTab` `setQuery` `play` `openPlaylist` `createPlaylist` `addToPlaylist` `playPlaylist`
`removeFromPlaylist` `moveInPlaylist` `renamePlaylist` `deletePlaylist` `toggleFavorite` `pressPreset` `clearError`

**现在入口**：媒体库 → 音乐库

#### B3 · 在线曲库 Subsonic（7）

`setEditing` `updateConfig` `saveConfig` `setQuery` `search` `play` `clearNote`

**现在入口**：媒体库 → 在线曲库（还要先配服务器）

#### B4 · 电台（11）

`reload` `add` `addAllVerified` `remove` `play` `playUrl` `discover` `addDiscovered` `playDiscovered` `stop` `clearError`

**现在入口**：媒体库 → 电台

#### B5 · 洛雪（6）

`setSource` `search` `cancelPending` `onScriptImported` `play` `clearError`

**现在入口**：底部「洛雪」一级页

#### B6 · 本地文件队列（10）

`loadTracks` `toggle` `selectAll` `clearSelection` `setLoopMode` `pushSelected` `playNow` `repush` `stopServer` `clearError`

**现在入口**：媒体库 → 本地队列

#### B7 · 统一搜索（1）

`SearchHubViewModel`：一次问四个源

**现在入口**：媒体库 → 其它入口 → 找点东西听

### C · 遥控（42 个动作，全在 `ConsoleViewModel`）

| 分组 | 动作 | 现在入口 |
|---|---|---|
| 发现/添加 | `scan` `addManual` `add` `select` `forget` `open` | 首页 |
| 状态 | `refreshStatus` `refreshDevices` `refreshGroup` `renameCard` `renameDevice` | 首页（自动） |
| 音量 | `setCardVolume` `adjustVolumeByKey` `canHandleVolumeKey` | 首页卡片 + 音量键 |
| 播放 | `togglePlayAll` `pauseAll` `playAll` `toggleDevicePlay` | 首页 |
| 组网 | `openGrouping` `closeGrouping` `toggleGroupingDevice` `selectEverywhere` `applyGrouping` `toggleGroupingDevice` `ungroupCard` | 首页 → 组网 |
| 声道 | `setCardChannel` | 首页卡片 |
| 机身键 | `pressPreset` | 首页卡片 |
| 遮罩 | `toggleMask` | 首页卡片长按 |
| 固件 | `checkFirmware` `startFirmwareUpdate` | 设备设置 |
| 恢复出厂 | `restoreToDefault` `leaveDevice` | 设备设置 |
| 睡眠定时 | `setSleepTimer` `cancelSleepTimer` | 首页 |
| 原始命令 | `sendCommand` | **无 UI**（只在诊断里） |
| 诊断 | `diagnosticsReport` `clearDiagnostics` `lastCrash` `flushIssues` `clearLog` | 设备设置 → 日志 |

**注意**：`renameCard` 和 `renameDevice` 两个、`toggleGroupingDevice` 出现两次 —— 疑似重复入口。

### D · 系统（18 个动作）

| 分组 | 动作 | 入口 |
|---|---|---|
| 配网 | `scanNetworks` `selectAp` `useManualSsid` `setPassword` `setOpenNetwork` `submit` `back` `restart` `toggleShowPassword` | 设备设置 → 配网 |
| WPS | `acknowledgeApConnected` `setManualIp` `useManualIp` `findDevice` `onDeviceLocated` | 配网 → WPS |
| 语音 | 由 `VoiceController` + `IntentRouter` 驱动 | 播放页麦克风 |
| 悬浮窗 | `FloatingControlService.start/stop` | 设备设置 → 悬浮控制 |
| 脚本 | `ScriptLibrary` + `ImportScriptDialog` | 媒体库 → 脚本 |

---

## 二、配置矩阵（7 处持久化）

| # | 存在哪 | 存什么 | 现在从哪改 | 该从哪改 |
|---|---|---|---|---|
| 1 | **`SessionStore`（SQLite）** | 设备列表（uuid/名字/ip/location/型号）、上次播放会话、设备队列、遮罩状态、组网拓扑 | 首页 | 首页 + 设备详情 |
| 2 | **`LibraryStore`（SQLite）** | 收藏、最近播放、电台收藏 | 各源页面 | 各源页面 ✅ |
| 3 | **`PlaylistStore`（SQLite）** | 歌单（名字 + 条目 + 顺序） | 音乐库 | 音乐库 ✅ |
| 4 | **`playback-output.txt`** | **输出口**（`local` / `device:<ip>`） | 播放页 | 播放页 ✅ |
| 5 | **`filesDir/scripts/`** | 洛雪音源脚本 + `_active` 标记 | 脚本页 | 脚本页 ✅ |
| 6 | **`filesDir/diagnostics/`** | 诊断报告（滚动） | 诊断台 | 诊断台 ✅ |
| 7 | **`filesDir/crash/`** | 崩溃记录 | 诊断台 | 诊断台 ✅ |

### 配置项逐条（用户能改的）

| 配置 | 存哪 | 现在改的地方 | 粒度 |
|---|---|---|---|
| 设备名称 | #1 | 首页长按 / 设备设置 | 每台 |
| 设备 IP / location | #1 | 添加时 / 设备设置 | 每台 |
| 音量 | 设备侧（不落盘） | 首页卡片 / 播放页 | 每台 |
| 声道（立/左/右） | 设备侧 | 首页卡片 | 每台 |
| 遮罩（临时静音） | #1 | 首页长按 | 每台 |
| 组网关系 | 设备侧 | 首页组网 | 全局 |
| **输出口** | #4 | **播放页** | 全局（一个） |
| 循环模式 | #1（随会话） | 播放页 / NAS 页 / 队列页 | 每源（**三处各一份**） |
| 睡眠定时 | 内存 | 首页 | 全局 |
| 在线曲库（地址/账号/密码） | #1 | 在线曲库页 | 全局 |
| 洛雪脚本（启用哪支） | #5 | 脚本页 | 全局（**单值**） |
| 投流 Referer / Cookie | 内存 | 播放页 | 每次 |
| 主题（深/浅） | 系统跟随 | **无** | — |
| 悬浮窗开关 | 系统权限 | 设备设置 | 全局 |
| 备份/恢复 | #1~#7 全打包 | 设备设置 | 全局 |

### 配置层面的三个问题

1. **循环模式有三份**（播放页 / NAS / 队列各自记），同一件事三个真值
2. **洛雪脚本是单值**（`_active` 只能一支），多源要按源匹配（`docs/89` 提过）
3. **主题不可选**（只跟随系统），而播放页是强制深色 —— 不一致

---

## 三、缺口（这是矩阵最有用的一栏）

### 有功能，没有 UI 入口

| 功能 | 影响 |
|---|---|
| `sendCommand`（原始命令） | 只在诊断里能发，排障时想发一条得写代码 |
| `switchSource`（设备输入源切换） | 播放页有入口，但用户不知道它是干什么的（显示"输入源 DLNA 渲染器"） |
| 红外（`IrBlaster`） | 只有设备设置里一个"试发 NEC 电源键"按钮 |
| 蓝牙 / WiFi 遥控 | **一行代码都没有**（`docs/84`、`docs/85` 只有调研） |

### 有重复，该合并

| 重复 | 位置 |
|---|---|
| `renameCard` / `renameDevice` | `ConsoleViewModel` |
| `setVolume` / `setVolumeLive` | `PlaybackViewModel` |
| `pressPreset` | 同时存在于 `ConsoleViewModel` 和 `LibraryViewModel` |
| 循环模式三份 | 播放 / NAS / 队列 |
| `QueueItem(...)` 6 处 | 已收 2 处（S3.1） |

### 有页面，没有对应"用户动作"

| 页面 | 用户在这干什么（说不清就是问题） |
|---|---|
| `SourcesScreen`(46) | 选一个入口 —— **中转动作，不是使用动作** |
| `MyMusicScreen`(232) | 同上 |
| `DeviceContentScreen`(183) | 看设备上的列表 —— 和队列重复 |
| `WpsGuideScreen`(161) | 一次性流程单独占一页 |

---

## 四、结论：UI 该怎么排（用矩阵说话）

### 1. 按**动作**排，不按**源**排

现在：NAS 一个页面、在线一个页面、电台一个页面、洛雪一个页面、本地队列一个页面 —— **5 个页面做的事是同一件：找一条能放的资源**。

矩阵里这 65 个动作，用户视角只有 3 个：

```
找（搜/浏览） → 选（哪条） → 放（去哪个口）
```

**源是"筛选维度"，不是"页面"。**

### 2. 配置只有 4 类，但要分清"每台"和"全局"

| 类 | 例子 | 该放哪 |
|---|---|---|
| **每台设备** | 音量、声道、遮罩、名字、固件 | 设备详情（点设备进） |
| **每个源** | 服务器地址、账号、脚本 | 该源的设置（源头部一个齿轮） |
| **全局一次** | 输出口、睡眠定时、备份 | 播放页 / 我 |
| **系统权限** | 悬浮窗、麦克风、通知 | 我 |

**现在最大的错**：把"每台"的配置（音量/声道/固件/配网）都塞在一台设备的设置页里，
换个设备要重新找一遍。

### 3. 147 个动作 → 用户只需要 4 类入口

| 用户想干什么 | 需要的动作数 | 剩下的怎么处理 |
|---|---|---|
| 让声音出来 / 停 | ~8 | 明面按钮 |
| 找一条能放的 | ~3（找/选/放） | 搜索 + 列表 |
| 调某一台 | ~5 | 行内 + 长按 |
| 出问题时看 | ~2 | 诊断台 |

**剩下 130 多个动作里，绝大多数是"内部编排"**（scan/openDir/toggleEntry/selectAllPlayable…），
它们不该有独立入口，只该是某个动作的中间步骤。

---

## 五、下一步（等用户点头）

按矩阵，UI 重排的优先级应该是：

| 优先 | 做什么 | 依据 |
|---|---|---|
| 1 | 把 5 个内容源页收成 1 个「内容」页（源做筛选） | 65 个动作其实是 3 个 |
| 2 | 设备页：一行一台 + 行内音量 + 长按菜单 | 42 个动作 → 4 个入口 |
| 3 | 配置分类：每台 / 每源 / 全局 / 权限 分开 | 现在混在一起 |
| 4 | 删 4 个"没有用户动作"的页面 | 中转页 |

**这个矩阵说明：不是 UI 不好看，是"147 个动作被摊在 17 个页面里，没有按用户动作归并"。**

---

# 补充 · 另外六个维度（用户问「还能更完整吗」）

第一版只有"功能 + 配置"两维。真正的全景至少还要六维：**协议能力 / 后台服务 / 时间 / 权限 / 诊断 / UI 组件**。

---

## 六、协议能力矩阵（底层能做什么）

### 6.1 LinkPlay 私有 httpapi —— **55 条命令**

| 类 | 代表命令 | 现在用了没 |
|---|---|---|
| 播放控制 | `setPlayerCmd:play/pause/stop/next/prev` `setPlayerCmd:seek` | ✅ |
| 音量 | `setPlayerCmd:vol:<n>` | ✅ |
| 循环 | `setPlayerCmd:loopmode:<n>` | ✅ |
| 队列 | `setPlayerCmd:playlist`（m3u） | ✅ |
| 状态查询 | `getStatusEx` `getPlayerStatus` `getControlDeviceInfo` | ✅ |
| 组网 | `multiroom:*`（`slave_list`/`joinGroup`/`leaveGroup`） | ✅ |
| 机身键 | `keyClick:<n>` | ✅ |
| 输入源 | `setPlayerCmd:switchmode:<x>` | ⚠️ 有，但用户看不懂 |
| 固件 | 检查/升级相关 | ✅（设备设置里） |
| **其余** | 均衡器、闹钟、LED、网络配置等 | ❌ **完全没用** |

**这一栏的意义**：55 条里我们用了约 20 条。**剩下 30 多条里可能有用户想要的功能**
（比如 LED 开关、闹钟、EQ），但**没人盘过**。要确认得逐条试设备。

### 6.2 标准 UPnP —— 4 个服务

| 服务 | 用途 | 谁用 |
|---|---|---|
| `AVTransport` | 播放/跳转/传输信息 | LinkPlay + 通用渲染器 |
| `RenderingControl` | 音量/静音/声道 | LinkPlay + 通用渲染器 |
| `ConnectionManager` | 协议能力协商 | 只读 |
| `WiiMu PlayQueue` | **私有**：整份列表交给设备 | 只有 LinkPlay |

---

## 七、后台服务矩阵（4 个前台服务）

| 服务 | 类型 | 干什么 | 谁启动 | 存活条件 |
|---|---|---|---|---|
| `QueuePlaybackService` | `dataSync` | 客户端逐首投流编排 | 队列播放 | 队列在跑 |
| `MediaServerService` | `dataSync` | 本机文件转 HTTP 给设备取 | 本地文件投送 | 有文件要服务 |
| `FloatingControlService` | `specialUse` | 悬浮控制条 | 设备设置里手动开 | 用户开/关 |
| `PlaybackService` | `mediaPlayback` | MediaSession（通知栏/锁屏/耳机键） | 播放时 | 在播 + 未划掉 |

**三个隐患**（矩阵暴露出来的）：

1. **两个 `dataSync` 服务** —— 同时跑时 Android 有前台服务时长限制，可能互相挤
2. **`FloatingControlService` 是 `specialUse`**，Android 14+ 对它有额外审核描述（已写 subtype）
3. **四个服务各自一套通知渠道**，用户可能在通知栏看到 4 条本 App 的通知

---

## 八、时间维度（多久变一次）

| 周期 | 谁在跑 | 干什么 |
|---|---|---|
| **500ms** | `LocalPlayer` ticker | 本机播放进度 |
| **1s** | `GroupController` | 组拓扑轮询（部分） |
| **3s** | 播放快照轮询 | 设备播放状态 |
| **6s** | `GroupController` / 电台 | 组网变化、电台探活 |
| **700ms / 500ms / 1.5s** | 组网操作后的等待 | 等设备稳定 |
| **300ms / 1s** | 电台发现 | 分页节流 |

**这一栏的意义**：**5 个不同周期同时在跑**。用户感知到的"跟不跟手"由最短那个决定，
而"多久发现设备掉线"由最长那个决定。这两件事没有统一，是"有时快有时慢"的来源。

---

## 九、权限矩阵（14 个）

| 权限 | 干什么用 | 用户会看到 |
|---|---|---|
| `INTERNET` / `ACCESS_NETWORK_STATE` / `ACCESS_WIFI_STATE` | 基础网络 | 无 |
| `CHANGE_WIFI_MULTICAST_STATE` | SSDP 组播发现 | 无 |
| `NEARBY_WIFI_DEVICES` | Android 13+ 发现附近设备 | 有（需要授权） |
| `READ_MEDIA_AUDIO` / `READ_EXTERNAL_STORAGE` | 本地音乐 | 有 |
| `RECORD_AUDIO` | 语音控制 | 有 |
| `SYSTEM_ALERT_WINDOW` | 悬浮窗 | 有（要跳系统设置） |
| `POST_NOTIFICATIONS` | 通知栏媒体控件 | 有（Android 13+） |
| `FOREGROUND_SERVICE` + 3 个子类型 | 4 个前台服务 | 无 |

**矩阵暴露的问题**：**4 个权限需要用户手动处理**（附近设备、本地音乐、麦克风、悬浮窗），
而它们分散在"第一次用到时"才申请 —— **用户不知道还有哪些没给**。
应该有一个"权限总览"，告诉他哪个功能因为没给权限所以用不了。

---

## 十、诊断段矩阵（20 段 = 20 类状态）

| 段 | 反映什么 |
|---|---|
| 崩溃记录 / 运行期问题 | 稳定性 |
| 设备 / 组 / 启动恢复 | 设备连接与拓扑 |
| NAS / 本地曲库 / 在线曲库 / 电台 / 洛雪引擎 / 脚本 | 6 个内容源 |
| 配网 / 语音 / 悬浮窗 / 红外 | 4 个外围能力 |
| 播放输出 / 播放 / 队列 / 后台服务 | 播放链路 |
| 备份 / 响应耗时 / 界面 / 指令 | 系统与性能 |

**这 20 段就是"系统内部状态的全部维度"** —— 换句话说，**App 里任何东西坏了，都会落在这 20 段之一**。

**推论（对 UI 有用）**：既然有 20 类状态，那界面上**每一类状态的"不好"都需要一个显示方式**。
现在只有少数几处在界面上有提示（红色错误条），其余全部只在诊断台里。

---

## 十一、UI 组件矩阵（11 个）

| 组件 | 用途 | 用了几处 |
|---|---|---|
| `AppCard` | 标准卡片 | 19 |
| `AppPage` | 页面骨架 | 少数新页 |
| `AppListRow` | 列表行 | 少数 |
| `AppSearchField` | 搜索框 | 4 |
| `AppStateCard` | 空/错/提示 | 多处 |
| `InsetGroup` / `ListRow` / `LargeTitle` | Apple 风格 | **0（刚建）** |
| `WaveProgressSlider` | 波浪进度条 | 1（播放页） |
| `Glyphs` | 手绘图标 | 多处 |
| `Buttons` / `AppShapesExpressive` | 按钮与形状 | 全站 |

**问题**：**现在有两套并存的呈现单元**（Material 卡片 + Apple 分组列表），
而新那套用了几处 = 0。**在切换完成之前，界面会同时存在两种视觉**。

---

## 十二、数据流矩阵（谁写谁读）

| 数据 | 唯一写者 | 读者 |
|---|---|---|
| 设备列表 / 会话 | `SessionStore` | 首页、播放页、恢复 |
| 队列 | `QueueBus` | 播放页、迷你条、悬浮窗、UpnpPlayer |
| 音量 | `VolumeBus` + 设备侧 | 播放页、卡片、音量键 |
| 输出口 | `PlaybackOutputStore` | 路由、播放页、迷你条、PlaybackService |
| 收藏 / 最近 / 电台 | `LibraryStore` | 各源页 |
| 歌单 | `PlaylistStore` | 音乐库 |
| 洛雪脚本 | `ScriptLibrary` | 洛雪搜索 |
| 诊断 | `AppDiagnostics` | 诊断台 + logcat |

**矩阵暴露的问题**：**队列有 4 个读者**，而其中 `UpnpPlayer` 是这周新加的。
一旦某一方对"队列"的理解不同（比如设备侧 vs 本地侧），就会出现
"通知栏显示第 3 首、播放页显示第 5 首"。

---

## 十三、总矩阵：把七维串起来

| 能力 | 动作数 | 协议支持 | 后台服务 | 时间周期 | 权限 | 诊断段 | UI 组件 | 界面上能摸到吗 |
|---|---:|---|---|---|---|---|---|---|
| 本机播放 | 8 | ExoPlayer | PlaybackService | 500ms | 通知/媒体 | 播放/播放输出 | 卡片 | ✅ |
| 投送设备 | 12 | httpapi + UPnP | QueuePlaybackService | 3s | 网络/组播 | 播放/队列 | 卡片 | ✅ |
| NAS 浏览 | 21 | UPnP ContentDirectory | — | 手动 | 网络 | NAS | 卡片 | ✅（三层） |
| 本地文件 | 15 | LocalMediaServer | MediaServerService | — | 媒体读取 | 本地曲库 | 卡片 | ✅（三层） |
| 在线曲库 | 7 | Subsonic HTTP | — | 手动 | 网络 | 在线曲库 | 卡片 | ✅（三层） |
| 电台 | 11 | 直连 + HLS | — | 6s | 网络 | 电台 | 卡片 | ✅（三层） |
| 洛雪 | 6 | QuickJS + 直链 | — | — | 网络 | 洛雪引擎 | 卡片 | ✅（一级） |
| 组网 | 7 | `multiroom:*` | — | 3s/6s | 组播 | 组 | 卡片 | ⚠️ 深 |
| 机身键 | 1 | `keyClick` | — | — | — | 设备 | 卡片 | ⚠️ 深 |
| 固件 | 2 | httpapi | — | 手动 | — | 设备 | 卡片 | ⚠️ 深 |
| 配网 | 9 | 设备 AP 模式 | — | — | 附近设备 | 配网 | 卡片 | ⚠️ 三层 |
| 语音 | — | Android STT | — | — | 麦克风 | 语音 | 麦克风按钮 | ✅ |
| 悬浮窗 | 3 | — | FloatingControl | — | 悬浮窗 | 悬浮窗 | 无 | ⚠️ 找不到 |
| 红外 | 2 | `ConsumerIrManager` | — | — | — | 红外 | 一个按钮 | ⚠️ 一个键 |
| 蓝牙 / WiFi 遥控 | 0 | — | — | — | — | — | — | ❌ |
| 诊断 | 5 | — | — | — | — | 20 段 | 卡片 | ⚠️ 三层 |

---

## 十四、这张总矩阵说明什么

1. **16 项能力，只有 7 项在界面上是"能直接摸到"的**（✅）
2. **8 项是"深"或"找不到"**（⚠️）—— 不是没有，是路径太绕
3. **2 项完全没有**（蓝牙/WiFi 遥控）
4. **每个能力都有自己的时间周期、自己的权限、自己的诊断段** ——
   但它们**没有统一的"状态显示"机制**，只有诊断台里能看到

**所以 UI 重排的真正目标不是"好看"，是：**
> 让这 16 项能力的**状态**在界面上各就各位，
> 而不是全部塞进一个诊断台、或者干脆不显示。
