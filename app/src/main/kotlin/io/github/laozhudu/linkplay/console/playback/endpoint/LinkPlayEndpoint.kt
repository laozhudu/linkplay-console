package io.github.laozhudu.linkplay.console.playback.endpoint

import io.github.laozhudu.linkplay.console.playback.PlaybackViewModel

/**
 * **DOSS 体系**（LinkPlay / WiiMu 方案，家里那三台 DS-1831）。
 *
 * ## 它能做什么
 *
 * LinkPlay 私有 httpapi 是表达力最强的一路：
 *
 * | 能力 | 有没有 | 依据 |
 * |---|---|---|
 * | 播放 / 暂停 / 停止 | 有 | `setPlayerCmd:` 系列 |
 * | 上一首 / 下一首 | **看设备能力位** | 老固件（如 doss3 的 3.6.5310）不支持 |
 * | 跳进度 / 音量 / 静音 | 有 | |
 * | 队列 | **有** | 我们自己推上去的，曲目单我们也存了 |
 *
 * 「上一首下一首看固件」这件事由 `caps` 回调回答 —— 能力是**问出来的**，不是按型号猜的。
 */
class LinkPlayEndpoint(
    vm: () -> PlaybackViewModel?,
    ip: String,
    input: () -> DeviceSnapshotInput,
    override val label: String,
    private val caps: () -> EndpointCapabilities,
) : DeviceEndpoint(vm, ip, input) {

    /** DOSS 的场景是"从别处投过来"，所以播放中那一个词是「投送中」。 */
    override val statusWord: String = "投送中"

    override fun capabilities(): EndpointCapabilities = caps()
}
