# LinkPlay / WiiMu 音箱控制协议规格

> 本文是从一个 2015 年的厂商 App（包名 `com.wifiaudio.doss`，ver1.6.5）静态分析后
> **重新表述**的协议事实，用于 clean-room 重写。文中不含反编译源码，所有命令、字段、
> 编码规则均为接口层面的客观描述。
>
> 标记约定：
> - ✅ **已确证**：在原厂 App 的字节码/字符串中直接读到，或在其解析代码中被引用。
> - ⚠️ **推断**：由命令家族的一致性推出，未在本样本中被调用；实机使用前请先验证。
>
> 复现方式见仓库根目录 README「证据与复现」。

---

## 1. 设备与网络模型

| 项 | 值 |
|---|---|
| 厂商平台 | LinkPlay 方案（WiiMu 白标），设备 SSDP 描述中 `manufacturer=WiiMu`、`modelName=WiiMu Media player` |
| 默认 AP SSID | `LinkPlay_<MAC 后 4 位>`（描述模板里为 `LinkPlay_32C8`） |
| 控制通道 A | HTTP，`GET http://<ip>/httpapi.asp?command=<cmd>`，**明文、无鉴权、无 token** ✅ |
| 控制通道 B | UPnP/DLNA（AVTransport + RenderingControl + 厂商私有 PlayQueue）✅ |
| 控制通道 C | 私有 TCP 文本协议（MCU，用于灯效/按键，见 §5）✅ |
| 发现 | SSDP 组播 `239.255.255.250:1900` ✅ |
| 默认超时 | 原厂对 httpapi 用 15 s，配网类命令 8 s ✅ |
| 请求头 | 固定 `Accept-Language: zh-cn` 或 `en-us` ✅ |

### 典型交互顺序

```
SSDP M-SEARCH ──► 拿到 device ip / LOCATION
      │
      ├─ GET /httpapi.asp?command=getStatusEx        → 设备身份、固件、联网状态
      ├─ GET /httpapi.asp?command=multiroom:getSlaveList → 多房间成员
      ├─ UPnP AVTransport / RenderingControl         → 播放、音量、进度
      └─ 需要改网络/改名时走 httpapi setXxx
```

## 2. HTTP 控制面（`httpapi.asp`）

全部为 `GET`，命令直接拼在 query 上，返回值多为 JSON 或纯文本，**无统一错误码**，
失败通常表现为连接超时或返回 `OK`/空串。客户端必须自己做超时与重发策略。

### 2.1 设备信息

| 命令 | 返回 | 说明 |
|---|---|---|
| `getStatusEx` | JSON（见 §2.2） | 设备身份与联网状态，最常用的探活命令 ✅ |
| `getNetwork` | 文本键值 | 当前 Wi-Fi / 有线状态 ✅ |
| `wlanGetApListEx` | 文本，逐行 AP | 扫描周边 AP，配网前用 ✅ |
| `StartCheck` | 文本 | 触发设备自检/连通性检查 ✅ |
| `restoreToDefault` | — | **恢复出厂**，清空 Wi-Fi 配置 ⚠️（危险） ✅ |

### 2.2 `getStatusEx` 返回字段（✅ 已确证）

```json
{
  "ssid": "LinkPlay_32C8",          // 设备自身 AP/名称
  "hardware": "A31",                // 硬件平台标识
  "firmware": "4.2.8820",           // 固件版本
  "uuid": "FF9607407DD495DAF88EA665",
  "eth2": "00:22:6C:32:C8:00",      // 有线 MAC
  "apcli0": "00:22:6C:32:C8:01",    // 无线 MAC
  "essid": "6c696e6b706c6179",      // 上游 Wi-Fi SSID，可能是 hex（见 §6）
  "expired": "0",
  "internet": 1,                     // 1 = 已联网
  "netstat": 1,
  "language": "zh_CN",
  "Release": "20190101",
  "build": "a31-2019",
  "securemode": "2"
}
```

`essid` 为 hex 时需解码（`6c696e6b706c6179` → `linkplay`）。`internet`/`netstat` 是整数，
其它字段基本是字符串（`expired` 也可能是字符串 `"0"`，解析时两种都要容忍）。

### 2.3 设备设置

| 命令 | 参数 | 编码 |
|---|---|---|
| `setDeviceName:<name>` | 新设备名 | **URL 编码**（`Living Room` → `Living+Room`）✅ |
| `setLanguage:<lang>` | 如 `zh_CN` | 原样 ✅ |
| `setPlayerCmd:switchmode:<mode>` | 输入源，如 `wifi` / `line-in` / `bluetooth` / `optical` | 原样 ✅ |
| `MCUKeyShortClick:<n>` | 模拟面板第 n 个按键 | 整数 ✅ |
| `setNetwork:<dhcp 0\|1>:<payload>` | 设置网络 | 原厂把第二参直接拼在冒号后，payload 语义未确证 ⚠️ |

## 3. 配网

LinkPlay 有三条配网路径，同一份 App 里全部存在：

### 3.1 AP 直连（推荐，最可靠）

手机先连设备自带热点 `LinkPlay_xxxx`，然后：

```
GET /httpapi.asp?command=ConnectMasterAp:ssid=<SSID_HEX_UPPER>:ch=<channel>:auth=<WPA2PSK|OPEN>:encry=<AES|NONE>:pwd=<PWD_HEX_UPPER>:chext=0
```

- SSID 与密码用 **大写 hex**（UTF-8 字节，见 §6）。
- 密码为空 → `auth=OPEN`、`encry=NONE`、`pwd=` 留空。
- `chext` 在直连场景为 `0`，在多房间从机跟随场景为 `1`。

#### 3.1.1 扫描周边 AP：`wlanGetApListEx`

返回值（✅ 已确证，字段与类型均来自原厂解析代码）：

```json
{"aplist":[
  {"ssid":"Home","bssid":"aa:bb:cc:dd:ee:ff","rssi":-47,"channel":6,
   "auth":"WPA2PSK","encry":"AES","extch":0}
]}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `ssid` | string | 网络名，可能是非 ASCII |
| `bssid` | string | AP 的 MAC |
| `rssi` | int | 信号强度，负值 dBm |
| `channel` | int | 信道号 |
| `auth` | string | `WPA2PSK` / `OPEN` / … |
| `encry` | string | `AES` / `NONE` / … |
| `extch` | int | 扩展信道（40MHz 绑定用） |

设备侧扫描较慢，客户端读超时应放宽（本项目用 25 s，普通命令是 15 s）。
注意根键是 `aplist`，**不是** `slave_list`。

#### 3.1.2 下发之后必须自己确认（重要）

`ConnectMasterAp` 的响应**只有 `OK` 或空串**，没有任何结构化结果；原厂 App 收到后
仅打印一行 log，**从不校验是否真的连上**。因此重写时不能照抄这一点，必须：

```
下发 ConnectMasterAp  →  轮询 getStatusEx  →  判断 essid == 目标 SSID 且 internet == 1
```

判定规则（本项目实现，见 `ProvisioningPoller`）：

| 观测 | 结论 |
|---|---|
| `essid` == 目标 且 `internet == 1` | 配网成功 |
| `essid` == 目标 但 `internet != 1` | 已切网但没拿到 IP/外网（多为密码错或 DHCP 问题） |
| `essid` 一直不是目标，直到超时 | 超时（带最后一次观测状态） |
| 连续多次探测失败 | 设备失联（**不一定是失败**：切网后原 AP 地址会失效） |

关键细节：设备切到 STA 模式后，原来 AP 模式那个地址立刻不可达，所以**探测异常不能
直接判失败**，要容忍连续若干次失败（本项目允许 4 次），否则会把「其实成功了」误报成失败。

### 3.2 已联网设备改连另一个 AP

```
GET /httpapi.asp?command=wlanConnectApEx:ssid=<SSID_HEX_LOWER>:ch=<ch>:auth=<AUTH>:encry=<ENCRY>:pwd=<PWD_HEX_LOWER>:chext=1
```

注意这里原厂用的是 **小写 hex**（与 3.1 不一致，是历史遗留差异）。

### 3.3 多房间从机跟随主机

```
GET /httpapi.asp?command=multiroom:ConnnectAP:<master_ip>:ConnectMasterAp:ssid=<SSID_HEX_LOWER>:ch=<ch>:auth=<AUTH>:encry=<ENCRY>:pwd=<PWD_HEX_LOWER>:chext=1
```

`ConnnectAP` 里的三个 n 是原厂拼写错误，**必须原样保留**，改成 `ConnectAP` 会失败。

### 3.4 EasyLink（声波/组播广播配网，兼容用）

- 实现于厂商 SDK 的 `smartlinkver2` 模块：把 SSID+密码编码后周期性组播/广播发出，
  设备在配网模式下监听并自行连网。
- 配网结束后需要通知设备停止应答：`GET /httpapi.asp?command=EasyLinkResponseStop` ✅
- 这条路径依赖 32 位 native 库（`libEasylinkControllerVer2.so`，仅 armeabi），
  重写时代价高、收益低，**建议只把 AP 直连作为主路径**，EasyLink 留作可选。

## 4. 多房间（multiroom）

| 命令 | 说明 |
|---|---|
| `multiroom:getSlaveList` | 返回 `{"slave_list":[...]}`，见下 ✅ |
| `multiroom:SlaveVolume:<ip>:<0-100>` | 设某从机音量 ✅ |
| `multiroom:SlaveChannel:<ip>:<ch>` | 设某从机声道（左右） ✅ |
| `multiroom:SlaveDeviceName:<ip>:<urlenc name>` | 从机改名（URL 编码） ✅ |
| `multiroom:SlaveMask:<ip|ip...>` | 把从机加入本机群组 ✅ |
| `multiroom:SlaveUnMask:<ip|ip...>` | 从群组移除（保留从机） ✅ |
| `multiroom:SlaveKickout:<ip>` | 把从机踢出群组 ✅ |

`getSlaveList` 单条元素：

```json
{"ip":"192.168.1.21","mask":"1","version":"4.2.8820","uuid":"...","name":"Kitchen","ssid":"Home","volume":30}
```

- `mask` 是**字符串** `"0"/"1"`：`1` 表示已加入本机群组。
- `volume` 是整数 0-100。
- 群组是「主从」模型：所有编组命令都发给主机，主机再下发给从机。

## 5. 私有 MCU 文本协议（灯效 / 按键）

设备另开一条 TCP 通道（原厂 NIO `SocketChannel`，读超时 20 s），收发**纯 ASCII 文本帧**，
帧以 `&` 结尾，字段以 `+` 分隔，命令空间前缀 `MCU+PAS+`：

| 帧 | 方向 | 含义 |
|---|---|---|
| `MCU+PAS+light-get&` | 客户端 → 设备 | 查询当前灯效 ✅ |
| `MCU+PAS+LWD+DAF+000&` | 客户端 → 设备 | 写入灯效参数（DAF 动画/亮度域） ✅ |
| `MCU+PAS+LWD+TIM+<...>&` | 客户端 → 设备 | 灯效定时参数 ✅ |
| `MCU+PAS+LIGHT-<n>&` | 设备 → 客户端 | 亮度回报（0-100 量级） ✅ |
| `MCU+PAS+LIGHTSTYLE-<s>&` | 设备 → 客户端 | 灯效样式回报 ✅ |

同一份 App 内按 OEM 分了三套 MCU 实现（厂商代号 `jabees`、`loogwood`、`youzhuan`），
帧格式相同但上报字段集合不同，重写时建议做成「厂商 profile + 通用帧解析」。

## 6. 编码规则（最容易踩的坑）

| 字段 | 编码 | 例 |
|---|---|---|
| `ConnectMasterAp` 的 ssid/pwd | UTF-8 → **大写** hex | `Home` → `484F4D45` |
| `wlanConnectApEx` / `multiroom:ConnnectAP` 的 ssid/pwd | UTF-8 → **小写** hex | `Home` → `486f6d65` |
| `setDeviceName` / `SlaveDeviceName` | **URL 编码**（空格变 `+`） | `Living Room` → `Living+Room` |
| `getStatusEx.essid` | 可能是 hex，需尝试解码后展示 | `6c696e...` → `linkplay` |
| 非 ASCII SSID | 按 UTF-8 字节走 hex | `客厅` → `e5aea2e58e85` |

hex 不是 Base64——原厂实现是逐字节 `toHexString(b & 0xFF)`、不足两位补 0。

## 7. UPnP / DLNA 面

设备声明为 `MediaRenderer:1`，服务清单：

| 服务 | controlURL | 用途 |
|---|---|---|
| `urn:schemas-upnp-org:service:AVTransport:1` | `/upnp/control/rendertransport1` | 播放/暂停/停止/Seek/Next/Previous、`SetAVTransportURI` 投流 |
| `urn:schemas-upnp-org:service:ConnectionManager:1` | `/upnp/control/renderconnmgr1` | 能力协商、协议信息 |
| `urn:schemas-upnp-org:service:RenderingControl:1` | `/upnp/control/rendercontrol1` | 音量、静音、EQ |
| `urn:schemas-wiimu-com:service:PlayQueue:1` | `/upnp/control/PlayQueue1` | 厂商私有播放队列 |

事件订阅走 `/upnp/event/...`（GENA），原厂 App 用 Cling 的 subscription 回调同步
「当前音量/播放状态」。**播放控制只有这条路径**——`httpapi.asp` 里没有
`setPlayerCmd:play/pause/vol`，`setPlayerCmd` 只用于 `switchmode`（输入源）。

### 7.1 控制点必须实现的 SOAP 细节

- `POST` 到 `controlURL`，头必须带 `SOAPACTION: "<serviceType>#<action>"`（含引号），
  body 是 SOAP 1.1 信封，参数名大小写敏感（`InstanceID`、`Channel`、`DesiredVolume`…）。
- `controlURL` 是**相对路径**，必须按 SSDP 的 `LOCATION` 解析成绝对地址，
  否则请求会打到错误的主机/端口（这批设备常在 49152 端口）。
- `Seek` 的 `Target` 是 `H:MM:SS` 文本，不是秒数；`REL_TIME` 表示按绝对时间跳。
- 失败时 HTTP 是 500，body 里带 `<errorCode>`（如 `701 Transition not available`），
  不要只看状态码。
- `SetAVTransportURI` 的 `CurrentURIMetaData` 建议给最小 DIDL-Lite（`dc:title` +
  `upnp:class` + `res protocolInfo`），部分固件缺这两项就拒播。

### 7.2 本项目为什么不做 GENA 订阅

GENA 要求控制点在手机上跑一个可被设备回调的 HTTP 服务（SUBSCRIBE → NOTIFY）。
代价：常驻监听端口、移动网络/休眠下回调丢失、设备重连后要重订阅、无法在纯 JVM 层单测。
收益仅是「状态变化推送更快」。本项目选**每秒轮询** `GetTransportInfo` + `GetPositionInfo`：
行为可预测、无后台服务、纯函数可测，嵌入式 renderer 上这点开销可忽略。
副作用：UI 上看到的状态最多滞后约 1 秒。

## 8. 固件升级（音箱侧）

```
GET /httpapi.asp?command=getMvRemoteUpdateStart     → 触发检查并开始下载
GET /httpapi.asp?command=getMvRemoteUpdateStatus    → 检查/下载状态
GET /httpapi.asp?command=getMvRomDownloadStatus     → 下载进度
GET /httpapi.asp?command=getMvRomBurnPrecent        → 烧写进度（原厂拼写 PerCent 错误，原样保留）
```

这是**音箱固件**的 OTA，与 App 自升级无关。轮询间隔原厂未固定，建议 1-2 s 并在
`getMvRomBurnPrecent` 到 100 后停止，避免刷写期间密集请求。

## 9. 安全事实（重写时必须知道的约束）

- `httpapi.asp` **完全无鉴权**：同一局域网内任何主机都能 `restoreToDefault`、
  `SlaveKickout`、`getMvRemoteUpdateStart`。这是设备固件侧的问题，客户端无法修复，
  只能在部署侧缓解（IoT VLAN / 访客网络 / 禁止跨网段访问）。
- 所有通道均为明文 HTTP/TCP，无 TLS。
- 命名/语言/网络命令的参数直接进 URL，未做长度或字符校验，客户端必须自己校验。

## 10. 尚未确证的部分（诚实清单）

- `setNetwork` 第二参的 payload 语义（原厂直接拼接，未见解析）。
- `getNetwork` / `wlanGetApListEx` 返回文本的具体行格式（原厂按行 + 分隔符手工切分，
  未见结构化解析代码路径）。
- `getMvRomBurnPrecent` 之外的烧写阶段命令（如写入完成回调）。
- MCU 帧的完整字段表（只确证了 light 相关的 5 条）。
- 部分老固件是否要求 `chext` 参数（当前样本中恒出现）。

---

## 11. 与 WiiM 官方 HTTP API 的对照（重要修正）

原本本文件基于 2015 年那台 DOSS App 的静态分析，得出「httpapi 只有设备管理命令，播放控制只能走 UPnP」。
查 **WiiM 官方文档《HTTP API for WiiM PRODUCTS》v1.2**（wiimhome.com/pdf/HTTP%20API%20for%20WiiM%20Products.pdf）后需要修正：

> 平台本来就有完整的播放控制 API。**2015 那批固件/App 没有使用这些命令**，
> 不能据此推断平台不具备。凡「本样本未用到」都要区分成 ⚠️ 平台可能支持、未实测，而不是 ❌ 不存在。

官方文档（v1.2）里与本项目相关的能力：

| 类别 | 命令 | 说明 |
|---|---|---|
| 播放状态 | `getPlayerStatus` | JSON，字段全为**字符串**：`status`、`curpos`(ms)、`totlen`(ms)、`vol`、`mute`、`mode`、`loop` |
| 播放 | `setPlayerCmd:play:<url>` | 直接播音频流，响应固定 `OK` |
| | `setPlayerCmd:playlist:<url>:<index>` | 播 m3u/asx 列表；另有 `hex_playlist`（URL 需 hex） |
| | `setPlayerCmd:pause` / `resume` / `onepause` / `stop` | `onepause` 为暂停/继续切换 |
| | `setPlayerCmd:next` / `prev` | 上下曲 |
| | `setPlayerCmd:seek:<秒>` | **单位是秒**，不是毫秒 |
| 音量 | `setPlayerCmd:vol:<0-100>`、`setPlayerCmd:mute:<0\|1>` | 编组时会同时作用于从机 |
| 循环 | `setPlayerCmd:loopmode:<n>` | 0 顺序不循环 / 1 单曲 / 2 随机循环 / -1 顺序循环 |
| 元数据 | `getMetaInfo` | `metaData.{title,artist,album,albumArtURI,sampleRate,bitDepth}` |
| 输入源 | `setPlayerCmd:switchmode:<mode>` | `line-in`/`bluetooth`/`optical`/`udisk`/`wifi` |
| EQ | `EQOn`/`EQOff`/`EQGetStat`/`EQGetList`/`EQLoad:<name>` | 预设名列表由 `EQGetList` 返回 |
| 预设键 | `MCUKeyShortClick:<1-12>`、`getPresetInfo` | 12 个预设，含 name/url/source/picurl |
| 设备 | `reboot`、`setShutdown:<秒>`、`getShutdown` | `setShutdown:0` 立即关机、`-1` 取消 |
| 时钟/闹钟 | `timeSync:YYYYMMDDHHMMSS`、`setAlarmClock:...`、`getAlarmClock:<n>`、`alarmStop` | 最多 3 组闹钟，时间 UTC |

同时 `getStatusEx` 的字段语义也得到了校正（2015 固件与 2022 文档存在出入，解析时要两种都容忍）：

| 字段 | 官方语义 | 我们代码里的处理 |
|---|---|---|
| `MAC` / `ETH_MAC` | Wi-Fi / 有线 MAC | 用于设备标识 |
| `apcli0` / `ra0` | 设备当前 **IPv4 地址** | 不作 MAC 使用 |
| `essid` | 上游 SSID，**HEX 编码**（如 `4C6966656E67...` = `LifeOfOffice_5G`） | 解码后展示 |
| `internet` | `"1"` 为已联网（字符串） | 兼容字符串与数字 |
| `group` | 0 主机 / 1 编组从机 | 多房间判断 |

**工程结论**：播放控制做成双通道——UPnP 优先（字段规范、能投任意 URL），
httpapi `setPlayerCmd:*` 兜底（老/精简固件可能只支持这个）。
两条都属**探测式使用**：先探通再用，不通就明确告知，不假设。

### 11.1 一个来自生态的实践提示

Music Assistant 的 DLNA 播放器文档里有个开关 **"Replace Pause with Stop"**，专门给
「忽略 pause 命令的老 UPnP 播放器」用。这说明**老固件上 `Pause` 可能无效**，
客户端要准备「暂停退化为停止」的兜底（本项目尚未实现，列入待验证清单）。

---

## 12. 私有播放队列服务（`urn:schemas-wiimu-com:service:PlayQueue:1`）

标准 UPnP 的 AVTransport **没有队列概念**（只有「设一个 URI」）。整列表推送靠厂商自建的这个私有服务。
以下动作名与参数名全部从原厂 App 的 Cling 动作封装类逐字段读出（✅ 已确证）。

| 动作 | 参数（顺序即 SOAP 顺序） | 说明 |
|---|---|---|
| `CreateQueue` | `QueueContext` | 建立队列 |
| `DeleteQueue` | `QueueName` | 删除队列 |
| `BackUpQueue` | `QueueContext` | 备份队列（语义未实测） |
| `AppendTracksInQueue` | `QueueContext` | 追加曲目 |
| `AppendTracksInQueueEx` | `QueueContext`, `Direction`, `StartIndex` | 带方向的插入 |
| `PlayQueueWithIndex` | `QueueName`, `Index` | 从第 N 条开始播 |
| `GetQueueIndex` | `QueueName` | 读当前条目 |
| `RemoveTracksInQueue` | `QueueName`, `RangStart`, `RangEnd` | 删除区间 |
| `SetQueueLoopMode` | `LoopMode` | 列表循环 |
| `BrowseQueue` | `QueueName` | 读队列内容 |

### 两个必须原样保留的坑

1. **`RangStart` / `RangEnd` 少一个 `e`**（不是 `RangeStart`）——设备端真实参数名，改了必失败。
2. `AppendTracksInQueueEx` 里 `Direction` 在 SOAP 中**排在 `StartIndex` 之前**，
   尽管原厂构造函数里 `StartIndex` 先出现。

### `LoopMode` 与 `setPlayerCmd:loopmode` 不是同一套编号

| | 队列服务 `SetQueueLoopMode` | httpapi `setPlayerCmd:loopmode` |
|---|---|---|
| 顺序不循环 | 0 | 0 |
| 单曲循环 | 1 | 1 |
| 列表循环 | **2** | **-1** |
| 随机 | **3** | **2** |

⚠️ 容易混，本项目用 `QueueLoopMode` 常量隔离，不要互相传值。

### ⚠️ 尚未确证：`QueueContext` 的内部结构

反向工程只能确定它是**一个字符串参数**（原厂把任意 `String` 直接塞进构造函数），
看不到设备端如何解析。本项目按 UPnP 通行做法用 DIDL-Lite 文档，并把格式做成可切换策略
（`QueueContextFormat`：`DIDL_ONLY` / `NAME_THEN_DIDL` / `NAME_ONLY`），真机上一试即知。
这是本规格里**最大的一处未知**，使用前必须验证。

## 13. 手机侧为什么必须起 HTTP 服务

原厂 App 内置了 **GNaP MediaServer**（DIDL 里能看到 `Container("2","0","Audios","GNaP MediaServer",...)`）
与 **NanoHTTPD**；`Res` 的 URL 用占位符拼装：`http://#IP_Param#` + 文件路径，
运行时替换成手机 IP + 端口。

原理：音箱是 DMR，**只会自己发起 HTTP 拉流**。手机要播本地文件，就必须把文件通过 HTTP 暴露到局域网。
本项目用自研的纯 JVM `LocalMediaServer` 替代 NanoHTTPD，并实现了 renderer 必需的
`HEAD` 与 `Range`（206 + `Content-Range`）——缺 Range 时进度条拖动会失败、部分固件直接不播。
