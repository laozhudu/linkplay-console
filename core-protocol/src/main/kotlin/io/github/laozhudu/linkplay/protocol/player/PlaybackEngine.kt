package io.github.laozhudu.linkplay.protocol.player

import io.github.laozhudu.linkplay.protocol.DeviceHttpClient
import io.github.laozhudu.linkplay.protocol.LinkPlayCommands
import io.github.laozhudu.linkplay.protocol.upnp.PositionInfo
import io.github.laozhudu.linkplay.protocol.upnp.TransportState
import io.github.laozhudu.linkplay.protocol.upnp.UpnpRendererClient

/**
 * 统一的播放能力抽象。
 *
 * 为什么需要这一层：这批设备有两套**都能出声**的通道，能力覆盖不同固件年代：
 * - UPnP（AVTransport/RenderingControl）：标准、能投任意 URL、状态字段规范；
 * - httpapi（`setPlayerCmd:*`）：WiiM 官方文档化的接口，老固件反而更可能支持，
 *   但**返回全是大写 OK/字符串**，能力随固件版本漂移。
 *
 * 上层不需要关心走哪条，只面对 [PlaybackSnapshot]。
 */
data class PlaybackSnapshot(
    val state: TransportState,
    val title: String?,
    val artist: String?,
    val albumArtUrl: String?,
    val positionSec: Long,
    val durationSec: Long,
    val volume: Int,
    val mute: Boolean,
    /** 输入源/模式的人类可读名，两套通道给的语义不同，各自填。 */
    val sourceLabel: String? = null,
) {
    val progress: Float
        get() = if (durationSec <= 0) 0f else (positionSec.toFloat() / durationSec.toFloat()).coerceIn(0f, 1f)
}

interface PlaybackEngine {
    /** 给 UI 显示的通道名，例如 `UPnP` / `HTTP API`。 */
    val channelName: String

    fun snapshot(): PlaybackSnapshot

    fun playUrl(url: String, title: String? = null)

    fun resume()
    fun pause()
    fun stop()
    fun seek(seconds: Long)
    fun setVolume(volume: Int)
    fun setMute(mute: Boolean)
}

/** UPnP 通道：投流靠 SetAVTransportURI + Play，状态靠 GetTransportInfo/GetPositionInfo。 */
class UpnpPlaybackEngine(private val client: UpnpRendererClient) : PlaybackEngine {
    override val channelName: String = "UPnP"

    override fun snapshot(): PlaybackSnapshot {
        val transport = client.transportInfo()
        val position = runCatching { client.positionInfo() }.getOrNull()
        val volume = runCatching { client.volume() }.getOrNull()
        return PlaybackSnapshot(
            state = transport.state,
            title = position?.trackMetadata?.title,
            artist = position?.trackMetadata?.artist,
            albumArtUrl = null,
            positionSec = position?.relTimeSec ?: 0,
            durationSec = position?.trackDurationSec ?: 0,
            volume = volume?.volume ?: 0,
            mute = volume?.mute ?: false,
            sourceLabel = "DLNA 渲染器",
        )
    }

    override fun playUrl(url: String, title: String?) {
        client.setAvTransportUri(url, title)
        client.play()
    }

    override fun resume() {
        client.play()
    }

    override fun pause() {
        client.pause()
    }

    override fun stop() {
        client.stop()
    }

    override fun seek(seconds: Long) {
        client.seek(seconds)
    }

    override fun setVolume(volume: Int) {
        client.setVolume(volume)
    }

    override fun setMute(mute: Boolean) {
        client.setMute(mute)
    }
}

/**
 * httpapi 通道：走 WiiM 官方文档的 `setPlayerCmd:*`（见 docs/01 §11）。
 *
 * 已实测（docs/14 §0.1，2026-11）：这台 2018 年固件的 `setPlayerCmd:*` **完整可用**，
 * `mute:1` 写入后 `GetMute` 回读为 1，`play:<url>` 能让设备主动来拉流并播完。
 * 早先「老固件不支持」的结论是探测脚本参数写错导致的误判，已更正。
 *
 * 本机实测的字段差异（与官方文档不同，必须按实际处理）：
 * - `getPlayerStatus` **确实带** `vol` / `mute` / `Title` / `Artist` / `Album` / `uri`，
 *   文本字段是 HEX 编码（`556E6B6E6F776E` = "Unknown"）；
 * - 而 `getMetaInfo` 在本机是 `unknown command`，所以**不能依赖它**补标题，
 *   只能把它当作「某些固件上存在的增强」，用 runCatching 包住；
 * - 直播流 `totlen` 为 0，进度条应显示为「直播」而不是 0%。
 */
class HttpApiPlaybackEngine(
    private val client: DeviceHttpClient,
    private val deviceIp: String,
) : PlaybackEngine {
    override val channelName: String = "HTTP API"

    /** 探测这条通道是否可用：能解析出 `getPlayerStatus` 就算可用。 */
    fun probe(): Boolean = runCatching { status() }.isSuccess

    fun status(): PlayerStatus = PlayerStatus.parse(client.send(deviceIp, LinkPlayCommands.getPlayerStatus()))

    fun metadata(): PlayerMetadata? =
        runCatching { PlayerMetadata.parse(client.send(deviceIp, LinkPlayCommands.getMetaInfo())) }.getOrNull()

    override fun snapshot(): PlaybackSnapshot {
        val s = status()
        // getMetaInfo 只在部分固件存在（本机是 unknown command），能拿到才用
        val meta = runCatching { metadata() }.getOrNull()
        return PlaybackSnapshot(
            state = when (s.playState) {
                PlayState.PLAY -> TransportState.PLAYING
                PlayState.PAUSE -> TransportState.PAUSED_PLAYBACK
                PlayState.STOP -> TransportState.STOPPED
                PlayState.LOADING -> TransportState.TRANSITIONING
                PlayState.UNKNOWN -> TransportState.UNKNOWN
            },
            // 标题优先用 getPlayerStatus 的 HEX 字段（本机可靠），getMetaInfo 兜底
            title = s.title ?: meta?.title,
            artist = s.artist ?: meta?.artist,
            albumArtUrl = meta?.albumArtUrl,
            positionSec = s.positionSec,
            durationSec = s.durationSec,
            volume = s.volume,
            mute = s.mute,
            sourceLabel = PlayerStatus.modeLabel(s.mode),
        )
    }

    override fun playUrl(url: String, title: String?) {
        client.send(deviceIp, LinkPlayCommands.playUrl(url))
    }

    override fun resume() {
        client.send(deviceIp, LinkPlayCommands.resume())
    }

    override fun pause() {
        client.send(deviceIp, LinkPlayCommands.pause())
    }

    override fun stop() {
        client.send(deviceIp, LinkPlayCommands.stopPlayer())
    }

    override fun seek(seconds: Long) {
        client.send(deviceIp, LinkPlayCommands.seekPlayer(seconds))
    }

    override fun setVolume(volume: Int) {
        client.send(deviceIp, LinkPlayCommands.playerVolume(volume))
    }

    override fun setMute(mute: Boolean) {
        client.send(deviceIp, LinkPlayCommands.playerMute(mute))
    }
}

/** 供 UI 复用的时间格式化，避免两套通道各写一份。 */
internal fun formatSeconds(seconds: Long): String = PositionInfo.formatTime(seconds)
