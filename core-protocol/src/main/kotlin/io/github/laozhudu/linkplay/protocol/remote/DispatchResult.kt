package io.github.laozhudu.linkplay.protocol.remote

/**
 * 一次下发的结果。
 *
 * ## 为什么不能是 `Boolean`
 *
 * 项目反复吃过「命令回 OK ≠ 功能生效」的亏：
 * - `chext=0` vs `chext=1` 差了两个字，命令都回 OK，一个组网一个不组
 * - `resume` 回 OK 但 `status` 仍是 stop
 * - https 电台回 OK 但没播（`https://lqingting…` 实测 pause）
 *
 * 如果返回 `Boolean`，这些完全不同的情况会被压成一个 `false`，界面只能说"失败了" ——
 * 而实际上"设备收到了但没照做"和"根本没连上"的排查方向完全相反。
 *
 * ## 为什么现在只有两个分支
 *
 * 蓝牙/红外还没做，而它们会引入第三种结果：**单向发出，无从得知**（红外没有回执）。
 * 届时加 `data object Sent` 即可 —— 所有 `when` 会编译报错，提醒补分支。
 *
 * 现在就把 `Sent` 定义出来是过度设计：没有任何代码能产生它，
 * 单测只能测"它存在"，测不了它的语义。**留门 ≠ 现在建好房间。**
 */
sealed interface DispatchResult {

    /** 对端确认收到了（HTTP 200 且不是错误文本）。**仍不等于功能生效**，见类注释。 */
    data class Acked(val raw: String) : DispatchResult

    /** 没送到，或对端明确拒绝。 */
    data class Failed(val failure: DispatchFailure) : DispatchResult

    /** 便捷判断：只关心"送没送到"时用。 */
    val isAcked: Boolean get() = this is Acked
}

/**
 * 失败原因 —— 分得越细，界面越能说人话。
 *
 * 为什么要四类而不是一句 `error.message`：界面要据此决定**显示什么、是否还提供入口**。
 * 例如 [Unsupported] 应该把按钮整个隐藏，而不是让用户再点一次看同样的错。
 */
sealed interface DispatchFailure {

    /** 连不上（超时、拒绝连接、DNS 失败）。设备可能只是睡了。 */
    data object Unreachable : DispatchFailure

    /** 这台设备不支持该指令（例如给通用 DLNA 渲染器发 `Preset`）。 */
    data object Unsupported : DispatchFailure

    /** 对端明确拒绝，带错误码。LinkPlay 的 704 = `play not allowed`（设备没媒体）。 */
    data class Rejected(val code: Int, val text: String) : DispatchFailure

    /** 回包看不懂（不是 OK 也不是错误码）。协议漂移或中间设备改写。 */
    data class Unparsable(val raw: String) : DispatchFailure
}
