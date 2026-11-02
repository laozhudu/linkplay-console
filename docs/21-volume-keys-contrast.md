# 21 · 多房间到底部 / 音量键绑定 / 提高对比度

## 1. 多房间继续下移到最底部

`MultiroomCard` 现在排在首页 `LazyColumn` 的**最后一个 item**——设备列表、添加音箱、AP 配网入口之后。
理由：组网是组一次管很久的低频配置，首屏应该先给"要用的设备"和"添加"。

## 2. 音量与手机音量键绑定（原厂行为）

实现在 `MainActivity.dispatchKeyEvent` + `ConsoleViewModel.adjustVolumeByKey`。

三个容易写错的点，都有单测钉住（`VolumeStepTest` 7 个）：

| 点 | 做法 |
|---|---|
| 在哪拦 | **`dispatchKeyEvent`**，不能只拦 `onKeyDown` —— 系统在 ACTION_UP 上也会调音量 |
| DOWN / UP 一致 | 两者都问 `canHandleVolumeKey()`，否则会出现"按下改音箱、抬起改手机" |
| 到顶到底 | **继续吞事件**。原实现按"还能不能变"判断，到 100 后继续按会把控制权交回系统，手机会突然开始动自己的媒体音量 |

- 步长 5（0-100 刻度，约等于 Android 15 档里的一档）
- 没选设备 / 设备离线 → **不拦**，让系统照常调手机音量（不能把手机变成砖）
- 值仍写进 `VolumeBus`，所以播放页的音量条同一帧跟着动

另外把「设备音量」和「组内成员音量」拆成两条通道（`deviceVolumeChannel` / `groupVolumeChannel`）：
前者语义是"当前设备"，跟有没有组无关；混在一起会出现"没组网时按键没反应"。

## 3. 对比度

### 找到一个真 bug（只在浅色模式暴露）

首页设备卡和播放页的渐变底是 `seedOf()` 派生的**深色**（hsl 亮度 0.24，永远深），
但文字色用的是 `ExtraColors.onBackdrop` —— 它会跟系统主题走。
**浅色主题下它是近黑，压在深渐变上等于隐形。**

修法：新增 `OnDarkPalette`（恒定白系），专门给"压在派生深底上的文字"用；
播放页改成**永远深色底**（暂停时也只深一档），这样播放页只面对一套文字色，不必为两种主题各调一遍。

### 其余提高对比度的改动

| 位置 | 改动 | 效果 |
|---|---|---|
| 深色 surfaceVariant | `0xFF1B2029` → `0xFF232A36` | 卡片与页面底拉开一档 |
| 深色 onSurfaceVariant | `0xFFC2C7D2` → `0xFFDCE1EA` | 正文 10.99:1 |
| 浅色 onSurfaceVariant | 默认灰 → `0xFF3B4250` | 正文约 9:1 |
| 浅色 surface / background | 补齐全部槽位 | 原来只覆盖 4 个色，其余是默认灰，卡片没层次 |
| `labelSmall` | 11sp 常规 → **12sp Medium** | 全站最难读的一档 |
| `labelMedium` | 补行高 16sp | 小字更耐读 |
| `AppCard` | 加 1dp 描边（outline 35%） | 卡片边界看得见，不再"字黏在背景上" |
| `PillTag` | 底 0.16→0.22、加 1dp 同色描边、垂直内边距 2→3dp | 小标签可读 |
| 播放页面板 | 白 6% → `OnDarkPalette.chip` | 内容区有厚度 |
| 状态色 | ok/warn/error 各提亮一档 | 深色卡片上 8.28:1（原 6.91） |

### 对比度门禁进 CI

`ThemeContrastTest` 把 WCAG 相对亮度算法写进单测，覆盖：
深/浅主题的正文与次要文字、播放页渐变两端、主按钮、状态色、以及 `seedOf` 派生的 5 组配色。
**任何一次改色掉到 4.5:1 以下，CI 直接红** —— 这类问题以后不用再靠肉眼发现。

关键数值（改前 → 改后）：浅色次要文字 4.46 → 8.14，深色播放页次要文字 7.78 → 10.41，
深色正文 9.64 → 10.99。

## 验证

- `:core-protocol:test` + `:app:testDebugUnitTest` **312 用例 0 失败**（新增 `VolumeStepTest` 7、`ThemeContrastTest` 7）
- `:app:assembleDebug` 通过
- 音量键的真实手感需你在手机上按几下确认；配色按上面数值判断，观感请一并过目
