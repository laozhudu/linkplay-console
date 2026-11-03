# 39 · 设备信息统一到一条路径（最短路径 + 单一合并规则）

用户的问题问得很准：

> 「硬件本身信息的回传到 app 上应该有最短路径，我们目前各个信息的回传是否各走一套，没有标准化统一起来」

答案：**是的，之前各走一套，一共 5 条通道各写各的**。这正是电量"有时有有时没有"的根因。

## 一、改造前的真实状况（5 条通道）

| 信息 | 通道 | 谁在写卡片 |
|---|---|---|
| 曲名 / 音量 / 播放态 / 列表长度 | httpapi `getPlayerStatus` | 首页 3 秒心跳、`refreshDevicesInGroup` |
| 电量 / 设备名 / 固件 | httpapi `getStatusEx` | `probeSaved` |
| 声道 / 静音 | UPnP `GetControlDeviceInfo` | `probeSaved`（上一轮新增） |
| 从机列表 | httpapi `multiroom:getSlaveList` | `probeSaved` |
| 通用 DMR 的一切 | UPnP 标准三连 | 首页心跳 |

每处都是 `cards + (uuid to 新卡片)` —— **整体替换**，只填自己知道的字段，其余留 `null`。

### 电量为什么还是看不到

我上一轮只修了 `refreshDevicesInGroup` 的覆盖，**漏掉了首页那个 3 秒心跳**（`ConsoleViewModel` 第 319 行）。
它每 3 秒自己拼一张全新 `DeviceCard`（没有 battery）整体覆盖 ——
电量刚由 `probeSaved` 写进去，最多 3 秒就被心跳清成 `null`。
用户看到的就是"有时候有、一会儿又没了"，实际上是"一直在被抹掉"。

## 二、原厂是怎么做的（一条路径）

反编译 `org/teleal/cling/support/d/a/a.java`：原厂只用**一个**入口
`RenderingControl.GetControlDeviceInfo`，一次返回：

```
CurrentVolume / CurrentMute / CurrentChannel / SlaveMask / SlaveList / Status
```

其中 `<Status>` 里嵌的就是**整份 `getStatusEx`**（电量、设备名、固件、信道、RSSI 全在）。

真机实测（`.153` / `.164`，各 3 次）：

| 请求 | 耗时 | 体积 | 能给什么 |
|---|---|---|---|
| `GetControlDeviceInfo` | 80ms | 3112B | 音量 + 静音 + 声道 + 从机 + **整份 Status（含电量）** |
| `getStatusEx` | 69ms | 1375B | 只是 Status 那一部分 |
| `getPlayerStatus` | 32ms | 485B | 曲名 / 进度 / 播放态（Status 里没有） |
| `getSlaveList` | 51ms | 60B | SlaveList 里已经有 |

**最短路径 = `GetControlDeviceInfo` + `getPlayerStatus`，两次请求。**

## 三、改造后的结构

```
                    ┌──────────────────────────────┐
   设备 ──────────▶ │ DeviceSnapshotReader（唯一取数）│
                    │ ① GetControlDeviceInfo        │ 音量/静音/声道/从机/整份Status
                    │ ② getStatusEx（仅当①缺字段） │ 电量/名字的兜底
                    │ ③ getPlayerStatus             │ 曲名/进度/播放态
                    │ ④ getSlaveList（仅当①没给）  │ 组信息兜底
                    └──────────────┬───────────────┘
                                   ▼
                        DeviceSnapshot（唯一模型）
                     mergeWith：没报的字段保留旧值
                                   ▼
                  applySnapshot（唯一写卡片路径）
                                   ▼
                          DeviceCard → 界面
```

四条规则：

1. **一个取数入口**：`core-protocol/snapshot/DeviceSnapshotReader`。所有轮询、所有页面都调它。
2. **一个数据模型**：`DeviceSnapshot`，带 `answered: Set<Channel>` —— 这一轮到底哪条通道答了，
   排障时一眼看出"电量为什么没有"。
3. **一条合并规则**：`DeviceSnapshot.mergeWith()` / `CardMerge.apply()`。
   **设备没报的字段保留旧值**，绝不用默认值覆盖真值。
4. **一个写入点**：`ConsoleViewModel.applySnapshot()`。
   原来的 4 处 `cards + (uuid to …)` 全部删掉（含那个罪魁祸首 3 秒心跳）。

## 四、界面上的第二个坑（电量被挤出去）

就算数据对了，电量原来放在**副标题行**：`IP · 在线状态` 后面跟 `🔋40%`。
IP 一长（`192.168.99.153 · 在线`），电量就被挤出可视区 —— 这是纯布局问题。

现在电量移到**设备名同一行**（右侧），字号 `labelLarge`，
充电态用绿色 `⚡100%`、放电用白色 `🔋40%`。名字过长时省略号截断，电量永远可见。

## 五、验证

真机闭环（`DeviceSnapshotLiveIT`，`LIVE_DEVICES=192.168.99.153,192.168.99.164`）：

```
[192.168.99.153] 通道=[CONTROL_INFO, PLAYER_STATUS] 请求=[getPlayerStatus]
[192.168.99.153] 电量=50 充电态=true 声道=0 音量=39 名字=doss3
[192.168.99.164] 通道=[CONTROL_INFO, PLAYER_STATUS] 请求=[getPlayerStatus]
[192.168.99.164] 电量=100 充电态=false 声道=0 音量=34 名字=doss1
```

- **每台只发 1 次 httpapi 请求**（`getPlayerStatus`），其余全部来自一次 UPnP 调用 —— 最短路径成立
- 两台电量都拿到，充电态与真机一致（`.153` 在用电池、`.164` 接着电）
- 单测 **603 用例 0 失败**（新增 `DeviceSnapshotTest` 6 + `DeviceSnapshotReaderTest` 4）

## 六、遗留

- 组内从机的电量/声道仍来自主机转述的 `slave_list`（从机在 `10.10.10.x`，物理上连不上），
  这一条无法走最短路径，但已经并入同一个 `DeviceSnapshot.slaves`，不再是独立通道。
- 电量采样本身有波动（`.153` 在 40~50 之间变），这是设备侧行为，不是我们读错。
