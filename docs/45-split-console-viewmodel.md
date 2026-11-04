# 45 · 拆 ConsoleViewModel（第一块：GroupController）

按 docs/43 的建议执行 P0 的第二项。本轮拆**一块**，不是四块 —— 理由在最后。

---

## 一、成果

```
ConsoleViewModel.kt   2029 行 → 1677 行   (-352)
GroupController.kt       新增  506 行
```

**界面零改动**：`git status` 只有两个文件（`ConsoleViewModel.kt` 修改、`GroupController.kt` 新增），
`MainActivity.kt` 一行没动。所有公开方法名保留为一行委托，所以单测和 UI 都不受影响。

---

## 二、拆的过程本身就是一次一致性审计

我把 24 个"组相关"函数逐个核对 **UI 到底调不调用**：

| 函数 | MainActivity 调用次数 |
|---|---|
| `ungroupCard` | 1 |
| `setCardChannel` | 1 |
| `openGrouping` | 1 |
| `closeGrouping` | 2 |
| `toggleGroupingDevice` | 1 |
| `selectEverywhere` | 1 |
| `applyGrouping` | 1 |
| `toggleMask` | 1 |
| **其余 16 个** | **0** |

`setGroupVolume`、`cycleCardChannel`、`joinSlave`、`kickSlave`、`renameSlave`、
`setSlaveVolume`、`addToGroup`、`removeFromGroup`、`setSlaveDialog`、`clearSlaveDialog`、
`setJoinChannel`、`setJoinPassword`、`cancelMultiroom`、`refreshSlaves` —— **UI 一次都没调**。

### 为什么会有这么多死函数：两套模型并存

```
旧模型  state.slaves : List<SlaveDevice>          ← 只有 ip/name/volume，没有 isMaster/masked
新模型  state.groupMembers : List<GroupMember>    ← 有 isMaster/masked/battery/channel
```

旧的那套是"多房间卡片"时代的产物（PR #24），后来被勾选式分组（PR #48）取代，
但函数一直留着。**这正是 docs/41 说的一致性问题的另一种形态：不是写法不一致，是模型并存。**

本轮**没有删**这 16 个 —— 删死代码需要更仔细的验证（可能有未来要用的入口），
单开一单更稳。这里只如实记录，让下次动手时有依据。

---

## 三、为什么用 `GroupHost` 接口，而不是直接传 ViewModel

```kotlin
internal class GroupController(private val host: GroupHost) {
    internal interface GroupHost {
        val state: StateFlow<ConsoleUiState>
        fun update(transform: (ConsoleUiState) -> ConsoleUiState)
        fun log(message: String)
        fun run(label: String, block: suspend () -> Unit)
        fun launch(block: suspend CoroutineScope.() -> Unit)
        fun http(): DeviceHttpClient
        fun send(ip: String, command: String): String
        fun status(ip: String): LinkPlayStatus
        fun slaveList(ip: String): List<SlaveDevice>
        suspend fun refreshAll()      // 等待完成（协程内用）
        fun refresh()                 // 不等（非协程上下文用）
        fun renderer(device: SavedDevice): UpnpRendererClient
        fun pushDeviceVolume(ip: String, volume: Int)
        fun pushGroupVolume(member: GroupMember, volume: Int)
    }
}
```

两个理由：

1. **避免循环依赖**：VM → Controller → VM 会让"谁改谁"变得不可读。
2. **依赖面可见**：13 个方法就是控制器的全部外部能力。每多一个，就多一条能"顺手改别人"的路。

拆的过程中这条约束真的救了两次：

- `host.refreshAll()` 是 suspend，但 `removeFromGroup` 在函数体顶层调用它 → 编译报错。
  如果直接拿 ViewModel，调 `probeSaved()`（非 suspend）根本不会报错，只是**刷新时机悄悄变了**。
- `MultiroomClient(client)` 拿不到 `client` → 编译报错，逼我显式加 `host.http()`，
  而不是"顺手用一下 ViewModel 的字段"。

---

## 四、验证

| 项 | 结果 |
|---|---|
| 单测 | **623 用例 0 失败**（与拆前一致，一个没改） |
| `:app:compileDebugKotlin` | BUILD SUCCESSFUL |
| `:app:assembleDebug` | BUILD SUCCESSFUL |
| 界面改动 | **0 行**（`MainActivity.kt` 未修改） |
| 行为变化 | 无（纯搬移 + 一行委托；`applyGrouping` 里两行重复的刷新已合并成一次） |

**未验证**：真机组网/声道/屏蔽的实际点击。`.153` 本轮不可达，`.164` 只做了只读核对，
不敢说"功能没退化"——只能说"代码等价搬移，单测与编译都过"。

---

## 五、为什么这轮只拆一块

原本计划拆 4 个（`DeviceRegistry` / `GroupController` / `DeviceControl` / `SessionRestore`）。
实际做下来判断**先停在一块**：

1. **发现了两套模型并存**（`slaves` vs `groupMembers`）。这不是"搬代码"能解决的，
   要先决定留哪套 —— 那是产品决策，不该夹在重构里顺手做。
2. **每块的依赖面不一样**。`GroupController` 的依赖相对干净（组状态 + multiroom 命令），
   `DeviceRegistry` 要碰 `snapshot` / `dmrClients` / `store` / `discovery`，耦合更重。
3. **单 PR 可回滚**。一块 500 行的搬移已经能验证模式可行；四块一起做，出问题难定位。

### 下一块的建议顺序

| 顺序 | 目标 | 依赖面 | 预计减少 |
|---|---|---|---|
| 1 | `DeviceControl`（音量/播放/预设/固件） | 中（`client` + `renderer` + `VolumeBus`） | ~400 行 |
| 2 | `DeviceRegistry`（发现/探活/记录/快照） | 重（snapshot + dmrClients + store + discovery） | ~500 行 |
| 3 | `SessionRestore` | 轻（`store` + `QueueBus`） | ~100 行 |

拆完后 `ConsoleViewModel` 约 300~400 行，只做状态聚合 + 委托 —— 就是 docs/43 说的"底座"。

### 另外要单独决策的

- 16 个死函数删不删（先确认没有未来用途）
- `state.slaves` 旧模型能不能整体删掉（UI 已全部走 `groupMembers`）
