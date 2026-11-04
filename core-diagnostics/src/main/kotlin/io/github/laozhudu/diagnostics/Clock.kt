package io.github.laozhudu.diagnostics

/**
 * 时间来源 —— 注入而不是直接 `System.currentTimeMillis()`。
 *
 * 理由很实际：报告里带时间戳，而**测试必须能固定时间**，否则断言要么写不进去，
 * 要么变成"包含任意数字就行"的假断言。项目里 `SleepTimer` 也用过同样的手法。
 */
fun interface Clock {
    fun nowMs(): Long

    companion object {
        val SYSTEM: Clock = Clock { System.currentTimeMillis() }

        /** 固定时钟（测试用）。 */
        fun fixed(ms: Long): Clock = Clock { ms }

        /** 每次调用前进 [stepMs] 的时钟（测试"两条记录时间不同"用）。 */
        fun ticking(startMs: Long, stepMs: Long = 1000): Clock {
            var next = startMs - stepMs
            return Clock { next += stepMs; next }
        }
    }
}
