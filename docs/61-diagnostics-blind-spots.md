# 61 · 诊断七个盲区：施工图

> 配合 `docs/57`（内容源标准化）第一步使用。那边要"先补诊断再动代码"，这份是**具体补什么**。
>
> 依据的代码状态：提交 `e802417`（712 单测 0 失败）。以代码为准。

---

## 一、这七个为什么是盲区

不是因为"没记日志"，是因为**错误信息只活在界面上**：

```kotlin
// radio/RadioViewModel.kt:177
_state.update { it.copy(error = "地址不合法") }

// online/OnlineViewModel.kt:115
_state.update { it.copy(note = "连上了但账号不对（ping 返回失败）") }
```

用户看到红字，然后：
- 切到别的页面 → 信息没了
- 重开 App → 信息没了
- 想给我看 → 只能截图那一瞬间

**而诊断报告里什么都没有。**

所以这七个源的任何问题，我都只能靠用户口述。这是目前最大的排障成本。

---

## 二、统一先做一件事：失败原因分类

现在各处的失败都是 `t.message` 直出，一句话。诊断要说准话，必须先分类。

```kotlin
// app/src/main/kotlin/.../diagnostics/SourceFailure.kt
enum class SourceFailure(val label: String) {
    NOT_CONFIGURED("未配置"),
    UNREACHABLE("连不上"),
    AUTH_FAILED("认证失败"),
    EMPTY("连上了但是空的"),
    UNSUPPORTED("设备不支持"),
    PERMISSION_DENIED("没权限"),
    TIMEOUT("超时"),
    PARSE_FAILED("回包看不懂"),
}
```

**为什么必须分类**：下面这两句，修法完全相反：

| 报告里写的 | 真实原因 | 怎么修 |
|---|---|---|
| `local 扫描 0 首` + `EMPTY` | 库里真没歌 | 去放几首歌 |
| `local 扫描 0 首` + `PERMISSION_DENIED` | 没给读媒体权限 | 去设置里授权 |

现在两种情况在界面上都是"0 首"，分不开。

---

## 三、七个源的施工图

### 1 · 电台（`radio/RadioViewModel.kt`，275 行）

**段 id**：`source-radio`

**记什么**：

```
[电台]
  库内 24 台（内置 8 个未添加）
  最近播放：http://ngcdn001.cnr.cn/live/zgzs/index.m3u8
    结果=成功  status=PLAY  3.2s
  最近失败：https://lqingting.qingting.fm/live/4804/64k.mp3
    原因=设备不支持（音箱不认 https）
```

**在哪记**：
- `playUrl()`（175 行起）：已经是 6 秒轮询判定的写法，把结果和耗时一起记
- `addStation()`：添加失败时记 `PARSE_FAILED` 或 `UNSUPPORTED`

**关键分类**（这个源坑最多）：

| 现象 | 分类 |
|---|---|
| `normalizeUrl` 返回 null | `PARSE_FAILED`（地址不合法） |
| 命令回 OK 但 `status` 不是 PLAY | `UNSUPPORTED`（**这是 https 的典型表现**） |
| `client.send` 抛异常 | `UNREACHABLE` |
| 6 秒内没到 PLAY | `TIMEOUT` |

**必须记的一条**：`scheme=http` 还是 `https`。
真机实测（docs/42）：`.164` 上 `http://` 能播、`https://` 命令回 OK 但没播。
报告里直接写 `scheme=https（音箱不认）`，一眼定位。

---

### 2 · 在线曲库（`online/OnlineViewModel.kt`，217 行）

**段 id**：`source-subsonic`

**记什么**：

```
[在线曲库]
  地址=http://192.168.99.124:9527/rest  用户=tt01
  配置=已通过 ping  verified=true
  最近搜索"周杰伦" 0 条 / 38ms  ← 服务端曲库是空的
  最近失败：— 
```

**在哪记**：
- `saveConfig()`（102 行起）：`ping()` 的结果 + 耗时
- `search()`（127 行起）：结果条数 + 耗时
- `play()`（159 行起）：`resolve()` 是否成功

**关键分类**：

| 现象 | 分类 |
|---|---|
| `ping()` 抛异常 | `UNREACHABLE` |
| `ping()` 返回 false | `AUTH_FAILED`（**现在是"连上了但账号不对"，分类更准**） |
| 搜索返回 0 条 | `EMPTY`（**tt-music 现在就是这种**） |
| 搜索超时 | `TIMEOUT` |

**特别重要**：`EMPTY` 要能区分"搜不到这个词"和"库里根本没歌"。
做法：记搜索结果条数的同时，记一次 `getAlbumList2` 的总数（一次调用，缓存 5 分钟）。

否则用户说"搜不到"，我分不清是关键词问题还是库空。

---

### 3 · NAS（`nas/NasViewModel.kt`，551 行）

**段 id**：`source-nas`

**记什么**：

```
[NAS]
  服务器=192.168.99.223:8200（fnOS/MiniDLNA）
  状态=已连接
  最近列目录 /All Music → 1931 首 / 210ms
  控制面=正常（49152 通）
```

**在哪记**：
- `scan()`：扫到几个服务器
- `connect()`：连接结果
- `browseInto()`：列目录条数 + 耗时
- 推流那两处（418 / 429 行）：`DevicePlaylistGateway.push` / `DeviceQueuePush.push` 的结果

**关键分类**：

| 现象 | 分类 |
|---|---|
| SSDP 一个都没扫到 | `EMPTY`（**注意不是 UNREACHABLE**——可能是组播被隔离） |
| 连不上指定地址 | `UNREACHABLE` |
| `ContentDirectory.Browse` 返回空 | `EMPTY` |
| `controlLost = true` | 单独一行：`控制面掉线（49152 不通），设备本身在线` |

**NAS 是七个里最需要诊断的**，因为它现在**自己走一套推流**（`docs/60` 说过），
和 `QueueStarter` 的降级链是两份实现。补了诊断才能对比"改前/改后"行为是否一致。

---

### 4 · 本地曲库（`library/LibraryViewModel.kt`，435 行）

**段 id**：`source-local`

**记什么**：

```
[本地曲库]
  权限=已授予（READ_MEDIA_AUDIO）
  最近扫描 0 首 / 120ms  ← MediaStore 里没有音频文件
```

**在哪记**：
- `scanLocal()`：权限检查结果 + 扫描条数 + 耗时

**关键分类**：

| 现象 | 分类 |
|---|---|
| 没给权限 | `PERMISSION_DENIED` |
| 有权限但 0 条 | `EMPTY` |
| 扫到但解析标签失败 | `PARSE_FAILED`（记几条失败的） |

**这条最容易误判**：0 首可能是没权限，也可能是手机里真没歌。
现在界面上都是"没有找到音乐"，分不开。

---

### 5 · 悬浮窗（`floating/FloatingControlService.kt`，421 行）

**段 id**：`floating`

**记什么**：

```
[悬浮窗]
  权限=已授予（SYSTEM_ALERT_WINDOW）
  服务=运行中
  最近一次显示：3 分钟前
  最近一次隐藏原因=用户关闭
```

**在哪记**：
- `show()` / `hide()`：显示/隐藏 + 原因
- 权限检查处：`Settings.canDrawOverlays()` 结果

**关键分类**：

| 现象 | 分类 |
|---|---|
| `canDrawOverlays()` false | `PERMISSION_DENIED` |
| 服务被系统杀掉 | 用 `ActivityManager` 查（`docs/42` 已有这个做法） |
| 布局算出 0 尺寸 | 单独记一行宽高 |

---

### 6 · 语音（`voice/VoiceController.kt`，205 行）

**段 id**：`voice`

**记什么**：

```
[语音]
  最近一次：识别到"放一首周杰伦"
    意图=playMusic  参数=周杰伦
    执行=成功
  最近失败：识别到""（空结果）  ← 麦克风没录到
```

**在哪记**：
- 识别回调：原始文本 + 空结果
- `IntentRouter.route()`：匹配到哪个意图 + 参数
- 执行：成功/失败

**关键分类**：

| 现象 | 分类 |
|---|---|
| 识别结果为空 | `EMPTY`（麦克风/环境问题） |
| 识别有文字但没匹配到意图 | `PARSE_FAILED`（**意图库要补**） |
| 匹配到了但执行失败 | 按具体原因（`UNREACHABLE` / `UNSUPPORTED`） |

**这三段必须分开**：现在"语音不管用"是一句话，但这三种的修法完全不同。

---

### 7 · 配网（`provisioning/`，778 行）

**段 id**：`provisioning`

**记什么**：

```
[配网]
  最近一次：扫到 5 个 AP
    连接 DOSS DS-1831_400C
    结果=超时（8 秒无响应）
```

**在哪记**：
- `scanAccessPoints()`：扫到几个
- `connectMasterAp()`：下发结果 + 是否等到 IP

**关键分类**：

| 现象 | 分类 |
|---|---|
| 扫描返回 0 个 AP | `EMPTY`（**设备侧扫描失败，不是手机的问题**） |
| 命令回 OK 但没拿到 IP | `TIMEOUT`（**配网最典型的坑：回 OK ≠ 连上了**） |
| 命令抛异常 | `UNREACHABLE` |

**这条特别重要**：配网是"命令回 OK ≠ 功能生效"最典型的场景。
真机经验（docs/01）：`ConnectMasterAp` 回 OK 之后要轮询 `getStatusEx` 才知道真的连上没有。

---

## 四、报告整体长什么样

七段加进 `AppDiagnostics.sections` 之后：

```
=== 声控台 诊断报告 ===
版本：0.9.70（70）
问题汇总：E1 W3 I12

[崩溃记录]
无（本次启动未捕获到崩溃）

[设备]
  doss1  192.168.99.164
    状态=在线  能力=LINKPLAY
    音量=45  声道=立体声  电量=100%
    取数通道=CONTROL_INFO+PLAYER_STATUS

[界面]
  一级页=设备  二级页=无  浮层=无

[内容源]
  subsonic  已配置(verified)  搜索"周杰伦" 0 条 / 38ms  ← EMPTY：服务端曲库为空
  nas       已连 .223:8200  列目录 1931 首 / 210ms
  local     权限已授予  扫描 0 首 / 120ms  ← EMPTY：MediaStore 无音频
  radio     库内 24 台  最近播放成功 3.2s

[指令]
  共 47 条 · 成功 44 · 失败 3 · 平均 68ms
  最近 3 条：
    13:14:22  SetVolume(45) @192.168.99.164 → OK  72ms
    13:14:20  Pause @192.168.99.164 → 失败：设备里没有可播放的内容  45ms

[队列]
  归属=192.168.99.164  设备持有=true  共 122 首 · 第 1 首

[悬浮窗]
  权限=已授予  服务=运行中

[语音]
  最近一次：识别"放一首周杰伦" → 意图=playMusic → 执行成功

[运行期问题 16 条（新→旧）｜E1 W3 I12]
  13:14:30 [W] [source-subsonic] EMPTY: 搜索"周杰伦"返回 0 条，服务端曲库为空
  13:12:05 [E] [source-radio] UNSUPPORTED: https 地址，音箱不认
```

**关键**：`[内容源]` 那四行里，每一行的"←"后面都是**一句人话结论**，
不是让读的人自己推断。

---

## 五、验收判据

补完诊断后，下面这些情况**不用问用户就能定位**：

| 用户说 | 报告里应该能看到 |
|---|---|
| "搜不到歌" | `subsonic 搜索 X 0 条 / 38ms ← EMPTY`（库空）还是 `UNREACHABLE` |
| "电台加了不响" | `scheme=https（音箱不认）` 还是 `UNREACHABLE` |
| "本地没音乐" | `PERMISSION_DENIED` 还是 `EMPTY` |
| "NAS 连不上" | 扫到几个服务器、连哪个失败、什么原因 |
| "悬浮窗不显示" | 权限有没有、服务在不在跑 |
| "语音不管用" | 识别出什么、匹配到哪个意图、执行结果 |
| "配网失败" | 扫到几个 AP、连的哪个、等到 IP 没有 |

**做不到这张表，诊断就白补了。**

---

## 六、施工顺序建议

| 顺序 | 源 | 为什么这个顺序 |
|---|---|---|
| 1 | **NAS** | 它要改推流路径（`docs/60`），**必须先有诊断才能验证改没改坏** |
| 2 | **本地曲库** | 权限问题最常见，且 0 首的两种原因分不开 |
| 3 | **在线曲库** | tt-music 现在库是空的，正好验证 `EMPTY` 分类 |
| 4 | **电台** | 坑最多（https/scheme/switchmode），但代码结构最清楚 |
| 5 | 配网 | 独立功能，改它不影响别的 |
| 6 | 语音 | 同上 |
| 7 | 悬浮窗 | 同上 |

**前四个是内容源**，和第 57 篇的标准化直接相关。
后三个是独立功能，可以单独做。

---

## 七、一条通用规则（写进 `docs/52` 已有，这里强调）

每个失败路径都要走这一句：

```kotlin
AppDiagnostics.log.record(
    DiagnosticEntry.ofError(System.currentTimeMillis(), "source-nas", error)
)
```

**但光记异常不够** —— 还要记**语义结论**：

```kotlin
// 不好的做法：只记异常
log.error("source-nas", e)   // → "IOException: Connection refused"

// 好的做法：记分类 + 人话
log.record(DiagnosticEntry(
    atMs = now, source = "source-nas", severity = Severity.WARN,
    message = "${SourceFailure.UNREACHABLE.label}: 连不上 192.168.99.223:8200",
))
```

理由：`IOException: Connection refused` 我能看懂，但报告是给**用户截图**用的，
而且三个月后我自己也未必记得当时是哪个环节。分类 + 人话能让任何人一眼定位。
