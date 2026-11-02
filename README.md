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
| 协议层单元测试 | ✅ 72 个用例 |
| AP 直连配网（4 步流程 + 结果轮询） | ✅ 已实现 |
| UPnP 播放控制（投流 / 播放暂停停止 / 跳转 / 音量 / 进度） | ✅ 已实现（1s 轮询，非 GENA） |
| MCU 灯效 / 音箱固件 OTA | 🚧 待做 |

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
