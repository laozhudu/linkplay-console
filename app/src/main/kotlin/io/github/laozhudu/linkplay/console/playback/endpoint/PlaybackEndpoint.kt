package io.github.laozhudu.linkplay.console.playback.endpoint

/**
 * **一种"能出声的东西"** —— 播放页背后的统一契约。
 *
 * ## 为什么要有这个接口（用户 2026-11-07 指出）
 *
 * > 「通用 upnp 是一个独立模块，doss 体系是另一个模块，肯定是你考虑欠缺导致的。
 * >  还有本机播放应该也是个独立模块」
 *
 * 上一轮的修法是错的：我在 `DeviceControl` 里加了一个 `transportFor(ip)` 查表，
 * 按能力位决定发 LinkPlay 私有命令还是 UPnP SOAP。那只是**在一个 if 后面接第二个 if**。
 *
 * 真实情况是家里有**三套完全平行的体系**：
 *
 * | 体系 | 状态从哪来 | 控制怎么发 | 界面分支 |
 * |---|---|---|---|
 * | 本机 | ExoPlayer 回调 | `LocalPlayer` / `AudioManager` | 20 处 `if (isLocalOutput)` |
 * | DOSS | `getPlayerStatus` 轮询 | LinkPlay 私有 httpapi | `transportFor` 查表 |
 * | 通用 UPnP | AVTransport 轮询 | SOAP RenderingControl | 同上 |
 * | ↑ 播放/暂停 | 同上 | **又一条**：`host.renderer(device).play()` | 第三套 |
 *
 * 后果是可预测的：每加一个体系，**每个读状态的地方都要加一个分支**。
 * `if (isLocalOutput)` 已经复现到 20 处 —— 而且上一次漏掉 5 个字段，
 * 表现就是"切到本机，顶栏变了但曲名还是音箱那首歌"。
 *
 * ## 这个接口怎么解决
 *
 * 每个体系实现一次 [snapshot]（把自家状态投影成统一形状）和一组控制方法。
 * **界面只问 endpoint，不再认识"本机/设备"的区别。**
 *
 * 判定依据写在各自的 `capabilities()` 里 —— 本机没有队列就报 `hasQueue = false`，
 * 界面据此不画播放列表入口，而不是靠调用方 `if` 猜。
 *
 * ## 为什么 [snapshot] 是函数不是 Flow
 *
 * 三个体系的状态源不同（ExoPlayer 回调 / LinkPlay 轮询 / SOAP 轮询），
 * 但它们**都已经存在于某处**（`LocalPlayer.ui`、`ConsoleUiState`）。
 * 再造三条 Flow 就是制造第二份真相 —— 那份副本一定会和原件漂移。
 *
 * 所以这里只做**投影**：Compose 依赖上层状态重组时调一次，纯函数，便宜。
 */
interface PlaybackEndpoint {

    /** 稳定的机器标识：`local` / `ip:192.168.99.164`。用于日志和去重，不给人看。 */
    val key: String

    /** 给人看的名字：`本机` / `doss1`。 */
    val label: String

    /**
     * 这台**能做什么**。
     *
     * 界面拿它决定画不画按钮 —— 项目铁律：**不给按了没反应的按钮**。
     * 例如本机播放器只放单曲 → `hasQueue = false` → 播放列表入口整块不画，
     * 而不是画一个点开是空的东西。
     */
    fun capabilities(): EndpointCapabilities

    /** 把自家状态投影成统一形状。纯函数，不触发 IO。 */
    fun snapshot(): EndpointState

    // ---- 控制入口。调用方不关心底下是谁。----

    suspend fun toggle()

    /** 不支持的体系由 [capabilities] 先挡住；真被调到就安静忽略，不假装成功。 */
    suspend fun next()

    suspend fun previous()

    suspend fun seek(positionSec: Long)

    suspend fun setVolume(percent: Int)

    /** 本机没有"设备侧静音"这个概念 —— 实现里退化成音量归零。 */
    suspend fun setMute(mute: Boolean)
}

/**
 * 统一的**状态视图**。
 *
 * 字段都刻意用"最不依赖具体协议"的单位：
 * 秒（不是毫秒/微秒两个都有）、0..100（不是 ExoPlayer 的 0f..1f）。
 * 换算是各自实现的事，界面不该知道。
 */
data class EndpointState(
    val playing: Boolean = false,
    /** 没有就是 null，**不要用 "" 冒充**（否则界面会画一行空白标题）。 */
    val title: String? = null,
    val artist: String? = null,
    val positionSec: Long = 0,
    /** 0 表示未知或直播流。 */
    val durationSec: Long = 0,
    val volume: Int = 0,
    val mute: Boolean = false,
    /** 队列里第几首（从 1 起）；0 表示没有。 */
    val queueIndex: Int = 0,
    val queueCount: Int = 0,
    /**
     * 这一行**已经算好的**状态文案。
     *
     * 为什么不让界面拼：三套体系措辞不同（"本机播放" / "投送中" / "线路输入 播放中"），
     * 拼装规则属于各体系自己的知识。放界面里就又变成 20 处 `if` 了。
     */
    val statusText: String = "",
) {
    /** 有进度条可画吗。 */
    val hasProgress: Boolean get() = durationSec > 0

    val progressFraction: Float
        get() = if (durationSec <= 0) 0f
        else (positionSec.toFloat() / durationSec).coerceIn(0f, 1f)
}

/**
 * 能力位。
 *
 * 全部显式必填（没有默认值）—— 逼每个实现在写的时候回答"我到底能不能做这个"。
 * 给默认值的话，新体系会悄悄继承一份**别人**的能力表，那正是漂移的来源。
 */
data class EndpointCapabilities(
    val canNext: Boolean,
    val canPrevious: Boolean,
    val canSeek: Boolean,
    /** 有"静音"这个独立状态吗（本机没有，只有音量）。 */
    val canMute: Boolean,
    /**
     * **我们有列表给用户看、让他点第 N 首吗？**
     *
     * ⚠️ 这问的不是"设备持有队列吗"。设备是否持有整份列表是
     * `DeviceCapabilities.supportsPlayQueue`（LinkPlay 私有），两者完全无关：
     *
     * | | 含义 | 谁有 |
     * |---|---|---|
     * | `supportsPlayQueue` | 设备持有列表，App 退出照播 | 只有 DOSS |
     * | `hasQueue` | 界面能显示列表、能点选 | **三种后端都有** |
     *
     * 上一版把这两个混为一谈，导致本机和电视盒子的播放列表整块消失。
     */
    val hasQueue: Boolean,
)
