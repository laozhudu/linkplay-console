package io.github.laozhudu.linkplay.console.playback.endpoint

import io.github.laozhudu.linkplay.console.playback.PlaybackViewModel

/**
 * **通用 UPnP 体系**（标准 DLNA 渲染器，家里那台电视盒子 `E900V22C-175`）。
 *
 * ## 它和 DOSS 不是一回事（用户 2026-11-07）
 *
 * > 「通用 upnp 是一个独立模块，doss 体系是另一个模块」
 *
 * 这不是命名讲究 —— 两者的**表达力差一个量级**：
 *
 * | 能力 | DOSS | 通用 UPnP |
 * |---|---|---|
 * | 音量 / 静音 | 有 | 有（`RenderingControl` 标准动作）|
 * | 播放 / 暂停 / 停止 | 有 | 有（`AVTransport`）|
 * | 跳进度 | 有 | 有 |
 * | **上一首 / 下一首** | 有 | **没有** —— DLNA 标准里没有这个动作 |
 * | **队列** | 有 | **没有** —— 它自己握着列表，我们读不到内容 |
 *
 * 所以这台在界面上**不该出现切歌按钮**。原来正是因为
 * 界面按 `DeviceCapabilities` 猜、而能力表报得太宽，才出现"点了没反应"。
 *
 * ## 关于 `.175` 的 80 端口
 *
 * 那台设备的 `LOCATION` 就是这个端口，探测必然失败 —— 属正常状态，
 * 界面显示「离线」即可，**不显示红色报错**（那是"设备坏了"的意思，会误导）。
 */
class UpnpEndpoint(
    vm: () -> PlaybackViewModel?,
    ip: String,
    input: () -> DeviceSnapshotInput,
    override val label: String,
) : DeviceEndpoint(vm, ip, input) {

    /** 通用渲染器上放的可能是任何人推的内容，措辞用中性的「播放中」。 */
    override val statusWord: String = "播放中"

    /**
     * **写死的能力表** —— 这是这个模块最重要的一行。
     *
     * 不查设备、不查探测结果：DLNA 标准里就是没有下一首。
     * 万一某个盒子自己实现了，那是它的扩展，我们不为它开例外
     * （开了以后每个盒子都要单独适配，正是 docs/50 说的"唯一实现者污染抽象"）。
     */
    override fun capabilities(): EndpointCapabilities = UPnP_CAPS

    private companion object {
        val UPnP_CAPS = EndpointCapabilities(
            canNext = false,
            canPrevious = false,
            canSeek = true,
            canMute = true,
            hasQueue = false,
        )
    }
}
