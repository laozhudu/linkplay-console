# 69 · 模块边界：为什么本地播放器要开模块，而 tt-music 和电台不用

> 2026-11-04 · 依据代码状态：提交 `8ac97c1`（773 单测 / 4 模块）
> 本文是**规划**，不含代码改动

---

## 零、结论

| 要接的东西 | 开模块吗 | 一句话理由 |
|---|---|---|
| **本地播放器** | ✅ **要开 `core-playback`** | Media3 是 Android 库，**进不了纯 JVM 底座** |
| tt-music（在线曲库） | ❌ 不开 | 只要 JDK，实现一个 `MediaProvider` 就够 |
| 电台（Radio Browser） | ❌ 不开 | 只要 JDK，移植两个类进 `core-protocol/radio/` |

**关键区别不是「看着独立不独立」，是「它拖不拖第三方依赖进来」。**

底座 `core-protocol` 现在是 `kotlin.jvm` 插件 + **零运行时依赖**（`build.gradle.kts` 里连协程都只放在 `testImplementation`）。
Media3 一进来，这个底座就再也不纯了 —— 而它是要 `maven-publish` 出去的。

---

## 一、判据：什么时候才该开模块

三条，**任一满足**才开：

| # | 判据 | 含义 |
|---|---|---|
| 1 | **拖进纯 JVM 装不下的依赖** | Android SDK / Media3 / 蓝牙 API / 原生 so |
| 2 | **有第二个消费者** | 别的端、别的项目也要用 |
| 3 | **能独立发版且不依赖项目类型** | 像 `core-diagnostics` 那样零依赖可 publish |

用这三条重算：

| 候选 | 判据1 | 判据2 | 判据3 | 结论 |
|---|---|---|---|---|
| 本地播放器 | ✅ **Media3** | ⚠️ 暂只有一个 | ❌ | **开** |
| tt-music 客户端 | ❌ 只要 JDK | ❌ | ❌ 依赖 `MediaTrack` | 不开 |
| 电台发现 | ❌ 只要 JDK | ❌ | ❌ 依赖 `RadioStation` | 不开 |

**只有本地播放器踩中判据 1。** 一条就够了。

### 反面参照：`core-diagnostics` 当时为什么该独立

它三条全中：零依赖、能 publish 给别人、和业务类型完全无关。
tt-music 客户端正好相反 —— 它处处依赖 `MediaTrack`，开模块只能变成
`core-ttmusic → core-protocol` 的叶子，没有独立价值。

---

## 二、目标模块图（4 → 5 个）

```
app                        界面 + 编排（16847 行，最大）
├── core-playback      ★新  本地播放引擎（Android + Media3）
├── core-protocol          纯 JVM 协议底座（不动，零依赖）
│     ├── mediasource/     MediaProvider 接口 + TtMusicClient + SubsonicClient
│     ├── radio/           RadioStation + RadioBrowserSource + StreamResolver
│     ├── player/          PlaybackEngine 接口（注意：接口在这，实现不在这）
│     └── remote/          RemoteCommand + LinkPlayTransport
├── core-diagnostics       纯 JVM，零依赖，可 publish
└── core-discovery         纯 JVM
```

**加一个模块，不是加七个。** 模块数量由判据决定，不拍脑袋。

---

## 三、分界线：接口留在底座，实现进 Android 模块

这是整个方案里最容易做错的一处。

### 正确切法

| 东西 | 放哪 | 为什么 |
|---|---|---|
| `PlaybackEngine` 接口 | **`core-protocol/player/`**（原地不动） | 纯接口，零依赖，纯 JVM 可测 |
| `PlaybackSnapshot` / `LoopMode` / `PlayerSource` | 同上 | 纯数据 |
| `UpnpPlaybackEngine` / `HttpApiPlaybackEngine` | 同上（原地不动） | 只发 HTTP，纯 JVM |
| **`LocalPlaybackEngine`** | **`core-playback`** | 要 Media3 |
| **前台服务 / MediaSession / 通知** | **`core-playback`** | 要 Android |
| **`LocalSink`（OutputSink 的本地实现）** | **`core-playback`** | 要 Media3 |

**一句话：`PlaybackEngine` 是第三个实现（前两个都是遥控设备），接口一行不用改。**
这正是 `docs/58` 当时判断「本地播放器可行性高」的原因 —— 门早就留好了。

### 依赖方向（单向，不许回头）

```
core-playback ──→ core-protocol
core-playback ──→ core-diagnostics
core-playback ──→ Media3

app ──→ 全部

core-protocol ──✗──→ core-playback      ← 绝对禁止
```

**为什么禁止回头**：`core-protocol` 一旦依赖 `core-playback`，它就不能再 publish 给纯 JVM 项目用，
而且要拖着一个 Android 库才能编译。这是不可逆的破坏。

### 怎么让它变成编译期强制

Gradle 不会自己拦这个，所以加一个 CI 检查 `scripts/check-module-deps.py`：

```
检查三件事：
1. core-protocol / core-diagnostics / core-discovery 的 build.gradle.kts 里
   不许出现 android / androidx / media3 / com.google.android
2. 这三个模块的 src/main 里不许出现 `import android.` 或 `import androidx.`
3. 依赖图里纯 JVM 模块不许指向 core-playback（防循环）
```

和现有的 `check-init-order.py` / `check-diagnostics-labels.py` 同一位置、同一形态。

---

## 四、和 `docs/60` 的两层结构怎么对上

`docs/60` 定的结构是「会话」和「输出」两层：

```
PlaybackSession（唯一真相，纯 JVM，放 core-protocol）
      ↓
OutputSink（接口，纯 JVM）
   ├── DeviceSink   → 推给音箱（纯 JVM，原地不动）
   └── LocalSink    → 本机播放（core-playback）★新
```

**本地播放器不是「另一套播放器」，是 `OutputSink` 的第二个实现。**

由此得出两条约束：

1. **`PlaybackSession` 绝不能知道自己接的是音箱还是本机** —— 它只知道「有个 Sink」
2. **`LocalSink` 的实现细节（Media3 的 ExoPlayer、音频焦点、通知）全烂在 `core-playback` 里**，
   上层看不到

这也顺带解决了 `docs/62` 里「播放状态四份」的问题：本地播放的状态从 `LocalSink` 回到
`SessionSnapshot`，不再另开一套。

---

## 五、本地播放器的五个坑（`docs/58` 已列，这里按模块重排）

| 坑 | 落在哪个模块 | 说明 |
|---|---|---|
| **音量语义不同** | `core-playback` | 音箱音量是设备音量；本机是**系统媒体音量**，不是 `player.volume` |
| **两套状态不能并存** | `core-protocol`（Session） | 同一时刻只能有一个 Sink 在播，切 Sink 要先停另一个 |
| **前台服务类型** | `core-playback` | 必须声明 `mediaPlayback`，不是 `dataSync` |
| **通知栏打架** | `core-playback` | 现有 `QueuePlaybackService` 已经在发通知，两个通知要合并或互斥 |
| **队列双份** | `core-protocol`（Session） | 队列只在 Session 里存一份，Media3 的 playlist 是它的投影，不是第二真相 |

**第 3、4 条正好说明为什么不能塞进 `app`** —— 这些是 Android 生命周期和系统服务的事，
混在 16847 行的界面层里没法单独测。

---

## 六、现有两个 Service 要不要一起搬

app 里现在有两个 Service：

| Service | 行数 | 类型 | 搬吗 |
|---|---|---|---|
| `QueuePlaybackService` | 304 | dataSync | **暂不搬** |
| `MediaServerService` | — | dataSync | **暂不搬** |

**理由**：它们和 app 的导航、权限、通知纠缠在一起，而且 `docs/59` 刚给它们加过断流兜底
（线程池异常被 `CrashGuard` 当崩溃），现在动风险大于收益。

**留门**：等本地播放器跑通、两套服务确实出现共性（都是「后台保活 + 通知 + 播放控制」），
再一起收进 `core-playback`。**只有一个实现者时抽象一定是错的**（`docs/49` 的教训）。

---

## 七、落地顺序（三步，每步都能单独合）

| 步 | 做什么 | 验收 |
|---|---|---|
| **1** | 建 `core-playback` 空壳 + `settings.gradle.kts` 加一行 + CI 加 `check-module-deps.py` | CI 绿，`./gradlew projects` 能看到 5 个模块，纯 JVM 三模块的依赖检查通过 |
| **2** | 在 `core-playback` 里写 `LocalPlaybackEngine`（实现 `core-protocol` 的 `PlaybackEngine`）+ 单测（假 Media3） | 单测能跑，`PlaybackEngine` 接口零改动 |
| **3** | 接 `LocalSink`，让 `PlaybackSession` 能切到本机播放 + 前台服务 + 通知 | 真机：手机上放一首 NAS 的歌，切到本机输出能出声；切回音箱正常 |

**第 1 步先做**，因为它把「不许回头依赖」这条规则钉死，后面才不会歪。

---

## 八、验收判据（写成可检验的）

1. **`core-protocol` 的 `build.gradle.kts` 里依然零运行时依赖**（`testImplementation` 除外）
2. **`./gradlew :core-protocol:publish` 产出的 jar 不包含任何 `android.*`**
3. **`PlaybackEngine` 接口零改动** —— 本地播放是第三个实现，不是改接口
4. **加本地播放器不改投送链路** —— `QueueStarter` / `DeviceQueuePush` / `DevicePlaylistGateway` 0 行改动
5. **`core-playback` 能单独跑单测**（不依赖 app、不需要真机）

---

## 九、明确不做的事

| 不做 | 为什么 |
|---|---|
| 把 `MediaProvider` 从 `core-protocol` 搬到新模块 | 它没有 Android 依赖，搬了只是制造 churn |
| 现在就建 `core-bluetooth` / `core-ir` | 只有 1 个实现者的抽象一定是错的（`docs/49`）；等真做再建 |
| 让 `core-protocol` 依赖 `core-playback` 好「统一播放器」 | 会让底座失去纯 JVM 属性，不可逆 |
| 把 tt-music 的投送也接进来 | 会绕过 `QueueStarter` / `PlayQueue`，多房间直接失效 |
| 一次搬完两个 Service | 见第六节，等共性出现 |

---

## 十、这一轮为什么改口

上一轮（`docs/68` 之后的接入分析）我说「两个都不用开模块」，
这一轮说「本地播放器要开」。**不是矛盾，是判据起作用了**：

- 当时讨论的是 tt-music 和电台 —— 两个都只吃 JDK，判据全不中
- 现在讨论的是本地播放器 —— **它吃 Media3，判据 1 直接命中**

**判据比结论重要。** 结论会随需求变，判据不会。
