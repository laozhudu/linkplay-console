# 功能裁剪决策表（34 个 Activity + 7 个 Service）

来源：原厂 App（`com.wifiaudio.doss` ver1.6.5）清单静态提取。决策口径：
**MVP 只保留「能控制设备」的最小闭环**，内容聚合类一律砍掉（接口多已失效/无合法授权）。

图例：✅ 保留（MVP） · 🔁 改造后保留 · ⏸ 延后（P2+） · ❌ 砍掉

## Activity

| # | 原类名 | 作用 | 决策 | 理由 / 现代替代 |
|---|---|---|---|---|
| 1 | `app.SplashActivity` | 启动页 + 首次引导判断 | 🔁 | 现代启动无需 splash Activity；用 `androidx.core.splashscreen` |
| 2 | `app.Splash2Activity` | 第二套启动页（白标差异） | ❌ | 与 1 重复 |
| 3 | `actionbarsherlock.app.SherlockFragmentActivity` | 基类被误注册为 Activity | ❌ | 库残留，直接删除 |
| 4 | `WAHomeActivity` | 主界面（设备已连接后的控制中心） | 🔁 | 核心，重写为 Compose 主页 |
| 5 | `view.pagesTurtorsViewActivity` | 新手引导 | ⏸ | 用 Compose Pager 做，非 MVP 阻塞项 |
| 6 | `pagesdevcenter.DeviceSettingCenterActivity` | 设备设置中心入口 | 🔁 | 合并进「设备详情」单页 |
| 7 | `pagesdevcenter.DeviceContentActivity` | 设备列表/内容列表 | 🔁 | 由「发现页 + 设备列表」承担 |
| 8 | `pagesdevcenter.AliasSettingActivity` | 改设备名 | ✅ | `setDeviceName`，一行命令 |
| 9 | `pagesdevcenter.AliasSettingNoTitleActivity` | 改名的无标题栏变体 | ❌ | UI 差异，功能重复 |
| 10 | `pagesdevcenter.AboutUsActivity` | 关于我们 | ⏸ | 可选 |
| 11 | `pagesdevcenter.WifiSpeakerAddActivity` | 添加 Wi-Fi 音箱（配网入口） | 🔁 | MVP 用「手动输入 IP + AP 直连」两步，见协议 §3.1 |
| 12 | `pagesdevcenter.SlaveSpeakerAddWPSActivity` | WPS 一键配对 | ⏸ | 依赖设备 WPS 按键，场景少 |
| 13 | `pagesdevcenter.SlaveSpeakerListActivity` | 从机列表 | ✅ | `multiroom:getSlaveList` |
| 14 | `pagesdevcenter.SlaveConnectActivity` | 从机连接/跟随 | 🔁 | `multiroom:ConnnectAP` |
| 15 | `pagesdevconfig.AboutDeviceActivity` | 设备信息（固件/MAC/UUID） | ✅ | `getStatusEx` |
| 16 | `pagesdevconfig.DeviceConnectAPActivity` | AP 直连配网页 | ✅ | 主配网路径 |
| 17 | `pagesdevconfig.DeviceSettingListActivity` | 设备设置项列表 | 🔁 | 合并进设备详情 |
| 18 | `pagesdevconfig.DeviceUpgradeActivity` | 音箱固件升级 | ⏸ | `getMv*` 家族，P2 |
| 19 | `pagesdevconfig.DeviceNewUpgradeActivity` | 新版升级页 | ⏸ | 同上，与 18 合并 |
| 20 | `pagesmsccenter.CurrPlayListActivity` | 当前播放队列 | 🔁 | 依赖 UPnP PlayQueue，P2 |
| 21 | `pagesmsccontent.MusicContentMenuActivity` | 内容菜单 | ❌ | 内容聚合外壳 |
| 22 | `pagesmsccontent.MusicContentSlidingMenuActivity` | 抽屉式内容菜单 | ❌ | 同上 |
| 23 | `pagesmsccontent.MusicContentPagersActivity` | 内容分页容器 | ❌ | 同上 |
| 24 | `pagesmsccontent.mymusic.FragTabMusicFolderList` | 本地音乐文件夹 | ⏸ | 用 Media3 + SAF 重做 |
| 25 | `pagesmsccontent.mymusic.TabMusicRecentlyMgtActivity` | 最近播放 | ⏸ | Room 记录即可 |
| 26 | `pagesmsccontent.mymusic.TabAdd2PlayListMgtActivity` | 加入播放列表 | ⏸ | 随 24/25 |
| 27 | `pagesmsccontent.easylink.LinkDeviceAddActivity` | EasyLink 声波配网 | ⏸ | 依赖 32 位 native，作为兼容备选 |
| 28 | `pagesmsccontent.lightctrl.LightCtrlActivity` | 灯效控制 | 🔁 | MCU 文本帧（协议 §5），P2 |
| 29 | `view.alarm.AlarmSettingMainActivity` | 定时闹钟/定时播放 | ⏸ | AlarmManager + 前台服务 |
| 30 | `view.pagesdevcenter.local.LocalSettingActivity` | 本地设置 | 🔁 | 并入设置页 |
| 31 | `view.ReportPage` | 用户反馈/上报页 | ❌ | 无后端可报 |
| 32 | `app.ServiceTesterceTester` | 调试用 Activity | ❌ | 调试残留 |
| 33 | `pagesmsccontent.tidal.TabTidalMgtActivity` | TIDAL 集成 | ❌ | 需重新申请官方开放 API，不与旧凭据复用 |
| 34 | `pagesmsccontent.newiheartradio.NActLoginIheartRadio` | iHeartRadio 集成 | ❌ | 同上 |

## Service

| 原类名 | 作用 | 决策 | 现代替代 |
|---|---|---|---|
| `org.teleal.cling.android.AndroidUpnpServiceImpl` | Cling UPnP 服务 | 🔁 | jUPnP + `foregroundServiceType=connectedDevice` |
| `org.clingext.upnp.WadAndroidUpnpServiceImpl` | 厂商改造版 UPnP 服务 | 🔁 | 统一并入上一个 |
| `org.clingext.upnp.WadUpnpService` | 同上（另一入口） | ❌ | 去重 |
| `com.wifiaudio.service.DlnaPlayService` | DLNA 播放 | 🔁 | Media3 `MediaSessionService`（type=mediaPlayback） |
| `com.wifiaudio.service.UIService` | 常驻 UI 服务 | ❌ | 现代无此需求 |
| `...ReenableAllApsWhenNetworkStateChanged$BackgroundService` | 网络变化时恢复 AP | ⏸ | 用 `ConnectivityManager` 回调 + WorkManager |
| `...floatingwindow.FloatingWindowService` | 悬浮窗控制条 | ⏸ | 优先 Media3 通知；悬浮窗需 SYSTEM_ALERT_WINDOW 授权 |

## MVP 范围（冻结）

**保留**：设备发现（SSDP + 手动 IP）· 设备信息（`getStatusEx`）· 改名 · 输入源切换 ·
从机列表与音量 · 原始命令控制台 · AP 直连配网（后续版本）。

**明确不做**：任何第三方流媒体集成、App 自升级、悬浮窗、新手引导动画、上报页。

## 统计

| 决策 | 数量 |
|---|---|
| ✅ 保留 | 4 |
| 🔁 改造后保留 | 10 |
| ⏸ 延后 | 12 |
| ❌ 砍掉 | 8 |
| **合计** | **34** |

Service：✅ 0 · 🔁 3 · ⏸ 2 · ❌ 2。
