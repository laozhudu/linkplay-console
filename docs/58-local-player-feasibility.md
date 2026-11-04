# 58 · 手机本地播放器：可行性评估

> 用户 2026-11-04：「补充，如果加一个手机本地播放器模块是否可行」
>
> **结论：完全可行，而且成本比想象低得多** —— 因为接口已经写对了。
> 但这次只留门，不做（用户说过先做完音箱）。
>
> **依据的代码状态：提交 `9497f67`**（710 单测 0 失败）。
> 本项目正在快速重构，若发现与代码不符，以代码为准并回来更新这一页。

---

## 一、为什么成本低：接口正好是对的

`core-protocol/player/PlaybackEngine.kt` 里的接口：

```kotlin
interface PlaybackEngine {
    val channelName: String
    fun snapshot(): PlaybackSnapshot
    fun playUrl(url: String, title: String?)
    fun resume() / pause() / stop() / seek(seconds) / setVolume(0-100) / setMute()
    fun next() / previous()
}
```

现有两个实现都是**遥控设备**的：
- `UpnpPlaybackEngine`（UPnP AVTransport）
- `HttpApiPlaybackEngine`（`setPlayerCmd:*`）

本地播放 = **第三个实现**，方法全都能映射：

| 接口方法 | ExoPlayer 对应 |
|---|---|
| `playUrl(url, title)` | `setMediaItem` + `prepare` + `play` |
| `resume` / `pause` / `stop` | `player.play()` / `pause()` / `stop()` |
| `seek(s)` | `player.seekTo(s * 1000)` |
| `next` / `previous` | `seekToNextMediaItem()` / `seekToPreviousMediaItem()` |
| `snapshot()` | 从 player 读 state / position / duration / metadata |
| `setVolume` | **⚠️ 见下面坑 1** |

**不需要新抽象。** 这和内容源那边完全不同——那边是要把已有的 `MediaProvider` 接上 5 个源，
这边是接口**已经对了**，只差一个实现类。

依赖：**Media3 1.11.0**（Apache-2.0），APK 增加 **1～3 MB**（取决于引入哪些模块）。

---

## 二、五个真坑

### 坑 1 · 音量语义不同（最容易漏，也最影响手感）

| | 设备播放 | 本地播放 |
|---|---|---|
| `setVolume(45)` 应该是什么 | 设备音量 45 | **系统媒体音量** 45 |
| 实现 | `setPlayerCmd:vol:45` | `AudioManager.setStreamVolume(STREAM_MUSIC, ...)` |

**不能用 `player.volume`**（那是 0.0～1.0 的增益）。
用增益的话，用户按音量键没反应——因为他预期的是系统音量在变。

**连带问题**：`MainActivity.dispatchKeyEvent` 现在把音量键拦给设备
（`canHandleVolumeKey()` 返回 true 就吞掉）。本地播放时必须返回 false，
让系统正常调手机音量。**同一个按键，两种行为**，这个不处理用户会疯。

### 坑 2 · 状态不能并存

`PlaybackViewModel` 只有一个 `engine` 字段。切到本地播放时，设备可能还在放。

需要明确一个概念：**当前输出目标**（`PlaybackRoute`）。
否则会出现「界面显示在播放、实际响的是另一个设备」。

这是 `docs/41` 那类"两份真相"病的重演风险。

### 坑 3 · 前台服务类型

现在三个服务：

| 服务 | foregroundServiceType |
|---|---|
| `QueuePlaybackService` | `dataSync` |
| `MediaServerService` | `dataSync` |
| `FloatingControlService` | `specialUse` |

本地播放需要 `mediaPlayback` 类型（Android 14+ 强制要求声明）。
要么加第四个服务，要么改造 `QueuePlaybackService`。

### 坑 4 · 通知栏会打架

本地播放的标准做法是 `MediaSessionService`（Media3 自带通知栏/锁屏/蓝牙耳机控制）。
但 `QueuePlaybackService` 已经是「控制设备」的前台服务。

两个媒体通知同时出现在通知栏，用户会分不清哪个是真的。

### 坑 5 · 队列双份

| | 队列在哪 |
|---|---|
| 设备播放 | `QueueBus`（按设备 IP 归属，PR #55 做过编译期强制） |
| 本地播放 | ExoPlayer 内部 playlist |

两份要同步，否则「界面显示第 3 首、实际放第 5 首」。

---

## 三、这次要做的「留门」（三条，成本很低）

**不做本地播放，但别把门焊死。**

### 留门 1 · `resolve()` 的 URL 别写死用途

`MediaProvider.resolve()` 返回的是 http URL。设备能拉，ExoPlayer 也能拉。
只是**注释里不要写**「这个 URL 是给设备拉的」——那会误导后来的人。

### 留门 2 · `startQueueOnDevice` 别硬编码「必须投给设备」

加一个 `route` 参数，现在只有"投给设备"一个取值：

```kotlin
sealed interface PlaybackRoute {
    data class Device(val ip: String) : PlaybackRoute
    // 以后加：data object Local : PlaybackRoute
}
```

用 sealed interface 而不是 Boolean —— 加 `Local` 时编译器会提醒所有 `when` 补分支，
不会漏。**留门 ≠ 现在建好房间。**

### 留门 3 · 诊断里记「输出目标」

`[内容源]` 那一段加一行：

```
输出=设备 192.168.99.164
```

以后有本地播放就变成 `输出=手机本地`。一眼能看出声音从哪出来。

---

## 四、什么时候做

**内容源标准化完成 + 投送层收干净之后。**

那时候的完整改动：

| # | 做什么 | 规模 |
|---|---|---|
| 1 | `LocalPlaybackEngine`（包 ExoPlayer） | 一个类，约 200 行 |
| 2 | `PlaybackRoute.Local` | 一个 case |
| 3 | `canHandleVolumeKey` 本地时返回 false | 几行 |
| 4 | `MediaSessionService` + 通知栏 | 一个新服务 |
| 5 | 队列同步（`QueueBus` ↔ ExoPlayer） | 需要设计 |

前四项是机械工作，**第五项是真设计问题**（两份队列怎么同步）。

---

## 五、一个产品层面的提醒

本地播放和「遥控器」是**两种不同的产品定位**：

| | 手机的角色 | 谁出声 |
|---|---|---|
| 遥控器（现在的定位） | 控制器 | 音箱 |
| 播放器 | 播放源 | 手机 |

用户之前在 `docs/06` 定过位：**"遥控器 + 检索层"（选 B）**。

加本地播放意味着定位扩展。**这不是技术问题，是产品问题**——但对个人使用来说很合理：
晚上不想吵到人，插耳机自己听；白天投给音箱全屋响。

技术上完全可以两条路并存，关键是**「当前输出目标」这个概念必须明确**（坑 2）。

---

## 六、结论

| 问题 | 答案 |
|---|---|
| 可行吗 | ✅ **完全可行**，而且成本比预想低（接口已对） |
| 现在做吗 | ❌ **不做**，先把内容源标准化 + 音箱功能做完 |
| 要留门吗 | ✅ **要**，三条低成本动作写进这次的提示词 |
| 最难的是什么 | 不是播放本身，是**音量语义**和**队列双份** |
| APK 会大多少 | 1～3 MB（Media3 1.11.0） |
