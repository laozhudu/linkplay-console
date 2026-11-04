# 60 · 播放器与推流能否统一为一块

> 用户 2026-11-04：「播放器模块能否与推流统一为一块」
>
> **结论：能，而且应该统一 —— 但不是合并成一个类，是分成「会话」和「输出」两层。**
>
> 依据的代码状态：提交 `ad80431`（712 单测 0 失败）。本项目重构很快，以代码为准。

---

## 一、先看一个反例：NAS 又写了一遍推流

`QueueStarter.startQueueOnDevice` 的注释写着"全 App 唯一入口"，
三段降级（PlayQueue → m3u → 逐首）也写在里面。

但 `NasViewModel` 根本不用它：

```
NasViewModel:418   DevicePlaylistGateway.push(rendererIp, rendererLocation, items, start)
NasViewModel:429   DeviceQueuePush.push(DeviceHttpClient(), rendererIp, items, start)
```

**它自己把同样的降级链又写了一遍。**

这就是"播放器和推流没统一"的代价：同一件事三份实现，改一处漏两处。

---

## 二、播放状态现在有 4 份

| # | 在哪 | 装什么 |
|---|---|---|
| 1 | `QueueBus.state`（`QueueRunState`） | 队列列表、第几首、nowPlaying、归属 IP、设备持有 |
| 2 | `PlaybackEngine.snapshot()`（`PlaybackSnapshot`） | 传输态、位置、时长、音量、静音 |
| 3 | `PlaybackViewModel` 的 `PlaybackUiState` | 通道名、输入源、**设备队列长度/序号**、传输态、曲目 |
| 4 | `ConsoleUiState.status` + `DeviceCard.playing` | 设备全量状态、卡片上那个"在放吗" |

四份都在描述"现在在放什么"，各自的字段还有重叠（`state` / `title` / `positionSec` / `volume` 至少三份都有）。

**这是 docs/41 那类"两份真相"病的放大版。**

如果只是把"播放器模块"和"推流模块"合成一个目录，而状态还是四份，**统一就是假的**。

---

## 三、能统一的基础：已经有三块拼图

### 拼图 1 · `PlaybackEngine` 接口已经证明了"控制语义"是共通的

```kotlin
interface PlaybackEngine {
    val channelName: String
    fun snapshot(): PlaybackSnapshot
    fun playUrl(url: String, title: String?)
    fun resume() / pause() / stop() / seek(seconds) / setVolume(0-100) / setMute()
    fun next() / previous()
}
```

现有两个实现都是**遥控设备**的（`UpnpPlaybackEngine` / `HttpApiPlaybackEngine`），
但方法签名对**本地播放同样成立**（详见 `docs/58`）。

说明「播放/暂停/切歌/跳转/音量」这套语义是输出方式无关的。

### 拼图 2 · `QueueRunState` 已经在建模"队列归属"

```kotlin
val deviceOwned: Boolean = false   // 队列由设备持有（推了 m3u 之后）
val ownerIp: String? = null        // 队列属于哪台设备
```

`deviceOwned` 的注释写得很清楚：

> `running` 表示"我们自己的前台服务正在逐首投流"。
> 设备持有列表时我们不需要任何后台循环，App 可以退出。

**这个区分对本地播放同样需要**：本地播放时 App 握着队列（退出就停），
所以 `deviceOwned = false, running = true` —— 语义能对上。

### 拼图 3 · 内容源已经在产出统一格式

`MediaTrack`（id/title/artist/album/duration/providerId/coverId）
是输出方式无关的。推给设备要 `resolve()` 成 URL，本地播放同样要 URL。

---

## 四、不能简单合并的三件事

| 差异 | 推流 | 本地 |
|---|---|---|
| 队列谁握着 | 两种（设备握着 / App 握着） | 只有 App 握着 |
| 需要中继服务器 | 要（`RelayMediaServer` 转发 https、`LocalMediaServer` 供本地文件） | 不要 |
| 状态从哪来 | 轮询设备（`getPlayerStatus` / `GetTransportInfo`） | 播放器回调 |
| App 退出后 | 设备继续放（PlayQueue 模式） | 停 |
| 音量语义 | 设备音量 | 系统媒体音量 |

**这些差异不能消灭，只能隔离。**

所以统一的方式不是"合成一个类"，而是**分层**。

---

## 五、建议结构：会话层 + 输出层

```
┌─ 会话层 PlaybackSession ────────────────────────────────┐
│  队列是什么（List<Track>）                              │
│  现在第几首、什么状态（Idle/Preparing/Playing/Paused/Error）│
│  控制语义：play / pause / next / previous / seek        │
│  快照：snapshot(): SessionSnapshot                      │
│                                                          │
│  ↑ 这一层**只有一份真相**，界面只读它                    │
└──────────────────────────────────────────────────────────┘
              │ 只依赖「输出目标」这个概念
              ▼
┌─ 输出层 OutputSink ─────────────────────────────────────┐
│  sealed interface OutputTarget                          │
│    ├─ Device(ip)      → DeviceSink（推流）              │
│    └─ Local           → LocalSink（本地，未来）          │
│                                                          │
│  interface OutputSink {                                  │
│      val target: OutputTarget                            │
│      suspend fun open(queue, startIndex): SinkHandle     │
│  }                                                       │
│                                                          │
│  DeviceSink 内部：整单下发 + 三段降级 + 中继服务器        │
│  LocalSink  内部：ExoPlayer                              │
└──────────────────────────────────────────────────────────┘
```

### 为什么这样分

**统一的是"会话与语义"，隔离的是"实现"。**

- 界面只跟 `PlaybackSession` 打交道 → 换输出方式不用改界面
- `DeviceSink` 内部可以继续复杂（三段降级、中继服务器都在里面）→ 复杂度不外溢
- 加本地播放 = 加一个 `LocalSink`，会话层和界面 0 行改动

---

## 六、具体改动点（可执行）

### 改动 1 · 队列归属从"IP"变成"输出目标"

现在是：

```kotlin
val ownerIp: String? = null      // 本地播放没有 IP，这个字段装不下
```

改成：

```kotlin
sealed interface OutputTarget {
    data class Device(val ip: String) : OutputTarget
    data object Local : OutputTarget
}

val owner: OutputTarget? = null
```

**这是本地播放能否接入的关键一处。** 现在 `ownerIp` 是 `String`，
本地播放只能塞 null，而 null 的语义是"不知道归属"——会跟"真的不知道"混在一起。

用 sealed 之后，加 `Local` 时所有 `when` 会编译报错提醒补分支。

### 改动 2 · `deviceOwned` 改名，语义更准

```kotlin
val deviceOwned: Boolean      // 现在
val queueHeldByOutput: Boolean // 改成这个
```

语义：**输出方自己握着队列**。
- 设备 + PlayQueue 模式：true（App 退出照播）
- 设备 + 逐首投流模式：false（App 握着）
- 本地：false

改名不是为了好看，是因为"device"这个词在本地播放接入后就不准确了。

### 改动 3 · 加 `OutputSink`，把推流收进去

`DeviceSink` 收进现在散落的：

| 现有文件 | 行数 | 归到哪 |
|---|---|---|
| `QueueStarter` | 115 | `DeviceSink.open()` |
| `DeviceQueuePush` | 106 | `DeviceSink` 内部 |
| `DevicePlaylistGateway` | 128 | `DeviceSink` 内部 |
| `RelayMediaServer` + `LocalMediaServer` | ~500 | `DeviceSink` 的依赖 |

**注意**：PR #67 刚给这两个 server 加了断流兜底（`SocketException` 不再当崩溃）
和推流统计（`CountingOutputStream`）。这恰好印证了本节的说法 ——
**推流这块的复杂度是真实的，不能靠"统一"消灭，只能靠"隔离"关进 `DeviceSink` 里。**

**NAS 不再直接调 `DeviceQueuePush`**，改走 `DeviceSink`。

### 改动 4 · 状态收敛到一份

这是**最难也最值钱**的一步。

现在 4 份状态（见第二节），收敛方案：

| 保留 | 删掉/合并 |
|---|---|
| `PlaybackSession.snapshot()`（新，唯一真相） | `PlaybackUiState` 里的 state/title/position/duration/volume（改为从 snapshot 派生） |
| | `DeviceCard.playing`（改为 `session.snapshot().state == PLAYING`） |
| | `PlaybackSnapshot`（并入 `SessionSnapshot`） |

**注意**：`ConsoleUiState.status`（`LinkPlayStatus`）不能删 —— 那是设备的**全量状态**
（固件、电量、SSID、组网），不只是播放。它和"播放会话"是两回事。

---

## 七、风险

| 风险 | 说明 | 缓解 |
|---|---|---|
| **动 `QueueBus` 有前科** | PR #55 刚给它加过编译期强制（所有写入必须传 `deviceIp`），13 处调用点全改过 | 改 `ownerIp → owner` 时同样靠编译器报错，不手工找 |
| **NAS 那条路径行为会变** | 它现在自己走 `DevicePlaylistGateway`/`DeviceQueuePush`，收进 `DeviceSink` 后降级顺序可能不同 | 先补 NAS 的诊断（现在完全没有），能对比改前改后 |
| **状态收敛会碰界面** | `PlaybackUiState` 的字段被 `PlayerScreen`（842 行）大量引用 | 分两步：先加 `snapshot()` 并存，再逐步切换引用，最后删旧字段 |
| **两个前台服务** | `QueuePlaybackService`（dataSync）和未来本地播放的 `mediaPlayback` | 统一时就要想清楚：一个服务管两种输出，还是两个服务各管一种 |

---

## 八、顺序建议

```
第一步  内容源标准化（那边在做）
          ↓
第二步  改 ownerIp → OutputTarget（小，编译器能兜住）
          ↓
第三步  OutputSink 抽出来，DeviceSink 收推流三件套 + NAS 改走它
          ↓
第四步  状态收敛到 PlaybackSession.snapshot（最难，要动界面）
          ↓
第五步  本地播放（LocalSink）
```

**第二步必须在第三步之前**：先让"输出目标"这个概念存在，`OutputSink` 才有意义。

**第四步必须在第五步之前**：本地播放接入时如果状态还有四份，就会出现
"界面显示在放、实际没声"，而且查不出来是哪一份错了。

---

## 九、结论

| 问题 | 答案 |
|---|---|
| 能统一吗 | ✅ **能**，而且应该 —— 现在是三份推流实现 + 四份状态 |
| 怎么统一 | **分两层**：会话层（唯一真相）+ 输出层（推流/本地各一个 Sink） |
| 合并成一个类行不行 | ❌ **不行** —— 推流要中继服务器、设备握着队列、轮询状态，本地都不需要 |
| 最难的是什么 | **状态收敛**（4 份 → 1 份），比模块拆分难得多 |
| 什么时候做 | 内容源标准化之后。且必须"先补诊断再动" |
