package io.github.laozhudu.linkplay.console.diagnostics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 带异常边界的 `viewModelScope.launch` —— 全项目启动协程只用这一个。
 *
 * ## 为什么这是"闪退"的根因（不是补丁）
 *
 * `viewModelScope` 的 Job 虽然是 `SupervisorJob`，但它只隔离**子协程之间**：
 * 一个 `launch {}` 内部抛出的异常如果没人接，会走 `CoroutineExceptionHandler`
 * → 默认处理器 → `Thread.uncaughtExceptionHandler` → **进程崩溃**。
 *
 * 也就是说：**任何一次网络异常、任何一次解析异常，都等于一次闪退**。
 * 我们后台有 4 个常驻轮询（3 秒卡片心跳、2.5 秒组音量、1 秒定时器、启动探活），
 * 加上每次点击触发的协程，一共 69 个 `launch` 点 —— 其中任何一个漏掉 try/catch，
 * 用户看到的就是"用着用着突然闪退，系统说空指针"。
 *
 * 修法不是给每个点补 try/catch（那还是补丁，下次新增代码又会漏），
 * 而是**把边界收在一个地方**：所有启动都走 [safeLaunch]，异常统一进 [IssueLog]。
 *
 * ## 为什么不吞掉 CancellationException
 *
 * 取消是正常控制流（切设备、页面销毁）。把它当异常记下来会污染日志，
 * 更严重的是**会让结构化并发失效**。所以原样抛出。
 */
fun ViewModel.safeLaunch(source: String, block: suspend CoroutineScope.() -> Unit): Job =
    viewModelScope.launch {
        try {
            block()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            // 记下来而不是崩掉：单台设备读失败不该杀掉整个 App
            IssueLog.record(source, error)
        }
    }
