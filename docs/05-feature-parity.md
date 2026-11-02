# 原 APK 功能盘点：已有 / 漏掉 / 主动砍掉

> 目的：回答「原有 App 还有哪些功能我们忽略了」。全部结论来自对
> `com.wifiaudio.doss` ver1.6.5 的静态分析（list 见文末证据）。
> 状态列：✅ 已实现 · 🚧 本次新增 · ❌ 未实现（漏掉） · 🚫 主动砍掉

## 一、按证据源盘出来的完整功能域

### 1. 本地音乐（本次补齐）

| 能力 | 证据 | 我们的状态 |
|---|---|---|
| 扫描手机音乐库 | `org.a.a.i` 查 `MediaStore.Audio.Media.EXTERNAL_CONTENT_URI` | 🚧 |
| 手机侧文件 HTTP 服务 | `org.a.a.f` = NanoHTTPD（`serveFile(): given homeDir is not a directory.`） | 🚧 |
| 曲目 DLNA 元数据（DIDL） | `new MusicTrack(...)` + `new Res(new MimeType(...), len, "http://#IP_Param#" + path)` | 🚧 |
| 整列表推送 | 私有 PlayQueue：`CreateQueue` / `AppendTracksInQueue` / `AppendTracksInQueueEx` | 🚧 |
| 列表循环 | `SetQueueLoopMode(LoopMode)` | 🚧 |
| 定位到第 N 首 | `PlayQueueWithIndex(QueueName, Index)` | 🚧 |
| 队列增删查 | `RemoveTracksInQueue` / `GetQueueIndex` / `BrowseQueue` / `BackUpQueue` / `DeleteQueue` | 🚧 客户端已实现全部动作 |
| 音乐文件夹维度 | `tb_music_folder(folder_name)` | ⚠️ 只存了 folder 字段用于搜索，未做文件夹浏览页 |
| 专辑维度 | `tb_album_info(playuri,title,album,creator,artist,albumarturi,duration,folder_name,source_type,song_id)` | ❌ |
| 收藏 | `tb_music_collection(playuri)` | ✅ |
| 最近播放（保留 100 条） | `tb_last_played(...)` + `order by first_played_time desc limit 100` | ✅（去重，重播只更新时间） |

### 2. 设备侧内容

| 能力 | 证据 | 我们的状态 |
|---|---|---|
| 浏览**音箱插的 U 盘 / TF 卡** | `local://{"ObjectID":"USBDISK","BrowseFlag":"BrowseMedia","MediaType":"Album",...,"PerPage":"50","Page":"N"}` | ❌ **完全漏掉** |
| 播放设备本地盘曲目 | 同上，走 `PlayQueue` 的 `USBDisk` 队列 | ❌ |
| 多设备记忆（名字/UUID/SSID） | `tb_alias_device(devname, devuuid, devssid)` | ❌ 我们只存 IP |
| 设备改名 | `setDeviceName` | ✅ |
| 设备信息（固件/MAC/UUID/联网） | `getStatusEx` | ✅ |

### 3. 播放与音频

| 能力 | 证据 | 我们的状态 |
|---|---|---|
| 播放/暂停/停止/上下曲/跳转 | UPnP AVTransport | ✅ |
| 音量/静音 | UPnP RenderingControl + `setPlayerCmd:vol/mute` | ✅ |
| 播放进度与时长 | `GetPositionInfo` / `getPlayerStatus` | ✅ |
| 输入源切换 | `setPlayerCmd:switchmode` | ✅ |
| 6 个预设键 | 布局 `vpreset`~`vpreset6` + `MCUKeyShortClick:n` | ✅ |
| 均衡器 EQ | 本样本 **0 命中**（`EQOn/EQOff/EQLoad` 全无）——是后来固件才有的；官方 API v1.2 有 | ❌ 本机固件可能无 |
| 定时关机 | 本样本仅 `getShutdownView` 一处，无 `setShutdown` | ❌ 基本无 |
| 闹钟 | `view.alarm.AlarmSettingMainActivity` + Cling alarm 相关类 | ❌ |
| 灯效（MCU） | `MCU+PAS+LIGHT-<n>&` / `LIGHTSTYLE-<s>&` / `LWD+DAF` | ❌ |
| 多房间编组 | `multiroom:SlaveMask/UnMask/Kickout/Volume/Channel/DeviceName` | ⚠️ 只做了从机音量展示，未做入组/解组/声道 |

### 4. 配网与网络

| 能力 | 证据 | 我们的状态 |
|---|---|---|
| AP 直连配网 | `ConnectMasterAp:ssid=<HEX>:ch:auth:encry:pwd:chext` | ✅ |
| 已联网设备改连 AP | `wlanConnectApEx` | ✅ 协议层有，UI 未接 |
| 多房间从机跟随主机 | `multiroom:ConnnectAP:<ip>:ConnectMasterAp:...` | ✅ 协议层有，UI 未接 |
| EasyLink 声波配网 | `smartlinkver2` + `libEasylinkControllerVer2.so` | ⏸ 需 32 位 so，主动不做 |
| WPS 一键配对 | `SlaveSpeakerAddWPSActivity` | ❌ |
| 扫描周边 AP | `wlanGetApListEx` → `{"aplist":[...]}` | ✅ |
| 网络电台（自定义 URL + 每设备记忆） | `tb_net_radio(isKept,isPlaying,soundName,soundUrl,invalid,deviceName)` | ✅ |

### 5. UI / 交互 / 系统集成

| 能力 | 证据 | 我们的状态 |
|---|---|---|
| 悬浮窗控制条 | `floatingwindow.FloatingWindowService` + `SYSTEM_ALERT_WINDOW` | ❌ |
| 通知栏控制 | `RemoteViews` | ❌ 未做 MediaSession |
| 语音控制（唤醒词 + 识别 + 合成） | 讯飞 `Wakeuper`（21 处）、`SpeechRecognizer`、`SpeechSynthesizer`、`view.dlg.voicesearch.*` | ❌ **漏掉且体量大** |
| 搜索（多源 + 历史） | `tb_search_history(search_key,search_date,search_type)` | ❌ |
| 拼音首字母搜索 | `pinyin4j` + `tb_pinyin(hanzi,pinyin)` | ✅（纯 JVM，无依赖，只给首字母） |
| 新手引导 | `pagesTurtorsViewActivity` | ❌ 非阻塞 |
| App 自升级 | `tb_softupgrade` + 明文 HTTP `.ini` | 🚫 主动砍掉（安全） |
| 用户反馈/上报页 | `view.ReportPage` | 🚫 无后端 |
| 第三方内容源 8 家 | 豆瓣/喜马拉雅/蜻蜓/天天动听/百度/TIDAL/iHeart/TuneIn | 🚫 主动砍掉（合规） |

### 6. 确认「原 App 也没有」的能力（避免误会）

| 能力 | 证据 | 结论 |
|---|---|---|
| AirPlay 发送/接收 | `raop`/`AirTunes`/`_raop._tcp`/`7000` 全 0 命中；只有 `icon_source_type_airplay.png` 和 `strK.equals("AIRPLAY")` 时禁用按钮 | 原 App 只**展示**状态，AirPlay 是**音箱固件**能力 |
| QQ 音乐接入 | `qq.com`/`y.qq`/`qqmusic`/`music.163`/`kugou`/`kuwo`/`migu` 全 0 | 原 App 没有；「QQ 音乐推给音箱」是 **QQ 音乐自己当控制点** |

## 二、优先级建议（按「用户实际会用到」排序）

| 优先级 | 功能 | 工作量 | 理由 |
|---|---|---|---|
| P0 | 本地播放列表 + 循环（本次） | 已完成 | 原 App 最高频用法 |
| P0 | 收藏 / 最近播放 | 已完成 | 没有它就没有「固定入口」 |
| P1 | 网络电台（自定义 URL） | 小 | 老音箱最实用的在线内容，且完全合规 |
| P1 | 拼音首字母搜索 | 中 | 中文曲库不解决这个等于没法搜 |
| P1 | 预设键 1-6 | 小（`MCUKeyShortClick:n`） | 硬件按键联动，成本极低 |
| P2 | 设备 U 盘 / TF 卡浏览 | 中（`local://` 协议 + 分页） | 很多人就是把 U 盘插音箱 |
| P2 | 多房间完整编组 | 中 | 原 App 有，且 `multiroom:*` 命令齐全 |
| P2 | 灯效 MCU | 中（TCP 帧 + 三套 OEM 差异） | 带灯机型才有价值 |
| P3 | 闹钟 | 中 | 依赖设备侧 alarm 动作 |
| P3 | 语音点歌 | 大（换新版讯飞 SDK，重注册 appid） | 体验好但成本高 |
| P3 | 悬浮窗 / 通知栏 | 中（Media3 MediaSession） | 现代替代是通知栏而非悬浮窗 |
| — | EQ / 定时关机 | — | 本机固件可能不支持，先探测再说 |

## 三、证据与复现

```bash
export REA_JADX_MCP_JAR=/opt/rea-providers/jadx-headless-mcp-0.7.1-all.jar
A=<990803.apk>

rea inspect-android-package $A --json          # 34 activity / 7 service / 16 权限
rea search-android-classes $A "" --json        # 4047 类

# 数据库表（功能清单的强证据）
strings -n 8 classes.dex | grep -oE "create table if not exists [a-zA-Z_]+"

# 各功能域关键字命中数
for s in raop AirPlay qq.com EQOn setShutdown Wakeuper PlayQueue; do
  printf "%-14s %s\n" "$s" "$(strings -n 4 classes.dex | grep -cF "$s")"
done
```

代码级检索用同 jar 的 `search_classes_by_keyword`（`search_in=code`），
脚本见技能 `rea-deployment/scripts/jadx_mcp.py`。


## 四、本轮（0.5.0）补齐情况

| 项 | 状态 | 实现位置 |
|---|---|---|
| 收藏 | ✅ | `LibraryStore.favorites`（表名沿用 `tb_music_collection`） |
| 最近播放 | ✅ 去重版 | `LibraryStore.recents`（重播只更新时间，原厂会插重复行） |
| 网络电台 | ✅ | `RadioStation` + `LibraryStore.radios`（`tb_net_radio`） |
| 拼音首字母搜索 | ✅ 无依赖 | `PinyinInitials`（GB2312 区位表）+ `LibrarySearch` |
| 预设键 1-6 | ✅ | `LinkPlayDevice.pressPreset` + 音乐库页按钮 |
| 音乐库页 | ✅ | `LibraryScreen`（搜索/收藏/最近/电台四分区） |
| 文件夹浏览页 | ⚠️ 未做 | 字段已存，缺 UI |
| 专辑维度 | ⚠️ 部分 | 只参与搜索，无专辑页 |


## 五、真机反馈（DOSS DS-1831 / 3.6.5310）修正了三条判断

| 原判断 | 真机实测 | 修正 |
|---|---|---|
| 整列表推送可用（PlayQueue CreateQueue） | `QueueContext` 不是 DIDL，而是自定义 `<PlayList>` + `<ListInfo>` 方言；且 `<Tracks>` 不许有内容 | **本固件不能推本地列表**，只能建「在线源描述符」队列。见 docs/08 §5.2 |
| 原 App 没有 QQ 音乐 | 设备**自带 QPlay 服务** | 这是设备侧能力，App 确实不需要实现；但 `InsertTracks` 被 `QPlayAuth` 挡住，第三方不可用。见 docs/08 §5.3 |
| PlayQueue 有 10 个动作 | 真机 SCPD 有 **27 个** | 反推会系统性低估固件；已补 `ReplaceQueue`/`AppendQueue`/`GetQueueLoopMode`/`SetQueuePolicy`/`BrowseQueue` 读回 |
