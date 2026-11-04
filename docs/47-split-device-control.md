# 47 · 拆 ConsoleViewModel（第二块：DeviceControl + ConsoleHost 统一）

按 docs/45 的顺序，本轮拆第二块。

---

## 一、成果

```
ConsoleViewModel.kt   1627 行 → 1331 行   (-296)
DeviceControl.kt         新增   348 行
ConsoleHost.kt           新增    78 行   ← 共享的能力面接口
GroupController.kt        332 → 304 行   （删掉自己的 GroupHost，改用共享的）
```

累计（从最初的 2029 行算起）：**2029 → 1331，减 698 行**。

**界面零改动**：`MainActivity.kt` 一行没动，所有公开方法名保留为一行委托，单测一个没改。

---

## 二、关键改动：`GroupHost` → `ConsoleHost`（共享一份）

上一轮 `GroupController` 自带一个 `GroupHost` 接口。这一轮 `DeviceControl` 需要的能力**高度重叠**
（读写状态、发命令、跑协程、拿 UPnP 客户端）。

如果各写一份，就会出现"同一个能力在两个接口里名字/语义不同"——**那正是本项目一直在治的病**
（docs/41 那 7 条、docs/43 说的"模型并存"都是这个形态）。

所以抽出 `ConsoleHost`（`ConsoleHost.kt`），两个控制器共用：

```kotlin
internal interface ConsoleHost {
    val state: StateFlow<ConsoleUiState>
    fun update(transform: (ConsoleUiState) -> ConsoleUiState)
    fun log(message: String)
    fun run(label: String, block: suspend () -> Unit)
    fun launch(block: suspend CoroutineScope.() -> Unit)
    fun http(): DeviceHttpClient
    fun send(ip: String, command: String): String
    fun status(ip: String): LinkPlayStatus
    suspend fun refreshAll()        // 探活 + 刷卡片，等完成
    fun refresh()                   // 同上但不等
    suspend fun refreshCards()      // 只刷卡片，不重新探活
    fun loadPlayback(uuid, ip): PlaybackSession?
    fun renderer(device: SavedDevice): UpnpRendererClient
    fun pushDeviceVolume(ip, volume)
}
```

实现只有一份：`ConsoleViewModel` 里的 `consoleHost` 字段，两个控制器都拿它。

### 拆的过程中又被接口约束救了两次

**第一次**：`playAll()` 里要读"上次的播放快照"，原代码是

```kotlin
PlaybackKey.candidates(uuid, masterIp).firstNotNullOfOrNull { store.loadPlayback(it) }
```

`store` 是 ViewModel 私有字段，直接搬会编译不过。**逼我加 `host.loadPlayback(uuid, ip)`** ——
这个抽象是对的：控制器不该知道 `PlaybackKey` 的候选键规则（那套规则在 PR #32 出过
"三处存盘键不一致"的事故）。

**第二次**：`toggleDevicePlay` / `playAll` 里要"让界面立刻刷新"，原来调 `refreshDevicesInGroup()`。
搬到控制器后编译不过（那是 VM 私有方法）。**逼我加 `host.refreshCards()` 而不是图省事用
`host.refreshAll()`** —— 后者会顺带重跑探活，把 `online` 状态也重写一遍，动作后界面会闪一下。
两个方法语义不同，混用就是 bug。

---

## 三、边界怎么划的

| 控制器 | 负责 | 不负责 |
|---|---|---|
| `GroupController` | 组的结构（谁和谁一组、声道、屏蔽） | 播放动作 |
| `DeviceControl` | 设备的动作（音量、播放、预设、固件、定时关闭） | 组的结构 |

交叉点是"全部播放/全部暂停"这类**作用于整组**的动作 —— 它们按 `groupMasterIp` 找主机，
但本质是播放动作，所以留在 `DeviceControl`。

`refreshDevicesInGroup`（取数轮询）**没有**搬进 `DeviceControl` —— 它属于"读设备"，
将来跟 `DeviceRegistry` 一起搬。

---

## 四、验证

| 项 | 结果 |
|---|---|
| 单测 | **623 用例 0 失败**（与拆前一致，一个没改） |
| `compileDebugKotlin` / `assembleDebug` | BUILD SUCCESSFUL |
| `scripts/check-init-order.py` | 通过 |
| `MainActivity.kt` | 未修改 |
| 失效 import | 清了 3 个（`VolumeStep` / `toSnapshot` / `UpnpRendererClient`） |

**未验证**：真机上音量键、全部播放/暂停、预设键、固件检查的实际点击。
`.153` 离线，`.164` 只做只读核对。

---

## 五、还剩什么

`ConsoleViewModel` 现在 1331 行，剩下的主要是：

| 关注点 | 行数估算 | 下一块 |
|---|---|---|
| 设备发现/探活/记录/快照 | ~450 | `DeviceRegistry` |
| 会话恢复（`restoreLastQueue`） | ~60 | `SessionRestore` |
| 诊断（`registerDiagnostics` / `diagnosticsReport`） | ~120 | 可留可拆 |
| 委托 + `consoleHost` 实现 | ~120 | 保留（这是底座的接线） |
| `renameCard` | ~70 | 待定（涉及设备记录 + 组内从机两条路） |

拆完 `DeviceRegistry` 后，`ConsoleViewModel` 大约 500 行，就是 docs/43 说的"底座"。
