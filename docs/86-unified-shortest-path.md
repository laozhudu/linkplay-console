# 施工单：内部接口最短路径 + 界面反馈 + 入口收敛

> **依据的代码状态：提交 `513b8e1`**
> 来源：`docs/83`（定位与缺口）、`docs/85`（B 站）、两份调研（交互/UI 深度、技术债与三轴矩阵）
> 性质：**给开发对话的施工单**。每条都写了「做什么 / 做到什么程度 / 收益多少」。
> 用户要求原话：「软件内接口互通需要最短」「要交代清楚做什么，做到什么程度，收益多少」

---

## 零、怎么读这份文档

**只做第一批就能拿到最大收益。** 四批不是四件事，是一条链：

```
第一批 内部接口最短   ← 删代码，收益最大，风险最低
第二批 界面反馈       ← 加东西，观感立刻不同
第三批 入口重排       ← 动结构，前置是第一批
第四批 清理           ← 一次性，可穿插在任何时候
```

**每一批都能独立合并、独立验收**，不要攒成一个大 PR。

---

## 一、目标：三条不变量（所有批次都服务于它们）

| # | 不变量 | 现在的违反情况 |
|---|---|---|
| **I1** | **一个动作只有一条实现** | 下发路径有 **3 条** |
| **I2** | **调用层级 ≤ 3**：界面 → 分派点 → 实现 | NAS 是 界面 → ViewModel 私有方法 → 3 段降级（**4 层**） |
| **I3** | **能力决定渲染，型号不决定** | NAS 路径**不看**能力位，硬撞一次 401 才降级 |

**这三条不是新原则** —— `docs/52` 的「单一入口 + 编译期强制」、`docs/61` 的「唯一入口」、决策 #29「能力档决定渲染哪些入口」都已经写了。这份施工单做的是**把它们真正执行完**。

---

## 二、第一批 · 内部接口最短（最高优先级）

### 1.1 删掉 NAS 自己那份三段降级

**做什么**

`NasViewModel` 里有一个私有方法 `pushQueue`（57 行），内容和 `QueueStarter.startQueueOnDevice` **逐行同构**：

```
① DevicePlaylistGateway.push(rendererIp, rendererLocation, items, start)
② DeviceQueuePush.push(DeviceHttpClient(), rendererIp, items, start)
③ QueuePlaybackService.start(getApplication(), rendererLocation, session)
```

改成一行调用：

```kotlin
startQueueOnDevice(
    context = getApplication(),
    deviceIp = rendererIp,
    location = rendererLocation,
    deviceUuid = deviceUuid,
    items = items,
    startAt = start,
    note = "NAS「$目录名」",
    log = ::log,
)
```

**做到什么程度（验收）**

- [ ] `NasViewModel` 里不再出现 `DevicePlaylistGateway` / `DeviceQueuePush` / `QueuePlaybackService` 任何一处直接调用（`grep` 零命中）
- [ ] 投 NAS 到**通用 DLNA 渲染器**（`.175` AirReceiver）时，日志里**不出现** `PlayQueue 未接受` 那一行 —— 而是直接出现「该设备是通用 DLNA 渲染器，没有设备侧队列」
- [ ] 投 NAS 到 DOSS 音箱，行为和现在**完全一致**（设备侧 PlayQueue 生效，`BrowseQueue` 能读回曲名）
- [ ] `NasSourceStatus` 的 `route()` 仍能记到三段降级的哪一段（不能因为换入口就丢诊断）

**收益**

| 项 | 前 | 后 |
|---|---|---|
| 下发路径 | 3 条 | **2 条**（再做完 1.2 就是 1 条） |
| NAS 投通用渲染器 | 撞一次 401/超时再降级 | **直接跳过**，少一轮往返 |
| 代码 | — | **-57 行**（NAS 那份）+ 少一份要同步维护的逻辑 |

**为什么这条排第一**：它是**删代码**，风险最低；而且它是 I1/I3 的直接违反，不修的话后面所有「统一」都是假的。

---

### 1.2 手机本地队列也走唯一入口

**做什么**

`QueueViewModel.push` 现在是「攒队列 → 起 `LocalMediaServer` → **直接** `QueuePlaybackService.start`」，
**连设备侧队列都不试**。

改成走 `startQueueOnDevice`（本地文件用 `LocalMediaServer` 的 URL，和现在一样）。

**做到什么程度（验收）**

- [ ] `QueueViewModel` 里不再直接调 `QueuePlaybackService.start`
- [ ] 投本地文件到 DOSS 音箱 → 日志出现「设备侧队列 · 原厂 PlayQueue」（现在是「客户端逐首投流」）
- [ ] 投本地文件到通用渲染器 → 正常降级到客户端逐首，行为不变
- [ ] 设备侧队列生效时，**App 退出后设备仍能自己往下播**（这是设备队列和客户端逐首的唯一实质区别）

**收益**

| 项 | 前 | 后 |
|---|---|---|
| 下发路径 | 2 条 | **1 条** |
| 手机本地文件投音箱 | App 退出就停（客户端逐首） | **设备自己往下播** |
| 代码 | — | 少一份分支逻辑 |

> ⚠️ **注意**：本地文件的 URL 是 `http://<手机IP>:<port>/t/<id>`，设备要能访问到手机。
> 这一点现在的客户端逐首也一样，所以不是新增风险。但**手机锁屏/切网后 IP 变了**这条要单独验。

---

### 1.3 `PlaybackRouter` 成为唯一的输出分派点（含队列）

**做什么**

`PlaybackRouter` 的类注释写着：

> **播放入口的唯一分派点** —— 本地播和推流从这里选，**界面不该各自判断**。

但它现在只有 `playOne(url, title, ...)` —— **只能播一条**。所以：

- NAS / 本地 / 在线 / 歌单 全部绕过它，直接投音箱
- 只有洛雪能用它（因为洛雪是「点一首搜一首」）

加一个 `playQueue`：

```kotlin
suspend fun playQueue(
    items: List<QueueItem>,
    startAt: Int,
    deviceIp: String,
    location: String?,
    deviceUuid: String?,
    note: String,
    log: (String) -> Unit = {},
): Boolean = when (output()) {
    PlaybackOutput.Local -> { /* 交给本地播放器，转 PlayerUiState */ }
    PlaybackOutput.Remote -> startQueueOnDevice(...)
}
```

然后把 5 个源改成调它：NAS / 手机本地 / 在线 / 歌单 / 洛雪。

**做到什么程度（验收）**

- [ ] 全仓 `startQueueOnDevice(` 的调用点**只剩 `PlaybackRouter` 内部一处**（其余全走 `playQueue`）
- [ ] `PlaybackOutput` 的 UI 开关从 `LxSearchScreen` **移到播放页**
- [ ] 在播放页切到「本机」，然后从 **NAS / 本地 / 在线 / 歌单 / 洛雪** 任一处点播放 → 声音从**手机**出来
- [ ] 切到「音箱」→ 声音从**音箱**出来，且**不会两边一起响**
- [ ] 切输出时另一头被停掉（`pauseBoth` 已有，验它真的生效）

**收益**

这是整份施工单**收益最大的一条**：

| 项 | 前 | 后 |
|---|---|---|
| 能手机本地播的内容源 | **1 / 6**（只有洛雪） | **6 / 6** |
| `PlaybackService`（MediaSession / 通知栏 / 锁屏 / 耳机键） | 6 个场景里 5 个是**死的** | 全场景可用 |
| 界面里 `if (本地) ... else ...` 的判断 | 分散 | 一处 |
| 调用层级 | 界面 → VM 私有方法 → 3 段降级（4 层） | 界面 → `playQueue` → `startQueueOnDevice` → 实现（**3 层**，符合 I2） |

**为什么这条同时解决「最短路径」和「UI 债」**：
现在「本地播 / 投音箱」的开关长在洛雪页（`LxSearchScreen` 里那两个 `SegmentChip`）—— 这是**功能落在偶然的位置**。
挪到播放页之后，输出选择变成播放的属性，而不是某个源的属性。

---

## 三、第二批 · 界面反馈（独立一批，不动结构）

现状（grep 全仓实测）：

```
animate* 2   AnimatedVisibility 2   Crossfade 0   触感 Haptic 0
Snackbar 0   Toast 4   下拉刷新 0   骨架屏 2
contentDescription 2   semantics 0   testTag 0
```

### 2.1 升 compose-bom（前置，单独一轮）

**做什么**：`2024.12.01` → `2026.09.00`，`material3` 随之到 `1.4.0`。

**做到什么程度**

- [ ] `:app:assembleDebug` 通过
- [ ] 全量单测通过（现在 1,036 个）
- [ ] `ThemeContrastTest`（WCAG 4.5:1）仍绿 —— 换版本最容易悄悄破坏对比度
- [ ] 界面**肉眼**看一遍：字号/间距/颜色没有意外变化

**收益**：解锁 `MaterialTheme.motionScheme`（M3 Expressive 的 spring token）。
**不升的话第二批 2.2 做不了。**

> ⚠️ 升级会触发一批 API 变更。**单独一轮，不要和任何 UI 改动混在一个 PR 里**（`docs/52` 的纪律）。

### 2.2 动效 token + 三处落地

**做什么**

先建 token（官方规格，可直接抄）：

```kotlin
// Spatial = 形状/尺寸；Effects = 颜色/透明度
Expressive fast spatial     350ms
Expressive default spatial  500ms
Expressive fast effects     150ms
Expressive default effects  200ms
```

只用在**三个地方**，不要一次铺开：

| 位置 | 用什么 |
|---|---|
| Tab 切换 | `AnimatedContent` + effects 淡入（200ms） |
| 队列增删 | `LazyColumn` 的 `Modifier.animateItemPlacement()` |
| 播放/暂停按钮 | 图标 morph + 轻微缩放（spatial 350ms） |

**做到什么程度**

- [ ] 这三处动效可见，且**不卡**（真机滑列表仍跟手）
- [ ] 动效时长集中在**一个文件**里，页面不写字面量
- [ ] 关掉系统动画（开发者选项「动画时长 0.5x」）后不崩、不卡死

**收益**：这是**唯一能一次改变全部页面观感**的东西。现在全仓 20,036 行里只有 2 处动画
（唯一做对的是 `VolumeOverlay` 的淡入滑入 —— 说明能力有，只是没成系统）。

### 2.3 触感反馈三处

| 位置 | 类型 |
|---|---|
| 拖动进度条跨过整数秒 | `TextHandleMove` |
| 切歌成功 / 队列项换位 | `Confirm` |
| 操作失败（连不上设备） | `Reject` |

**做到什么程度**：三处各震动一次，**失败时震动能区分出来**（不是所有震动都一样）。

**收益**：成本极低（Compose 里 `LocalHapticFeedback.current.performHapticFeedback(...)`），是「手感」的核心。

### 2.4 补 `contentDescription` / `testTag`

**做什么**：所有 `IconButton` 加 `contentDescription`；所有可点控件加 `testTag`（格式 `<页面>.<功能>`，列表项带 uuid）。

**做到什么程度**

- [ ] `grep -c contentDescription` ≥ 所有 `IconButton` 数量
- [ ] `testTag` 覆盖 4 个一级页 + 播放页的主控件
- [ ] 开着 TalkBack 走一遍：每个按钮都念得出名字

**收益（双重）**

1. 无障碍从 2 处到可用
2. **解锁 `docs/76` 的实机测试台** —— `uiautomator` 读不到文字节点，`tap --text` 全废；
   没有 `testTag`，agent 只能按坐标点，**换手机就废**。

> 这条是**自动化测试的前置条件**，不是「体验优化」。想以后能真机自动验证，这条必须做。

### 2.5 Tab 栏换真图标

**做什么**：现在底部 Tab 是**文字符号**：

```kotlin
Devices("设备", "◍"),  Player("播放", "▮▮"),  Lx("洛雪", "♪"),  Sources("媒体库", "≡")
```

换成 `NavigationBar` + `NavigationBarItem` + Material Symbols（自带选中胶囊、图标切换动画、徽标位）。

**做到什么程度**

- [ ] 四个 Tab 图标在**不同字号**下宽度一致（现在几何符号会不对齐）
- [ ] 有选中态（不只是颜色变化）
- [ ] 播放中仍有「正在播放」的指示

**收益**：这是用户**每天看几十次**的东西。`◍` 看不出是设备，`▮▮` 看不出是播放。

---

## 四、第三批 · 入口收敛（前置：第一批）

### 3.1 一级入口按「对象」分，不按「播放 vs 遥控」分

**做什么**：四个一级入口语义改一个字：

| 一级 | 管什么 | 现在散在 |
|---|---|---|
| **设备** | 有哪些设备、组网、每台状态；点进去是**这台的遥控面**（音量/声道/输入源/预设/固件） | `HomeScreen` + `DeviceContentScreen` + `DeviceSettingsScreen` |
| **播放** | 在放什么、队列、进度、**切输出** | `PlayerScreen` + 洛雪页的开关 |
| **找歌** | 一个入口问所有源 | `MyMusicScreen` 两个列表 + `SearchHubScreen` + 6 个二级页 |
| **设置** | 备份、诊断、红外、悬浮窗、脚本 | `DeviceSettingsScreen` 里那 4 个不该在的区块 |

**做到什么程度**

- [ ] 同一台音箱的控制动作**只出现在一个页面**（现在播放/暂停同时存在于首页卡片和播放页）
- [ ] 「设置」里不再有「配置备份 / 红外遥控 / 诊断记录 / 桌面悬浮控制」这四块
- [ ] `MyMusicScreen` 里 NAS / 本地 / 在线 / 电台**各只出现一次**（现在各出现两次：汇总行 + 下方卡片）

**收益**

| 症状 | 前 | 后 |
|---|---|---|
| `DeviceSettingsScreen` 区块数 | **8** | 4 |
| `MyMusicScreen` 重复入口 | 4 组 | 0 |
| 控一台音箱要去的页面 | 4 个 | **2 个**（设备页 + 播放页） |

### 3.2 能力位补两个根位

**做什么**：`DeviceCapabilities` 现在 9 项全是「怎么播」。加两个：

```kotlin
/** 能不能出声 —— 决定它出不出现「播放到哪」的选择器 */
val canPlayAudio: Boolean = false,
/** 能不能被遥控 —— 红外/蓝牙/WiFi 都算 */
val canBeRemoted: Boolean = false,
```

于是设备模型统一了：

| 设备 | 出声 | 被遥控 | 出现在 |
|---|:-:|:-:|---|
| DOSS 音箱 | ✅ | ✅ | 设备页 + 播放选择器 + 遥控面 |
| 通用 DLNA 电视 | ✅ | ✅ | 设备页 + 播放选择器 + 遥控面 |
| 手机本地 | ✅ | ✅ | 播放选择器（**不进设备页**） |
| 红外空调 | ❌ | ✅ | 设备页 + 遥控面（**不进播放选择器**） |

**做到什么程度**

- [ ] 通用渲染器（`.175` 电视盒子）出现在设备页，能力位正确
- [ ] 点电视盒子进「播放到哪」选择器，能投流
- [ ] 电视盒子**不显示**声道、六键预设、固件升级（它没有）

**收益**：这就是「统一操控」的正确形态 —— 一套设备模型、一套能力位、一套遥控面组件，
不同设备只是能力位不同。

> ⚠️ **不要加 `kind` 字段给设备贴死标签**。能力会随固件变（`.153` 旧固件 vs `.164` 新固件已实测出字段差异）。
> 正确做法是**把探测结果持久化**，冷启动先用上次的值画，探测回来再修正。

### 3.3 `QueueItem` 补 `providerId`（统一的「条目」）

**做什么**：现在「条目」有 5 个类型（`QueueItem` / `MediaTrack` / `LocalTrack` / `RadioStation`×2），字段大面积重叠。
`QueueItem` 已经是 `startQueueOnDevice` 的入参，只缺一个来源标识。

**做到什么程度**

- [ ] `QueueItem` 有 `providerId`
- [ ] 诊断报告里能看出「这一队来自哪个源」
- [ ] `MediaTrack → QueueItem` 的转换只在一处做（`MediaProvider.resolve()` 之后）

**收益**：这是「内容源统一增删改查」的真实缺口 —— **CRUD 其实都齐了**
（`PlaylistStore` 9 个方法、`RadioStationStore` 7 个方法），缺的是**统一的条目类型**。

---

## 五、第四批 · 清理（可穿插）

| # | 做什么 | 做到什么程度 | 收益 |
|---|---|---|---|
| 4.1 | 12 个 `*SourceStatus` 收敛成一张注册表 | 行数从 1,466 降到 400 左右（**估计**）；诊断输出内容不变 | 加功能不再抄 140 行 |
| 4.2 | 4 处 `items()` 补 `key` | 列表刷新不丢滚动位置 | 滚动手感 |
| 4.3 | `LxSourceStatus` / `GroupSourceStatus` 挪进子包 | 包结构和 `docs/52`「按功能分包」一致 | 可维护 |
| 4.4 | 拆 `HomeScreen`（623 行 1 个 composable） | 拆成子 composable，整页重组范围缩小 | 滑动流畅度 |

**4.1 的注意**：诊断台必须跟着功能长（铁律）。收敛形状**不能减少输出内容**，
否则以后「点了没反应」就查不出来了。

---

## 六、验收总表（一张表看做完没有）

| # | 验收项 | 怎么验 | 属于 |
|---|---|---|---|
| 1 | 下发路径只剩 1 条 | `grep -rn 'QueuePlaybackService.start\|DevicePlaylistGateway.push' app/src/main` → 只在 `QueueStarter` 里 | 第一批 |
| 2 | NAS 投电视盒子不再撞 401 | 真机投 `.175`，日志无「PlayQueue 未接受」 | 第一批 |
| 3 | 6 个源都能手机本地播 | 逐个点播放，声音从手机出来 | 第一批 |
| 4 | 通知栏/锁屏/耳机键可用 | 投本地时下拉通知栏有媒体控件 | 第一批 |
| 5 | compose-bom 升级后全绿 | `:app:assembleDebug` + 全量单测 + `ThemeContrastTest` | 第二批 |
| 6 | 三处动效可见且不卡 | 真机看；开 0.5x 动画不崩 | 第二批 |
| 7 | `testTag` 覆盖主控件 | `grep -c testTag` ≥ 30 | 第二批 |
| 8 | TalkBack 能念出每个按钮 | 开 TalkBack 走一遍 | 第二批 |
| 9 | 设置页只剩 4 个区块 | 数 `SectionTitle` | 第三批 |
| 10 | 同一动作只有一个页面有 | 播放/暂停只在一处 | 第三批 |
| 11 | 电视盒子在设备页且能力位正确 | 真机看卡片 | 第三批 |
| 12 | `*SourceStatus` 行数下降且诊断不变 | 对比诊断报告全文 | 第四批 |

---

## 七、明确不做

| 不做 | 理由 |
|---|---|
| 为「三轴」建新抽象层（`OutputPort` / `SourcePort`） | 现在的问题是**已有接口没通电**，不是缺接口 |
| 引入 `NavHost` 换掉 enum 状态机 | 是 UI 债，但优先级低于第一批；而且现在没有返回栈是**能用**的 |
| 把 `*SourceStatus` 拆成模块 | `docs/83`：不为架构纯粹拆模块 |
| 下载功能 | **它不是 CRUD 的一部分**，是独立链路（下载队列 + 存储 + 离线索引 + 磁盘上限）。单独立项 |
| 红外 / 蓝牙真做 | 地基是空的（`ir/` 只有 196 行、蓝牙 0 行）；先把播放设备这条链统一 |
| 让悬浮窗也控本地播放 | 悬浮窗是「桌面遥控器」语义，本地播放有通知栏；两者互补不重叠 |
| 补全 ViewModel 测试**再**动结构 | 顺序反了 —— 先删重复实现（第一批），剩下的一份再补测试 |

---

## 八、最容易踩的五个坑

1. **`PlayQueueWithIndex` 的 Index 是 1 基**，m3u 只认 `index=0` 所以要把列表旋转 —— 这段已经在 `startQueueOnDevice` 里了，**别重写**
2. **`QueueBus.restore` 自己会设 owner**，不要再前面 `setOwner`（那是旧 API 的两步写法）
3. **通用渲染器没有 `PlayQueue`**，硬推拿 401 白等一轮 —— 一定要先问 `DeviceProfileCache.supportsDeviceQueue`
4. **`mediaPlayback` 前台服务类型不能复用 `dataSync`**（`QueuePlaybackService` 是 `dataSync`，`PlaybackService` 是 `mediaPlayback`），权限一一对应
5. **升 compose-bom 会悄悄破坏对比度** —— `ThemeContrastTest` 是唯一的防线，别跳过

---

## 九、给开发的一句话

**第一批（1.1 / 1.2 / 1.3）先做，它全是删代码和改调用点，不新增功能，但能一次解决「内部接口不互通」和「本地播放只有洛雪能用」两个最大的结构问题。**

做完第一批，界面那边再怎么改都不会返工 —— 因为「声音从哪出来」这件事终于只有一个地方决定了。

---

## 附：本文引用的现状数字（依据 `513b8e1`）

```
app/src/main            20,036 行 / 96 文件
下发路径                 3 条
PlaybackOutput UI 开关   1 处（LxSearchScreen）
能本地播的源             1 / 6
ViewModel 合计           4,765 行 / 0 测试
*SourceStatus 合计       1,466 行 / 10 测试文件
animate*                 2        Haptic 0
contentDescription       2        testTag 0
DeviceSettingsScreen     8 个 SectionTitle
composeBom               2024.12.01（最新 2026.09.00）
```
