package io.github.laozhudu.linkplay.console

import io.github.laozhudu.linkplay.protocol.DeviceHttpClient
import io.github.laozhudu.linkplay.protocol.LinkPlayStatus
import io.github.laozhudu.linkplay.protocol.session.SavedDevice
import io.github.laozhudu.linkplay.protocol.upnp.UpnpRendererClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

/**
 * `ConsoleViewModel` 对外暴露给各个控制器的能力面。
 *
 * ## 为什么是接口而不是直接传 ViewModel
 *
 * 1. **避免循环依赖**：VM → Controller → VM 会让"谁改谁"不可读。
 * 2. **依赖面可见**：下面这十几个方法就是控制器的全部外部能力。
 *    每多一个，就多一条能"顺手改别人"的路。
 *
 * 这条约束在拆 `GroupController` 时真的救了两次：
 * - `refreshAll()` 是 suspend，但 `removeFromGroup` 在函数体顶层调用它 → 编译报错。
 *   若直接拿 ViewModel 调非 suspend 的 `probeSaved()` 根本不会报错，只是**刷新时机悄悄变了**。
 * - `MultiroomClient(client)` 拿不到 `client` → 编译报错，逼着显式加 `http()`。
 *
 * ## 为什么共用一份而不是每个控制器一个
 *
 * `GroupController` 和 `DeviceControl` 需要的能力高度重叠（读写状态、发命令、跑协程）。
 * 各写一份接口，就会出现"同一个能力在两个接口里名字不同"——那正是本项目一直在治的病。
 *
 * 实现由 `ConsoleViewModel` 里的一个匿名对象提供，见 `consoleHost`。
 */
internal interface ConsoleHost {

    val state: StateFlow<ConsoleUiState>

    fun update(transform: (ConsoleUiState) -> ConsoleUiState)

    fun log(message: String)

    /** 带 busy 闸门的动作执行（与 ViewModel 的 `run` 同一套语义）。 */
    fun run(label: String, block: suspend () -> Unit)

    /**
     * 起一个协程。**必须给 [source]**：诊断报告里靠它定位"是哪条路崩的"，
     * 用户报"点播放没反应"时，标签要能对上那句话。
     * 写成可选参数就会有人不写，所以设计成必填。
     */

    /** 启动一个协程（异常已被 `safeLaunch` 收口，不会闪退）。 */
    fun launch(source: String, block: suspend CoroutineScope.() -> Unit)

    /** 原始 HTTP 客户端（`MultiroomClient` 这类协议客户端需要它）。 */
    fun http(): DeviceHttpClient

    fun send(ip: String, command: String): String

    fun status(ip: String): LinkPlayStatus

    /** 探活 + 刷新卡片并**等待完成**（协程内用；原 `probeSaved` + `refreshDevicesInGroup`）。 */
    suspend fun refreshAll()

    /** 同上但不等（非协程上下文用，内部自己起协程）。 */
    fun refresh()

    fun renderer(device: SavedDevice): UpnpRendererClient

    /** 把音量塞进合并通道（拖动会连续产生几十个值，逐条发 HTTP 会堵住）。 */
    fun pushDeviceVolume(ip: String, volume: Int)

    /**
     * 只刷新卡片（不发 SSDP、不动设备记录）。
     *
     * 为什么单独一条：控制器做完动作要"让界面立刻反映"，但不该顺带触发一次探活
     * —— 那会把 `online` 状态也重写一遍，动作后界面闪一下。
     */
    suspend fun refreshCards()

    /**
     * 按 uuid/ip 的候选键读上次的播放快照。
     *
     * 控制器不该知道 `SessionStore` 的键规则（`PlaybackKey.candidates` 有过一次
     * "三处存盘键不一致"的教训），所以这一步收在宿主里。
     */
    fun loadPlayback(uuid: String?, ip: String): io.github.laozhudu.linkplay.protocol.session.PlaybackSession?
}
