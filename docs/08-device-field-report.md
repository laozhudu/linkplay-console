# 真机对接实录：DOSS DS-1831 @ 192.168.99.153

> 首次真机验证。此前所有结论都是协议层推断，这份文档把「实测通过」与「实测打脸」分开写。

## 0. 设备定位（先说一个坑）

你给的地址是 `192.168.99.164`，实测**这个地址上没有设备**：
ARP 解析失败（`ip neigh` → `FAILED`），全网段扫描也不在存活列表里。

真正在线的是 **`192.168.99.153`**，它的 MAC 是 `00:22:6c:b6:ad:b6` —— `00:22:6c` 正是 LinkPlay 的 OUI。
也就是说**音箱换了 DHCP 租约**。`getStatusEx` 里的 `MAC` 是 `00:22:6C:B6:AD:B4`、`STA_MAC` 是 `...:B6`。

**结论：按 IP 记设备是不可靠的，必须按 UUID 重发现。** 这是本轮最重要的工程教训（已写进 §5）。

## 1. 设备画像（`getStatusEx` 原样）

| 字段 | 值 | 说明 |
|---|---|---|
| `ssid` | `DOSS DS-1831_ADB4` | 型号 |
| `project` / `priv_prj` | `DS_1831` | 平台项目代号 |
| `firmware` | `3.6.5310` | 固件 |
| `Release` | `20180110` | 2018-01-10 构建（比手上 APK 的 2015 版晚 3 年） |
| `hardware` | `A31` | 全志 A31 |
| `mcu_ver` | `126` | MCU 版本 |
| `preset_key` | `6` | 面板 6 个预设键，与 docs/05 的推断一致 |
| `battery` / `battery_percent` | `1` / `60` | **电池供电的便携机** |
| `essid` | `4D594444` | HEX → `MYDD`（我们的解码逻辑正确） |
| `apcli0` | `192.168.99.153` | 官方文档语义：这是 **IPv4**，不是 MAC（docs/01 §11 的校正得到印证） |
| `uart_pass_port` / `communication_port` | `8899` / `8819` | 两个私有端口，实测**都在监听** |
| `upnp_version` | `1004` | |

## 2. 网络面

| 项目 | 实测结果 |
|---|---|
| TCP 开放端口 | `80`(GoAhead-Webs)、`8899`、`8819`、`49152`(UPnP) |
| SSDP | ✅ 组播与**单播** M-SEARCH 都回；`LOCATION: http://192.168.99.153:49152/description.xml` |
| SSDP ST 响应 | MediaRenderer、`upnp:rootdevice`、**`urn:schemas-wiimu-com:service:PlayQueue:1`** 均直接回 |

单播 M-SEARCH 可用这点很关键：**跨网段/VPN 时不需要组播也能发现**（见 docs/09）。

## 3. 服务清单（`description.xml` 实读）

| 服务 | controlURL |
|---|---|
| `urn:schemas-upnp-org:service:AVTransport:1` | `/upnp/control/rendertransport1` |
| `urn:schemas-upnp-org:service:ConnectionManager:1` | `/upnp/control/renderconnmgr1` |
| `urn:schemas-upnp-org:service:RenderingControl:1` | `/upnp/control/rendercontrol1` |
| `urn:schemas-wiimu-com:service:PlayQueue:1` | `/upnp/control/PlayQueue1` |
| **`urn:schemas-tencent-com:service:QPlay:1`** | `/upnp/control/QPlay1` |

friendlyName `doss3`、manufacturer `DOSS 1010`、modelName `DOSS 1010 Player`。

**新发现：这台设备原生带 QPlay 服务** —— 也就是「QQ 音乐推歌到音箱」是**设备侧能力**，
和 docs/05 里「原 App 没有 QQ 音乐」并不矛盾：App 不需要实现，音箱自己支持。

## 4. 实测通过的能力（可写进对接清单）

| 能力 | 实测证据 |
|---|---|
| `getStatusEx` | 返回完整 JSON（§1） |
| 音量/静音/声道 | `GetVolume=50`（我们改成 25 又读回 25）、`GetMute=0`、`GetChannel=0` |
| 传输状态 | `GetTransportInfo` → `PAUSED_PLAYBACK` → 播放后 `PLAYING` |
| **单曲投流（核心）** | `SetAVTransportURI` + `Play` 一条我们自建 HTTP 服务上的 mp3 → 设备真的来拉流（服务端日志：`192.168.99.153 GET /tone1.mp3 200`），`GetPositionInfo` 返回 `TrackDuration=00:00:06`、`RelTime` 递增、`PlayMedium=THIRD-DLNA` |
| Range 支持必要性 | 我们的服务同时回了 `206 Partial Content`，设备接受 |
| 队列读回 | `GetQueueLoopMode` → `0`；`GetQueueIndex` 在队列存在时返回 `1` |

> 这条投流是我们第一次证明「手机当文件服务器 + 音箱拉流」的整套设计在真机上成立。

## 5. 实测打脸 / 未解项（必须诚实列出）

### 5.1 PlayQueue 的动作比 APK 多得多

真机 SCPD 有 **27 个动作**，2015 APK 只反推出 10 个。多出来的包括：

`ReplaceQueue`、`AppendQueue`、`GetQueueLoopMode`、`SetQueuePolicy`、`SetKeyMapping` / `GetKeyMapping`、
`GetQueueOnline` / `SearchQueueOnline`、`SetQueueRecord` / `SetSongsRecord`、
**账号族**（`UserRegister` / `UserLogin` / `UserLogout` / `GetUserInfo` / `GetUserAccountHistory` /
`SetUserFavorites` / `GetUserFavorites`）、`DeleteActionQueue`。

→ 「从旧 APK 反推协议」会系统性低估固件能力。已把新代码里能用的几个补上。

### 5.2 `QueueContext` 不是 DIDL —— 是自定义 `<PlayList>` 方言

实测（每一条都是真机返回）：

| 传入 | 结果 |
|---|---|
| DIDL-Lite（单/多 item） | ❌ `404 ParseXmltoPlayList error` |
| 100+ 种 DIDL/JSON/URL/m3u/属性式变体 | ❌ 同样报错 |
| `<PlayList><ListName>x</ListName></PlayList>` | ❌ 报错 |
| **`<PlayList><ListName>Douban</ListName><ListInfo><SearchUrl>…</SearchUrl><TrackNumber>0</TrackNumber><Quality>0</Quality><UpdateTime>0</UpdateTime><AutoGenerate>1</AutoGenerate><StationLimit>10</StationLimit></ListInfo></PlayList>`** | ✅ **200 OK** |

成功之后 `BrowseQueue(QueueName=Douban)` 让**设备自己把队列序列化回来**，这就是权威格式：

```xml
<?xml version="1.0"?>
<PlayList>
<ListName>Douban</ListName>
<ListInfo>
<SearchUrl>http://www.douban.com</SearchUrl><MarkSearch>0</MarkSearch>
<TrackNumber>0</TrackNumber><Quality>0</Quality><UpdateTime>0</UpdateTime>
<LastPlayIndex>1</LastPlayIndex><RealIndex>0</RealIndex>
<SwitchPageMode>0</SwitchPageMode><CurrentPage>0</CurrentPage><TotalPages>0</TotalPages>
<searching>0</searching><PressType>0</PressType><Volume>0</Volume>
</ListInfo>
<Tracks></Tracks>
</PlayList>
```

**关键限制**：`<Tracks>` 里只要放任何内容（`Track`/`Item`/`Song`/`Url`/`MediaUrl`/`FileUrl`/
`SongUrl`/纯文本 URL/`TrackInfo`/`res`/`Media` 共 12 种候选），设备一律 `404 ParseXmltoPlayList error`；
空 `<Tracks/>` 才接受。

→ 判断：**这一版固件把 `CreateQueue` 当作「在线源描述符」入口**（靠 `SearchUrl` 让设备自己去拉），
**不能**用它推一份本地文件列表。要推本地列表需要更高版本固件或 QPlay 通道。

代码侧已把格式固化成 `PlayListContext`（含 13 个 ListInfo 字段），并在注释里写明这个限制——
不假装它能推列表。

### 5.3 QPlay 通道存在但被认证挡住

| 动作 | 实测 |
|---|---|
| `GetMaxTracks` | ✅ 200（队列上限 **200** 首） |
| `GetTracksCount` | 返回**空响应体** → 无曲目/未认证 |
| `InsertTracks(QueueID, StartingIndex, TracksMetaData)` | HTTP 200 但**响应体为空**，写入被静默忽略 |
| `QPlayAuth` | 需要腾讯下发的种子，第三方拿不到 |

→ QPlay 对第三方客户端**等于关闭**。它存在的意义是让 QQ 音乐这类合作方推歌。
本项目的 `QPlayClient` 只做「探测 + 说清为什么不能用」，不做任何绕过认证的尝试。

### 5.4 多 item DIDL 不会变成队列

`SetAVTransportURI` 传 3 条 item 的 DIDL：`GetMediaInfo` 的 `NrTracks` 仍是 **1**，
`Next` 之后 `TrackURI` 不变。→ **标准 DLNA 这条路拿不到列表**，与规范预期一致。

### 5.5 其它实测到但未深入的东西

- `RenderingControl` 里还有 `MultiPlaySlaveMask`、`SetAlarmQueue`/`GetAlarmQueue`/`DeleteAlarmQueue`、
  `SetDeviceName`、`GetSimpleDeviceInfo`、`GetControlDeviceInfo` —— 说明**闹钟**是真的走这条服务，
  不是靠 App 定时器。
- `AVTransport` 有自定义动作 `GetPlayType`、`GetInfoEx`、`SetPlayMode`（未深挖）。
- 端口 `8899`、`8819` 在监听，对应 `uart_pass_port` / `communication_port`（可能是 MCU 直通与内部通信，未触碰）。

## 6. 对 App 的直接影响

| 问题 | 现状 | 需要的改动 |
|---|---|---|
| **IP 会变**（.164 → .153） | 我们按 IP 记设备 | 按 `MAC`/`uuid` 重发现：SSDP 广播里就带 `USN: uuid:…`，重连时用 UUID 匹配 |
| 整列表推送 | 试遍格式不可行（§5.2） | UI 要如实降级：本固件只能单曲；或改用「客户端排队」（当前曲目快结束时推下一首） |
| QPlay | 不可用（§5.3） | UI 不提供入口 |
| 单曲投流 + 本地 HTTP 服务 | ✅ 已验证 | 这是本机最可靠的播放路径 |

## 7. 复现脚本

真机探测脚本都在 `/root/.hermes/cache/scratch/`（一次性工具，未入库）：

| 脚本 | 作用 |
|---|---|
| `dev153_soap.py` | 通用 SOAP 调用（`AVT`/`RC`/`PQ`/`QPLAY`/`CM` 五套服务） |
| `dev153_queue_probe.py` | QueueContext 格式首批试探 |
| `dev153_ctx_fuzz.py` / `dev153_fuzz2.py` / `dev153_playlist_fuzz.py` | 100+ 候选格式穷举 |
| `dev153_tracks_fuzz.py` | `<Tracks>` 内曲目节点穷举（12 种） |
| `dev153_qplay.py` | QPlay 通道验证 |
| `range_server.py` | 带 Range 的临时媒体服务（验证投流） |

复现一次核心结论：

```bash
# 1) 设备真的在拉我们的流
curl -sS "http://192.168.99.153:49152/description.xml" | grep -o '<serviceType>[^<]*'
# 2) 设备自己序列化的队列（先建一个能被接受的队列）
python3 dev153_soap.py PQ CreateQueue 'QueueContext=<?xml version="1.0"?><PlayList>…'
python3 dev153_soap.py PQ BrowseQueue QueueName=Douban
```

## 8. 下一步（按价值排序）

1. **按 UUID 重发现**（解决 IP 漂移，成本最低、收益最高）
2. **客户端排队**作为整列表的降级实现：单曲投流 + 位置轮询到末尾前 3 秒推下一首（已具备全部零件）
3. 若你能拿到**这台音箱对应的新版 DOSS App**，我可以再逆向一次 —— 它很可能用 `GetQueueOnline`
   或其它通道实现了真正的列表推送，比继续盲猜格式高效得多
4. 闹钟（`SetAlarmQueue` 已确认存在）、灯效（端口 8899 可能是 MCU 通道）
