package io.github.laozhudu.linkplay.console.playback.endpoint

import io.github.laozhudu.linkplay.protocol.dlna.DeviceCapabilities

/**
 * **三种后端的能力表，集中一处** —— 让它们的差别一眼可见。
 *
 * ## 为什么集中
 *
 * 上一版把能力表写在各自的实现类里（`private companion object`），后果是：
 * 我把本机和通用 UPnP 都写成"不能切歌、没有队列"，**没有任何东西提醒我它们是错的** ——
 * 因为三张表从来没有并排出现过。
 *
 * 现在并排放着，谁和谁不一样一目了然：
 *
 * | | 切歌 | 跳进度 | 静音 | 队列 |
 * |---|---|---|---|---|
 * | 本机 | 有 | 有 | **无** | 有 |
 * | DOSS | 按固件 | 有 | 有 | 有 |
 * | 通用 UPnP | 有 | 有 | 有 | 有 |
 *
 * 唯一真正为 false 的是**本机的静音**（手机只有音量，没有"设备侧静音"状态）。
 *
 * ## 写这张表的规矩
 *
 * **只写"平台真做不到的"，不写"我这轮没做"。**
 * 后者属于待办，写进能力表之后没有任何东西会提醒你补 —— 而界面会一直按
 * "做不到"来渲染，用户看到的是一个残缺的产品。
 */
object EndpointCapabilitiesCatalog {

    /** 本机：ExoPlayer。队列和切歌都有（`setMediaItems` / `seekToNextMediaItem`）。 */
    val local: EndpointCapabilities = EndpointCapabilities(
        canNext = true,
        canPrevious = true,
        canSeek = true,
        // 手机没有"设备侧静音"这个状态，只有音量
        canMute = false,
        hasQueue = true,
    )

    /**
     * 通用 DLNA 渲染器。
     *
     * ## 切歌为什么是 true，尽管设备自己不会切
     *
     * 实测那台 AirReceiver 对 `AVTransport.Next` 回 **701 transition not available**
     * —— 它没有自己的队列，不知道切到哪。所以
     * `DeviceCapabilities.supportsNextPrev = false`（**它自己会不会换**）。
     *
     * 但我们有列表，把下一首**重新投**给它就行（`PlaybackViewModel.nextByReplay`）。
     * 所以这里 `canNext = true`（**我们能不能让它换**）。
     *
     * 两个问题只差一个词，但答案相反。混在一起就会把按钮删掉。
     */
    val genericUpnp: EndpointCapabilities = EndpointCapabilities(
        canNext = true,
        canPrevious = true,
        canSeek = true,
        canMute = true,
        hasQueue = true,
    )

    /**
     * DOSS：**按固件问出来的能力位**，不写死。
     *
     * 家里三台里 doss3 是最老的固件（3.6.5310），它不支持切歌；
     * doss1/doss2（3.6.6623）支持。写死任何一边都会错一半。
     */
    fun linkPlay(caps: DeviceCapabilities): EndpointCapabilities = EndpointCapabilities(
        canNext = caps.supportsNextPrev,
        canPrevious = caps.supportsNextPrev,
        canSeek = caps.supportsSeek,
        canMute = caps.supportsMute,
        hasQueue = true,
    )
}
