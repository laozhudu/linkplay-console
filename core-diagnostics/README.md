# core-diagnostics

App 内诊断工具包：**分级环形日志 + 自注册状态段 + 可截图的纯文本报告**。

纯 JVM、零运行时依赖、不含 Android 代码。`implementation` 一下就能接进任何 JVM / Android 项目。

---

## 它解决什么问题

自用 App 的用户看不到 adb，只能截图。截图要能回答四件事：

1. 崩在哪（异常类 + 消息 + 最上面几行堆栈）
2. 崩的时候各模块是什么状态
3. 每条链路通不通（哪条没答 → 哪个功能没反应）
4. 有没有**静默失败**（异常被吞了、命令回 OK 但没生效）

这四件事的共同点是：**信息分散在各个模块里，而用户只能提供一个现象**。
所以这里不做"日志框架"，做的是**把状态聚成一份能截图的东西**。

---

## 三个零件

```kotlin
val log = EntryLog(capacity = 200)                 // 环形条目（分级、带时间）
val sections = SectionRegistry(sink = log)         // 状态段注册表
val report = DiagnosticReport.build(header, crash, body, log.recent())
```

### 1. `EntryLog` —— 分级条目

```kotlin
log.error("playQueueItem", e)        // 异常 → ERROR，自动带类名+堆栈
log.warn("probeSaved", "设备未应答")
log.info("getStatusEx", "完成 69ms")

log.timed("getStatusEx") { client.send(ip, cmd) }   // 成功也记（带耗时），失败记 ERROR 并重抛
```

**为什么成功也要记**：排障时最常问的是"这个动作到底跑了没有"。
只有失败才记的话，"没反应"和"根本没触发"分不开 —— 这两种的修法完全不同。

### 2. `SectionRegistry` —— 谁有状态谁注册

```kotlin
sections.register("devices", "设备", { if (allOffline) Severity.WARN else Severity.INFO }) {
    buildString { /* 返回多行文本，自己缩进两格 */ }
}
sections.unregister("playback")   // 模块销毁时必须注销，否则会显示"上一个实例"的状态
```

- 渲染顺序按**首次**注册先后（截图前后位置不变）
- 单段抛异常不影响其他段，并记一条到 `sink`（诊断自己崩了要留痕）
- `severity` 是**函数**不是值：注册只发生一次，而"全没应答"是运行中才出现的状态

### 3. `DiagnosticReport` —— 定宽纯文本

```
=== 声控台 诊断报告 ===
版本：0.9.60（60）
时间：2026-11-04 02:30:00
问题汇总：E1 W2 I5

[崩溃记录]
无（本次启动未捕获到崩溃）

[设备]
  doss1  192.168.99.164
    状态=在线  能力=LINKPLAY

[运行期问题 3 条（新→旧）｜E1 W2 I0]
  02:29:58 [E] [playQueueItem] IOException: HTTP 500 from 192.168.99.153: 401 invalid action
  02:29:57 [W] [probeSaved] 设备未应答，保留上次状态
  02:29:56 [I] [getStatusEx] 完成 69ms
```

排版判据不是"好不好看"，而是**截图能不能截到关键信息**：
堆栈只留前 16 行、条目只列前 30 条（其余折叠成"另有 N 条"）、每条一行。

---

## 接入一个项目

```kotlin
// 1. 选存储：Android 用 filesDir，桌面用任意目录，测试用 MemoryStore
val store = FileStore(File(context.filesDir, "diagnostics"))

// 2. 组装
val log = EntryLog(capacity = 200)
val sections = SectionRegistry(sink = log)

// 3. 协程边界收在一处（接入方自己实现，因为协程 scope 是各框架的事）
fun ViewModel.safeLaunch(source: String, block: suspend CoroutineScope.() -> Unit) =
    viewModelScope.launch {
        try { block() }
        catch (c: CancellationException) { throw c }
        catch (e: Throwable) { log.record(DiagnosticEntry.ofError(nowMs(), source, e)) }
    }

// 4. 出报告
DiagnosticReport.build(
    header = DiagnosticReport.Header("我的应用", "1.0", nowText),
    crashText = crashGuard.lastReport(),
    body = sections.render(),
    entries = log.recent(),
)
```

`source` 用「用户做了什么」命名（`playQueueItem` / `probeSaved`），
不要用类名 —— 用户说的是"点播放没反应"，标签要对得上这句话。

---

## 设计取舍

| 决定 | 为什么 |
|---|---|
| 实例而非 `object` 单例 | 单例会让测试互相污染（只能靠 `clear()` 打补丁），也无法在一个进程里诊断两套东西 |
| 存 epoch 毫秒而非格式化字符串 | 跨天可读、可排序、可换渲染格式（报告/界面/落盘各一份） |
| 环形而非全留 | 排障只需要"刚操作完那几条"；全留撑内存且让报告长到截不完 |
| 纯文本而非富文本卡片 | 截图时排版越简单越不会漏；而且可以整段复制 |
| 零依赖 | 接入方不该为了记日志引入日志框架；`SimpleDateFormat` 而非 `java.time` 是为了兼容 Android API 24（未开 desugaring） |
| 报告骨架在库里、正文由调用方给 | "设备/播放/队列"是各项目独有的知识，库里不该知道 |

---

## 测试

```bash
./gradlew :core-diagnostics:test
```

41 个用例，覆盖：环形淘汰、严重度汇总、异常无 message 时不出现 `null`、
段渲染失败隔离且留痕、注册顺序稳定、两个注册表互不干扰、落盘编解码无损、
不可写目录返回 false 而不抛。

---

## 发布

```bash
./gradlew :core-diagnostics:publish
# → core-diagnostics/build/repo/io/github/laozhudu/linkplay/diagnostics/0.1.0/
```

只发布到本地仓库（个人项目，不做中央仓库分发）。
