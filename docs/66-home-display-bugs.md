# 66 · 主界面显示错误：三台在线却报"1 台在线" + 红字

> 2026-11-04 · 用户截图（14:12）· 仅分析，不改代码
> 依据代码状态：`34a2394`

---

## 一、截图读出来的事实

| 卡片 | IP | 显示 | 电量 |
|---|---|---|---|
| doss1 | `.164` | **在线**，在播放（122 首第 1 首，音量 60） | 60% |
| doss3 | `.153` | **属 doss1** / 组内 / **设备无响应** / 没有播放列表 | 100% |
| doss2 | `.181` | **属 doss1** / 组内 / **设备无响应** / 没有播放列表 | 100% |

- 标题下：**"1 台在线"**
- 红字：`ConnectException: Failed to connect to /192.168.99.181:80`
- 聚合条：`doss1 · 3 台同步`

**结论：三台已经组网，doss1 是主机。** 组网后从机落在主机 AP 网段（`10.10.10.x`），
**从路由器侧不可直连** —— 这是项目早就验证过的事实（`docs/27`、`docs/43`）。

所以问题不是"设备坏了"，是**界面把"不可直连"当成了"不在线"和"无响应"**。

---

## 二、问题 1 · "1 台在线" —— 探活没跳过组内从机

### 根因（`ConsoleViewModel.probeSaved`）

```kotlin
private fun probeSaved() {
    val targets = _state.value.saved
    ...
    for (d in targets) {                                   // ← 逐台，不判断在不在组里
        val snap = withContext(IO) { snapshots.read(d.ip) } // ← 从机必然失败
        if (snap != null && ...) { result[d.uuid] = true }
        else {
            result[d.uuid] = false                          // ← 从机落到这里
            val (upnpProfile, _) = profileOfSaved(d)        // ← 再连一次（也失败）
            ...
        }
    }
    // ↓↓↓ 组关系是在探活**之后**才读的
    for (d in targets) {
        if (result[d.uuid] != true) continue                // ← 从机 online=false，直接跳过
        val slaves = MultiroomClient(client).slaves(d.ip)   // ← 只有主机能读到从机
        ...
    }
}
```

**时序问题**：

```
1. 逐台直连探活        → 从机连不上 → online = false
2. 才读 slave_list     → 这时才知道谁是主机、谁是从机
3. 界面统计            → count { online != false } = 1
```

### 对比：卡片心跳**已经做对了**

`ConsoleViewModel:1137`（`cardHeartbeat`）：

```kotlin
// 只轮询"路由器侧够得着"的设备：入组的从机在主机 AP 网段里，
// 从这边直接连必然超时（真机实测），跳过去，靠主机的 slave_list 表达它的状态。
if (_state.value.groupedUnder.containsKey(UuidMatch.key(d.uuid))) continue
```

**心跳跳过了，探活没跳过。** 同一个判断，两处只做了一处 —— 这是典型的"多路径不一致"。

### 修复方向

**最小改动**：探活开头先用**上一轮的** `groupedUnder` 跳过从机。

```kotlin
for (d in targets) {
    // 和 cardHeartbeat 同一判断：组内从机不可直连，状态由主机转述
    if (_state.value.groupedUnder.containsKey(UuidMatch.key(d.uuid))) {
        result[d.uuid] = true      // ← 组内 = 在线（只是不可直连）
        continue
    }
    ...
}
```

**为什么是"上一轮的"**：首次启动时 `groupedUnder` 是空的，第一次探活仍然会失败。
第二次（3 秒后的心跳/下一轮探活）就对了。

**更好的做法**：`online` 从 `Boolean` 改成三态，见第三节。

---

## 三、更深的问题 · `online: Map<String, Boolean>` 表达不了"在组里"

现在的语义：

| 值 | 界面理解 |
|---|---|
| `true` | 在线 |
| `false` | 离线 |
| `null` | 未探测 |

但真实世界有**第四种**：**在工作，但不可直连**（组内从机）。

这不是"离线"，因为它在正常同步播放；也不是"在线"，因为我们连不上它。

### 建议改成三态

```kotlin
enum class DeviceReachability {
    DIRECT,    // 能直连（独立设备 / 主机）
    IN_GROUP,  // 在组里，只能由主机转述（从机）
    OFFLINE,   // 连不上，也不在任何组里
}
```

**收益**：
- "N 台在线"统计时 `DIRECT + IN_GROUP` 都算
- 卡片显示能分清"设备无响应"和"跟随主机同步"
- 诊断报告能说准话（现在写"离线"是撒谎）

**这正是 `docs/57`（内容源）和 `docs/62`（状态收敛）说的同一个病**：
**用 Boolean 表达不了三种状态**。

---

## 四、问题 2 · 从机显示"设备无响应"

### 根因（`home/DeviceCardRow.kt:201`）

```kotlin
title ?: if (online == false) "设备无响应" else "空闲中 · 点开挑一首"
```

**只判断 `online`，不判断 `groupedUnder`。**

而同一张卡片右上角（第 167 行）显示"组内"标签。

→ 一张卡片同时说"这是组内的"和"设备无响应"，**自相矛盾**。

### 修复方向

```kotlin
title ?: when {
    groupedUnder != null -> "跟随 $groupedUnder 同步"   // 组内：在放，只是我们连不上
    online == false -> "设备无响应"
    else -> "空闲中 · 点开挑一首"
}
```

**优先级有意**：组内从机的状态由主机转述，比"能不能直连"更准确。

---

## 五、问题 3 · 红字 `ConnectException: ... /192.168.99.181:80`

### 能确定的部分

1. **格式**是 `run()` 的 catch（`ConsoleViewModel:961`）：
   ```kotlin
   _state.update { it.copy(error = "${t.javaClass.simpleName}: ${t.message}") }
   ```
   只有这一处会产出 `类名: message` 的格式。

2. **端口 80** = httpapi（`DeviceHttpClient`），不是 UPnP（49152）。

3. **目标是 `.181`** = 组内从机 → 必然 `ConnectException`。

4. **`run()` 有 12 个调用点**，其中**裸 `host.send` 不包 `runCatching`** 的只有三个：

| 位置 | 动作 | 界面入口 |
|---|---|---|
| `DeviceControl.sendCommand`（69 行） | 手输命令 | 设备设置页的"发送命令" |
| `DeviceControl.setDeviceName`（79 行） | 改名 | 卡片齿轮 → 改名 |
| `DeviceControl` 固件检查/升级（316-320、338 行） | 固件 | 设备设置页 |

**其余 9 个调用点都包了 `runCatching`，不会抛到这里。**

### 不能确定的部分

**不知道你点了哪个。** 三种都可能，需要一条信息才能锁定：

- 诊断报告的 `[指令]` 段（会显示最近发出的命令和目标）
- 或者你回忆一下：红字出现前点了什么按钮？

### 但根本问题不在"哪个按钮"

**这三个动作对组内从机根本不该可用** —— 从机不可直连，发了必然失败。

正确做法是**按能力位隐藏入口**（`DeviceCapabilities` 的方向）：
从机应该有一个"不可直连"的状态，界面据此**不渲染**这些按钮。

这和 `docs/52` 的铁律一致：**不给按了没反应的按钮**。

---

## 六、三个问题的关系

```
根因：online 是 Boolean，表达不了"在组里但不可直连"
  ├─→ 问题 1："1 台在线"（统计错）
  ├─→ 问题 2："设备无响应"（文案矛盾）
  └─→ 问题 3：红字（按钮不该出现，点了必然失败）
```

**修一个根因，三个现象一起消失。**

如果只修表象（比如把"设备无响应"改成"组内"），红字还在；
如果只修统计（把从机算在线），红字还在。

---

## 七、建议的修复顺序

| # | 做什么 | 成本 | 能消掉 |
|---|---|---|---|
| 1 | 探活跳过组内从机（照 `cardHeartbeat` 的判断） | 低 | 问题 1 |
| 2 | 卡片文案优先看 `groupedUnder` | 低 | 问题 2 |
| 3 | 组内从机隐藏"改名/固件/手输命令"入口 | 低 | 问题 3 |
| 4 | `online` 改三态（`DeviceReachability`） | 中 | 三个问题的根 |

**1-3 可以先做**（都是局部改动，不改数据结构），**4 是根治**但会碰 `ConsoleUiState`，
建议等 `docs/62` 的状态收敛一起做。

---

## 八、和现有计划的关系

这个问题**不在** `docs/64` 的六批次里 —— 它是新发现的。

**建议插在批次 1 之后、批次 2 之前**，作为"批次 1.5"。

理由：
- 它影响**用户每天看到的主界面**，比"补完六个源的诊断"更紧急
- 但它和批次 2 的"取数旁路"共用同一块代码（`probeSaved` / `cardHeartbeat`），
  所以要和批次 2 一起规划，别两边同时改

**不打断当前开发**：那边正在做批次 1（六个源的诊断），
这个问题可以等它做完当前那个源再插。

---

## 九、需要你提供的一条信息

红字定位需要知道**你点了哪个按钮**。两个办法：

1. 把诊断报告截图给我（`◔` 那个），看 `[指令]` 段最近发了什么
2. 或者回忆一下：红字出现前你点了什么？

有了这条，问题 3 就能从"三个候选"收敛到一个。
