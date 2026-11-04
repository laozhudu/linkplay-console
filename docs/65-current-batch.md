# 65 · 当前批次任务（活文档）

> **这是一份活的任务清单。每完成一项，请回来把 `[ ]` 改成 `[x]` 并填上 PR 号。**
> 依据代码状态：`1326bf7`（726 单测 0 失败）
> 完整路线图见 `docs/64-development-roadmap.md`，这里只列"现在要做的事"。

---

## 批次 1 · 补完诊断（七个源）

模板：照 `nas/NasSourceStatus.kt` 的形状做 —— 单例 + `AtomicReference` 快照 +
每个动作一个记录函数（结果 + 耗时）+ `render()`，在 `ConsoleDiagnostics` 注册一段。

### 进度

- [x] **NAS** — PR #70（`SourceFailure` + `NasSourceStatus`）
- [x] **本地曲库** — PR #74（`LocalSourceStatus` + `queryWithStats`）
- [x] **在线曲库** — PR #75（`SubsonicSourceStatus`）
- [x] **电台** — PR #76（`RadioSourceStatus`）
- [x] **配网** — PR #78（`ProvisionSourceStatus`）
- [x] **语音** — PR #80（`VoiceSourceStatus`）
- [x] **悬浮窗** — PR #81（`FloatingSourceStatus`）

### 每个源必须能回答的问题

| 源 | 必须能分清 |
|---|---|
| 本地曲库 | `扫描 0 首` 是**没权限**（`PERMISSION_DENIED`）还是**库里真没歌**（`EMPTY`） |
| 在线曲库 | 搜不到是**关键词问题**还是**服务端空库**（tt-music 现在就是空的） |
| 电台 | 加了不响是 **`scheme=https`（音箱不认）** / `UNREACHABLE` / `TIMEOUT` |
| 配网 | 命令回 OK 但**没等到 IP**（最典型的"回 OK ≠ 生效"） |
| 语音 | **识别空** / **没匹配意图** / **匹配了执行失败** 三段必须分开 |
| 悬浮窗 | **没权限** / **服务被杀** / **布局 0 尺寸** |

### 验收标准（批次 1 做完的判据）

用户说下面的话时，报告里必须能看到对应信息，**不需要再追问**：

| 用户说 | 报告里应该有 |
|---|---|
| "本地没音乐" | `权限=未授予` 或 `扫描 0 首 ← MediaStore 无音频` |
| "搜不到歌" | `搜索"X" 0 条 / 38ms ← EMPTY：服务端曲库为空` |
| "电台加了不响" | `scheme=https（音箱不认）` 或 `UNREACHABLE` |
| "配网失败" | `扫到 N 个 AP` + `连 X` + `结果=超时（8 秒无等待到 IP）` |
| "语音不管用" | `识别"..." → 意图=... → 执行=...` 三段 |
| "悬浮窗不显示" | `权限=已授予  服务=未运行` |

**做不到这张表，批次 1 就没做完。**

---

## 批次 2 · 零风险四项（可与批次 1 并行）

| # | 做什么 | 注意 |
|---|---|---|
| 1 | 取数 4 处旁路收口 | `DeviceQueuePush` 和 NAS 推流相邻，**等批次 1 稳定后** |
| 2 | UPnP 客户端 3 处收口 → 走 `rendererFor` | 无冲突 |
| 3 | 两个重复组件合并（`TrackRow` / `nextLoop`） | 无冲突 |
| 4 | `DeviceControl` 依赖 `RemoteTransport` 而非 `LinkPlayTransport` | 无冲突 |

**优先改 `DeviceVolumeControl.readVolume()`**：它用 `getPlayerStatus`（485 字节）读音量，
而 `GetControlDeviceInfo`（3112 字节）一次给全音量+声道+电量+名字+固件。
真机实测大请求 43-54ms、小请求 18-31ms —— 慢不了多少，但**同一个值有两个来源会打架**。

### 进度

- [ ] 取数旁路收口（4 处）
- [ ] UPnP 客户端收口（3 处）
- [ ] 重复组件合并（2 处）
- [ ] `RemoteTransport` 类型声明

---

## 批次 6 · logcat 输出（10 行，随时可插）

`AppDiagnostics` 的 `downstream` 注释里写着"留给未来接 logcat"，现在接上：

```kotlin
downstream = DiagnosticSink { entry ->
    android.util.Log.i("LinkPlayDiag", "${entry.severity.code}|${entry.source}|${entry.message}")
}
```

再让诊断报告生成时打一条完整文本到 logcat（tag 用 `LinkPlayDiag`）。

**收益**：用户打开诊断页 → 全文进 logcat → 我用 `adb logcat` 直接读，不用截图。

- [x] logcat sink（PR #84）
- [x] 报告生成时打完整文本（PR #84）

---

## 硬规则（违反过的都留了记录）

1. **先补诊断再改代码** —— 改了看不见的东西等于没改
2. **一个 PR 一件事** —— 批次 1 是一个源一个 PR
3. **单测数只能增不能减**（当前 726）—— 减了要在 PR 里说明为什么
4. **交付时如实标注**：哪些真机验证了、哪些没有
5. **诚实优先**：「命令回 OK」≠「功能生效」，这个项目坑过四次

## 明确不做

- ❌ 不拆 5 个内容源 Gradle 模块
- ❌ 不重写 `MediaProvider` 接口
- ❌ 不做本地播放（Media3）、不做蓝牙红外
- ❌ **不删语音/悬浮窗/WPS** —— 没验证 ≠ 不能用
- ❌ 不动 `core-protocol` 协议层

## 留门（不用写代码，别焊死）

- `MediaProvider.resolve()` 的 URL 别在注释里写死"给设备拉的"
- `PlaybackEngine` 别加 `deviceIp: String` 这种必填参数
- `DispatchResult` 保持 sealed

---

## 做完这一批之后

下一批是**批次 3 内容源标准化**（`docs/57`）。
唯一硬判据：**加一个新内容源 = 只加一个实现类 + 注册一行，投送链路 0 行改动。**

批次 4（音量与推流合并）、批次 5（状态收敛）在批次 3 之后，顺序见 `docs/64`。
