package io.github.laozhudu.linkplay.console.content

/**
 * 一切「内容源」的统一接口。
 *
 * ## 为什么现在就要有它
 *
 * 现在六个源各有：一个 ViewModel、一个 Screen、一段诊断。
 * 加起来 **65 个动作、6 个页面**（`docs/94`）—— 而用户视角只有一件事：**找 → 选 → 放**。
 *
 * 定稿 UI 要把它们收进「内容」一屏。**如果不先建这个接口，那一屏会变成 6 套 if。**

## 刻意不放进接口的两件事
 *
 * **① 目录浏览（`browse`）不进。**
 * NAS 是唯一需要"逐层下钻文件夹"的源。把它塞进接口，等于逼本地/电台/洛雪
 * 都实现一个用不上的方法 —— 那正是 `ToQueueItemsUseCase` 里 `SourceTrack` 那个坑
 * （造了个不存在的类型，最后只能用泛型绕开）。NAS 的目录树留在它自己的页面。
 *
 * **② 不提供 `play()`。**
 * 播放要走唯一入口（`PlaybackRouter` / `QueueStarter`），源只负责"给出地址"。
 * 让源自己播，多房间和输出口选择会全部失效（`docs/74` 的硬规则）。
 */
interface ContentSource {

    val id: SourceId

    /** 现在能不能用。不能时给一句人话（界面据此把 chip 置灰而不是点了没反应）。 */
    fun readiness(): Readiness

    /**
     * 搜索。
     *
     * 不支持搜索的源返回空列表，并在 [diagnoseLine] 里说清楚为什么 ——
     * **不要抛异常**：一个源不支持搜索不该让整屏报错。
     */
    suspend fun search(query: String, limit: Int = 30): List<ContentItem>

    /** 把一条内容变成可投的地址。 */
    suspend fun resolve(item: ContentItem): Resolved

    /** 一行诊断（接进 `AppDiagnostics` 时不用按源 switch）。 */
    fun diagnoseLine(): String
}
