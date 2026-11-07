package io.github.laozhudu.linkplay.console.playback.endpoint

import io.github.laozhudu.linkplay.console.playback.PlaybackViewModel

/**
 * **设备那一路的公共骨架** —— DOSS 体系和通用 UPnP 体系共用的部分。
 *
 * ## 为什么可以共用（这不违背"两个独立模块"）
 *
 * 用户说的是「通用 upnp 是一个独立模块，doss 体系是另一个模块」，**不是**"两套代码一行不许像"。
 * 两者真正不同的地方只有两处：
 *
 * | | DOSS | 通用 UPnP |
 * |---|---|---|
 * | 设备侧执行 | `PlaybackViewModel`（LinkPlay 命令） | 同一个 VM（探测后走 SOAP）|
 * | 能力 | 切歌 / 预设键 / 组网 / 输入源 | **只有音量·静音·放·停·进度** |
 *
 * 「把一条播放意图送到设备」这件事两边完全一样，而且**都该走同一个 VM** ——
 * 绕过它自己发 HTTP 就会出现"界面改了、状态没跟上"（项目里重复犯过）。
 *
 * 真正会漂移的是**能力表**，所以能力表由各子类自己写死，不继承、不给默认值。
 *
 * ## 状态投影
 *
 * 两路的设备状态来源是同一个轮询，字段形状一样。
 * 措辞差异通过 [statusWord] 一个抽象属性表达，不复制整个 `snapshot`。
 */
abstract class DeviceEndpoint(
    /** 当前受控的 VM。取不到时所有控制方法返回 false/无操作 —— **不假装成功**。 */
    private val vm: () -> PlaybackViewModel?,
    private val ip: String,
    /** 每帧现取状态（纯读，不触发 IO）。 */
    private val input: () -> DeviceSnapshotInput,
) : PlaybackEndpoint {

    override val key: String get() = "ip:" + ip

    /** 播放中时那一个词。DOSS 说「投送中」，UPnP 说「播放中」。 */
    protected abstract val statusWord: String

    override fun snapshot(): EndpointState {
        val i = input()
        return EndpointState(
            playing = i.playing,
            title = i.title,
            artist = i.artist,
            positionSec = i.positionSec,
            durationSec = i.durationSec,
            volume = i.volume,
            mute = i.mute,
            queueIndex = i.queueIndex,
            queueCount = i.queueCount,
            statusText = statusTextOf(i),
        )
    }

    /**
     * 状态行。
     *
     * ## 为什么"输入源"要单独说（真机问题）
     *
     * 切到线路输入/蓝牙/U盘时，设备确实在放，但**放的不是我们投的东西**。
     * 界面只写「投送中」会让用户以为 App 点的歌生效了 —— 实际声音来自 AV 线。
     *
     * 反过来，日常网络播放时**不提输入源**（否则每屏都挂着一句废话）。
     */
    private fun statusTextOf(i: DeviceSnapshotInput): String = when {
        i.inputSourceLabel != null ->
            "输入源 " + i.inputSourceLabel + " · " + (if (i.playing) "播放中" else "待播放")
        i.playing -> statusWord
        i.stopped -> "待播放"
        else -> "已暂停"
    }

    override suspend fun toggle() {
        val target = vm() ?: return
        if (input().playing) target.pause() else target.play()
    }

    override suspend fun next() {
        if (!capabilities().canNext) return
        vm()?.next()
    }

    override suspend fun previous() {
        if (!capabilities().canPrevious) return
        vm()?.previous()
    }

    override suspend fun seek(positionSec: Long) {
        if (!capabilities().canSeek) return
        vm()?.seekTo(positionSec)
    }

    override suspend fun setVolume(percent: Int) {
        vm()?.setVolumeLive(percent.coerceIn(0, 100))
    }

    override suspend fun setMute(mute: Boolean) {
        if (!capabilities().canMute) return
        vm()?.toggleMute()
    }
}

/**
 * 设备那一路投影的**原料**。
 *
 * 刻意用"算好的布尔和文案"而不是原始协议字段：
 * `DeviceEndpoint` 不该认识 `plcurl`、`mode` 这些编码细节。
 */
data class DeviceSnapshotInput(
    val playing: Boolean = false,
    /** 真的停在停止态（区别于"暂停"）。 */
    val stopped: Boolean = false,
    val title: String? = null,
    val artist: String? = null,
    val positionSec: Long = 0,
    val durationSec: Long = 0,
    val volume: Int = 0,
    val mute: Boolean = false,
    val queueIndex: Int = 0,
    val queueCount: Int = 0,
    /** 非网络输入（线路/蓝牙/U盘/光纤）时给出人话名字，否则 null。 */
    val inputSourceLabel: String? = null,
)
