package io.github.laozhudu.script

/**
 * 脚本仓库 —— 装了什么、现在用哪支、导入一支之后变成什么样。**唯一入口**。
 *
 * ## 磁盘上只有两种东西
 *
 * 1. 脚本文件本身（名字由 `@name` 安全化得到，后缀 `.js`）；
 * 2. 一个 `_active` 标记文件，内容是当前启用那支的文件名。
 *
 * 没有索引文件：名字/版本/作者/大小/时间全部现读现解析。
 * 这样"别人手动往目录里塞一支脚本"也能立刻出现在列表里，不会出现
 * "界面说没有、目录里明明有"这类只能靠重启才暴露的矛盾。
 *
 * ## 同名导入算更新
 *
 * 按 `@name` 认同一支脚本。重复导入（用户下载了新版本）= **更新**，
 * 并回报上一版版本号 —— 用户才知道自己换成了什么。
 *
 * **改了名字的脚本会被当成新的一支并存**，不会被当成"同一支改名"。
 * 这是有意的：脚本名字由脚本自己声明，我们没有可靠办法判断"这两支其实是一支"，
 * 而乱删用户文件的代价远大于列表里多一条（他随时可以自己删）。
 *
 * ## 第一次导入自动启用
 *
 * 用户导完脚本，如果还要再手动点一次"启用"才能用，那在界面上等价于"导了没用"。
 * 所以只在**当前没有启用项**时自动设为启用；已有启用项则保持用户的选择不动。
 */
class ScriptRepository(
    private val storage: ScriptStorage,
    private val validator: ScriptValidator,
    /** 让单测能控制时间；默认系统钟。 */
    private val clock: () -> Long = { System.currentTimeMillis() },
) {

    companion object {
        /** 内部标记文件：当前启用哪支。**以 `_` 开头，列表时跳过。 */
        const val ACTIVE_MARKER = "_active"

        /** 脚本后缀。 */
        const val EXTENSION = ".js"

        private const val INTERNAL_PREFIX = "_"

        /** 脚本名字做文件名时的最长长度（保留余量，避免超出文件系统的单段上限）。 */
        private const val MAX_NAME = 60
    }

    /** 导入结果。失败分「用户给的东西不对」和「我们没存下去」两类。 */
    sealed interface ImportResult {
        data class Added(val entry: ScriptEntry) : ImportResult
        data class Updated(val entry: ScriptEntry, val previousVersion: String?) : ImportResult
        /** 脚本本身不合格（原因见 [ScriptCheck]）。 */
        data class Rejected(val check: ScriptCheck) : ImportResult
        /** 校验过了但没存下去（磁盘满、没权限）。 */
        data class Failed(val reason: String) : ImportResult
    }

    /** 已装好的脚本，按导入先后（先导的在前）。 */
    fun list(): List<ScriptEntry> {
        val activeFile = activeFileName()
        return storage.list()
            .filterNot { it.fileName.startsWith(INTERNAL_PREFIX) }
            .mapNotNull { entryOf(it, activeFile) }
            .sortedBy { it.importedAtMs }
    }

    /**
     * 目录里那些**认不出来**的文件（不是合规脚本）。
     *
     * 为什么要单独给出：如果列表直接把它们藏掉，用户会看到"我明明放了三个脚本，界面只有两个"，
     * 而且没有任何线索。有这一条，诊断能写"另有 1 个文件不是合规脚本：xxx.js"。
     */
    fun unrecognized(): List<String> =
        storage.list()
            .filterNot { it.fileName.startsWith(INTERNAL_PREFIX) }
            .filter { entryOf(it, null) == null }
            .map { it.fileName }

    /** 当前启用的那支；没有则为 `null`。 */
    fun active(): ScriptEntry? {
        val fileName = activeFileName() ?: return null
        val stored = storage.list().firstOrNull { it.fileName == fileName } ?: return null
        return entryOf(stored, fileName)
    }

    /** 指定启用哪支；传 `null` 表示都不启用。 */
    fun setActive(fileName: String?): Boolean = runCatching {
        storage.write(ACTIVE_MARKER, fileName ?: "")
        true
    }.getOrDefault(false)

    /** 取某支脚本的原文（执行层要用）。 */
    fun textOf(fileName: String): String? = runCatching { storage.read(fileName) }.getOrNull()

    /** 删除一支。删的若是当前启用项，启用标记一并清掉。 */
    fun delete(fileName: String): Boolean {
        if (activeFileName() == fileName) storage.write(ACTIVE_MARKER, "")
        return storage.delete(fileName)
    }

    /** 导入一支。见类注释里的三条规则。 */
    fun import(candidate: ScriptCandidate): ImportResult {
        val check = validator.validate(candidate.text)
        if (check !is ScriptCheck.Ok) return ImportResult.Rejected(check)

        val fileName = fileNameOf(check.name)
        // 同一支脚本 = 同一个 @name（文件名由它推导，所以按文件名找等价且更省一次解析）
        val previous = runCatching { list().firstOrNull { it.fileName == fileName } }.getOrNull()

        return runCatching {
            storage.write(fileName, candidate.text)
            if (activeFileName().isNullOrEmpty()) setActive(fileName)

            val stored = storage.list().firstOrNull { it.fileName == fileName }
                ?: return@runCatching ImportResult.Failed("写进去了但读不回来：$fileName")
            val entry = entryOf(stored, activeFileName())
                ?: return@runCatching ImportResult.Failed("写进去了但内容不合规：$fileName")

            if (previous == null) ImportResult.Added(entry)
            else ImportResult.Updated(entry, previous.version)
        }.getOrElse { error ->
            ImportResult.Failed(error.message ?: error.javaClass.simpleName)
        }
    }

    /**
     * 名字 → 文件名。**必须安全化**：`@name` 是脚本自己写的，
     * 里面带 `/` 或 `..` 时会写到目录外面去（这不是假想的攻击 —— 任何能改脚本头的人都能触发）。
     *
     * 逐字符处理而不是用正则：正则要穿透「Kotlin 字符串 → 正则字符串 → 字符类」三层转义，
     * 写错一个反斜杠就变成"什么都不替换"，而这种错在单测里不一定看得出来。
     * 这里用显式字符集，改起来一眼就懂。
     */
    fun fileNameOf(name: String): String {
        val safe = buildString {
            name.trim().forEach { ch ->
                when {
                    // 路径分隔符 + Windows 不允许的字符
                    ch in "\\/:*?\"<>|" -> append('_')
                    // 空白与控制字符
                    ch.isWhitespace() || ch.isISOControl() -> append('_')
                    // 其余（含中文）保留：文件系统支持，用户也认得
                    else -> append(ch)
                }
            }
        }
        // 首尾的点和下划线去掉：`..` 与隐藏文件的形态都不该出现
        return safe.trim('_', '.').take(MAX_NAME).ifEmpty { "script" } + EXTENSION
    }

    private fun activeFileName(): String? =
        runCatching { storage.read(ACTIVE_MARKER)?.trim() }.getOrNull()?.takeIf { it.isNotEmpty() }

    private fun entryOf(stored: StoredScript, activeFileName: String?): ScriptEntry? {
        val check = validator.validate(stored.text)
        if (check !is ScriptCheck.Ok) return null
        return ScriptEntry(
            name = check.name,
            fileName = stored.fileName,
            version = check.version,
            author = check.author,
            description = check.description,
            sizeBytes = stored.text.toByteArray(Charsets.UTF_8).size,
            importedAtMs = stored.lastModifiedMs,
            active = stored.fileName == activeFileName,
        )
    }
}
