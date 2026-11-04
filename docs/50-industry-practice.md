# 50 · 大厂怎么做遥控/智能家居的分层（调研 + 对我们的取舍）

> 状态：**调研结论**（供参考，不是待办）
> 前提：**不做蓝牙、不做红外**，先把音箱这块做完。

---

## 一、四个体系的实际模型

### Matter（CSA 标准，Apple / Google / Amazon / Samsung 共用）

```
Node → Endpoint → Cluster → { Attributes, Commands, Events }
```

原文关键句：

> A **Device Type** is a collection of mandatory and optional **Clusters**.
> A **cluster** may be thought of as an *interface, service, or object class* and is
> **the lowest independent functional element** in the device data model.

> **Endpoint 0** is reserved for the **Utility Clusters** — discovery, addressing,
> **diagnostics** and software update. **Application Clusters** support primary actions.

**两条可直接用**：
1. **设备类型不是独立概念，只是「能力（Cluster）的组合清单」**
2. **诊断是工具集群，独立于应用集群**（Endpoint 0）—— 我们 `core-diagnostics` 正好是这个形态

### HomeKit

```
Accessory → Service → Characteristic
```

> HomeKit uses two types of attributes, **services** and **characteristics**,
> to model the **capabilities** of an accessory.

一个 Accessory（一个物理设备）挂多个 Service（灯 / 门 / 温度），
Service 里是 Characteristic（On / Brightness / Hue）。

**和 Matter 同构**：Service ≈ Cluster，Characteristic ≈ Attribute。

### Home Assistant

```
Integration（协议，如 hue）→ Entity（标准化，如 light）→ Device / Entity Registry
```

原文关键句：

> The **entity integration** (that is, `light`) is responsible for defining the
> **abstract entity class and services** to control the entities.
> The **device integration** (`hue`) uses config to **set up a connection** with the device.

> The entity registry will write an **`unavailable`** state for any registered entity
> that is **not currently backed by an entity object**.

> **the user's choices win** — the integration can update its own fields at any time,
> **without overwriting the user's choice**.

**三条可直接用**：
1. **协议层（hue）和抽象层（light）是两个不同的包**，各自独立演进
2. **「没有 backing 对象」是 `unavailable`，不是「关闭」** —— 我们的「未探测」三态同源
3. **协议层永远不覆盖用户的命名/排序/分类** —— 我们 PR #44 那个「改名被 SSID 覆盖」的 bug 正是违反这条

### Music Assistant

```
PlayerProvider（协议）→ Player（设备实例）→ PlayerType（PLAYER / PROTOCOL / GROUP）
```

原文关键句：

> **PlayerType.PROTOCOL**: Generic protocol players discovered via standard streaming
> protocols (AirPlay, Chromecast, DLNA) but lacking native vendor integration.
> These are **hidden from the UI** and either: (1) **Linked to a native player if
> identifiers match (protocol linking)**, or (2) wrapped in a generic player.

**这条对我们直接有用**：同一个物理设备可能被两条路发现两次
（比如音箱同时开了 LinkPlay 和 DLNA）。它的做法是：
- 用标识符匹配 → **合并成一个，优先用原生**
- 匹配不上的 generic 播放器 **不在 UI 显示**

---

## 二、四家共性：一个骨架

| | 能力单元 | 设备类型是什么 | 协议层职责 | UI 依据 |
|---|---|---|---|---|
| **Matter** | Cluster | Cluster 的组合清单 | 传输无关（fabric） | 集群的 mandatory 集合 |
| **HomeKit** | Service | Service 的组合清单 | HAP 传输 | Service 类型 |
| **Home Assistant** | Entity（domain） | Entity 的组合 | Integration | Entity domain |
| **Music Assistant** | Player + capabilities | PlayerType | PlayerProvider | type + 能力位 |

**四条铁一样的共识**：

1. **能力是「服务/集群/实体」，设备类型只是能力的组合清单**
   → 判断"能不能显示音量按钮"看**有没有音量能力**，不看"是不是音箱"

2. **UI 从能力推导，不从设备类型推导**
   → HomeKit 的整个 UI 是"这个 Accessory 有哪些 Service"推出来的

3. **协议层与抽象层是两个独立的包，抽象层定义接口，协议层只负责连设备**
   → HA 的 `light`（抽象）和 `hue`（协议）是分开的两个仓库路径

4. **诊断/寻址/OTA 属于「工具层」，与业务能力平行**
   → Matter 的 Endpoint 0

---

## 三、我们已经做对的（对照下来）

| 做法 | 大厂对应 | 我们的实现 |
|---|---|---|
| 诊断独立于业务 | Matter Endpoint 0 Utility Clusters | `core-diagnostics`（零依赖、可 publish） |
| 「未探测」≠「离线」 | HA 的 `unavailable` | `online: Map<String, Boolean?>` 三态 |
| 能力位决定渲染 | Matter 的 Cluster / HomeKit 的 Service | `DeviceProfile` 的 8 项能力位 |
| 单一取数入口 | HA 的 state machine | `DeviceSnapshotReader` → `applySnapshot` |

**结论**：方向上我们和标准是一致的，不是自己发明的。

---

## 四、我们**不能**照搬的三条

大厂做这些的前提是「要对接上千家厂商的设备」，我们是「自己用几台」。差一个量级，照搬就是过度设计。

| 大厂做法 | 为什么我们不能照搬 |
|---|---|
| Matter 的 Device Type Library（几百页标准文档） | 我们只有 3 类设备。定义 20 个 Device Type 是纯负担 |
| HomeKit 的完整 Characteristic 类型表（200+ 种） | 我们需要的不到 15 个 |
| HA 的完整 registry（device / entity / area / floor / label 五套） | 我们只有"设备"一层，不需要房间/楼层/标签 |

**要的是它们的「思路」，不是它们的「完整度」。**

判据：如果某个抽象需要「先写一份标准文档」才能用，那对我们就是过度设计。

---

## 五、既然不做蓝牙红外，P0 砍掉什么

这是本轮最重要的结论：**原 P0 里有一半是为蓝牙/红外准备的，现在可以砍。**

| 原计划 | 现在 | 理由 |
|---|---|---|
| `TargetAddress` 三地址（Net/Bt/Ir） | ❌ **砍掉** | 全是 WiFi，`SavedDevice(ip)` 够用 |
| `DispatchResult.Sent`（单向无回执） | ❌ **砍掉** | WiFi 一定有应答 |
| `Transport.observe(): Flow?`（可空） | ❌ **砍掉可空** | WiFi 一定有状态 |
| `RemoteCommand` 语义指令 | ✅ **保留** | LinkPlay + DLNA **已经是两个实现者** |
| `Capability` 能力位 | ✅ **保留并扩大** | 大厂共识，且我们已有雏形 |
| `RemoteView`（UI 依赖它） | ✅ **保留** | UI 解耦的唯一路径 |

### 关键：砍掉 ≠ 封死

砍掉的方式是**用 sealed interface 只留现有分支**，将来加分支是加一个 case，不改签名：

```kotlin
// 现在
sealed interface DispatchResult {
    data class Acked(val detail: String? = null) : DispatchResult
    data class Failed(val failure: DispatchFailure) : DispatchResult
}
// 接红外那天，加一个 case 就行，所有 when 会编译报错提醒补分支
// data object Sent : DispatchResult
```

**留门 ≠ 现在建好房间。** 这是两个成本差一个数量级的动作。

### 瘦身后的 P0（三件事）

| # | 做什么 | 收益 |
|---|---|---|
| 1 | `RemoteCommand` + `LinkPlayTransport`（语义 → 方言翻译） | `DeviceControl` 不再拼字符串 |
| 2 | `Capability` 扩大 + 界面改成按能力位渲染 | 加 DLNA 设备不用改界面 |
| 3 | `RemoteView`（UI 吃它，不吃 `ConsoleUiState`） | 界面与音箱解耦 |

**不做**：`TargetAddress`、`Transport` 接口、`observe()`、`protocol-*` 模块拆分。

---

## 六、一个现在就该补的缺口：protocol linking

对照 Music Assistant 的做法，我们有个真缺口：

**同一个物理设备可能被两条路发现两次**。

- DS-1831 同时开着 LinkPlay 和 DLNA 时会怎样？
- 那个身份未明的 `192.168.99.181` —— **有没有可能它就是 `.153` 的另一种发现方式？**

Music Assistant 的处理：
1. 用标识符（uuid / MAC / 序列号）匹配
2. 匹配上 → 合并成一个，优先用原生（功能全的）
3. 匹配不上 → generic 播放器 **不在 UI 显示**（避免列表里出现两个"同一个东西"）

我们现在 `DeviceProfile` 分了三档，但**没有合并逻辑**。
`.181` 这个悬案正好是验证这个缺口的实例。

→ 建议：下一轮先扫一次全网，把 `MediaRenderer` + LinkPlay 两种发现结果都列出来，
按 uuid 对齐，看 `.181` 到底是什么。**这既是查案，也是验证 protocol linking 的必要性。**

---

## 七、结论

1. **我们的方向和标准一致**：能力位驱动、诊断独立、三态状态、单一取数入口 —— 这四条都对
2. **要补的是 UI 解耦**：界面 39 处直接读音箱字段，必须改成读 `RemoteView.capabilities`
3. **P0 可以砍一半**：蓝牙/红外的抽象现在不做，用 sealed interface 留门即可
4. **要补一个缺口**：protocol linking（同一设备多路发现时合并），`.181` 是活例子
5. **不要照搬完整度**：我们要思路，不要 Device Type Library 那种规模的标准化
