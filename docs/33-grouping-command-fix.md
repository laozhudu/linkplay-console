# 33 · 组网为什么一直不成功：命令根本发错了

## 结论先说

之前所有"组网不成功"的尝试，问题**不在轮询、不在超时、不在密码格式**，而是
**发的是另一条命令**。原厂在两种场景下用两条不同的 `ConnectMasterAp`：

| 场景 | 命令 | 前缀 | hex | chext |
|---|---|---|---|---|
| **两台独立设备组网**（拖拽） | `ConnectMasterAp:ssid=…:ch=…:auth=…:encry=…:pwd=…:chext=0` | **无** | **大写** | 0 |
| 把一个**已在组里**的从机搬到别的主机 | `multiroom:ConnnectAP:<ip>:ConnectMasterAp:…:chext=1` | `multiroom:` | 小写 | 1 |

我们一直发的是第二条（因为当初只反编译到三参重载 `b.a(g, g2, g3)`），
设备收到"搬迁一条不存在的从机"，回 `OK` 但什么也不做 —— 这就是 40 秒轮询必然超时的原因。

## 真机验证（2026-11-03，本轮实做）

```
# 发给从机 .153，参数里放主机 .164 的热点信息
ConnectMasterAp:ssid=444F53532044532D313833315F34303043:ch=11:auth=OPEN:encry=NONE:pwd=:chext=0

t+2s  {"slaves": 0}
t+4s  {"slaves": 0}
t+6s  {"slaves": 0}
t+8s  {"slaves": 1, "slave_list":[{"name":"doss3","ssid":"DOSS DS-1831_ADB4","mask":0,
       "volume":62,"mute":0,"channel":0,"battery":60,"ip":"10.10.10.92",...}]}
```

**8 秒组上**，从机落到 `10.10.10.92`。

顺手把之前没验过的三条也一次做实：

```
multiroom:SlaveChannel:10.10.10.92:1  → OK → 回读 channel=1
multiroom:SlaveChannel:10.10.10.92:2  → OK → 回读 channel=2
multiroom:SlaveChannel:10.10.10.92:0  → OK → 回读 channel=0
multiroom:SlaveVolume:10.10.10.92:30  → OK → 回读 volume=30
AVTransport.Play（主机）              → 200 → status=play
AVTransport.Pause（主机）             → 200 → status=pause
multiroom:SlaveKickout:10.10.10.92    → OK → 3 秒后 master slaves=0，.153 回到 192.168.99.153
```

**测试后已完整复原**：`.153` 回到家里 Wi-Fi，主机 `slaves=0`。

## 密码解析也顺带纠正了

上一版把 `getNetwork` 的整段 JSON 当密码（`auth=WPA2PSK` + JSON 的 hex）。读了
`com/wifiaudio/a/x.java` 的 `a(String)` 才知道原厂是**解析**：

```java
String[] split = str.split(",");
if (split[0].substring(split[0].lastIndexOf(":")+1).replace("\"","").equals("1")) {
    str2 = split[1]...                      // securemode=1 时取密码
} else {
    str2 = "";                              // securemode != 1 → 空串 → auth=OPEN
}
```

所以 `{"securemode":"0",}` 的正确结果是**空密码**。这次实测用 `auth=OPEN:encry=NONE:pwd=`
就是成功的配置，与源码一致。

（记忆中"WPA2PSK + 整段 JSON 能组上"那条结论已作废 —— 那次其实是用户用原厂 App 完成的组网。）

## 本轮改动

### 协议层
- `MultiroomClient.joinGroup()` 改为真实命令（无前缀、大写 hex、`chext=0`）
- 新增 `MultiroomClient.moveExistingSlave()`（保留原来那条 `chext=1` 的，语义标注清楚，UI 不用）
- 新增 `MultiroomClient.pairingSecret()`：原厂 `x.a(String)` 的解析，纯函数
- 新增 `hexUpper()`；`hexLower()` 保留给搬迁命令

### 交互层
- **声道可点**：卡片右下角 `LR / L / R` 胶囊点一下循环，立刻下发
  `multiroom:SlaveChannel` 并回读确认。主机点了提示"主机是左右两个单元，不单独设声道"；
  不在组里的设备点了提示"声道只能对组内从机设置"。
- **「全部播放」真正播放**：优先 `AVTransport.Play`，失败退 `setPlayerCmd:play`；
  设备没队列时从本地快照恢复整份列表（和设备侧同一份真相），恢复不了才提示去选歌。
- **新增「全部暂停」**：组内一起停（`AVTransport.Pause`，兜底 httpapi）。
- **迷你播放条加了播放/暂停**：从首页按「全部播放」之后不必再进播放页才能停。
  状态只读 `DeviceCard`/`QueueBus`，不本地存，不会两处不一致。
- **删掉「如何在多音箱同步和独立播放间切换？」**（用户要求：有了拖拽，这行既不是操作也不是链接）
- **删除多房间卡片**（`MultiroomCard.kt` 整个文件删除）：拖拽已经能组网，
  再放一张"组起来"的卡片就是两套入口；组内状态已经画在各自的设备卡片上。

## 测试

新增 `PairingSecretTest`（6）；`JoinGroupCommandTest` 重写为 6 条 —— 每条对应一个
曾经导致组不上的错误（多 `multiroom:` 前缀、小写 hex、`chext=1`、JSON 当密码）。
`MultiroomChannelTest` / `MultiroomJoinTest` 的断言同步到新命令。

全量 **435 用例 0 失败**。

## 还没做的

拖拽手势本身只有代码正确性保证（编译 + 判据单测），**没有在手机上实测过手感**。
命令链路已经全部真机验证；如果拖拽松手后仍失败，问题会落在"命中判定/松手事件"这一层，
届时看日志里的 `拖拽组网：xxx → 主机 xxx` 有没有打出来即可定位。
