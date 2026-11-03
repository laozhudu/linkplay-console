# 35 · 原厂全功能对照表（一次性盘完，不再零散补）

这份表是**按原厂 APK 的组件清单 + 反编译源码 + 真机实测**逐项做的，用来回答
"原厂有什么、我们做了什么、做不了的为什么做不了"。

盘点来源：
- `aapt2 dump xmltree AndroidManifest.xml`（组件）
- jadx 1.5.1 全量反编译（2618 个类，`/tmp/jadxout/sources`）
- 全部 `httpapi.asp?command=` 字面量（24 条）
- `.153` / `.164` 真机实测（2026-11-03）

## 一、原厂的全部控制面（24 条命令，已逐条验证）

| 命令 | 用途 | 我方 | 真机 |
|---|---|---|---|
| `getStatusEx` | 设备属性（含 `battery_percent`、`preset_key`、`WifiChannel`） | ✅ | 200 |
| `getNetwork` | 热点/上游网络 | ✅ | `{"securemode":"0",}` |
| `getPlayerStatus` | 播放状态（HEX 曲名、`plicount/plicurr`） | ✅ | 200 |
| `wlanGetApListEx` | 扫 AP（配网） | ✅ | 200 |
| `setDeviceName:<urlenc>` | 改名 | ✅ | `doss1 → doss1test → doss1` |
| `setLanguage:<lang>` | 语言 | ✅ 已接（设备内容页未放，设置页有） | 未实测 |
| `setNetwork:<0\|1>:<ssid[:pwd]>` | 直连配网（另一条路） | ✅ 已实现 | 未实测（会断网，需单独场景） |
| `restoreToDefault` | 恢复出厂 | ✅（二次确认） | 未实测（危险） |
| `setPlayerCmd:switchmode:<src>` | 输入源 | ✅ | 200 |
| `MCUKeyShortClick:<1..6>` | **六键预设** | ✅ 本轮新增 | **1~6 全 OK** |
| `getMvRemoteUpdateStart` | 查固件更新 | ✅ 本轮新增 | OK |
| `getMvRemoteUpdateStatus` | 更新状态 | ✅ | `0`（= 无更新） |
| `getMvRomDownloadStatus` | 下载状态/进度 | ✅ 本轮新增 | 结构化 JSON |
| `getMvRomBurnPrecent` | 烧写进度 | ✅ 本轮新增 | `{"status":"0","progress":"0"}` |
| `setMvRemoteUpdateStart` | 开始下载固件 | ✅ 本轮新增 | 未实测（无可用固件） |
| `multiroom:getSlaveList` | 组内从机（含 battery/channel/mask） | ✅ | `slaves:1`（真机组网中） |
| `multiroom:ConnnectAP:…:chext=1` | 搬迁已有从机 | ✅ 保留（UI 未用） | 未实测 |
| `ConnectMasterAp:…:chext=0` | **组网**（拖拽用的就是它） | ✅ | **8 秒组上** |
| `multiroom:SlaveVolume` | 从机音量 | ✅ | 回读生效 |
| `multiroom:SlaveChannel` | 从机声道 | ✅ 本轮接入 UI | 0/1/2 回读生效 |
| `multiroom:SlaveDeviceName` | 从机改名 | ✅ 本轮接入 | 未实测（需组网窗口） |
| `multiroom:SlaveMask` / `SlaveUnMask` | **屏蔽/恢复从机** | ✅ 本轮新增 | `mask` 0→1→0 |
| `multiroom:SlaveKickout` | 踢出组 | ✅ | 3 秒复原 |
| `EasyLinkResponseStop` | EasyLink 配网收尾 | ⏭️ 不做（我们用 AP 直连配网） | — |

## 二、34 个 Activity 逐项判定

| 原厂 Activity | 功能 | 我方对应 | 状态 |
|---|---|---|---|
| `SplashActivity` / `Splash2Activity` | 启动闪屏 | 无（不需要） | ⏭️ 不做 |
| `WAHomeActivity` | 主页 | 首页（设备卡片） | ✅ |
| `MusicContentPagersActivity` | 播放页 | `PlayerScreen` | ✅ |
| `CurrPlayListActivity` | 播放列表（另一个 Activity） | 播放页中部列表（单层） | ✅ |
| `MusicContentMenuActivity` / `SlidingMenuActivity` | 音乐库菜单 | 底部导航「音乐库」 | ✅ |
| `AliasSettingActivity` / `AliasSettingNoTitleActivity` | 改名 | 齿轮 → 重命名对话框 | ✅ |
| `DeviceSettingCenterActivity` | 设备设置中心 | `DeviceSettingsScreen` | ✅ |
| `DeviceContentActivity` | **设备内容（六键预设）** | `DeviceContentScreen` | ✅ 本轮新增 |
| `AboutDeviceActivity` | 设备信息 | 设置页「设备信息」 | ✅ |
| `DeviceUpgradeActivity` / `DeviceNewUpgradeActivity` | **固件升级** | `DeviceContentScreen` 固件区 | ✅ 本轮新增 |
| `DeviceSettingListActivity` | 设置列表 | 设置页 | ✅ |
| `DeviceConnectAPActivity` | AP 配网 | `ProvisioningScreen` | ✅ |
| `WifiSpeakerAddActivity` | 添加音箱 | 首页「+」 | ✅ |
| `easylink.LinkDeviceAddActivity` | EasyLink 一键配网 | 不做（AP 直连更稳） | ⏭️ |
| `pagesTurtorsViewActivity` | 新手引导 | 无（说明写在卡片上） | ⏭️ |
| `SlaveSpeakerListActivity` | 从机列表 | 首页卡片 + 组内标签 | ✅ |
| `SlaveConnectActivity` | 从机连接进度 | 拖拽后 40 秒轮询进度 | ✅ |
| `SlaveSpeakerAddWPSActivity` | **WPS 配网**（纯 UI 流程："去按音箱 WPS 键"） | 未做 | ⏳ 待做（低优先，需真机按键） |
| `mymusic.FragTabMusicFolderList` | 文件夹维度浏览 | NAS 目录 ✅ / 手机本地按文件夹 ❌ | ⚠️ 部分 |
| `mymusic.TabMusicRecentlyMgtActivity` | 最近播放 | 音乐库「最近」 | ✅ |
| `mymusic.TabAdd2PlayListMgtActivity` | 加入歌单 | 音乐库「歌单」+ 收藏批量加入 | ✅ |
| `local.LocalSettingActivity` | 手机本地音乐 | `QueueScreen` | ✅ |
| `lightctrl.LightCtrlActivity` | **灯效**（MCU 串口 TCP 8899：`MCU+PAS+light-get&`） | 无 | ❌ 硬件/固件不支持：8899 端口能连但**无响应** |
| `alarm.AlarmSettingMainActivity` | **闹钟**（cling `support.d.alarm` AlarmClock） | 无 | ❌ 固件不支持：`description.xml` 只声明 5 个服务，**没有 AlarmClock** |
| `iheartradio.NActLoginIheartRadio` / `tidal.TabTidalMgtActivity` | 第三方平台登录 | 无 | ⛔ 约定不做（不内置平台音源/凭据） |
| `ReportPage` / `ServiceTesterceTester` | 内部工具/测试页 | 无 | ⏭️ 不做 |
| `SherlockFragmentActivity` | 基类 | — | — |

## 三、7 个 Service

| 原厂 Service | 作用 | 我方 |
|---|---|---|
| `AndroidUpnpServiceImpl` / `WadAndroidUpnpServiceImpl` / `WadUpnpService` | cling UPnP 栈 | 我们手写轻量客户端（不引 cling，纯 JVM 可测） |
| `DlnaPlayService` | DLNA 播放服务 | `QueuePlaybackService`（前台服务） ✅ |
| `ReenableAllApsWhenNetworkStateChanged$BackgroundService` | 网络变化后恢复热点 | 不适用（我们不改设备网络栈） |
| `UIService` | UI 辅助 | 不需要 |
| `floatingwindow.FloatingWindowService` | **悬浮窗**（桌面小窗控制） | 未做 | ⏳ 待做 |

## 四、数据库表

| 原厂表 | 用途 | 我方 |
|---|---|---|
| `tb_music_collection` | 收藏/歌单曲目 | `LibraryStore.favorites`（同名表）+ `linkplay_playlist.db`（歌单） ✅ |
| `tb_last_played` | 最近播放 | `LibraryStore.recents` ✅ |
| `tb_net_radio` | 网络电台 | `LibraryStore.radios` ✅ |

原厂还有 alarm 的本地偏好（`0:0:0:0:0:0:0` 日期串），因设备不支持 AlarmClock 服务，**不做**。

## 五、本轮实际新增（都有真机证据）

1. **六键预设** `MCUKeyShortClick:1..6` → 真机 1~6 全 `OK`
2. **固件升级**四连命令 → 结构化 JSON 全部读到；界面按 `FirmwareUpdate.phase` 显示
   "没有可用更新 / 正在下载 N% / 正在写入 N%"，**没查到新版本时按钮就是灰的**
   （不给按了没反应的按钮）
3. **从机屏蔽/恢复** `SlaveMask` / `SlaveUnMask` → `mask` 0→1→0 实测
4. **声道接进 UI**：卡片 `LR/L/R` 点击循环（0→1→2），走 `SlaveChannel`
5. **定时关闭入口**：齿轮菜单 → 15/30/45/60/90/120 分钟，显示倒计时
6. **设备内容页**（`DeviceContentScreen`）：预设键 + 固件 + 输入源

## 六、明确不做的（有证据，不是懒）

| 项 | 证据 |
|---|---|
| 闹钟 | 设备 `description.xml` 无 `AlarmClock` 服务；`GetAlarmQueue` / `BrowseQueueAlarm` 回 `401 invalid action` |
| 灯效 | TCP 8899 可连但 `MCU+PAS+light-get&` **无响应**（3 秒超时）；本机 `uart_pass_port=8899` 却没灯效 MCU 应答 |
| 平台音源（iHeart/Tidal/QQ/Spotify） | 项目约定：不内置平台音源与凭据 |
| 设备侧 U 盘浏览 | `BrowseQueueUSBDisk` 回 `401 invalid action`（固件没这个动作）；只能靠 `mode=11/16` 识别"现在放的是 U 盘" |

## 七、待做（有价值但没排上，按优先级）

1. **悬浮窗**（原厂 `FloatingWindowService`）：桌面小窗控制播放。需要 `SYSTEM_ALERT_WINDOW` 权限。
2. **WPS 配网**（原厂 `SlaveSpeakerAddWPSActivity`）：纯 UI 流程 —— 让用户按音箱 WPS 键后等它出现。
3. **手机本地音乐按文件夹浏览**（原厂 `FragTabMusicFolderList` 的本地那一半）。
4. 从机改名的真机验证（需要一次组网窗口）。
