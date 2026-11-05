# 施工图：从现状到「一个意图 → 一份实现」

> **依据代码：`f26e0a4`（#139）**
> 本文是把 `docs/86`（最短路径）、`docs/87`（BOM）、`docs/88`（第三轮）、`docs/89`（任何源×任何输出）、
> 以及两份工作区方案（`方案-总体架构与护栏.md`、`方案-全局编排层.md`）**收敛成一份可执行的施工图**。
>
> **零件够了，这份是图纸。** 每一道工序都写了：改哪个文件、改什么、怎么验、怎么退。
>
> ⚠️ 本文所有行号基于 `f26e0a4`，动手前先 `git log -1` 确认没漂。

---

# 第 0 章 · 怎么用这份图纸

## 0.1 三条铁律（工序必须遵守）

| # | 铁律 | 违反的后果 |
|---|---|---|
| **R1** | **一道工序 = 一个 PR，可独立回退** | 出问题只能整批回退，前面的活白干 |
| **R2** | **每道工序先跑验收命令，再进下一道** | 错误会往后传，越传越贵 |
| **R3** | **不动 `core-protocol` 的公开 API**（除 S1.1 / S2.6 两处明确标注的） | 底座是 613 个单测的地基 |

## 0.2 工序总表（12 道）

| 工序 | 名称 | 依赖 | 预估 | 改文件数 |
|---|---|---|:-:|:-:|
| **S0.1** | 建护栏注解 | — | 0.5h | 1 新 |
| **S0.2** | 三个原语打标记 | S0.1 | 0.5h | 3 |
| **S0.3** | 反向验证编译门禁 | S0.2 | 0.5h | 0 |
| **S0.4** | 两个 CI 检查（error） | S0.3 | 1h | 1 新 + CI |
| **S0.5** | 两个 CI 检查（warn） | S0.4 | 0.5h | 2 新 + CI |
| **S1.1** | `PlayIntent` 落地 | S0.x | 2h | 1 新 + 2 |
| **S1.2** | `dispatch` 唯一执行点 | S1.1 | 3h | 1 |
| **S1.3** | 三入口接入 dispatch | S1.2 | 2h | 2 |
| **S1.4** | 输出开关搬到播放页 | S1.3 | 2h | 2 |
| **S2.1** | 统一播放状态 | S1.x | 1d | 4 |
| **S2.2** | `UpnpPlayer` + MediaSession | S2.1 | 2d | 1 新 + 2 |
| **S2.3** | 能力位成为唯一判据 | S2.2 | 1d | 1 + N |
| **S3.1** | 三个 UseCase | S2.x | 2d | 3 新 + 9 |

**总计约 9–11 个工作日。** 每道工序都能单独上线、单独回退。

## 0.3 顺序为什么不能换

```
S0 护栏 ──→ S1 编排 ──→ S2 柱子 ──→ S3 UseCase
  │           │           │           │
  │           │           │           └─ 收重复：前提是重复的地方已被 dispatch 收拢
  │           │           └─ 状态收口：前提是已有唯一执行点
  │           └─ 执行点：前提是原语被锁住，新代码进不来
  └─ 锁原语：最便宜，收益是「永远」
```

**反过来做的具体后果**：
- 先 S2 再 S1 → `PlayerUiState` 会变成没人用的空壳（**现在就是这样**）
- 先 S3 再 S1 → 会收错，因为有些「重复」是意图没统一造成的，不是逻辑没抽出造成的

---

# 第 1 章 · 现状基线（施工前必须核对这些数）

## 1.1 文件与行数

```
app/src/main                    20,000 行 / 96 文件
playback 包                      3,391 行 / 15 文件
  PlaybackViewModel.kt             897
  PlayerScreen.kt                  882
  QueuePlaybackService.kt          304
  QueueBus.kt                      259
  PlaybackRouter.kt                168
  DevicePlaylistGateway.kt         128
  QueueStarter.kt                  115
  DeviceQueuePush.kt               106
  local/LocalPlayer.kt              96
  DeviceVolumeControl.kt            94
  VolumeBus.kt                      63
  PlaybackService.kt                60
  DeviceProfileCache.kt             59
  VolumeOsd.kt                      37
  VolumeOverlay.kt                 123

core-protocol                   16,714 行 / 150 文件
```

## 1.2 施工要动的全部调用点（这是图纸的坐标）

```
playOne          1 处
  LxSearchViewModel.kt:163

playQueue        4 处
  NasViewModel.kt:437
  LibraryViewModel.kt:385
  QueueViewModel.kt:195
  OnlineViewModel.kt:202

startQueueOnDevice  3 处
  PlaybackRouter.kt:94   （playOne 内部）
  PlaybackRouter.kt:135  （playQueue 内部）
  PlaybackViewModel.kt:514

setOutput        2 处
  LxSearchScreen.kt:90
  LxSearchScreen.kt:98

QueueItem( 拼装  5 处
  NasViewModel.kt:400
  LibraryViewModel.kt:206
  LibraryViewModel.kt:274   ← 与 206 逐字重复
  QueueViewModel.kt:180
  OnlineViewModel.kt:181

音量             4 处
  PlaybackEngine.kt:51        setVolume(volume: Int)
  PlaybackViewModel.kt:614    setVolume(value)
  PlaybackViewModel.kt:627    setVolumeLive(value)
  DeviceVolumeControl.kt:44   setVolume(ip, volume)
  DeviceControl.kt:107        adjustVolumeByKey(delta)

悬浮窗直发 httpapi  3 处
  FloatingControlService.kt:204  previousTrack()
  FloatingControlService.kt:205  togglePause()
  FloatingControlService.kt:206  nextTrack()
```

## 1.3 施工前自检命令

```bash
cd /root/.hermes/workspace/linkplay-console
git log -1 --format='%h %s'          # 应为 f26e0a4 或其后
git status --short                    # 应为空
grep -rho '@Test' app/src/test core-*/src/test | wc -l    # 应为 1036
grep -rn 'DevicePlaylistGateway.push\|DeviceQueuePush.push\|QueuePlaybackService.start' --include=*.kt app/src/main | wc -l
                                      # 应为 3（都在 QueueStarter.kt）
```

**四个数对不上就别开工** —— 说明代码已经漂了，图纸的坐标失效。

---

# 第 2 章 · 目标结构

## 2.1 目录树（新增的用 ★ 标）

```
app/src/main/kotlin/io/github/laozhudu/linkplay/console/
├── playback/
│   ├── ★ guards/
│   │   └── ★ SingleEntryPoint.kt        ← 两个编译期注解
│   ├── ★ PlaybackDispatcher.kt          ← 唯一执行点（PlayIntent → 实现）
│   ├── PlaybackRouter.kt                ← 保留，降级为「输出选择 + 本地/远程分派」
│   ├── PlaybackViewModel.kt             ← 不动（设备控制的真实实现）
│   ├── QueueStarter.kt                  ← 不动（唯一下发入口）
│   ├── ★ UpnpPlayer.kt                  ← SimpleBasePlayer 子类（设备状态 → Player.State）
│   ├── PlaybackService.kt               ← 改：MediaSession 挂统一门面
│   ├── local/LocalPlayer.kt             ← 改：填全 PlayerUiState
│   ├── DevicePlaylistGateway.kt         ← 改：push 加 @SingleEntryPoint
│   ├── DeviceQueuePush.kt               ← 改：push 加 @SingleEntryPoint
│   ├── QueuePlaybackService.kt          ← 改：start 加 @SingleEntryPoint
│   └── FloatingControlService.kt        ← 改：删 httpapi 直发，改走 dispatcher
├── ★ usecase/
│   ├── ★ ToQueueItemsUseCase.kt         ← 收 5 处 QueueItem 拼装
│   ├── ★ SetVolumeUseCase.kt            ← 收 4 套音量
│   └── ★ PlayFromSourceUseCase.kt       ← 收 5 个 VM 的「播放整份列表」
└── ui/player/PlayerUiState.kt           ← 改：补 4 个字段

core-protocol/src/main/kotlin/io/github/laozhudu/linkplay/protocol/
├── ★ playback/PlayIntent.kt             ← 意图（从 voice/VoiceIntent 演进）
├── voice/IntentRouter.kt                ← 改：产出类型换成 PlayIntent
└── dlna/DeviceProfile.kt                ← 改：DeviceCapabilities 加两个根位
```

**新增文件 7 个，改动文件约 20 个，删除 0 个文件。**

## 2.2 每层的职责（一句话，防止职责漂移）

| 层 | 职责 | 不做什么 |
|---|---|---|
| `PlayIntent` | 描述「用户想干什么」，12 个 sealed 分支 | 不含任何执行逻辑、不碰 Context |
| `PlaybackDispatcher` | 意图 → 实现，按 output 和能力位分派 | 不实现播放逻辑，只做分派 |
| `PlaybackRouter` | 输出选择 + 本地/远程两条链 | 不再被界面直接调用（降级为 dispatcher 的内部件） |
| `QueueStarter` | 唯一下发入口（三段降级） | 不关心意图从哪来 |
| `UpnpPlayer` | 设备状态 → Media3 `Player.State` | 不自己发命令，转发给 `PlaybackViewModel` |
| `*UseCase` | 纯逻辑（数据类 → 数据类） | 不碰 Context、不做 IO |

---

# 第 3 章 · 接口契约（图纸的核心）

> **这一章是代码，不是描述。** 施工时照着写，不要自由发挥。

## 3.1 `SingleEntryPoint.kt`（新文件，S0.1）

```kotlin
package io.github.laozhudu.linkplay.console.playback.guards

/**
 * 标记「只能从唯一入口调用」的内部原语。
 *
 * ## 为什么用编译期而不是文档
 *
 * 「唯一入口」在 15 篇文档里写过 28 次，代码里被破了 4 次，修了 4 个 PR
 * （#130 #131 #132 #139）。写文档约束不住人 —— 但编译不过能约束住。
 *
 * ## 用法
 *
 * 标在原语上；调用方必须显式 `@OptIn(SingleEntryPoint::class)`。
 * 唯一入口自己加一次，其他人想调，编译器报错，逼他先解释「为什么这里可以绕过」。
 */
@RequiresOptIn(
    message = "这是唯一入口的内部原语。请调 PlaybackDispatcher / startQueueOnDevice，不要直连。",
    level = RequiresOptIn.Level.ERROR,
)
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
annotation class SingleEntryPoint

/**
 * 标记「只能由播放页写的输出选择」。
 *
 * 现状：开关长在洛雪页（LxSearchScreen:90,98），而 5 个源都受它影响 ——
 * 用户从 NAS 页想听手机上放，得跑去洛雪页切。S1.4 搬到播放页后转 ERROR。
 */
@RequiresOptIn(
    message = "输出选择只能从播放页改。",
    level = RequiresOptIn.Level.WARNING,
)
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.FUNCTION)
annotation class OutputSwitch
```

## 3.2 `PlayIntent.kt`（新文件，S1.1）

```kotlin
package io.github.laozhudu.linkplay.protocol.playback

/**
 * 用户想干的全部播放动作。
 *
 * ## 从 VoiceIntent 演进而来
 *
 * 原来叫 `VoiceIntent`，只服务语音（`PlayerScreen:865` 唯一使用点）。
 * 但「下一首」在界面 / 悬浮窗 / 通知栏各走各的，一共 4 条路径 ——
 * 因为意图层只给语音开了口。
 *
 * 改名的理由：**意图不关心它是说出来的还是点出来的。**
 *
 * ## 为什么仍是 sealed interface
 *
 * 分派端必须**穷尽**处理所有分支，新增意图时编译器直接报错，
 * 而不是运行时静默落到 else 变成「点了没反应」。（原 VoiceIntent 的注释，原样保留）
 */
sealed interface PlayIntent {

    data object Next : PlayIntent
    data object Previous : PlayIntent
    data object Play : PlayIntent
    data object Pause : PlayIntent
    data object Stop : PlayIntent

    /** 「播放/暂停」取决于当前状态，交给执行点按状态决定。 */
    data object TogglePlay : PlayIntent

    data object VolumeUp : PlayIntent
    data object VolumeDown : PlayIntent
    data object Mute : PlayIntent

    /** 循环模式切下一个（单曲/列表/随机）。 */
    data object LoopNext : PlayIntent

    /** 拖动音量条：定值而不是增量。S1.1 新增。 */
    data class SetVolume(val value: Int) : PlayIntent

    /** 拖动进度条：秒。S1.1 新增。 */
    data class SeekTo(val seconds: Long) : PlayIntent

    /** 想找某首/某人的歌：关键词交给搜索，不决定从哪个曲库搜。 */
    data class Search(val keyword: String) : PlayIntent

    /** 没听懂。**带上原文**是为了原样回显，让用户知道我们听到了什么。 */
    data class Unknown(val text: String) : PlayIntent
}
```

## 3.3 `PlaybackDispatcher.kt`（新文件，S1.2）

```kotlin
package io.github.laozhudu.linkplay.console.playback

/**
 * **全部播放意图的唯一执行点。**
 *
 * ## 为什么必须有这一层
 *
 * 「下一首」现在有 4 条路径：
 *   1 界面    PlayerScreen → vm.next()
 *   2 语音    IntentRouter → vm.next()
 *   3 悬浮窗  FloatingControlService → send(LinkPlayCommands.nextTrack())  ← 绕过一切
 *   4 通知栏  MediaSession(ExoPlayer) → 只对本机有效
 *
 * 路径 3 是 bug：LinkPlay 私有 httpapi，投通用 DLNA 渲染器时设备不认，
 * 而 DeviceCapabilities.supportsNextPrev 从来没被读过。
 *
 * ## 两条硬规矩
 *
 * 1. **能力位判断在这里做，不在界面做** —— 界面只问「这个意图能不能执行」。
 * 2. **不实现播放逻辑，只做分派** —— 真正的实现在 PlaybackRouter / PlaybackViewModel。
 */
class PlaybackDispatcher(
    private val app: Application,
    private val router: PlaybackRouter = PlaybackRouter.of(app),
    private val capabilities: (ip: String) -> DeviceCapabilities = { DeviceProfileCache.capabilitiesOf(it) },
) {

    /**
     * 执行一个意图。
     *
     * @return true 表示「这个意图在当前输出上能执行且已发出」。
     *   false 表示「这台设备没有这个能力」—— 界面据此**不渲染**对应按钮。
     */
    suspend fun dispatch(intent: PlayIntent): Boolean = when (intent) {
        PlayIntent.Next        -> next()
        PlayIntent.Previous    -> previous()
        PlayIntent.Play        -> play()
        PlayIntent.Pause       -> pause()
        PlayIntent.Stop        -> stop()
        PlayIntent.TogglePlay  -> toggle()
        PlayIntent.VolumeUp    -> volumeBy(+5)
        PlayIntent.VolumeDown  -> volumeBy(-5)
        PlayIntent.Mute        -> mute()
        PlayIntent.LoopNext    -> loopNext()
        is PlayIntent.SetVolume -> setVolume(intent.value)
        is PlayIntent.SeekTo    -> seekTo(intent.seconds)
        is PlayIntent.Search    -> false   // 搜索不归播放分派管，界面自己跳
        is PlayIntent.Unknown   -> false
    }

    /** 这个意图在当前输出上能不能执行。界面用它决定画不画按钮。 */
    fun can(intent: PlayIntent): Boolean = when (intent) {
        PlayIntent.Next, PlayIntent.Previous -> when (router.output()) {
            PlaybackOutput.Local -> true
            PlaybackOutput.Remote -> caps().supportsNextPrev
        }
        PlayIntent.SeekTo -> when (router.output()) {
            PlaybackOutput.Local -> true
            PlaybackOutput.Remote -> caps().supportsSeek
        }
        PlayIntent.SetVolume, PlayIntent.VolumeUp, PlayIntent.VolumeDown ->
            router.output() == PlaybackOutput.Local || caps().supportsVolume
        PlayIntent.Mute -> router.output() == PlaybackOutput.Local || caps().supportsMute
        PlayIntent.LoopNext -> router.output() == PlaybackOutput.Local
        else -> true
    }

    private fun caps(): DeviceCapabilities =
        capabilities(QueueBus.owner() ?: return DeviceCapabilities())

    // ---- 每个意图一个私有实现，dispatch 只做分派（防止它变成巨型 when）----
    private suspend fun next() = when (router.output()) {
        PlaybackOutput.Local -> { router.local.raw().seekToNext(); true }
        PlaybackOutput.Remote -> caps().supportsNextPrev.also { if (it) vm().next() }
    }
    // …previous / play / pause / stop / toggle / volumeBy / setVolume / seekTo / mute / loopNext 同构
}
```

> **施工提示**：`vm()` 指「当前设备对应的 `PlaybackViewModel`」。现状是 `PlayerScreen` 里 `remember { ... }` 出来的，
> S1.2 要把它提到 `PlaybackDispatcher` 或一个 `PlaybackViewModelHolder` 里 —— **这是本工序唯一有设计选择的地方，选最省事的：一个 object 持有 map<ip, PlaybackViewModel>。**

## 3.4 `PlayerUiState` 补四个字段（S2.1）

```kotlin
data class PlayerUiState(
    val title: String? = null,
    val artist: String? = null,
    val playing: Boolean = false,
    val hasQueue: Boolean = false,
    val controlLost: Boolean = false,
    val source: Source = Source.Remote,

    // ---- S2.1 新增：这才是「播到哪」 ----
    /** 当前位置毫秒；直播时是 0。 */
    val positionMs: Long = 0,
    /** 总时长毫秒；**0 表示未知**（直播流 / 还没 prepare 完）。 */
    val durationMs: Long = 0,
    /** 直播流：进度条显示「直播」，不显示时间、不许 seek。 */
    val isLive: Boolean = false,
    /** 0-100；**-1 表示不受我们控制**（本机播放交给系统音量）。 */
    val volume: Int = -1,
)
```

## 3.5 `UpnpPlayer`（新文件，S2.2）

```kotlin
package io.github.laozhudu.linkplay.console.playback

/**
 * 把「投到设备的播放」包装成一个 Media3 `Player`。
 *
 * ## 为什么要它
 *
 * MediaSession 现在挂的是 ExoPlayer（`PlaybackService:34`），
 * 所以投音箱时通知栏/锁屏/耳机键/蓝牙遥控**全是死的**。
 * PlaybackService 的注释早就写对了：「需要 MediaSession 换成一个把命令转发到设备的 Player 实现」。
 *
 * ## 为什么继承 SimpleBasePlayer 而不是实现 Player
 *
 * 官方：「reduces the number of methods to implement to a minimum」——
 * 只需 override getState()，其余方法按 availableCommands 自动忽略。
 * 实现 Player 要写 100 多个方法。
 *
 * ## 官方硬契约
 *
 * 「Player instances need to update the visible state immediately after each method call,
 *   even if the actual changes are handled on background threads or even other devices.」
 * → 所以我们**立刻**把状态改成「最可能的结局」，设备拒绝时再回滚。
 *   不能再靠「发命令 → 轮询 6 秒看 status」。
 */
class UpnpPlayer(
    looper: Looper,
    private val stateSource: () -> PlaybackUiState,   // 来自 PlaybackViewModel
    private val queueSource: () -> QueueRunState,     // 来自 QueueBus
    private val caps: () -> DeviceCapabilities,
    private val onIntent: suspend (PlayIntent) -> Boolean,
) : SimpleBasePlayer(looper) {

    override fun getState(): State = State.Builder()
        .setAvailableCommands(availableCommands())
        .setPlaylist(queueSource().items.map { MediaItemData.Builder(it.url).setMediaMetadata(...).build() })
        .setPlayWhenReady(stateSource().playing, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
        .setPlaybackState(if (stateSource().playing) Player.STATE_READY else Player.STATE_IDLE)
        .setContentPositionMs(stateSource().positionMs)
        .build()

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        scope.launch { onIntent(if (playWhenReady) PlayIntent.Play else PlayIntent.Pause) }
        return immediateVoidFuture()   // 立刻返回，状态由 invalidateState 更新
    }
    // handleSeek / handleSetVolume / handleSeekToNext / handleSeekToPrevious 同构
}
```

## 3.6 三个 UseCase（S3.1）

```kotlin
// usecase/ToQueueItemsUseCase.kt
/** 「源里的一条 → 可投送的一条」。纯函数，收掉 5 处手写。 */
class ToQueueItemsUseCase(
    private val resolveUrl: suspend (SourceTrack) -> String?,   // 各源注入
) {
    operator suspend fun invoke(tracks: List<SourceTrack>): List<QueueItem>
}

// usecase/SetVolumeUseCase.kt
/** 所有音量改动的唯一入口。收掉 4 套实现 + 两条下发链。 */
class SetVolumeUseCase(
    private val engine: () -> PlaybackEngine?,
    private val cardVolume: (ip: String, v: Int) -> Boolean,
) {
    /** 定值（slider 松手 / 卡片拖动）。 */
    operator suspend fun invoke(value: Int): Int
    /** 增量（音量键 / 语音）。返回 null 表示「不该吞掉按键事件」。 */
    suspend fun byKey(delta: Int): Int?
}

// usecase/PlayFromSourceUseCase.kt
/** 「从某个源播放一整份列表」。收掉 5 个 ViewModel 各写一遍。 */
class PlayFromSourceUseCase(
    private val dispatcher: PlaybackDispatcher,
    private val toQueueItems: ToQueueItemsUseCase,
) {
    operator suspend fun invoke(source: SourceRef, startAt: Int, note: String): Boolean
}
```

## 3.7 `DeviceCapabilities` 补两个根位（S2.3）

```kotlin
data class DeviceCapabilities(
    // ---- S2.3 新增：两个根位，决定「出现在哪」而不是「怎么播」 ----
    /** 能不能出声 —— 决定它出不出现「播放到哪」的选择器。 */
    val canPlayAudio: Boolean = false,
    /** 能不能被遥控 —— 红外 / 蓝牙 / WiFi 都算。 */
    val canBeRemoted: Boolean = false,

    // ---- 原有 9 项，一个字不动 ----
    val supportsPlayQueue: Boolean = false,
    val supportsMultiroom: Boolean = false,
    val supportsPresets: Boolean = false,
    val supportsHttpapi: Boolean = false,
    val supportsVolume: Boolean = false,
    val supportsSeek: Boolean = false,
    val supportsNextPrev: Boolean = false,
    val supportsMute: Boolean = false,
    val supportsChannel: Boolean = false,
)
```

⚠️ **不加 `kind` 字段。** 型号标签会随固件变（`.153` 旧固件 vs `.164` 新固件已实测出字段差异），
能力位是探测出来的、可以修正；标签一旦存进库就固化了。

---

# 第 4 章 · 施工工序（逐道）

## S0.1 · 建护栏注解

**改什么**

| 文件 | 动作 |
|---|---|
| `playback/guards/SingleEntryPoint.kt` | **新建**，内容 = 3.1 |

**验收**

```bash
./gradlew :app:assembleDebug     # BUILD SUCCESSFUL（注解还没人用）
```

**回退**：删文件。

---

## S0.2 · 三个原语打标记

**改什么**

| 文件 | 行 | 动作 |
|---|---|---|
| `DevicePlaylistGateway.kt` | `fun push(` | 上方加 `@SingleEntryPoint` |
| `DeviceQueuePush.kt` | `fun push(` | 上方加 `@SingleEntryPoint` |
| `QueuePlaybackService.kt` | `fun start(` | 上方加 `@SingleEntryPoint` |
| `QueueStarter.kt` | 文件级 | 加 `@file:OptIn(SingleEntryPoint::class)` |

**验收**

```bash
./gradlew :app:assembleDebug     # BUILD SUCCESSFUL
```

**回退**：去掉 4 处注解。

---

## S0.3 · 反向验证（**这一步不能跳**）

**做什么**：故意在 `NasViewModel` 里加一行 `DevicePlaylistGateway.push(...)`（不编译期 opt-in）。

**验收**

```bash
./gradlew :app:assembleDebug 2>&1 | grep -q "这是唯一入口的内部原语" && echo "✅ 门禁生效" || echo "❌ 门禁没生效"
```

**必须看到编译错误。** 看不到就说明注解没生效（常见原因：`@Target` 漏了 `FUNCTION`、或 `level` 写成了 WARNING）。

**然后删掉那行故意加的代码。**

**为什么这一步不能跳**：`docs/88` 里「补测试」那条就是只写了绿灯用例、没做 RED 验证，
结果测试把错误假设固化了（`docs/113` 洛雪信封那个 bug 就是这么漏的）。

---

## S0.4 · 两个 CI 检查（error 级）

**新建**

```python
# scripts/check-single-dispatch.py
"""下发原语只允许出现在 QueueStarter.kt。

来源：docs/86 不变量 I1；4 个 PR（#130 #131 #132 #139）修的都是这一件事。
规则：DevicePlaylistGateway.push / DeviceQueuePush.push / QueuePlaybackService.start
      只允许出现在 app/src/main/**/QueueStarter.kt。
风险：低，纯只读。
"""
```

```python
# scripts/check-no-global-optin.py
"""不许用全局 opt-in 绕过编译期门禁。

为什么：@RequiresOptIn 挡得住「不小心」，挡不住「加一行 -opt-in= 全局开关」。
规则：*.kts 里不许出现 -opt-in= 或 freeCompilerArgs 里的 optIn。
"""
```

**改什么**

| 文件 | 动作 |
|---|---|
| `.github/workflows/android.yml` | 在 `Check doc references` 之后插两步 |

**验收**

```bash
python3 scripts/check-single-dispatch.py   # 通过
python3 scripts/check-no-global-optin.py   # 通过
# 故意在 NasViewModel 加一行 push，第一个脚本应 exit 1
```

**回退**：删两个脚本 + CI 两步。

---

## S0.5 · 两个 CI 检查（warn 级）

| 脚本 | 规则 | 现在为什么只能 warn |
|---|---|---|
| `check-output-switch.py` | `PlaybackOutputStore.write` 只许在 `PlayerScreen.kt` | 开关还在洛雪页，S1.4 后才能转 error |
| `check-profile-in-ui.py` | `*Screen.kt` 里不许出现 `DeviceProfile.` | 现在 15 处，S2.3 后才能转 error |

**做法**：脚本 exit 0 但打印清单，CI 里加 `|| true`。

**收益**：清单先摆出来，清一处划一处，不搞突击。

---

## S1.1 · `PlayIntent` 落地

**改什么**

| 文件 | 动作 |
|---|---|
| `core-protocol/.../playback/PlayIntent.kt` | **新建**，内容 = 3.2 |
| `core-protocol/.../voice/IntentRouter.kt` | 产出类型 `VoiceIntent` → `PlayIntent`（**规则逻辑一个字不改**） |
| `core-protocol/.../voice/VoiceIntent.kt` | 改成一个 `typealias VoiceIntent = PlayIntent`（过渡，S1.3 后删） |

**验收**

```bash
./gradlew :core-protocol:test     # 613 用例必须全绿（口语规则一条没动）
./gradlew :app:assembleDebug
```

**回退**：删 `PlayIntent.kt`，`IntentRouter` 改回 `VoiceIntent`。

⚠️ **这是唯一动 `core-protocol` 公开 API 的两处之一**（R3 的例外）。用 `typealias` 过渡是为了让 613 个单测不用改。

---

## S1.2 · `dispatch` 唯一执行点

**改什么**

| 文件 | 动作 |
|---|---|
| `playback/PlaybackDispatcher.kt` | **新建**，内容 = 3.3 |
| `playback/PlaybackRouter.kt` | 加 `@SingleEntryPoint`（界面不再直接调它） |
| `PlayerScreen.kt` | 把 `PlaybackViewModel` 的持有方式改成可被 dispatcher 取到（object 持有 map） |

**验收**

```bash
./gradlew :app:assembleDebug
grep -rn 'PlaybackRouter.of(' --include=*.kt app/src/main | grep -v Dispatcher   # 应只剩少量
```

**回退**：删 dispatcher，界面改回直接调 vm。

---

## S1.3 · 三入口接入 dispatch

**改什么**

| 文件 | 现在 | 改成 |
|---|---|---|
| `PlayerScreen.kt` | 按钮 `vm.next()` | `dispatcher.dispatch(PlayIntent.Next)` |
| `PlayerScreen.kt:865` | `handleVoiceIntent(intent, vm)` | `dispatcher.dispatch(intent)` |
| `FloatingControlService.kt:204-206` | `send(LinkPlayCommands.previousTrack())` | `dispatcher.dispatch(PlayIntent.Previous)` |
| `FloatingControlService.kt:106` | `QueueBus.owner() ?: fallbackIp` | 保留（只是取 ip），但**不再直发 httpapi** |

**验收（关键，这条是本工序的全部意义）**

```bash
grep -rn 'LinkPlayCommands' app/src/main/kotlin/io/github/laozhudu/linkplay/console/floating/   # 应为空
```

真机：
- [ ] 投到 DOSS 音箱，悬浮窗按「下一首」→ 真的切歌
- [ ] 投到电视盒子（`.175`），悬浮窗的「下一首」**不出现**（`can()` 返回 false）
- [ ] 语音说「下一首」→ 切歌
- [ ] 界面按「下一首」→ 切歌

**回退**：三处各自独立，可单独回退。

---

## S1.4 · 输出开关搬到播放页

**改什么**

| 文件 | 动作 |
|---|---|
| `PlayerScreen.kt` | 顶部（标题行下方、封面之上）加一组 `FilterChip`：本机 / 音箱。**必须内联，不能浮层**（`docs/37`） |
| `LxSearchScreen.kt:80-102` | **删掉**那组 chip |

**验收**

```bash
grep -rn 'setOutput(' --include=*.kt app/src/main     # 只在 PlayerScreen
python3 scripts/check-output-switch.py                # 通过
```

真机：
- [ ] 播放页能看到「从哪出声 本机 / 音箱」
- [ ] 切「本机」后，从 NAS / 本地 / 在线 / 歌单 / 洛雪 任一处点播放 → 声音从手机出来
- [ ] 切「音箱」→ 从音箱出来，**不两边一起响**
- [ ] 洛雪页不再有这个开关

**转 error**：本工序合入后，把 `check-output-switch.py` 的 warn 去掉。

**回退**：chip 搬回洛雪页。

---

## S2.1 · 统一播放状态

**改什么**

| 文件 | 动作 |
|---|---|
| `ui/player/PlayerUiState.kt` | 补 4 个字段（内容 = 3.4） |
| `playback/local/LocalPlayer.kt` | 加 ticker（500ms）填 positionMs/durationMs/isLive/volume |
| `playback/PlaybackViewModel.kt` | 加一个 `fun toUiState(): PlayerUiState`（设备态 → 统一态） |
| `PlayerScreen.kt` / `home/MiniPlayerBar.kt` | 改读 `PlayerUiState`，不再直接读 `PlaybackViewModel.state.progress/.volume` |

**验收**

```bash
grep -n 'state.progress\|state.volume' app/src/main/kotlin/.../PlayerScreen.kt   # 应为空
```

真机：
- [ ] 本机播放时播放页**有进度条在走**
- [ ] 本机播放时音量条能拖且真的改音量
- [ ] 切到音箱 → 进度/音量立刻变成设备的值，**不串**
- [ ] 播放页**仍无任何浮层**（`docs/37`）

**回退**：字段留着（默认值不影响），界面改回读旧来源。

---

## S2.2 · `UpnpPlayer` + MediaSession 换挂

**改什么**

| 文件 | 动作 |
|---|---|
| `playback/UpnpPlayer.kt` | **新建**，内容 = 3.5 |
| `playback/PlaybackService.kt` | `MediaSession.Builder(this, player)` 里 `player` 从 `local.raw()` 换成「按 output 转发的门面」 |

**门面写法**（这是本工序唯一的取舍点）：

```kotlin
// 优先用 ForwardingSimpleBasePlayer（官方推荐），不行就手写一个 20 行的 when
private fun currentPlayer(): Player = when (PlaybackRouter.of(this).output()) {
    PlaybackOutput.Local -> PlaybackRouter.of(this).local.raw()
    PlaybackOutput.Remote -> upnpPlayer
}
```

⚠️ **MediaSession 一旦建立就不容易换 player 实例**。若 `MediaSession.Builder` 不接受动态切换，
改为：两个 session 互斥，切输出时 `release()` 旧的再 `build()` 新的。**先试前者，不行再用后者。**

**验收**

```bash
./gradlew :app:assembleDebug
```

真机（**这是本工序的核心收益，必须逐条验**）：
- [ ] 投音箱时**通知栏出现媒体控件**，标题/进度正确
- [ ] 通知栏按「暂停」→ 音箱真的暂停
- [ ] 蓝牙耳机按键能切歌（投音箱时）
- [ ] 本机播放时行为**不回归**（现在能用的不能坏）
- [ ] 锁屏能看到控件

**回退**：`PlaybackService` 改回挂 `local.raw()`，删 `UpnpPlayer.kt`。

---

## S2.3 · 能力位成为唯一判据

**改什么**

| 文件 | 动作 |
|---|---|
| `core-protocol/.../dlna/DeviceProfile.kt` | `DeviceCapabilities` 加两个根位（内容 = 3.7） |
| `ConsoleViewModel.kt:423,432` | 探测时填 `canPlayAudio` / `canBeRemoted` |
| 各 `*Screen.kt` | 把 `if (profile == DeviceProfile.GENERIC_DMR)` 改成 `if (caps.supportsChannel)` 等 |
| `PlayerScreen.kt` | 用 `dispatcher.can(intent)` 决定画不画按钮 |

**验收**

```bash
grep -rn 'DeviceProfile\.' app/src/main/kotlin/**/*Screen.kt     # 应为空
python3 scripts/check-profile-in-ui.py                            # 通过 → 转 error
grep -rn 'supports' --include=*.kt app/src/main | wc -l           # 应从 2 涨到 ≥ 8
```

真机：
- [ ] 电视盒子（`.175`）在设备页显示正确
- [ ] 电视盒子**不显示**声道、六键预设、固件升级
- [ ] 电视盒子能进「播放到哪」选择器并投流
- [ ] DOSS 音箱的六键预设/声道/固件**照旧显示**

**转 error**：本工序合入后，`check-profile-in-ui.py` 的 warn 去掉。

---

## S3.1 · 三个 UseCase

**改什么**

| 文件 | 动作 |
|---|---|
| `usecase/ToQueueItemsUseCase.kt` | **新建** |
| `usecase/SetVolumeUseCase.kt` | **新建** |
| `usecase/PlayFromSourceUseCase.kt` | **新建** |
| `NasViewModel.kt:400` | 删 `toQueueItem`，改调 UseCase |
| `LibraryViewModel.kt:206` / `:274` | 两处逐字重复合并，改调 UseCase |
| `QueueViewModel.kt:180` | 改调 UseCase |
| `OnlineViewModel.kt:181` | 改调 UseCase |
| `PlaybackEngine/PlaybackViewModel/DeviceVolumeControl/DeviceControl` | 4 套音量 → 1 个 UseCase |
| 5 个 ViewModel 的「播放整份列表」 | 改调 `PlayFromSourceUseCase` |

**验收**

```bash
grep -rn 'QueueItem(' --include=*.kt app/src/main | grep -v usecase   # 应为 0
grep -rn 'fun setVolume\|fun setVolumeLive\|fun adjustVolumeByKey' --include=*.kt app/src/main | wc -l   # 应为 1
```

**单测（这是还 `docs/88` T6 的债，必须做）**

| 用例 | 断言 |
|---|---|
| `ToQueueItemsUseCase` 对 5 种源输入产出字段一致 | 5 条断言 |
| `SetVolumeUseCase.byKey` 到顶/到底返回 null | 2 条 |
| `PlayFromSourceUseCase` 空列表不调 dispatcher | 1 条 |
| **`startQueueOnDevice` 能力位为 false 时不调 gateway** | 1 条（需先把 `firstStep()` 抽成纯函数） |
| **PlayQueue 成功时不调后两段** | 1 条 |
| **两段都失败时退客户端逐首** | 1 条 |

**RED 验证（必做）**：故意删掉能力位判断 → 对应测试**必须挂**。

**回退**：UseCase 类留着（不影响），调用点改回去。

---

# 第 5 章 · 迁移映射表

| 旧 | 新 | 工序 |
|---|---|---|
| `VoiceIntent` | `PlayIntent`（`typealias` 过渡后删） | S1.1 |
| `handleVoiceIntent(intent, vm)`（PlayerScreen:865） | `dispatcher.dispatch(intent)` | S1.3 |
| `FloatingControlService.send(LinkPlayCommands.nextTrack())` | `dispatcher.dispatch(PlayIntent.Next)` | S1.3 |
| `LxSearchScreen` 的 chip → `router.setOutput` | `PlayerScreen` 的 chip → `router.setOutput` | S1.4 |
| `PlayerScreen` 读 `state.progress` / `state.volume` | 读 `PlayerUiState.positionMs` / `.volume` | S2.1 |
| `MediaSession.Builder(this, local.raw())` | `MediaSession.Builder(this, currentPlayer())` | S2.2 |
| `if (profile == DeviceProfile.GENERIC_DMR)` | `if (caps.supportsXxx)` | S2.3 |
| `NasViewModel.toQueueItem` / `LibraryViewModel` ×2 / `QueueViewModel` / `OnlineViewModel` 的 `QueueItem(...)` | `ToQueueItemsUseCase` | S3.1 |
| `PlaybackEngine.setVolume` / `setVolumeLive` / `DeviceVolumeControl.setVolume` / `adjustVolumeByKey` | `SetVolumeUseCase` | S3.1 |

---

# 第 6 章 · 依赖图与关键路径

```
S0.1 ──→ S0.2 ──→ S0.3 ──→ S0.4 ──→ S0.5
                              │
                              ↓
                          S1.1 ──→ S1.2 ──→ S1.3 ──→ S1.4
                                                    │
                                                    ↓
                                        S2.1 ──→ S2.2 ──→ S2.3
                                                          │
                                                          ↓
                                                        S3.1
```

**关键路径**：S0.1 → S0.2 → S0.3 → S1.1 → S1.2 → S1.3 → S2.1 → S2.2 → S3.1

**可以并行的**：
- S0.4 与 S0.5 可并行
- S1.4（开关搬家）与 S1.2/S1.3 可并行（它只碰两个界面文件）
- S2.3 的「能力位补两个根位」与 S2.2 可并行（前者只改 `DeviceProfile.kt`）

**必须串行的三处**：
1. **S0.3 必须在 S0.2 之后** —— 没有标记就验不了门禁
2. **S1.3 必须在 S1.2 之后** —— 没有 dispatcher 无处可接
3. **S3.1 必须在 S2.x 之后** —— 重复的地方要先被收拢

---

# 第 7 章 · 验收总表（12 项，每项一条命令或一个真机动作）

| # | 验收 | 怎么验 | 工序 |
|---|---|---|---|
| 1 | 编译期门禁生效 | 故意调原语 → **必须编译报错** | S0.3 |
| 2 | 下发原语只有一处 | `grep` 只在 `QueueStarter.kt` | S0.4 |
| 3 | 无全局 opt-in | `check-no-global-optin.py` 通过 | S0.4 |
| 4 | 意图层覆盖四入口 | `grep LinkPlayCommands floating/` 为空 | S1.3 |
| 5 | 悬浮窗对通用渲染器不显示下一首 | 真机投 `.175` | S1.3 |
| 6 | 输出开关只在播放页 | `grep setOutput` 只在 `PlayerScreen` | S1.4 |
| 7 | 5 个源都能本机播 | 逐个点播放，声音从手机出 | S1.4 |
| 8 | 本机播放有进度/音量 | 真机看 | S2.1 |
| 9 | 投音箱时通知栏有控件 | 真机下拉通知栏 | S2.2 |
| 10 | 蓝牙耳机键能切歌 | 真机 | S2.2 |
| 11 | Screen 层零型号判断 | `check-profile-in-ui.py` 通过 | S2.3 |
| 12 | `QueueItem` 只在 UseCase 里拼 | `grep QueueItem(` 排除 usecase 后为 0 | S3.1 |

---

# 第 8 章 · 风险登记

| # | 风险 | 概率 | 影响 | 应对 |
|---|---|:-:|:-:|---|
| R-1 | `@RequiresOptIn` ERROR 级一下子编不过 | 中 | 中 | S0.2 先设 WARNING 跑一遍看有多少处，清完转 ERROR |
| R-2 | `SimpleBasePlayer` 是 `@UnstableApi` | 低 | 中 | 稳定多年；只在一处用，变了改一个文件 |
| R-3 | MediaSession 不能动态换 player | **中** | 中 | 先试 `ForwardingSimpleBasePlayer`；不行改「两个 session 互斥」 |
| R-4 | `PlaybackViewModel` 无法被 dispatcher 取到 | 中 | 低 | 加一个 `object PlaybackViewModelHolder`（map<ip, VM>） |
| R-5 | S2.1 后两个状态源并存期出双份刷新 | 中 | 低 | 只在 S2.1 期间并存，S2.2 收口 |
| R-6 | 门禁转 error 卡住别的开发 | 中 | 中 | warn 阶段先列清单，清一处划一处 |
| R-7 | 真机不可用（`adb devices` 为空） | **高** | 高 | 第 5/7/8/9/10 项验收只能靠用户装机确认，**不能假装验过** |

---

# 第 9 章 · 明确不做

| 不做 | 理由 |
|---|---|
| 引入 Hilt / Koin | 149 个动作靠构造注入会更乱；`PlaybackRouter.of()` 单例够用 |
| 为 UseCase 建新 Gradle 模块 | 纯 Kotlin 类放 `app` 即可（`docs/83`：不为架构纯粹拆模块） |
| 把 149 个动作都包成 UseCase | 只收**被两个以上入口用到**的那三个 |
| 重写 `PlaybackViewModel`（897 行） | 它是设备控制的真实实现，`UpnpPlayer` 只做适配 |
| 一次性上 transfer（本地↔音箱无缝搬） | 现在没需求；S2.2 之后再加是「加一层转发」，不会返工 |
| 加 `kind` 字段给设备贴型号标签 | 能力位是探测出来的、可修正；标签一旦入库就固化 |
| 下载 / 红外 / 蓝牙真做 | 都不在这条链上，S3 之后单独排 |

---

# 第 10 章 · 一句话

**零件够了，缺的是把零件装起来的顺序和接口。这份图纸给了 12 道工序、7 个新文件、20 处改动、12 条验收命令 —— 每道工序都能单独上线、单独回退。**

**第一道（S0.1–S0.3）只花 1.5 小时，但从此「唯一入口」被破会编译不过。**

---

## 附：施工前自检（复制即用）

```bash
cd /root/.hermes/workspace/linkplay-console
git log -1 --format='%h %s'
git status --short
grep -rho '@Test' app/src/test core-*/src/test | wc -l
grep -rn 'DevicePlaylistGateway.push\|DeviceQueuePush.push\|QueuePlaybackService.start' --include=*.kt app/src/main
```

期望：`f26e0a4` 或其后 / 空 / `1036` / 3 行（全在 `QueueStarter.kt`）
