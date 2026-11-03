# 40 · 闪退溯源：先让崩溃自己交代，再从结构上消除

用户报「最新这一版出现闪退，表示空指针错误」，并明确要求**不要打补丁，要溯源**。

问题在于：MIUI 那个弹窗只给一句模板文案（"因自身空指针问题，造成闪退"），
**没有异常类、没有行号、没有时间**。我手上没有堆栈，任何"改一版试试"都是猜 ——
而这个项目已经因为"猜"吃过一次大亏（协议层：命令回 OK 就当成功）。

所以这一轮做两件事，顺序不能反：

1. **先把证据链建起来**（否则"溯源"无从谈起）；
2. **再从结构上消灭"闪退"这一类故障**（不是逐点 try/catch）。

---

## 一、为什么 NPE 会变成闪退（根因类型）

`viewModelScope` 的 Job 是 `SupervisorJob`，但它只隔离**子协程之间**。
一个 `launch {}` 内部抛出的异常如果没人接，会走：

```
协程内未捕获异常 → CoroutineExceptionHandler（未设置） → 默认处理器
                → Thread.uncaughtExceptionHandler → 进程崩溃
```

**所以：任何一次网络异常、任何一次解析异常，都等于一次闪退。**

而本项目后台有 4 个常驻轮询（3 秒卡片心跳、2.5 秒组音量、1 秒定时器、启动探活），
加上每次点击触发的协程，**一共 69 个 `launch` 点**。
其中任何一个漏了 try/catch，用户看到的就是"用着用着突然闪退"。

这就是为什么这类问题反复出现：**每加一个功能就多一个漏点**。

### 修法：把边界收在一处

新增 `diagnostics/SafeLaunch.kt`：

```kotlin
fun ViewModel.safeLaunch(source: String, block: suspend CoroutineScope.() -> Unit): Job =
    viewModelScope.launch {
        try { block() }
        catch (cancelled: CancellationException) { throw cancelled }   // 取消是控制流，必须透传
        catch (error: Throwable) { IssueLog.record(source, error) }    // 记下来，不崩
    }
```

**69 处 `viewModelScope.launch` 全部改为 `safeLaunch("来源")`**，
`return@launch` 同步改为 `return@safeLaunch`。

这不只是"不崩了"：异常进 `IssueLog` 之后，**"功能没反应"也有了记录**——
以前一个 `runCatching { }.getOrNull()` 吞掉的失败，现在能在诊断页看到。

### 顺带修掉一个确定会崩的写法

```kotlin
// 设备报"有列表"但曲目一条都没解析出来时，items 为空
val idx = deviceList.currentIndex.coerceIn(0, items.lastIndex)   // coerceIn(0, -1) → IllegalArgumentException
```

`coerceIn(min, max)` 在 `min > max` 时**抛异常**，不是返回边界值。这类"范围反了"的写法
是真正会闪退的，已加空列表守卫。

---

## 二、证据链：崩溃记录 + 运行期记录 + 一页诊断

### 1. `crash/CrashGuard`

`Thread.setDefaultUncaughtExceptionHandler`，崩溃时落盘：
时间 / 线程 / 异常类 / 消息 / **应用状态摘要** / 完整堆栈。
**只记录，不吞异常** —— 闪退行为不变（吞掉会让 App 处于半死状态，比闪退更难查）。

滚动保留 5 份，最新一份是 `crash-latest.txt`。

### 2. `diagnostics/IssueLog`

内存环形缓冲 200 条 + 落盘。记录被 `safeLaunch` 接住的协程异常。
这样"没崩但也没成功"的事第一次有了痕迹。

### 3. `diagnostics/DiagnosticsReport`（纯函数，8 单测）

把状态渲染成**定宽纯文本**，回答四个问题：

```
=== LinkPlay Console 诊断报告 ===
版本：0.9.52（52）
时间：2026-11-04 00:10:48

[崩溃记录]
  异常：java.lang.NullPointerException
  消息：Attempt to invoke virtual method on a null object reference
    at io.github.laozhudu.linkplay.console.ConsoleViewModel.applySnapshot(ConsoleViewModel.kt:620)
    ...

[设备 3 台]
  doss1  192.168.99.164
    状态=在线  能力=LINKPLAY
    音量=34  声道=仅右  电量=100%（充电）  播放=否
    取数通道=CONTROL_INFO+PLAYER_STATUS
  doss3  192.168.99.153
    状态=在线  属=doss1  能力=LINKPLAY
    音量=39  声道=仅左  电量=50%  播放=是
    取数通道=PLAYER_STATUS
  E900V22C-175[DLNA]  192.168.99.175
    状态=在线  能力=GENERIC_DMR
    音量=39  声道=未报  电量=无  播放=否
    取数通道=全部未应答

[运行期问题 2 条（新→旧）]
  00:09:12 [ConsoleViewModel] IOException: HTTP 500 from 192.168.99.153: 401 invalid action
```

几个刻意的设计：

- **堆栈只留前 16 行**：再长截图截不全，而前几行就是真凶。
- **缺失字段显式写出来**（`电量=无`、`声道=未报`），不静默省略 ——
  用户报的"电量看不到"在报告里必须能一眼确认。
- **`取数通道`** 是排障关键：如果某台设备从没出现 `CONTROL_INFO`，
  说明它的 UPnP 控制地址读不到，那么声道/电量这类只能走 UPnP 的功能**必然无效**。
  这比让用户描述现象准得多。
- **不用富文本卡片**：截图时排版越简单越不会漏信息。

### 4. `diagnostics/DiagnosticsScreen`

- 顶部三个按钮：**复制全部** / 刷新 / 清空记录
- 主体是整段等宽文本（11sp，可滚动）
- 等宽字体让堆栈和通道名对齐，截图更易读

---

## 三、三个入口（覆盖三种习惯）

| 入口 | 位置 | 场景 |
|---|---|---|
| 顶栏 `◔` | 首页右上角，常驻 | 任何"不对劲"当场截图 |
| 崩溃横幅 | 首页顶部，红底，只有崩过才出现 | 闪退重开后第一眼看到，显示异常类 + 消息摘要 |
| 设置页卡片 | 设备设置第一张卡 | "出问题会去设置里找"的习惯 |

崩过之后顶栏图标从 `◔` 变成 `⚠` 并染成警示色 —— 不看横幅也知道有事。

---

## 四、验证

| 项 | 结果 |
|---|---|
| 单测 | **613 用例 0 失败**（+10：DiagnosticsReportTest 8 / 其他） |
| 报告渲染 | 已实跑打印，见上（`sample report is readable` 用例输出） |
| 编译 | `:app:assembleDebug` BUILD SUCCESSFUL |
| 未验证 | 真机崩溃是否被接住（需要真的崩一次）；`safeLaunch` 覆盖后是否有行为回退 |

---

## 五、下一版给用户的操作说明

1. 装上这版，正常用；
2. 如果闪退：**重开 App**，首页顶部会出现红条「上次运行闪退了」；
3. 点它进诊断页，或点右上角 `⚠`；
4. 点「复制全部」把文本发我 —— 里面有异常类、行号、当时的设备状态。

拿到那段文本之后，才是真正的"溯源"：**按行号改，不再猜**。
