# 任务单 · 第三轮：动效落地 + 第一批补漏收尾

> **依据的代码状态：提交 `3096e9d`**（#135 compose-bom 升级已合入）
> 承接 `docs/86`（施工单）、`docs/87`（BOM 兼容性实测）
> 性质：**接单开工文档**。每条写了「做什么 / 做到什么程度 / 收益 / 回退」。
> 本文的验收数据是我**自己重跑 grep + 解包 APK** 得到的，不是复述汇报。

---

## 零、结论

**BOM 升级成立，可以开始做动效了。但第一批还有两个 🔴 没修，动效做完之前先把它们补上。**

| # | 事实 | 状态 |
|---|---|---|
| 1 | `#135` **只改了一行**，AGP / Gradle / Kotlin / compileSdk 一个没动 | ✅ 复核通过 |
| 2 | `MotionScheme` **真的进了 APK**（不是只在 toml 里） | ✅ 解包确认 |
| 3 | 1,036 测试 / `ThemeContrastTest` 7 用例 | ✅ 数字对得上 |
| 4 | **动效一处没做**（`motionScheme` 全仓 0 次使用） | ⏳ 本轮开始 |
| 5 | **第 4 条下发路径还在**（`PlaybackViewModel.kt:503`） | 🔴 未修 |
| 6 | **输出开关还在洛雪页**（播放页没有） | 🔴 未修 |

**这一轮做四件事**：补两个 🔴 → 动效三处 → 触感补档 → 加测试锚点。

---

## 一、BOM 升级验收（我的独立复核）

### 1.1 确实只改了一行

```
gradle/libs.versions.toml
-  composeBom = "2024.12.01"
+  composeBom = "2026.03.01"
```

```
agp = "8.7.3"          ← 没动
kotlin = "2.1.0"       ← 没动
gradle 8.11.1          ← 没动
compileSdk 35          ← 没动（APK 里 compileSdkVersion='35' 可证）
```

### 1.2 MotionScheme 真的进了 APK（这条最关键）

只改 toml 不能证明解析到了 1.4.0。我把 `linkplay-console-0.9.136-debug.apk` 解包查了：

```
dex 文件 30 个
含 "MotionScheme" 的 dex：classes.dex
含 "ExpressiveMotionTokens" 的 dex：classes.dex（命中 3 处）
```

**类真的在包里，说明 material3 确实解析到了 1.4.0。** 这条以前只能靠相信汇报，现在有实物证据。

### 1.3 数字对得上

```
@Test 总数 1,036（app 174 / protocol 613 / lx 97 / radio 54 / diagnostics 50 / upnp 22 / script 15 / discovery 11）
ThemeContrastTest  @Test 7
APK  0.9.136  versionCode 136  compileSdk 35  targetSdk 35  minSdk 24  21MB
```

### 1.4 一个要纠正的说法

> 「上一轮我说得升 AGP 9，实际是我直接试了最高版本那一档」

这个自我纠正是对的，而且**「升版本和改界面不放同一个 PR」这条纪律执行得好**（回退时界面没白做）。

**但要注意**：这次能成，是因为 `docs/87` 先把「哪一档能过」查出来了。下次遇到「依赖升级撞墙」，
**第一件事是去读 AAR 元数据找分水岭，不是往上试**。方法在 `docs/87` 第四节。

---

## 二、动效现状：BOM 升了，但一处没用

```
animateFloatAsState        2
AnimatedVisibility         2
Crossfade / AnimatedContent 0
animateItemPlacement       0
MaterialTheme.motionScheme  0   ← 升上来的东西一次没使
```

**有意思的是**：`PlayerScreen` 里已经有一处手写 spring：

```kotlin
val artworkScale by animateFloatAsState(
    targetValue = if (playing) 1f else 0.94f,
    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
    label = "artwork-scale",
)
```

**这就是「手感不统一」的活样本** —— 参数是手挑的，跟别处没有关系。
升 BOM 换来的正是「不用再手挑」。所以 T3 的第一步就是把它换掉。

---

## 三、本轮任务（四件，按优先级）

### T1 · 🔴 把「从哪出声」搬到播放页

**为什么排第一**：这是 `docs/86` 1.3 真正的收益项，现在**机制通了但用户摸不到**。

现状比修之前更别扭：5 个源都受这个开关影响了（都走 `PlaybackRouter`），
但开关还锁在洛雪页 —— 用户在 NAS 页想听手机上放，得**跑去洛雪页切，再回来点播放**。

**做什么**

把 `LxSearchScreen.kt` 里那组 `FilterChip`（`本机` / `音箱`，约 80-102 行）搬到 `PlayerScreen`。

落点：**播放页顶部**（标题行下方、封面之上），按现有布局就近放。

⚠️ **`docs/37` 铁律：播放页不许有任何浮层。** 所以只能是内联一行，不能用 `ModalBottomSheet` / `DropdownMenu`。

⚠️ **搬完把洛雪页那份删掉**，不要留两处 —— 两处都能改同一个状态就会不同步。

**做到什么程度**

- [ ] `grep -rn 'setOutput(' app/src/main` → 只在 `PlayerScreen`（和 `PlaybackRouter` 自身）出现
- [ ] `LxSearchScreen` 里零命中
- [ ] 播放页能看到「从哪出声 本机 / 音箱」
- [ ] 在播放页切「本机」，然后从 **NAS / 手机本地 / 在线 / 歌单 / 洛雪** 任一处点播放 → 声音从**手机**出来
- [ ] 切「音箱」→ 声音从音箱出来，**不两边一起响**
- [ ] 播放页顶部因此多一行，**封面和标题没有被挤变形**

**收益**

| 项 | 前 | 后 |
|---|---|---|
| 能手机本地播的源 | 机制 5/6，**用户实际只能用 1/6** | **5/6** |
| 通知栏 / 锁屏 / 耳机键 | 大部分场景用不上 | 切到本机就活 |
| 找开关要去的页面 | 洛雪页（跟源无关的页面） | 播放页（本来就在放声音的地方） |

**回退**：`git revert`，界面回到现在（洛雪页那组 chip 不动即可）。

---

### T2 · 🔴 堵掉第 4 条下发路径

**现状**（`PlaybackViewModel.kt` 约 500-513 行）：

```kotlin
// ② 本地有队列但设备没有：把队列交给设备（原厂点"全部播放"就是这个动作）
if (!hasDeviceQueue && localItems.isNotEmpty()) {
    val ok = withContext(Dispatchers.IO) {
        runCatching { DevicePlaylistGateway.push(deviceIp, location, localItems, 0) }.getOrNull()
    }
    if (ok?.ok == true) { … }
}
```

**三个问题**：

1. **没有能力位判断** —— 投通用 DLNA 渲染器（电视盒子）会白撞一次 401
2. **没有二段三段降级** —— PlayQueue 拒了就放弃，不会退 m3u 再退客户端逐首
3. **和唯一入口不同步** —— 唯一入口有 `PlayJournal.recordCurrent`、`QueueBus.restore`，这里只有 `markDeviceOwned`

**做什么**

改成调唯一入口：

```kotlin
if (!hasDeviceQueue && localItems.isNotEmpty()) {
    val ok = startQueueOnDevice(
        context = appContext ?: return@safeLaunch,
        deviceIp = deviceIp,
        location = location,
        deviceUuid = null,          // 这里拿不到 uuid 就传 null，startQueueOnDevice 会退回用 ip
        items = localItems,
        startAt = 0,
        note = "全部播放",
        log = ::log,
    )
    if (ok) {
        _state.update { it.copy(busy = false, error = null) }
        refreshOnce(silent = true)
        return@safeLaunch
    }
}
```

**做到什么程度**

- [ ] `grep -rn 'DevicePlaylistGateway.push\|DeviceQueuePush.push' app/src/main` → **只在 `QueueStarter.kt` 里**
- [ ] 首页/播放页点「全部播放」到 DOSS 音箱 → 日志出现「设备侧队列 · 原厂 PlayQueue」
- [ ] 点「全部播放」到通用渲染器（`.175`）→ 日志**不出现**「PlayQueue 未接受」，而是直接说「该设备是通用 DLNA 渲染器」
- [ ] 「最近播放」**只记一次**（`startQueueOnDevice` 自己会记，别在外层再记一次）

**收益**

| 项 | 前 | 后 |
|---|---|---|
| 下发路径 | **4 条** | **1 条** |
| 投通用渲染器 | 白撞一次 401 超时 | 直接跳过 |
| `docs/86` 验收第 1 条 | 不成立 | **成立** |

**回退**：单文件改动，`git revert` 即可。

---

### T3 · 动效 token + 三处落地

**前置已满足**：`MaterialTheme.motionScheme` 可用（APK 里已确认有 `MotionScheme` 类）。

#### 3.1 先建 token，只建一个文件

```kotlin
// ui/theme/Motion.kt
// 为什么单独一个文件：页面里不许出现字面量时长。
// 统一从这里取，改手感只改这一处。
```

用官方六档（`MaterialTheme.motionScheme` 自带，不必自己定义数值）：

| 用途 | 取哪个 |
|---|---|
| 形状 / 尺寸 / 位移（spatial） | `defaultSpatialSpec()` |
| 颜色 / 透明度（effects） | `defaultEffectsSpec()` |
| 快反馈（按下、切换） | `fastSpatialSpec()` / `fastEffectsSpec()` |

#### 3.2 三处落地（只做三处，不要铺开）

**① 把现有的手写 spring 换掉**（这是「换 token」最直接的示范）

```kotlin
// PlayerScreen.kt 现状
animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),

// 改成
animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
```

**② Tab 切换加 `AnimatedContent`**

```kotlin
// ConsoleApp.kt 里 Tab 内容区
AnimatedContent(
    targetState = tab,
    transitionSpec = {
        fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()) togetherWith
            fadeOut(MaterialTheme.motionScheme.fastEffectsSpec())
    },
    label = "tab",
) { t -> when (t) { … } }
```

**③ 队列增删加 `animateItemPlacement`**

```kotlin
// QueueScreen / PlayerScreen 的 LazyColumn items
Modifier.animateItemPlacement(animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec())
```

**做到什么程度**

- [ ] `grep -c 'motionScheme' app/src/main` ≥ 3
- [ ] `grep -rn 'spring(' app/src/main` 里**不再有手挑参数**（要么删掉，要么换成 token）
- [ ] 三处动效**肉眼可见**，且列表滑动**不卡**
- [ ] 开发者选项「动画时长 0.5x」下不崩、不卡死
- [ ] **播放页仍然没有任何浮层**（`docs/37`）

**收益**

| 项 | 前 | 后 |
|---|---|---|
| `motionScheme` 使用 | 0 | 3 处 |
| 手挑 spring 参数 | 1 处 | 0 |
| 切 Tab | 瞬变 | 淡入淡出 |
| 队列增删 | 列表跳变 | 平滑位移 |
| 手感一致性 | 各写各的 | 同一套 token |

**回退**：三处各自独立，可以只回退某一处。

⚠️ **动效不是越多越好**。只做这三处，做完先看手感，**不要顺手给每个页面都加**。

---

### T4 · 触感补两个语义档

**现状**：两处都是 `TextHandleMove`（最轻档），加在 `AppCard` 和 `BottomNav` 上。

**问题**：`AppCard` 是**容器**不是**动作**。点「配置备份」「诊断记录」「六键预设」全一样震，
语义完全不同 —— 用户分不出「成功了」和「点了没反应」。

**做什么**

| 位置 | 档位 | 语义 |
|---|---|---|
| 切歌成功（`next` / `prev` / 点队列项） | `Confirm` | 「做成了」 |
| 操作失败（连不上设备 / 投流被拒） | `Reject` | 「没成」 |
| `AppCard` 默认（保留） | `TextHandleMove` | 「收到这一下」 |

**外加一个自检**：设置页放「测试触感」按钮，点一下依次触发三档。

**为什么需要自检**：触感在各家 ROM 上差别很大（有的很轻、省电模式一次都不给）。
**不要**因为「没感觉」就全局换重档 —— `Confirm` 是「确认了重要操作」，
用在点卡片上会变成震动提醒，反而吵。让用户当场自己确认最省事。

**做到什么程度**

- [ ] `grep -c 'HapticFeedbackType\.' app/src/main` ≥ 4（现在 2）
- [ ] 成功和失败**震感不同**（闭眼能分辨）
- [ ] 点普通卡片仍是轻档，不吵
- [ ] 设置页有「测试触感」，三档可分辨

**收益**：触感从「覆盖率 100%、语义 0%」变成「覆盖率 100%、关键两档有语义」。

---

### T5 · 补 `testTag` + `contentDescription`

**为什么这条不能一直往后放**：它不只是无障碍 ——
**没有 `testTag`，`docs/76` 的实机测试台做不了**（`uiautomator` 读不到文字节点，
agent 只能按坐标点，换手机就废）。

**做什么**

- 所有 `IconButton` 加 `contentDescription`
- 主控件加 `testTag`，格式 `<页面>.<功能>`，列表项带 uuid

**做到什么程度**

- [ ] `grep -c 'testTag' app/src/main` ≥ 30（现在 0）
- [ ] `grep -c 'contentDescription' app/src/main` ≥ `IconButton` 数量（现在 2）
- [ ] 开 TalkBack 走一遍 4 个一级页 + 播放页：每个按钮都念得出名字
- [ ] `lpdiag.py ui` 能按 text 找到播放/暂停按钮（这是自动化的门槛）

**收益**

1. 无障碍从「2 处」到可用
2. **解锁实机自动化测试** —— 以后「点了没反应」能自动抓，不靠用户截图

---

### T6 · 给唯一入口补三条降级测试

**为什么现在补**：T2 改完之后，「同一动作只有一条实现」才成立，
这时候补测试才锁得住 —— 否则测试锁的是错的实现。

**做什么**

给 `startQueueOnDevice` 补一组单测（假 Gateway / 假 Push / 假 Service，纯 JVM）：

| 用例 | 断言 |
|---|---|
| 能力位 `supportsPlayQueue = false` | **不调用** `DevicePlaylistGateway.push` |
| PlayQueue 成功 | **不调用** `DeviceQueuePush.push` / `QueuePlaybackService.start` |
| PlayQueue + m3u 都失败 | 走到 `QueuePlaybackService.start`（客户端逐首） |

**做到什么程度**

- [ ] 3 个用例，`@Test` 总数从 1,036 涨到 ≥ 1,039
- [ ] 故意把能力位判断删掉 → **测试必须挂**（RED 验证，不能只写绿灯用例）

**收益**：`docs/87` 里「NAS 漏了能力位判断」这类问题，以后进不来。

---

## 四、PR 切分建议（4 个，别攒一个）

| PR | 内容 | 为什么这样切 |
|---|---|---|
| **A** | T2 + T6（堵路径 + 单测） | **零界面改动**，纯结构 + 测试，最好审 |
| **B** | T1 + T4（开关搬家 + 触感分档） | 都是小界面改动，一起看不会乱 |
| **C** | T3（动效 token + 三处） | 纯视觉，做完要看手感，单独一轮 |
| **D** | T5（testTag + 无障碍） | 量大但机械，适合独立一轮 |

**A 先合**（结构对了，后面改界面不会返工）。
**C 和 B 顺序可以换**，但不要混在一个 PR 里 —— 动效出问题时要能单独回退。

---

## 五、验收总表

| # | 验收项 | 怎么验 | 属于 |
|---|---|---|---|
| 1 | 下发路径只剩 1 条 | `grep -rn 'DevicePlaylistGateway.push\|DeviceQueuePush.push' app/src/main` → 只在 `QueueStarter.kt` | A |
| 2 | 三条降级有测试 | `@Test` ≥ 1,039，删能力位判断测试会挂 | A |
| 3 | 投电视盒子不再撞 401 | 真机投 `.175`，日志无「PlayQueue 未接受」 | A |
| 4 | 开关只在播放页 | `grep -rn 'setOutput(' app/src/main` → 只在 `PlayerScreen` | B |
| 5 | 5 个源都能手机本地播 | 逐个点播放，声音从手机出来 | B |
| 6 | 触感能分辨成功/失败 | 闭眼对比 | B |
| 7 | 设置页有「测试触感」 | 点一下三档依次触发 | B |
| 8 | 三处动效可见且不卡 | 真机看；开 0.5x 动画不崩 | C |
| 9 | 无手挑 spring 参数 | `grep -rn 'spring(' app/src/main` 为零或全是 token | C |
| 10 | 播放页仍无浮层 | 真机看（`docs/37`） | C |
| 11 | `testTag` ≥ 30 | `grep -c testTag` | D |
| 12 | TalkBack 能念出每个按钮 | 开 TalkBack 走一遍 | D |

---

## 六、明确不做

| 不做 | 理由 |
|---|---|
| 升 AGP 9 / Gradle / Kotlin | BOM 已经通了，`MotionScheme` 已到手，没有理由再动 |
| 追更高 BOM（2026.06.01 / 2026.08.00） | 2026.06.01 要 Kotlin 2.1.20，2026.08.00 要 AGP 9.1.0。收益和现在完全一样（都是 material3 1.4.0） |
| 让本地播放支持整份列表 | `PlaybackRouter.playQueue` 的 Local 分支只播一首，这是**已知限制且已诚实标注**。要做是另一件事（本地队列 + MediaSession 队列同步），不在本轮 |
| 给每个页面都加动效 | 只做三处，先看手感 |
| 因为「触感没感觉」全局换重档 | 语义错了，会变成震动提醒 |
| 第三批（入口重排 / `canPlayAudio` / `QueueItem.providerId`） | 是结构改动，排在 A 合入之后单独一轮 |

---

## 七、五个坑（都踩过）

1. **`PlayQueueWithIndex` 的 Index 是 1 基**，m3u 只认 `index=0` 要旋转列表 —— 这段在 `startQueueOnDevice` 里，T2 别重写
2. **`QueueBus.restore` 自己会设 owner**，不要在它前面 `setOwner`（旧 API 的两步写法）
3. **`startQueueOnDevice` 自己会记「最近播放」**，T2 改完别在外层再记一次，否则重复
4. **通用渲染器没有 `PlayQueue`**，硬推拿 401 白等一轮 —— 能力位判断必须在推之前
5. **换 BOM 后 `ThemeContrastTest` 是唯一防线** —— 后续任何界面改动都要让它保持绿

---

## 八、一句话

**BOM 那一仗打赢了（一行改动 + 物证），接下来该把「赢了但用户摸不到」的两处补上 ——
开关搬到播放页、第 4 条路径堵掉 —— 然后再动手效。动效只做三处，做完先看手感。**

---

## 附：本文验收数据（可复核）

```
依据 3096e9d（#135）

#135 diff：gradle/libs.versions.toml 一行（composeBom 2024.12.01 → 2026.03.01）
agp 8.7.3 / kotlin 2.1.0 / gradle 8.11.1 / compileSdk 35 —— 全部未动

APK 0.9.136（21MB）
  versionCode 136 / compileSdk 35 / targetSdk 35 / minSdk 24
  30 个 dex，classes.dex 内含 MotionScheme 与 ExpressiveMotionTokens（3 处命中）

@Test 1,036（app 174 / protocol 613 / lx 97 / radio 54
             / diagnostics 50 / upnp 22 / script 15 / discovery 11）
ThemeContrastTest 7 用例

动效：animateFloatAsState 2 / AnimatedVisibility 2 / Crossfade 0
      animateItemPlacement 0 / motionScheme 0

触感：2 处，均为 TextHandleMove
无障碍：testTag 0 / contentDescription 2 / semantics 0

未修：
  PlaybackViewModel.kt:503  DevicePlaylistGateway.push（第 4 条路径）
  LxSearchScreen.kt:90/98   setOutput（开关仍在洛雪页）
```
