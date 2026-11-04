package io.github.laozhudu.linkplay.console.lx

import io.github.laozhudu.script.ScriptEntry
import io.github.laozhudu.script.ScriptRepository

/**
 * 诊断段「脚本」—— 装了几支、哪支在用、最近一次导入成没成。
 *
 * ## 为什么这一条不能省
 *
 * 洛雪只负责"搜"，**取直链全靠用户脚本**。所以"没有脚本 / 脚本坏了"是
 * "搜到歌却播不出来"的**第一嫌疑**。没有这一段，用户报"点了没反应"，
 * 我只能让他去翻设置页数有几支脚本。
 *
 * ## 为什么分成三段写
 *
 * 1. **库存**：装了几支、哪支启用、有没有认不出来的文件；
 * 2. **最近一次导入**：成没成、几点了、失败说的是哪种；
 * 3. **结论**：按库存与最近一次结果给一句人话。
 *
 * 混成一行"脚本：正常"，出问题时看不出是哪一环。
 */
object ScriptSourceStatus {

    /** 最近一次导入的记录（只在内存里 —— 它描述的是"刚才那一下"，不是持久事实）。 */
    private data class LastImport(val atMs: Long, val ok: Boolean, val text: String)

    private var last: LastImport? = null

    fun record(result: ScriptRepository.ImportResult, nowMs: Long = System.currentTimeMillis()) {
        val ok = result is ScriptRepository.ImportResult.Added || result is ScriptRepository.ImportResult.Updated
        last = LastImport(nowMs, ok, ScriptLibrary.explain(result))
    }

    /** 清空（用户点「清空记录」时一起清）。 */
    fun clear() {
        last = null
    }

    /** 最近一次导入失败过？（诊断段用它决定级别） */
    fun lastFailed(): Boolean = last?.ok == false

    /**
     * 渲染。参数全部传进来，**不在这里读磁盘** —— 这样这段能被单测锁住，
     * 不会因为某个用例跑在没有 filesDir 的环境里就崩掉。
     */
    fun render(
        entries: List<ScriptEntry>,
        unrecognized: List<String>,
        activeName: String?,
        last: LastImportView? = null,
    ): String {
        val view = last ?: this.last?.let { LastImportView(it.atMs, it.ok, it.text) }
        return buildString {
            if (entries.isEmpty()) {
                appendLine("库存=空  洛雪只能搜不能取直链，没脚本 = 搜到也放不了")
            } else {
                val active = activeName ?: entries.firstOrNull { it.active }?.name
                appendLine("库存=${entries.size} 支  启用=${active ?: "无（导一支就会自动启用）"}")
                entries.forEach { e ->
                    val mark = if (e.active) "✓" else " "
                    val size = if (e.sizeBytes >= 1024) "${e.sizeBytes / 1024}KB" else "${e.sizeBytes}B"
                    appendLine("  $mark ${e.name} ${e.version ?: "-"} · ${e.author ?: "未知作者"} · $size")
                }
            }
            if (unrecognized.isNotEmpty()) {
                appendLine("  另有 ${unrecognized.size} 个文件不是合规脚本：${unrecognized.joinToString("/")}")
            }
            view?.let {
                val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date(it.atMs))
                appendLine("  最近导入=$time ${if (it.ok) "成功" else "失败"} —— ${it.text}")
            } ?: appendLine("  最近导入=还没导入过")
        }.trimEnd()
    }

    /** 渲染用的视图（把私有类型挡在外面）。 */
    data class LastImportView(val atMs: Long, val ok: Boolean, val text: String)
}
