package io.github.laozhudu.linkplay.console.ir

/**
 * 诊断「红外」段 —— 记录**这台手机到底有没有红外发射器**，以及试发的结果。
 *
 * ## 为什么第一件事是验证能力而不是做功能
 *
 * `docs/81`：手机不能学码，码库是唯一路径；而**没有发射器的手机上整个功能不存在**。
 * 所以在写任何码库之前，先回答一个问题：这台手机有没有？
 *
 * 记录放在这里而不是弹 toast：试发是一次性动作，用户点完就过去了，
 * 而"到底有没有发射器"这个结论要能事后查（截图给我）。
 */
object IrStatus {

    private var hasEmitter: Boolean? = null
    private var carrier: List<Int> = emptyList()
    private var lastAttempt: String? = null
    private var lastOk: Boolean? = null
    private var lastAtMs: Long = 0L

    fun probed(hasEmitter: Boolean, carrier: List<Int>) {
        this.hasEmitter = hasEmitter
        this.carrier = carrier
    }

    fun attempted(ok: Boolean, note: String?, nowMs: Long = System.currentTimeMillis()) {
        lastOk = ok
        lastAttempt = note ?: if (ok) "已发出，没有异常" else "失败"
        lastAtMs = nowMs
    }

    fun clear() {
        hasEmitter = null
        carrier = emptyList()
        lastAttempt = null
        lastOk = null
        lastAtMs = 0L
    }

    /** 最近一次试发失败过？（诊断段据此决定级别） */
    fun lastFailed(): Boolean = lastOk == false

    fun render(): String = buildString {
        val emitter = hasEmitter
        when (emitter) {
            null -> appendLine("  发射器=还没检测（打开设置页会自动查一次）")
            true -> {
                appendLine("  发射器=有")
                if (carrier.isNotEmpty()) {
                    appendLine("  载波=${carrier.distinct().joinToString("/")}Hz")
                } else {
                    appendLine("  载波=设备没报（按 38kHz 试）")
                }
            }
            false -> appendLine("  发射器=没有 —— 红外功能在这台手机上不成立（docs/81 已核实手机不能学码）")
        }
        val note = lastAttempt
        if (note != null) {
            val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date(lastAtMs))
            appendLine("  最近试发=$time ${if (lastOk == true) "成功" else "失败"} —— $note")
        } else {
            appendLine("  最近试发=还没试过")
        }
    }.trimEnd()
}
