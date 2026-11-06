# 矩阵 · 内部管线（按 UI 定稿的五屏）

> 用户：「按这个定稿，内部管线同样做个矩阵，**找最短路径和保留扩展性**」
>
> 依据：`docs/93`（UI 定稿五屏）+ `docs/94`（功能配置七维矩阵）
> 本文回答两个问题：**① 每个动作穿了几层，哪些是绕路 ② 加新东西要改几处**

---

## 零、管线总览（七层）

```
① UI 界面        Screen / Composable
② 状态层         10 个 ViewModel
③ 路由分派       PlaybackRouter / PlaybackDispatcher / QueueStarter
④ 协议客户端     DeviceHttpClient / UpnpRendererClient / GenericRenderer / SubsonicClient / LxUserApi
⑤ 传输           HTTP / SOAP / QuickJS / SSDP / IR 载波
⑥ 设备或服务     三台 DOSS / 电视盒子 / 红外电视 / 本地文件服务
⑦ 观测           诊断 20 段 + 耗时账本 13 个动作
```

**七个 core 模块的分工**（这是扩展性的边界）：

| 模块 | 管什么 | 谁依赖它 |
|---|---|---|
| `core-protocol` | LinkPlay 私有协议 + UPnP 基础 + 队列编排 | app（47 文件） |
| `core-upnp` | 通用 DLNA 渲染器（能力/状态/怪癖） | app |
| `core-discovery` | SSDP 发现 | app（4 文件） |
| `core-lx` | 洛雪引擎（QuickJS + bundle） | app（4 文件） |
| `core-radio` | 电台目录（radio-browser） | app（3 文件） |
| `core-script` | 脚本仓库（存/校验/启用） | app |
| `core-diagnostics` | 诊断段 + 耗时账本 | app（6 文件） |

**方向铁律**：`app → core-* → core-protocol`。反向依赖会让底座被界面需求污染。

---

## 一、五屏 → 动作 → 链路跳数（找最短路径）

跳数 = 从"用户手指落下"到"字节离开手机"经过的**层数**。**跳数越少，跟手性上限越高。**

### 屏 1 · 设备

| 动作 | 链路 | 跳数 | 评价 |
|---|---|---:|---|
| 看设备状态 | Screen → VM.probeOne → snapshots → `GetControlDeviceInfo`+`getPlayerStatus` | **4** | ✅ 已并发、已合并两次取数（最短） |
| 拖音量 | Screen(手势) → VM.setCardVolume → `DeviceHttpClient` → httpapi | **4** | ✅ 最短 |
| 音量键 | KeyEvent → VM.adjustVolumeByKey → 同上 | **4** | ⚠️ **与上一行是两套实现** |
| 进设备详情 | Screen → DeviceSettingsScreen（同层页面切换） | 2 | ✅ |
| 扫描发现 | Screen → VM.scan → SSDP → 设备回包 | **4** | ✅ |
| 添加设备 | Screen → VM.add → SessionStore 写盘 | 3 | ✅ |
| 组网 | Screen → VM.applyGrouping → `multiroom:*` 连发 3~4 条 | **5~6** | ⚠️ 命令数多，属必要（协议限制） |

**这一屏最短 = 4 跳**（状态/音量/扫描都是），已经压到底。

### 屏 2 · 播放

| 动作 | 链路 | 跳数 | 评价 |
|---|---|---:|---|
| 播放（本机） | Screen → VM → **Dispatcher** → Router → ExoPlayer | **5** | ✅ |
| 播放（投音箱） | Screen → VM → Dispatcher → Router → **QueueStarter** → Gateway → HTTP | **7** | ⚠️ 最长，但每一段都有职责 |
| 暂停/切歌（本机） | Screen → Dispatcher → ExoPlayer | **3** | ✅ 最短 |
| 暂停/切歌（投音箱） | Screen → Dispatcher → VM.pause → httpapi | **4** | ✅ |
| 拖进度 | Screen → Dispatcher → VM.seekTo → SOAP `Seek` | **4** | ✅ |
| 换输出口 | Screen → Router.setOutput → 写文本文件 → **重启服务** | **5** | ⚠️ 重启服务是代价（MediaSession 不能换 player） |
| 从内容页起播 | 内容页 → Router.playQueue → QueueStarter → Gateway → HTTP | **6** | ✅ 已收口 |

**最长 7 跳是"投音箱播放"** —— 每段职责：
`VM`（设备态）、`Dispatcher`（能力位）、`Router`（输出选择）、
`QueueStarter`（三段降级）、`Gateway`（协议封装）、`HTTP`（传输）。

**能不能再减？** 只剩一个候选：`Dispatcher` 和 `Router` 合并。
代价是"能力位判断"会浸进路由层，将来本机播放器也要知道设备能力 —— **不减更干净。**

### 屏 3 · 内容

| 动作 | 链路 | 跳数 | 评价 |
|---|---|---:|---|
| 搜（洛雪） | Screen → VM → `LxMusicSdk` → QuickJS(bundle) → 结果 | **5** | ✅ |
| 搜（电台） | Screen → VM → `RadioBrowserSource` → HTTP | **3** | ✅ **全 App 最短** |
| 搜（NAS） | Screen → VM → UPnP `Browse` → SOAP | **4** | ✅ |
| 搜（本地） | Screen → VM → `LocalLibrary` → SQLite | **3** | ✅ |
| 点一首播放（洛雪） | 上 + `LxUserApi` 取直链 → Router → QueueStarter | **7** | ⚠️ 必然（要跑脚本取直链） |
| 点一首播放（电台） | 上 + 直连 URL → Router → QueueStarter | **5** | ✅ |

**洛雪那条 7 跳是算法决定**（QuickJS 跑脚本 + 取直链 + 投送），砍不掉。

### 屏 4 · 遥控

| 动作 | 链路 | 跳数 | 评价 |
|---|---|---:|---|
| 按红外键 | Screen → `IrBlaster` → `NecEncoder` → `ConsumerIrManager.transmit` | **4** | ✅ 最短（无网络） |
| 蓝牙遥控 | **不存在** | — | ❌ 零代码 |
| WiFi 遥控 | **不存在** | — | ❌ 零代码 |

**红外是全 App 唯一"不经过网络"的路径** —— 4 跳里还有 2 跳是本地计算，实际耗时 <5ms。

### 屏 5 · 我

| 动作 | 链路 | 跳数 | 评价 |
|---|---|---:|---|
| 看诊断 | Screen → AppDiagnostics 读盘 → 渲染 20 段 | **3** | ✅ |
| 配网 | Screen → VM → 设备 AP 模式 HTTP → 下发凭据 | **5** | ⚠️ 一次性流程，跳数不重要 |

---

## 二、绕路清单（这是矩阵的主要产出）

### 🔴 真绕路（该修）

| # | 问题 | 现状 | 目标 |
|---|---|---|---|
| 1 | **音量两套实现** | `setVolume`（定值）+ `setVolumeLive`（实时），两条下发链 | 一个 `SetVolumeUseCase`（`docs/90` S3.1 未做） |
| 2 | **循环模式三份存储** | 播放页 / NAS / 队列各记一份 | 一份，跟输出口走 |
| 3 | **界面按钮仍直连 VM** | `PlayerScreen` 十几处 `vm.xxx()`，没走 `Dispatcher` | 全走 `Dispatcher`（S1.3 剩余） |
| 4 | **`QueueItem(` 还有 6 处手写** | 已收 2 处 | 全走 `ToQueueItemsUseCase` |
| 5 | **`pressPreset` 在两个 VM** | `ConsoleViewModel` 和 `LibraryViewModel` 各一份 | 一份 |

### 🟡 结构性冗余（可不改）

| 现象 | 为什么可以保留 |
|---|---|
| `Dispatcher` + `Router` 两层 | 一个管"能不能做"，一个管"往哪做"，合并会让本机播放器也要懂设备能力 |
| 寄存器/网关 5 个类 | `DevicePlaylistGateway`（PlayQueue）/ `DeviceQueuePush`（m3u）/ `QueuePlaybackService`（逐首）是**三段降级**，不是一个东西的三个实现 |
| 5 个轮询周期 | 各自用途不同（500ms 播放进度 / 3s 状态 / 6s 组网），但**没有统一配置入口** ← 这个可以补 |

---

## 三、扩展性矩阵（加新东西要改几处）

### 3.1 加一个新的**播放设备**

| 情形 | 改动点 | 数量 |
|---|---|---|
| 标准 DLNA 渲染器（电视、功放） | `core-upnp` 探测能力位 → 自动落进"其它播放设备" | **0（app 层）** ✅ |
| 有私有队列的厂商 | `core-protocol` 加客户端 + 能力位 | 2~3 |
| 私有协议且功能多 | 新模块 | 4+ |

**现状评价：标准设备友好（零改动），私有协议每来一家要动 2~3 处。**

这是正确的取舍 —— 世界上标准 DLNA 设备远多于私有协议的。

### 3.2 加一个新的**内容源**

**现在**（要改 4 处）：

```
① app/.../新源/NewViewModel.kt        新建
② app/.../新源/NewScreen.kt           新建
③ Nav.kt SubPage 加一项 + ConsoleApp 加分支
④ ConsoleDiagnostics 加一段诊断
```

**定稿 UI 之后**（可减到 2 处）：

```
① ContentSource 接口实现（一个文件：搜索 + 播放 + 诊断一行）
② 「我」里加一条配置入口
```

**前提**：把 `docs/93` 屏 3 的"源筛选"做成一个接口，而不是 6 个 Screen。

**这是定稿 UI 最大的扩展性收益 —— 加源从 4 处降到 2 处。**

### 3.3 加一个新的**遥控类型**（红外 / 蓝牙 / WiFi）

**现在**：

| 类型 | 抽象 | 状态 |
|---|---|---|
| 红外 | ❌ 无（`NecEncoder` 直接写在 `ir/` 包里） | 只有 NEC 一种编码 |
| 蓝牙 | ❌ 无 | 零代码 |
| WiFi | ❌ 无 | 零代码 |

**问题**：每加一种就要**再散一次**（编码 + 发送 + UI + 诊断各一份）。

**必须补的一层**（否则 U3 会变成三份互不相干的实现）：

```kotlin
interface RemoteControl {
    val label: String                       // "电视" / "空调"
    suspend fun send(key: RemoteKey): RemoteResult
    /** 这台设备支持哪些键 —— UI 据此画遥控器，不画按不动的键 */
    fun keys(): Set<RemoteKey>
}
```

三种实现：
- `IrRemoteControl`（红外，`NecEncoder` 变成它的内部细节）
- `BleRemoteControl`（蓝牙 HID / GATT）
- `HttpRemoteControl`（WiFi，各家协议）

**UI 只认 `RemoteControl`** —— 遥控页画什么键由 `keys()` 决定，加新类型 UI 零改动。

### 3.4 扩展性总表

| 加什么 | 现在改几处 | 目标 | 差距 |
|---|---:|---:|---|
| 标准播放设备 | **0** | 0 | ✅ 已达标 |
| 私有协议播放设备 | 2~3 | 2 | 小 |
| 内容源 | **4** | 2 | **定稿 UI 能解决** |
| 遥控类型 | **4×N**（每种各散一次） | 1 + 实现 | **必须先建接口** |
| 播放输出 | 0（`PlaybackOutput` 已泛化） | 0 | ✅ |
| 诊断段 | 1 | 1 | ✅ |

---

## 四、定稿 UI 需要的管线改造（按最短路径排序）

| 优先 | 改造 | 为什么现在改 |
|---|---|---|
| **1** | 建 `RemoteControl` 接口 + 红外实现 | 遥控页要落地，**不先建接口就会散三份** |
| **2** | 建 `ContentSource` 接口 | 内容页要合并 6 源，接口不建就得写 6 套 UI |
| **3** | 音量收成一个 UseCase | 两个现有实现已经不一致（真机踩过：卡片 60、按键后读回 40） |
| 4 | 界面按钮走 `Dispatcher` | 接 `can()` 后电视盒子才不会画"下一首" |
| 5 | 循环模式收成一份 | 三份存储迟早大家各说各话 |
| 6 | 轮询周期收成一处配置 | "有时跟手有时不跟手"的根 |

**前两条是"先修路再跑车"** —— 不建接口，定稿的遥控页和内容页会变成两坨各自硬编码的界面。

---

## 五、一句话

**最短路径已经压到底了**：状态/音量/扫描 4 跳、本机控制 3 跳、红外 4 跳（无网络）。
最长的 7 跳（洛雪、投送）是算法和协议决定的，**再减就要牺牲职责分离**。

**真正的差距在扩展性**：
- 内容源现在要改 4 处 → 定稿 UI 能降到 2 处
- **遥控类型现在每加一种就散一次，必须先把 `RemoteControl` 接口建起来**

**所以定稿 UI 落地前，先建两个接口**（`RemoteControl` / `ContentSource`），
否则做完就是两坨硬编码，下次加东西还得拆。
