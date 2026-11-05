# 84 · 蓝牙遥控调研：机顶盒 / 智能马桶等场景 + 给开发的「最优实践与最短路径」

> 写于 2026-11-05 · 依据 main `0c15e34`
> 用户：「按以上，调研蓝牙遥控如机顶盒，智能马桶等场景」
> 承接 `docs/81-infrared-codelibrary-module.md`、`docs/82-infrared-ondemand.md`
>
> **本文只调研，不含代码改动。开发由负责写代码的对话执行。**
> 第 5 节是给开发的「最优实践 + 最短路径」，**开工前先读那一节**。

---

## 零、结论

**蓝牙和红外是两种完全不同的东西：红外有「码库」，蓝牙没有。**

| | 红外 | 蓝牙 |
|---|---|---|
| 命令是什么 | 一段时序，**广播式**，不需要配对 | 一条 GATT 写 / HID report，**点对点会话** |
| 有通用码库吗 | ✅ 有（CC0，30 万条） | ❌ **没有，也不可能有** |
| 复用单位 | 品牌+型号 → 一条码 | **品牌+型号+固件 → 一整套字节协议** |
| 发现方式 | 无（人从列表里选） | 扫描（能看到 MAC / 广播名） |
| 要配对吗 | 不要 | **要**（且会断、要重连） |
| 有回执吗 | 没有 | **有**（GATT write 应答） |

所以「蓝牙遥控」没法做成「装个码库就什么都能控」。它只能做成**「通用连接层 + 每设备一个协议插件」**。

**按场景给结论：**

| 场景 | 最优路径 | 成熟度 | 建议 |
|---|---|---|---|
| **Android TV 盒子**（小米/当贝/Shield/Google TV） | **不是蓝牙**，是 Android TV Remote Protocol **v2**（WiFi + TLS + protobuf） | ⭐⭐⭐⭐⭐ 有官方 AOSP Java 实现 | **最值得做** |
| **智能电视**（带蓝牙的） | BLE/经典 HID 模拟键盘 | ⭐⭐⭐ 库成熟但要试 | 可做，兼容性靠试 |
| **运营商 IPTV 盒子**（电信/移动/联通） | 基本只有**红外** | — | 走 `docs/81` 那条 |
| **智能马桶** | BLE 串口透传 + 逆向协议 | ⭐ 三个型号三个项目，各自 0 星 | 自用可做，产品化不值 |
| **Apple TV** | 也不是蓝牙，是 Companion/AirPlay 协议（WiFi） | ⭐⭐⭐⭐ `pyatv` 1203★ MIT | 可选 |

**一句话：机顶盒别用蓝牙，用 Android TV v2 协议；马桶这类廉价 BLE 家电，能连上但每家都要逆向。**

---

## 一、先纠正 `docs/49` 两处

### 纠正 1 · `BluetoothHidDevice` 不是「BLE HID over GATT」

`docs/49` 写：

> **BLE HID over GATT** | Android TV / 部分投影 | 手机扮演 HID 设备（`BluetoothHidDevice`, API 28+），需实现 HID report descriptor

**这是两种不同的东西，被混在一起了：**

| | `BluetoothHidDevice`（官方） | BLE HID over GATT / HOGP |
|---|---|---|
| 底层 | **经典蓝牙 BR/EDR** HID Device profile | **BLE** GATT |
| API | **28+**，官方公开 | **Android 没有公开外设 API** |
| 怎么做 | `BluetoothAdapter.getProfileProxy` + `registerApp` + `sendReport` | 只能自己用 `BluetoothGattServer` 手工建 HID Service（0x1812） |
| 谁在用 | 官方途径 | `kshoji/BLE-HID-Peripheral-for-Android`（259★ Apache-2.0，API 21+） |

**影响**：如果目标设备只接受 BLE（不认经典蓝牙键盘），`BluetoothHidDevice` 发不出去，得走 HOGP 手工实现。
**做之前必须先确定目标设备认哪一种** —— 这不是文档能查到的，只能试。

### 纠正 2 · 漏掉了机顶盒真正该用的那条路

`docs/49` 的蓝牙章节只给了 BLE HID 和厂商私有 GATT 两条，**漏了 Android TV Remote Protocol v2** ——
而这条路才是机顶盒的主流，且成熟度远高于蓝牙。见下一节。

---

## 二、机顶盒：Android TV Remote Protocol v2（重点）

### 它是什么

Google TV 手机 App 用的就是这套协议。**不需要 ADB、不需要开发者选项**，
只要求设备预装 `com.google.android.tv.remote.service`（多数 Android TV / Google TV 设备都有）。

### 协议事实（从 `tronikos/androidtvremote2` 源码读的）

```python
pair_port: int = 6467     # 配对
api_port:  int = 6466     # 遥控
```

- **TLS 双向认证**：客户端要生成自签证书，用 `load_cert_chain` 带上
  （源码里 `check_hostname = False`、`verify_mode = CERT_NONE` —— 校验被关掉了）
- **配对**：电视上显示 6 位码，客户端输入 → 双方交换证书
- **消息**：protobuf（`remotemessage.proto`，29KB，字段极多）
- **能力**：按键 / 文字输入 / **深链打开 App** / **语音（PCM 16-bit mono 8kHz）**

### 成熟实现

| 项目 | ★ | 许可 | 语言 | 说明 |
|---|---:|---|---|---|
| **`tronikos/androidtvremote2`** | **136** | **Apache-2.0** | Python | **Home Assistant 官方 androidtv_remote 集成用的就是它** |
| `louis49/androidtv-remote` | 88 | MIT | Node.js | v2 协议实现 |
| `phhusson/androidtv-remote-shell` | 13 | — | Go | 命令行版 |
| `nguyluky/ESP32_android_tv_remote_v2` | 17 | — | C++ | **ESP32 上跑通了 → 证明协议可移植，不依赖大运行时** |
| `farshid616/Android-TV-Remote-Controller-Python` | 28 | GPL-3.0 | Python | **v1 旧协议**，别用 |

### 关键：有官方 AOSP 实现可以照抄

`androidtvremote2` 的 README 直接点名：

> Official [implementation](https://android.googlesource.com/platform/external/google-tv-pairing-protocol/+/refs/heads/master)
> of the pairing protocol in Java

**`platform/external/google-tv-pairing-protocol` 是 AOSP 里的 Java 实现，Apache-2.0。**
我们的项目是 Kotlin，**这是最理想的抄写对象** —— 配对那套 TLS/证书交换最容易写错，官方有现成的。

### 为什么它比蓝牙好

| | Android TV v2 | 蓝牙 HID |
|---|---|---|
| 连接稳定性 | WiFi，稳定 | 会断、要重连、要重配对 |
| 能不能读到设备状态 | 能（有回包） | 部分能 |
| 深链打开 App | ✅ 支持 | ❌ 做不到 |
| 语音 | ✅ 支持（PCM） | ❌ 做不到 |
| 需要配对码吗 | 要（6 位，一次性） | 要（系统配对弹窗） |
| 需要用户拿原遥控操作吗 | **不需要** | **需要**（得用原遥控在电视上确认配对） |

最后一条很关键：**蓝牙 HID 配对时，用户必须拿原遥控在电视上点「确认」**。原遥控丢了/坏了，这条路直接走不通。
而 v2 协议只需要在屏幕上看到 6 位码 —— 但**也得能看见屏幕**。

### 适用范围（要说清）

| 设备 | 支持 v2 吗 |
|---|---|
| Android TV / Google TV 盒子（小米盒子国际版、当贝、Shield、Chromecast with Google TV） | ✅ 有 `tv.remote.service` 就能 |
| 国产安卓电视（小米电视、TCL、海信） | ⚠️ **不一定**。国内版常把 Google 服务剥了，需实测有没有那个 service |
| 运营商 IPTV 盒子（电信/移动/联通） | ❌ 基本是 Linux/定制安卓，没有 Google 服务 |
| Apple TV | ❌ 另一套协议（见下） |

**这条必须先探活**：连 6466 端口能不能 TLS 握手成功，比查文档靠谱。

### Apple TV（顺带）

不是蓝牙，是 **Companion Link / AirPlay / MRP** 协议，走 WiFi。
`postlund/pyatv`（**1203★，MIT**，2026-08 还在推）是成熟实现，Python。
如果将来要支持 Apple TV，这是唯一值得抄的对象。

---

## 三、智能马桶：能连，但每家都要逆向

### 我找到的三个项目（都是 HA 集成，都 0 星）

| 项目 | 设备 | UUID | 许可 |
|---|---|---|---|
| `xiaoyaner0201/ha-jomoo-ble` | **九牧 ZS800J** | `0000ffe0` / `0000ffe1` | Apache-2.0 |
| `wfac-hub/vogo-toilet-ha` | vogo 某型号 | `0000ffe1` / `ffe2` / `ffe3` | — |
| `kolomiichenko/ha-viclean` | **Villeroy & Boch ViClean** | `6e400001-b5a3-f393-e0a9-e50e24dcca9e`（**Nordic UART**） | MIT |

**三个设备，三套不同的 UUID。** 但有个规律值得注意：

| UUID | 是什么 | 谁在用 |
|---|---|---|
| `FFE0` / `FFE1` | **HM-10 / CC2541 透传模块**（最常见的廉价 BLE 串口桥） | 九牧、vogo |
| `6E400001…` | **Nordic UART Service (NUS)** | Villeroy & Boch |
| `FFE1` / `FFE2` / `FFE3` | 厂商自定义 | vogo |

**这三个都是「BLE 串口透传」** —— 本质是把 BLE 当一根串口线用，字节流里跑的是厂商自定义命令。

### 逆向成本（用九牧那个项目量化）

```
custom_components/jomoo_ble/lib/protocol.py   623 行 / 18.6 KB（18,644 字节）
custom_components/jomoo_ble/lib/client.py    22.3 KB（22,804 字节）
```

**一个型号 ≈ 40KB 协议代码**（含帧格式、校验、状态位解析、模式映射）。

### 真实世界的坑（从九牧那个项目的代码里读到的）

```python
DEFAULT_POLL_INTERVAL = 0  # 0 = never poll; every connection makes the toilet beep
MIN_POLL_INTERVAL = 300
```

**每次连接马桶都会「嘀」一声。** 所以作者默认不轮询，最短轮询间隔 300 秒。
这类体验问题文档里查不到，只有做过的人知道。

### 安全隐患（必须提）

这些廉价 BLE 家电**通常没有配对、没有鉴权、没有加密** ——
邻居在蓝牙范围内就能连上你的马桶。九牧那个项目的 README 明写「No cloud, no account, no vendor code」，
反过来说就是**也没有任何访问控制**。

真要做，得在 App 层加「只连已保存的设备」，但这挡不住别人用自己的手机连。

### 结论：自用可以，产品化不值

| | 判断 |
|---|---|
| 技术可行吗 | ✅ 可行，已有人做出来 |
| 通用吗 | ❌ 每个型号一套协议，40KB 逆向工作量 |
| 值吗（自用） | ⚠️ 看你有几台、是不是同一型号 |
| 值吗（产品） | ❌ 中国智能马桶品牌上百个、型号上千个，做不过来 |

---

## 四、通用规律：BLE 家电的三层结构

把马桶、灯、按摩椅、风扇这些「廉价 BLE 家电」抽象出来，是三层：

```
① 发现层    扫描 BLE，按广播名/厂商 ID 过滤        ← 完全通用
② 连接层    GATT 连接 + 找服务/特征值              ← 半通用（三类串口桥覆盖大部分）
③ 协议层    字节命令（帧头/长度/校验/命令码/状态）  ← 每家不同，必须逆向
```

**第 ① ② 层可以做成通用模块，第 ③ 层只能一设备一插件。**

这就是「蓝牙做不成码库」的根本原因：红外的第 ③ 层是**公开的物理规范**（NEC 就是 NEC），
蓝牙的第 ③ 层是**厂商私有的应用层协议**，没有标准可循。

---

## 五、给开发对话：最优实践 + 最短路径

**这一节是给负责写代码的对话看的。开工前先读，能省掉几轮返工。**

### 5.1 这个项目已经定下的最优实践（别再发明新的）

| # | 实践 | 依据 | 一句话 |
|---|---|---|---|
| 1 | **先找官方实现，别手搓** | `docs/70` 洛雪、`docs/77` CastPlayer、本文 Android TV v2 有 AOSP Java | 有官方实现就抄结构，没有才自己写 |
| 2 | **单一入口 + 编译期强制** | `docs/52` | 靠 `private constructor` / `internal` / 必填参数，不靠文档约束 |
| 3 | **抽象写了要验装配** | 铁律四 | `MediaProvider` 三次「抽象写了没装配」的教训 |
| 4 | **断言对真值，不对自述** | `docs/76` 三路对账 | app 说自己发了命令 ≠ 设备真的变了 |
| 5 | **诊断台跟着功能一起长** | 铁律一 | 新功能必须同时加诊断段，否则真机出问题只能猜 |
| 6 | **单测 fixture 从真实响应抄** | `docs/80` 信封 bug | 照文档猜的 fixture 会把错误假设固化，测试全绿而真机全挂 |
| 7 | **不给按了没反应的按钮** | 铁律 | 能力位决定渲染，不支持的入口直接不出现 |
| 8 | **归档后全删** | `docs/57` | 删代码前归档源码 + 为什么删 + 怎么恢复 |

### 5.2 本次四件事的最短路径

`docs/83` 第七节排了四项，每项的最短路径是：

| 要做 | ❌ 弯路 | ✅ 最短路径 |
|---|---|---|
| **MediaSession** | 自己写前台服务 + 手搓通知栏 | 直接用 `media3-session` 的 `MediaSessionService`；`MediaSession` 绑 `LocalPlayer.player`。**唯一要注意**：现有 `QueuePlaybackService` 是 `dataSync`，`mediaPlayback` 不能复用（类型和权限一一对应），要新增一个服务 |
| **统一入口** | 做跨源结果合并排序 / 开新 Gradle 模块 | app 层一个 `SearchHub`（~200 行）+ 四源各 30 行适配。**不做合并排序** —— NAS 的《晴天》和网易云的《晴天》不是一回事 |
| **配置备份** | 引入 DataStore 迁移 / Room schema 升级 | 直接 zip `filesDir` 里那几个文件（设备列表 / 收藏 / 歌单 / 电台 / `core-script` 脚本 / `playback-output.txt`）。纯文件操作，`runCatching` 包住 |
| **core-discovery 补测试** | mock 整个网络栈 | 先把 SSDP 报文**解析**拆成纯函数，只测解析。发送侧留着靠真机 |

### 5.3 明确是弯路的（看到就绕开）

| 弯路 | 为什么 |
|---|---|
| 为「架构纯粹」再拆模块 | 8 个已经够多，`docs/83` 已论证短期不需要新模块 |
| 做通用蓝牙遥控 | 每设备 40KB 逆向（九牧马桶 `protocol.py` 623 行的量级），自用最多 2-3 台 |
| 自己实现 HID report descriptor | `BluetoothHidDevice` 已有官方封装；BLE 那侧用 `kshoji` 的库（Apache-2.0） |
| 红外全量码库内置 | 62MB 进不了 APK，索引 199KB + 按需下载才是对的路（`docs/82`） |
| 为 `maven-publish` 牺牲设计 | 底座留着能发就行，**不为它改任何接口** |

### 5.4 提交前自查（三项，CI 已经挡了两项）

```bash
python3 scripts/check-init-order.py          # init 必须在所有类级属性之后
python3 scripts/check-diagnostics-labels.py  # 诊断标签必须是「用户点了什么」
python3 scripts/check-doc-refs.py            # 文档引用必须带路径且文件存在（本次起进 CI）
./gradlew test assembleDebug
```

---
## 六、能不能做成模块

按 `docs/69` 三条判据 + 我在 `docs/81` 提的第四条：

| 判据 | 蓝牙通用层 |
|---|---|
| 1 拖进纯 JVM 装不下的依赖 | ✅ **满足** —— `BluetoothAdapter`/`BluetoothGatt` 是 Android SDK，进不了纯 JVM 底座 |
| 2 有第二个消费者 | ❌ 目前没有 |
| 3 能独立发版 | ⚠️ 勉强 |
| 4 带大体积资源 | ❌ 没有（蓝牙没有码库） |

**判据 1 明确满足** → 该开模块。但要注意它和红外不一样：

| | 红外 `core-ir` | 蓝牙 `core-bt` |
|---|---|---|
| 纯 JVM 部分 | 解析器 + 13 个协议编码器（**绝大部分**） | 几乎没有（协议是字节流，得连着 GATT 才能测） |
| 必须有 Android | 只有 `transmit` 40 行 | **全部**（扫描/连接/GATT/配对） |
| 可测性 | 好（613 单测那种） | **差** —— 需要真设备，或用 mock GATT |

**所以蓝牙模块的可测性天然比红外差一个档次。** 这会影响 CI 能挡多少问题。

### 建议的切法（如果要做）

```
core-bt（Android 模块）
  BtScanner          扫描 + 按广播名/UUID 过滤
  BtLink             GATT 连接 / 断线重连 / MTU / 特征值发现
  SerialBridge       三类串口透传抽象（FFE0 / NUS / 自定义）
  HidDevice          BluetoothHidDevice 封装（经典 HID，API 28+）
  protocol/          每设备一个插件（不进 core-bt，进 app 或独立插件包）

app/protocols/
  JomooToilet.kt     ~40KB 逆向成果（如果真要做）
```

**协议插件必须留在 `app` 或独立包**，不进 `core-bt` —— 否则每加一个设备就要重发一次通用库。

---

## 七、坑清单

| 坑 | 说明 |
|---|---|
| **经典 HID vs BLE HOGP** | `BluetoothHidDevice` 只走经典蓝牙；BLE 外设要自己建 GATT Server。**先确认目标设备认哪种** |
| **蓝牙配对要原遥控** | HID 配对时用户得拿原遥控在电视上点确认。原遥控丢了这条路就断 |
| **连接会断** | 蓝牙连接不是长久的，息屏/距离/干扰都会断。现在的 `SavedDevice(ip)` 装不下「连接态」 |
| **扫描权限** | Android 12+ 要 `BLUETOOTH_SCAN` + `BLUETOOTH_CONNECT` **运行时权限**，还要 `neverForLocation` 标志 |
| **后台扫描被限** | Android 8+ 后台扫描有频率限制；Android 13+ 更严 |
| **BLE 吞吐低** | 默认 MTU 23 字节，要 `requestMtu` 才能到 500+。大数据要分包 |
| **GATT 写要串行** | 同时多个 write 会失败，必须排队 |
| **廉价 BLE 家电无鉴权** | 马桶/灯这类通常无配对无加密，邻居能连 |
| **每次连接有副作用** | 九牧马桶每次连接会「嘀」一声 —— 别做定时轮询 |
| **Android TV v2 要看见屏幕** | 配对码显示在电视上，看不见就没法配 |
| **国内安卓电视未必有 Google 服务** | `com.google.android.tv.remote.service` 可能被剥掉，必须探活 |

---

## 八、建议

### 排序（按「值得做」）

| 优先级 | 做什么 | 理由 |
|---|---|---|
| **1** | **Android TV Remote v2**（WiFi，不是蓝牙） | 有 AOSP 官方 Java 实现可抄；成熟度最高；能做深链和语音；机顶盒场景真正对路 |
| 2 | 红外（`docs/81`/`82`） | 码库 CC0、可离线、覆盖电视/空调/机顶盒 |
| 3 | 蓝牙 HID 模拟键盘 | 覆盖面广但兼容性靠试，且配对要原遥控 |
| 4 | 智能马桶这类 BLE 家电 | 一家 40KB 逆向，自用可以，产品化不值 |

**注意排序**：机顶盒场景下，**Android TV v2 排在蓝牙前面**。
如果先做蓝牙 HID，做完会发现国产盒子和运营商盒子都不认。

### 最小验证路径（零成本）

1. **探 6466 端口**：拿局域网里任何一台安卓电视/盒子，`nc -zv <ip> 6466`。
   通了 → v2 协议大概率可用，值得做。
2. **扫 BLE 看广播名**：手机装 nRF Connect 扫一圈，看你家设备都暴露了什么服务
   （是不是 FFE0 / NUS 这类串口桥）。这决定「通用连接层」有没有意义。
3. **查 `BluetoothHidDevice` 支不支持**：`BluetoothAdapter.getProfileProxy(..., BLUETOOTH_HID_DEVICE, ...)` 返回非 null 就是支持。

**这三步都不需要写代码，用现成工具就能做，但能定掉后面 80% 的方向。**

---

## 九、一句话

**红外是「一条码」，蓝牙是「一套协议」——所以蓝牙永远不会有码库。机顶盒这个场景真正该用的是 Android TV v2 协议（WiFi + protobuf，有 AOSP 官方 Java 实现），不是蓝牙；智能马桶这类能连上，但每个型号要逆向 40KB，只适合自用。**

---

## 附：本次核实过的仓库

| 仓库 | ★ | 许可 | 用途 |
|---|---:|---|---|
| `tronikos/androidtvremote2` | 136 | Apache-2.0 | **Android TV v2 协议（推荐照抄）** |
| `louis49/androidtv-remote` | 88 | MIT | 同协议 Node 实现 |
| `nguyluky/ESP32_android_tv_remote_v2` | 17 | — | 证明协议可移植到 MCU |
| `postlund/pyatv` | 1203 | MIT | Apple TV 协议 |
| `kshoji/BLE-HID-Peripheral-for-Android` | 259 | Apache-2.0 | BLE HOGP 外设（Android 无公开 API，靠它手工建 GATT） |
| `xiaoyaner0201/ha-jomoo-ble` | 0 | Apache-2.0 | 九牧马桶 BLE（FFE0 透传） |
| `wfac-hub/vogo-toilet-ha` | 0 | — | vogo 马桶 BLE |
| `kolomiichenko/ha-viclean` | 0 | MIT | V&B 马桶 BLE（Nordic UART） |
| `bertrandmartel/bluetooth-remote-control` | 31 | MIT | BLE HID 遥控（docs/49 原有引用） |

**排除一个**：搜索结果里出现的 `yaleedhaque/BluetoothRemoteHid` 实际 404（仓库不存在），
搜索引擎的结果不可直接信，必须 `gh api` 核实。
