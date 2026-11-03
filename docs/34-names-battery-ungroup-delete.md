# 34 · 名称同步 / 电量 / 解除组网 / 删除设备

四项都按原厂口径做了，其中**改名一直"改不掉"是本轮查出的真 bug**。

## 一、改名改不掉的根因：`touch()` 每次探活都用 SSID 覆盖名字

```kotlin
// 改之前
name = status?.ssid?.takeIf { it.isNotBlank() } ?: existing.name,
```

`setDeviceName` 改的是 **UPnP friendlyName**，而 `getStatusEx.ssid` 是**设备热点名**
（`DOSS DS-1831_400C`）。所以：改名命令下发了、设备也改了，但只要探活一次，
本地记录就被热点名覆盖回去 —— 表现就是"改名没生效 / 刷新后又变回去"。

改成从 UPnP 描述里取 `friendlyName` 同步（原厂也是这个口径）：

```kotlin
val friendly = UpnpRendererClient.describe(loc).friendlyName?.takeIf { it.isNotBlank() }
name = friendly ?: existing.name      // 读不到就保留原值，绝不用 SSID 顶替
```

真机验证（`.164`）：

```
before:            doss1
setDeviceName:doss1test → OK
description.xml    → doss1test     ← friendlyName 立刻跟着变
setDeviceName:doss1 → OK
description.xml    → doss1         ← 已复原
```

### 从机改名走另一条命令

组内从机在主机 AP 网段（`10.10.10.x`），我们连不上，只能让主机转达：

```
GET http://<主机IP>/httpapi.asp?command=multiroom:SlaveDeviceName:<从机IP>:<URL编码名字>
```

`renameCard()` 里按"是不是从机"自动选通道，改完**回读确认**（主机 `slave_list` /
设备 `friendlyName`），而不是信命令回 OK。

## 二、电量（原厂卡片上一直有，我们之前完全没读）

两种来源，都要读：

| 设备身份 | 来源 | 真机值 |
|---|---|---|
| 独立设备 / 组内主机 | `getStatusEx.battery_percent` | `"100"`（**是字符串**，不是数字） |
| 组内从机 | `multiroom:getSlaveList[].battery` | `60` / `70` |

卡片第二行右侧显示 `🔋100%`。

写解析时先踩了一脚：`Json.int()` 只认 `Num`，而设备给的是字符串 `"100"`，
第一次单测就红了（`BatteryParsingTest.battery percent comes from getStatusEx`）。
现在两种都认；"设备没报"仍然是 `null`，不会显示成 0%。

## 三、解除组网（真机验证过）

```
GET http://<主机IP>/httpapi.asp?command=multiroom:SlaveKickout:<从机IP>
```

- 从机卡片上出现「解除」按钮 → 只踢这一台；
- 主机卡片上点「解除」→ 把组内所有从机各踢一次（解散整组）；
- 按钮判定用的是**主机自报的 `10.10.10.x`**，不是保存记录里的旧 IP（旧 IP 在组里已经不通）。

真机（`.164` 主机 + `10.10.10.92` 从机）：`SlaveKickout` → OK → **3 秒后**主机
`slaves=0`，从机回到 `192.168.99.153`。所以 UI 踢完等 3.5 秒再重新探活，
否则会先显示"离线"（从机正在重连 Wi-Fi）。

## 四、删除设备

`forget()` 之前只删存储和在线状态，**卡片状态、组内归属、选中态都留着**：
删完列表里没了，但组里还挂着幽灵设备，播放页也可能还对着已删的设备。

现在一并清掉：`cards`、`groupedUnder`、以及"删的正好是当前选中设备"时的
`selectedIp/selectedUuid/selectedLocation`。

入口也补了：长按原来是"移除"，改成拖拽组网后没有入口了 ——
现在**齿轮**点开就是这台设备的操作菜单：

```
重命名
删除这台音箱
设备信息 / 设置
```

（这与原厂长按卡片弹出的菜单对应，只是手势换成了齿轮 —— 一个手势不能既是拖拽又是菜单。）

## 五、验证

| 项 | 结果 |
|---|---|
| 改名 round-trip（`.164`） | `doss1 → doss1test → doss1`，`friendlyName` 每次都跟着变，已复原 |
| 电量 | `getStatusEx.battery_percent="100"`、`slave_list[].battery=60` 均解析成功 |
| 解除组网 | `SlaveKickout` → 3 秒内 `slaves=0`、从机回原 IP（上一轮实测，本轮未重复踢） |
| 删除 | 只删本机记录，不动音箱；状态一并清理 |
| 单测 | 新增 `BatteryParsingTest`（3）；全量 **438 用例 0 失败** |

## 六、没做的 / 注意

- **从机改名没有真机验证**（要先组网，而每次组网/解除都会短暂断掉 `.153` 的网络）。
  命令格式取自原厂 `com/wifiaudio/a/v.java` 的 `SlaveDeviceName:<ip>:<urlencode(name)>`，
  与我们已有的 `LinkPlayCommands.slaveDeviceName` 一致。
- 电量是**探活时读一次**（3 秒心跳只读 `getPlayerStatus`，那个接口没有电量）。
  如果要做"电量实时下降"，得把 `getStatusEx` 也塞进心跳 —— 老固件多一条请求就多一份压力，
  目前先按 3 秒级的"进入页面时刷新"处理。
