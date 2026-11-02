# 集成指南：把这一层接进别的项目

> 读者：想把 LinkPlay/WiiMu 设备（含 2015 那批白标固件）接进自己软件的人，或者想给
> Music Assistant / Home Assistant / Lyrion 这类项目补一个 provider 的人。
>
> 目标是让对接方**只看一页就能开工**：调什么、能问什么、哪些不确定。

## 0. 分工：什么该复用，什么不该

| 你的目标 | 该用什么 | 不要做什么 |
|---|---|---|
| 控制设备（播放/音量/队列/配网） | 本库 `linkplay-protocol` | 别自己逆向一遍 httpapi，尤其别漏掉 `RangStart` 这类拼写坑 |
| 管音乐库、播放在线内容 | Navidrome/Jellyfin/Music Assistant | 别在本层塞流媒体接口（合规 + 维护成本，见 docs/06） |
| 智能家居联动 | Home Assistant 的 WiiM 集成 / 社区 `mjcumming/wiim` | 别重复实现 HA 的实体模型 |
| 做 UI | 自己写 | 本库不含 UI |

## 1. 产物与坐标

| 产物 | 类型 | 坐标（本地 Maven） |
|---|---|---|
| `core-protocol` | 纯 JVM JAR（无 Android 依赖） | `io.github.laozhudu.linkplay:linkplay-protocol:0.5.0` |
| `core-discovery` | Android AAR（SSDP 发现） | `io.github.laozhudu.linkplay:linkplay-discovery:0.5.0` |

生成到本地仓库：

```bash
./gradlew :core-protocol:publishMavenPublicationToLocalRepository \
          :core-discovery:publishReleasePublicationToLocalRepository
# 产物在 core-protocol/build/repo 与 core-discovery/build/repo
```

消费方：

```kotlin
repositories {
    maven { url = uri("/path/to/linkplay-console/core-protocol/build/repo") }
}
dependencies {
    implementation("io.github.laozhudu.linkplay:linkplay-protocol:0.5.0")
    // Android 端再加：
    implementation("io.github.laozhudu.linkplay:linkplay-discovery:0.5.0")
}
```

服务端/桌面端（非 Android）只用 `linkplay-protocol` 就够——它是**纯 JVM**，
已用 `./gradlew :core-protocol:test` 的 150 个用例证明不需要 Android 运行时。

## 2. 主入口：`LinkPlayDevice`

```kotlin
// 1) 拿设备。location 来自 SSDP 的 LOCATION；手动输入 IP 时为 null（降级，见 §3）
val device = LinkPlayDevice.open(ip = "192.168.1.20", location = "http://192.168.1.20:49152/description.xml")

// 2) 先问能力，再决定走哪条通道
val caps = device.detectCapabilities()
println(caps.summary())   // httpapi=✓ upnp=✓ playqueue=✓ httpPlayer=✗

// 3) 设备信息与设置
val status = device.status()          // LinkPlayStatus：ssid/firmware/uuid/essid/internet...
device.rename("客厅音箱")
device.switchInput("bluetooth")       // wifi / line-in / bluetooth / optical
device.pressPreset(3)                 // MCUKeyShortClick:3（面板 1-6，官方文档到 12）
device.rawCommand("getNetwork")       // 兜底：任何 httpapi 命令

// 4) 播放（UPnP 优先，自动退 httpapi）
val engine = device.playback() ?: error("无可控通道")
engine.playUrl("http://192.168.1.5:8123/t/1", "夜曲")
engine.setVolume(35)
engine.snapshot()                     // PlaybackSnapshot：state/title/artist/position/duration/volume

// 5) 整列表推送（私有 PlayQueue）
val queue = device.queue() ?: error("设备不支持队列")
queue.createQueue(didlOrContext)
queue.setQueueLoopMode(QueueLoopMode.ALL)
queue.playQueueWithIndex("linkplay-console", 0)
```

### 线程模型（重要）

**所有方法都是阻塞 IO**。调用方必须自己在后台线程执行。
Android 参考实现：`app/.../library/LibraryViewModel.kt`（`withContext(Dispatchers.IO)`）。

本库**不做 GENA 事件订阅**，状态靠调用方轮询 `snapshot()` / `status()`。
轮询间隔参考：播放中 1 秒，空闲 5 秒以上。

## 3. 通道矩阵：什么时候哪条能用

| 场景 | httpapi | UPnP 播放 | PlayQueue | httpapi 播放 |
|---|---|---|---|---|
| 2015 白标固件 + SSDP 发现 | ✅ | ✅ | ✅（多数） | ❌ |
| 2015 白标固件 + 手输 IP（无 LOCATION） | ✅ | ❌ | ❌ | ❌ |
| 2022+ WiiM 固件 | ✅ | ✅ | ✅ | ✅ |

**结论**：如果是「扫描到的设备」就传 `location`，能用上播放控制与队列；
手输 IP 的设备只能设备管理（改名/切换输入源/原始命令）——这不是 bug，
是「没有描述文件就不知道 controlURL」的必然结果。UI 要如实告知用户。

## 4. 协议事实（对接时必须知道的部分）

完整规格见 `docs/01-linkplay-protocol.md`；这里是「不看长文也要知道」的六条：

1. **所有通道都是明文 HTTP/TCP，无鉴权**。设备固件如此，客户端改不了。部署侧应把这些设备放进独立 VLAN。
2. `httpapi.asp?command=...` 的参数编码**不一致**：`ConnectMasterAp` 用大写 HEX，
   `wlanConnectApEx` 用小写 HEX，`setDeviceName` 用 URL 编码。改了就失败。
3. `getStatusEx` 的 `essid` 是 **HEX**；`apcli0` 在官方文档里是 **IPv4 地址**（不是 MAC）。
4. PlayQueue 的 `RangStart`/`RangEnd` **少一个 e**；`ConnnectAP`（配网）**少一个 c**。
5. 两套循环模式编号不同：队列 `SetQueueLoopMode` 用 `0/1/2/3`，
   httpapi `setPlayerCmd:loopmode` 用 `0/1/2/-1`（列表循环在队列是 2，在 httpapi 是 -1）。
6. `setPlayerCmd:seek:<秒>` 单位是秒；UPnP `Seek` 的 `Target` 是 `H:MM:SS` 文本。

## 5. 给 Python 侧（HA / Music Assistant provider）的移植契约

不必依赖 JVM：语言无关的**语义契约**如下，照它实现即可与 Kotlin 版行为一致。

| 能力 | 调用 | 期望 |
|---|---|---|
| 发现 | SSDP M-SEARCH 到 `239.255.255.250:1900`，ST 依次 `urn:schemas-wiimu-com:service:PlayQueue:1`、`urn:schemas-upnp-org:device:MediaRenderer:1`、`ssdp:all` | 收集回包里的 `ip` 与 `LOCATION` |
| 探活 | `GET http://<ip>/httpapi.asp?command=getStatusEx` | JSON；`internet=="1"` 表示已联网 |
| 能力 | 解析 `LOCATION` 的 XML `<serviceList>` | 有 AVTransport → 可播放；有 `urn:schemas-wiimu-com:service:PlayQueue:1` → 可整列表 |
| 播放 | UPnP `POST <controlURL>` + `SOAPACTION: "<serviceType>#<action>"` | action: `SetAVTransportURI` → `Play` |
| 音量 | UPnP RenderingControl `SetVolume(Channel=Master, DesiredVolume=0-100)` | |
| 队列 | PlayQueue `CreateQueue(QueueContext)` → `PlayQueueWithIndex(QueueName, 0)` | |
| 状态 | `GetTransportInfo` + `GetPositionInfo`（`RelTime` 是 `H:MM:SS`） | 别用 GENA，轮询即可 |
| 兜底 | `setPlayerCmd:play:<url>` / `pause` / `resume` / `stop` / `vol:<n>` | 老固件可能 404，要能优雅降级 |

**注意**：`QueueContext` 的内部结构**至今未确证**（原厂只是把字符串塞进去）。
Kotlin 版用 `QueueContextFormat` 三选一策略；Python 侧建议同样做成可配，先用
`DIDL_ONLY`（纯 DIDL-Lite 文档）试，不行再换 `NAME_THEN_DIDL`。

## 6. 本层已实现 / 未实现（对接前请确认边界）

**已实现（有单测覆盖）**
- httpapi 命令集（设备信息、设置、播放兜底、EQ/闹钟/预设命令的拼装）
- UPnP 描述解析 + AVTransport/RenderingControl 控制点 + SOAP fault 解析
- 私有 PlayQueue 10 个动作
- AP 直连配网 + 凭据预校验 + 下发后轮询确认状态机
- 本地媒体 HTTP 服务（HEAD + Range 206，供 renderer 拉流）
- 曲库模型/收藏/最近/电台/拼音首字母搜索（纯 JVM，可跨端复用）

**未实现（别指望）**
- GENA 事件订阅（明确不做，理由见 docs/01 §7.2）
- 完整拼音（只有首字母；完整拼音需要 2 万字音表）
- MCU 灯效通道（TCP 帧已记录，未实现客户端）
- 音箱固件 OTA 轮询、闹钟、语音、悬浮窗
- 任何流媒体平台接口

## 7. 真机联调清单（第一次接进去请按这个顺序）

| # | 步骤 | 通过标准 |
|---|---|---|
| 1 | `getStatusEx` | 返回 JSON 且 `internet` 可解析 |
| 2 | 解析 LOCATION | 拿到 `avTransportControlUrl` |
| 3 | `GetTransportInfo` | 返回 `PLAYING/STOPPED` 之一 |
| 4 | `GetVolume` | 返回 0-100 |
| 5 | `SetAVTransportURI` + `Play`（一条公网直链 mp3） | 设备出声 |
| 6 | `SetQueueLoopMode` + `PlayQueueWithIndex` | 队列能连续播放 |
| 7 | Range 请求 | `curl -H "Range: bytes=0-1023"` 返回 206 |
| 8 | 断电重连 | 队列丢失属正常，App 需提供「重推」 |

第 5 步不出声时，先换成公网直链排除「手机 HTTP 服务不可达」这个变量，
再看设备是否要求 DIDL metadata（部分固件缺 `upnp:class` 就不播）。
