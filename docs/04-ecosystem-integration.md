# 生态接入调研：这套东西能接哪些成熟音乐项目

> 范围：只做调研，不改代码。所有结论都标了「依据」与「未验证」。
> 调研时间：2026-11；结论会随上游项目变化而失效，改动前请回到链接复核。

## 0. 结论先行

| 问题 | 答案 |
|---|---|
| 我们的设备能被成熟生态**控制**吗？ | **能**。这批音箱是标准 DLNA/UPnP 渲染器，Music Assistant、Lyrion、Symfonium、OwnTone、Feishin（fork）都能当控制端直接推流给它。 |
| 我们能否让 App **接成熟内容源**？ | **能，而且应该**。Navidrome / Jellyfin / Music Assistant 都提供 OpenSubsonic 或标准 API，不必自建曲库。 |
| 我们自研的协议层有没有复用价值？ | **有，但方向要反过来**：打包成设备能力库（Kotlin AAR / Python 包），供生态补齐「老 LinkPlay 固件」适配，而不是再做一个播放器 App。 |
| Home Assistant 官方 WiiM 集成能直接用吗？ | **不一定**。官方文档明确「其他 LinkPlay 产品不保证兼容」，需要 `_linkplay._tcp.local.` 与新接口。我们的 2015 设备是否满足**未验证**。 |
| 最省力的落地路径？ | 先把音箱接进 Music Assistant（DLNA player provider），拿成熟 UI 用起来；App 退化为「配网 + 排障 + 私有能力（MCU 灯效/固件 OTA）」工具。 |

## 1. 我们的设备在生态里的位置

逆向结论（见 `docs/01-linkplay-protocol.md`）说明它有两条控制面：

```
        ┌────────────────────────────┐
        │   LinkPlay 音箱（2015 固件） │
        └───────────┬────────────────┘
     UPnP/DLNA      │        httpapi.asp（私有，官方已文档化）
   （标准，任何人可用）│        （WiiM 官方 API v1.2 + 社区整理）
        ┌───────────┴────────────────┐
        │                            │
  成熟控制端可直接用              自研 App 的差异化能力
  （MA/LMS/Symfonium/OwnTone）    （配网、MCU 灯效、固件 OTA、多房间私有命令）
```

**关键判断**：一旦确认设备是标准 DLNA 渲染器，生态里所有「能推流给 DLNA」的软件都能控制它——我们不需要把它们的能力再实现一遍。

## 2. 可接入的成熟项目（按角色分类）

### 2.1 内容源 / 服务端（让 App 有歌可放）

| 项目 | 接口 | 依据 | 备注 |
|---|---|---|---|
| **Navidrome** | Subsonic API v1.16.1 + 持续补 OpenSubsonic 扩展 | navidrome.org/docs/developers/subsonic-api/ | 自托管最主流；「任何支持 OpenSubsonic 的客户端都能用」 |
| **Jellyfin** | 自有 REST + 部分 Subsonic 兼容 | 未逐条核实 | 生态客户端多，字段语义与 Subsonic 有差异，接入前要实测 |
| **Music Assistant** | 作为内容聚合层，自身支持 OpenSubsonic 源（官方称测过 Gonic/Navidrome） | music-assistant.io/music-providers/subsonic/ | 对「一个 App 管所有源」最省事 |

**对我们的意义**：App 侧只要实现一个 OpenSubsonic 客户端（`getAlbumList2`/`stream`/`getCoverArt`），就同时获得 Navidrome / Gonic / 大部分 Subsonic 服务端。**不需要**自己写媒体库、扫描、元数据抓取。

### 2.2 播放控制端（把音推给我们的音箱）

| 项目 | 形态 | 能否控制老 LinkPlay | 依据 |
|---|---|---|---|
| **Music Assistant** | 自托管服务 + Web/App 前端 | **很可能可以**：DLNA Player Provider 自动发现 UPnP/DLNA 设备即用 | music-assistant.io/player-support/dlna/ |
| **Lyrion Music Server (LMS)** | 自托管 + 插件 | 可以：官方「UPnP/DLNA bridge」插件让 DLNA 设备冒充 Squeezebox（squeeze2upnp） | lyrion.org/plugins/directory/ 、lyrion.org/players-and-controllers/ |
| **Symfonium** | 付费 Android App | 可以：一个 App 支持 Chromecast / UPnP-DLNA / Kodi / Sonos 多种投射目标 | symfonium.app/android-music-player-sonos-chromecast-dlna/ |
| **Feishin (+ feishin-connect fork)** | 桌面（Electron） | 可以（fork 才加了投射）：给播放栏加 cast 按钮，推 Sonos/Chromecast/AirPlay/DLNA | github.com/mihaitom/feishin-connect |
| **OwnTone** | 自托管服务 | 可以：cast 到 DLNA / Chromecast / AirPlay / Sonos | 社区实践（Reddit r/navidrome）与项目自身能力 |
| **upmpdcli + MPD** | 自托管（Linux/Docker） | 反向用法：把 MPD 变成 DLNA 渲染器；也可 library 模式接 Subsonic | github.com/triplem/upmpdcli |
| **Kodi / VLC** | 桌面/盒子 | 可以（作为 UPnP 控制点/渲染器） | 通用能力，未逐条核实版本细节 |

**Music Assistant 的两个细节值得记住**（因为它们正好命中最老固件）：
- DLNA 播放器默认开 **queue flow mode**（多数 DLNA 播放器不会自己排队下一首）；
- 提供 **"Replace Pause with Stop"** 开关，专门给「忽略 pause 命令的老 UPnP 播放器」用。
  → 这暗示：**老设备上 `Pause` 可能无效**，我们的 App 也该准备「暂停退化为停止」的兜底。

### 2.3 Home Assistant（智能家居落地，不用写 App）

Home Assistant 官方文档站收录了 **WiiM** 集成（`home-assistant.io/integrations/wiim/`）：

- 支持「宣传 `_linkplay._tcp.local.` zeroconf 服务、并暴露 WiiM UPnP/HTTP 控制接口」的 WiiM 与 Audio Pro 设备；
- 能力：播放控制、音量/静音/输入源、预设播放、**多设备分组同步**；
- 依赖：同一局域网 + mDNS 放行 + HA 配置了可用内网 URL（设备要回调 UPnP 事件）。

**风险点（必须实测）**：文档原文是「Other LinkPlay-based products are **not guaranteed** to be compatible」。
我们的设备是 2015 年 LinkPlay 白标（`com.wifiaudio.doss`），SSDP 里有厂商服务 `urn:schemas-wiimu-com:service:PlayQueue:1`，
但**没有证据**表明它广播 `_linkplay._tcp.local.`。所以：**能不能被 HA 自动发现 = 未知，需要真机验证**。

另有社区实现 **mjcumming/wiim**（基于自研 `pywiim`，强调全异步 + multiroom），可作为「官方集成不适配时的替代」。

### 2.4 底层库（决定我们该不该继续自己写 UPnP）

| 库 | 许可 | 依据 | 结论 |
|---|---|---|---|
| **jUPnP**（`org.jupnp`） | **CDDL-1.0** | github.com/jupnp/jupnp（badge 与仓库说明） | Cling 的官方后继；要写完整 UPnP 栈就用它 |
| **Cling**（`org.teleal.cling`/`4thline`） | 已停止维护 | github.com/4thline/cling | 老设备 App 用的就是它，不要在新项目里继续用 |
| 我们现在的实现 | MIT | 本仓库 | 只做「控制点 + 需要的动作」，约 600 行，无反射、纯 JVM 可测 |

**注意许可差异**：CDDL-1.0 与 MIT/Apache 不同（文件级 copyleft），引入 jUPnP 会改变我们的分发义务；
现在的自研控制点是有意为之——它只覆盖 AVTransport/RenderingControl 几个动作，不需要通用 UPnP 栈。

## 3. 四条可选路线（含取舍）

### 路线 A：App 做「OpenSubsonic 客户端 + DLNA 控制点」

- 内容来自 Navidrome/Jellyfin，播放推给音箱。
- 优点：一次实现，服务端随便换。
- 缺点：**正面撞 Symfonium / Feishin / Music Assistant**，它们已经成熟且覆盖多投射目标。我们的差异化只剩「这批老固件的配网/灯效/OTA」。
- 工作量：中（+8~15 人日：登录、库浏览、搜索、封面缓存、流式播放）。

### 路线 B（推荐长期）：把 `core-protocol` 做成「LinkPlay 老固件能力库」

- 产物：Kotlin AAR（Android/服务端可复用）+ Python 包（对齐 `pywiim` 的接口风格）。
- 卖给生态的不是 UI，而是**这些别人没有的适配**：
  - AP 直连配网（`ConnectMasterAp` 的大写/小写 hex 差异）
  - 多房间 `multiroom:*` 私有命令（含原厂拼写错误 `ConnnectAP`）
  - MCU 灯效帧（`MCU+PAS+LIGHT-<n>&`）
  - 老固件的「Pause 无效 → 退化为 Stop」等行为差异
- 受益方：Music Assistant（可加一个 LinkPlay 专用 player provider）、HA 自定义集成、LMS 桥接。
- 优点：**补的是生态缺口**，不是重复造轮子。
- 前提：至少一台真机验证（当前一条都没实测，这是硬阻塞）。

### 路线 C（推荐短期）：不写 App，直接用成熟生态

- 音箱接 Music Assistant（DLNA provider）→ 拿到完整库管理、多房间、UI；
- 或者接 LMS + UPnPBridge；桌面用 Feishin-connect，手机用 Symfonium。
- 我们的 App 退化为**配网 + 排障 + 私有能力**工具（它已经有这些东西）。
- 优点：当天可用；缺点：依赖第三方服务常驻，且灯效/MCU 这类私有能力它们不管。

### 路线 D：把 MPD/upmpdcli 装到小主机当渲染器

- 这是「让别的东西变成 DLNA 渲染器」的反向用法，与我们的音箱无关，**不适用**（列在此避免混淆）。

## 4. 建议

1. **立刻做路线 C**：把音箱接进 Music Assistant，验证 DLNA 通路是否真的听得到声音——这是所有后续判断的前提，也是唯一能顺手完成的真机验证。
2. **App 维持现状定位**：配网 + 设备信息 + 播放控制 + 排障控制台，不再扩张成通用播放器。
3. **路线 B 作为长期差异化**：等真机验证过 MCU/多房间/配网差异后，再决定是否发布 AAR/Python 包。
4. **路线 A 只做最小版**（可选）：接 OpenSubsonic 只为「让 App 能直接放自己的歌」，
   不做库管理 UI，避免和 Symfonium/Feishin 正面对撞。

## 5. 待验证清单（按优先级）

| # | 待验证 | 验证方法 | 阻塞什么 |
|---|---|---|---|
| 1 | 设备能否被 Music Assistant DLNA provider 发现并出声 | 装 MA → Player Providers → DLNA → 看是否自动出现 | 路线 C 全部 |
| 2 | 老固件的 `Pause` 是否有效 | 用 UPnP 播放后发 Pause，看状态 | 是否要加「Pause→Stop」兜底 |
| 3 | 是否广播 `_linkplay._tcp.local.` | 局域网 mDNS 抓包（`avahi-browse -a` / `dns-sd -B`） | HA 官方 WiiM 集成能否用 |
| 4 | `getPlayerStatus` 在这台固件上是否可用 | `curl http://<ip>/httpapi.asp?command=getPlayerStatus` | httpapi 通道能否兜底 |
| 5 | 多房间 `multiroom:*` 命令是否生效 | 需要第二台同系设备 | 多房间功能 |
| 6 | `chext` 与 `extch` 的取舍 | 抓配网成功与失败时的 channel 值 | 配网成功率 |

## 6. 明确未核实项（不做断言）

- Music Assistant / Feishin / OwnTone / LMS 的**具体开源许可**未逐一核实，商用或再分发前需自行确认。
- Jellyfin 对 Subsonic API 的兼容程度未核实。
- Symfonium 为付费闭源 App，仅确认其官网声明支持 UPnP/DLNA 投射目标。
- 「这些软件能否控制 2015 年 LinkPlay 固件」全部属**协议层推断**，没有实测证据。
