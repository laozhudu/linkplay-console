package io.github.laozhudu.linkplay.protocol.remote

/**
 * 遥控指令 —— **语义**，不是协议文本。
 *
 * ## 为什么要有这一层
 *
 * 改造前，每个调用点直接拼字符串：
 *
 * ```kotlin
 * host.send(ip, LinkPlayCommands.playerVolume(next))
 * host.send(ip, if (playing) "setPlayerCmd:pause" else "setPlayerCmd:play")
 * ```
 *
 * 后果是**调用点知道方言**。加第二类设备（DLNA 电视盒子、未来的蓝牙）时，
 * 每个调用点都要变成 `if (kind == BT) … else …`，而且必然有地方漏掉。
 *
 * 语义化之后：调用点只说「把音量设成 45」，由 [RemoteTransport] 决定怎么表达。
 *
 * ## 为什么**不**包含改名 / 固件 / 恢复出厂
 *
 * 这三类是**运维动作**，不是业务能力。参考 Matter 的分法：
 * 业务能力放 Application Cluster，发现/寻址/**诊断**/固件升级放 Utility Cluster（Endpoint 0）。
 *
 * 区别很实际：业务能力**每个协议都有等价物**（谁都能调音量）；
 * 运维动作是**厂商私有的**（只有 LinkPlay 有 `setDeviceName`，DLNA 没有"改名"这个概念）。
 *
 * 把运维动作塞进通用指令，等于给通用层加一条"只有一种设备支持"的分支 —— 那层就不通用了。
 * 所以它们继续走 [io.github.laozhudu.linkplay.protocol.LinkPlayCommands]，不通用化。
 *
 * ## 为什么 `Raw` 逃生口是 internal
 *
 * 红外/厂商私有码确实需要发裸码。但那是**传输层内部**的事。
 * 如果界面能拿到 `Raw`，半年后界面又开始拼字符串 —— 这一层就白做了。
 */
sealed interface RemoteCommand {

    /** 开始/继续播放。 */
    data object Play : RemoteCommand

    /** 暂停（保留播放位置）。 */
    data object Pause : RemoteCommand

    /** 停止（清位置）。 */
    data object Stop : RemoteCommand

    /** 下一首。**依赖设备自己握着队列**：通用 DLNA 渲染器没有队列，会回 701。 */
    data object Next : RemoteCommand

    /** 上一首。同上。 */
    data object Previous : RemoteCommand

    /**
     * 跳转到指定秒数。
     * @param positionSec 从 0 开始
     */
    data class Seek(val positionSec: Int) : RemoteCommand

    /**
     * 设定绝对音量。
     * @param level 0-100。**越界由实现方钳制**，不在这里抛异常 ——
     *   音量来自滑块/音量键/组内同步三个来源，抛异常会把一次滑动变成一次崩溃。
     */
    data class SetVolume(val level: Int) : RemoteCommand

    /** 静音。 */
    data object Mute : RemoteCommand

    /** 取消静音。 */
    data object Unmute : RemoteCommand

    /**
     * 切换输入源。
     *
     * @param id 输入源标识，用设备自己的叫法（LinkPlay：`wifi` / `bluetooth` / `line-in` /
     *   `optical` / `udisk`）。**不用枚举**：各家的输入源集合不一样，枚举一建就得为每台设备改一次。
     *   界面渲染的候选来自 [RemoteTransport] 侧的能力查询，不是这里。
     */
    data class SelectInput(val id: String) : RemoteCommand

    /**
     * 按机身预设键（LinkPlay 的 6 个物理键）。
     *
     * @param uiIndex **0 基**（界面第 0-5 个），设备侧要 +1 变成 `MCUKeyShortClick:1..6`。
     *   保持 0 基是为了和界面循环索引一致 —— 转换只发生在一处（[LinkPlayTransport.encode]）。
     */
    data class Preset(val uiIndex: Int) : RemoteCommand
}
