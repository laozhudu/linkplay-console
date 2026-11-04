package io.github.laozhudu.diagnostics

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * 状态段注册表 —— 让诊断覆盖"整个应用"，而不只是某一个模块。
 *
 * ## 为什么需要它
 *
 * 第一版诊断页只读一个状态对象：设备、电量、声道、崩溃。
 * 但用户真正会报的问题分布在各处：播放起不来、队列串台、电台加了不响、
 * 在线曲库搜不到、悬浮窗不显示…… 这些状态都在各自的模块里，诊断页拿不到 ——
 * 于是又回到"你描述现象，我猜"。
 *
 * 这里的做法：**谁有状态，谁自己渲染一段**。模块启动时注册一个
 * 「把我的状态写成几行文本」的 provider，报告只负责按顺序拼起来。
 * 新增模块不需要改诊断代码（开闭原则），也不会出现"某个模块的状态忘了加"。
 *
 * ## 为什么是实例而不是 object
 *
 * 第一版是 `object DiagnosticsHub`。两个后果：
 * 1. **测试互相污染**：一个用例注册的段出现在另一个用例的报告里，只能靠 `clear()` 打补丁；
 * 2. **不可复用**：一个进程里想同时诊断两套东西（比如主 App + 一个 SDK）做不到。
 *
 * 现在它是普通类，接入方自己持有。
 *
 * ## 为什么必须能注销
 *
 * 播放模块切设备会重建实例。`onCleared` 之后旧实例还挂在注册表里会：
 * 1. 泄漏整个实例（它可能持有 Context）；
 * 2. 诊断页显示**上一台设备**的播放状态 —— 比没有更误导。
 * 所以注销是硬要求，不是可选项。同 id 重复注册按覆盖处理。
 *
 * ## 线程
 *
 * 注册/注销/渲染可能来自不同线程（主线程 UI、IO 协程），用 `ConcurrentHashMap`。
 * 渲染顺序按**首次**注册先后 —— 让"设备 → 播放 → 队列"这种稳定顺序出现，
 * 用户截图时位置不会每次都变。
 */
class SectionRegistry(
    /** 段渲染失败时记到哪里（默认丢弃）。传进来才能看见"诊断自己崩了"。 */
    private val sink: DiagnosticSink = DiagnosticSink.NONE,
) {

    private val sections = ConcurrentHashMap<String, Section>()
    private val order = ConcurrentHashMap<String, Int>()
    private val counter = AtomicInteger(0)

    /** 注册（或替换）一段。重复注册**不改变**它的显示位置。 */
    fun register(
        id: String,
        title: String,
        severity: () -> Severity = { Severity.INFO },
        render: () -> String,
    ) {
        sections[id] = Section(id, title, severity, render)
        order.putIfAbsent(id, counter.incrementAndGet())
    }

    /** 固定级别版本（级别不随状态变化的段用这个，读起来更短）。 */
    fun register(id: String, title: String, severity: Severity, render: () -> String) =
        register(id, title, { severity }, render)

    fun unregister(id: String) {
        sections.remove(id)
        order.remove(id)
    }

    /** 当前已注册的段（按注册顺序）。界面与测试都用它。 */
    fun sections(): List<Section> =
        sections.values.sortedBy { order[it.id] ?: Int.MAX_VALUE }

    /** 整份注册表最严重的那一级（报告头用）。 */
    fun worst(): Severity = Severity.worst(sections.values.map { runCatching { it.severity() }.getOrDefault(Severity.INFO) })

    /**
     * 渲染全部段。
     *
     * **单段失败不影响其他段**：某个 provider 抛异常时只把那一行换成错误说明，
     * 并记一条到 [sink]。理由很直接 —— 诊断工具自己崩掉，比被诊断的功能崩掉更难查。
     */
    fun render(): String = buildString {
        sections().forEach { s ->
            appendLine("[${s.title}]")
            val body = try {
                s.render()
            } catch (error: Throwable) {
                sink.record(
                    DiagnosticEntry(
                        atMs = 0,
                        source = "诊断/${s.id}",
                        severity = Severity.ERROR,
                        message = "渲染失败：${error.javaClass.simpleName}: ${error.message}",
                    ),
                )
                "<渲染失败：${error.javaClass.simpleName}: ${error.message}>"
            }
            if (body.isBlank()) appendLine("  无") else body.lineSequence().forEach { appendLine(it) }
            appendLine()
        }
    }

    /** 测试与"彻底重置"用。 */
    fun clear() {
        sections.clear()
        order.clear()
        counter.set(0)
    }
}
