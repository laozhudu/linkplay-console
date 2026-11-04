package io.github.laozhudu.linkplay.protocol.remote

/**
 * 传输层 —— 负责把 [RemoteCommand] 送到设备，并如实报告结果。
 *
 * ## 为什么是接口而不是直接用 LinkPlayTransport
 *
 * 现在只有一个实现者（LinkPlay）。接口看起来"多余"，但它的价值不在抽象，在**约束**：
 * 调用方（`DeviceControl`）只能通过 `dispatch` 发指令，拿不到"拼字符串"的能力。
 * 这是编译期强制，不靠自觉。
 *
 * 加第二类设备时（DLNA 电视盒子已经在用、未来的蓝牙），实现者多一个，抽象自然成立。
 * **不在只有一个实现者时就建六个模块** —— 唯一的实现者会悄悄污染抽象（见 docs/50）。
 *
 * ## 为什么 `supports` 是必需的
 *
 * 项目铁律：**不给按了没反应的按钮**。
 * 界面要先问 `supports(command)` 再决定渲染不渲染，而不是让用户点一下看报什么错。
 */
interface RemoteTransport {

    /**
     * 这台传输**能**表达这条指令吗？
     *
     * 注意与"设备是否支持"的区别：这里回答的是**传输/协议层**有没有等价表达。
     * 例如通用 DLNA 没有"按机身预设键"这个概念 → `Preset` 返回 false。
     * 而"这台特定设备固件太老不支持 Seek"属于运行时失败，由 [DispatchResult.Failed] 表达。
     */
    fun supports(command: RemoteCommand): Boolean

    /**
     * 下发一条指令。
     *
     * **不抛异常**：所有失败都通过 [DispatchResult.Failed] 返回。
     * 理由：调用点在协程里，抛异常要走 `safeLaunch` 的异常边界，
     * 而"设备睡了"是正常业务情况，不该被记成 ERROR 级故障。
     */
    suspend fun dispatch(deviceIp: String, command: RemoteCommand): DispatchResult
}
