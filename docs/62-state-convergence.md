# 62 · 播放状态收敛：四份合成一份

> 配合 `docs/60`（播放器与推流统一）第四步使用。**这是整个重构里最难的一步。**
>
> 依据的代码状态：提交 `e802417`（712 单测 0 失败）。以代码为准。

---

## 一、先看重叠：同一个事实存了几份

| 事实 | 存在哪几份 | 份数 |
|---|---|---|
| **音量** | `PlaybackUiState.volume` / `PlaybackSnapshot.volume` / `DeviceCard.volume` | **3** |
| **静音** | `PlaybackUiState.mute` / `PlaybackSnapshot.mute` / `DeviceCard.mute` | **3** |
| **曲名** | `PlaybackUiState.title` / `QueueRunState.nowPlaying` / `DeviceCard.title` | **3** |
| **播放状态** | `PlaybackUiState.state` / `QueueRunState.running` / `DeviceCard.playing` / `ConsoleUiState.status.playState` | **4** |
| **队列位置** | `PlaybackUiState.deviceQueueIndex` / `QueueRunState.index` / `DeviceCard.plicurr` | **3** |
| **队列长度** | `PlaybackUiState.deviceQueueCount` / `QueueRunState.total` / `DeviceCard.plicount` | **3** |
| **进度** | `PlaybackUiState.positionSec` / `PlaybackSnapshot.positionSec` | 2 |

**音量三份是最危险的**：调一次音量要更新三处，任何一处漏了，界面上就出现
"滑块 45、卡片 39、设备实际 52"这种三头不对的状态。而且**没有单测能发现**——
三处各自都是"对的"，只是互相不一致。

---

## 二、为什么 `PlaybackUiState` 有 30 多个字段

不是字段多，是**六个不同的关注点挤在一个类里**：

| 关注点 | 字段 | 该归谁 |
|---|---|---|
| ① 通道探测 | `preparing` `ready` `unavailableReason` `channel` | 设备运行时 |
| ② 设备状态 | `friendlyName` `sourceLabel` `sourceId` `mode` | 设备运行时 |
| ③ **播放会话** | `state` `title` `artist` `albumArtUrl` `positionSec` `durationSec` `volume` `mute` `loopMode` | **会话快照** |
| ④ 队列位置 | `deviceQueueCount` `deviceQueueIndex` | **会话快照**（和 QueueBus 合并） |
| ⑤ 投送参数 | `castUrl` `castNeedsHeaders` `castReferer` | 投送层 |
| ⑥ 媒体附加 | `embeddedCover` `lyrics` `tagsLoadedFor` | 独立的带缓存读取 |
| ⑦ 界面提示 | `busy` `error` `notice` `log` | 界面瞬时态 |

**七个关注点，30 多个字段。** 这才是它臃肿的真相。

所以"收敛"不是简单合并，是**先按关注点拆开，再把 ③④ 合并成唯一真相**。

---

## 三、目标结构：五个数据类，各管一件事

```kotlin
// core-protocol/session/SessionSnapshot.kt  ← 纯 JVM，可单测
/**
 * 播放会话的唯一真相。**输出方式无关** —— 不管声音从音箱出来还是手机出来，
 * 界面读的都是这一个。
 */
data class SessionSnapshot(
    val target: OutputTarget?,          // 输出目标：设备(ip) / Local / null(未知)
    val state: PlaybackState,           // Idle / Preparing / Playing / Paused / Error
    val title: String?,
    val artist: String?,
    val albumArtUrl: String?,
    val positionSec: Long,
    val durationSec: Long,
    val volume: Int,                    // 0-100，语义由 target 决定（设备音量 / 系统媒体音量）
    val mute: Boolean,
    val loopMode: LoopMode,
    val queueIndex: Int,                // 0 基，-1 = 未知
    val queueTotal: Int,
    val queueHeldByOutput: Boolean,     // 输出方自己握着队列（PlayQueue 模式）
) {
    val progress: Float get() = if (durationSec <= 0) 0f
        else (positionSec.toFloat() / durationSec).coerceIn(0f, 1f)
}
```

```kotlin
// app/.../playback/DeviceRuntime.kt  ← 设备特有，不属于会话
data class DeviceRuntime(
    val channel: String,                // "UPnP" / "HTTP API"
    val ready: Boolean,
    val unavailableReason: String?,
    val friendlyName: String?,
    val sourceLabel: String?,
    val sourceId: String?,
    val mode: Int?,
)
```

```kotlin
// app/.../playback/PlaybackUiState.kt  ← 只剩界面瞬时态
data class PlaybackUiState(
    val snapshot: SessionSnapshot = SessionSnapshot.empty(),
    val device: DeviceRuntime? = null,
    val extras: MediaExtras = MediaExtras(),
    val busy: Boolean = false,
    val error: String? = null,
    val notice: String? = null,
    val log: List<String> = emptyList(),
)
```

**关键**：`SessionSnapshot` 放在 `core-protocol`（纯 JVM），
这样它能被单测覆盖，而且**将来本地播放的 `LocalSink` 也用同一个类型**。

---

## 四、四步走（每步都能单独验证）

### 第一步 · 新建 `SessionSnapshot` + 转换函数，**并存不删**

```kotlin
fun SessionSnapshot.Companion.from(
    ui: PlaybackUiState,
    queue: QueueRunState,
    device: DeviceCard?,
): SessionSnapshot
```

这一步**不删任何字段**，只是新增一个"把现有四份折算成一份"的纯函数。

**验证方式**：单测里造 20 组 `(PlaybackUiState, QueueRunState, DeviceCard)` 组合，
断言折算结果符合预期。**这就是"合并后信息没丢"的证明。**

这一小步就能暴露重叠字段的**真实冲突**——比如 `PlaybackUiState.volume=45` 但
`DeviceCard.volume=39` 时，折算函数该取哪个？

**必须在文档里定死规则**：

| 字段 | 取谁 | 为什么 |
|---|---|---|
| `volume` / `mute` | **设备真值优先**：`DeviceCard` > `PlaybackSnapshot` > `PlaybackUiState` | 项目铁律：界面显示设备报的值 |
| `title` | `PlaybackUiState` > `DeviceCard` > `QueueRunState` | 播放页的元数据最全（内嵌标签读出来的） |
| `state` | `PlaybackUiState.state`（传输态最准） | 它来自 `GetTransportInfo`，比 `playing: Boolean` 精确 |
| `queueIndex` / `queueTotal` | `queueHeldByOutput` 时取设备值，否则取 `QueueRunState` | 设备握着队列时以设备为准 |

### 第二步 · `PlaybackViewModel` 内部改成"snapshot 是唯一计算源"

现在 `PlaybackUiState` 的字段是**各处直接写**的（轮询回来逐个 `copy`）。
改成：轮询回来先算出 `SessionSnapshot`，再让 `PlaybackUiState` 从它派生。

```kotlin
// 之前
_state.update { it.copy(state = transport.state, positionSec = pos.relTimeSec, volume = v.volume) }

// 之后
_state.update {
    val snap = it.snapshot.copy(state = transport.state, positionSec = pos.relTimeSec, volume = v.volume)
    it.copy(snapshot = snap)
}
```

界面还读旧字段（`s.state` / `s.volume`），但旧字段变成 snapshot 的**投影**：

```kotlin
data class PlaybackUiState(...) {
    val state: TransportState get() = snapshot.state.toTransportState()
    val volume: Int get() = snapshot.volume
    // 旧字段变 getter，界面一行不用改
}
```

**这是关键技巧**：旧字段改成 `get()` 转发到 snapshot，**界面零改动**，
但数据源头已经统一了。改完之后，三份音量实际上只剩一份。

**验证**：712 个单测全过 + 界面行为不变。

### 第三步 · 界面逐个字段换（一个字段一个 PR 也行）

界面从 `s.volume` 改成 `s.snapshot.volume`。听起来只是换个路径，
但它让"界面依赖会话快照"这件事变成**显式的**。

**为什么值得做**：现在界面读 `s.volume` 看不出这个值从哪来；
改成 `s.snapshot.volume` 之后，读代码的人立刻知道"这是会话真相"。

而且这一步做完了，第四步（删旧字段）才安全。

### 第四步 · 删旧字段 + 删重复的状态源

- `PlaybackUiState` 里那 12 个会话字段全删（界面已经改读 snapshot）
- `DeviceCard.playing` 删（改为 `session.snapshot().state == PLAYING`）
- `QueueRunState.nowPlaying` 删（改为读 snapshot）

**这一步要谨慎**：`DeviceCard` 是卡片用的，卡片是**多台设备并排**的，
每台都有自己的播放状态。而 `SessionSnapshot` 是"当前选中那台"的。

**所以 `DeviceCard.playing` 不能直接删** —— 它和 SessionSnapshot 的语义不同：
- `SessionSnapshot` = 我正在控制的那一台
- `DeviceCard.playing` = 列表里每一台各自在不在放

**这是个重要的区分**，我一开始也差点搞错。文档里记下来。

---

## 五、风险与缓解

| 风险 | 缓解 |
|---|---|
| 界面 842 行大量引用旧字段 | 第二步用 `get()` 转发，界面零改动；第三步才逐个换 |
| 折算规则定错 → 界面显示错值 | 第一步的 20 组单测锁死规则 |
| `DeviceCard.playing` 语义混淆 | 明确：卡片是"多台各自的"，snapshot 是"当前这台" |
| 改到一半中断 | 每一步都独立可交付，第二步做完就已经"数据源头统一"了 |
| 没有真机验证 | 每步都要在诊断里加对比（改前/改后的值），用户装上就能看出差异 |

---

## 六、顺序与依赖

```
第一步  SessionSnapshot + from()（纯新增，零风险）
          ↓
第二步  PlaybackViewModel 内部以 snapshot 为源，旧字段变 getter
          ↓ ← 到这里"三份音量"实际上已经是一份了
第三步  界面逐个改读 snapshot
          ↓
第四步  删旧字段
          ↓
第五步  本地播放（LocalSink）
```

**第二步做完就能拿到 80% 的收益**（数据源头统一），
第三、四步是"让依赖关系显式化"，可以慢慢做。

---

## 七、这一步和内容源标准化的关系

**没有直接依赖，但顺序不能反。**

- 内容源标准化改的是"内容从哪来"
- 状态收敛改的是"在放什么、怎么显示"

如果先做状态收敛，内容源那边还在改推流路径，两边的状态定义会打架。
所以 `docs/60` 定的顺序是：内容源 → 输出目标 → 输出层 → **状态收敛** → 本地播放。

---

## 八、一条提醒

这一步的难点不是技术，是**定规则**：

> `PlaybackUiState.volume=45` 和 `DeviceCard.volume=39` 同时存在时，界面该显示哪个？

这个问题的答案不是"代码怎么写"，是"**哪个是真相**"。

项目的既有铁律给了答案：**界面显示设备报的值**（`docs/38` 电量问题、
`docs/39` 取数最短路径都是这个原则）。

所以折算规则里 `DeviceCard` > `PlaybackUiState`。这条要写死在
`SessionSnapshot.from()` 的注释里，否则半年后又会有两套说法。
