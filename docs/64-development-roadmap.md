# 64 · 开发路线图（整合版）

> 把 `docs/57` `58` `60` `61` `62` + 综合优化评估 + 实机接口方案，整合成一份可开工的顺序表。
> **细节在各自文档里，这里只给顺序、依赖、验收标准。**
>
> 依据代码状态：提交 `1fa5331`（726 单测 0 失败）

---

## 一、现在在哪

| 已完成 | 提交 |
|---|---|
| 诊断台独立成 `core-diagnostics`（零依赖、可发布） | #60 |
| 诊断段：设备 / 界面 / 指令 / 队列 / 后台服务 | #60 #62 #63 |
| **NAS 诊断**（`SourceFailure` 分类 + `NasSourceStatus`） | **#70** |
| 语义指令层（`RemoteCommand` / `LinkPlayTransport`） | #61 |
| 能力位驱动界面（`DeviceCapabilities` 真正被消费） | #61 |
| `ConsoleViewModel` 1184 行（原 2029） | #59 #65 |

**基线**：726 单测 0 失败 · 4 个模块 · 25470 行

---

## 二、六批，按依赖排序

```
批次 1  补完诊断（六个源）        ← 进行中，最紧急
   ↓
批次 2  零风险四项（可并行）
   ↓
批次 3  内容源标准化（docs/57）
   ↓
批次 4  音量与推流合并（docs/60）
   ↓
批次 5  状态收敛（docs/62）
   ↓
批次 6  实机接口（logcat 10 行，随时可插）
```

---

## 批次 1 · 补完诊断（六个源）

**依据**：`docs/61` 施工图。NAS 已完成，照它的模板做剩下六个。

### 模板（`NasSourceStatus` 的形状）

```kotlin
object XxxSourceStatus {
    private val ref = AtomicReference(XxxStatus())
    fun snapshot(): XxxStatus = ref.get()

    // 每个关键动作一个记录函数（结果 + 耗时）
    fun scanned(count: Int, ms: Long) = mutate { ... }
    fun failed(error: Throwable, ms: Long) = mutate { ... }

    fun render(): String   // 输出给人看的文本
}

data class XxxStatus(...)
```

在 `ConsoleDiagnostics` 注册一段，级别用函数（有 failure 就 WARN）。

### 六个源 + 各自的验收

| 顺序 | 源 | 文件 | 必须能回答 |
|---|---|---|---|
| 1 | 本地曲库 | `library/LibraryViewModel.kt` | `扫描 0 首` 是**没权限**还是**库里真没歌** |
| 2 | 在线曲库 | `online/OnlineViewModel.kt` | 搜不到是**关键词问题**还是**服务端空库** |
| 3 | 电台 | `radio/RadioViewModel.kt` | 加了不响是 **https 不认** / **UNREACHABLE** / **TIMEOUT** |
| 4 | 配网 | `provisioning/` | 命令回 OK 但**没等到 IP**（最典型的"回 OK ≠ 生效"） |
| 5 | 语音 | `voice/VoiceController.kt` | **识别空** / **没匹配意图** / **匹配了执行失败** 三段分开 |
| 6 | 悬浮窗 | `floating/FloatingControlService.kt` | **没权限** / **服务被杀** / **布局 0 尺寸** |

### 验收标准（这一批做完的判据）

**用户说下面七句话时，报告里必须分别能看到对应信息，且不需要再追问：**

| 用户说 | 报告里能看到 |
|---|---|
| "搜不到歌" | `subsonic 搜索 X 0 条 / 38ms ← EMPTY：服务端曲库为空` |
| "电台加了不响" | `scheme=https（音箱不认）` 或 `UNREACHABLE` |
| "本地没音乐" | `PERMISSION_DENIED` 或 `EMPTY` |
| "NAS 连不上" | ✅ 已完成 |
| "悬浮窗不显示" | `权限=已授予 服务=未运行` |
| "语音不管用" | 识别出什么、匹配到哪个意图、执行结果 |
| "配网失败" | 扫到几个 AP、连的哪个、等到 IP 没有 |

**做不到这张表，这一批就没做完。**

### 注意

- **别一次性做完六个**。一个源一个 PR，每个 PR 带自己的单测（照 `NasSourceStatusTest` 的 101 行）。
- 顺序有意：前四个是内容源（和批次 3 直接相关），后三个是独立功能。

---

## 批次 2 · 零风险四项（可与批次 1 并行）

**依据**：综合优化评估的 A2 / A3 / B5 / D4。

| # | 做什么 | 位置 | 风险 |
|---|---|---|---|
| 1 | 取数 4 处旁路收口 | `SessionRestore.restoreLastQueue()` / `RadioViewModel.playUrl()` / `DeviceQueuePush.push()` / **`DeviceVolumeControl.readVolume()`**（最后一个最该改） | 低 |
| 2 | UPnP 客户端 3 处收口 | `NasViewModel`（探测处）/ `QueuePlaybackService`（取 renderer 处）/ `PlaybackViewModel`（建 engine 处）→ 都改走 `rendererFor` | 低 |
| 3 | 两个重复组件合并 | `TrackRow`（Library+Queue）、`nextLoop`（Nas+Queue）→ `ui/components` | 极低 |
| 4 | 传输接口类型声明 | `DeviceControl` 依赖 `RemoteTransport` 而非 `LinkPlayTransport` | 极低 |

**为什么这批可以并行**：四项都不改行为，712→726 单测能兜住。

**唯一的冲突点**：第 1 项里的 `DeviceQueuePush` 和 NAS 推流路径相邻，
而批次 1 正在改 `NasViewModel`。**所以第 1 项等批次 1 的 NAS 那部分稳定后再做。**

**`DeviceVolumeControl.readVolume()` 优先改**：它用 `getPlayerStatus`（485 字节）读音量，
而 `GetControlDeviceInfo`（3112 字节）一次给全音量+声道+电量+名字+固件。
真机实测大请求 43-54ms、小请求 18-31ms —— 慢不了多少，但**同一个值有两个来源会打架**。

**验收**：单测数不减 + 每个改动点都有注释说明"为什么收在这里"。

---

## 批次 3 · 内容源标准化

**依据**：`docs/57`。

### 做什么

1. 本地 / NAS / 电台 / 播放队列 各实现一个 `MediaProvider`
2. `OnlineViewModel` 改成依赖接口，不直接 import `SubsonicClient`
3. 加注册表（谁是当前可用源、谁配置好了）
4. `startQueueOnDevice` 改成只接受 `MediaProvider` 的输出

### 验收（唯一硬判据）

> **加一个新内容源（WebDAV / 网易云）= 只加一个实现类 + 注册一行，投送链路 0 行改动。**

**做不到这条，这一批就是白做。**

### 风险

NAS 和电台现在各有自己的降级链，收进统一入口时**行为可能变**。
所以必须在批次 1 之后做 —— 先能看见，才能验证改没改坏。

### 不要做

- ❌ 不要拆成 5 个 Gradle 模块（拆实现类就够，见 `docs/57` 第二节）
- ❌ 不要重写 `MediaProvider` 接口（它设计是对的）

---

## 批次 4 · 音量与推流合并

**依据**：`docs/60`。

### 四个改动，按顺序

| # | 改什么 | 为什么这个顺序 |
|---|---|---|
| 1 | `QueueRunState.ownerIp: String?` → `owner: OutputTarget?` | 先让"输出目标"这个概念存在，后面才有意义 |
| 2 | 音量四条路 → 一条 | 依赖第 1 项（要按 target 分派） |
| 3 | 推流三份 → `DeviceSink` | 依赖第 1、2 项 |
| 4 | 两个 MediaServer 收门面 | 和第 3 项一起 |

### 音量现在的四条路（必须合并）

| # | 在哪 | 用什么 |
|---|---|---|
| 1 | `DeviceControl.adjustVolumeByKey` | `RemoteCommand.SetVolume` |
| 2 | `ConsoleViewModel:1176` | `DeviceVolumeControl.setVolume` |
| 3 | `PlaybackEngine.setVolume`（两个实现） | 直接拼协议命令 |
| 4 | `DeviceVolumeControl.readVolume` | `getPlayerStatus` 读音量（最笨的路） |

### 验收

- 音量只有一个写入口（`RemoteCommand.SetVolume`）
- `DeviceVolumeControl` 的写路径删掉，`readVolume` 并进统一取数
- 726 单测不减

### 风险

`PlaybackEngine` 两个实现被播放页（843 行）依赖。改动要先加新路径、再删旧的。

---

## 批次 5 · 状态收敛

**依据**：`docs/62`。**这是最难的一批。**

### 四步走（每步可单独交付）

| 步 | 做什么 | 界面改动 |
|---|---|---|
| 1 | 新建 `SessionSnapshot` + `from()`，**并存不删** | 零 |
| 2 | `PlaybackViewModel` 以 snapshot 为源，**旧字段变 `get()` 转发** | **零**（关键技巧） |
| 3 | 界面逐个改读 `snapshot` | 逐个 |
| 4 | 删旧字段 | 零 |

**第二步做完就能拿到 80% 的收益**（三份音量实际已是一份）。

### 必须先定的规则

`PlaybackUiState.volume=45` 和 `DeviceCard.volume=39` 同时存在时显示哪个？

**答案（来自既有铁律）**：界面显示设备报的值。所以
`DeviceCard > PlaybackSnapshot > PlaybackUiState`，写死在 `from()` 注释里。

### 一个不能删的

`DeviceCard.playing` **不能删** —— 卡片是"多台设备并排"，每台各有状态；
`SessionSnapshot` 是"当前选中那台"。语义不同。

---

## 批次 6 · 实机接口（随时可插）

**依据**：实机接口方案讨论。

### 第一步只要 10 行

`AppDiagnostics` 的 `downstream` 注释里写着"留给未来接 logcat"，现在接上：

```kotlin
downstream = DiagnosticSink { entry ->
    android.util.Log.i("LinkPlayDiag", "${entry.severity.code}|${entry.source}|${entry.message}")
}
```

再让诊断报告生成时打一条完整文本到 logcat。

**收益**：用户打开诊断页 → 全文进 logcat → agent 直接读，不用截图。

**成本**：10 行 + 电脑装 adb + 手机开一次无线调试。

### 后面两步（现在不做）

- 只读 HTTP API（300 行）—— 等状态收敛（批次 5）之后
- 写操作 —— 现在想不出必须的场景，护栏五条见讨论文档

---

## 三、留门（现在不做，但别把门焊死）

| 项 | 现在要做什么 |
|---|---|
| 本地播放（Media3） | `MediaProvider.resolve()` 的 URL 别写死"给设备拉的"用途 |
| 本地播放 | `PlaybackEngine` 别加 `deviceIp: String` 这种必填参数 |
| 蓝牙 / 红外 | `DispatchResult` 保持 sealed（将来加 `Sent` 分支） |
| 三个播放门面合并 | 最后做，`LinkPlayDevice` 是对外库 API，动之前看 `docs/07` |
| 两套能力模型合并 | 同上，`LinkPlayCapabilities` 是导出 API |

---

## 四、明确不做

| 不做 | 为什么 |
|---|---|
| 拆 5 个内容源 Gradle 模块 | 拆实现类就够 |
| 重写 `MediaProvider` 接口 | 设计是对的 |
| 本地播放（Media3） | 先把音箱做完 |
| 蓝牙 / 红外 | 同上 |
| 删语音/悬浮窗/WPS | **没验证 ≠ 不能用**，先补诊断再决定 |
| 动 `core-protocol` 的协议层 | 它是全项目最健康的部分（零依赖、可发布、测试全绿） |

---

## 五、每批都必须做的事

1. **先补诊断再改代码** —— 改了看不见的东西，等于没改
2. **一个 PR 一件事** —— 批次 1 是一个源一个 PR
3. **单测数只能增不能减** —— 减了要在 PR 里说明为什么
4. **交付时如实标注**：哪些真机验证了、哪些没有
5. **文档跟着改** —— 改 `LinkPlayDevice` 要同步 `docs/07`；改行为要新增 docs

---

## 六、一句话总结

**协议层不动**（最健康）。**app 层的"多份真相"是全部问题所在**：
音量 4 条路、取数 4 处旁路、播放状态 4 份、3 个播放门面、2 套能力模型、2 份推流实现。

顺序就是：**先能看见（诊断）→ 再清旁路（零风险）→ 再接接口（内容源）→ 再合并（音量推流）→ 最后收敛（状态）**。

每一步都能单独交付，不需要一次做完。
