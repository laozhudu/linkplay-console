# 54 · 拆 MainActivity（1417 行 → 7 个文件）+ 界面位置进诊断

> 用户 2026-11-04：「同意，基础模块做扎实，以后才轻松」
> 规则依据：`docs/52`（任何开发动作必须伴随诊断台进化）

---

## 一、拆之前

`MainActivity.kt` **1417 行**，里面混着三件完全不同的东西：

| 混在里面的 | 行数 |
|---|---|
| 生命周期 + 音量键拦截 | ~57 |
| 导航骨架（Tab / 页面栈 / 底部栏） | ~250 |
| 首页（顶栏 + 崩溃横幅 + 播放条 + 设备列表 + 4 个对话框） | ~543 |
| 设备卡片 | ~333 |
| 杂项（迷你播放条、媒体库页、空态） | ~230 |

最能说明问题的：**改一个按钮的位置，要在 1400 行里找**；加一个功能容易碰到别的东西。

---

## 二、拆之后

| 文件 | 行数 | 职责 |
|---|---|---|
| `MainActivity.kt` | **68** | 生命周期 + 音量键拦截 + 崩溃记录 |
| `Nav.kt` | 18 | 一级页 / 二级页枚举 |
| `ConsoleApp.kt` | 331 | 导航骨架 + 音量浮层宿主 + 底部栏 + 空态 |
| `SourcesScreen.kt` | 40 | 媒体库一级页 |
| `home/HomeScreen.kt` | 607 | 首页 |
| `home/DeviceCardRow.kt` | 384 | 设备卡片 |
| `home/MiniPlayerBar.kt` | 99 | 迷你播放条 |

**MainActivity 从 1417 行降到 68 行**，只留真正属于 Activity 的东西
（生命周期、`dispatchKeyEvent` 音量键、崩溃记录安装）。

### 分包规则

按**功能**分包，和现有的 `device/` `library/` `nas/` `radio/` 一致：
首页相关的东西进 `home/`，导航骨架留在 `console/` 根。

没有新建 `ui/screens/` 那种"按类型"的包 —— 因为现有项目是按功能分的，
混用两种分法会让"该放哪"变成每次都要纠结的问题。

### 唯一的行为改动：`private` → `internal`

跨文件调用就不能是 `private`。用 `internal` 而不是 `public`：
同一模块内可见，但**不暴露给将来依赖这个 App 的东西**。

---

## 三、这次踩到的坑：清理 import 删掉了 `getValue` / `setValue`

拆完先跑了一遍"删未使用 import"的脚本，结果编译报了一堆：

```
Type 'State<...>' has no method 'getValue(Nothing?, KProperty0<*>)',
so it cannot serve as a delegate.
```

**根因**：`androidx.compose.runtime.getValue` / `setValue` 是**委托操作符**，
用 `by remember { }` / `by state.collectAsState()` 时**隐式调用**——
代码里根本不出现 `getValue` 这个标识符，脚本判定"未使用"就删了。

修法：清理脚本必须知道这两个是隐式使用的。已经加进 skill 的陷阱表。

顺带踩的第二个坑：插入 import 时漏了 `import ` 前缀，
变成 `androidx.compose.runtime.setValue` 这种裸行，Kotlin 报
`Expecting a top level declaration`（因为它在文件里像是个表达式）。

---

## 四、这一轮的诊断进化：界面位置

按 `docs/52` 的规则，拆文件本身不产生新状态，但**拆的过程中暴露了一个盲区**：
用户说「点了没反应」时，我不知道他站在哪一页。

同样的现象在不同页面是**不同的问题**：

| 用户在哪 | 「点了没反应」的真实原因 |
|---|---|
| 首页点卡片 | 选中设备失败 |
| 播放页点暂停 | `PlaybackViewModel` 那条链路 |
| **有个对话框开着时点任何地方** | **对话框挡住了（最常见，也最容易被误报成 bug）** |
| 音量浮层显示中 | 全屏遮罩吃掉了点击 |

没有这个信息，我只能反问「你在哪一页点的」——而这正是用户最不耐烦回答的问题。

### 做法

`NavTrace`（极简全局快照，4 个 `@Volatile` 字段）：

```kotlin
object NavTrace {
    var tab: String = "设备"
    var subPage: String? = null
    var overlay: String? = null          // 开着的对话框
    var volumeOsdVisible: Boolean = false
}
```

写入方：
- `ConsoleBody` 里 `LaunchedEffect(tab, sub)` 更新一级页 / 二级页
- `VolumeOsdHost` 里更新音量浮层
- `HomeScreen` 里 `LaunchedEffect(6 个对话框状态)` 更新浮层名

报告里长这样：

```
[界面]
  一级页=设备
  二级页=无
  浮层=设备菜单  ← 浮层开着时点背景不会响应
  音量浮层=显示中  ← 全屏遮罩，会吃掉点击
```

**为什么用 `LaunchedEffect` 而不是直接在 body 里赋值**：Compose 的 body 会因为
任意状态变化而重组，直接赋值等于每帧写一次全局变量；`LaunchedEffect` 只在真的变了才跑。

**为什么不用 Compose 状态**：诊断页和首页不在同一棵 Compose 树里，拿不到对方的
`remember`。而且报告要的是"此刻在哪"的快照，不是随状态重组。

---

## 五、验证

| 项 | 结果 |
|---|---|
| `:core-protocol:test` | 611 用例 0 失败 |
| `:core-diagnostics:test` | 41 用例 0 失败 |
| `:app:testDebugUnitTest` | **53 用例 0 失败**（+6：NavTraceTest） |
| 合计 | **705 用例 0 失败** |
| `assembleDebug` | 成功 |
| `check-init-order.py` | 通过 |
| `check-diagnostics-labels.py` | 通过（61 处） |

`NavTraceTest` 6 个，锁的是「能不能判断用户被浮层挡住了」：

- 默认状态说「二级页=无 / 浮层=无」
- 有浮层时**必须带上后果说明**（只写「浮层=设备菜单」不够，要让人知道这会吃掉点击）
- 音量浮层单独标「全屏遮罩」（它和对话框不同，用户完全看不出有问题）
- 没有浮层时不出现浮层那一行

**未验证**：真机上看报告里是否真的出现「界面」这一段、切页面时是否实时更新
（`.153` 离线，`.164` 只做只读核对）。

---

## 六、还剩什么

`HomeScreen.kt` **607 行**还是偏大，里面还能再分三块：

| 内部块 | 行数估算 | 拆出去之后 |
|---|---|---|
| 崩溃横幅 | ~45 | `home/CrashBanner.kt` |
| 聚合播放条 | ~50 | `home/NowPlayingBar.kt` |
| 4 个对话框（分组 / 菜单 / 改名 / 睡眠） | ~200 | `home/HomeDialogs.kt` |

拆这几块需要**状态提升**（对话框用到的 `menuTarget` / `sleepMenu` 等局部状态要传参），
比纯搬代码风险高，所以留到下一轮单独做，一次只动一块。

其它待办：
- `ConsoleViewModel` 1386 行（`DeviceRegistry` 拆分，等 `RemoteTarget` 定了）
- `PlaybackViewModel` 851 行 / `PlayerScreen` 842 行
- 诊断盲区：电台 / 在线曲库 / NAS / 本地曲库 / 悬浮窗 / 语音 / 配网
