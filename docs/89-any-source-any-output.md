# 任务单 · 第四轮：任何内容源 × 任何输出

> **依据的代码状态：提交 `bcf6088`**（#136 docs/88 已合入）
> 承接 `docs/86`（施工单）、`docs/87`（BOM 实测）、`docs/88`（第三轮）
> 用户需求原话：「电台能否手机也能直接播放做完整？」「我需要以后所有内容源都能完美的在任何设备使用」
> 性质：**接单开工文档**。本文的矩阵和障碍全部来自实读代码，不是推断。

---

## 零、结论（三句话）

1. **6 个内容源 × 2 种输出 = 12 格，现在只通 6 格。** 洛雪是唯一「两边都能用」的源。
2. **电台能手机播，但不只是「接到 Router」** —— 手机本机走 ExoPlayer，官方文档明确要求播放 HLS 必须依赖 `media3-exoplayer-hls`。**这是一行前置依赖，不是障碍、不是风险**；音箱侧的 HLS 早已多次实测可播（见 1.2）。
3. **真正的结构障碍是播放页读的是「设备状态」不是「播放状态」** —— 所以哪怕把 6 个源全接上 Router，本地播放时**播放页看不到进度和音量**。

**所以这一轮不是「接线」，是「把播放页从设备遥控器变成播放器」。**

---

## 一、现状矩阵（实读代码，逐格）

| 内容源 | 投音箱 | 手机本机 | 本机缺什么 |
|---|:-:|:-:|---|
| NAS | ✅ | ❌ | 没走 Router |
| 手机本地文件 | ✅ | ❌ | 没走 Router + 队列 |
| 在线（Subsonic） | ✅ | ❌ | 没走 Router |
| **网络电台** | ✅ **专用流程** | ❌ **完全没做** | HLS 模块 + Router + 直播态 |
| 洛雪 | ✅ | ✅ | — |
| 歌单 / 最近播放 | ✅ | ⚠️ **只播一首** | 队列 |

**6 / 12。** 洛雪能两边用，是因为它是唯一从一开始就走 `PlaybackRouter` 的源。

### 1.1 电台为什么特殊（不是「漏了接线」）

`RadioViewModel.playUrl()` 走的是完全独立的流程：

```kotlin
client.send(deviceIp, LinkPlayCommands.switchMode("wifi"))   // 先切输入源
delay(300)
client.send(deviceIp, LinkPlayCommands.playUrl(url))          // 再播
repeat(6) { delay(1000); /* 轮询 6 秒看 status 是否 PLAY */ }
```

**这三步都是「对设备说话」**，没有一步能在手机上执行：

| 步骤 | 手机上为什么不需要 |
|---|---|
| `switchMode("wifi")` | 手机没有「输入源」这个概念 |
| `playUrl(url)` | 手机应该直接 `ExoPlayer.setMediaItem` |
| 6 秒轮询 `status` | 手机不需要轮询，`onPlaybackStateChanged` 会回调 |

**但注意**：这个流程本身是对的，投音箱时必须保留（旧固件不切输入源只把地址存进 uri 不播，真机验过）。**要做的是给它加一条本机分支，不是替换它。**

---

## 二、一个前置依赖 + 四个结构性障碍

先分清两件不同的事，之前文档把它们的措辞混在一起了：

| | 音箱播 HLS | 手机本机播 HLS |
|---|---|---|
| 谁在解流 | **音箱固件自己**（curl 拉流后自己解播放列表） | **ExoPlayer**（我们代码里的播放器） |
| 要不要额外依赖 | **不要** | **要一行 `media3-exoplayer-hls`** |
| 实测状态 | ✅ **多次实测可播**（见 1.2） | 尚未实现，因为本机播电台整个还没做 |

**所以 HLS 从来不是「问题」。** 音箱侧是既有能力，本机侧是一行依赖 —— 两者都不构成风险。

### 前置 1 · 本机播放要一行 HLS 依赖（不是障碍）⬜

**先说结论：HLS 没问题，音箱侧已多次实测能播，用户也确认过。** 这里说的是完全不同的另一件事。

#### 1.2 音箱侧 HLS —— 已实测可播（不是问题，别再当问题提）

| 依据 | 记录 |
|---|---|
| `docs/19` | CNR 中国之声 / CNR 音乐之声 `index.m3u8` → **✅ play，curpos 递增** |
| `docs/42` | 「好消息是音箱**能播 m3u8**（CNR 已验证）」 |
| `docs/85` | `https://ngcdn001.cnr.cn/…m3u8` 也 `play`（https 那条旧结论一并推翻） |

内置 5 台里 2 台是 HLS，**投音箱一直是通的**。这一轮完全不碰这条路。

> ⚠️ 顺带修两条早已被推翻但没标过时的旧结论（见本轮同时修的 `docs/16` `docs/42`）：
> `docs/16` 说「m3u8 设备端不解析」、`docs/42` 说「音箱不认 https」—— **两条都是错的**，
> 分别被 `docs/19` 和 `docs/85` 的实测推翻。

#### 本机侧要一行依赖

```toml
# gradle/libs.versions.toml 现状
androidx-media3-exoplayer = ...   ✅
androidx-media3-common    = ...   ✅
androidx-media3-session   = ...   ✅
androidx-media3-exoplayer-hls = ❌ 没有
```

为什么需要：手机本机播的是 **ExoPlayer**，不是音箱固件。ExoPlayer 把 HLS 拆成了独立模块，
官方文档原话：

> **To play an HLS stream, you need to depend on the HLS module.**
> `implementation("androidx.media3:media3-exoplayer-hls:1.11.1")`
> —— `developer.android.com/media/media3/exoplayer/hls`

我核过 `media3-exoplayer-1.5.1.aar`：853 个类里 **HLS 相关类 0 个**（`HlsMediaSource` / `DefaultHls` / `HlsPlaylistParser` 全为 0），所以确实要单独加。

**但这只是一行，而且零风险**：

```
media3-exoplayer-hls:1.5.1
  minAndroidGradlePluginVersion = 1.0.0
  minCompileSdk = 35
```

跟现有 `media3 = "1.5.1"` **完全同版本**，不涉及任何构建工具链升级（`docs/87` 那套门槛问题在这里不存在）。

#### 量级参考（不是障碍，是「加了能多播多少」）

`docs/72` Radio Browser 中国区 500 台：`.m3u8` **228 台 = 45%**。
加了这行，本机播放覆盖 100%；不加，本机播电台只能覆盖 55%。

**所以它不是「障碍」，是「要不要顺手把 45% 一起拿下」。我的建议：加。**

### 障碍 2 · `LocalPlayer` 不支持队列 🔴

```kotlin
// LocalPlayer.kt
fun play(url: String, title: String?) {
    player.setMediaItem(MediaItem.Builder().setUri(url)...build())   // 单条
    player.prepare()
    player.play()
}
```

`PlaybackRouter.playQueue` 的 Local 分支（他们自己也诚实标注了）：

```kotlin
PlaybackOutput.Local -> {
    val item = items.getOrNull(startAt...) ?: items.first()
    local.play(item.url, item.title)
    log("本机播放：只播这一首（本地列表暂不支持） —— ${item.title}")
    true
}
```

**后果**：从 NAS 目录 / 歌单 / 手机本地列表切到「本机」，只播第一首，**放完就停**。

`ExoPlayer` 天生支持队列（`setMediaItems(List<MediaItem>, startIndex, startPositionMs)`），**是我们没用**。

### 障碍 3 · 播放页读的是「设备状态」不是「播放状态」 🔴（最根本）

```kotlin
// PlayerScreen.kt:231-232
val progress = scrubDraft ?: state.progress      // state = PlaybackViewModel（设备）
val shownVolume = volDraft ?: (state.volume / 100f)
```

而本机播放的状态在另一个地方：

```kotlin
// PlayerUiState.kt
data class PlayerUiState(
    val title: String? = null,
    val playing: Boolean = false,
    val hasQueue: Boolean = false,
    val controlLost: Boolean = false,
    val source: Source = Remote,
)
```

**`PlayerUiState` 没有 `progress` / `duration` / `volume` / `isLive` 任何一个字段。**

**所以哪怕 6 个源全接上 Router，本地播放时播放页依然是空的**：没有进度条、没有音量、不知道在播什么位置。

`PlayerUiState` 的类注释早就写了这是「为以后接本地播放器留的口子」，但**只留了「播什么、在不在放」，没留「播到哪」**。

### 障碍 4 · 音量键永远控音箱 🟠

```kotlin
// DeviceControl.kt:135
fun canHandleVolumeKey(): Boolean {
    val ip = host.state.value.selectedIp ?: return false
    if (host.state.value.online[ip] == false) return false
    return VolumeBus.volumeOf(ip) != null || ...
}
```

**完全不看 `PlaybackOutput`。** 后果：本机播放时按音量键 →

1. 改的是**音箱**音量（声音从手机出来，用户听到的是手机没变）
2. **事件被吞掉**，手机媒体音量也调不了
3. 注释里那条理由（「不吞的话一次按键改两处」）在这个场景下**正好反过来**：现在改成了一处错的

### 障碍 5 · 直播没有「live」概念 🟠

电台是无限流：`duration = 0`、不能 seek、进度条应该显示「直播」而不是 0:00 / 0:00。

现在 `PlayerScreen` 里进度条的逻辑是 `if (duration <= 0) return`（松手不 seek），但**进度条本身还会画出来**，显示一个不动的 0。

---

## 三、目标形态：一个播放状态，两个实现

不要为「本地播放」新建一套界面。正确做法是 **`PlayerUiState` 变成唯一的播放视图状态，设备侧也填它**。

这就是 `docs/77` 里 Media3 `Player` 接口的思路（`CastPlayer` 和 `ExoPlayer` 对上层是一个 `Player`）。

```kotlin
data class PlayerUiState(
    val title: String? = null,
    val artist: String? = null,
    val playing: Boolean = false,
    val hasQueue: Boolean = false,
    val controlLost: Boolean = false,
    val source: Source = Remote,

    // 新增四项 —— 这才是「播到哪」
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

**填法**：

| 输出 | 谁填 | 数据来源 |
|---|---|---|
| 音箱 | `PlaybackViewModel` → `fromRemote(...)` | `DeviceSnapshot`（已有） |
| 本机 | `LocalPlayer` 的 listener | `ExoPlayer` 的 `currentPosition` / `duration` / `volume` |

**界面只读 `PlayerUiState`** —— 从此「播到哪」不再分设备/本机。

---

## 四、任务分解

### T7 · 电台支持本机播放（用户明确要的）

#### 7.1 加 HLS 依赖（一行，本机侧才需要）

```toml
# gradle/libs.versions.toml
androidx-media3-exoplayer-hls = { module = "androidx.media3:media3-exoplayer-hls", version.ref = "media3" }
```

```kotlin
// app/build.gradle.kts
implementation(libs.androidx.media3.exoplayer.hls)
```

**同版本 1.5.1，无升级风险。** 音箱侧不需要它（音箱固件自己解 HLS，`docs/19` 已实测），
所以这条只影响「手机本机播电台」这一格。

#### 7.2 给 `playUrl` 加本机分支

```kotlin
fun playUrl(rawUrl: String, name: String?, storeId: Long) {
    val url = normalizeUrl(...) ?: return
    when (PlaybackRouter.of(app).output()) {
        PlaybackOutput.Local -> playLocally(url, name, storeId)   // 新增
        PlaybackOutput.Remote -> playOnDevice(url, name, storeId)  // 现有那套原样保留
    }
}
```

⚠️ **投音箱那条分支一个字都不要改** —— `switchMode("wifi")` + 6 秒轮询是真机验过的，改了会退回「点了没反应」。

#### 7.3 直播态处理

```kotlin
// LocalPlayer 里
val isLive = player.isCurrentMediaItemLive
```

界面：`isLive` 时进度条显示「直播 · LIVE」，**不画时间**，拖动禁用。

**做到什么程度**

- [ ] `gradle/libs.versions.toml` 有 `media3-exoplayer-hls`，版本与 media3 一致
- [ ] 切「本机」后点**内置 5 台**全部能出声，包括两台 `.m3u8`（CNR 中国之声 / CNR 音乐之声）
- [ ] 切「本机」后点 Radio Browser 里的 `.m3u8` 台能出声（抽 5 台验，`docs/72` 记着哪些主机支持 HLS）
- [ ] 切「音箱」后行为**与现在完全一致**（回归：内置 5 台仍 5/5）
- [ ] 直播时进度条显示「直播」，拖动无效
- [ ] 切到本机时**音箱停止**（不能两边一起响）

**收益**

| 项 | 前 | 后 |
|---|---|---|
| 电台输出 | 只有音箱 | **音箱 + 手机** |
| 音箱侧 HLS | ✅ 已可播 | ✅ **不变**（本来就没问题） |
| 本机侧 HLS | — （本机播电台整个没做） | ✅ 一行依赖后 100% 覆盖 |
| 矩阵格数 | 6/12 | **7/12** |

#### ⚠️ 7.4 一个现成的 bug：切到本机时音箱不停

```kotlin
// PlaybackRouter.playOne 的 Local 分支
PlaybackOutput.Local -> {
    startLocalService()
    local.play(url, title)      // ← 只启动了本机，没有停设备
    true
}
```

`pauseBoth()` 也只 pause 了 local，没停设备。

**后果**：音箱在放 A，切到本机播 B → **两边一起响**。

这条要一起修：`Local` 分支里先发一次设备暂停/停止。

---

### T8 · `LocalPlayer` 支持队列

**做什么**

```kotlin
fun playQueue(items: List<Pair<String,String?>>, startAt: Int) {
    player.setMediaItems(
        items.map { (url, title) -> MediaItem.Builder().setUri(url).setMediaId(title ?: url).build() },
        startAt.coerceIn(0, items.lastIndex),
        0L,
    )
    player.prepare()
    player.play()
}
```

`PlaybackRouter.playQueue` 的 Local 分支改调它，**删掉那句「只播这一首」的 log**。

**做到什么程度**

- [ ] 从 NAS 目录切「本机」→ 整份列表在手机上连续播，**放完一首自动下一首**
- [ ] 从歌单切「本机」→ 同上
- [ ] 从手机本地列表切「本机」→ 同上（`content://` URI 也要能播）
- [ ] 通知栏的上一首/下一首能切
- [ ] 队列顺序与设备侧一致

**收益**

| 项 | 前 | 后 |
|---|---|---|
| 本地播放队列 | 只播一首 | **完整队列** |
| 歌单 / NAS / 手机本地 | 切到本机就废 | 可用 |
| 矩阵格数 | 7/12 | **10/12** |

---

### T9 · 播放页读统一播放状态

**做什么**

1. `PlayerUiState` 加 `positionMs` / `durationMs` / `isLive` / `volume`（见第三节）
2. `LocalPlayer` 用 `Player.Listener` + 一个 500ms 的 ticker 填这四项
3. `PlayerScreen` 的进度条/音量条改成读 `PlayerUiState`，不再直接读 `PlaybackViewModel.state`
4. `MiniPlayerBar` 同理（它已经在用 `PlayerUiState`，只是没填新字段）

**做到什么程度**

- [ ] 本机播放时播放页**有进度条在走**
- [ ] 本机播放时播放页**音量条能拖动并真的改音量**
- [ ] 切到音箱 → 进度/音量立刻变成设备的值，**不串**
- [ ] `PlayerScreen` 里 `grep 'state.progress\|state.volume'` 不再直接读设备 VM
- [ ] 播放页仍然**没有任何浮层**（`docs/37`）

**收益**

| 项 | 前 | 后 |
|---|---|---|
| 本机播放的进度显示 | **无** | 有 |
| 本机播放的音量显示 | **无** | 有 |
| 界面数据来源 | 两套（设备 VM / PlayerUiState） | **一套** |
| 以后加输出（蓝牙等） | 每个页面都要改 | 只填 PlayerUiState |

**这是整轮里最根本的一条** —— 它决定以后再加输出要不要返工。

---

### T10 · 音量键按输出分流

**做什么**

```kotlin
fun canHandleVolumeKey(): Boolean {
    // 本机播放时不要接管：交给系统，改的是手机媒体音量（那才是用户想改的）
    if (PlaybackRouter.of(app).output() == PlaybackOutput.Local) return false
    ...
}
```

**做到什么程度**

- [ ] 本机播放时按音量键 → 改的是**手机媒体音量**，音箱音量**不变**
- [ ] 投音箱时按音量键 → 改的是**音箱音量**，手机音量不变（现有行为不能坏）
- [ ] 活动在 `ACTION_DOWN` 和 `ACTION_UP` 上给出一致答案（现有注释的硬要求）

**收益**：修掉「声音从手机出来，音量键改音箱」这个明显的错。

---

### T11 · 12 格验收（真机，逐格打勾）

**这一项不是开发任务，是验收纪律** —— 以后任何内容源接入都必须过这张表。

| 源 | 投音箱 | 手机本机 |
|---|:-:|:-:|
| NAS 目录 | ☐ | ☐ |
| 手机本地文件 | ☐ | ☐ |
| 在线（Subsonic） | ☐ | ☐ |
| 网络电台（mp3/aac） | ☐ | ☐ |
| 网络电台（HLS） | ☐ | ☐ |
| 洛雪 | ☐ | ☐ |
| 歌单 / 最近播放 | ☐ | ☐ |

**每格验三件事**：
1. **出声**（真的响了，不是「命令回了 OK」）
2. **另一边停**（不能两边一起响）
3. **播放页对**（标题/进度/音量都对得上）

**做到什么程度**

- [ ] 12 格全打勾
- [ ] 每格三个子项都过
- [ ] 失败格记录现象（截图 + 诊断报告），不写「大概能用」

---

### T12 · 补单测

| 用例 | 断言 |
|---|---|
| `playUrl` 在 `Local` 输出下**不调** `client.send` | 手机不跟设备说话 |
| `playUrl` 在 `Remote` 输出下**调** `switchMode("wifi")` | 设备分支没被改坏 |
| `playQueue` 的 Local 分支调 `setMediaItems` 而不是 `setMediaItem` | 队列真的传下去了 |
| `canHandleVolumeKey` 在 Local 输出下返回 false | 音量键不吞事件 |
| `PlayerUiState` 直播时 `durationMs == 0 && isLive` | 进度条逻辑有依据 |

**做到什么程度**

- [ ] 5 个用例，`@Test` 总数从 1,036 涨到 ≥ 1,041
- [ ] 关键两条做 **RED 验证**（故意改回错的，测试必须挂）

---

## 五、PR 切分（3 个）

| PR | 内容 | 为什么这样切 |
|---|---|---|
| **E** | T7.1 HLS 依赖 + T7.4 切输出停设备 + T10 音量键 | 都是「小改但影响明显」，能独立验 |
| **F** | T9 统一播放状态 + T8 队列 | 动 `PlayerUiState` + `PlayerScreen` + `LocalPlayer`，要一起做，否则界面契约会短暂不同步 |
| **G** | T7.2/7.3 电台本机分支 + T12 单测 | 依赖 F 的状态字段，排在 F 后 |

**E 先合**（风险最低、收益立刻可见：两台 CNR 能本地播了）。
**F 是核心**，做完「任何源 × 任何输出」才真的成立。

---

## 六、明确不做

| 不做 | 理由 |
|---|---|
| 为本地播放新建一套播放页 | `PlayerUiState` 已经是那个口子，再开一套就是第三套 |
| 把电台投音箱那条流程改成走 Router 的三段降级 | 电台是单条无限流，没有「队列」，`PlayQueue` 对它无意义。保留专用流程是对的 |
| 让本机播放也走 `QueueBus` | `QueueBus` 是「设备侧队列归属」的模型，本机队列归 `ExoPlayer` 自己 |
| 给电台加「收藏到歌单」等新功能 | 本轮只解决「能不能播」，不扩功能 |
| 支持 DASH / SmoothStreaming | 电台实测只有 HLS 需要（`.m3u8` 45%），DASH 一个都没有 |
| 追 media3 新版本 | 1.5.1 的 hls 模块够用，`docs/87` 的教训：够用就别动 |

---

## 七、五个坑

1. **投音箱那条电台流程一个字别改** —— `switchMode("wifi")` 是真机验过的（旧固件不切输入源只存 uri 不播）
2. **手机本机播 `.m3u8` 需要 hls 模块**（官方要求），只加 `media3-exoplayer` 不够；**音箱侧不需要，别搞混**
3. **直播流 `duration` 是 0**，进度条不能按「0/0」画，要显示「直播」
4. **`ExoPlayer` 音量是 0.0-1.0 浮点**，`PlayerUiState.volume` 是 0-100 整数，转换别搞反
5. **`canHandleVolumeKey` 的返回值必须在 `ACTION_DOWN` 和 `ACTION_UP` 一致**（现有注释的硬要求，改它别破坏）

---

## 八、一句话

**「所有源都能在任何设备用」不是接线活 —— 6/12 缺的 6 格里，真正卡住的是三件事：`LocalPlayer` 只会播一首、播放页读的是设备状态所以本机播放没有进度和音量、切到本机时音箱不停。把这三件做完，12 格才可能全通。**

**HLS 不在这三件里。** 音箱侧它一直是通的（`docs/19` `docs/42` `docs/85` 都实测过）；手机本机侧只是要加一行 `media3-exoplayer-hls`，官方要求、同版本、零风险 —— 属于「顺手把 45% 一起拿下」，不属于「卡住」。

---

## 附：本文验收数据（可复核）

```
依据 bcf6088

媒体依赖（gradle/libs.versions.toml）
  media3 = "1.5.1"
  media3-exoplayer ✅  media3-common ✅  media3-session ✅
  media3-exoplayer-hls ❌ 不存在

media3-exoplayer-hls 1.5.1 元数据
  minAndroidGradlePluginVersion = 1.0.0
  minCompileSdk = 35

电台 URL 形态（Radio Browser 中国区 500 台，docs/72）
  .m3u8 228 (45%) / .mp3 184 (36%) / 无后缀 78 (15%) / .ogg 5 / .aac 3 / .m3u 2
内置 5 台里 2 台是 .m3u8（CNR 中国之声 / CNR 音乐之声）

cleartext：AndroidManifest.xml:45 usesCleartextTraffic="true"  ✅ 电台 http 可播

矩阵现状 6/12：
  NAS 投✅/本❌  |  手机本地 投✅/本❌  |  在线 投✅/本❌
  电台 投✅/本❌  |  洛雪 投✅/本✅  |  歌单 投✅/本⚠️只一首

PlayerUiState 字段：title / playing / hasQueue / controlLost / source
  缺 positionMs / durationMs / isLive / volume

PlayerScreen.kt:231-232 读的是 state.progress / state.volume（设备 VM）
DeviceControl.kt:135 canHandleVolumeKey() 不看 PlaybackOutput
```
