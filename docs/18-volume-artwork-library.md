# 18 · 音量同步 / 内嵌封面与歌词 / 媒体库统一

用户这一轮四条：

> 原厂apk在多房间模式是可以各音箱独立设置音量和声道没问题，同时主页音量应该和播放器的音量调节及时同步，目前不够及时。
> 音乐封面和歌词是可以通过upnp获得内嵌在音乐文件里的信息。
> 媒体库各页都没有与主页样式一致，特别是顶部内容太靠近屏幕上边缘。
> 而且功能没有完全开发，特别是音乐库，包括里面网络电台，根本无从下手配置，而且样式难看。

## 1. 音量全站同步（`playback/VolumeBus.kt`）

**根因**：播放页有 `PlaybackViewModel.volume`（自己 1s 轮询），首页多房间卡片有
`groupMembers[].volume`（只在进入 App 时读一次 `getPlayerStatus`）。两份状态互不相通，
所以在播放页拖完音量回首页还是旧数字。

**改法**：新增进程内唯一的 `VolumeBus`（按设备 IP 索引），三处都写它、都读它：

| 写入方 | 时机 |
|---|---|
| `PlaybackViewModel.setVolumeLive` | 拖动当下（乐观值，数字立刻跟手） |
| `PlaybackViewModel.refreshOnce` | 每次轮询回读（外部改了也能跟上） |
| `ConsoleViewModel.setGroupVolume` | 首页拖主机音量 |
| `ConsoleViewModel` 心跳 | 每 2.5s 在首页时回读一次主机 `getPlayerStatus` |

UI 侧：播放页音量条、首页多房间主机音量条都订阅 `VolumeBus.state`，同帧更新。
从机音量仍只能由主机回报（从机在主机热点网段，直连不可达），这是物理限制，不是实现取舍。

## 2. 内嵌封面与歌词（`core-protocol/metadata/`）

新增三个纯 JVM 文件：

- `TagParser.kt` — ID3v2.2/2.3/2.4（`APIC`/`USLT`/`TIT2`/`TPE1`/`TALB`）+ FLAC（`VORBIS_COMMENT`/`PICTURE`）
- `Mp4Tags.kt` — MP4/M4A 的 `moov.udta.meta.ilst`（`covr` / `©lyr` / `©nam` / `©ART` / `©alb`）
- `TagFetcher.kt` — 用 **HTTP Range** 读：先读头 256KB（ID3/FLAC），若是 MP4 再读尾 1MB

**为什么必须读尾部**：本项目 NAS（fnOS/MiniDLNA）上的曲目实测是 **M4A**，而且 `moov`
在文件末尾（非 faststart）。只读开头永远读不到封面 —— 这是实测出来的，不是猜的。

**真机证据**（`.223:8200/MediaItems/515.mp3`，13.8MB）：

```
文件头: 00 00 00 1c | ftyp isom | free | mdat(13.6MB)
文件尾: moov(190992B) → udta → meta → ilst → covr(59456B) / ©nam / ©ART / ©alb
解析结果: cover=59432B image/jpeg，title/artist 均拿到
```

这条验证写成了 `Mp4RealSampleTest`（环境变量 `LIVE_M4A_HEAD` / `LIVE_M4A_TAIL` 开关，
CI 上自动跳过）。

播放页行为：
- 服务器给了 `albumArtURI` 就用它（Coil）；没给就用文件内嵌封面（`BitmapFactory` → `Image`）；
  都没有才退回首字母占位。
- 文件带歌词（ID3 `USLT` / MP4 `©lyr` / FLAC `LYRICS`）才显示「查看歌词」，
  没有就不显示按钮 —— 不做假功能。
- 切歌时清空上一首的封面/歌词，避免新歌配旧图。

## 3. 媒体库页面统一 + 顶部留白

**顶部贴边的根因**：二级页（NAS / 手机音乐 / 音乐库 / 设备设置 / 配网）走的是
`Scaffold` 之外的**早退分支**，从来没避让状态栏。之前只有播放页自己加了
`windowInsetsPadding(systemBars)`，而且它在 Scaffold 内，等于加了两份。

**改法**：
- 早退分支统一包一层 `Box(windowInsetsPadding(systemBars))` —— 一处修，所有二级页都吃到；
- 播放页删掉自己那份（Scaffold 已提供），顶部不再多一条空白。

**音乐库重写**（`library/LibraryScreen.kt`）：
- 页头用 `PageHeader`（与设备设置/NAS/手机音乐同一套 40dp 圆钮 + 标题 + 副标题）；
- 删掉页尾那个「返回」TextButton —— 页头已有返回箭头，两个入口等于承认没想清楚；
- 调试日志从页面正文收进「诊断」折叠卡；
- 四个分区内容全部装进 `AppCard`，间距只用 `Spacing` 令牌（原来混着 6dp/8dp/10dp）；
- 搜索框改成 56dp 大胶囊（与首页搜索同形状）。

## 4. 网络电台可用化

原来两个裸文本框常驻页面上，用户不知道「流地址」填什么 —— 就是「无从下手」。

现在：
- 空态先说清「点添加电台，粘一条流地址就行」；
- 点开对话框：流地址（**边输边校验**，非 http/https 直接标红且禁用提交）+ 名称（留空自动取域名）+ **三条可点的示例**（SomaFM ×2、Radio Paradise，都是公开可直连的流）；
- 列表里每行是「图标 + 名称 + 地址」，点行即播，行尾 ✕ 二次确认删除。

仓库仍然**不内置任何平台音源接口或凭据**，示例只是公开流地址，用户可以全部删掉。

## 验证

- `:core-protocol:test` + `:app:testDebugUnitTest` **282 用例 0 失败**（新增 TagParser 8、Mp4RealSample 2、VolumeBus 5）
- 真机真实样本解析通过（M4A 封面 59432B）
- `:app:assembleDebug` 通过
- 界面观感需在手机上确认；音量跟手的手感也需实测，我这里只能保证链路（乐观值 + 合并通道 80ms）是对的
