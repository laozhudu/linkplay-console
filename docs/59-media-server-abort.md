# 57 · 音箱断流被当成崩溃 —— 本地媒体服务把 App 打死了

> 触发：2026-11-04 用户回传 0.9.66 诊断报告，崩溃记录里一条 12:24:38 的
> `SocketException: Software caused connection abort`，线程 `pool-5-thread-1`。
> 规则依据：`docs/52`（任何开发动作必须伴随诊断台进化）

---

## 一、报告里看到了什么

```
版本：0.9.66（66）      运行时长：4s      问题汇总：E0 W0 I0

[崩溃记录]
  时间：2026-11-04 12:24:38
  线程：pool-5-thread-1
  异常：java.net.SocketException
  消息：Software caused connection abort
   --- 应用状态 ---
   已保存设备=3 在线=2 选中=192.168.99.164 组主机=无 卡片=2
   --- 堆栈 ---
   at LocalMediaServer.stream(LocalMediaServer.kt:200)
   at LocalMediaServer.handle(LocalMediaServer.kt:164)
   at LocalMediaServer.start$lambda$2$lambda$1(LocalMediaServer.kt:88)
```

同一份报告里，「启动恢复」段（上一版新加的）是好的：

```
[启动恢复]
  设备=doss1（192.168.99.164）
  队列=122 首 · 第 1 首 · 来源=设备列表
  耗时=653ms
```

**注意两个数字的矛盾**：崩溃发生在 12:24，报告时间 12:51，而运行时长只有 4 秒。
说明 12:24 那次异常**让进程结束了**，App 是后来才重新起来的。

这不是噪音，是**真闪退**。

---

## 二、异常从哪来

`LocalMediaServer` 是手机侧的 HTTP 服务：音箱是渲染器，只会自己来拉流，
手机必须把本地音乐文件通过 HTTP 暴露出去。

原来的代码：

```kotlin
// 第 88 行附近
pool.execute { handle(client) }      // ← 没有任何 try

private fun handle(socket: Socket) {
    socket.use { s ->
        ...
        if (method == "GET") stream(file, start, length, output)   // 第 164 行
    }
}

private fun stream(file: File, offset: Long, length: Long, output: OutputStream) {
    ...
    output.write(buffer, 0, read)     // 第 200 行 ← 音箱断开就抛在这
}
```

音箱拉到一半主动断开是**常态**：切歌、缓冲满、换 Wi-Fi、播放器主动 close。
这时候 `output.write` 抛 `SocketException`，一路冒到线程池外。

---

## 三、为什么"一个线程的异常"会把整个 App 打死

`CrashGuard.install()` 装的是 `Thread.setDefaultUncaughtExceptionHandler`：

```kotlin
Thread.setDefaultUncaughtExceptionHandler { thread, error ->
    runCatching { write(app, thread, error, stateSnapshot()) }
    // 交回系统，保持"闪退"这一既有行为
    previous?.uncaughtException(thread, error) ?: Process.killProcess(myPid())
}
```

这个处理器**对任何线程**生效。所以：

1. 音箱断开 → `SocketException`
2. 线程池线程没人接 → 默认处理器收到
3. `CrashGuard` 记一条崩溃 + 交回系统 → **系统杀进程**

用户感受：**听本地音乐，切一首歌 App 就没了。**

而报告里看不出来是"切歌"引起的——只有一条 socket 异常和一堆行号。

---

## 四、修法

原则：**已知的正常网络事件，不该冒到"未捕获异常"那一层。**
但也不能吞掉了就算——吞掉之后就变成"没声音也没人知道为什么"。

所以在两个推流服务的线程池入口各加一层 `serve()`：

```kotlin
private fun serve(client: Socket) {
    val progress = Progress()
    bump { it.copy(requests = it.requests + 1) }
    try {
        handle(client, progress)
        bump { it.copy(completed = it.completed + 1) }
    } catch (e: IOException) {
        if (!running.get()) return          // stop() 时的中断是预期的
        bump { it.copy(aborted = it.aborted + 1, lastAbortNote = "客户端中途断开：${e.message}…") }
    } catch (e: Throwable) {
        bump { it.copy(failed = it.failed + 1, lastFailedNote = "…") }
    } finally {
        runCatching { client.close() }
    }
}
```

**两个服务都修了**：

| 服务 | 用途 | 原来 | 现在 |
|---|---|---|---|
| `LocalMediaServer` | 本地曲库推给音箱 | 裸 `pool.execute { handle() }` | `serve()` 兜底 |
| `RelayMediaServer` | 在线电台 / DLNA 投流中继 | **同样的裸调用** | `serve()` 兜底 |

中继那条路其实**更容易断**（上游是外网，音箱断得更随意），所以一并修。

### 计数为什么单独做一个类

发送路径有四种：整文件、Range 片段、内存 m3u、上游 `copyTo` 透传。
每处各写一次 `written += n` 必然漏。所以统一在流这一层数：

```kotlin
internal class CountingOutputStream(delegate, progress) : OutputStream() {
    override fun write(b: ByteArray, off: Int, len: Int) {
        delegate.write(b, off, len)
        progress.written += len     // 只要字节从 socket 出去就一定会被数到
    }
}
```

---

## 五、诊断台这轮加了什么

### 5.1 「后台服务」段带上推流统计

```
[后台服务]
  队列播放=未运行
  本地媒体服务=运行中
    推流=:8123 请求=12 完成=10 中断=2 失败=0
    最近中断=12:24:38 客户端中途断开：Software caused connection abort（已发 1.2MB / 共 3.4MB）
  悬浮控制=未运行
```

**以前只有"运行中/未运行"**，用户说"本地音乐没声音"时我分不出：

| 现象 | 以前 | 现在 |
|---|---|---|
| 音箱根本没来请求 | 无法区分 | `请求=0` |
| 请求了但文件读不出来 | 无法区分 | `失败=1` + 原因 |
| 发到一半断了 | 无法区分 | `中断=1` + 已发/共多少 |

### 5.2 「播放」段带上中继统计

`PlaybackViewModel` 自己持有中继实例，所以在它注册的那一段里写：

```
  中继=http://192.168.99.120:8124 请求=3 完成=1 中断=2 失败=0
    最近中断=客户端中途断开：Broken pipe（已发 512KB）
```

### 5.3 严重度规则

- `中断`（音箱断开）→ **不算问题**，INFO
- `失败`（读文件出错、我们自己炸）→ **WARN**
- 以前两者混在一起，只能靠"有没有崩溃记录"猜

---

## 六、验证

### 已验证（可复现）

```
core-protocol     613 用例  0 失败   ← 新增 2 个
core-diagnostics   41 用例  0 失败
app                58 用例  0 失败
──────────────────────────────────
合计              712 用例  0 失败
```

```
init 顺序检查通过（所有 init 块都在类级属性之后）。
诊断标签检查通过（61 处）。
```

新增的两个用例（`LocalMediaServerTest`）用的是**真 socket**，不是 mock：

1. `client aborting mid-stream is counted as abort and never thrown`
   发请求头 → 读 64 字节 → `SO_LINGER=0` 强制 RST → 关连接。
   断言：`requests=1 / completed=0 / aborted=1`，且原因文字非空。
   **这条在修复前会直接把测试线程打死**（异常没人接），修完才通过。
2. `completed request is counted as completed`
   正常读完 → `completed=1 / aborted=0`，两个计数不互相污染。

### 未验证（必须靠真机）

- 真机上"听本地音乐中途切歌"是否还闪退 —— 需要实机操作
- 中继那条路径（在线电台 / DLNA 投流）同样没有真机验证
- 诊断段那几行在真机上的排版效果

**说清楚**：单测证明的是"异常不再从 `serve()` 里冒出去"，
不能证明"音箱在实际网络下不会触发别的异常"。

---

## 七、顺带发现的一件事

`CrashGuard` 的设计是对的（不吞异常、交给系统），但它意味着：
**任何线程池里的裸任务，只要抛异常，都会杀进程。**

这次修的是两个推流服务。以后加后台线程池任务时，一律先包：
- 用 `safeLaunch(source)`（VM 内）
- 或在线程池入口自己 catch，并给出可见的计数/原因

否则下一次又是一个"用户说闪退、我们只能猜"的循环。
