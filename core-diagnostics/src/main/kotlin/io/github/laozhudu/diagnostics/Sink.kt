package io.github.laozhudu.diagnostics

/**
 * 诊断记录的出口。
 *
 * ## 为什么是接口而不是直接写 [EntryLog]
 *
 * 接入方可能想同时写：内存环形（给界面看）、文件（下次启动看）、logcat（开发时看）。
 * 也可能在测试里只要一个"什么都不做"的实现。
 * 一个接口 + [tee] 就够了，不需要引入日志框架。
 *
 * 实现方**必须吞掉自己的异常** —— 它站在异常处理路径上，
 * 自己抛一次就把原始错误盖掉了。
 */
fun interface DiagnosticSink {
    fun record(entry: DiagnosticEntry)

    companion object {
        /** 丢弃一切。测试和"未配置"时用。 */
        val NONE: DiagnosticSink = DiagnosticSink { }

        /** 扇出到多个出口；任一出口抛异常不影响其他出口。 */
        fun tee(vararg sinks: DiagnosticSink): DiagnosticSink = DiagnosticSink { entry ->
            sinks.forEach { sink -> runCatching { sink.record(entry) } }
        }
    }
}
