# 32 · 首页拖拽组网（原厂交互）

## 交互

在设备列表里**长按一张卡片 300ms 后拖动**，把它拖到另一张卡片上松手：

```
被拖的那台 = 从机      松手时悬停的那台 = 主机
```

方向不能反 —— 主机要维持热点（`ra0=10.10.10.254`）并持有组；从机接入主机 AP 后会掉到
`10.10.10.x`，从路由器侧再也直连不到。所以"谁是主机"必须由用户明确指定，
不能像一键组网那样默认用当前选中的设备。

视觉反馈三件套：

1. 拖起来的卡片跟手、放大 1.03 倍、带阴影，同时压暗到 0.85 让目标高亮更显眼；
2. 悬停到另一台上时，那台描一圈绿色边（`border` 不改布局尺寸，不会把下面的卡片推走）；
3. 松手后：进入组网进度（"等待从机出现… Ns / 40s"），最后给一句结果。

## 为什么长按不再是"移除记录"

长按原来绑的是移除设备。一个手势不能同时是两件事 —— 移除已移到
**设备设置页**（那里本来就有「移除这台设备」的确认对话框），手势归组网。

卡片顶部也加了一行说明（只在有 2 台以上时显示）：
「长按一张卡片拖到另一台上，松手即可组网（被拖到的那台当主机）」。

## 判据层（可测）

`core-protocol/multiroom/DragGrouping.kt`，纯函数，14 个单测。每一条拒绝都给一句人话
（否则用户在界面上只会看到"拖过去没反应"，分不清是手势没识别还是设备不允许）：

| 情况 | 提示 |
|---|---|
| 拖到自己身上 | 拖到自己身上了 |
| 目标是离线设备 | 「X」当前连不上，不能当主机 |
| 目标已经是别人的从机 | 「X」已经在「Y」的组里，先从那边移出再试 |
| 被拖的已在某个组里 | 「X」已在「Y」的组里 |
| 拖到空白处 | 没识别到目标音箱 |

命中判定用**中心点距离**（`hitTest`）而不是"必须完全覆盖"：手指盖住的面积远小于卡片，
要求完全覆盖会让用户觉得怎么拖都不中。半径 130dp —— 卡片高约 190dp、纵向间距约 200dp，
这个值能可靠命中相邻卡片，又不会把两条之外的吸过来。

### 一个实现坑（已避开）

卡片拖拽用 `graphicsLayer` 平移，而 `boundsInRoot()` **包含**这层平移。
如果拖拽期间继续上报中心点，指尖位置 = 中心 + 位移 就会重复累加，命中判定立刻错位。
所以拖拽开始时冻结自己的中心（`onGloballyPositioned` 里判 `!dragging` 才更新）。

## 命令层：与一键组网完全共用，且**逐字校验过**

拖拽只是换了"谁是主机"的入口，命令本身必须与原厂一致。这轮用 jadx 1.5.1 直接读原厂源码核了一遍
（`com/wifiaudio/a/b.java`）：

```java
// 组网（chext=1）—— ssid 与 pwd 都是 utils.a.b(bytes)，小写 hex，不转大写
"multiroom:ConnnectAP:" + slaveIp + ":ConnectMasterAp:ssid=" + hex(ap.ssid)
  + ":ch=" + ap.channel
  + ":auth=" + (pwd.length()!=0 ? "WPA2PSK" : "OPEN")
  + ":encry=" + (pwd.length()!=0 ? "AES" : "NONE")
  + ":pwd=" + hex(pwd) + ":chext=1"

// 普通配网（chext=0）—— **要** toUpperCase()
"...ConnectMasterAp:ssid=" + hex(ssid).toUpperCase() + ":ch=" + ch + ...
  + ":pwd=" + hex(pwd).toUpperCase() + ":chext=0"
```

`utils.a.b(byte[])` 是 `Integer.toHexString(b & 255)` 补零 —— **小写字母**；
并且**不会**把输入串本身转大小写。

新增 `JoinGroupCommandTest`（4 个用例）把这条命令逐字锁死，包括
「组网的 ssid 保持小写 hex，而普通配网要转大写」这个容易抄错的分支。
写测试时我第一版把 ssid 先 `lowercase()` 再取 hex，断言失败 —— 反而证明这条用例有价值。

## 当前两台设备的真实输入（只读实测）

```
.164（拟作主机）  ssid=DOSS DS-1831_400C  WifiChannel=11  ra0=10.10.10.254
                  getNetwork={"securemode":"0",}   slaves=0
                  → ssid hex 444f53532044532d313833315f34303043
                  → pwd  hex 7b227365637572656d6f6465223a2230222c7d

.153（拟作从机）  ssid=DOSS DS-1831_ADB4  WifiChannel=11  ra0=10.10.10.254
                  getNetwork={"securemode":"0",}   slaves=0
```

也就是说要下发的命令会是：

```
multiroom:ConnnectAP:192.168.99.164:ConnectMasterAp:ssid=444f53532044532d313833315f34303043:ch=11:auth=WPA2PSK:encry=AES:pwd=7b227365637572656d6f6465223a2230222c7d:chext=1
```

（发给**从机** `.153`，参数里放主机 IP 与主机热点信息。）

## 没有做的事（如实说明）

**没有真机执行这次组网。** 因为一旦成组，`.153` 会离开家里 Wi-Fi 掉进 `10.10.10.x`，
从路由器侧再也连不到 —— 而把从机弄回来目前只能靠原厂 App 或断电重启
（`SlaveKickout` 的真机参数格式我们还没验证过）。这属于"改网络状态"的动作，
不能替用户决定。

命令格式这一层已经用原厂源码 + 单测锁死；输入参数也已经用只读命令核对过。
要真机跑一次的话，说一句，我就用上面这条命令做「组网 → 读回 slave_list → 复位」的闭环。

## 改动

- 新增 `core-protocol/multiroom/DragGrouping.kt`（判据 + 命中）+ `DragGroupingTest`（14）
- 新增 `JoinGroupCommandTest`（4，逐字校验原厂命令）
- `ConsoleViewModel.groupByDrag()`：判据 → 现读主机 ssid → 下发 → 轮询 `slave_list` 确认
- `MainActivity`：`DragState` + `DraggableDeviceCard`（长按拖拽、跟手、目标高亮、松手成组）
- 长按移除改为设备设置页的「移除这台设备」（原本就有）
