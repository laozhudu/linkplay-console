# 51 · P0 语义指令层 + 能力位驱动（RemoteView 延后，附理由）

> 前提：不做蓝牙、不做红外，先把音箱做完（用户 2026-11-04 决定）
> 依据：`docs/49` 规划 + `docs/50` 大厂对照，按后者**砍掉一半**后执行

---

## 一、做了什么

| # | 内容 | 结果 |
|---|---|---|
| 1 | `remote/` 包：`RemoteCommand` / `DispatchResult` / `RemoteTransport` / `LinkPlayTransport` | 5 个文件，语义指令层落地 |
| 2 | `DeviceControl` 改走语义指令 | 6 处 `host.send(ip, 拼字符串)` 消失 |
| 3 | **能力位真正被消费** | `DeviceCapabilities` 从"定义了没人用"变成界面唯一判据 |
| ~~4~~ | ~~`RemoteView`~~ | **没做**，理由见第四节 |

单测：**685 用例 0 失败**（660 → +25：17 个翻译表 + 8 个能力位）。

---

## 二、核心发现：能力表写了，但被 `_` 丢掉了

这是本轮最有价值的发现，也是"为什么界面只能按设备类型判断"的根因。

`DeviceCapabilities` 定义了 8 项能力位（`supportsPlayQueue` / `supportsMultiroom` /
`supportsPresets` / `supportsHttpapi` / `supportsVolume` / `supportsSeek` /
`supportsNextPrev` / `supportsMute`），注释写得很清楚"界面据此决定显示哪些入口"。

**但 app 层 0 处消费。**

根源在这一行（`ConsoleViewModel.profileOfSaved`）：

```kotlin
val (profile, _) = detect(desc, httpapiReachable = false)
//              ↑ 能力表被丢弃
if (profile != DeviceProfile.UNKNOWN) return profile
```

`detect()` 返回 `Pair<DeviceProfile, DeviceCapabilities>`，能力表算出来了，
然后被 `_` 扔掉。于是 `DeviceCard` 只有 `profile`，界面只能写：

```kotlin
if (profile != DeviceProfile.GENERIC_DMR) { 屏蔽/解除按钮 }
if (profile == DeviceProfile.GENERIC_DMR) { 不渲染声道 }
```

**按设备类型判断**——加第三类设备时每个判断点都要加分支，而且必然有地方漏。

这和 `docs/43` 记的 `MediaProvider`（接口写了、app 层 0 处消费）是**完全同一个病**：
抽象写了，装配没做。

### 修法

```
detect() → Pair(profile, capabilities)
    ↓ 两个都要
putCardProfile(uuid, profile, capabilities)
    ↓
DeviceCard.profile      ← 只用于显示"通用 DLNA"这类类型标签
DeviceCard.capabilities ← 决定渲染哪些按钮（唯一判据）
DeviceProfileCache      ← 一起存，起播路径问的是能力不是类型
```

界面三处判断的**等价性**（改完行为不变，逐条核过）：

| 原判据 | 新判据 | LINKPLAY | GENERIC_DMR | 未探测 |
|---|---|---|---|---|
| `profile != GENERIC_DMR` | `capabilities.supportsMultiroom` | true→true | false→false | 默认 LINKPLAY→true ✓ |
| `profile == GENERIC_DMR`（跳过声道） | `!capabilities.supportsChannel` | false→false | true→true | 默认→false ✓ |
| `of(ip) != GENERIC_DMR` | `capabilitiesOf(ip).supportsPlayQueue` | true→true | false→false | 默认→true ✓ |

新增 `supportsChannel` 能力位：LinkPlay 有（`RenderingControl.SetChannel`，实测不依赖组网），
通用渲染器没有（实测 AirReceiver 不认）。

---

## 三、语义指令层

### 边界：业务能力 vs 运维动作

`RemoteCommand` 只放**业务能力**（播放/音量/输入源/预设）。
改名 / 固件 / 恢复出厂 / 逃生口 `sendCommand` **继续走 `LinkPlayCommands`**。

这不是偷懒，是 Matter 的分法：

| | Matter | 我们 |
|---|---|---|
| 每个协议都有等价物 | Application Cluster | `RemoteCommand`（通用层） |
| 厂商私有 | Utility Cluster（Endpoint 0） | `LinkPlayCommands`（留方言） |

判据很实际：**业务能力每个协议都有等价物**（谁都能调音量）；
运维动作是厂商私有的（DLNA 没有"改名"这个概念）。把运维动作塞进通用层，
等于给通用层加一条"只有一种设备支持"的分支——那层就不通用了。

改完后 `DeviceControl` 里剩下的 `LinkPlayCommands` 调用，**全是运维动作**：

```
79   deviceName(name)              改名
316  firmwareUpdateStart()         固件
338  setMvRemoteUpdateStart        固件
356  restoreToDefault()            恢复出厂
69   host.send(ip, command)        逃生口（设置页手输命令）
```

### 结果三态，但现在只有两个分支

```kotlin
sealed interface DispatchResult {
    data class Acked(val raw: String) : DispatchResult
    data class Failed(val failure: DispatchFailure) : DispatchResult
}
```

蓝牙/红外会引入第三种（`Sent`：单向发出无从得知）。**现在不定义它**——
没有任何代码能产生它，单测只能测"它存在"，测不了语义。
加的时候是加一个 case，所有 `when` 编译报错提醒补分支。**留门 ≠ 现在建好房间。**

### 失败必须翻译成人话

```kotlin
fun DispatchFailure.describe(): String = when (this) {
    Unreachable -> "设备没有响应（可能休眠或不在同一网络）"
    Unsupported -> "这台设备不支持该操作"
    is Rejected -> when (code) {
        704 -> "设备里没有可播放的内容，先去「我的音乐」点一首"
        401 -> "这台设备的固件不支持该操作"
        ...
    }
    is Unparsable -> "设备回包无法识别，可能是固件版本差异"
}
```

**为什么在协议层翻译**：`704` 对 LinkPlay 是"没有媒体"，对 DLNA 是
`Transition not available`——错误码的含义是协议知识，界面不该知道（docs/40 同理）。

### 一个容易忽略的行为变化

改造前 `switchMode` 失败会走 `host.run` 的 catch → 界面显示红字。
改造后 `dispatch` **不抛异常**（设备睡了是正常业务，不该记成 ERROR），
所以如果不主动翻译，**用户点「光纤」会什么都没发生**——正是铁律禁止的
"按了没反应的按钮"。

所以 `DeviceControl.dispatch()` 里主动把 `Failed` 转成 `IOException` 抛出去，
复用 `host.run` 的错误显示通道：

```kotlin
private suspend fun dispatch(ip: String, command: RemoteCommand) {
    val result = transport.dispatch(ip, command)
    if (result is DispatchResult.Failed) throw IOException(result.failure.describe())
}
```

---

## 四、为什么 `RemoteView` 没做

P0 原定三件，第三件是 `RemoteView`（UI 吃协议无关的视图模型）。**没做，这是有意的。**

### 理由

**1. 只有一类设备时，"协议无关视图模型"必然是假的**

`RemoteView` 的设计是：

```kotlin
data class RemoteView(
    val name: String, val statusLabel: String,
    val capabilities: Set<Capability>,
    val nowPlaying: NowPlaying?,
    val extras: Map<String, String>,   // 电量=100% / 输入源=HDMI1
)
```

但声道那一格是**可点击的按钮**（点一下循环切换），不是纯文本——塞不进 `extras`。
所以现在做，`RemoteView` 里还是得放 `channel: Int?`。
**那就是音箱视图模型套了个通用名字**，抽象是假的。

`extras` 在只有音箱的时候等于"把电量塞进 Map 再取出来"，纯粹的增加间接层。

**2. 收益已经拿到了**

`RemoteView` 解决的是"不同设备的字段差异"（电量 vs 输入源）。
而"加新设备界面不改"这个更重要的收益，**能力位驱动已经拿到了**：
加一台电视盒子，按钮自动隐藏，界面一行不用改。

**3. 39 处 UI 改动无法单测**

界面直接读音箱字段 39 处、涉及 8 个文件。这些是 Compose 代码，纯 JVM 单测覆盖不了。
在没有真机验证的情况下一次改 39 处，风险远大于收益。

### 替代方案（已做）

把判据收敛到 `capabilities`，界面逻辑变成：

```kotlin
if (capabilities.supportsMultiroom) { 屏蔽/解除 }
if (!capabilities.supportsChannel) { 不渲染声道 }
```

以后加设备只需要**填对能力表**，判断点一处不用改。这比"换一个视图模型"更本质——
因为视图模型是数据的形状，能力位是**判断的依据**。

### 什么时候该做 `RemoteView`

**等真有第二类设备需要不同字段时**（电视的输入源、空调的温度）。
那时 `extras` 才有真实的消费者，抽象才有对照物。

现在做，是在为想象的需求建房间。

---

## 五、验证

| 项 | 结果 |
|---|---|
| `:core-protocol:test` | 597 用例 0 失败（+25） |
| `:core-diagnostics:test` | 41 用例 0 失败 |
| `:app:testDebugUnitTest` | 47 用例 0 失败 |
| 合计 | **685 用例 0 失败** |
| `:app:assembleDebug` | 成功 |
| `check-init-order.py` | 通过 |
| `check-diagnostics-labels.py` | 通过（61 处） |
| 界面行为 | 三处判据等价性逐条核过（见第二节表格） |

### 新增单测

`LinkPlayTransportTest`（17 个）—— 翻译表逐字锁死：

```
setPlayerCmd:play / pause / stop / next / prev
setPlayerCmd:seek:125        （冒号不是等号，错了设备 500）
setPlayerCmd:vol:0..100      （越界钳制不抛）
setPlayerCmd:mute:1 / 0
setPlayerCmd:switchmode:wifi
MCUKeyShortClick:1..6        （uiIndex 0 基 → 设备 1 基，差一只在这里转）
```

回包判读的四个坑：

- `704` 裸数字是**拒绝**不是成功（HTTP 200 + 裸码是 LinkPlay 的错误表达方式）
- `Failed: OK timeout` 不能因为含 `OK` 就判成功（先认失败词，再认 OK）
- 空 body 算成功（部分固件对 pause/stop 就这样回）
- 看不懂的回包**绝不猜成功**（猜成功是这类项目最贵的错）

`DeviceCapabilitiesTest`（8 个）—— 能力位逐项锁死：

- LINKPLAY 九项全 true
- 通用渲染器没有 PlayQueue / 多房间 / 预设 / httpapi / 声道
- 有 RenderingControl 才有音量静音；没有就只有 Seek
- 通用渲染器不能上/下首（设备没队列，`AVTransport.Next` 回 701）
- `detect()` 用**生产路径**（`DeviceDescription.parse`）构造，不手搓 data class

**未验证**：真机上点「光纤」失败时是否显示红字、音量键、预设键的实际效果。
`.153` 离线，`.164` 只做只读核对。

---

## 六、还剩什么

1. **`RemoteView`** —— 等第二类设备需要不同字段时再做
2. **`MainActivity` 拆分** —— 1400 行里混了 UI + 事件处理 + 状态组装。
   这才是"UI 可改"的真前提，比 `RemoteView` 更优先
3. **`DeviceRegistry` 拆分** —— docs/43 排的，但 `RemoteTarget` 定了再拆
4. **诊断页用上分级** —— 报告文本有 `[E]/[W]`，界面还是整段等宽文本
5. **`timed()` 接进实际路径** —— `getStatusEx` / `GetControlDeviceInfo` 的耗时
