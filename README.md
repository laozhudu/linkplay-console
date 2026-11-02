# LinkPlay Console

LinkPlay / WiiMu 方案 Wi-Fi 音箱的**现代化控制台**：Kotlin + Jetpack Compose 重写，
替代 2015 年那批 minSdk 10、只有 32 位 native 库、连 Android 14 都装不上的厂商 App。

[![android-ci](https://github.com/laozhudu/linkplay-console/actions/workflows/android.yml/badge.svg)](https://github.com/laozhudu/linkplay-console/actions/workflows/android.yml)

> 这是一个 **clean-room 重写**项目：只使用从原厂 App 静态分析中重新表述的**协议事实**
> （命令名、参数格式、返回字段、编码规则），不含任何反编译源码。详见
> [docs/01-linkplay-protocol.md](docs/01-linkplay-protocol.md) 的说明。

## 状态

| 能力 | 状态 |
|---|---|
| SSDP 设备发现 + 手动 IP | ✅ 已实现 |
| 设备信息（`getStatusEx`） | ✅ 已实现 |
| 多房间从机列表 / 音量 | ✅ 已实现 |
| 改名 / 输入源切换 / 恢复出厂（二次确认） | ✅ 已实现 |
| 原始命令控制台 | ✅ 已实现 |
| 协议层单元测试 | ✅ 225 个用例 |
| AP 直连配网（4 步流程 + 结果轮询） | ✅ 已实现 |
| UPnP 播放控制（投流 / 播放暂停停止 / 跳转 / 音量 / 进度） | ✅ 已实现（1s 轮询，非 GENA） |
| 本地音乐整列表推送 + 列表循环（私有 PlayQueue） | ✅ 已实现 |
| 手机本地媒体 HTTP 服务（Range/HEAD） | ✅ 已实现 |
| **局域网 DLNA 发现 NAS 媒体库 + 浏览 + 列表推送** | ✅ 已实现；**真机验证 3/3 连续播放**（音箱日志逐首拉流） |
| 音箱控制面自愈（UPnP 掉线 → `reboot` 恢复，约 40s） | ✅ 已接线（队列中断时提示并一键重启） |
| 记忆上次的 NAS 与浏览路径（按设备 UUID） | ✅ 已实现 |
| 断点续播（队列快照） | ✅ 已实现 |
| MCU 灯效 / 音箱固件 OTA | 🚧 待做 |
| 搜索 / 收藏 / 最近播放 / 网络电台 | ✅ 已实现（音乐库页） |
| 拼音首字母搜索 | ✅ 已实现（纯 JVM，无第三方依赖） |
| 设备预设键 1-6 | ✅ 已实现 |

## 模块

```
app/              Compose UI（发现页 + 设备详情 + 命令控制台）
core-discovery/   SSDP 发现（Android library）
core-protocol/    协议客户端与模型（纯 JVM，可单测、可复用为 AAR）
docs/             协议规格、功能裁剪决策、架构说明
```

## 构建

```bash
./gradlew :core-protocol:test      # 协议单测（无需 Android SDK）
./gradlew :app:assembleDebug       # 产出 app/build/outputs/apk/debug/*.apk
```

要求：JDK 17、Android SDK（compileSdk 35）。CI 每次 push 自动跑测试并上传 APK artifact。

## 用法（真机）

1. 手机与音箱接同一局域网（**不要**跨 VLAN/网段，设备控制面是明文 HTTP 无鉴权）；
2. 打开 App → 「SSDP 搜索局域网设备」；若搜不到（组播被隔离）→ 手动输入设备 IP；
3. 点设备进详情页：可看固件/联网状态、改名、切输入源、调从机音量；
4. 设备详情顶部是**播放控制**：投一条音频直链给音箱、播放/暂停/停止、拖进度、调音量；
5. 「原始命令」框可执行任意 `httpapi.asp?command=`，排障用。

> 播放控制走 UPnP，需要 SSDP 提供的描述地址（LOCATION）。手动输入 IP 的设备没有这个地址，
> 卡片会说明原因而不是假装能用。

### 防盗链直链也能投（本机转发）

音箱拉流时只发 `curl/7.36.0` 的裸 GET，不带 Referer/Cookie，所以平台直链必然 403。
`RelayMediaServer` 会替它补头并把 Range 接住：播放页勾「需要 Referer（自动转发）」即可。
真机已验证（设备拉到的是本机 `/s/<token>.mp3`，进度正常递增）。详见
[docs/16-lxmusic-feasibility.md](docs/16-lxmusic-feasibility.md)。

### 界面

播放页重做为「封面主导 + 曲名派生渐变 + 浮动主控」：进度/音量拖动松手才下发、循环模式本地先变、
上一首/下一首/停止齐全。图标全部 Canvas 手绘（省 3MB 依赖）。视觉规范与许可证边界见
[docs/15-visual-design.md](docs/15-visual-design.md)。

> 开源里好看的音乐 App 基本是 GPL-3.0（Gramophone / Auxio / Rhythm），**代码不能抄**；
> 唯一可复制的设计系统是 Apache-2.0 的 `material-3-expressive-catalog`。

### 防盗链直链也能投（本机转发）

音箱拉流时只发 `curl/7.36.0` 的裸 GET，不带 Referer/Cookie，所以平台直链必然 403。
`RelayMediaServer` 会替它补头并把 Range 接住：播放页勾「需要 Referer（自动转发）」即可。
真机已验证（设备拉到的是本机 `/s/<token>.mp3`，进度正常递增）。详见
[docs/16-lxmusic-feasibility.md](docs/16-lxmusic-feasibility.md)。

### 界面改造节奏

**结构先行，视觉最后**：现在只固定主题令牌与页面职责，配色细节/字体/动效留到功能齐了再做
（理由见 [docs/12-ui-phasing.md](docs/12-ui-phasing.md)）。

### 安装与升级

APK 采用**固定签名** + `versionCode` 随提交递增，因此新版可以直接覆盖安装，不需要先卸载。
细节（含签名指纹、密钥备份、报错对照）见 [docs/11-install-and-upgrade.md](docs/11-install-and-upgrade.md)。

> 如果你装过 0.8.0 及更早的版本（那批包每次构建换一把随机 debug 钥匙），需要**卸载一次**，
> 之后就能一路覆盖升级。

### NAS 列表推送（推荐用法）

音箱能直接拉 NAS 的流，所以「局域网发现媒体 + 列表推送」不需要手机当文件服务器：

1. 设备详情页 → 「NAS 媒体库：浏览并推送整列表」
2. 搜索 DLNA 服务器（或手填 `192.168.99.223:8200`）→ 逐层进目录 → 勾选 → 「加入队列」
3. 「推送给音箱」：本固件不支持音箱侧排队，改用**客户端逐首投流**（剩余 2 秒切下一首）

细节与实测数据见 [docs/10-nas-dlna-push.md](docs/10-nas-dlna-push.md)。

### 真机实测（DOSS DS-1831）

首次真机验证记录见 [docs/08-device-field-report.md](docs/08-device-field-report.md)：
单曲投流 + 本地 HTTP 服务「音箱真的来拉流」已实测通过；
整列表推送的方向被推翻（该固件的 `QueueContext` 是自定义 `<PlayList>` 方言，且不接受曲目节点），
设备还多出一个 QPlay 服务（QQ 音乐通道，被认证挡住）。
跨 VPN 的可行性调研见 [docs/09-vpn-reachability.md](docs/09-vpn-reachability.md)。

### 给新音箱配网

1. 系统 WLAN 里连上音箱热点 `LinkPlay_xxxx`（此时手机没外网，正常）；
2. App 首页点「新音箱？走 AP 直连配网」；
3. 「自动查找音箱」→ 先 SSDP，失败再试常见默认地址；也可直接填地址；
4. 选目标 Wi-Fi（或手输 SSID）→ 输密码 → 开始配网；
5. 页面会显示「第 N 次探测 / 已等待 X 秒」。**音箱切网期间原地址会短暂失联，属正常**；
6. 成功后把手机切回自家 Wi-Fi，回首页 SSDP 搜索即可。

> 老固件多不支持 5GHz，优先选 2.4GHz 网络。

## 安全提醒

- 设备侧 `httpapi.asp` **完全无鉴权**，同一局域网任何主机都能恢复出厂、控制音量、
  触发固件刷写。这不是本 App 能修的问题（在固件里），只能在网络侧隔离：
  把音箱放到独立 IoT VLAN/访客网络，禁止其访问主网段与外网。
- 本 App 不访问任何公网服务，所有请求只发往你输入的设备 IP。

## 生态

这批设备是标准 DLNA 渲染器，因此**能被成熟生态直接控制**（Music Assistant、Lyrion + UPnPBridge、
Symfonium、OwnTone、Feishin-connect 等），也**能从成熟内容源取歌**（Navidrome/Jellyfin 的 OpenSubsonic API）。
四套接入路线的取舍、逐项依据与待验证清单见 [docs/04-ecosystem-integration.md](docs/04-ecosystem-integration.md)。

功能对照（原 App 有什么、我们有什么、漏了什么）见 [docs/05-feature-parity.md](docs/05-feature-parity.md)；
音源方案（能否参考洛雪 LX Music）见 [docs/06-music-sources.md](docs/06-music-sources.md)。

## 给其他项目复用

`core-protocol` 是**纯 JVM**（无 Android 依赖），可发布成 `linkplay-protocol` JAR；
`core-discovery` 是 Android AAR。入口是 `LinkPlayDevice` 门面（探测能力 → 自动选通道）。
对接方式、Python 侧语义契约、真机联调清单见 [docs/07-integration-guide.md](docs/07-integration-guide.md)。

## 证据与复现

协议事实来自对 `com.wifiaudio.doss` ver1.6.5（2015-11-14 构建，SHA-256
`1f21fac2…4198d`）的静态分析，工具链为 [REA](https://github.com/morluto/rea) 6.8.0
+ jadx-headless-mcp 0.7.1。要点：

- 清单/组件/权限：`inspect-android-package`
- 签名方案：`inspect-apk-signature`（仅 v1，`CN=doss` 自签）
- 命令与常量定位：`search-android-classes` + 代码级搜索（`search_in=code`）
- 未确证项已在协议文档 §10 明确列出，不做推测性断言。

## 许可

代码：MIT（见 LICENSE）。文档：[CC BY 4.0](https://creativecommons.org/licenses/by/4.0/)。
本项目与 DOSS / WiiMu / LinkPlay 无关联，未使用其商标、图标或代码。
