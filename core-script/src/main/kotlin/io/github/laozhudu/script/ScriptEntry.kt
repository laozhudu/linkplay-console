package io.github.laozhudu.script

/**
 * 已经装好的一支脚本。
 *
 * ## 为什么这些字段都从磁盘现算，而不是存在索引文件里
 *
 * 索引文件会带来"两个真相"：有人手动把脚本删了、改了、拷进来一支，
 * 索引还停在旧状态 —— 于是界面显示的和实际有的不一致，而这种不一致**只在下次重启后才暴露**。
 *
 * 现在磁盘上只有两种东西：脚本文件本身、以及一个"当前启用哪支"的标记。
 * 其余（名字/版本/作者/大小/导入时间）**每次列的时候从文件现读现解析**。
 *
 * 代价：每次列脚本要读几份几十 KB 的文本、跑一遍解析。家里几支脚本，可以忽略。
 */
data class ScriptEntry(
    /** 脚本自己声明的名字（`@name`），也是界面上的显示名。 */
    val name: String,
    /** 磁盘上的文件名（安全化过的名字 + `.js`）。 */
    val fileName: String,
    val version: String? = null,
    val author: String? = null,
    val description: String? = null,
    val sizeBytes: Int = 0,
    /** 文件的最后修改时间 —— 当作"导入时间"用，不另存。 */
    val importedAtMs: Long = 0L,
    /** 是不是当前启用的那支。 */
    val active: Boolean = false,
)
