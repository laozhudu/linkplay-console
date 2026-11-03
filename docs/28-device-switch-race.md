# 28 · 切设备串台：旧设备的轮询还在跑

用户现象：「两个设备各自打开不同内容，切到某台时先报错，等一会儿又正常了，
估计传输设备信息有延迟」——「延迟」不是原因，**是两只 ViewModel 同时在跑**。

## 根因

`PlayerScreen` 里是这么取 VM 的：

```kotlin
val vm: PlaybackViewModel = viewModel(key = "player-$deviceIp", factory = ...)
```

`key` 变了就会新建一个实例 —— 这部分看着是对的。问题在下半句：
**旧的实例不会被销毁**。它仍挂在 Activity 的 `ViewModelStore` 里，
`viewModelScope` 也没取消，于是它那个

```kotlin
while (isActive) { delay(1000); refreshOnce(silent = true) }
```

**每秒继续轮询上一台设备**，并往这些共享对象里写：

| 共享写入点 | 后果 |
|---|---|
| `_state`（自己被换掉的界面已不看它，无害） | — |
| `VolumeBus.publish(deviceIp, ...)` | 首页多房间卡片、新设备音量被旧值覆盖 |
| `QueueBus.syncDeviceIndex(...)` | 新设备的列表"跳首"错乱 |
| `SessionStore` 播放快照（键里带 uuid，本身不串） | — |

再加上 UPnP 读描述、拉快照这些请求本身要几百毫秒，切过去的瞬间
**新设备的数据和旧设备的数据在抢同一个 `_state`/`VolumeBus`**。
表现出来就是「先错、一会儿自己好」—— 那不是延迟补上了，是新设备的轮询终于盖过了旧的。

## 修法（三处，都很小）

### 1. 一台 App 只留一个 PlaybackViewModel

```kotlin
// PlayerScreen：去掉 key
val vm: PlaybackViewModel = viewModel(factory = ...)
LaunchedEffect(deviceIp, deviceLocation, deviceUuid) {
    vm.switchTo(deviceIp, deviceLocation, deviceUuid)   // 首次组合时同设备直接 return
}
```

`deviceIp` / `location` / `deviceUuid` 从构造参数 `val` 改成 `var`，
换设备**原地切换**，世界上永远只有一只轮询循环。

### 2. 代际号（`DeviceGeneration`）作废在飞请求

```kotlin
val token = generation.current()      // 发起前取票
...suspend 请求...
if (generation.isStale(token)) return // 回来先验票
```

切设备时 `generation.bump()`，之前发出的票立即全部作废。
为什么光靠「取消协程」不够：HTTP 请求完全可能在 `cancel()` 落地的同一刻返回，
取消不是同步屏障。票号比较是最后一道闸，成本只有一个 int 比较。

已经加了票的三个地方：`refreshOnce`、`prepare`（两处：读完描述、探完 httpapi）、
`restoreLastSession`（读完设备列表）。

### 3. 切设备清掉上一台的残留

- `switchTo` 把状态重置成 `PlaybackUiState(preparing = true)`，
  **只保留**用户正在填的投流参数（url / 需要头 / referer），其余全清 ——
  否则界面会短暂显示上一台的曲名和音量。
- `VolumeBus.forget(ip)`：新设备第一次回读之前，别拿旧设备那一档音量顶着。
- 停掉转发服务（`RelayMediaServer` 占着端口，属于上一台设备的需要）。

## 验证

- `DeviceGenerationTest` 5 个用例（切设备后旧票作废、无切换时票仍然有效、
  切换后新票有效、多次 bump、实例互不影响）
- 全量 **377 用例 0 失败**
- 真机协议层：`.153` 与 `.164` 的 `getStatusEx` / `description.xml` / `GetVolume` /
  `GetMute` / `GetTransportInfo` / `GetQueueIndex` / `BrowseQueue` / SSDP 组播
  全部正常 —— 说明「报错」与设备无关，纯 App 侧竞态

## 未做（如实说明）

这个修复的**最终确认只能在手机上点**：我这边没有能驱动真机界面的通道，
单测覆盖的是票号逻辑，`switchTo` 的状态重置是读代码确认的。
请在手机上做一次「.164 播放 → 切 .153 → 立刻看播放页」，
应当**不出现旧设备的曲名/音量**，也不该再出现红字。
