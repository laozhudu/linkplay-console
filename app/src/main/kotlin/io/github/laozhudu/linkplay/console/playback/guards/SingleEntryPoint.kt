package io.github.laozhudu.linkplay.console.playback.guards

/**
 * 标记「只能从唯一入口调用」的内部原语。
 *
 * ## 为什么用编译期而不是文档
 *
 * 「唯一入口」在 15 篇文档里写过 28 次，代码里被破了 4 次，修了 4 个 PR
 * （#130 #131 #132 #139）。写文档约束不住人 —— 但编译不过能约束住。
 *
 * ## 用法
 *
 * 标在原语上；调用方必须显式 `@OptIn(SingleEntryPoint::class)`。
 * 唯一入口自己加一次，其他人想调，编译器报错，逼他先解释「为什么这里可以绕过」。
 */
@RequiresOptIn(
    message = "这是唯一入口的内部原语。请调 PlaybackDispatcher / startQueueOnDevice，不要直连。",
    level = RequiresOptIn.Level.ERROR,
)
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
annotation class SingleEntryPoint

/**
 * 标记「只能由播放页写的输出选择」。
 *
 * 现状：开关长在洛雪页（`LxSearchScreen:90,98`），而 5 个源都受它影响 ——
 * 用户从 NAS 页想听手机上放，得跑去洛雪页切。S1.4 搬到播放页后转 ERROR。
 *
 * 现在只能是 WARNING：搬之前洛雪页还在调，设 ERROR 会立刻编不过。
 */
@RequiresOptIn(
    message = "输出选择只能从播放页改。",
    level = RequiresOptIn.Level.WARNING,
)
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.FUNCTION)
annotation class OutputSwitch
