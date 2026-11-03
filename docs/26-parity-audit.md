# 26 · 全量对比原厂：该照做的照做，该保留的保留

目标：**减少自研比重、贴近原厂做法**，同时保留我们的优点。下面每一项都标了依据
（反编译出处 / 真机实测），以及"为什么这样定"。

## 一、协议层逐项对比

先看原厂到底用了多少命令 —— 把 `classes.dex` 的全部字符串过了一遍，
它其实只用了 **7 条 httpapi 命令**，其余**全部走 UPnP**：

```
httpapi（仅 7 条）
  getStatusEx / getNetwork / setDeviceName:<n> / wlanGetApListEx
  setPlayerCmd:switchmode:<mode> / getMvRemoteUpdateStatus / multiroom:*
UPnP（主要路径）
  AVTransport: Play / Pause / Stop / Next / Previous / Seek
               SetAVTransportURI / GetPositionInfo / GetMediaInfo / GetInfoEx
  RenderingControl: GetVolume / SetVolume / GetMute / SetMute
  PlayQueue: CreateQueue / AppendTracksInQueue / ReplaceQueue / DeleteQueue
             PlayQueueWithIndex / GetQueueIndex / BrowseQueue
  MediaServer: Browse（NAS 浏览）
  DeviceProperties: GetSimpleDeviceInfo
```

### 逐项对照

| 能力 | 原厂 | 我们（改造前） | 本轮结论 |
|---|---|---|---|
| 设备信息 | `getStatusEx` + UPnP `GetSimpleDeviceInfo` | `getStatusEx` + description | ✅ 一致 |
| 播放/暂停/停止 | UPnP `AVTransport` | UPnP 优先 + httpapi 兜底 | ✅ 一致（兜底是我们优点） |
| **上一首/下一首** | UPnP `AVTransport.Next/Previous` | httpapi `setPlayerCmd:next/prev`（**自研**） | 🔧 **已改为 AVTransport** |
| 跳转 | UPnP `Seek` | UPnP `Seek` | ✅ 一致 |
| **音量/静音** | UPnP `RenderingControl` | httpapi `setPlayerCmd:vol/mute`（**自研**） | 🔧 **已改为 RenderingControl 优先** |
| 进度/时长 | UPnP `GetPositionInfo` | UPnP `GetPositionInfo` | ✅ 一致 |
| **播放队列** | UPnP `PlayQueue` 全套 | 先是客户端逐首投流，后是 m3u 命令（**都是自研**） | 🔧 **已完整复刻 PlayQueue**（PR #35） |
| 单曲投流 | UPnP `SetAVTransportURI` | 同 | ✅ 一致 |
| 输入源切换 | httpapi `switchmode` | 同 | ✅ 一致 |
| 多房间 | httpapi `multiroom:*` | 同（`ConnnectAP`/`SlaveVolume`/`SlaveChannel`…） | ✅ 一致 |
| Wi-Fi 扫描 | `wlanGetApListEx` | 同 | ✅ 一致 |
| 设备改名 | `setDeviceName` | 同 | ✅ 一致 |
| 网络信息 | `getNetwork` | 同 | ✅ 一致 |
| NAS 浏览 | UPnP `ContentDirectory.Browse` | 同 | ✅ 一致 |
| 固件更新检查 | `getMvRemoteUpdateStatus` | 有 `firmwareUpdateStatus()`，界面未用 | ⚠️ 半实现 |
| 闹钟 | UPnP `AlarmQueue` 系列 | 未做 | ❌ 缺口 |
| U 盘 / TF 卡 | `BrowseQueueUSBDisk` + mode 11/16 | 只在来源显示里映射 mode | ❌ 缺口 |
| 灯效 | MCU 串口（8899）`MCUDispatchThread` | 未做 | ❌ 缺口（价值低） |
| 语音唤醒 | 讯飞 `Wakeuper` | 不做（闭源 SDK + 权限） | ⏭️ 不做 |

## 二、本轮实际改动

### 1. 上一首/下一首改为 `AVTransport.Next/Previous`

之前是 `httpAct("下一首", "setPlayerCmd:next")` —— 自研组合。原厂走
`AVTransport.Next`，**只有这样才能按设备持有的 PlayQueue 切歌**；
用 httpapi 那条在老固件上只挪"当前曲索引"，与队列不同步。

代码：`PlaybackEngine` 新增 `next()/previous()`；UPnP 实现走 AVTransport，
httpapi 实现走 `setPlayerCmd:next/prev`（仅在引擎不可用时）。

真机：`AVTransport.Next/Previous` → 200。

### 2. 音量/静音改为 `RenderingControl` 优先

新增 `DeviceVolumeControl`：按 IP 缓存 `UpnpRendererClient`（避免每按一次键都拉
`description.xml`），`SetVolume/SetMute` 走 UPnP，失败才退回 httpapi。

真机：`GetVolume=27` → `SetVolume(45)` → 回读 `45`，httpapi `getPlayerStatus` 也是 `45`
（两条通道一致，说明此前"界面 60 实际 40"的隐患确实存在）。

### 3. 保留的自研部分（明确理由）

| 自研 | 为什么保留 |
|---|---|
| httpapi 兜底通道 + 自动降级 | 设备 UPnP 偶发不可达时仍能控制；原厂没有这层，但这是纯增益 |
| 内嵌封面/歌词解析（ID3/FLAC/MP4） | 原厂不读文件标签；NAS 的 M4A 实测能读出 59KB 封面 |
| m3u 兜底（`setPlayerCmd:playlist`） | 设备没有 PlayQueue 服务时的最后手段 |
| 客户端排队（`ClientSideQueue` + 前台服务） | 设备拒收整列表时的兜底；也是"循环模式"唯一实现途径 |
| 对比度门禁、单测体系、`VolumeBus`/`QueueBus` 单一状态源 | 工程质量，不动 |

## 三、还没对齐的（下一步候选，按价值排序）

1. **闹钟**（`AlarmContext` + `SetAlarmQueue`）：原厂有，我们完全没有。
2. **U 盘/TF**（`BrowseQueueUSBDisk`）：插 U 盘时能浏览播放。
3. **固件更新检查**接到界面上（`getMvRemoteUpdateStatus` 已有命令实现）。
4. **灯效**（MCU 串口 8899）：只有带灯机型有意义，优先级最低。

## 四、验证

- `UpnpRendererClientTest` 新增 3 个：`Next/Previous` 必须是 AVTransport、
  `GetMute` 带 `Master` 通道、`SetVolume` 走 RenderingControl 而**不是** httpapi
- 真机（`.153`）：`SetVolume/GetVolume`、`Next/Previous` 全部 200 且数值一致
- 全量单测见 PR 检查结果
