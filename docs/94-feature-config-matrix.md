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
