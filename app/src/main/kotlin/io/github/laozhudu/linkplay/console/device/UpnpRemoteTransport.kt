package io.github.laozhudu.linkplay.console.device

import io.github.laozhudu.linkplay.protocol.remote.DispatchFailure
import io.github.laozhudu.linkplay.protocol.remote.DispatchResult
import io.github.laozhudu.linkplay.protocol.remote.RemoteCommand
import io.github.laozhudu.linkplay.protocol.remote.RemoteTransport
import io.github.laozhudu.upnp.GenericRenderer

/**
 * 通用 DLNA 渲染器（电视盒子那一类）的指令通道。
 *
 * ## 为什么必须单独一个（真机问题）
 *
 * 用户：「通用 upnp 设备无法控制音量」。
 *
 * 根因很直接：`DeviceControl` 里写死了
 *
 * ```kotlin
 * private val transport: RemoteTransport = LinkPlayTransport(host.http())
 * ```
 *
 * 于是**不管设备是什么类型，都发 LinkPlay 私有 httpapi**。
 * 电视盒子不认这些命令 → 静默失败 → 用户按了音量键什么都没发生，
 * 连个错误提示都没有（`dispatch` 拿不到异常，因为连接是通的，只是设备不认）。
 *
 * 而 `GenericRenderer` 那边**早就实现了** `setVolume` / `setMute`（走 SOAP
 * `RenderingControl`），只是从来没人从这条路上调它。
 *
 * ## 只提供设备真的有的能力
 *
 * 通用渲染器只有 AVTransport + RenderingControl 两个服务，所以：
 * - 能：播放 / 暂停 / 停止 / 音量 / 静音 / 进度
 * - 不能：上一首下一首（设备回 701 transition not available，实测）、预设键、组网、声道
 *
 * ## ⚠️ "这条通道没有下一首" ≠ "用户在界面上不能切歌"
 *
 * 上一首下一首不在这条**设备命令**通道里，因为它走不通（实测 701）。
 * 但用户在自己的 App 里是**能切**的 —— 我们从自己的列表里重投下一首
 * （`PlaybackViewModel.nextByReplay`），效果一样。
 *
 * 所以：
 *   - `supports(Next)` = false  ← 这条通道发不出去
 *   - `EndpointCapabilities.canNext` = true  ← 界面该画这个按钮
 *
 * 把这两件事混在一起，就是上一版删掉切歌按钮的原因。
 *
 * 不能的一律 `supports = false`，界面据此不画按钮（项目铁律：不给按了没反应的按钮）。
 */
class UpnpRemoteTransport(
    /** 按 IP 取已缓存/新建的渲染器；拿不到返回 null（设备没应答）。 */
    private val rendererOf: suspend (String) -> GenericRenderer?,
) : RemoteTransport {

    override fun supports(command: RemoteCommand): Boolean = when (command) {
        is RemoteCommand.SetVolume -> true
        RemoteCommand.Mute -> true
        RemoteCommand.Unmute -> true
        RemoteCommand.Play -> true
        RemoteCommand.Pause -> true
        RemoteCommand.Stop -> true

        is RemoteCommand.Seek -> true
        else -> false
    }

    override suspend fun dispatch(deviceIp: String, command: RemoteCommand): DispatchResult {
        val renderer = runCatching { rendererOf(deviceIp) }.getOrNull()
            ?: return DispatchResult.Failed(DispatchFailure.Unreachable)
        return try {
            when (command) {
                is RemoteCommand.SetVolume -> renderer.setVolume(command.level)
                RemoteCommand.Mute -> renderer.setMute(true)
                RemoteCommand.Unmute -> renderer.setMute(false)
                RemoteCommand.Play -> renderer.play()
                RemoteCommand.Pause -> renderer.pause()
                RemoteCommand.Stop -> renderer.stop()

                is RemoteCommand.Seek -> renderer.seek(command.positionSec.toLong())
                else -> return DispatchResult.Failed(DispatchFailure.Unsupported)
            }
            // 通用渲染器对成功动作回 SOAP 200 + 空响应体
            DispatchResult.Acked("soap-ok")
        } catch (error: Throwable) {
            // 设备明确拒绝 / 连不上 / 回包看不懂 —— 交给协议层翻译成人话
            DispatchResult.Failed(
                when (error) {
                    is io.github.laozhudu.upnp.RendererException.Unsupported -> DispatchFailure.Unsupported
                    is java.io.IOException -> DispatchFailure.Unreachable
                    else -> DispatchFailure.Rejected(0, error.message ?: error.javaClass.simpleName)
                },
            )
        }
    }
}
