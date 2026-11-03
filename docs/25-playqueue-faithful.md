# 25 · 播放列表：完整复刻原厂 PlayQueue 机制

用户明确要求「完整模仿原 APK 的方法」。所以这一轮不再缝补自研方案，而是把原厂的
队列机制逐字挖出来照做。

## 一、原厂到底怎么做的（逐字还原）

反编译三条线索：

| 线索 | 位置 |
|---|---|
| 队列服务与动作名 | `com.wifiaudio.g.b.b.e`：`CreateQueue` / `AppendTracksInQueue` / `DeleteQueue` / `PlayQueueWithIndex` / `GetQueueIndex` / `BrowseQueue`，服务 `urn:schemas-wiimu-com:service:PlayQueue:1` |
| QueueContext XML 生成 | `org.teleal.cling.support.c.a.l#a(...)` |
| 单曲 DIDL 生成 | `org.teleal.cling.support.c.a.e.d#a(...)` |
| 开机从设备读回并替换本地 | `com.wifiaudio.g.bf#a(ay)` → `com.wifiaudio.g.bk`（`queryCurrentQueueAndReplaceLocalQueue`） |

### QueueContext 的真实结构

```xml
<?xml version="1.0" ?>
<PlayList>
  <ListName>队列名</ListName>
  <ListInfo>
    <Radio>0</Radio>
    <SourceName>队列名</SourceName>
    <TrackNumber>6</TrackNumber>
    <SearchUrl></SearchUrl>
    <Quality>0</Quality>
  </ListInfo>
  <Tracks>
    <Track1>
      <Source>队列名</Source>
      <URL>曲目直链</URL>
      <Id>0</Id>
      <Key></Key>
      <Metadata>&lt;DIDL-Lite …&gt;</Metadata>   ← 转义过的 DIDL
    </Track1>
    <Track2>…</Track2>
  </Tracks>
</PlayList>
```

`Metadata` 里的 DIDL-Lite（再被转义一次）：

```xml
<DIDL-Lite xmlns:dc="…" xmlns:upnp="…" xmlns:song="www.wiimu.com/song/"
           xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/">
  <upnp:class>object.item.audioItem.musicTrack</upnp:class> <item>
  <res protocolInfo="http-get:*:audio/mpeg:DLNA.ORG_PN=MP3;DLNA.ORG_OP=01;" duration="0">URL</res>
  <dc:title>…</dc:title> <upnp:artist>…</upnp:artist>
  <upnp:album>…</upnp:album> <upnp:albumArtURI>…</upnp:albumArtURI>
  </item> </DIDL-Lite>
```

### 为什么以前做不到（道歉式复盘）

之前记录「本固件不支持整列表推送」，试了 100+ 种 `QueueContext` 全被拒。真正的错误是三条：

1. `<Tracks/>` 只能空 —— 实际每首必须包成 **`<Track1>`、`<Track2>`…标签名带序号**；
2. `Metadata` 必须是**二次转义**的 DIDL（DIDL 转义一次 → 塞进 QueueContext 再转义一次）；
3. 队列名必须与 `PlayQueueWithIndex` 里的 `QueueName` 严格一致，且要先 `CreateQueue`。

## 二、真机验证（`.153` / fw 3.6.5310，2026-11-03）

```
DeleteQueue(LinkPlayConsole)      → 200
CreateQueue(6 首的 QueueContext)   → 200
PlayQueueWithIndex(QueueName, 4)   → 200
getPlayerStatus                    → plicount=6 plicurr=4 mode=10 status=play
BrowseQueue(LinkPlayConsole)       → 6 首，曲名全部正确
                                     LastPlayIndex=4 → 0 基 3 → 第 518 首 ✓
GetQueueIndex                      → CurrentIndex=4（1 基）
未知队列名                          → 500 UPnPError（据此判"没有队列"）
```

**关键点**：整个读回过程**不依赖手机**——把 App 杀掉之后，设备仍然握着这份列表，
`BrowseQueue` 照样能读出 6 首曲名。这正是用户要的「重开 App 列表同步设备」。

另外一个真机细节：`PlayQueueWithIndex` 的 `Index` 是 **1 基**。传 3 播的是第 3 首，
传 4 才是第 4 首。代码里统一用 0 基入参、发送时 +1。

## 三、实现

| 新文件 | 作用 |
|---|---|
| `core-protocol/upnp/QueueContextXml.kt` | QueueContext 生成与解析（含单曲 DIDL），纯 JVM |
| `core-protocol/upnp/PlayQueueClient.kt` | CreateQueue / PlayQueueWithIndex / BrowseQueue / GetQueueIndex / DeleteQueue |
| `app/playback/DevicePlaylistGateway.kt` | 上层的推送与读回，含"读回校验条数" |

行为变化：

- **推送列表**：`CreateQueue` + `PlayQueueWithIndex` 为主路径；m3u 只是设备没有 PlayQueue 服务时的兜底；
- **恢复列表**：启动时 `BrowseQueue` 把设备上的**整份列表（含曲名）**读回来，本地快照只做兜底；
- **跳位**：设备持有列表时用 `PlayQueueWithIndex`（原厂做法），不再自己投流；
- **显示**：播放页列表旁标注「设备列表 N 首 · 第 i 首」，来源可辨。

## 四、测试

- `QueueContextXmlTest` **10 个**（含**真机返回原文**作为样本：标签带序号、Metadata 二次转义、LastPlayIndex 优先于 RealIndex、控制字符清洗、再转义一层仍可解析）
- 全量 **369 用例 0 失败**

## 五、遗留

- 主机 `.164` 仍离线，多房间未复测
- 设备不回填 `getPlayerStatus.Title`（仍 `Unknown`），但队列里的曲名由 `BrowseQueue` 提供，界面已不再依赖它
