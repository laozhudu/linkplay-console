# 交接卡（HANDOVER）

> 最后更新：2026-11-04 · **779 单测 0 失败** · 版本号见下方表格（**APK 版本 = 提交数**，所以这里不写死）
> 新对话接手请**从头读完这一页**，再去看 `docs/52-dev-conventions.md`（硬规则）。

---

## 一、这是什么

**声控台** —— 一个用 Kotlin + Compose 重写的 Android 应用，用来控制家里三台
DOSS DS-1831 音箱（LinkPlay / WiiMu 方案）。

起点：逆向 2015 年的闭源 APK（`com.wifiaudio.doss`，包名 `990803.apk`）。
现在是一个功能对齐原厂、但结构现代化的自用 App。

- 仓库：`https://github.com/laozhudu/linkplay-console`（public，gh 账号 `laozhudu`）
- 本地：`/root/.hermes/workspace/linkplay-console`
- **仅个人使用、不分发**；不内置平台音源、不复用原厂凭据

---

## 二、当前状态（一句话）

**功能基本齐了，正在做结构整理。** 三台音箱全部实测在线可用，播放/音量/声道/多房间都通。

| 项 | 数值 |
|---|---|
| 提交数 | `git rev-list --count HEAD` |
| 单元测试 | **779 个，0 失败** |
| 文档 | 71 篇（`docs/01`～`docs/71`） |
| APK | `0.9.<提交数>`，versionCode 同值 |
| 模块 | 4 个（见下） |

---

## 三、三台音箱实测基线（2026-11-04 本次验收）

**三台全部在线，全部是 DS-1831（硬件 `DS_1831/A31`）。**

| | doss3 | doss1 | doss2 |
|---|---|---|---|
| IP | `192.168.99.153` | `192.168.99.164` | `192.168.99.181` |
| uuid（24 位） | `FF31F0174FE5C9B2A2ACEAE3` | `FF310096A5760623673F81F1` | `FF31009611F6457672B5250D` |
| 固件 | **3.6.5310**（20180110，旧） | 3.6.6623（20190123） | 3.6.6623（20190123） |
| 热点 SSID | `DOSS DS-1831_ADB4` | `DOSS DS-1831_400C` | `DOSS DS-1831_D6FC` |
| 音量 | 39 | 45 | 29 |
| 声道 | 0（立体声） | 0 | 0 |
| 电量 | 100% 接电 | 100% **用电池** | 10% 接电 |
| 播放 | stop | **load，122 首第 1 首** | stop |
| 组网 | 无 | 无 | 无 |

### 验收实测（可复现）

```
GetControlDeviceInfo  .153 200 2980B 54ms | .164 200 3119B 43ms | .181 200 3118B 49ms
getPlayerStatus       .153 200  273B 18ms | .164 200  487B 31ms | .181 200  273B 28ms
```

→ **取数最短路径 = 这两条，合计 60～85ms/台**，`GetControlDeviceInfo` 的
`<Status>` 里已内嵌 `DeviceName` / `battery` / `battery_percent` / `firmware`，
不需要单独再发 `getStatusEx`。

### uuid 归一化规则验证通过

三台的 `upnp_uuid` 全部符合 **`<uuid24><前8位重复>`** 结构：

```
FF31F0174FE5C9B2A2ACEAE3 → ff31f0174fe5c9b2a2aceae3ff31f017   ✓
FF310096A5760623673F81F1 → ff310096a5760623673f81f1ff310096   ✓
FF31009611F6457672B5250D → ff31009611f6457672b5250dff310096   ✓
```

判据是**结构吻合**（32 位且末 8 位 == 前 8 位），不是"长度 32 就截断"——
标准 GUID 不会误伤。实现在 `core-protocol/identity/UuidMatch.canonical()`。

### 遗留观察（未定论）

`.181` 的电量两次读取分别是 **20%** 和 **10%**（相隔几分钟）。可能上报不稳，
也可能真在掉电。**下次验收时留意**，若持续掉就要查是不是电池老化。

---

## 四、模块结构（4 个箱子）

| 模块 | 文件 | 行数 | 里面装什么 |
|---|---|---|---|
| `core-protocol` | 74 | 8825 | 所有"怎么指挥音箱"的门道（协议、UPnP、多房间、播放队列、媒体服务） |
| `core-discovery` | 1 | 252 | 在局域网里把音箱找出来（SSDP） |
| `core-diagnostics` | 10 | 605 | 诊断（**零依赖纯 JVM，可独立发布**） |
| `app` | 71 | 16941 | 界面 + 状态 + 各功能页 |

### app 内部已拆开的结构

```
MainActivity.kt        68   只管开机、音量键、崩溃记录
Nav.kt                 18   一级页/二级页名单
ConsoleApp.kt         331   导航骨架 + 底部栏 + 音量浮层
SourcesScreen.kt       40   媒体库页
home/HomeScreen.kt    607   首页（还能再拆）
home/DeviceCardRow.kt 384   设备卡片
home/MiniPlayerBar.kt  99   迷你播放条
ConsoleDiagnostics.kt 232   诊断五段注册 + 报告拼装
session/SessionRestore.kt 213 重开 App 恢复上次设备 + 播放列表
```

### 还大的文件（按优先级）

| 文件 | 行数 |
|---|---|
| `ConsoleViewModel.kt` | **1183** ← 最大的 |
| `PlaybackViewModel.kt` | 852 |
| `PlayerScreen.kt` | 843 |
| `home/HomeScreen.kt` | 608 |
| `library/LibraryScreen.kt` | 600 |
| `nas/NasViewModel.kt` | 552 |

---

## 五、硬规则（违反过的地方都留了记录）

### 规则 1 · 诊断台必须跟着功能一起长

**用户明确要求：任何开发动作必须伴随诊断台进化。**

原因：这个项目**没有真机自动化测试**。验证只有两条路——单测（碰不到网络/设备/界面）
和**用户截图给我的诊断报告**。诊断台是唯一的眼睛，盲区就是只能靠猜的地方。

每加一个有状态/会失败的东西，同一批改动里必须有：
1. 注册一段诊断（`AppDiagnostics.sections.register`）
2. 失败路径记一条（`Severity.ERROR`/`WARN`）
3. **成功路径也记**（带耗时，`log.timed`）—— 否则"没反应"和"根本没触发"分不开
4. 动作名 = **用户做了什么**（`playQueueItem`），不是类名（CI 有脚本挡）

### 规则 2 · 诚实优先

- 「实测通过 / 实测打脸 / 未确证」分开写，不混
- **「命令回 OK」≠「功能生效」**，已坑过四次：
  `chext=0` vs `1`、`resume` 回 OK 但 status 仍 stop、https 电台回 OK 但没播、
  改名回 OK 但被 SSID 覆盖回去
- 单测全绿 ≠ 功能可用

### 规则 3 · 一致性靠「单一入口 + 编译期强制」

不靠文档、不靠自觉：`private constructor` + 唯一工厂、`internal` 限可见性、
必填参数（`safeLaunch(source)`）、CI 静态检查。

### 规则 4 · 删代码前先归档

完整源码 + 为什么删 + 怎么恢复，写进 `docs/archive/`，再删。

---

## 六、已知陷阱（都踩过）

| 陷阱 | 后果 | 做法 |
|---|---|---|
| `init` 写在属性之前 | Kotlin 按声明顺序初始化，协程先跑访问 null → NPE **静默杀掉整个探活** | `init` 放最后；`check-init-order.py` 挡 |
| `by lazy` 滥用 | 引入「delegate 未初始化就被访问」整类故障 | 只在真需要延迟时用 |
| `viewModelScope.launch` 裸用 | 未捕获异常 = **进程崩溃** | 一律走 `safeLaunch(source)` |
| 界面按设备类型判断 | 加新设备时每个判断点都要加分支，必漏 | 按 `DeviceCapabilities` 能力位判断 |
| 一次脚本批量替换标签 | 会串行（曾把 4 个心跳写成 `acquireMulticastLock`） | 替换后跑 CI 检查 + 抽查 |
| 脚本清理"未使用 import" | 删掉 `getValue`/`setValue`（`by remember` 隐式调用）→ 编译炸 | 清理后补回 |
| 按行号切文件边界错一行 | 把下一个函数的注释带进来 → 语法错误 | 边界 = 上一个函数最后一个 `}` |
| 文件末尾放顶层 `data class` | `check-init-order.py` 把它的 4 空格 `val` 当成类级属性 → 误报「init 之前还有属性」 | 状态载体类另放一个文件（`RadioOutcome` 就是因此挪到 `RadioSourceStatus.kt` 的） |
| **线程池里的裸任务抛异常** | `CrashGuard` 的默认处理器对**任何线程**生效 → 记完交给系统**杀进程**。音箱断流被当成崩溃，表现是"听本地音乐切歌就闪退" | 推流服务在线程池入口用 `serve()` 兜底并计数；新加线程池任务一律先包（`docs/59`） |

---

## 七、怎么跑

```bash
export ANDROID_HOME=/opt/android-sdk
cd /root/.hermes/workspace/linkplay-console

# 全量验证
./gradlew --no-daemon :core-protocol:test :core-diagnostics:test \
    :app:testDebugUnitTest :app:assembleDebug
python3 scripts/check-init-order.py
python3 scripts/check-diagnostics-labels.py
```

**数用例，不要只看 BUILD SUCCESSFUL**（脚本见 skill `linkplay-console-workflow`）。

### 交付流程

```
feature 分支 → gh pr create → 等 CI 绿 → squash merge → main 再验一遍 → 下载 APK 核对
```

APK 核对三件事：包名/版本号、签名指纹 `dc3f1f24…2e9507`、应用名「声控台」。
产物放 `/root/.hermes/workspace/linkplay-console-<ver>-debug.apk`，删上一版。

### 签名

`/root/.hermes/keys/linkplay-release.jks` + `pass.txt`（RSA 4096，10000 天）

---

## 八、诊断台现在能看到什么

```
[崩溃记录]      上次崩溃的堆栈（只留前 16 行）
[设备]          三台的在线状态、能力档、音量/声道/电量、**取数通道**（哪条没答）
[启动恢复]      上次那台设备找回没有、队列从哪来（设备列表/本地快照）、耗时
[NAS]           扫到几个服务器、连的哪个、列目录几项、**实际走了哪条推流路线**
[本地曲库]      权限授没授、扫描几首、**0 首是没权限还是真没歌还是全被跳过**
[在线曲库]      ping 通没过、搜索条数与耗时、**连续 3 次 0 条才判"库空"**
[电台]          最近播放的 **scheme（http/https）**、结果与耗时；https 会直说"音箱不认"
[配网]          扫到几个 AP、下发的 SSID、**"命令回 OK"与"等到 IP"分开写**
[语音]          识别原文 → 意图名 → 执行结果（三段必须分开）
[悬浮窗]        权限 / 服务在不在跑 / 窗口实际宽高（0 尺寸会被点出来）
[界面]          当前在哪一页、有没有浮层挡着（← 最新加，覆盖"点了没反应"最常见误报）
[指令]          最近发出去的口令、成没成、为什么失败、耗时（← 最新加）
[队列]          归属哪台、运行中、设备持有、偏移、控制丢失
[后台服务]      队列播放 / 本地媒体 / 悬浮控制 跑没跑 + **推流统计**
                （请求/完成/中断/失败、最近中断原因、已发多少字节）
[运行期问题]    按严重度分级，头部给 E/W/I 汇总
```

**全文同时进 logcat**（`adb logcat -s LinkPlayDiag`）：每条记录一行，
报告按 3500 字符分块打全文，不用截图。

**七个盲区已于 `docs/65` 批次 1 全部补齐**（NAS #70、本地曲库 #74、在线曲库 #75、
电台 #76、配网 #78、语音 #80、悬浮窗 #81）。每一段都按同一形状做：
`SourceFailure` 分类 + 一个 `XxxSourceStatus` 单例 + 在 `ConsoleDiagnostics` 注册一段 +
一组**断言报告文本**的用例。加第八个源照抄即可。

---

## 九、待办（按优先级）

### ✅ P1 · 拆 `ConsoleViewModel` —— 本轮做完了（1409 → 1183）

搬走两块，详见 `docs/56`：
- 会话恢复 → `session/SessionRestore.kt`（213 行，**含恢复结果状态**）
- 诊断注册 + 报告拼装 → `ConsoleDiagnostics.kt`（232 行）

剩下的部分（`scan` / `addManual` / `identify` / 音乐计数 / `probeSaved`）
**全都和"设备是什么"绑在一起**，仍然先停：`DeviceRegistry` 要等
"设备"从 `SavedDevice(ip)` 变成多地址模型（见第十节）再动。
现在拆等于给即将变形的模型先装箱，将来要拆两次。

### P2 · 拆 `home/HomeScreen.kt`（608 行）

还能分三块：崩溃横幅（45）、聚合播放条（50）、4 个对话框（200）。
**需要状态提升**（对话框用到的 `menuTarget`/`sleepMenu` 等要传参），
比纯搬代码风险高，一次只动一块。

### ✅ P3 · 补诊断盲区 —— 已完成（七个源全齐）

`docs/65` 批次 1 收工：NAS #70、本地曲库 #74、在线曲库 #75、电台 #76、
配网 #78、语音 #80、悬浮窗 #81。

模式已固定，加新源照抄：`SourceFailure` 分类 + 一个 `XxxSourceStatus` 单例 +
在 `ConsoleDiagnostics` 注册一段 + 一组**渲染断言**（测报告里那句话，不测内部字段）。

下一批是 `docs/65` 的批次 2（零风险四项）与批次 3（内容源标准化）。

### P4 · 功能欠账

- **DLNA 投流真机验证**（`.175` 电视盒子，会出声几秒）—— 等用户点头
- **tt-music 扫库**（`/vol2/1000/ttmusic/music/local_lib`，现在曲库是空的）—— 在 `.124` 上做，不在 App 侧
- **语音 / 悬浮窗 / WPS** 三项真机点验
- 电台模块接入（`radiostation-discovery` 源码；**必须按 `scheme == http` 过滤**，
  音箱不认 https）

### P5 · 诊断页用上分级

报告文本有 `[E]/[W]` 了，但界面还是整段等宽文本，没做按级别过滤与错误高亮。

---

## 十、未来的方向（用户已定，但**现在不做**）

用户想把这个 App 做成**多协议遥控集合**：WiFi 遥控（已做）+ 蓝牙遥控 + 红外遥控
（手机支持的）+ 本地播放。

**已明确：先把音箱这块全部做完，再考虑蓝牙和红外。**

规划在 `docs/49`，大厂对照在 `docs/50`，结论摘要：

- 架构方向是**能力位驱动**（HomeKit 的 Service / Matter 的 Cluster 那套），不是设备类型驱动
- 遥控要按**传输通道**切模块（wifi / ble / ir），**不按品牌切**——品牌差异是数据不是代码
- `RemoteView`（协议无关的视图模型）**故意没做**：只有一类设备时它必然是假的
- `DeviceRegistry` 拆分也**先停**：等"设备"这个概念从 `SavedDevice(ip)` 变成多地址模型

**关键判断**：不在接第二个协议之前建一堆模块。唯一的实现者会悄悄污染抽象。

---

## 十一、接手后第一步建议

1. 先读 `docs/52-dev-conventions.md`（硬规则 + 动手前自检清单）
2. 看最新三篇 `docs/52` `docs/53` `docs/54`（这轮做了什么、为什么）
3. 跑一遍全量验证，确认 779 单测全绿
4. 问用户：这轮是想**补功能**还是**继续拆结构**？

**不要**在没有真机验证的情况下宣称"功能可用"——`.153` 之前离线过好几天，
每次交付都要如实标注哪些验证了、哪些没验证。
