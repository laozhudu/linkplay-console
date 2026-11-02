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

## 5. 建议的挖掘顺序（性价比）

1. **httpapi 投流兜底**（已在 §0.1 验证）→ 立刻把 `PlaybackEngine` 里的「UPnP 掉线就 reboot」改成「先降级 httpapi」
2. **`getPlayerStatus` 作为进度源** → 轮询成本比 SOAP 低，且 80 端口更稳
3. **`SetDeviceName` / `GetSimpleDeviceInfo`** → 设备卡片一次拿全，去掉多次请求
4. **闹钟**（要构造 `AlarmContext`，与已有 `PlayListContext` 同族，成本可控）
5. **TF 卡 / 网络电台** → 需要先找到设备侧的入口命令（`SearchUrl` 或 `SONGLIST-LOCAL` 的 QueueContext）
6. **灯效 / 多房间 / AirPlay** → 需要额外硬件或苹果设备，先不投入
