# 75 · 实机接管：无线 adb + 诊断台合并方案

> 写于 2026-11-04 · 依据 main `fc3de9e`（PR #98 合入后）
> **这份文档是给"负责写代码那个对话"看的。** 只读第零、四节就能干活。

---

## 零、三行摘要

1. **不用装任何第三方 agent 项目**。无线 adb + 现有诊断台就够了 —— 不用碰音箱、不用改协议。
2. **能和诊断台合，而且比预想的更省**：诊断报告的全文**早就进 logcat 了**（`AppDiagnostics.logReport`），`adb logcat -s LinkPlayDiag` 直接能读。**这一条零改动。**
3. **要你做的只有 1 项、约 30 行**：加一个「不点界面也能生成报告」的入口。其余全在电脑侧，我这边写。

---

## 一、先纠正三个前提（都实测过）

### 1.1 我们之前装的 adb 不支持无线调试

| 路径 | 版本 | `pair` | `mdns` |
|---|---|---|---|
| `/usr/bin/adb` → `/usr/lib/android-sdk/...`（Debian 包） | **29.0.6**（2019-05，456KB） | ❌ | ❌ |
| `/opt/android-sdk/platform-tools/adb` | **37.0.1**（10.6MB） | ✅ | ✅ |

```
$ /usr/bin/adb --help | grep -c pair
0
$ /opt/android-sdk/platform-tools/adb --help | grep pair
 pair HOST[:PORT] [PAIRING CODE]
$ /opt/android-sdk/platform-tools/adb mdns check
mdns daemon version [adb discovery 0.0.0]
```

`adb pair` 是 platform-tools **30.0.0** 才引入的，Android 11 的无线调试配对必须用它。

**结论：不用装任何东西，改用 `/opt/android-sdk/platform-tools/adb` 即可。** PATH 里那个是 Debian 包自带的老版本。

### 1.2 报告全文已经在 logcat 里了

`ConsoleDiagnostics` 生成报告后调 `AppDiagnostics.logReport(text)`：按 3500 字符切块、每块加 `[报告 N]` 前缀、用 `Log.i(TAG = "LinkPlayDiag", …)` 输出。

所以 `adb logcat -d -s LinkPlayDiag` 拿到的就是**完整报告**，含 15 个 section 的现场快照：

```
设备 / 启动恢复 / 洛雪引擎 / 组 / 本地曲库 / 在线曲库 / 电台
配网 / 语音 / 悬浮窗 / NAS / 界面 / 指令 / 队列 / 后台服务
```

这些是**现场算出来的快照**，logcat 的时间线给不了 —— 这正是它比"只读日志"值钱的地方。

**唯一的问题：触发条件是「用户进诊断页」**（`DiagnosticsScreen` 里 `reportProvider()`）。这是要补的口子。

### 1.3 顺手发现的死代码

```
AppDiagnostics.flush(context)     ← 只有 ConsoleViewModel.flushIssues() 这个包装
ConsoleViewModel.flushIssues()    ← 全仓无调用点
AppDiagnostics.previous(context)  ← 全仓无调用点
```

后果：`filesDir/diagnostics/issue-log.txt` **从来没被写过**，「上次会话记录」永远是空。

不影响本方案（本方案走 logcat），但属于「写了没接线」，要处理（见 4.2）。

---

## 二、为什么不用第三方项目

调研过 `droidrun`（9.9k★）、`mobile-mcp`（10.3k★）、`android-remote-control-mcp`（783★，跑在手机上免 adb）等 12 个仓库，结论是不用：

| 方案 | 前提 | 它能给我们什么 |
|---|---|---|
| ARC MCP | 手机上装第三方 App + **常驻无障碍服务** | 界面操作 |
| mobile-mcp | adb | 界面操作 |
| droidrun | adb 或 Portal + **自带 agent loop** | 全自动（和我们自己的 agent 重复） |

三条理由：

1. **它们只解决「操作界面」，不解决「读内部状态」。** 诊断报告是 app 内部状态，不在屏幕上 —— 无障碍树只能读到可见文字，长文本会被截断。
2. **我们的报告已经有现成通道**（logcat），比任何 UI 抓取都完整、都便宜。
3. **多一个常驻无障碍服务 = 多一份耗电 + 一份权限**，而 adb 用完就能关。

**所以：操作界面用 `adb shell input`，读状态用 logcat，两个都不需要第三方 App。**

---

## 三、三条通道与能力边界

| 通道 | 拿到什么 | 要改 app | 可靠性 |
|---|---|---|---|
| **A** `adb logcat -s LinkPlayDiag` | 报告全文 + 时间线 | ❌ **零改动** | 依赖"进过诊断页"；受 logcat 环形缓冲限制 |
| **B** `run-as <pkg> cat files/diagnostics/issue-log.txt` | 运行期条目（落盘） | ✅ 接线 ~10 行 | 仅 debuggable 包 |
| **C** `am start --es diag 1` | 不点界面也能生成 + 落盘报告 | ✅ ~30 行 | 最稳，可自动化 |

**建议**：A 今天就能用（不用等代码）；C 作为长期加固。

---

## 四、要你做的（唯一一项）

### 4.1 加一个无 UI 的触发入口

现状：报告只在 `DiagnosticsScreen` 被打开时生成。

要的：`MainActivity` 收到 `--es diag 1` 时，直接生成报告 → 进 logcat → 落盘，界面可以完全不显示。

示意（**不是最终代码**，位置和风格按 `docs/52` 现有约定）：

```kotlin
// MainActivity.onCreate 里，setContent 之前
if (intent?.getStringExtra("diag") != null) {
    // ⚠️ 必须复用诊断页那一条生成路径，不许复制一份逻辑
    val report = consoleDiagnostics.report()
    AppDiagnostics.logReport(report)            // 进 logcat（已有）
    AppDiagnostics.writeReport(ctx, report)     // 新增：落盘 report.txt
}
```

硬约束（按 `docs/52`）：

- **不许复制报告生成逻辑** —— 必须复用 `ConsoleDiagnostics` 那一条路径（单一入口原则）
- 落盘文件名固定 `report.txt`，**头部带生成时间 + versionName + versionCode** —— 电脑侧靠这个判断"这份报告是不是这次的"
- 失败不能崩：整体 `runCatching` 包住，失败只记诊断
- 触发方式要能被 `adb shell am start -n <包名>/.MainActivity --es diag 1` 调到

### 4.2 顺手处理两个死代码

- `ConsoleViewModel.flushIssues()` 接上（建议 `onStop`，别放 `onDestroy`）
- `AppDiagnostics.previous()` 二选一：**接上**（报告里显示「上次会话」）或**删掉**。留着不调会误导后来人

### 4.3 为什么不做原计划的 HTTP API

`docs/65` 第二步原本规划了「只读 HTTP API（5 个端点，约 300 行）」。**本方案替代它**：

| | HTTP API | 本方案 |
|---|---|---|
| app 侧代码 | ~300 行（ServerSocket + 路由 + 序列化） | **~30 行** |
| 要保活吗 | 要（否则端口时有时无） | **不要** |
| 要鉴权吗 | 要（局域网暴露） | **不要**（adb 自带授权） |
| 端口冲突 | 要处理 | **无** |
| 能操作界面吗 | ❌ 不能 | ✅ `input` 直接点 |

**唯一劣势**：release 包（非 debuggable）读不到私有文件。但我们调研用的就是 debug 包，CI 本来就产 debug APK，够用。

---

## 五、电脑侧：`lpdiag`（我这边写，不碰 app 代码）

Python 标准库，放 `scripts/lpdiag.py`。

```
lpdiag pair <ip:端口> <6位配对码>   # 首次：手机「无线调试」页里的两个数
lpdiag connect <ip:端口>            # 之后每次（端口会变，以手机页面为准）
lpdiag devices
lpdiag report [--save 路径]         # logcat 抓全文，按 [报告 N] 重组成完整报告
lpdiag logs [-s 5m] [-f]            # 只要时间线
lpdiag ui                           # uiautomator dump → 紧凑文本（文字 + 坐标）
lpdiag tap "文字" | --xy x y         # 按文字找坐标点，比写死坐标稳
lpdiag shot [路径]
lpdiag start | stop | clear
```

实现要注意的坑：

1. **必须用 `/opt/android-sdk/platform-tools/adb`** —— PATH 里那个是 29.0.6，没有 `pair`
2. `uiautomator dump` 的 XML 比紧凑文本大 10–50 倍，**必须转换后再给模型**
3. logcat 环形缓冲默认 256KB，会被其他日志冲刷 → 先 `adb logcat -G 8M`
4. `[报告 N]` 分块要**按 N 的顺序拼接**，不能按到达顺序
5. 多设备必须 `-s <serial>`
6. **配对端口 ≠ 连接端口**，两个都在手机页面上

---

## 六、坑（没验证的都标出来）

| 坑 | 说明 | 状态 |
|---|---|---|
| **Compose 语义树** | Compose 有时把整页合成一个节点，`uiautomator dump` 读不到按钮文字 | ⚠️ **未验证**，可能要给关键控件补 `testTag` / `contentDescription` |
| `run-as` | 只对 debuggable 包有效 | 文档行为，**未实测** |
| 无线调试重启失效 | Android 11+ 端口每次重启随机变；配对信息通常保留，直接 `connect` 即可 | **未实测** |
| mDNS 在 LXC 里 | `mdns check` 通了（daemon 可用），但多播能不能出 LXC 没测 → **方案不依赖 mDNS**，直接给 IP:端口 | daemon 已验证 |
| 安全性 | 无线调试开着时同网段任何人都能配对 | 用完就关 |
| 息屏 | 息屏后 `input` 会先亮屏，可能点到锁屏 | 要设「充电时不锁屏」 |

---

## 七、验收清单

- [ ] `platform-tools/adb pair` 配上手机，`devices` 显示 `device` 状态
- [ ] `lpdiag report` 拿到完整报告，**15 个 section 全在**
- [ ] `lpdiag tap "诊断记录"` 能进诊断页 —— **这条同时验证 Compose 语义树读不读得到**
- [ ] `lpdiag shot` 截图能看清
- [ ] 手机重启后 `lpdiag connect` 一次就恢复
- [ ] 报告头部的时间/版本号能判断是不是这次生成的

---

## 八、没验证的（诚实清单）

1. **真机一次都没连过** —— `adb devices` 一直是空。所有 adb 行为都是按版本能力和官方文档推的，**不是实测**。
2. **Compose 语义树能不能读** —— 这条决定「按文字点击」能不能用。读不到就只能退到坐标。
3. **报告全文在 logcat 里会不会被截断** —— 3500 字符切块 + 环形缓冲，理论上够，没实测。

---

## 九、一句话

**报告已经在 logcat 里了，你只要给它一个不用点界面的触发口（30 行），剩下的 adb 活我这边全包。**
