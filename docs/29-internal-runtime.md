# 29 · 内部功能与运转：歌单内核、最近播放、定时关闭

这一轮不看界面，只补"用起来能不能真运转"的部分。所有结论都有实测或反编译出处。

## 一、先纠正两条之前写错的

### 1. 原厂**有**定时关闭

之前的功能盘点写的是"原厂没有定时关机"——**错的**。证据：

```
classes.dex 字符串表：android/os/CountDownTimer
资源串：            定时关闭_queue_clock
```

它是**客户端倒计时**（`CountDownTimer`），到点由 App 自己下发停止。设备端没有
"播完 N 分钟自己停"的接口。

这一轮已按这个大前提实现 `SleepTimer`（纯 JVM，可测，8 个用例）：
`start(minutes)` / `isDue(now)` / `remainingText(now)` / `cancel()`，档位 15/30/45/60/90/120 分钟。
`ConsoleViewModel` 每秒推进一次时钟，到点下发 `setPlayerCmd:stop` 并清空定时器。

### 2. 闹钟与 U 盘队列在这两台固件上**根本不可用**

不是我们没做，是设备没有这些动作：

```
GetAlarmQueue        → 500  401 invalid action
BrowseQueueAlarm     → 500  401 invalid action
BrowseQueueUSBDisk   → 500  401 invalid action
httpapi getAlarmList → unknown command
```

两台都是（fw 3.6.5310 / 3.6.6623）。所以原厂那套 `AlarmContext` / `SetAlarmQueue`
在 DS-1831 上跑不起来；"插 U 盘读目录"也没有可用的 UPnP 动作，只能靠
`getStatusEx` 的 `mode=11/16` 判断"现在在放 U 盘/TF"，然后靠设备自己的列表。

结论：**不做闹钟、不假装能浏览 U 盘**。来源识别（`PlayerSource.currentIdOf`）
已经能把 11/16 认出来，界面上照实写"U 盘 / TF 卡"。

## 二、歌单内核（原厂"全部歌单"）

`core-protocol/playlist/PlaylistOps.kt` —— 纯函数，13 个单测。

规则（都是原厂的语义）：

| 规则 | 为什么 |
|---|---|
| 按 **URL** 判重，URL 为空才退回「标题+艺术家」 | 同一个 NAS 文件被两个目录索引时标题可能不同，地址一定相同 |
| 歌单名唯一，同名 = 同一个歌单 | 用户连点两次「新建」不该出现两个空歌单 |
| 一个歌单最多 2000 首、一个库最多 200 个 | 防止一次误选把内存和 SQLite 撑爆 |
| `addTracks` 支持 `at` 位置插入，越界按追加处理 | 拖拽落点经常差一位 |
| `move` 越界夹到末尾，长度不变 | 在列表外松手是常见操作 |
| 重名改名**直接拒绝** | 悄悄合并会吞掉另一个歌单，用户无从察觉 |

写测试时抓到一个真 bug：上限判断写成了"加完再判"，导致第 2000 首之后还会多进一首。
改成"先判再加"（`PlaylistOpsTest > adding respects the track cap` 锁死）。

### 持久化

`app/library/PlaylistStore.kt`，**独立数据库文件** `linkplay_playlist.db`：

```
tb_playlist       id, name(unique), updated_at
tb_playlist_item  id, list_id, position, url, title, artist, album, duration_sec, mime, album_art_url
```

为什么不加进已有的 `LibraryStore`：那里存着收藏/最近/电台，是真实数据。
加表就得走 `onUpgrade`，版本判断写错会连收藏一起没。新开一个库文件，坏了只丢歌单，
回滚就是删一个文件 —— 这与"数据安全优先"的要求一致。

### 播放整单：和音乐库走同一条路

`LibraryViewModel.startQueue()`：PlayQueue（原厂机制）→ m3u 兜底 → 客户端逐首投流。

抽出来的原因是**两条入口必须一条路**。以前"从音乐库播"和"从 NAS 播"各写一遍，
结果设备侧行为不一致（一边有设备队列、一边没有），这正是历史上反复漂移的地方。

## 三、最近播放：只有一个写入点

`app/library/PlayJournal.kt`。

之前只有音乐库那一页在起播后写最近播放，于是同一个动作在不同入口结果不同：

| 入口 | 改之前 | 改之后 |
|---|---|---|
| 音乐库点歌 | 有记录 | 有 |
| NAS 目录点歌 | **无** | 有（`NasViewModel.pushQueue`） |
| 歌单播放 | — | 有（走 `startQueue` 后记录） |

规则：**起播之后**才写（设备拒收时不写，没播过不算听过）；去重键交给
`TrackRecord.dedupeKey`（`sourceType:playUri`），不另写一套判重。

## 四、设备卡片状态（内部运转的一环）

`ConsoleViewModel` 每 3 秒逐台读 `getPlayerStatus`，填 `cards: Map<uuid, DeviceCard>`：
曲名、音量、静音、mode、`plicount/plicurr`、是否在放。

两个关键约束：

1. **只轮询路由器侧够得着的设备** —— 入组的从机在主机 AP 网段（`10.10.10.x`），
   从这边连必然超时，跳过去，靠主机的 `slave_list` 表达它的状态；
2. 读到的音量写进 `VolumeBus`，与播放页、音量键共用同一个真相。

## 五、定时关闭放哪（如实说明）

现在跑在 `ConsoleViewModel` 的 `viewModelScope` 里，和原厂的 `CountDownTimer`
同级别 —— Activity 还在就有效，进程被杀就没了。

**没有**放进前台服务：那样能跨进程存活，但会和"原厂语义一致"冲突，
而且服务没起来时反而不生效。如果以后要求"熄屏锁屏也一定准点停"，
再把它挪进 `QueuePlaybackService`（那里本来就有常驻循环）。

## 六、真机验证与边界

| 项 | 结果 |
|---|---|
| 两台设备 PlayQueue 闹钟/U 盘动作 | 500 `401 invalid action`（固件不支持） |
| `getMvRemoteUpdateStatus` | `.153`=22、`.164`=0（命令可用，界面未接） |
| 定时关闭 | 纯客户端，无法真机自动化；协议侧只用到已多次验证的 `setPlayerCmd:stop` |
| 歌单 → 设备 PlayQueue | 复用 PR #35 已闭环验证的同一段 `DevicePlaylistGateway.push`（CreateQueue→PlayQueueWithIndex→BrowseQueue 读回 6 首曲名） |

**没有**在真机上压测歌单下发：两台设备此刻都握着你正在听的队列
（`.153` 122 首、`.164` 300 首），覆盖它们会打断播放。要验的话，
请先说一声，我在你停播的时候做一次「3 首歌单 → 设备 → 读回」的闭环。

## 七、改动清单

- 新增 `core-protocol/player/SleepTimer.kt` + 8 个单测
- 新增 `core-protocol/playlist/PlaylistOps.kt` + 13 个单测
- 新增 `app/library/PlaylistStore.kt`（独立库文件）
- 新增 `app/library/PlayJournal.kt`（最近播放唯一写入点）
- `LibraryViewModel`：`playlists` 状态、新建/加歌/播放整单/改名/删除/移曲、`startQueue` 抽取
- `NasViewModel`：起播写最近播放
- `ConsoleViewModel`：`DeviceCard` 轮询、定时关闭、`playAll`、卡片音量、音乐数量
- `VolumeBus.forget()`
