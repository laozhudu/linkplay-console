# 77 · 播放器独立模块：本地播放与推流统一（成熟项目调研）

> 写于 2026-11-04 · 依据 main `5f7babb`
> 用户：「现在考虑播放器独立模块，用于兼容推流和本地播放的问题，你有什么意见？使用成熟项目为主。进行充分调研」

---

## 零、我的意见（三行）

1. **不要自己发明播放抽象** —— 用 Media3 的 `Player` 接口。Google 自己就是用它统一「本地播放」和「投到 Chromecast」的，我们只是把 Chromecast 换成 DOSS。
2. **不要自己发明能力位** —— 用 `getAvailableCommands()`（42 个）。我们自造的 `DeviceProfile` 是它的子集，只该留「设备专有补充」。
3. **队列归谁由能力位 `ENQUEUE` 决定** —— 这是 Music Assistant 的成熟做法：设备原生能入队就把队列放设备上，不能就逐首推。**一个能力位决定三档实现，不用按设备型号写分支。**

---

## 一、我们现在的病（不是猜的，docs/60 已经量化过）

| 病 | 现状 |
|---|---|
| 同一件事三份实现 | `QueueStarter.startQueueOnDevice` 自称"全 App 唯一入口"，但 `NasViewModel` 自己又写了一遍三段降级 |
| 播放状态 4 份 | 设备快照 / ViewModel / 队列 / 界面各存一份 |
| 音量 3 份、4 条路 | `docs/61` 认定的最严重项 |
| 没有本地播放 | 全仓**零** Media3 依赖（`app/build.gradle.kts` 里没有，`libs.versions.toml` 里也没有） |
| 有前台服务，但不是 MediaSession | `QueuePlaybackService` 已存在（`foregroundServiceType="dataSync"`），跑的是**队列编排循环**；没有 `MediaSession`，所以通知栏媒体控件、耳机按键、锁屏控件一样都没有 |
| 能力位已有，只用在降级链第一步 | `DeviceCapabilities`（9 项）存在，`supportsPlayQueue` 在 `QueueStarter.kt:59` 被用来决定「先试 PlayQueue 还是直接 m3u」；但它**没有决定队列模型归谁**，也没有 flow 预取 |

所以这不是"重构"，是**新建一个模块 + 把现有三份实现收口**。

---

## 二、外部调研：五个成熟项目，各抄什么

### 2.1 Media3（Apache-2.0，3063★，1.11.1 稳定版）—— **主力抄这个**

先看版本事实（Google Maven 实时查）：

```
media3-exoplayer 最新 stable = 1.11.1
                最新预发布   = 1.12.0-alpha02
模块 34 个，含 media3-cast / media3-session / media3-ui-compose-material3
```

我们的 `compileSdk 35 / minSdk 24 / jvmTarget 17`，Media3 要求 minSdk 21，**没有兼容问题**。

#### 抄点 1：`CastPlayer` —— 官方怎么统一「本地」和「远程」

`libraries/cast/.../CastPlayer.java` 的类声明和文档原文：

```java
public final class CastPlayer extends ForwardingPlayer {
  public interface TransferCallback {
    TransferCallback DEFAULT = new DefaultCastPlayerTransferCallback();
  }
}
```

它的做法（源码注释原文翻译）：

> 这个类通过**把播放委派给一个专用播放器**来工作，取决于 Cast 会话是否可用。
> 当会话变为可用/不可用时：新的活动播放器是 `RemoteCastPlayer`（会话活跃）或 `local player`（否则）；
> 一个可定制的 `TransferCallback` 拿到两个播放器**用于在它们之间搬运状态**；不活跃的那个被 `stop()`。

**这就是「兼容推流和本地播放」的标准答案，而且是 Google 写的。**

对我们的映射：

| CastPlayer 里的角色 | 我们对应的东西 |
|---|---|
| `local player` | `ExoPlayer`（手机本地播） |
| `RemoteCastPlayer` | 包住 `UpnpPlaybackEngine` / `HttpApiPlaybackEngine` 的远程实现 |
| `Cast session 可用与否` | 用户选了哪台设备 / 设备是否在线 |
| `TransferCallback` | 切输出时搬运播放位置与队列 |

#### 抄点 2：`PlayerTransferState` —— 「切输出」要搬的东西，官方列全了

`libraries/common/.../PlayerTransferState.java`，332 行，Apache-2.0。它搬 8 样：

```
playWhenReady / repeatMode / shuffleModeEnabled
currentMediaItemIndex / currentPosition
mediaItems / playbackParameters / trackSelectionParameters
```

用法就两句：

```java
PlayerTransferState.fromPlayer(source).setToPlayer(target);
```

**这 8 个字段就是「从手机切到音箱」必须转移的全部状态。** 不用我们自己想漏了什么。

`DefaultCastPlayerTransferCallback` 还演示了一个真实坑：**没有播放 URI 的 MediaItem 必须过滤掉，并且要相应修正 currentIndex**（源码里 20 行专门干这事）。我们投送到设备时同样要过滤「只在本地能播」的条目。

#### 抄点 3：`getAvailableCommands()` —— 我们一直自造的能力位，官方有 42 个

`Player.java` 开头的文档原文：

> 可用功能可以受限。Player 实例通过 `getAvailableCommands()` 提供一组可用命令来**声明功能支持**，
> 接口的使用方**必须只在对应 Command 可用时才调用方法**。

42 个能力位（原文提取，节选）：

```
PLAY_PAUSE  PREPARE  STOP
SEEK_IN_CURRENT_MEDIA_ITEM  SEEK_TO_NEXT_MEDIA_ITEM  SEEK_TO_PREVIOUS_MEDIA_ITEM
SET_SHUFFLE_MODE  SET_REPEAT_MODE
GET_TIMELINE  CHANGE_MEDIA_ITEMS  SET_MEDIA_ITEM
GET_VOLUME  SET_VOLUME  GET_DEVICE_VOLUME  SET_DEVICE_VOLUME_WITH_FLAGS
ADJUST_DEVICE_VOLUME_WITH_FLAGS  SET_SPEED_AND_PITCH
GET_AUDIO_ATTRIBUTES  SET_AUDIO_ATTRIBUTES  SET_TRACK_SELECTION_PARAMETERS
```

**这正好是项目铁律「不给按了没反应的按钮」的成熟版本** —— 我们原来靠 `DeviceProfile` 九项能力位手写，现在有官方 42 项，而且 UI 组件（`media3-ui`）本来就认这套。

#### 抄点 4：`setVolume` vs `setDeviceVolume` —— 治「音量 4 条路」

Media3 明确区分两个音量：

| 命令 | 方法 | 语义 |
|---|---|---|
| `COMMAND_SET_VOLUME`(24) | `setVolume(float)` | **播放器内部**音量（本地淡入淡出） |
| `COMMAND_SET_DEVICE_VOLUME_WITH_FLAGS`(33) | `setDeviceVolume(int, int)` | **输出设备**音量（带绝对/相对 flags） |
| `COMMAND_ADJUST_DEVICE_VOLUME_WITH_FLAGS`(34) | 音量键走的相对调节 | |

我们现在的「手机音量键 → 设备音量」是自己接的（`docs/20` 音量键方案）。用这套 API，**音量键、界面滑块、远程设备音量三者天然是一条路**，正是 `docs/61` 要的「音量统一入口」。

#### 抄点 5：`MediaSessionService` —— 后台播放/通知栏/耳机键，白拿

`media3-session` 提供 `MediaSessionService` + `MediaSession`。接上之后自动获得：
通知栏媒体控件、锁屏控件、蓝牙耳机按键、音频焦点管理、车机/Android Auto、系统媒体选择器。

**这些现在一样都没有**（app 里没有前台服务做播放）。

⚠️ 真实约束：`targetSdk 35` 下后台播放必须声明 `FOREGROUND_SERVICE_MEDIA_PLAYBACK` 权限 + `mediaPlayback` 服务类型，否则 Android 14+ 直接崩。

---

### 2.2 Music Assistant（Apache-2.0，3278★，今天还在推）—— **抄队列模型**

它的架构文档直接放在代码目录里（`controllers/players/README.md` 346 行、`controllers/player_queues/README.md` 462 行），是难得的高质量参考。

#### 抄点 1：**队列归谁，由一个能力位决定**（本次调研最重要的发现）

`models/player.py`：

```python
@property
def requires_flow_mode(self) -> bool:
    """Return if the player needs flow mode for (queue) playback."""
    # Default implementation: True if the player does not support PlayerFeature.ENQUEUE
    return PlayerFeature.ENQUEUE not in self.supported_features
```

`PlayerFeature.ENQUEUE` 的文档：*"The player supports (en)queuing of media items natively."*

**翻译成我们的语言**：

| 设备能力 | 队列放哪 | 怎么播 |
|---|---|---|
| 原生支持入队（`ENQUEUE`） | **设备上** | 一次下发整个队列（我们已有原厂 PlayQueue） |
| 不支持 | **我们这边** | flow 模式：一次推一首，快播完时推下一首 |

我们的三台 DOSS 都支持 PlayQueue → 走设备队列；`.175` 电视盒子只有 `MediaRenderer:1`、没有 PlayQueue → 只能 flow。

**关键：这是能力位判断，不是型号判断。** 同一型号换固件能力变了，代码不用改。

#### 我们其实已经走了一半 —— 但只走了一半

`DeviceCapabilities`（`core-protocol/.../dlna/DeviceProfile.kt:47`）里 **9 项能力位**已经存在：

```
supportsPlayQueue / supportsMultiroom / supportsPresets / supportsHttpapi
supportsVolume / supportsSeek / supportsNextPrev / supportsMute / supportsChannel
```

`supportsPlayQueue` 也真的在用 —— `QueueStarter.kt:59`：

```kotlin
val upstream = if (DeviceProfileCache.supportsDeviceQueue(deviceIp)) { /* 先试 PlayQueue */ }
```

**但用法和 MA 差一层**：

| | 我们现在 | Music Assistant |
|---|---|---|
| 能力位决定什么 | **降级链的起点**（先试 PlayQueue，失败再 m3u、再逐首） | **整个队列模型的归属**（设备队列 or flow 模式，二选一，不降级） |
| 失败怎么办 | 三级降级，每次都重试一遍 | 一开始就选对，不存在"试错" |
| flow 模式 | 逐首推，**不预取** → 有断音 | 预取下一首 |

**所以不是"要引入新概念"，是"已有的位只用了半截"。** 这是本次调研最省力的一条改进。

#### 抄点 2：flow 模式必须**预取下一首**

`player_queues/README.md` 的 "Look-Ahead and Buffering" 原文要点：

> 为了让播放无缝、并支持淡入淡出，控制器**提前计算下一个索引**、预先解析该条目的流信息，
> **并提前把下一条交给播放器**。

对我们的意义：flow 模式下如果等 `status=stop` 才推下一首，中间一定有肉眼可见的间隙。
必须在 `curpos` 接近 `totlen` 时（例如剩 5～10 秒）就推。

这条我们**目前完全没有**（`docs/65` 批次里没有这项）。

#### 抄点 3：多协议合并规则（一台设备被多条通道发现）

`controllers/players/README.md` 原文：协议 player 按**可靠性顺序**匹配到同一台物理设备：

```
MAC_ADDRESS > SERIAL_NUMBER > UUID > CAST_UUID/AIRPLAY_ID > IP_ADDRESS > player_id
```

配套三条细节，都很实用：

- **同协议域不合并**（同一个 provider.domain 的两个 player 即使 MAC 相同也不合并）—— 防止把同一台机器上的两个软件实例当成一台设备
- **延迟 15 秒评估**（等其它协议都注册完）；如果之前挂过某个父设备但它还没注册，等 45 秒
- 合并出来的 `Universal Player`：**聚合各协议的能力位，但不实现 PLAY_MEDIA**，播放委派给具体协议 player

我们已经有 `LinkPlayIdentity` 三档判据 + `UuidMatch`，方向对，但缺「同协议域不合并」和「延迟评估」。

#### 抄点 4：队列的五条不变量（治「状态收敛」）

`player_queues/README.md` 的 Invariants，直接可抄：

```
1. queue_id == player_id（一个播放器一个队列，键都共用这个 id）
2. protocol player 永不 reconcile —— 多协议合并时只信原生 player 的状态
3. active 只当 player 的 active source 是本队列
4. transitioning 期间忽略 player 更新 —— 避免换曲瞬间的状态抖动
5. media-time vs stream-time 要分开存（resume 用前者）
```

第 4 条特别对症：我们 `docs/73` 那个「三台都在线还报红字」就是换曲瞬间的抖动。

---

### 2.3 只学设计、不抄代码的项目（许可证不允许）

| 项目 | ★ | 许可 | 为什么只能看 |
|---|---|---|---|
| `snapcast/snapcast` | 8044 | **GPL-3.0** | 多房间同步的教科书，但 GPL 不能进 MIT 仓 |
| `MusicPlayerDaemon/MPD` | 2813 | **GPL-2.0** | 客户端-服务端播放架构参考 |
| `owntone/owntone-server` | 2585 | **GPL-2.0** | AirPlay 多房间 |
| `xbmc/xbmc`（Kodi） | 21435 | NOASSERTION（GPL） | 播放核心太大，学不来 |
| `mikebrady/shairport-sync` | 8995 | NOASSERTION | AirPlay 实现，我们用不上 |
| `mopidy/mopidy` | 8610 | Apache-2.0 | 后端/前端分层可看，Python 用不上 |
| `librespot-org/librespot` | 7366 | MIT | Spotify Connect，我们用不上 |

**顺带一个诚实的调研结论**：我在 GitHub 上按 `media3 dlna` / `upnp android player` / `android upnp renderer` 搜了全部结果，**没有一个是成熟的「Media3 + UPnP 投送」统一实现** —— 全是几十星的个人项目，或者 GPL-3.0 的视频投屏接收端。

所以这件事**没有现成轮子可装**，只能照 Google 的结构自己搭。这也解释了为什么该抄 `CastPlayer` 而不是抄某个第三方 App。

---

## 三、模块怎么切（结论）

### 3.1 分界线

```
core-protocol（纯 JVM，零运行时依赖 —— 不动）
  player/PlaybackSnapshot    纯数据，已有
  player/PlaybackEngine      远程侧契约，纯 JVM 可测，已有
  player/LoopMode / PlayerSource / DeviceQueueState   已有
  ↓ 被下面包住，不反向依赖

core-playback（★新建，Android + Media3）
  LocalPlayer        ExoPlayer，天然实现 Media3 Player
  RemotePlayer       实现 Media3 Player，内部委托给 PlaybackEngine（UPnP / httpapi）
  SwitchingPlayer    ForwardingPlayer + TransferCallback（照抄 CastPlayer）
  PlaybackService    MediaSessionService（后台 + 通知栏 + 耳机键）
  PlaybackCommands   把 DeviceProfile 映射成 Player.Commands（42 项的子集）
```

**依赖方向单向**：`core-playback → core-protocol`，绝不回头（`docs/69` 定的，仍然有效）。

### 3.2 为什么 `PlaybackEngine` 留着不删

它是**远程侧**的纯 JVM 契约，80 多个单测在跑。`RemotePlayer` 只是把它包成 Media3 `Player` 的形状。

**删了它，远程逻辑就绑死在 Android 上，再也不能在 JVM 上测。** 这是项目最值钱的一条资产（921 个单测里 613 个在 core-protocol）。

### 3.3 队列三档（照 Music Assistant）

```kotlin
sealed interface QueueMode {
    data class Device(val client: PlayQueueClient) : QueueMode   // ENQUEUE 能力位 = true
    data class Flow(val push: (MediaTrack) -> Unit) : QueueMode  // 不支持入队
    data class Local(val player: ExoPlayer) : QueueMode          // 手机本地
}
```

选择规则：**看能力位，不看设备型号**。

```kotlin
fun queueModeFor(profile: DeviceProfile, ...) =
    if (profile.canEnqueue) QueueMode.Device(...) else QueueMode.Flow(...)
```

---

## 四、我最想说的一条风险

**Media3 的 `Player` 接口有 60+ 个方法。** 远程侧（UPnP/httpapi）只支持其中一小部分。

错误做法：全部实现成 no-op，然后假装成功。
正确做法：**在 `getAvailableCommands()` 里只声明真支持的**，其余不声明；UI 据 `isCommandAvailable()` 禁用。

Media3 官方文档明确写了这个契约：

> 接口的使用方**必须只在对应 Command 可用时才调用方法**。

**这就是「不给按了没反应的按钮」在 Media3 里的正式表达。** 如果图省事全声明，等于把这条铁律废掉 —— 界面上会到处是点了没反应的按钮，而且没人知道为什么。

---

## 五、不做什么

| 不做 | 原因 |
|---|---|
| 不自造播放器接口 | Media3 `Player` 已经是事实标准，且 UI 组件、通知栏、耳机键全认它 |
| 不自造能力位 | 42 个官方命令位够用；`DeviceCapabilities` 只留**设备专有**补充（`supportsPresets` / `supportsChannel` / `securemode` 这类 Media3 没有的） |
| 不引入 Cast SDK | 闭源，且我们的设备不支持 Chromecast |
| 不抄 GPL 项目代码 | snapcast / MPD / owntone 只能看设计 |
| 不做跨设备无缝同步播放 | 那是 Snapcast 的活（GPL），而且 DOSS 固件做不到帧级同步 —— 现在多房间靠主机 AP，不假装能做 |
| 不在 `core-protocol` 里引 Media3 | 底座要保持零依赖、可 `maven-publish` |

---

## 六、坑（都会真实撞到）

| 坑 | 说明 |
|---|---|
| **前台服务权限** | `targetSdk 35` 下 `mediaPlayback` 类型必须声明 `FOREGROUND_SERVICE_MEDIA_PLAYBACK`，否则 Android 14+ 崩。现有 `QueuePlaybackService` 是 `dataSync` 类型，**加媒体播放要新增一种类型**，不能复用（类型和权限一一对应） |
| **远程 Player 的方法数** | 60+ 方法要实现，大量是 `UnsupportedOperationException` 或不进 `Commands` |
| **设备队列 ≠ Media3 timeline** | 切输出时不能简单 `setMediaItems`，要把 Media3 列表**投影**成设备 PlayQueue（原厂 `CreateQueue` + `AppendTracksInQueue`） |
| **设备不支持队列时** | 只能搬「当前曲 + 位置」，**必须告诉用户队列不会跟过去**，不能假成功 |
| **flow 模式的间隙** | 不预取下一首就一定有断音，且是"偶发"的（网络快就听不出来） |
| **MediaItem 与设备 URL** | 设备要的是直链，`MediaItem` 里可能只有 `content://` 或本地路径 → 必须先解析成可投的 URL |
| **本地播放的音频焦点** | 手机来电/别的 App 播放时要正确处理，Media3 帮你管但要接 |

---

## 七、分阶段

| 阶段 | 内容 | 前置 |
|---|---|---|
| **1** | `core-playback` 骨架 + `RemotePlayer`（把现有两个 engine 包成 Media3 Player）+ `getAvailableCommands` 映射 | 无 |
| **2** | `LocalPlayer`（ExoPlayer）+ `PlaybackService`（MediaSessionService）+ 通知栏 | 阶段 1 |
| **3** | `SwitchingPlayer` + `TransferCallback`（本地 ↔ 设备切换） | 阶段 2 |
| **4** | 队列三档收口：把 `QueueStarter` / `NasViewModel` / 电台三份实现并进 `QueueMode` | 阶段 1 |
| **5** | flow 模式预取（针对没有 PlayQueue 的通用 DLNA 设备） | 阶段 4 |
| **6** | 音量收口：全部走 `setDeviceVolume` / `adjustDeviceVolume` | 阶段 2 |

**建议先做阶段 4**（把三份实现收口）—— 它不依赖 Media3，纯逻辑，风险最低，收益最大（`docs/60` 认定的头号病）。

---

## 八、一句话

**统一抽象用 Media3 的 `Player`（Google 自己就这么统一本地和投屏），队列归属用 Music Assistant 的 `ENQUEUE` 能力位，切换输出用 `PlayerTransferState` 那八个字段 —— 三样都是成熟实现，我们只做适配，不发明。**

---

## 九、参考清单（可点开核）

- `androidx/media` — `libraries/cast/.../CastPlayer.java`、`DefaultCastPlayerTransferCallback.java`、`libraries/common/.../Player.java`、`PlayerTransferState.java`（全部 Apache-2.0）
- `music-assistant/server` — `controllers/players/README.md`、`controllers/player_queues/README.md`、`models/player.py`、`music_assistant_models/enums.py`（Apache-2.0）
- Google Maven `androidx.media3` group-index（版本事实来源）
