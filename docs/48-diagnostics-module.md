# 48 · 诊断台独立成通用模块

上一轮（docs/47）拆完 `DeviceControl`，用户的反馈是：

> 诊断台的功用现在可以更广泛精准，用处很大，考虑独立成一个通用模块接入。

这句话拆成两个要求：**更广泛精准**（能力升级）+ **独立成模块**（可复用）。

两件事其实是一件事：只有先把「通用的部分」和「这个项目特有的部分」分开，
才能既复用又升级 —— 现在这两半是搅在一起的。

---

## 一、现状：五处耦合

| 文件 | 行数 | 耦合在哪 |
|---|---|---|
| `DiagnosticsHub` | 85 | `object` 单例；段只有一段纯文本，**没有级别** |
| `IssueLog` | 71 | `object` 单例；存的是**格式化好的字符串**（`"00:09:12 [类名] ..."`） |
| `DiagnosticsReport` | 137 | 混了两件事：通用排版 + `DeviceLine`（电量/声道/取数通道是 LinkPlay 专属） |
| `CrashGuard` | 99 | 硬编码 `filesDir/crash`、硬编码文件名，不可多实例 |
| `SafeLaunch` | 42 | 依赖 `androidx.lifecycle`，进不了纯 JVM 模块 |

三个具体后果：

1. **测试互相污染** —— `DiagnosticsHubTest` 每个用例都要 `@After { DiagnosticsHub.clear() }`。
   这不是测试写法问题，是单例的必然代价。
2. **不能统计** —— `List<String>` 没法回答"到底几类问题、几条是硬错误"。
   用户说"老是时好时坏"，我们手上 200 行文本得逐行读。
3. **不能复用** —— 想接到另一个项目，得把 `Context`、`LinkPlay` 的设备模型一起搬。

---

## 二、模块边界怎么划的

新建 `core-diagnostics`：**纯 JVM、零运行时依赖**（只有 kotlin-stdlib）、`maven-publish`，
和 `core-protocol` 同级同规格。

```
core-diagnostics/
├── Severity.kt            INFO / WARN / ERROR / FATAL，带短码与权重
├── DiagnosticEntry.kt     结构化条目：atMs + source + severity + message + detail
├── Clock.kt               时间来源（可注入，测试能固定）
├── Sink.kt                出口接口 + tee()
├── EntryLog.kt            环形日志（实例，容量可配）
├── Section.kt             一段状态（severity 是函数，见下）
├── SectionRegistry.kt     注册表（实例，单段失败隔离）
├── DiagnosticReport.kt    报告骨架（纯函数，文案可换语言）
├── Store.kt               持久化接口 + MemoryStore
└── FileStore.kt           java.io 落盘（Android 传 filesDir 即可）
```

**留在 app 里的只剩三样**，因为它们真的是这个项目特有的：

- `AppDiagnostics`（78 行）：组装根 —— 给零件选好 Android 的落盘目录
- `DiagnosticsReport.DeviceLine` + `channelText`：电量/声道/取数通道/能力档
- `SafeLaunch`：`viewModelScope` 是 Android 的概念，模块不该依赖它

### 为什么删掉 `IssueLog` / `DiagnosticsHub` 两个 object

引入模块后如果保留它们当壳，就会变成「模块里有一套、App 里还有一套」——
同一个能力两个名字、两处语义。这正是这个项目反复在治的病
（docs/41 的 7 条、docs/43 的「模型并存」、docs/45 的两套 slave 模型）。

所以统一成 `AppDiagnostics.log` + `AppDiagnostics.sections`，两个都是模块里的普通类。

---

## 三、「更广泛精准」具体做了什么

### 1. 分级（最有价值的一条）

条目从 `String` 变成带 `Severity`。报告头直接给汇总：

```
问题汇总：E1 W2 I5
...
[运行期问题 3 条（新→旧）｜E1 W2 I0]
  02:29:58 [E] [playQueueItem] IOException: HTTP 500 from 192.168.99.153: 401 invalid action
  02:29:57 [W] [probeSaved] 设备未应答，保留上次状态
  02:29:56 [I] [getStatusEx] 完成 69ms
```

以前要读 200 行才知道"是硬错误还是噪声"，现在扫一眼报告头。

### 2. 段自己会喊

`Section.severity` 是**函数**不是值 —— 注册只发生一次，而"设备全没应答"是运行中才出现的状态。

| 段 | WARN 条件 |
|---|---|
| 设备 | 全部未探测（= 探活没跑起来，PR #58 那个 NPE 就是这个形态）；或在线但一条取数通道都没答 |
| 队列 | `controlLost` 或 `error != null` |
| 后台服务 | `QueueBus.running` 但服务没在跑 |
| 播放 | `unavailableReason != null` 或 `notice != null` |

### 3. 时间戳存 epoch 毫秒

以前存 `"00:09:12 ..."` 字符串：跨天看不出、不能排序、不能换格式。
现在存结构，渲染交给报告 —— 同一个对象可以渲染成报告一行、界面一个可展开条目、落盘一行。

### 4. `timed()` —— 成功也记，带耗时

```kotlin
log.timed("getStatusEx") { client.send(ip, cmd) }
```

排障时最常问的是"这个动作到底跑了没有"。只有失败才记的话，
**"没反应"和"根本没触发"分不开** —— 这两种的修法完全不同。耗时还能区分"慢"和"断"。

### 5. 运行时长

报告头多一行 `运行时长：2h13m`，用于对上用户那句"用了两个多小时才出问题"。

---

## 四、顺手修掉的 bug：诊断标签错乱

上一轮把 59 处 `safeLaunch` 的来源从类名改成函数名，用的是一次性脚本替换，
**替换错了 6 处**：

| 位置 | 错的标签 | 实际是什么 |
|---|---|---|
| `ConsoleViewModel:1261` | `acquireMulticastLock` | 组音量心跳（2.5 秒） |
| `ConsoleViewModel:1275` | `acquireMulticastLock` | 卡片心跳（3 秒） |
| `ConsoleViewModel:1298` | `acquireMulticastLock` | 定时关闭时钟（1 秒） |
| `ConsoleViewModel:1319` | `acquireMulticastLock` | 音量下发队列 |
| `ConsoleViewModel:1034/1045` | `log` | 控制器协程 / 刷卡片 |
| `ConsoleViewModel:1005` | `run` | 带 busy 闸门的动作（**有 label 参数却没用**） |
| `PlaybackViewModel:553/679` | `httpAct` / `act` | 同样有 label 参数却没用 |
| `LibraryViewModel:90` | `LibraryViewModel` | `detectCapabilities` |
| `QueueViewModel:69` | `QueueViewModel` | `initDevice` |

后果：诊断报告里看起来像"锁的获取炸了"，实际是卡片心跳。
**这类错人眼扫代码发现不了**，只有在看报告时才会困惑 —— 而看报告的时候正是最需要准确信息的时候。

### 修法：不只是改对，是让它不能再错

1. 6 处标签改成动作名；`run(label)` / `httpAct(label)` / `act(label)` 直接用调用方给的 `label`
2. `ConsoleHost.launch(block)` → **`launch(source, block)`**（必填参数，4 个调用点全给了标签：
   `volumeKey` / `setChannel` / `setGroupChannel` / `applyGrouping`）
3. 新增 CI 检查 `scripts/check-diagnostics-labels.py`：禁止类名式标签与占位词

```
$ python3 scripts/check-diagnostics-labels.py
诊断标签检查通过（61 处）。
```

**验证脚本不是假通过**：把 `QueueViewModel` 那处临时改回类名，脚本报

```
app/src/main/kotlin/.../QueueViewModel.kt:68: 标签 'QueueViewModel' 是类名，不是动作名
exit=1
```

改回后重新通过。

---

## 五、验证

| 项 | 结果 |
|---|---|
| `:core-protocol:test` | **572 用例 0 失败**（一个没改） |
| `:core-diagnostics:test` | **41 用例 0 失败**（新增） |
| `:app:testDebugUnitTest` | **47 用例 0 失败**（原 51，删掉 7 个 `DiagnosticsHubTest` 用例 + 新增 3 个装配用例） |
| 合计 | **660 用例 0 失败** |
| `:app:assembleDebug` | 成功 |
| `:core-diagnostics:publish` | 成功 → `build/repo/.../diagnostics-0.1.0.jar` |
| jar 依赖 | 只有 `kotlin-stdlib`，**无 Android** |
| `check-init-order.py` | 通过 |
| `check-diagnostics-labels.py` | 通过（61 处） |

### 为什么删掉 `DiagnosticsHubTest` 的 7 个用例

它测的语义（顺序 / 覆盖 / 注销 / 失败隔离 / 空段）**全部**在 `SectionRegistryTest` 里有了，
而且模块里的版本还多测了「两个注册表互不干扰」—— 那正是单例做不到的事。
留着就是同一份语义两处断言。

app 侧只留 `AppDiagnosticsTest` 3 个装配用例：段渲染失败落到 `log`、重注册保位置、注销生效。

**未验证**：真机上看这份新报告长什么样。`.153` 离线，`.164` 只做只读核对。

---

## 六、这个模块现在能怎么用

```kotlin
implementation(project(":core-diagnostics"))   // 或 maven 坐标
```

```kotlin
val log = EntryLog(capacity = 200)
val sections = SectionRegistry(sink = log)
val report = DiagnosticReport.build(header, crashText, sections.render(), log.recent())
```

换项目、换语言（`DiagnosticReport.Labels`）都不用改库代码。
详细用法见 `core-diagnostics/README.md`。

---

## 七、还剩什么

1. **界面还没用上分级** —— 报告文本有 `[E]/[W]` 了，但诊断页还是整段等宽文本，
   没做按级别过滤、没做错误高亮。下一轮做。
2. **`timed()` 只接了几个点** —— 基础设施有了，真正把 `getStatusEx` /
   `GetControlDeviceInfo` 的耗时接进去还没做。
3. **`CrashGuard` 还没用上 `FileStore`** —— 它自己管目录和滚动，逻辑重复。
4. 继续拆 `DeviceRegistry`（docs/43 的顺序）。
