# 局域网 DLNA 媒体发现与 NAS 列表推送

> 这一轮把「音箱放歌」的主链路从「手机当文件服务器」换成了「NAS 当媒体服务器」——
> 因为真机证明音箱能直接拉 NAS（比让音箱连手机稳得多，也是 docs/09 里唯一能过 VPN 的思路）。

## 1. 发现：局域网里到底有什么

| 位置 | 服务 | 结论 |
|---|---|---|
| `192.168.99.223:8200` | **fnOS 的 DLNA（MiniDLNA 系）**，`friendlyName=fnos` | ✅ 主力媒体库 |
| `192.168.99.124:8080` | 也有 `rootDesc.xml` | ⚠️ 存在但本次未深入（描述里没有 friendlyName 字段） |
| `192.168.99.153:49152` | DOSS DS-1831（MediaRenderer，**含 QPlay**） | ✅ 播放端 |
| 其它存活主机 | 无 MediaServer 服务 | — |

SSDP 搜索 `urn:schemas-upnp-org:device:MediaServer:1` 就只回一个：**`.223:8200`**。

## 2. NAS 的内容结构（实读）

```
Browse(0) = 4 个容器：Browse Folders(64) / Music(1) / Pictures(3) / Video(2)
Music(1) 下：Album / All Music / Artist / Folders / Genre / Playlists / Recently Added
All Music(1$4) = 1931 首（TotalMatches=1931）
```

曲目形态（`res` 属性齐全，可直接给 renderer 用）：

```json
{"title":"000封神演义宣传片",
 "url":"http://192.168.99.223:8200/MediaItems/24.mp3",
 "protocolInfo":"http-get:*:audio/mpeg:*",
 "size":"3278636","duration":"0:02:14.131","bitrate":"192000"}
```

## 3. 关键验证：音箱直接拉 NAS

```
SetAVTransportURI(http://192.168.99.223:8200/MediaItems/24.mp3) → Play
GetTransportInfo  → PLAYING
GetPositionInfo   → TrackDuration 00:02:14，RelTime 递增
```

**结论：媒体面彻底不需要手机参与。** 这也是 docs/09 的判断在真机上得到印证：
内容放在局域网服务器上，音箱自己拉，控制端在哪都无所谓。

## 4. 列表推送：固件不支持队列，改成「客户端排队」

`PlayQueue` 在这台固件上只接受「在线源描述符」（docs/08 §5.2），设备里那个名为 `Douban` 的
队列串 `<Tracks></Tracks>` 里**不放内容就是合法的**，放了任何曲目节点都会被拒。
所以列表推送改用控制端编排：

```
逐首 SetAVTransportURI + Play
   → 每秒读 GetPositionInfo
   → 剩余 ≤ 2 秒时推下一首
```

实现：`core-protocol/queue/ClientSideQueue.kt`（`probe`/`sleep`/`now` 全部注入，可用假时钟测）。
与 Music Assistant 的 "queue flow mode" 同一思路。

### 单元测试抓到的真实 bug

第一版把「读不到位置」当成「已播完」，于是不断切歌，**每首都只播几秒就被打断**。
测试直接暴露（期望播 1 首，实际播了 4 首）。修正后分三种结局：

| 结局 | 处理 |
|---|---|
| `FINISHED`（接近结尾 / STOPPED） | 推下一首 |
| `ABORTED`（连续读不到位置、或长时间未开播） | **中止队列**，不盲目切歌 |
| `CANCELLED`（外部要求停止） | 立即退出 |

## 5. 实测中发现的设备问题（重要）

| 现象 | 证据 | 影响 |
|---|---|---|
| **UPnP 端口 49152 会消失** | 播放期间可用；做完一轮队列/QPlay 探测后 `connection refused`，30 秒内反复检查持续关闭，而 `httpapi`(80) 一直 200 | 所有播放控制走 UPnP，因此**必须实现自动重连/降级**，不能假设一次连上就一直在 |
| UPnP 不在 80 端口 | `:80/description.xml` → 404，`:80/upnp/control/*` → 404 | 没有「换个端口」的兜底，只能等它恢复或用 httpapi 兜底（`setPlayerCmd:*`，但本固件不支持） |
| 私有端口常开 | `8899`(uart_pass)、`8819`(communication) | 可能是 MCU 直通/内部通信，未触碰 |
| 电量在掉 | `battery_percent` 60 → 70 → …（充电中/波动） | 便携机；低电可能进休眠导致 UPnP 关闭 |

**建议**：App 侧对 UPnP 用「探活 + 指数退避重试」，并在 UI 上把「UPnP 掉线」和「设备离线」分开提示
（httpapi 能通就说明设备在线，只是控制面掉了）。

## 6. 固件能否升级（调研结论：不建议动）

### 6.1 设备侧证据

| 探测 | 结果 | 解读 |
|---|---|---|
| `getStatusEx.VersionUpdate` / `NewVer` / `mcu_ver_new` | `0` / `0` / `0` | 设备自己认为**没有可用新版本** |
| `getMvRemoteUpdateStatus` | `22` | OTA 接口存在并响应，但无更新 |
| `getMvRomDownloadStatus` | 全 0，`new_ver:"0"` | 无下载任务、无新版本号 |
| `getMvRomBurnPrecent` | `{"status":"0","progress":"0"}` | 无烧写 |
| `SystemCommand+onlineupgrade_check`（GET 与 POST、`+` 与 `%2B` 都试过） | `unknown command` | **该构建没有实现 `SystemCommand` 家族**：Web UI 里那些升级按钮是 LinkPlay 的通用模板，本固件未接线 |
| `getfwinfo` | `unknown command` | 同上 |

当前固件：`3.6.5310`（`Release 20180110`），`hardware A31`，`mcu_ver 126`，`dsp_ver 6.120.1.11`。

### 6.2 外部渠道（社区事实）

- **A31 模块是 LinkPlay 的通用音频模块**。社区（Arylic 论坛）提到 A31 固件已到 `4.6.x`，
  但那是 **Arylic 自家产品**的构建；把别的厂商构建刷进 DOSS 设备，会丢掉 DOSS 的 UI/音效/MCU 映射，
  而且 **MCU(126) 与 DSP(6.120.1.11) 是和固件成套配对的**，跨版本刷写有变砖风险。
- 有开源替代：`hn/linkplay-a31`（OpenWrt + 设备树）。代价是**完全失去原厂功能**（无 DLNA/QPlay/灯效/MCU），
  等于把音箱变成一块普通 Linux 板。
- DOSS 官网目前只售消费级蓝牙/Wi-Fi 音箱，**没有看到 DS-1831 的固件下载页**（检索未命中）。

### 6.3 结论

| 选项 | 可行性 | 风险 | 建议 |
|---|---|---|---|
| 官方在线升级 | ❌ 本机构建无 `SystemCommand`，且设备认为自己已最新 | — | 放弃 |
| 刷 Arylic A31 固件 | ⚠️ 硬件同族 | **高**：掉功能、MCU/DSP 不匹配、可能变砖 | 不做 |
| 刷 OpenWrt（hn/linkplay-a31） | ⚠️ 有开源方案 | 高：失去全部音频功能 | 除非当玩具，否则不做 |
| **不升固件，客户端绕过** | ✅ 已做到 | 无 | **推荐**：我们已经有完整控制 + NAS 推流，升级收益几乎为零 |

一句话：**这台设备的固件基本停在 2018 年，没有可靠的升级通道；但也不需要升级**——
限制（不能推列表）已经被客户端排队解决了。

## 7. 后续开发建议（按性价比排序）

| 优先级 | 建议 | 理由 |
|---|---|---|
| **P0** | **UPnP 断线自愈**：探活 + 指数退避 + 「设备在线但控制面掉线」的独立提示 | 本轮实测到的最大稳定性问题，不做的话用户会觉得「时好时坏」 |
| **P0** | 记住 NAS（按 UUID 而非 IP）+ 记住上次浏览路径 | 每次重新搜索很烦 |
| P1 | 队列持久化 + 断点续播（Room 存队列与索引） | 手机锁屏/杀进程后能从原处继续 |
| P1 | 后台播放守护：把队列编排放进前台服务（现有 `MediaServerService` 可扩展） | 编排在 ViewModel 里，退到后台可能被挂起 |
| P2 | 搜索：ContentDirectory 的 `Search` 动作（MiniDLNA 支持） | 1931 首靠翻目录太低效 |
| P2 | 封面：`upnp:albumArtURI` 已在解析，但 UI 没显示 | 体验提升明显、成本低 |
| P2 | 定时停止 / 睡眠定时 | 便携音箱夜间常用 |
| P3 | 多音箱同步（`multiroom:*`） | 需要第二台设备才能验证 |
| P3 | 局域网自动发现全部设备 + NAS 后给出「一键听歌」入口 | 减少操作步骤 |
| — | 灯效 / 闹钟 / 语音 | 见 docs/05，收益低于上面这些 |

## 8. 复现命令

```bash
# 1) 找 NAS
#    SSDP ST=urn:schemas-upnp-org:device:MediaServer:1 → LOCATION
# 2) 读描述拿 ContentDirectory controlURL（本例 /ctl/ContentDir）
# 3) 浏览根 → Music → All Music
# 4) 取 res 的 URL 直接投给音箱
```

本轮用的临时脚本在 `/root/.hermes/cache/scratch/`：`dev153_soap.py`、`dev153_queue_live.py`；
NAS 侧探测是内联 python（Browse 六参数见 `ContentDirectoryClient`）。
