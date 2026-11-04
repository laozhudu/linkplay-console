package io.github.laozhudu.linkplay.console.lx

import android.content.Context
import io.github.laozhudu.linkplay.lx.LxScriptValidator
import io.github.laozhudu.script.FileScriptStorage
import io.github.laozhudu.script.ScriptCheck
import io.github.laozhudu.script.ScriptEntry
import io.github.laozhudu.script.ScriptRepository
import java.io.File

/**
 * 脚本仓库在本 App 的**组装根** —— 把 `core-script` 的零件接上 Android 的落盘目录。
 *
 * 为什么需要这一层（和 `AppDiagnostics` 同一个理由）：仓库本身是通用的（纯 JVM、
 * 目录由调用方给），"存到哪"是各 App 自己的事。所以这里只做两件事：
 * 选目录（`filesDir/scripts`）、选校验器（洛雪格式）。
 *
 * 为什么是进程级单例：仓库本身无状态（每次列都从磁盘读），
 * 重复 new 也不会错，但会让"这次导入"和"那次导入"落在不同实例上，
 * 诊断里看到的就不是同一份。**一处真相**比"每次 new 更干净"更重要。
 */
object ScriptLibrary {

    /** 落盘子目录。改它等于让用户已装的脚本消失，别动。 */
    const val DIR = "scripts"

    private var cached: ScriptRepository? = null

    fun repository(context: Context): ScriptRepository = cached ?: run {
        val dir = File(context.applicationContext.filesDir, DIR)
        ScriptRepository(FileScriptStorage(dir), LxScriptValidator()).also { cached = it }
    }

    /** 单测/排障用：丢掉缓存（下次重新指向目录）。 */
    fun reset() {
        cached = null
    }

    fun entries(context: Context): List<ScriptEntry> =
        runCatching { repository(context).list() }.getOrDefault(emptyList())

    fun unrecognized(context: Context): List<String> =
        runCatching { repository(context).unrecognized() }.getOrDefault(emptyList())

    fun activeName(context: Context): String? =
        runCatching { repository(context).active()?.name }.getOrNull()

    /**
     * 导入结果 → 用户看得懂的一句话。
     *
     * 四种拒绝原因必须分开说：合并成"导入失败"，用户只会反复试同一份文件。
     */
    fun explain(result: ScriptRepository.ImportResult): String = when (result) {
        is ScriptRepository.ImportResult.Added ->
            "已导入「${result.entry.name}」${result.entry.version?.let { " $it" }.orEmpty()}，已设为启用"

        is ScriptRepository.ImportResult.Updated ->
            "已更新「${result.entry.name}」→ ${result.entry.version ?: "新版本"}" +
                "（原 ${result.previousVersion ?: "未知"}）"

        is ScriptRepository.ImportResult.Rejected -> when (val check = result.check) {
            ScriptCheck.Empty -> "还没粘东西"
            ScriptCheck.NoHeader -> "这不像是洛雪脚本：开头要有一段 /*! … */ 注释"
            ScriptCheck.NoName -> "脚本缺 @name —— 洛雪靠这个名字显示它"
            is ScriptCheck.Rejected -> check.reason
            is ScriptCheck.Ok -> "已导入"
        }

        is ScriptRepository.ImportResult.Failed -> "存不下来：${result.reason}"
    }
}
