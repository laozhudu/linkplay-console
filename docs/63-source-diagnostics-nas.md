# 63 · 补第一个诊断盲区：NAS（接 `docs/61` 施工图）

> 分工：另一个对话负责研讨与方案文档，我负责把它落成代码。
> 依据：`docs/61`（七个盲区施工图）第六节的顺序 —— **NAS 排第一**。
> 代码基线：`37c941c`（712 单测 0 失败）。

---

## 一、为什么先补 NAS

`docs/61` 给的理由是：**NAS 要改推流路径（`docs/60`），改之前必须先能看见现在的行为**。

现在 NAS 页自己走一套投流（`DevicePlaylistGateway.push` → `DeviceQueuePush.push` → 客户端逐首），
和 `QueueStarter` 的降级链是两份实现。要证明"改完还是走同一条路"，
唯一办法是让报告里写着**实际走了哪一条**。

没有这条记录，改完之后我只能说"代码看起来是对的"——那正是这个项目一直在治的病。

---

## 二、先做那件"统一的事"：失败分类

新增 `diagnostics/SourceFailure.kt`：

| 分类 | 标签 | 典型来源 |
|---|---|---|
| `NOT_CONFIGURED` | 未配置 | 没填地址 |
| `UNREACHABLE` | 连不上 | `ConnectException` / `UnknownHostException` / `SocketException` |
| `AUTH_FAILED` | 认证失败 | 连上了但账号不对 |
| `EMPTY` | 连上了但是空的 | 目录 0 项 / 搜索 0 条 |
| `UNSUPPORTED` | 设备不支持 | 音箱不认 https 这类 |
| `PERMISSION_DENIED` | 没权限 | `SecurityException` |
| `TIMEOUT` | 超时 | `SocketTimeoutException` |
| `PARSE_FAILED` | 回包看不懂 | 解析类异常 |
| `UNKNOWN` | 未知错误 | ← **施工图里没有，我自己加的** |

### 为什么加了 `UNKNOWN`

施工图列了八类，没留兜底。那样遇到不认识的异常，就只能硬塞进某一类 ——
**那是在报告里说谎**。宁可写"未知错误"让人去查，也不假装知道原因。

这条和 `docs/52` 的「诚实优先」是一条线：不确定就写不确定。

### 分类为什么不能解析 message

`of(error)` 只看异常类型和类名，不看 message 文本 —— 后者随系统语言变，
中文机上采集到的报告在英文机上读不出来。

---

## 三、NAS 段记什么

新增 `nas/NasSourceStatus.kt`（184 行）+ 诊断段 `source-nas`。

### 为什么是进程级 object，不是 ViewModel 字段

`NasViewModel` 跟着页面走：退出 NAS 页实例就没了。
而"我刚才连 NAS 没连上"这件事要在**之后**的诊断报告里还看得到 ——
那正是用户想起来截图的时候。

项目里已有同类做法：`CommandTrace.DEFAULT`、`QueueBus` 都是进程级 object。

### 为什么渲染方法在这个类里

渲染是纯函数，放这里能被单测直接喂状态验证排版；放到 `ConsoleDiagnostics` 里就只能靠真机看出来。

### 埋了五个点

| 位置 | 记什么 | 以前 |
|---|---|---|
| `scan()` | 扫到几个 + 耗时 + 失败分类 | `getOrDefault(emptyList())` 把异常吞成"发现 0 个" |
| `connect()` | 服务器名 / 控制面 URL / 耗时 / 失败分类 | 只有界面红字 |
| `browseInto()` | 目录名 + 项数 + 总数 + 耗时 | 只进 `log`（界面上滚过去了就没了） |
| `pushQueue()` | **实际走了哪条路线** + 明细 + 耗时 | 只有界面日志 |
| `observeQueue()` | 控制面是否掉线 | 只在卡片上变灰 |

**最重要的一处改动是 `scan()`**：原来是

```kotlin
val found = runCatching { discovery.discoverMediaServers() }.getOrDefault(emptyList())
```

异常被吞成空列表，于是"网络不通"和"没有 NAS"在界面上完全同形。
现在分成 `onSuccess` / `onFailure` 两条，`failure` 里写清原因。

### 扫到 0 个的分类是 `EMPTY`，不是 `UNREACHABLE`

这是施工图特别指出的：SSDP 一个都没回来，**可能是组播被隔离**，不是"NAS 关机了"。
两者修法完全不同（一个是网络/路由器设置，一个是去开 NAS）。

所以报告里这句话是：

```
发现=0 个（组播没回来，可能是 NAS 没开 DLNA 或网络隔离）
```

### 控制面掉线单独一行

```
控制面状态=掉线（49152 不通，设备本身在线）
```

为什么单列：`controlLost` 的时候设备是在线的、音乐可能还在放，
只是 UPnP 控制口断了。写进"设备离线"会把人引到错地方。

---

## 四、报告里长什么样

正常：

```
[NAS]
  发现=2 个 · http://192.168.99.223:8200/desc.xml / 210ms
  服务器=fnOS/MiniDLNA  状态=已连接 / 412ms
  控制面=http://192.168.99.223:8200/ctl/ContentDir
  控制面状态=正常
  最近列目录 All Music → 1931 项（共 1931）/ 210ms
  最近推流=设备列表(PlayQueue)（ok）/ 640ms
```

出问题：

```
[NAS]
  发现=0 个（组播没回来，可能是 NAS 没开 DLNA 或网络隔离）
  最近失败=12:31:02 连上了但是空的 —— 目录「空目录」返回 0 项
```

还没进过页面：

```
[NAS]
  发现=还没扫过
  （还没打开过 NAS 页）
```

**最后这条很重要**：不能因为没数据就显示一堆 0 —— 那会被读成"扫描失败"。

---

## 五、验证

### 已验证

```
core-protocol     613 用例  0 失败
core-diagnostics   41 用例  0 失败
app                72 用例  0 失败   ← 新增 14 个
──────────────────────────────────
合计              726 用例  0 失败
```

```
init 顺序检查通过（所有 init 块都在类级属性之后）。
诊断标签检查通过（61 处）。
```

新增 14 个用例分两组：

**`SourceFailureTest`（6）** — 锁映射规则
- 超时 → `TIMEOUT`（不能落成 `UNREACHABLE`，修法不同）
- 拒绝连接 / 解析不出主机 → `UNREACHABLE`
- `SecurityException` → `PERMISSION_DENIED`
- 不认识的异常 → `UNKNOWN`（**不能被猜成某一类**）
- `describe` 保留标签并拼明细
- 空目录走 `EMPTY`

**`NasSourceStatusTest`（8）** — 锁报告里那句话
- 进页面之前显示"还没扫过"，不显示一堆 0
- 扫到 0 个时必须点明"组播"可能，否则会被读成 NAS 关机
- 扫描成功后清掉旧的失败（否则报告永远挂着一条旧错）
- 连接失败保留目标地址（不然不知道该查哪台）
- 成功连上后条数 + 耗时都出现
- 空目录≠断连：`connected` 不能跟着翻成 false
- 控制面掉线单独一行、写明"设备本身在线"
- 推流路线被记下来

**测的是渲染文本，不是内部字段** —— 报告是唯一给用户看的东西，
字段对了但排版丢了等于没补。

### 未验证（必须靠真机）

- 真机上 NAS 页走一遍：扫描 → 连接 → 列目录 → 起播，看报告那几行是否都出现
- `DevicePlaylistGateway.push` 失败回退到 m3u 的分支，单测碰不到（要设备在网里）
- 中继/本地媒体服务的统计和 NAS 段同时出现时的排版

---

## 六、下一步

按 `docs/61` 第六节的顺序，接下来是：

| 顺序 | 源 | 状态 |
|---|---|---|
| 1 | NAS | ✅ 本次 |
| 2 | 本地曲库（`source-local`） | 待做 |
| 3 | 在线曲库（`source-subsonic`） | 待做 |
| 4 | 电台（`source-radio`） | 待做 |
| 5-7 | 配网 / 语音 / 悬浮窗 | 待做 |

第 2 个（本地曲库）最该紧跟着做：**权限没给** 和 **手机里真没歌**
现在在界面上都是"没有找到音乐"，而这两种情况用户自己能一眼分辨出来 ——
只是报告里没有这句话。

模式已经立住了：`SourceFailure` + 一个 `XxxSourceStatus` object + 在 `ConsoleDiagnostics` 注册一段 + 一组渲染断言。
后面六个照抄这个形状即可，**不再需要讨论结构**。
