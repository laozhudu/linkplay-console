package io.github.laozhudu.linkplay.console.crash

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 崩溃现场记录 —— 让"闪退"从不可诊断变成可诊断。
 *
 * ## 为什么必须做（这一版踩的坑）
 *
 * 用户报"空指针闪退"，但手机上的 MIUI 弹窗只给一句模板文案（"因自身空指针问题"），
 * 既没有异常类、没有文件行号、也没有时间。我们手上没有堆栈，只能靠猜 ——
 * 而"猜着改"正是这个项目反复出问题的根源（协议层已经吃过一次亏：
 * 命令回 OK 就当成功）。
 *
 * 所以先把**证据链**建起来：未捕获异常发生时把完整堆栈连同当时的应用状态落到私有目录，
 * 下次启动在首页顶部显示出来，可一键复制。这样下一次闪退是"看到行号改"，
 * 不再是"再猜一版发给你试试"。
 *
 * ## 为什么不用第三方崩溃上报
 *
 * 个人自用、局域网内，不联网上报；写本地文件最直接，也不引入依赖。
 * 文件只有最近一次（[MAX_KEEP] 份滚动），不会无限增长。
 */
object CrashGuard {

    private const val DIR = "crash"
    private const val LATEST = "crash-latest.txt"
    private const val MAX_KEEP = 5

    @Volatile
    private var installed = false

    /**
     * 安装全局未捕获异常处理器。必须在 `setContent` 之前调用，
     * 且只能装一次（重复装会把上一个处理器链断掉）。
     *
     * @param context 任意 Context，内部只取 applicationContext
     * @param stateSnapshot 崩溃瞬间额外要记的状态（设备数、当前页、版本…）。
     *   传函数而不是字符串：只有真崩溃时才求值，平时零开销。
     */
    fun install(context: Context, stateSnapshot: () -> String) {
        if (installed) return
        installed = true
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            // 写盘本身绝不能抛：一旦这里再抛，原始堆栈就丢了
            runCatching { write(app, thread, error, stateSnapshot()) }
            // 交回系统（或上一个处理器），保持"闪退"这一既有行为 ——
            // 我们不吞异常，只记录。吞掉会让 App 处于半死状态，比闪退更难查。
            previous?.uncaughtException(thread, error)
                ?: android.os.Process.killProcess(android.os.Process.myPid())
        }
    }

    /** 上一次崩溃的记录（含堆栈）。没有返回 null。 */
    fun lastReport(context: Context): String? {
        val f = File(dir(context), LATEST)
        return if (f.isFile && f.length() > 0) runCatching { f.readText() }.getOrNull() else null
    }

    /** 用户看过就清掉，避免每次启动都弹。 */
    fun clear(context: Context) {
        runCatching { File(dir(context), LATEST).delete() }
    }

    private fun dir(context: Context): File =
        File(context.filesDir, DIR).apply { if (!exists()) mkdirs() }

    private fun write(context: Context, thread: Thread, error: Throwable, state: String) {
        val dir = dir(context)
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val body = buildString {
            appendLine("时间：$stamp")
            appendLine("线程：${thread.name}")
            appendLine("异常：${error.javaClass.name}")
            appendLine("消息：${error.message}")
            appendLine("--- 应用状态 ---")
            appendLine(state)
            appendLine("--- 堆栈 ---")
            appendLine(error.stackTraceToString())
        }
        File(dir, LATEST).writeText(body)
        // 滚动保留：按时间戳另存一份，超过 MAX_KEEP 删最旧的
        runCatching {
            val archived = File(dir, "crash-${System.currentTimeMillis()}.txt")
            archived.writeText(body)
            dir.listFiles { f -> f.name.startsWith("crash-") && f.name != LATEST }
                ?.sortedByDescending { it.lastModified() }
                ?.drop(MAX_KEEP)
                ?.forEach { it.delete() }
        }
    }
}
