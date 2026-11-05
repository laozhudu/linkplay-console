package io.github.laozhudu.linkplay.console.backup

/**
 * 诊断「备份」段 —— 最近一次导出/导入的结果。
 *
 * 为什么要记：备份是**低频且不可验证**的动作 —— 用户导完不会立刻发现少了什么，
 * 等发现时已经过了几天。有这一段，至少能回答"上次备份是什么时候、成功没有、几个文件"。
 */
object BackupStatus {

    private data class Last(val atMs: Long, val action: String, val ok: Boolean, val text: String)

    private var last: Last? = null

    fun recorded(action: String, ok: Boolean, text: String, nowMs: Long = System.currentTimeMillis()) {
        last = Last(nowMs, action, ok, text)
    }

    fun clear() {
        last = null
    }

    fun lastFailed(): Boolean = last?.ok == false

    fun render(): String = buildString {
        val item = last
        if (item == null) {
            appendLine("  最近备份=还没做过")
            appendLine("  不备份的目录=${ConfigBackup.excludedNames().joinToString("/")}（诊断与崩溃记录属于上一次的现场）")
            return@buildString
        }
        val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date(item.atMs))
        appendLine("  最近${item.action}=$time ${if (item.ok) "成功" else "失败"} —— ${item.text}")
        appendLine("  不备份的目录=${ConfigBackup.excludedNames().joinToString("/")}")
    }.trimEnd()
}
