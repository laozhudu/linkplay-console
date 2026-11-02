# 界面能不能借成熟项目，而不是手搓

## 结论

**能，而且不该手搓。** 正确的借法是 `androidx.media3` 的两层：

| 层 | 组件 | 作用 | 许可证 |
|---|---|---|---|
| 引擎抽象 | `media3-common` 的 **`SimpleBasePlayer`** | 把「自定义播放后端」包装成标准 `Player` | Apache-2.0 |
| UI | **`media3-ui-compose-material3`** | `Player` / `MiniController` / `PlayPauseButton` / `SeekBack` / `SeekForward` / `Next` / `Previous` / `RepeatButton` / `ShuffleButton` / `MuteButton` / `PositionAndDurationText` / `ProgressSlider` / `ErrorText` | Apache-2.0 |
| 系统集成 | `media3-session` 的 `MediaSessionService` | 锁屏控制、通知、蓝牙/耳机按键、Android Auto、Wear OS，全部免写 | Apache-2.0 |

`media3-ui-compose-material3` 明确写着「Provides the `Player` and `MiniController` composables and a full set of prebuilt, Material3-styled composables」，
并且 `Player` 组合了 content frame + 进度条 + 控件，可用 `PlayerDefaults` 定制。
最新稳定版 **1.11.1**（2026-09）。

## 关键点：我们的「播放器」不是 ExoPlayer，但可以伪装成它

现有点播链路是「手机当遥控器，音箱当渲染器」——没有本地解码。media3 的 UI 全部基于
`androidx.media3.common.Player` 接口，而 **`SimpleBasePlayer` 就是为了这种场景准备的**：

> "A base implementation for `Player` that reduces the number of methods to implement to a minimum.
> Subclasses must override `getState` to populate the current player state on request."

于是只要写一个 `RemoteRendererPlayer : SimpleBasePlayer`：

```
getState()          ← 从 UpnpRendererClient 轮询 PlaybackState（已实现 positionInfo/transportInfo）
handleSetPlayWhenReady() → Play / Pause          （已实现）
handleSeek()        → Seek(H:MM:SS)             （已实现）
handleSetRepeatMode() → SetQueueLoopMode / 客户端排队循环
handleSetVolume()   → SetVolume(Channel=Master)
invalidateState()   → 轮询到变化时通知
```

**播放页、进度条、按钮、通知、锁屏、蓝牙按键就全部白拿**，一行 UI 不用手写。
附带好处：`MediaSessionService` 天然解决我们刚做完的「前台服务保活」，可以替换掉手写的
`QueuePlaybackService` 通知代码（保留编排逻辑）。

## 三档方案与取舍

| 方案 | 做法 | 工作量 | 风险 |
|---|---|---|---|
| **A（推荐）** | 加 `media3-ui-compose-material3` + `media3-session`，写 `RemoteRendererPlayer`，播放页直接用 `Player` 组件 | 中（主要是 Player 适配层，可单测） | 低。库是官方的，`SimpleBasePlayer` 标 `@UnstableApi`，但只影响 API 稳定性，不影响可用性 |
| B | 只用 `media3-ui-compose` 的 state holder（`PlayPauseButtonState`/`CurrentMediaItemState`/`PlaylistState`），UI 自己画 | 中 | 低，但等于把「按钮长什么样」又变成自己的活 |
| C | 抄 Gramophone / Auxio 的界面代码 | — | **不可行**：两者都是 **GPL-3.0**，一旦抄进本仓库，整个项目被迫 GPL 化 |

## 许可证边界（必须守住的线）

| 项目 | 许可证 | 能不能用它的代码 |
|---|---|---|
| AndroidX Media3 | Apache-2.0 | ✅ 随便用 |
| Compose Material3 | Apache-2.0 | ✅ |
| Gramophone | GPL-3.0 | ❌ 不能抄代码；可以看**设计**后自己实现 |
| Auxio | GPL-3.0（README 写明 GPL） | ❌ 同上 |
| MusicFree | AGPL-3.0 | ❌ 传染性更强 |
| RetroMusicPlayer | GPL-3.0 | ❌ |

> 「参考设计」与「复制代码」的界：布局层级、交互顺序、配色观感属于设计思想，可学；
> 具体的 Composable 实现、资源文件、字符串要自己写。这与 docs/01 的 clean-room 原则一致。

## 还缺的一块：DLNA 特有的界面

media3 解决的是「播放控制」，不解决：

- 媒体服务器浏览（目录树、专辑/艺术家网格、多选入队）→ 需要自己写，但没有更好的开源可借
  （BubbleUPnP/Hi-Fi Cast 都是闭源）
- 设备卡片（音量、输入源切换、多房间、预设键）→ 自己写，用 Material3 组件
- 队列编辑（拖拽排序、删除、跳播）→ Compose 的 `LazyColumn` + `reorderable` 类库

这三块里，「媒体库浏览」参考 **Gramophone 的信息层级**（列表 → 详情 → 现在播放）就够了，
它是 Material 3 音乐库的标准范式。

## 建议的落地顺序

1. 引入 media3（`media3-common` + `media3-ui-compose-material3` + `media3-session`），不动现有逻辑
2. 写 `RemoteRendererPlayer`（纯适配，`getState` 用已有轮询，可对假渲染器单测）
3. 播放页换成 `Player` 组件 + 自定义底部 `MiniController` 常驻
4. `MediaSessionService` 接管通知与锁屏（删掉手写通知代码）
5. 最后再谈视觉（配色/字体），此时骨架已稳
