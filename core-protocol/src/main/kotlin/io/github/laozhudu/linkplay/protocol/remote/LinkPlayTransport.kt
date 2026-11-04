package io.github.laozhudu.linkplay.protocol.remote

import io.github.laozhudu.linkplay.protocol.DeviceHttpClient
import io.github.laozhudu.linkplay.protocol.LinkPlayCommands
import java.io.IOException

/**
 * LinkPlay 方言的传输实现 —— 唯一的职责是**语义 → 方言**的翻译。
 *
 * ## 为什么翻译函数是 `internal`
 *
 * `encode()` 会返回 `setPlayerCmd:pause` 这种协议文本。
 * 如果 app 层能调到它，半年后一定又有人在界面里拼字符串 —— 这一层就白做了。
 *
 * `internal` 之后：core-protocol 内部的单测能测它（锁死翻译表），
 * app 层只能调 `dispatch()`。**一致性靠编译期，不靠自觉。**
 *
 * ## 怎么判定"成功"
 *
 * LinkPlay 的成功回包有三种形态，实测都见过：
 * - `OK`（`setPlayerCmd:*`、`switchmode:*`）
 * - `OK
` / 空 body（部分固件）
 * - 直接回 JSON（`getStatusEx` 类查询，但那些不走 dispatch）
 *
 * 失败形态：`Failed`、`Error`、或数字错误码（`704`）。
 * 无法归类的回包给 [DispatchFailure.Unparsable] —— **不猜成成功**。
 * 猜成成功是这类项目最贵的错：用户看到"已发送"但什么都没发生，然后来报"没反应"。
 */
class LinkPlayTransport(
    private val client: DeviceHttpClient,
) : RemoteTransport {

    override fun supports(command: RemoteCommand): Boolean = when (command) {
        // LinkPlay 系全支持：私有 httpapi 有对应命令
        is RemoteCommand.Play,
        is RemoteCommand.Pause,
        is RemoteCommand.Stop,
        is RemoteCommand.Next,
        is RemoteCommand.Previous,
        is RemoteCommand.Seek,
        is RemoteCommand.SetVolume,
        is RemoteCommand.Mute,
        is RemoteCommand.Unmute,
        is RemoteCommand.SelectInput,
        is RemoteCommand.Preset,
        -> true
    }

    override suspend fun dispatch(deviceIp: String, command: RemoteCommand): DispatchResult {
        val raw = try {
            client.send(deviceIp, encode(command))
        } catch (io: IOException) {
            // 超时/拒绝连接/DNS：设备可能只是睡了，不是"故障"
            return DispatchResult.Failed(DispatchFailure.Unreachable)
        } catch (t: Throwable) {
            // 解析层意外：仍然不抛，交给调用方按 Failed 处理
            return DispatchResult.Failed(DispatchFailure.Unparsable(t.message ?: t.javaClass.simpleName))
        }
        return judge(raw)
    }

    /** 语义 → 方言。**单测逐条锁死这张表**，它是唯一会漂移的地方。 */
    internal fun encode(command: RemoteCommand): String = when (command) {
        RemoteCommand.Play -> "setPlayerCmd:play"
        RemoteCommand.Pause -> "setPlayerCmd:pause"
        RemoteCommand.Stop -> "setPlayerCmd:stop"
        RemoteCommand.Next -> "setPlayerCmd:next"
        RemoteCommand.Previous -> "setPlayerCmd:prev"
        is RemoteCommand.Seek -> LinkPlayCommands.seekPlayer(command.positionSec.toLong())
        is RemoteCommand.SetVolume -> LinkPlayCommands.playerVolume(command.level.coerceIn(0, 100))
        RemoteCommand.Mute -> LinkPlayCommands.playerMute(true)
        RemoteCommand.Unmute -> LinkPlayCommands.playerMute(false)
        is RemoteCommand.SelectInput -> LinkPlayCommands.switchMode(command.id)
        // uiIndex 是 0 基，LinkPlayCommands.presetKey 内部 +1 并钳到 1..6
        is RemoteCommand.Preset -> LinkPlayCommands.presetKey(command.uiIndex)
    }

    /**
     * 判读回包。
     *
     * 顺序有意：先认错误码，再认 OK。反过来的话 `"OK"` 出现在错误文本里（如
     * `"Failed: OK timeout"`）会被误判成成功。
     */
    internal fun judge(raw: String): DispatchResult {
        val text = raw.trim()
        if (text.isEmpty()) {
            // 空 body：部分固件对 pause/stop 就这样回。实测是成功的。
            return DispatchResult.Acked(raw)
        }
        // 1. 明确的失败词
        if (text.startsWith("Failed", ignoreCase = true) || text.startsWith("Error", ignoreCase = true)) {
            return DispatchResult.Failed(DispatchFailure.Rejected(code = 0, text = text.take(200)))
        }
        // 2. 纯数字 = 错误码（LinkPlay 用 HTTP 200 + 裸码表达业务错误）
        text.toIntOrNull()?.let { code ->
            return DispatchResult.Failed(DispatchFailure.Rejected(code = code, text = text))
        }
        // 3. OK
        if (text.startsWith("OK", ignoreCase = true)) return DispatchResult.Acked(raw)
        // 4. 其它：不猜
        return DispatchResult.Failed(DispatchFailure.Unparsable(text.take(200)))
    }
}
