package io.github.laozhudu.script

/** 磁盘上的一份脚本原文。 */
data class StoredScript(
    val fileName: String,
    val text: String,
    val lastModifiedMs: Long,
)

/**
 * 脚本的存取口子。**只用 `java.io` 的语义，不出现 Android 类型**。
 *
 * 这样三件事都成立：
 * 1. 单测用临时目录就能全跑（不需要真机）；
 * 2. 换宿主只要换目录（Android 的 `filesDir`、桌面的任意目录、内存里的假实现）；
 * 3. 这个模块能保持纯 JVM 零依赖，可以被别的项目直接依赖。
 *
 * 写失败**抛异常**（`IOException`），由 [ScriptRepository] 统一转成给用户看的结果 ——
 * 这一层不决定"怎么跟用户说"。
 */
interface ScriptStorage {

    /** 目录里现存的全部文件（含内部标记文件，由仓库负责过滤）。 */
    fun list(): List<StoredScript>

    /** 读一份；不存在返回 `null`。 */
    fun read(fileName: String): String?

    /** 写一份（覆盖同名）。失败抛异常。 */
    fun write(fileName: String, text: String)

    /** 删一份；返回是否真的删掉了。 */
    fun delete(fileName: String): Boolean
}
