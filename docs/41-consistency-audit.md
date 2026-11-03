# 41 · 一致性审计：还有哪些地方是"各走一套"

上一轮把**设备信息回传**收敛成一条路径（`DeviceSnapshotReader` → `DeviceSnapshot` → `applySnapshot`）。
顺着同一条思路把整个项目扫了一遍，结论是：**设备信息是第一个被治好的，不是唯一有病的**。

下面每一条都有 `文件:行号` 证据，不是感觉。

---

## 总览（按危害排序）

| # | 问题 | 现状 | 危害 | 证据 |
|---|---|---|---|---|
| 1 | **设备 uuid 存储侧 3 套归一化** | 查询侧宽容了（`UuidMatch`），**存储侧仍各自造** | 同一台音箱会变成两条记录、会话键分叉 | `ConsoleViewModel.kt:901 / :918 / :568` |
| 2 | **队列归属只覆盖 3/13 处写入** | `QueueBus.restore()` 用"当前 owner"，只有 `QueueStarter` 和 `PlaybackViewModel` 先 `setOwner` | 切设备后队列显示别人家的（PR #40 只修了一条路径） | `QueueBus.kt:137`；13 处写入见下表 |
| 3 | **音量下发 3 套路径** | 音量键走 httpapi、拖动走 UPnP、播放页走 engine | "界面 60、实际 40"；同一下操作不同结果 | `ConsoleViewModel.kt:1248`、`DeviceVolumeControl.kt`、`PlaybackEngine.kt:110/193` |
| 4 | **能力档存了两份状态** | `DeviceProfileCache`（按 IP）与 `DeviceCard.profile`（按 uuid）各自记录 | 可能一个探测到一个没有，起播与界面判断不一致 | `DeviceProfileCache.kt:35` vs `ConsoleViewModel.kt:2004` |
| 5 | **错误文案直出异常 message** | 12+ 处 `error = t.message` | 界面会显示 `HTTP 500 from 192.168.99.153: 401 invalid action` | 见下 |
| 6 | **时长格式化 4 套实现** | 两处委托 `PositionInfo.formatTime`，两处自己写 | 超过 1 小时显示不一致；`%d:%02d` 有重复实现 | `QueueScreen.kt:341`、`OnlineScreen.kt:185` |
| 7 | **"未知"的语义不统一** | 有时 `null`、有时 `-1`、有时 `0` | 0 音量和"读不到音量"分不清 | `DeviceCard.volume: Int = 0` |

---

## 1. 设备 uuid：查询宽容了，存储没归一（真 bug）

三处各造各的：

```kotlin
// ConsoleViewModel.kt:901  手动填 IP 添加
uuid = found.udn?.removePrefix("uuid:") ?: ip

// ConsoleViewModel.kt:918  扫描结果点「添加」
uuid = device.uuid?.substringAfterLast(':')?.removePrefix("uuid:") ?: device.ip

// ConsoleViewModel.kt:568  组内从机
uuid = sl.uuid?.removePrefix("uuid:")
```

实算一次（真机 `.153` 的 UDN）：

```
输入            uuid:FF31F017-4FE5-C9B2-A2AC-EAE3FF31F017
:901 的结果     FF31F017-4FE5-C9B2-A2AC-EAE3FF31F017   ← 32 位带连字符
:918 的结果     FF31F017-4FE5-C9B2-A2AC-EAE3FF31F017   ← 同上（substringAfterLast 没起作用）
getStatusEx 给的 FF31F0174FE5C9B2A2ACEAE3              ← 24 位无连字符
```

**后果**：同一台音箱「扫描添加」和「手动加 IP 添加」会生成两条不同 uuid 的记录。
`DeviceList.upsert` 按 uuid 去重，两条都留下 → 首页出现两台同名设备；
而 `SessionStore` 用 `deviceUuid` 当 subject → 播放快照、NAS 记忆也各存一份。

**为什么之前没暴露**：我在**查询**侧加了 `UuidMatch.same()`（归一化后比前缀），
所以电量/声道能对上。但**存储**侧没改，脏数据照样写进去。
这正是"只修了症状没修源头"。

**修法**：`SavedDevice` 只接受 `UuidMatch.key()` 归一化后的值，
三处造值点全部改调一个 `DeviceIdentity.of(udn/uuid/ip)`；
已有记录在 `DeviceList.decode` 时归一化 + 合并重复。

---

## 2. 队列归属：13 处写入，只有 3 处设过 owner

`QueueBus.restore()` 内部写的是 `ownerIp = currentOwner`（**当前值**，可能是 null 或上一台设备）：

```kotlin
// QueueBus.kt:137
fun restore(items, index, title, deviceOwned, offset, note) = _state.update {
    QueueRunState(ownerIp = currentOwner, ...)
}
```

而 `setOwner` 只有 4 次调用（`QueueStarter.kt:68/94/104`、`PlaybackViewModel.kt:198`），
写入却有 13 处：

| 写入点 | 先 setOwner？ |
|---|---|
| `QueueStarter.kt:69 / :95` | ✅ |
| `PlaybackViewModel.kt:380 / :720 / :740` | ✅（198 设过） |
| `NasViewModel.kt:426 / :437` | ❌ 用上一次的 owner |
| `ConsoleViewModel.kt:430 / :453 / :1469` | ❌ |
| `QueuePlaybackService.kt:143` | ❌ |
| `QueueBus.begin()` | ❌（内部也不设 owner） |

**后果**：从 NAS 起播时，如果 owner 还停在上一次的设备上，播放页会显示那台设备的队列 ——
**和 PR #40 修掉的那个 bug 完全同类**，只是当时只改了 QueueStarter 这一条路。

**修法**：`restore/begin/playing` 强制要求传 `deviceIp`（编译期强制，不靠自觉），
内部 `setOwner` 后再写；`currentOwner` 变成私有。

---

## 3. 音量：三条路，行为不同

| 入口 | 走哪条 | 代码 |
|---|---|---|
| 手机音量键 | **httpapi 直发**，绕过 UPnP | `ConsoleViewModel.kt:1248` |
| 卡片/组内拖动 | `DeviceVolumeControl`（UPnP 优先 + 兜底） | `ConsoleViewModel.kt:359/370` |
| 播放页滑块/语音 | `PlaybackEngine.setVolume` → 引擎内部再选 | `PlaybackViewModel.kt:543/556` |
| 组内从机 | `multiroom:SlaveVolume`（只能这样） | `ConsoleViewModel.kt:1730` |

`:1248` 那段：

```kotlin
runCatching { client.send(ip, LinkPlayCommands.playerVolume(next)) }
```

同一台音箱，按音量键走 httpapi、拖滑块走 UPnP。真机上这两条通道**可能不同步**
（`DeviceVolumeControl` 的注释里自己写了"界面 60、实际 40"）——
所以"按键调完再拖滑块"会出现跳变。

**修法**：音量键也走 `DeviceVolumeControl.setVolume`（它是 UPnP 优先 + 兜底，本来就是最稳的）。
`DeviceVolumeControl` 提升为唯一入口，`PlaybackEngine.setVolume` 内部改调它。

---

## 4. 能力档存了两份状态（不是矛盾，是重复）

**先纠正我自己的初判**：我一开始以为两处默认值相反、界面会给电视盒子渲染无效按钮。
查了调用点，不是这样 —— 两者回答的是**不同问题**，默认值也是各自刻意的：

```kotlin
// DeviceProfileCache.kt:35  用于"要不要先试设备侧 PlayQueue"（起播路径）
// 没探测过 → 按 LINKPLAY 试，失败自动降级。宁可按功能全开，不因为一次没探测到就关掉设备队列
fun of(ip: String): DeviceProfile = profiles[ip] ?: DeviceProfile.LINKPLAY

// MainActivity 的渲染判据是 `profile == GENERIC_DMR` 才隐藏入口
// 所以 UNKNOWN 等价于"全部渲染"，与上面并不冲突
```

真正的隐患是**状态存了两份**：`DeviceProfileCache` 按 **IP** 索引，
`DeviceCard.profile` 按 **uuid** 索引。IP 漂移（`.164` → `.153` 实测过）后
缓存里会留一条旧 IP 的记录，而卡片按 uuid 拿到的是新探测结果 ——
两者可能一个说 LINKPLAY、一个说 GENERIC_DMR。

**修法**：能力档只保留一份（挂到 uuid 上，IP 只是查找键），
或者干脆并进 `DeviceSnapshot.profile`（它已经是唯一取数入口，顺手就能带上）。

---

## 5. 错误文案直出异常

12 处 `error = t.message`：

```
NasViewModel.kt:244 / :279
LibraryViewModel.kt:131 / :206 / :275 / :295 / :415
QueueViewModel.kt:201
RadioViewModel.kt:205
QueuePlaybackService.kt:184
DevicePlaylistGateway.kt:91
ConsoleViewModel.kt:827
```

用户会在界面上看到 `HTTP 500 from 192.168.99.153: 401 invalid action`。
`SoapFault` 翻译层只覆盖了播放路径（`UpnpFault`），其余全是裸异常。

**修法**：抽 `UserMessage.of(Throwable)`，把 `IOException` / `SoapFaultException` /
`IllegalStateException` 统一翻成人话，原始消息只进 `IssueLog`（诊断页能看）。

---

## 6. 时长格式化 4 套

```
UpnpModels.PositionInfo.formatTime    ← 正确处理小时
NasViewModel.formatDuration           ← 委托上面 ✅
PlaybackViewModel.format              ← 委托上面 ✅
QueueScreen.kt:341                    ← 自己写 "%d:%02d"（无小时）
OnlineScreen.kt:185                   ← 自己写 "%d:%02d"（无小时）
```

超过 1 小时的曲目在两处会显示成 `73:20` 而不是 `1:13:20`。
另有 `DidlLiteBuilder.formatDuration`（ISO 8601 时长，不同用途，保留）。

**修法**：删掉两处私有实现，统一调 `PositionInfo.formatTime`。

---

## 7. "未知"的语义

```kotlin
data class DeviceCard(
    val volume: Int = 0,     // 0 是"真静音"还是"还没读到"？
    ...
)
```

界面上音量条会显示 0 —— 用户分不清"音箱音量 0"和"还没连上"。
同理 `channel: Int?`（null=没报）已经在诊断报告里区分了，但卡片没区分。

**修法**：`volume` 改 `Int?`，界面 null 显示 `--`。这是个小改动，但影响所有音量显示点。

---

## 建议的执行顺序（按"用户能感知"排）

| 批次 | 内容 | 理由 |
|---|---|---|
| **P1** | #1 uuid 存储归一 + #2 队列 owner 强制 | 都是**真 bug**，会造成"两台一样的设备""队列串台"，且改动集中在协议层 + 可单测 |
| **P2** | #3 音量统一入口 + #4 能力档统一 | 用户能立刻感知（音量不再跳、盒子不再出现无效按钮） |
| **P3** | #5 错误文案 + #6 时长格式化 + #7 未知语义 | 打磨，但每条都能直接看到效果 |

每批都会：纯函数 + 单测 → 全量测试 → PR → APK。
P1 的 uuid 归一还要**兼容已有脏数据**（`DeviceList.decode` 归一化 + 合并重复），不能要求用户重装。
