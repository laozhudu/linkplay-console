# 能力清单：设备固件 + 原 APK 全量盘点

> 本文只写**实测到的**和**从组件/SCPD 读到的**，推断项单独标 ⚠️。
> 本轮新发现两条**纠正了此前结论**的事实，见 §0。

## 0. 两条重要纠正

### 0.1 `setPlayerCmd:*` 是可用的（此前记为「不支持」，错了）

实测（写入 + 回读闭环）：

```
GET /httpapi.asp?command=setPlayerCmd:mute:1   → OK
GET RenderingControl#GetMute                   → CurrentMute=1     ← 真的生效
GET /httpapi.asp?command=setPlayerCmd:mute:0   → OK
GET RenderingControl#GetMute                   → CurrentMute=0     ← 还原
```

**并且它可以直接投流**：

```
GET /httpapi.asp?command=setPlayerCmd:play:http://192.168.99.226:8765/tone1.mp3 → OK
getPlayerStatus → mode=10 status=play curpos=678→5020 totlen=6000（正是该文件时长）
本地服务日志   → [srv] 192.168.99.153 "GET /tone1.mp3" 200
```

**用 NAS 真实曲目再验一次**（不依赖本地临时服务）：

```
setPlayerCmd:play:http://192.168.99.223:8200/MediaItems/515.mp3 → OK
getPlayerStatus 连读 8 次：status=play，位置 1.1s → 15.6s 单调递增，totlen=863s
uri（HEX 解码）= http://192.168.99.223:8200/MediaItems/515.mp3
```

注意：投流后 `Title/Artist/Album` 是空（该固件不从 URL 猜元数据），
所以**标题要由控制端自己保留**，不能指望设备回读。

意义：**UPnP 的 49152 掉线时不需要重启音箱**，80 端口的 httpapi 就能投流与控音量。
之前那套「掉线 → reboot → 等 40 秒」从「唯一解」降级为「UPnP 专属功能不可用时的备选」。

### 0.2 RenderingControl 的多数动作必须带 `Channel`

```
GetVolume + <Channel>Master</Channel> → 200 CurrentVolume=25
GetVolume（不带 Channel）              → 500 "Missing Channel"
```

之前脚本里误写成 `renderingcontrol1`（正确是 **`rendercontrol1`**，注意少一次 ing），
且漏了 Channel，于是把「参数错」读成了「设备不支持」。**这是本轮最该记住的教训**：
500 要读 `errorDescription`，`Invalid Action`（路径/动作错）与 `Missing Channel`（参数缺）完全不同。

## 1. 设备自述能力（硬证据）

`AVTransport#GetDeviceCapabilities` → `PlayMedia`：

```
NONE, STATION-NETWORK, SONGLIST-NETWORK, SONGLIST-LOCAL, SONGLIST-LOCAL_TF, THIRD-DLNA, AIRPLAY, UNKNOWN
```

| 播放类型 | 含义 | 我们的现状 |
|---|---|---|
| `THIRD-DLNA` | 第三方 DLNA 投流 | ✅ 已实现（本机播 NAS/本地文件都走这条） |
| `STATION-NETWORK` | 网络电台 | ⚠️ 未接线（设备侧支持，未验证具体入口） |
| `SONGLIST-NETWORK` | 在线歌单（设备自己管队列） | ⚠️ 即 PlayQueue 的「在线源描述符」路径 |
| `SONGLIST-LOCAL` | 本机/本地歌单 | ⚠️ 未接线 |
| `SONGLIST-LOCAL_TF` | **TF 卡歌单** | ⚠️ 未接线（设备有 TF 卡槽，原 APK 有 `USBDISK` 逻辑） |
| `AIRPLAY` | AirPlay 接收 | ⚠️ 设备自称支持；需 Apple 设备实测，无法用 Android 验证 |

## 2. 设备 UPnP 服务与未接入的动作

| 服务 | controlURL | 动作数 | 我们用了 |
|---|---|---|---|
| AVTransport | `/upnp/control/rendertransport1` | 16 | SetAVTransportURI / Play / Pause / Stop / Seek / Next / Previous / GetPositionInfo / GetTransportInfo / GetMediaInfo / GetTransportSettings |
| RenderingControl | `/upnp/control/rendercontrol1` | 17 | Get/SetVolume、Get/SetMute |
| ConnectionManager | `/upnp/control/renderconnmgr1` | 3 | 未用（GetProtocolInfo 可验证可播格式） |
| PlayQueue（wiimu 私有） | `/upnp/control/PlayQueue1` | 28 | 探测过，仅接受「在线源描述符」 |
| QPlay（tencent 私有） | `/upnp/control/QPlay1` | 9 | 探测过，需腾讯侧种子 |

### 2.1 未接入但确认存在（且能调通）

| 动作 | 实测结果 | 能做什么 |
|---|---|---|
| `GetSimpleDeviceInfo` | `MultiType=1 SlaveMask=0 Name=doss3 CurrentVolume=25 CurrentMute=0 CurrentChannel=0 SlaveList={"slaves":0}` | 一次拿全设备名/音量/声道/多房间状态，比逐个查快 |
| `GetControlDeviceInfo` | `Router=4D594444 Ssid=DOSS DS-1831_ADB4 Status={...}` | 含完整 getStatusEx，一个请求顶两个 |
| `ListPresets` | `FactoryDefaults` | 音效预设列表 |
| `GetEqualizer`（带 Channel） | `CurrentEqualizer=0` | **EQ 是设备能力**（原 APK 里没有 EQ 界面，见 docs/05 的判断需修正为「设备有、App 未提供」） |
| `GetChannel`（带 Channel） | `CurrentChannel=0` | 声道/输入源 |
| `MultiPlaySlaveMask` | 需 `SlaveMask` 参数（有副作用，未调用） | 多房间从机掩码 |
| `SetDeviceName` | 未调用（会改名） | 设备重命名 |
| `SetAlarmQueue` / `GetAlarmQueue` / `DeleteAlarmQueue` | `GetAlarmQueue(AlarmName=X)` → `GeneratePlayListInQueueToXMLString failed`（队列不存在） | **闹钟是 QueueContext 语义**，需先 `SetAlarmQueue` 建，与 PlayList 同一套 XML 方言 |
| `GetPlayType` | `41` | 当前播放类型编码（41 与 `getPlayerStatus.mode` 一致） |
| `GetInfoEx` | `PAUSED_PLAYBACK OK 1 0 00:00:00 <DIDL…>` | 带 DIDL 元数据的整合查询 |
| `SetPlayMode` | 未调用 | 播放模式 |

### 2.2 httpapi 命令可用性（逐个实测）

| 可用 | 返回 |
|---|---|
| `getStatusEx` | 完整设备状态（已用） |
| `getStatus` | `language/ssid/hideSSID/SSIDStrategy/firmware/build/project/...` |
| `getPlayerStatus` / `getPlayerStatusEx` | `{"type","ch","mode","loop","eq","status","curpos","offset_pts","totlen","Title"(HEX),"Artist","Album",...}` |
| `getEqualizer` | `0` |
| `getShutdown` | `0`（定时关机状态位） |
| `multiroom:getSlaveList` | `{"slaves": 0}` |
| `getNetworkInfo` | `{"securemode":"0",}` ← 注意尾逗号，非法 JSON |
| `setPlayerCmd:play:<url>` | 投流 ✅ |
| `setPlayerCmd:pause` / `resume` / `mute:N` / `loopmode:N` / `vol:N` ⚠️（vol 未测，同族必通） | `OK` |
| `reboot` | `OK`（真重启，40s 恢复 UPnP） |
| `wlanGetApListEx` / `wlanStopConnect` / `wlanGetConnectState` / `getWifiConnectAP` / `wlanGetConnectInfo` / `GetCurrentWirelessConnectEx` | 配网族（Web UI 在用） |
| `getMvRemoteUpdateStatus` / `getMvRomDownloadStatus` / `getMvRomBurnPrecent` / `getMvRemoteUpdateStart` / `getMvKillProcess` | OTA 族（本机版本自认最新） |
| `getUartCmd` / `setUartCmd:<baud,data,parity,stop>` | **MCU 串口参数**（不是业务通道） |
| `SystemCommand+firmwareConfirm` / `+getOnlineState` | 固件升级确认 |

| 不可用（`unknown command`） | 说明 |
|---|---|
| `getMode`/`getVolume`/`getMute`/`getDeviceName`/`getSlaveList`/`getPresetList`/`getKeyMapping`/`getAlarmList`/`getLight*`/`getEQ`/`getChannel`/`getSleepTimer`/`getBattery`/`getStorage`/`getUSBDisk` 等 | 这些走 UPnP 或不存在。**「httpapi 里没有」不等于「设备没有」**，是本轮 §0.2 教训的反面 |
| `SystemCommand+onlineupgrade_check` 等 | 本构建未注册该命名空间（`reboot` 可用证明 httpapi 本身正常） |

## 3. 原 APK 的组件清单（34 Activity + 7 Service）

| 组件 | 功能 | 我们 |
|---|---|---|
| `WAHomeActivity` / `Splash*` | 主界面/启动 | ✅ 已重写 |
| `DeviceConnectAPActivity` | AP 直连配网 | ✅ 已实现 |
| `WifiSpeakerAddActivity` / `SlaveSpeakerAddWPSActivity` / `SlaveSpeakerListActivity` / `SlaveConnectActivity` | 加设备 / **WPS 配对** / **多房间从机列表** | ⚠️ 仅做多房间只读 |
| `DeviceSettingCenterActivity` / `DeviceSettingListActivity` / `DeviceContentActivity` | 设备设置中心 | 🚧 部分 |
| `AliasSettingActivity` / `AliasSettingNoTitleActivity` | 设备别名 | ⚠️ 未做（`SetDeviceName` 可用） |
| `DeviceUpgradeActivity` / `DeviceNewUpgradeActivity` / `AboutDeviceActivity` | 固件升级 / 关于 | ❌ 判定不升 |
| `MusicContentPagersActivity` / `MusicContentSlidingMenuActivity` / `MusicContentMenuActivity` | 音乐内容主界面（多源分页 + 抽屉） | 🚧 部分（NAS 页 + 音乐库页） |
| `FragTabMusicFolderList` | 按文件夹浏览 | ✅ NAS 浏览已覆盖同类需求 |
| `CurrPlayListActivity` | 当前播放列表 | ✅ 客户端队列 |
| `TabAdd2PlayListMgtActivity` | 加入播放列表（管理） | ✅ picked 队列 |
| `TabMusicRecentlyMgtActivity` | 最近播放 | ✅ RecentStore |
| `AlarmSettingMainActivity` | **闹钟** | ❌ 未做（设备侧 `SetAlarmQueue` 存在） |
| `LightCtrlActivity` | **灯效** | ❌ 未做 |
| `FloatingWindowService` | **悬浮窗控制**（`SYSTEM_ALERT_WINDOW`） | ❌ 未做 |
| `LocalSettingActivity` | 本机设置（本地音乐/USB、TF） | ❌ 未做 |
| `TabTidalMgtActivity` / `NActLoginIheartRadio` | Tidal / iHeartRadio 登录 | ❌ 不做（第三方账号体系） |
| `ReportPage` / `AboutUsActivity` | 反馈 / 关于 | ❌ 不做 |
| `ServiceTesterceTester` | 内部测试入口 | ❌ 不做 |
| `DlnaPlayService` / `UIService` | 播放与 UI 常驻服务 | ✅ 已用前台服务替代 |
| `WadAndroidUpnpServiceImpl` / `WadUpnpService` / `AndroidUpnpServiceImpl` | Cling UPnP 栈 | ✅ 已自己实现 |

权限侧面印证的功能：`RECORD_AUDIO`（讯飞语音点歌）、`SYSTEM_ALERT_WINDOW`（悬浮窗）、
`MOUNT_UNMOUNT_FILESYSTEMS`（U 盘/TF）、`VIBRATE`（按键反馈）、`WRITE_SETTINGS`（亮度/铃声类）。

## 4. 尚未验证 / 需要外部条件的项

| 项 | 为什么没验 |
|---|---|
| **AirPlay** | 需要 Apple 设备；设备 `GetDeviceCapabilities` 自称支持 |
| **TF 卡 / U 盘播放**（`SONGLIST-LOCAL_TF`） | 需要插卡，且入口命令未知 |
| **网络电台**（`STATION-NETWORK`） | 需要电台地址格式，`PlayQueue` 的 `SearchUrl` 可能就是入口 |
| **闹钟** | `SetAlarmQueue` 需要构造 `AlarmContext`（与 QueueContext 同族），建成功才能 `GetAlarmQueue` 回读 |
| **EQ 写入** | `SetEqualizer` 需 `DesiredEqualizer` 枚举值，盲试可能改变音色（先回读 0 已够证明存在） |
| **多房间** | 只有一台音箱，`slaves:0` 无法验证从机行为 |
| **灯效** | 走 `uart_pass_port:8899` 的 MCU 直通帧，原 APK 三套 OEM 帧已反推但未验证 |

## 4.6 设备识别与记忆（本轮新增）

首页只认 DOSS/LinkPlay 系，判据写在 `LinkPlayIdentity`（纯 JVM，9 个单测）：

| 判据 | 强度 | 实测 |
|---|---|---|
| 描述里含 `urn:schemas-wiimu-com:service:PlayQueue:1` 或 QPlay | **铁证** | 真机 `.153` 两条都有 |
| manufacturer/modelName/friendlyName 含 wiimu/LinkPlay/DOSS | 强 | `manufacturer: DOSS 1010` |
| PlayQueue + AVTransport + RenderingControl 同时具备 | 中（需三者同时，否则电视也算进来） | 真机成立 |

反例已用真机数据验证：NAS `.223` 只有 ContentDirectory + ConnectionManager +
`X_MS_MediaReceiverRegistrar`，**不含任何私有服务**，因此被正确排除。

为什么不靠 SSDP 头筛：真机 `SERVER` 是 `Linux/3.x UPnP/1.0`，没有任何品牌字样（单测里锁住了这条）。

识别后的设备写进 `tb_session` 的 `devices` 键，按 **uuid** 排序（最近用过在前）。
`getStatusEx` 探活时如果发现 IP 变了，会把新 IP 写回 —— 真机从 `.164` 漂到 `.153` 就是这么处理的。

## 4.7 多房间：原厂「拖拽成组」的真实协议（2026-11 反编译 + 真机实测）

### 命令（逐字取自 `com.wifiaudio.a.b.a(master, slave, apInfo)`）

```
GET http://<从机IP>/httpapi.asp?command=
    multiroom:ConnnectAP:<主机IP>:ConnectMasterAp:
    ssid=<主机自身热点名的小写HEX>:ch=<信道>:auth=<WPA2PSK|OPEN>:encry=<AES|NONE>:pwd=<HEX>:chext=1
```

三个容易搞错的点（都是读代码读出来的）：

| 点 | 说明 |
|---|---|
| 请求发给**从机** | 主机信息放在参数里，不是发给主机让它去拉从机 |
| `ssid` 用**小写 hex** | 普通配网的 `ConnectMasterAp` 用大写 hex，两者不同 |
| `chext=1` | 表示「多房间从机接入」，普通配网是 `chext=0` |

### 其他 multiroom 命令

```
multiroom:getSlaveList                          读从机列表
multiroom:SlaveVolume:<从机IP>:<0-100>          设从机音量
multiroom:SlaveChannel:<从机IP>:<声道>           设从机声道
multiroom:SlaveDeviceName:<从机IP>:<URL编码名>   给从机改名
multiroom:SlaveMask / SlaveUnMask / SlaveKickout 入组/退组/踢出（参数在 APK 里不透明）
```

### 原厂 App 的确认机制（重要）

发完 `ConnnectAP` 后，它**不看命令返回值**，而是定时轮询：

- `az`：每 2 秒读一次 `getSlaveList`，超过 **35 秒**判失败
- `bb`：另一种拖拽（异步播放）用 40 秒超时
- 成功 → `drag_msg_play_sync_success`；超时 → `drag_msg_play_sync_fail_timeout`

原因很清楚：`multiroom:*` 命令**只要名字认识就回 `OK`**。真机验证过：`SlaveMask` 换各种参数都回 `OK`，
但一台从机都没出现。所以唯一可信的成功判据是「主机读回来的从机列表真的多了一台」。
本仓库的 `MultiroomClient.joinWithPolling` 照这个节奏实现（含注入时钟，可单测）。

### 真机实测：卡在哪（诚实记录）

两台 DS-1831 同时在线（`.164` fw 3.6.6623 主机 / `.153` fw 3.6.5310 从机）：

```
初始:            master.slaves=0
发 ConnnectAP:   slave 回 OK
轮询 40 秒:      master.slaves 始终 0，两台都保持在线
逐信道 1..13 试: 全部 OK、master.slaves 仍 0
                 （ch=12 时从机掉线数秒后又自己回来 → 命令确实执行了，只是没连上）
MultiPlaySlaveMask(UPnP): 500 Action Failed，SlaveMask 字段读回仍是 0
```

关键线索是反编译里的这一行：

```java
// com.wifiaudio.view.pagesmsccontent.ax.a(String str)   ← str 是 getNetwork 的返回
this.f1415a.f.n = str;                       // 当成主机热点密码
com.wifiaudio.a.b.a(this.b, this.c, this.f1415a);   // 再下发 ConnnectAP
```

而这两台的 `getNetwork` **只返回 `{"securemode":"0",}`**（不是密码）。
原厂逻辑把这个 JSON 当密码、并按「密码非空 → WPA2PSK」走，于是 auth 变成 WPA2PSK + 一段 JSON 当密码，
**必然连不上**。这很可能就是「原厂 App 在这批固件上多房间也不好用」的根因。

### 交互不照抄拖拽（这是刻意的）

原厂用「把 A 拖到 B 身上」来成组。不复刻的理由：

1. **规则看不见** —— 「拖到谁身上谁当主机」只能靠试；
2. **手机上拖拽是误触高发区**，尤其是要把两台设备卡片精确重叠；
3. 主流智能音箱（Sonos / 小爱 / HomePod）都是「选设备 → 加入」的列表式，用户已经习惯。

本仓库的做法：**当前设备固定当主机**，在「设备设置 → 多房间」里点「添加音箱」挑一台加入，
用芯片块展示「本机 ＋ 从机…」的从属关系，点从机芯片可单独调音量或让它退出。
底层命令与原厂完全相同，只换了入口和表达方式。

### 现在的实现与待验证项

- 协议层已实现并单测覆盖（`MultiroomClient` / `joinWithPolling`，6 + 5 个用例）
- 设置页「多房间」里可手填**信道**与**热点密码**，默认 OPEN + 自动信道
- **待验证**：需要用户在真机上试一次（从机会短暂离开原 Wi-Fi，失败会自己回来）。
  若手填正确信道+密码后仍不成功，下一步应怀疑主机热点根本没开
  （可试 `SSIDStrategy` 相关命令），而不是命令格式问题 —— 格式已由反编译 + `OK` 响应双重确认。

## 5. 建议的挖掘顺序（性价比）

1. **httpapi 投流兜底**（已在 §0.1 验证）→ 立刻把 `PlaybackEngine` 里的「UPnP 掉线就 reboot」改成「先降级 httpapi」
2. **`getPlayerStatus` 作为进度源** → 轮询成本比 SOAP 低，且 80 端口更稳
3. **`SetDeviceName` / `GetSimpleDeviceInfo`** → 设备卡片一次拿全，去掉多次请求
4. **闹钟**（要构造 `AlarmContext`，与已有 `PlayListContext` 同族，成本可控）
5. **TF 卡 / 网络电台** → 需要先找到设备侧的入口命令（`SearchUrl` 或 `SONGLIST-LOCAL` 的 QueueContext）
6. **灯效 / 多房间 / AirPlay** → 需要额外硬件或苹果设备，先不投入
