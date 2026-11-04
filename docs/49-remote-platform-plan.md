# 49 · 从「音箱遥控」到「多协议遥控平台」的模块规划

> 状态：**待审核**（本轮只出方案，不动代码）
> 触发：诊断报告无异常 + 用户要求「拆模块要从长远角度考量」

---

## 零、先说报告

报告本身**没有故障**，但要读对两处：

**1. `运行时长：3s` 解释了「三台全部未探测」**

探活是逐个设备发 HTTP，3 秒时还在跑。而 `doss1` 已经有
`取数通道=CONTROL_INFO+PLAYER_STATUS`、电量 100%、音量 69 —— 那是卡片心跳（3 秒一轮）先写进去的。

这恰好证明「未探测 / 离线 / 在线」三态分开是对的。如果混成一个"离线"，
我现在会误判成「三台全挂了」，然后去查一个不存在的问题。

**2. 一个真问题：`doss2 192.168.99.181`**

```
状态=未探测  能力=UNKNOWN
音量=0  声道=未报  电量=无
取数通道=全部未应答
```

它在你设备列表里好几天了，身份始终没确认。它可能是：
- 第三台 DS-1831（但你之前说只有两台）
- `.153` 换了 IP 又改了名
- 别的品牌设备被误加进来

**这个不能一直悬着**。下一轮我用 SSDP 主动扫一次全网的 `MediaRenderer`，
把 MAC + `getStatusEx` 全量对一遍，能定位就定位，不能就明确标"不是音箱"。

---

## 一、平台约束（先说地基，不然规划是空中楼阁）

你提的三条路，**两条有硬约束**。我先查证了，不是凭印象：

### ✅ WiFi —— 三条都通

| 协议 | 现状 | 说明 |
|---|---|---|
| LinkPlay httpapi + UPnP | **已实现** | 现 8125 行 core-protocol 的主体 |
| 通用 DLNA / UPnP | **已实现** | `.175` 电视盒子只做过只读探测 |
| Android TV Remote v2 | 未做 | protobuf over TLS，6466/6467 端口，`tronikos/androidtvremote2` 有完整实现可参考 |

### ⚠️ 蓝牙 —— **不能发 AVRCP，只能走 BLE GATT**

这是最容易踩的坑。查证结论：

> Android 系统**没有公开 API** 让第三方 App 主动发 AVRCP 命令。
> 第三方只能作为 AVRCP **target**（被控），不能当 controller。

所以「蓝牙遥控」实际只有两条路：

| 路径 | 能控什么 | 代价 |
|---|---|---|
| **BLE HID over GATT** | Android TV / 部分投影 | 手机扮演 HID 设备（`BluetoothHidDevice`, API 28+），需实现 HID report descriptor |
| **厂商私有 GATT** | 小米/华为/极米等特定型号 | 每家一套 UUID + 命令，逆向工作量按品牌计 |

参考实现：`bertrandmartel/bluetooth-remote-control`（BLE HID 控 Android TV）、
F-Droid 的 `com.atharok.btremote`。

**这条约束直接影响架构**：蓝牙必须先"连接"才能发指令，且连接会断、要重连、要配对。
现在的 `SavedDevice(ip)` 装不下这个。

### ✅ 红外 —— 可用但看机型

`android.hardware.ConsumerIrManager`（API 19+，`TRANSMIT_IR` 是普通权限）：
- `hasIrEmitter()` 判断有没有发射器 —— **绝大多数手机没有**
- `getCarrierFrequencies()` 查支持的载波
- `transmit(freq, pattern)` 发原始时序

**红外没有回执**。发出去就是发出去了，不知道电视有没有收到。

### 三条路的本质差异

| | WiFi | 蓝牙 | 红外 |
|---|---|---|---|
| 寻址 | IP + 端口 | MAC | 品牌 + 码集 ID |
| 有状态吗 | 有（在线/离线） | 有（连接/断开） | **没有** |
| 有回执吗 | 有（SOAP/httpapi 应答） | 有（GATT write 应答） | **没有** |
| 要配对吗 | 不要 | 要 | 不要 |
| 能回读音量吗 | 能 | 部分能 | **不能** |
| 发现方式 | SSDP / 手动 | 扫描 | 无（选品牌型号） |

**这张表就是架构的全部约束**。任何抽象如果假设"设备都在线、命令都有回执、音量都能回读"，
接红外那天必须推倒重来。

---

## 二、现状体检：8125 行里哪些能用

我按"换个品牌还能不能用"分了四类：

### A 类 · 与协议完全无关（可直接进通用层）—— 约 2400 行

| 包 | 行数 | 是什么 |
|---|---|---|
| `media/` | 660 | 本地 HTTP 服务（Range/切片/token） |
| `metadata/` | 576 | ID3/FLAC/MP4 标签解析 |
| `library/` | 361 | 拼音搜索（GB2312 区位表）/ 曲目记录 |
| `mediasource/` | 270 | `MediaProvider` 接口 + Subsonic 客户端 |
| `radio/` | 197 | 电台模型 |
| `playlist/` | 116 | 歌单操作 |
| `voice/` | 254 | 语音意图路由 |
| `Json/JsonWriter/Hex` | 300 | 工具 |

**这部分一行都不用改**。它们是"遥控平台"的公共资产。

### B 类 · 通用 UPnP/DLNA（任何 DLNA 设备都能用）—— 约 1400 行

`upnp/` 的 `SoapEnvelope`、`DeviceDescription`、`ContentDirectoryClient`、
`UpnpRendererClient`、`SsdpMessages`。

**但混了 LinkPlay 专属的东西**：`PlayQueueClient`（`urn:schemas-wiimu-com`）、
`ControlDeviceInfo`（RenderingControl 的私有扩展）、`QueueContextXml`。

### C 类 · LinkPlay 方言（换品牌即废）—— 约 1900 行

| 文件 | 行数 |
|---|---|
| `LinkPlayCommands` | 220（24 条 httpapi） |
| `LinkPlayStatus` | 127（getStatusEx 解析） |
| `LinkPlayDevice` | 190 |
| `multiroom/` | 457（`ConnectMasterAp` / `SlaveChannel` / `SlaveMask`） |
| `playqueue/` | 335（原厂 PlayQueue） |
| `provisioning/` `Provisioning` `ApScanItem` `WpsGuide` | 约 350 |
| `qplay/` `identity/` `snapshot/` `FirmwareUpdate` | 约 700 |

### D 类 · **真正的债**（架构层面的阻碍，不只是"专属"）

这四条不是"以后再说"，是**现在不改，接蓝牙时一定要返工**：

#### 债 1 · `SavedDevice` 只有 `ip`

```kotlin
data class SavedDevice(
    val uuid: String, val name: String, val ip: String,   // ← 地址只有 IP
    val location: String?, val modelName: String?, val lastSeenMs: Long,
)
```

蓝牙设备没有 IP，红外设备连"设备"都没有（只有品牌+码集）。

#### 债 2 · `online: Map<String, Boolean>` —— 假设了"有状态"

```kotlin
val online: Map<String, Boolean> = emptyMap()   // uuid → 在线
```

红外设备永远是"不知道"。硬塞 `false` 就是撒谎，塞 `true` 也是撒谎。

#### 债 3 · `DeviceCard` 是「音箱卡片」，30 个字段全是音箱语义

```kotlin
data class DeviceCard(
    val volume: Int, val mute: Boolean, val channel: Int?,   // 声道：电视没有
    val battery: Int?, val charging: Boolean,                // 电量：电视没有
    val plicount: Int, val plicurr: Int,                     // 设备队列：DLNA 没有
    val profile: DeviceProfile, val answered: Set<...>,
)
```

未来电视要「输入源 / 电源 / 方向键」，空调要「温度 / 模式」。
现在这张卡片装不下，也没法在一个列表里混排。

#### 债 4 · `DeviceControl` 直接拼协议字符串

```kotlin
host.send(ip, LinkPlayCommands.playerVolume(next))   // ← 调用点知道方言
```

指令是**字符串**，不是语义。接蓝牙时会在每个调用点加 `if (kind == BT) ... else ...`。

**这四条是这次规划的真正目标**，其它都是顺带。

---

## 三、目标架构

### 三层，不是六层

```
┌─ 概念层 core-remote ────────────────────────────────────┐
│  TargetAddress  怎么寻址（IP / MAC / 品牌码集）          │
│  RemoteCommand  想做什么（语义，不是协议文本）            │
│  Transport      谁能送到（WiFi / BLE / IR）              │
│  DispatchResult 送到的结果（有回执 / 单向 / 失败）        │
│  Capability     这台能做什么（能力位，不是设备类型）      │
│  RemoteTarget   一个可遥控的目标（= 地址 + 能力 + 状态）  │
└──────────────────────────────────────────────────────────┘
              ▲ 只依赖 JDK，零 Android、零协议
              │
┌─ 传输层 transport-* ─────────────────────────────────────┐
│  transport-wifi   HTTP / UPnP / SSDP 发现                │
│  transport-ble    BLE GATT（HID 或厂商私有）              │
│  transport-ir     ConsumerIrManager                      │
└──────────────────────────────────────────────────────────┘
              ▲
┌─ 协议层 protocol-* ──────────────────────────────────────┐
│  protocol-linkplay  ← 现在的 core-protocol 主体           │
│  protocol-dlna      ← 从 core-protocol 剥离的通用 UPnP    │
│  protocol-androidtv Android TV Remote v2（未来）          │
│  protocol-ir-codes  红外码库（未来）                      │
└──────────────────────────────────────────────────────────┘
```

**为什么三层而不是"每个协议一个模块"**：
协议层可以有 N 个（每加一个品牌就一个），但概念层和传输层必须稳定。
把 `RemoteCommand` 放进 `protocol-linkplay`，接蓝牙时就得复制一份。

### 核心抽象（具体签名）

#### 1. 寻址 —— 三种，不是"IP 加个可选字段"

```kotlin
sealed interface TargetAddress {
    data class Net(val host: String, val port: Int = 0) : TargetAddress
    data class Bt(val mac: String, val kind: BtKind) : TargetAddress   // HID / 厂商 GATT
    /** 红外没有"设备"，只有"这台设备认得哪套码"。 */
    data class Ir(val brand: String, val codeSetId: String) : TargetAddress
}
```

#### 2. 指令 —— 语义，且**必须能表达电视需要的键**

```kotlin
sealed interface RemoteCommand {
    // 播放
    data object Play : RemoteCommand
    data object Pause : RemoteCommand
    data object Stop : RemoteCommand
    data object Next : RemoteCommand
    data object Previous : RemoteCommand
    // 音量
    data class SetVolume(val level: Int) : RemoteCommand
    data class VolumeStep(val delta: Int) : RemoteCommand
    data object Mute : RemoteCommand
    // 电源 / 输入（电视、投影）
    data class Power(val on: Boolean) : RemoteCommand
    data class SelectInput(val id: String) : RemoteCommand
    // 方向键 / 数字键（电视、机顶盒；红外遥控器的主要用法）
    data class Key(val code: RemoteKey) : RemoteCommand
    // 逃生口：红外码、厂商私有码。**只允许协议层产生，界面不许用**
    data class Raw(val payload: String) : RemoteCommand
}

enum class RemoteKey { Up, Down, Left, Right, Ok, Back, Home, Menu, VolumeUp, VolumeDown, Num0 /* … */ }
```

`Raw` 存在是必要的（红外就是裸码），但要靠**模块可见性**限制它：
`internal` 给协议层，界面层拿不到 —— 否则半年后界面又开始拼字符串。

#### 3. 结果 —— 三态，不能是 Boolean

```kotlin
sealed interface DispatchResult {
    /** 对端确认了（SOAP 200 / GATT write ack）。**仍不等于功能生效**，见 docs/43。 */
    data class Acked(val detail: String? = null) : DispatchResult
    /** 单向发出，无从得知结果（红外、部分 BLE notify）。 */
    data object Sent : DispatchResult
    data class Failed(val failure: DispatchFailure) : DispatchResult
}

sealed interface DispatchFailure {
    data object Unreachable : DispatchFailure      // 连不上
    data object NotPaired : DispatchFailure        // 没配对（蓝牙）
    data object Unsupported : DispatchFailure      // 这台设备不支持该指令
    data object NoEmitter : DispatchFailure        // 手机没有红外发射器
    data class Rejected(val code: Int, val text: String) : DispatchFailure  // 对端拒绝（704 / 500）
}
```

**为什么这个必须现在定**：项目反复吃过「命令回 OK ≠ 功能生效」的亏（`chext=0` vs `1`、
`resume` 回 OK 但 status 仍 stop、https 电台回 OK 但没播）。
如果 `dispatch` 返回 `Boolean`，那三种完全不同的失败会压成一个 `false`，界面只能说"失败了"。
三态之后界面能说准话：

- `Acked` + 回读不符 → 「设备说收到了，但状态没变」（这才是真 bug）
- `Sent` → 「已发送（红外无回执）」（不假装成功也不假装失败）
- `Failed(NoEmitter)` → 「这台手机没有红外发射器」（**直接不渲染红外入口**）

#### 4. 传输 —— `observe` 可空，因为红外没有状态

```kotlin
interface Transport {
    val kind: TransportKind
    /** 这台传输**能**做什么。界面据此隐藏入口，而不是 try 一下看报什么错。 */
    fun capabilities(): Set<TransportCapability>
    suspend fun dispatch(target: RemoteTarget, command: RemoteCommand): DispatchResult
    /**
     * 状态流。**红外返回 null** —— 没有状态可观察，不是"状态未知"。
     * 返回 null 而不是发一个"unknown"，是为了让调用方在类型层面就必须处理"没有状态"这件事。
     */
    fun observe(target: RemoteTarget): Flow<TargetState>?
}

enum class TransportCapability {
    CAN_READ_BACK,      // 能回读真值（WiFi 能，红外不能）
    HAS_STATE,          // 有在线/离线概念（红外没有）
    NEEDS_PAIRING,      // 要配对（蓝牙要）
    NEEDS_CONNECTION,   // 要先建立连接（蓝牙要，WiFi 不要）
    SUPPORTS_DISCOVERY, // 能自动发现（SSDP / BLE 扫描；红外不能）
}
```

#### 5. 能力位 —— 描述"能做什么"，不是"是什么"

现在 `DeviceProfile` 是 `LINKPLAY / GENERIC_DMR / UNKNOWN` 三档，
未来会有"小米电视（BLE）"、"格力空调（红外）"、"投影（DLNA）"。
用类型判断会爆炸，用能力位不会：

```kotlin
enum class Capability {
    PLAYBACK, VOLUME, MUTE, POWER, INPUT_SELECT,
    DIRECTIONAL_KEYS, NUMERIC_KEYS, PRESET_KEYS,
    MULTIROOM, BATTERY, CHANNEL, QUEUE, NOW_PLAYING,
}
```

界面渲染规则从「是不是 LinkPlay」变成「有没有这个能力位」——
这条其实**项目已经在做了**（`DeviceProfile` 那 8 项能力位），只是范围太窄。

---

## 四、分阶段路线（每阶段有可验证判据）

### P0 · 现在就做（本轮之后，约 2-3 小时）

**只在 `core-protocol` 里加 `remote/` 包 + 让 `DeviceControl` 走语义指令。不建新模块。**

| 做什么 | 为什么这样 |
|---|---|
| 加 `RemoteCommand` / `DispatchResult` / `Transport` / `Capability` 接口 | 概念先落地，但只有 LinkPlay 一个实现者 |
| `LinkPlayTransport` 包住现有 `DeviceHttpClient`，做「语义 → 方言」翻译 | 一个翻译表，220 行 `LinkPlayCommands` 原样保留 |
| `DeviceControl` 的 `send(ip, cmd)` 改成 `transport.dispatch(target, RemoteCommand.X)` | 调用点不再知道方言 |
| 新增约 20 个单测：每个 `RemoteCommand` 翻译成什么字符串、`Acked/Sent/Failed` 怎么判 | 锁死翻译表，这是唯一会漂移的地方 |

**判据**：660 单测一个不改仍全绿；界面零改动；`DeviceControl` 里不再出现 `LinkPlayCommands`。

**为什么现在做**：这一步**不改变任何行为**，但决定了后面三步能不能做。
如果不做，接蓝牙时每个调用点都要加分支。

### P1 · 拆模块（P0 稳定后）

```
core-remote        ← 概念层（零依赖，可 publish）
protocol-linkplay  ← 现在的 core-protocol 主体
protocol-dlna      ← 通用 UPnP 剥离出来
```

**判据（这条很重要）**：拆的时候如果发现 `core-remote` 被迫 `import` 任何 LinkPlay 类型，
**说明 P0 的抽象是错的，停下来改抽象，不要用 `@Suppress` 糊过去**。

这是唯一能验证抽象正确性的时机 —— 在接第二个协议之前。

### P2 · 接蓝牙（第一个真正的外部协议）

**这一步会暴露 P0 抽象的真问题，而且是好事。**

新增 `transport-ble`。已知会撞上的：

| 问题 | 现在装不下 |
|---|---|
| 要配对 + 连接状态机 | `SavedDevice` 只有 IP |
| 连接会断、要重连 | 没有"连接态"这个概念 |
| HID 要发 report descriptor | 指令不是"字符串"而是二进制帧 |

**判据**：`SavedDevice` 能被替换成 `RemoteTarget`（地址是三选一），且现有 LinkPlay 路径不受影响。

### P3 · 接红外

新增 `transport-ir` + `protocol-ir-codes`。

| 新问题 | 应对 |
|---|---|
| 没有回执 | `DispatchResult.Sent`（已在 P0 定义） |
| 没有状态 | `observe()` 返回 null（已在 P0 定义） |
| 手机可能没发射器 | `capabilities()` 不含 IR → 界面**根本不渲染**红外入口 |
| 码库巨大（每品牌一套） | 只做你实际有的设备，不追求通用码库 |

**判据**：红外设备在首页和设备列表里不显示"在线/离线"，显示"红外"标签。

### P4 · 本地播放

新增 `core-player`（Media3）+ `PlaybackRoute` 抽象。

这里会出现一个**新的语义分叉**，值得单独说：

```
现在：播放 = 把 URL 投给音箱（PlayQueue）
未来：播放 = 本地放（Media3） 或  投给设备
```

所以 `PlaybackRoute` 要能表达：
```kotlin
sealed interface PlaybackRoute {
    data object Local : PlaybackRoute                        // 手机本地放
    data class Remote(val target: RemoteTarget) : PlaybackRoute  // 投给设备
}
```

**判据**：`PlaybackEngine` 不关心"本地还是远程"，只关心"能不能播"。

---

## 五、现在**不**做什么（同样重要）

| 不做 | 为什么 |
|---|---|
| **不在接第二个协议之前建 6 个模块** | 唯一的实现者会悄悄污染抽象。等第二个实现者出现，抽象才有对照 |
| 不重写 `DeviceCard` | 40 个字段全被界面依赖，一次改完必然引入 10 个 bug。P2 时按需拆 |
| 不做通用红外码库 | 几千个品牌几十万条码，只用得上自家那几台。做"添加设备时录码"更实际 |
| 不做通用 AVRCP | 系统不给 API，绕不过去 |
| 不把 `core-protocol` 改名 | 8125 行、660 单测、30 个 PR 的历史。等 P1 真正拆的时候再分 |

---

## 六、三个需要你决策的点

### 决策 1 · 蓝牙优先做哪条？

| 选项 | 能控 | 工作量 |
|---|---|---|
| **A. BLE HID（手机当遥控器）** | Android TV / 部分投影 | 中（HID descriptor + GATT） |
| **B. 厂商私有 GATT** | 小米/华为/极米特定型号 | 高（每品牌逆向） |
| C. 两条都做 | — | 很高 |

**我的建议：A**。理由：HID 是标准协议，一次实现对所有支持 BLE HID 的设备有效；
厂商私有协议每个都要重新逆向，而你手上的设备型号有限。

**但前提**：你家里有没有 Android TV / 支持 BLE HID 的设备？

### 决策 2 · 红外这条路你实际用得上吗？

`hasIrEmitter()` 在绝大多数手机上返回 false（小米/华为部分机型有）。

**先测一下再决定**：我可以加一个按钮，点了显示"这台手机有/没有红外发射器 + 支持的载波频率"。
有就排进 P3，没有就把红外从规划里划掉（省一大块工作）。

**要我现在加这个检测吗？**

### 决策 3 · 本地播放的定位

| 选项 | 含义 | 影响 |
|---|---|---|
| **A. 手机本地播放** | 手机自己出声（耳机/蓝牙耳机） | 加 Media3，与现有投流并列 |
| **B. 本地解码 + 投给设备** | 手机当转码服务器 | 已有 `RelayMediaServer`，主要是格式覆盖 |
| C. 两者都要 | — | 两条路都要 `PlaybackRoute` |

**我的建议：C，但 B 优先**。因为 B 已经在做了（`RelayMediaServer` 就是为了绕 https 限制），
A 是新增能力，且和"遥控器"定位有冲突 —— 但你说"甚至可能加入本地播放模块"，所以留着口子。

---

## 七、我的建议顺序

```
P0（现在）  语义指令层，不建模块     ← 唯一"不做会返工"的一步
   ↓
你回答决策 2（红外检测）→ 决定 P3 存不存在
   ↓
P1（拆 core-remote / protocol-linkplay / protocol-dlna）
   ↓
P2（蓝牙，按决策 1）
   ↓
P3/P4 按你的实际设备决定
```

**P0 是唯一"现在必须做"的**。其它都可以等你有具体设备再定 —— 因为抽象的对照物是**真实的第二个实现**，
不是想象出来的。

---

## 八、这轮不动的东西

- 现有 660 单测：一个不改
- 界面：零改动
- `ConsoleViewModel` 的拆分（docs/43 的 `DeviceRegistry`）：**先停**。
  因为 `DeviceRegistry` 要处理"设备记录 + 探活 + 快照"，而"设备"这个概念马上要从
  `SavedDevice(ip)` 变成 `RemoteTarget(三种地址)`。现在拆了，P2 还得再拆一次。

  → 这是本次规划对原计划的**唯一调整**，也是最重要的一条。


---

## 九、按用户的模块想法复核

用户的提法：

> 音箱控制是整套中的一个模块，其他遥控后续也可从其他成熟项目参考各做模块，
> UI 界面是一个模块（可改），播放器是一个模块。

**结论：方向可行，三处要调整。**

### 复核表

| 用户想法 | 判断 | 依据 |
|---|---|---|
| 音箱控制是其中一个模块 | ✅ 完全对 | 现有 `core-protocol` 主体就是它 |
| 其他遥控各做模块 | ⚠️ 方向对，**切分维度错** | 按品牌切会重复实现连接/配对/重连/超时 |
| UI 是一个模块（可改） | ⚠️ 对一半 | **是不是模块 ≠ 能不能换** |
| 播放器是一个模块 | ⚠️ 要拆成两个 | 混了「播放控制」与「播放引擎」 |

### 现状实测（UI 绑死音箱的证据）

```
界面直接读 ConsoleUiState 的文件：8 个
界面直接读音箱字段（cards/groupMembers/saved/slaves）：39 处
界面读 DeviceCard 音箱字段（battery/channel/plicount/plicurr/charging）：25 处
ui/ 目录（组件+主题）：1050 行
```

→ 现在「UI 是模块」只是换了层皮，里面还是只能显示音箱。

### 调整 1 · 按「怎么送到」切，不按品牌切

```
传输层（按通道，各 1 份）        协议层（按品牌，N 份）
transport-wifi        ────→      protocol-linkplay（已实现）
                                 protocol-dlna
                                 protocol-androidtv
transport-ble         ────→      protocol-mi-gatt
                                 protocol-hid
transport-ir          ────→      ircodes/*.json   ← 纯数据，不是代码
```

**品牌差异是数据，不是代码。** 红外尤其明显：红外遥控没有"协议"，
只有「这台电视的电源键是这段时序」。所以红外不该是 Kotlin 模块，
应该是一堆 JSON 码表 + 一个解释器。

判据：**加一个新红外设备 = 加 1 个 JSON，0 行代码。**

### 调整 2 · UI 能不能换，取决于它依赖什么

现在 UI 吃 `ConsoleUiState`（40+ 字段全是音箱语义）。换 10 个 UI 实现，
还是只能显示音箱。要真能换，UI 必须依赖协议无关的视图模型：

```kotlin
data class RemoteView(
    val id: String,
    val name: String,
    val transportLabel: String,          // "WiFi" / "蓝牙" / "红外"
    val statusLabel: String,             // "在线" / "已连接" / "红外（无回执）"
    val capabilities: Set<Capability>,   // 决定渲染哪些按钮
    val nowPlaying: NowPlaying?,
    val extras: Map<String, String>,     // 电量=100% / 声道=立体声 / 输入源=HDMI1
)
```

`extras` 是核心：音箱有电量声道，电视有输入源，空调有温度。
UI 用 key-value 渲染就能**混排**，不用为每个协议写一套卡片。

UI 也要分层，不能是一个模块：

```
ui-core      协议无关：设备列表 / 卡片 / 详情（只吃 RemoteView）
ui-speaker   音箱：播放页 + 组网面板（现在这套）
ui-tv        电视：方向键 + 输入源（未来）
```

音箱播放页和电视方向键长得完全不一样，塞一个模块里就会变成满屏 `if (kind == SPEAKER)`。

### 调整 3 · 「播放器」要拆成两个

| | 是什么 | 归属 |
|---|---|---|
| 播放**控制** | 发 play/pause/next 给设备 | 遥控命令（`RemoteCommand.Play`） |
| 播放**引擎** | 本地解码出声（Media3） | 独立模块 `core-player` |

混在一起的后果：要么本地播放依赖遥控命令定义，要么遥控被塞进 Media3。

**后者是致命的**：`core-protocol` 现在是纯 JVM、零依赖、660 单测跑得快。
塞进 Media3 就等于给协议层加 Android 依赖，660 个测试全得跑模拟器。

粘合靠 `PlaybackRoute`：

```kotlin
sealed interface PlaybackRoute {
    data object Local : PlaybackRoute                            // 手机本地放
    data class Remote(val target: RemoteTarget) : PlaybackRoute  // 投给设备
}
```

`PlaybackEngine` 只关心"能不能播"，不关心本地还是远程。

### 修正后的模块图

```
概念层   core-remote         RemoteTarget / RemoteCommand / Transport / DispatchResult
         core-player         本地播放（Media3）+ PlaybackRoute
         core-diagnostics    ✅ 已完成

传输层   transport-wifi      HTTP / SSDP / 超时重试          ← 按"怎么送到"切
         transport-ble       GATT / 配对 / 重连状态机
         transport-ir        ConsumerIrManager

协议层   protocol-linkplay   音箱（已实现）
         protocol-dlna       通用 UPnP
         protocol-androidtv  电视（未来）
         ircodes/*.json      红外码表，纯数据

界面层   ui-core            协议无关：列表/卡片/详情（吃 RemoteView）
         ui-speaker         音箱：播放页/组网面板
         ui-tv              电视：方向键/输入源（未来）
```

### 三个量化判据

模块化不模块化，不看"有几个模块"，看三个数：

| 场景 | 目标 |
|---|---|
| 加一个新红外设备 | 改 **1 个 JSON，0 行代码** |
| 加一个新品牌 WiFi 音箱 | 改 **1 个 protocol-* 模块**，不动 transport 和 ui-core |
| 换一套 UI | 只改 **ui-***，core-remote 一行不动 |

做不到这三条，模块划分就是假的 —— 只是把代码挪了目录。

### P0 调整为四步（新增 RemoteView）

| # | 做什么 |
|---|---|
| 1 | `core-remote` 概念（`RemoteCommand` / `DispatchResult` / `Capability`） |
| 2 | `LinkPlayTransport`（语义 → 方言翻译） |
| 3 | **`RemoteView`**（新增：UI 改吃它，不再吃 `ConsoleUiState`） |
| 4 | `DeviceControl` 改发语义指令 |

做完这四步，**界面已经和音箱解耦**：以后加电视，`ui-core` 的列表一行不用改，
只是多一个 `ui-tv` 详情页。这是最能提前拿到收益的一步。

`DeviceRegistry` 的拆分仍然**先停**（等 `RemoteTarget` 定了再拆）。
