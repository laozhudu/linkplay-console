package io.github.laozhudu.linkplay.console.diagnostics

import java.util.concurrent.ConcurrentHashMap

/**
 * 全 App 诊断注册表 —— 让诊断页覆盖"整个应用"，而不只是设备列表。
 *
 * ## 为什么需要它
 *
 * 第一版诊断页只读 `ConsoleUiState`：设备、电量、声道、崩溃。
 * 但用户真正会报的问题分布在各处：播放起不来、队列串台、电台加了不响、
 * 在线曲库搜不到、悬浮窗不显示…… 这些状态都在各自的 ViewModel 里，
 * 诊断页拿不到 —— 于是又回到"你描述现象，我猜"。
 *
 * 这里的做法：**谁有状态，谁自己渲染一段**。模块启动时 `register` 一个
 * 「把我的状态写成几行文本」的 provider，诊断页只负责按顺序拼起来。
 * 这样新增模块不需要改诊断代码（开闭原则），也不会出现"某个模块的状态忘了加"。
 *
 * ## 为什么必须能注销
 *
 * `PlaybackViewModel` 切设备会重建，`onCleared` 之后旧实例还挂在注册表里就会：
 * 1. 泄漏整个 ViewModel（它持有 Context）；
 * 2. 诊断页显示**上一台设备**的播放状态，比没有更误导。
 * 所以 ViewModel 在 `onCleared` 里必须 `unregister`。同 id 重复注册按覆盖处理。
 *
 * ## 线程
 *
 * 注册/注销/渲染都可能来自不同线程（主线程 UI、IO 协程），用 `ConcurrentHashMap`。
 * 渲染顺序按注册先后 —— 让"设备 → 播放 → 队列"这种稳定顺序出现，
 * 用户截图时位置不会每次都变。
 */
object DiagnosticsHub {

    /** 一段诊断内容。`title` 进方括号，`render` 返回多行文本（自己负责缩进）。 */
    data class Section(
        val id: String,
        val title: String,
        val render: () -> String,
    )

    // LinkedHashMap 语义 + 并发安全：ConcurrentHashMap 不保证顺序，
    // 所以额外用一个自增序号做排序键
    private val sections = ConcurrentHashMap<String, Section>()
    private val order = ConcurrentHashMap<String, Int>()
    private val counter = java.util.concurrent.atomic.AtomicInteger(0)

    /** 注册（或替换）一段诊断内容。 */
    fun register(id: String, title: String, render: () -> String) {
        sections[id] = Section(id, title, render)
        order.putIfAbsent(id, counter.incrementAndGet())
    }

    fun unregister(id: String) {
        sections.remove(id)
        order.remove(id)
    }

    /** 当前已注册的段（按注册顺序）。测试与界面都用它。 */
    fun sections(): List<Section> =
        sections.values.sortedBy { order[it.id] ?: Int.MAX_VALUE }

    /**
     * 渲染全部段。
     *
     * **单段失败不影响其他段**：某个 provider 抛异常时只把那一行换成错误说明。
     * 理由很直接 —— 诊断工具自己崩掉，比被诊断的功能崩掉更难查。
     */
    fun render(): String = buildString {
        sections().forEach { s ->
            appendLine("[${s.title}]")
            val body = runCatching { s.render() }.getOrElse { e ->
                "  <渲染失败：${e.javaClass.simpleName}: ${e.message}>"
            }
            if (body.isBlank()) appendLine("  无") else body.lineSequence().forEach { appendLine(it) }
            appendLine()
        }
    }

    /** 测试用：清空注册表。 */
    fun clear() {
        sections.clear()
        order.clear()
        counter.set(0)
    }
}
