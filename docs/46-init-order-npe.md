# 46 · 诊断报告定位到的 NPE：init 与字段的初始化竞态

诊断报告里的这条：

```
09:45:34 [ConsoleViewModel] NullPointerException:
  Attempt to invoke interface method 'java.lang.Object kotlin.Lazy.getValue()' on a null object reference
```

配合同一份报告里的 **三台设备全部「状态=未探测」**，根因只有一种解释。

---

## 一、根因

`ConsoleViewModel` 里：

```
252 行   init {  ... probeSaved() ... }        ← init 在前
572 行   private val snapshots ... by lazy {}  ← 字段在后
```

Kotlin **按声明顺序**初始化字段与执行 `init` 块。于是实际执行顺序是：

```
主线程：init 开始
  └─ probeSaved() → viewModelScope.launch { ... }
        └─ withContext(Dispatchers.IO) { snapshots.read(ip) }
              └─ 派发到 IO 线程池 —— 立刻可能在另一个线程开始跑
主线程：init 继续往下（还没到 572 行）
IO 线程：访问 snapshots → snapshots$delegate 还是 null
        → Lazy.getValue() on null → NPE
```

**这是一个真实的初始化竞态，不是理论风险。** 两个证据互相印证：

1. `Lazy.getValue() on null` —— delegate 字段本身为 null，只能是"字段初始化语句还没执行"。
2. 三台设备全部「状态=未探测」—— `online` map 是空的，说明 `probeSaved()` 的协程
   **整个崩在第一个设备之前**，一条都没写进去。而卡片上的电量/声道有值，
   那是 3 秒心跳写的（心跳有 `delay(3_000)` 先挂起，那时 init 早跑完了）。

`safeLaunch` 把异常接住了（所以 App 没闪退），这是上一轮那个改动的价值 ——
但它也让这个 bug 变成"静默失效"，只在诊断页留下一条记录。**诊断页这次直接定位到了行号。**

---

## 二、修法（三条，都是结构性的）

### 1. `init` 移到所有属性之后

`ConsoleViewModel` / `PlaybackViewModel` / `NasViewModel` 三个文件的 `init` 块全部移到类末尾，
并在原位留注释说明为什么必须这样。

### 2. `snapshots` 去掉 `by lazy`

```kotlin
// 改前
private val snapshots: DeviceSnapshotReader by lazy { DeviceSnapshotReader(...) }

// 改后
private val snapshots: DeviceSnapshotReader = DeviceSnapshotReader(...)
```

`DeviceSnapshotReader` 的构造只存字段（无副作用），不需要延迟。
去掉 `lazy` 就**消灭了 `Lazy.getValue() on null` 这一整类故障** —— 不只是这一处。

### 3. 加 CI 静态检查 `scripts/check-init-order.py`

编译器不会报这个错，单测也测不到（要真跑 Android ViewModel）。所以用静态检查兜住：

```
判据：4 空格缩进的 init { 行号，必须大于同类里所有 val/var 声明的行号
```

**这个检查一上线就抓出另外两处同类隐患**：

| 文件 | init 行 | 它后面的属性 | 会不会真炸 |
|---|---|---|---|
| `ConsoleViewModel.kt` | 252 | `snapshots`（572，by lazy） | **已炸**（本次） |
| `PlaybackViewModel.kt` | 167 | `volumeChannel`（595，`Channel`） | **会炸**：`prepare() → refreshOnce() → volumeChannel.trySend()`，未初始化时是 null |
| `NasViewModel.kt` | 107 | `contentDir`（127，`var … = null`） | 不会（null 是合法初值，且 init 不碰它） |

前两个是真隐患，都修了；第三个按规范一并移到位。

---

## 三、顺带：诊断来源从类名改成函数名

原来 59 处 `safeLaunch("ConsoleViewModel")` 全用类名，诊断报告里只能看到
`[ConsoleViewModel] NullPointerException` —— 不知道是哪条路崩的。

改成按所在函数命名：

```
[probeSaved] NullPointerException
[PlaybackViewModel] ...
```

现在 59 处都带具体来源（`probeSaved` / `scan` / `connect` / `pushQueue` / `prepare` …），
下次诊断报告能直接说"是探活崩了"还是"是起播崩了"。

---

## 四、验证

| 项 | 结果 |
|---|---|
| `scripts/check-init-order.py` | 修复前抓出 3 处，修复后 **通过** |
| 单测 | **623 用例 0 失败** |
| `compileDebugKotlin` / `assembleDebug` | BUILD SUCCESSFUL |
| CI | 已加 `Check init block ordering` 步骤（在单测之前跑） |

**未验证**：真机上"状态=未探测"是否变成"在线"。`.153` 离线，`.164` 需要装机确认。
但逻辑上：`probeSaved()` 不再崩 → `online` map 会有值 → 卡片不再显示"未探测"。

---

## 五、这条经验值得记住

**`init` 块里启动异步任务（协程/线程/回调）是危险模式**，因为：

- 后面声明的字段那时还是 null / 未初始化；
- 编译器不报错；
- 单测测不到（要真跑 Android 生命周期）；
- 如果外层有 `try/catch` 或 `safeLaunch`，会变成**静默失效**，比崩溃更难查。

**规则：`init` 永远放类末尾。** CI 已经在守这条了。
