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
 * | **上一首 / 下一首** | 有 | **有** —— AVTransport 的 `Next` / `Previous` |
 * | **队列** | 有 | **有** —— 但那是**我们的**队列，不是它的 |
 *
 * ## 两个"队列"必须分清（我上一版就混了）
 *
 * | | 含义 | DOSS | 通用 UPnP |
 * |---|---|---|---|
 * | `supportsPlayQueue` | 设备**持有**整份列表，App 退出照播 | 有（私有） | 没有 |
 * | `hasQueue`（本接口） | **我们**有列表可看、可点第 N 首 | 有 | **有** |
 *
 * 上一版我把后者写成 false，理由是"它自己握着列表，我们读不到内容"——
 * 混淆了这两件事。界面上的播放列表一直是**我们自己的**列表，
 * 逐首投给它就行，跟它持不持有列表无关。
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

    /** 能力表在 `EndpointCapabilitiesCatalog`（三张表并排放，差别一眼可见）。 */
    override fun capabilities(): EndpointCapabilities = EndpointCapabilitiesCatalog.genericUpnp

    /**
     * **重投下一首** —— 不是发 `AVTransport.Next`。
     *
     * 那台 AirReceiver 对 `Next` 回 701 transition not available（实测，
     * 见 `DeviceCapabilitiesTest` 里那条断言）。它没有自己的队列，不知道切到哪。
     *
     * 但我们有队列，把下一首重新投给它，效果一样 ——
     * 这就是用户说的「通用 upnp 模块是能播放列表能切歌」的实现方式。
     */
    override suspend fun next() {
        vm()?.nextByReplay()
    }

    override suspend fun previous() {
        vm()?.previousByReplay()
    }
}
