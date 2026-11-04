package io.github.laozhudu.linkplay.console.diagnostics

import io.github.laozhudu.diagnostics.ActionSample
import io.github.laozhudu.diagnostics.TimingLedger
import io.github.laozhudu.linkplay.protocol.remote.CommandTrace

/**
 * 动作耗时统计的**唯一入口**。
 *
 * ## 为什么要有这一层，而不是各处自己掐表
 *
 * 之前各处的写法是 `val t0 = System.currentTimeMillis()` + `log.timed(...)`，散落在
 * 三四个文件里，结果有三个问题（都是本项目的真事）：
 * 1. **名字不统一**：同一个"刷新设备"在不同文件里叫 `refresh` / `probe` / `心跳一次`；
 * 2. **有的动作忘了记**：加功能时记得记失败的，忘了记成功的 → 又变成"没反应"和"没触发"分不开；
 * 3. **只记耗时，不记代价**：240ms 到底是一条指令慢还是三条指令各 80ms，看不出来。
 *
 * 现在所有用户动作都走 [measure] / [begin]：耗时、指令条数、指令名、成败**一起**记下来。
 *
 * ## 为什么动作名是枚举而不是字符串常量
 *
 * 字符串常量挡不住 `begin("setVolum")` 这种手误 —— 编译通过，报告里却多出一个永远只出现一次的动作。
 * 枚举把这件事挪到**编译期**：名字只有那几个，写错就是编译不过。这与 `docs/52` 的
 * 「一致性靠单一入口 + 编译期强制」是同一条线。
 *
 * ## 指令条数怎么来的（关键）
 *
 * 口令层已经收口到 `CommandTrace.DEFAULT` 一处，每条记录都带 `atMs`。
 * 所以动作结束后按**时间窗**把这段时间里发出的指令捞出来 —— 不需要各处手动 `+= 1`
 * （手动计数必漏，`docs/59` 的字节统计已经栽过一次）。
 *
 * 局限（写出来比藏着好）：如果两个动作**真并发**跑在同一段时间窗里，指令数会互相算进去。
 * 用户操作基本是串行的，所以现在接受这个局限；真并发分析要看指令流水本身的时间戳。
 *
 * ## 为什么不记后台心跳
 *
 * 心跳 3 秒一次会把平均值拉平，"跟不跟手"就没意义了。账本只收**用户主动触发**的动作，
 * 心跳次数各自诊断段有记。
 */
object ActionTiming {

    /**
     * 用户能做出来的动作。**加一个就在这里加一行**，然后调用点必须传它。
     *
     * `id` 是报告里显示的名字：驼峰、小写开头、表示"用户做了什么"（不是类名）。
     */
    enum class Action(val id: String) {
        REFRESH_DEVICES("refreshDevices"),
        SET_VOLUME("setVolume"),
        PAUSE("pause"),
        STOP_PLAYBACK("stopPlayback"),
        NEXT_TRACK("nextTrack"),
        PREV_TRACK("prevTrack"),
        PLAY_ITEM("playItem"),
        SEEK("seek"),
        MUTE_TOGGLE("muteToggle"),
        CAST_STREAM("castStream"),
        RADIO_PLAY("radioPlay"),
        RADIO_ADD("radioAdd"),
        RADIO_DISCOVER("radioDiscover"),
        // LX_SEARCH / LX_IMPORT_SCRIPT 等到在线曲库与导入脚本界面落地时再加。
        // 现在加进来是**假承诺** —— CI 的 check-timing-names.py 会因为"声明了但没接上"直接报错，
        // 这个报错是对的，别用注释绕过去。
    }

    val ledger = TimingLedger()

    val names: List<String> = Action.entries.map { it.id }

    /**
     * 一次动作的计时器。给**异步动作**用。
     *
     * 为什么不能只有 [measure]：本项目大量动作是"发起后异步完成"（探活、投流、起播轮询）。
     * `measure { launch { ... } }` 会在 launch 返回的瞬间就结束，测出来的 0ms 是假的 ——
     * 这是最坏的一种错：报告说"跟手"，用户体感是卡。
     *
     * 所以异步动作用 [begin] + `end()`，把计时的两头分别放在协程内。
     */
    class Marker internal constructor(private val name: String, private val startedAt: Long) {
        private val done = java.util.concurrent.atomic.AtomicBoolean(false)

        /** 结束计时。**重复调用只记第一条**，避免异常路径和正常路径各记一次。 */
        fun end(note: String? = null, ok: Boolean = true) {
            if (!done.compareAndSet(false, true)) return
            record(name = name, startedAt = startedAt, endedAt = System.currentTimeMillis(), ok = ok, note = note)
        }
    }

    fun begin(action: Action): Marker = Marker(action.id, System.currentTimeMillis())

    /**
     * 包住一次用户动作，**成功失败都记**。
     *
     * 为什么在 `finally` 里记：动作抛异常时也得留下"点了、跑了 87ms、发了 1 条指令、失败"，
     * 否则报告里只剩一条异常，看不出用户点的是哪个功能。
     */
    suspend fun <T> measure(action: Action, note: String? = null, block: suspend () -> T): T {
        val marker = begin(action)
        var ok = true
        try {
            return block()
        } catch (error: Throwable) {
            ok = false
            throw error
        } finally {
            marker.end(note = note, ok = ok)
        }
    }

    private fun record(name: String, startedAt: Long, endedAt: Long, ok: Boolean, note: String?) {
        runCatching {
            val issued = CommandTrace.DEFAULT.recent(CommandTrace.DEFAULT.capacity)
                .filter { it.atMs in startedAt..endedAt }
            val labels = issued.map { it.command.substringBefore('(') }.distinct().take(4)
            ledger.record(
                ActionSample(
                    name = name,
                    atMs = startedAt,
                    durationMs = (endedAt - startedAt).coerceAtLeast(0),
                    commands = issued.size,
                    commandLabels = labels,
                    ok = ok,
                    note = note,
                ),
            )
        }
    }

    /** 诊断段用：把"这次应该发几条指令"的期望值算出来（在线设备数等）。 */
    fun render(expectations: Map<String, Int> = emptyMap()): String = ledger.render(expectations = expectations)

    fun clear() = ledger.clear()
}
