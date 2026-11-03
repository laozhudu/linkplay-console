# 38 · 四个问题的根因与大厂对照

用户报的四件事，三件是 bug、一件是"还能借鉴什么"。三件 bug 的根因都**不在界面**，
在数据来源与合并规则上。

## 一、设备重命名不成功

### 根因：读回的值比写入慢，读到旧值又把新名字覆盖回去

真机实测（`.153`，固件 3.6.5310）：

```
setDeviceName:客厅  → OK
t≈200ms   getStatusEx.DeviceName = 客厅        ← 立刻生效
t≈200ms   description.xml friendlyName = doss3 ← 还是旧名
t≈600ms   description.xml friendlyName = 客厅
```

上一版 `renameCard()` 是「下发 → `delay(800)` → `probeSaved()`」，而 `probeSaved()→touch()`
同步名字用的是 **`description.xml` 的 friendlyName**。在更早的机器上 800ms 够，
但慢一点的时候读回旧名，`touch()` 就把本地记录写回旧名 —— 用户看到"改名不成功"。

### 修法

1. `LinkPlayStatus` 新增 `deviceName`（`getStatusEx.DeviceName`）—— 这是**立刻生效**的真值来源；
2. `touch()` 的名字优先级改为 `DeviceName` → `friendlyName` → 保留本地值（**永不落到 ssid**）；
3. `renameCard()` 改成**轮询回读**：最多 8 次（≈3.5 秒）比对 `DeviceName`，
   一致才写回本地并提示"已改名为「X」"；不一致就如实报"设备仍报「旧名」"，不假装成功。

## 二、电量有时有、有时没有

### 根因：每 3 秒的播放轮询用一张全新卡片覆盖了整条记录

`refreshDevicesInGroup()` 原来是：

```kotlin
DeviceCard(title=…, volume=…, channel=p.channel, …)   // battery / profile 都是默认 null
cards + (uuid to card)                                // 整条替换
```

电量是另一条通道（`getStatusEx.battery_percent` / `slave_list[].battery`）写进卡片的。
两条通道交替跑，播放轮询一跑就把电量清成 `null` —— 于是电量"闪一下、消失、再闪一下"。

同时组内从机的匹配是 `it.uuid.equals(saved.uuid, ignoreCase = true)`，而设备给的
`slave_list[].uuid` 是 `uuid:FF31F017-4FE5-C9B2-A2AC-EAE3FF31F017`、
`getStatusEx.uuid` 是 `FF31F0174FE5C9B2A2ACEAE3` 这种 24 位无连字符格式 ——
**永远不相等**，从机的电量根本写不进卡片。

### 修法

1. 新增纯函数 `CardMerge`（6 单测）：**设备没报的字段保留上一轮的值**，报了才覆盖；
2. 新增 `identity/UuidMatch`（5 单测）：归一化后相等或互为前缀（≥16 位）才算同一台设备，
   覆盖 `groupedUnder` / 组内从机电量声道 / 声道指令 / 改名 等全部匹配点；
3. 电量前区分充电态：`battery=0` 显示 `⚡100%`，`battery=1` 显示 `🔋40%`
   （真机对照：`.164` 插电 `battery=0`，`.153` 放电 `battery=1`）。

## 三、声道切换又不能用 / 要实时显示当前声道

### 根因（两个，都是"只做了一半"）

1. **只做了从机那条命令**：上一版声道只发 `multiroom:SlaveChannel`，前提是"在组里"。
   设备没组网 → 直接提示"不在组里"；即使组了网，uuid 匹配（见上）也匹配不上 → 还是"不在组里"。
2. **卡片上的声道值一直是 null**：同样因为 `refreshDevicesInGroup` 覆盖式刷新。

### 原厂是怎么做的（反编译结论）

`com/wifiaudio/b/bl.java`（声道点击）：

```
if (device.b == "master")  → com.wifiaudio.g.a.d(i)  →  RenderingControl.SetChannel
else if (device.b == "slave") → multiroom:SlaveChannel:<从机IP>:<n>
```

而 `org/teleal/cling/support/d/a/{a,b}.java`（原厂自带的一份 Cling 扩展）给出逐字参数：

```
GetControlDeviceInfo   InstanceID=0
SetChannel             InstanceID=0, Channel=Master, DesiredChannel=<0|1|2>
SetVolume              InstanceID=0, Channel=Single,  DesiredVolume=<n>
```

**所以声道是 UPnP 动作，不依赖组网。** 真机实测（`.153` 未组网）：

```
SetChannel 1 → HTTP 200 → CurrentChannel=1  (getPlayerStatus.ch 也是 1)
SetChannel 2 → HTTP 200 → CurrentChannel=2
SetChannel 0 → HTTP 200 → CurrentChannel=0
```

### 修法

1. `UpnpRendererClient` 新增 `setChannel(n)` / `controlDeviceInfo()`；
2. `cycleCardChannel` 分两条路：组内从机 → 主机转达 `SlaveChannel`；**其余（独立设备/主机）→ `SetChannel`**；
3. 下发后 `delay(500)` **回读 `CurrentChannel`** 再刷新卡片 —— 显示的是设备报的值；
4. 卡片上声道从 28dp 圆点改成文字胶囊：「立体声 / 仅左 / 仅右」，
   选仅左/仅右时染成绿色，一眼看出当前状态；
5. 点击改成**三选一面板**（见下节）。

## 四、可以借鉴的成熟做法（已落地的 / 候选的）

| 借鉴点 | 出处 | 状态 |
|---|---|---|
| 声道一步选定左/右，而不是循环点击 | Sonos「创建立体声对」是引导式一步选定；M3 `SingleChoiceSegmentedButtonRow` 就是"最多 5 项选一" | ✅ 本轮：点声道 → 三选一面板（立体声/仅左/仅右） |
| 电量常驻卡片 + 区分充电态 | Sonos Roam 的电量在新 App 里被塞进「齿轮→房间→产品→Status 首行」，社区大量抱怨"找不到/时有时无"；成熟做法是常驻 + ⚡ | ✅ 本轮：卡片常驻 `🔋/⚡ + %` |
| 设备名只有一个真相源 | Sonos 官方承认改名"不持久"，原因是多个控制器（HomeKit/Alexa）互相覆盖 | ✅ 本轮：以设备 `DeviceName` 为唯一真值，本地只镜像 |
| 分组面板字体要够大 | Material 3 对话框正文用 `bodyMedium`、标题 `headlineSmall`，`labelSmall` 只用于辅助标签 | ✅ 本轮：标题/设备名/说明全部升一档 |
| 组内单台音量与组音量联动 | Sonos 官方 `docs/volume`：**用户调单台时组音量滑块要跟着动；但单台不跟随组事件** | ⏳ 候选（我们目前各管各的） |
| 点音量条展开"每房间一个滑块" | Sonos 社区/官方同款交互 | ⏳ 候选 |
| 设备列表里标"正在此设备播放" | Spotify Connect 的设备选择器 | ⏳ 候选（我们卡片上已有"在放"标签，可再强化） |
| 多选房间后一次性 Apply | AirPlay 2：控制中心 → 正在播放 → 扬声器图标 → 勾选多个房间 | ✅ 上轮已做（勾选式分组） |

## 五、验证

| 项 | 结果 |
|---|---|
| 单测 | **593 用例 0 失败**（上轮 574 + 19：UuidMatch 5 / ControlDeviceInfo 5 / CardMerge 6 / UpnpRendererClient 3） |
| 改名 | 真机 `.153`：下发 → 300ms `DeviceName=客厅` → 复原 `doss3` |
| 电量/充电 | 真机 `.153`：`battery_percent=40`、`battery=1` → `🔋40%`；`.164`：`100` / `battery=0` → `⚡100%` |
| 声道 | 真机 `.153`（**未组网**）：SetChannel 1/2/0 各 200，`CurrentChannel` 回读一致，`getPlayerStatus.ch` 同步 |
| 未验证 | 手机上的实际点击、组内从机声道的真机往返（需要先组网，会短暂断开 `.153`） |

## 六、下一轮候选（按性价比排）

1. **组音量与单台音量联动**（Sonos 官方规则，改动小、感知强）
2. **点音量条展开每台一个滑块**（组内 3 台以上才有价值）
3. **正在播放的设备置顶**
4. 从机声道的真机闭环（需要一次组网窗口）
