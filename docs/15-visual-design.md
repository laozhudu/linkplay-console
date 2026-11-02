# 界面参考：能借什么，不能借什么

## 结论先说

| 想要的东西 | 现实 | 怎么办 |
|---|---|---|
| 直接依赖一个「好看的播放器 UI 库」 | **没有**。开源里好看的音乐 App 几乎全是 GPL-3.0 | 自己按 Material 3 Expressive 规范画，规范是公开的、没有版权问题 |
| 官方现成组件 | `media3-ui-compose-material3`（Apache-2.0）有 `Player` / `PlayPauseButton` / `ProgressSlider` 等 | 风格是「标准 Material3」，做不出层次感。取它的**架构**，不取外观 |
| 参考设计（信息层级、交互节奏） | Gramophone / Auxio / Rhythm / RetroMusic 都可看 | 看得进脑子，进不了代码库 |
| 可复制代码的设计系统 | `meticha/material-3-expressive-catalog`（**Apache-2.0**） | 唯一一处可以放心抄组件的来源 |

## 许可证核对（逐个查过仓库页面）

| 项目 | 许可证 | 结论 |
|---|---|---|
| `meticha/material-3-expressive-catalog` | **Apache-2.0** | ✅ 组件可复制/借鉴 |
| `FoedusProgramme/Gramophone` | **GPL-3.0** | ❌ 不能抄代码 |
| `cromaguy/Rhythm` | **GPL-3.0** | ❌ 不能抄代码 |
| `Auxio` / `RetroMusicPlayer` | GPL-3.0 | ❌ 不能抄代码 |
| `MusicFree` | AGPL-3.0 | ❌ 更严格 |
| AndroidX Media3 / Compose Material3 | Apache-2.0 | ✅ 可依赖 |

> GPL 的传染性：一旦把 GPL 代码贴进本仓库，整个仓库要么整体 GPL，要么下架。
> 「设计参考」与「复制代码」的界线是：布局层级、交互顺序、配色观感属于设计思想（可学）；
> 具体 Composable 实现、资源文件、字符串要自己写。

## 这一轮实际做成的视觉基线

自绘，不引第三方 UI 库。核心决策与理由：

### 1. 配色由曲名派生，而不是固定主题色

`seedOf(title, artist)` 取哈希落到 7 个色相上，生成「渐变顶色 + 强调色 + 背景色」。

- 为什么派生：同一首歌每次进来颜色一致（不会闪），不同歌又有区分度；
  固定主题色会让所有歌长得一样，纯随机色又会每次刷新都变。
- 为什么不用 `androidx.palette` 从封面取色：没有真实封面（DLNA 的 `albumArtURI` 多数为空），
  取色库反而要处理「没有图」的分支。

### 2. 播放页的三层结构

```
背景：垂直渐变（播放中才用曲名色，暂停退回中性色 → 一眼看出在不在播）
中层：1:1 大封面（无图时用渐变色块 + 曲名首字 96sp）
前景：曲名 / 艺术家 / 进度条 / 主控 / 音量
```

主控按钮 76dp、两侧 48dp 触控区、其余图标 30dp —— 拇指落点与视觉重量都靠这个比例。

### 3. 图标全部手绘（Canvas）

不用 `material-icons-extended`：那个包给 APK 多约 3 MB，而我们只要十来个图形。
`material-icons-core` 又缺 Repeat/Shuffle/Stop。手绘 20 行还能精确控制线宽，
按钮放大时视觉重量不变。

### 4. 状态色单独一层

`StatusColors.ok / warn / error`：`CONTROL_LOST`（设备在线但 UPnP 掉了）和 `OFFLINE`
（整台不通）处置完全不同，共用一个 `colorScheme.error` 会让人去查网络。

## 三条写进规矩的约束

1. 尺寸只用 `Spacing.*`，颜色只用 `colorScheme` 或 `LocalExtraColors`；
2. 圆角只用 `AppShapes`，不写裸 `RoundedCornerShape(13.dp)`；
3. 同一套控制只出现一次（详情页只留摘要行，控制集中在播放页）。

第 3 条来自本轮的实际教训：详情页原来嵌了一整套控制卡片，加了全屏播放页之后就有两份，
改一处必然漏另一处。**界面漂移比功能缺失更难发现**。

## 还没做的（下一步）

- 真实封面图：需要图片加载库（Coil，Apache-2.0），并在 `albumArtURI` 非空时取图 + 从图取色
- 列表页（设备/目录）的卡片化重做：本轮只统一了主题，列表仍是旧布局
- 播放页动效：切歌时封面 crossfade、背景色平滑过渡
- 平板/宽屏双栏

## 参考清单（供以后看设计，不复制代码）

- Material 3 Expressive 规范：层级、运动曲线、尺寸阶梯的官方定义
- Gramophone：列表 → 详情 → 现在播放的信息层级范式
- Auxio：大目录下的滚动性能与索引
- Spotify / Apple Music 的 now playing：封面主导 + 渐变的行业共识
