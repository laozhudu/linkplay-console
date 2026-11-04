# 44 · 一致性 P1：uuid 单一入口 + 队列归属编译期强制

按 docs/43 的建议执行 P0。两条都是**真 bug**，而且正好是"单一入口 + 编译期强制"的样板。

---

## 一、uuid：真因不是大小写，是长度

### 我原本以为的问题

三处各自归一化，一处带 `substringAfterLast(':')`、一处只 `removePrefix` —— 以为差异在写法。

### 实际拿真机算出来的

`.153` 的 UDN 去连字符后逐段对齐：

```
ff31f0174fe5c9b2a2aceae3 ff31f017
└──── 24 位真 uuid ────┘ └ 前 8 位重复 ┘
```

- 后 8 位 **正好等于** 前 8 位
- 前 24 位 **正好等于** `getStatusEx.uuid`

**所以 LinkPlay 的 UDN 格式是 `<uuid24><前8位重复>`。**

`.164` 复核同样成立：

```
getStatusEx.uuid = FF310096A5760623673F81F1
upnp_uuid        = uuid:FF310096-A576-0623-673F-81F1FF310096
                   ff310096a5760623673f81f1 ff310096
```

两台设备、两条独立证据，规则成立。

### 为什么"去前缀去连字符"不够

```
uuid:FF31F017-4FE5-C9B2-A2AC-EAE3FF31F017  → 去连字符 → 32 位
FF31F0174FE5C9B2A2ACEAE3                    → 本身就是 → 24 位
```

**两个不同字符串。** 所以哪怕三处写法统一，只要不处理这 8 位尾巴，同一台音箱仍会被存成两条记录。

### 修法

`UuidMatch` 新增 `canonical()`：

```kotlin
fun canonical(raw: String?): String {
    val k = key(raw)
    // 结构吻合才截断：32 位、且末 8 位 == 前 8 位
    if (k.length == 32 && k.substring(24) == k.substring(0, 8)) return k.substring(0, 24)
    return k
}
```

判据是**结构吻合**，不是"长度 32 就截断" —— 标准 GUID 后 8 位等于前 8 位的概率可忽略，不会误伤。

`SavedDevice` 构造函数改成 `private`，唯一入口 `SavedDevice.of(rawId, ip, name, …)`，
三处调用点全部改走工厂。**编译器从此刻起不允许再出现第四种写法。**

### 老数据怎么办

`SavedDevice.decode` 也走 `of()`，所以升级后旧记录自动归一化。
`DeviceList.decode` 再用 `UuidMatch.same` 做一次去重合并：

- 保留**最近见过**的那条（IP 更新）
- 名字取"第一个非空的" —— 名字是用户设的，比 IP 更不该丢

**用户不需要手动删设备重加。**

---

## 二、队列归属：编译期强制，而不是"记得先 setOwner"

### 改造前的真实状况

PR #40 修过一次"切设备后队列串台"，但只改了 `QueueStarter` 一条路。
`QueueBus.restore()` 内部写的是 `ownerIp = currentOwner`（**当前值**），
调用方忘了先 `setOwner` 就把新列表挂到上一台设备名下。

当时 13 处写入，只有 3 处设过 owner：

| 写入点 | 改造前 |
|---|---|
| `QueueStarter:69/95` | ✅ setOwner |
| `PlaybackViewModel:380/751/772` | ✅（198 设过） |
| `NasViewModel:426/437` | ❌ |
| `ConsoleViewModel:431/455/1470` | ❌ |
| `QueuePlaybackService:67/77/150/163` | ❌ |

### 修法：签名强制

```kotlin
// 改前
fun restore(items, index, title, deviceOwned, offset, note)
fun begin(items)
fun playing(title, index, items)
fun clear()

// 改后
fun restore(deviceIp, items, index, title, deviceOwned, offset, note)
fun begin(deviceIp, items)
fun playing(deviceIp, title, index, items)   // 归属不符直接忽略
fun clear(deviceIp)                          // 只清自己那台
```

`currentOwner` 变 `private`，外部不能再直接写。

### 编译器的表现（这是"编译期强制"的实证）

改完签名后 `:app:compileDebugKotlin` 一次报出 **13 处漏点**：

```
ConsoleViewModel.kt:436/460/1473        No value passed for parameter 'deviceIp'
NasViewModel.kt:426/437                 Argument type mismatch (List vs String)
PlaybackViewModel.kt:383/755/776        ...
QueuePlaybackService.kt:66/76/143/153   ...
QueueStarter.kt:70                      ...
```

**一个都跑不掉。** 如果是"记得先调 setOwner"，这 10 处会一直静默存在，
直到某天用户从 NAS 起播、切设备、看到别人家的列表。

---

## 三、验证

| 项 | 结果 |
|---|---|
| 单测 | **623 用例 0 失败**（609 + 新增 14） |
| 新增用例 | `SavedDeviceIdentityTest` 8 条、`QueueBusOwnershipEnforcedTest` 6 条 |
| 真机 uuid | `.164`：`canonical(FF310096A5760623673F81F1)` == `canonical(uuid:FF310096-A576-0623-673F-81F1FF310096)` == `ff310096a5760623673f81f1` ✅ |
| 真机 `.153` | **不可达**（ping / 80 / 49152 全 000，ARP `FAILED`，局域网扫描未发现换 IP）—— 设备应是关机了，本轮无法验证第二台 |
| 编译期强制 | 改签名后编译器一次抓出全部 13 处漏点 |

**诚实标注**：`.153` 离线，所以"两台都验过"这句话不成立。
uuid 规则已在 `.153` 的历史数据上算过（`ff31f0174fe5c9b2a2aceae3` + `ff31f017`），
但那是离线推算，不是本轮实时读到的。

---

## 四、下一步

`ConsoleViewModel`（2027 行 / 76 函数）拆分另起一单，便于单独回滚。
本轮不动它 —— 两个真 bug 先落地，风险和收益都更清楚。
