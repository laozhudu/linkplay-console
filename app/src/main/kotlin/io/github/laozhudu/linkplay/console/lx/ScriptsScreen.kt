package io.github.laozhudu.linkplay.console.lx

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import io.github.laozhudu.linkplay.console.ui.components.VSpace
import io.github.laozhudu.linkplay.console.ui.skeleton.AppListRow
import io.github.laozhudu.linkplay.console.ui.skeleton.AppPage
import io.github.laozhudu.linkplay.console.ui.skeleton.AppStateCard
import io.github.laozhudu.linkplay.console.ui.skeleton.RowBadge
import io.github.laozhudu.linkplay.console.ui.skeleton.StateKind
import io.github.laozhudu.linkplay.console.ui.theme.Spacing
import io.github.laozhudu.script.ScriptCandidate
import io.github.laozhudu.script.ScriptEntry
import io.github.laozhudu.script.ScriptRepository

/**
 * 洛雪音源脚本：装了什么、哪支在用、导入新的。
 *
 * 骨架与洛雪音乐页一致（[AppPage]）。这里是"卡片堆叠"型页面，所以整页滚动。
 */
@Composable
internal fun ScriptsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { ScriptLibrary.repository(context) }

    var entries by remember { mutableStateOf<List<ScriptEntry>>(emptyList()) }
    var broken by remember { mutableStateOf<List<String>>(emptyList()) }
    var note by remember { mutableStateOf<String?>(null) }
    var showImport by remember { mutableStateOf(false) }

    // 每次列都从磁盘读（仓库不缓存）：从文件管理器塞进来的脚本也能立刻看到
    fun reload() {
        entries = runCatching { repo.list() }.getOrDefault(emptyList())
        broken = runCatching { repo.unrecognized() }.getOrDefault(emptyList())
    }

    LaunchedEffect(Unit) { reload() }

    AppPage(
        title = "洛雪音源脚本",
        subtitle = if (entries.isEmpty()) "洛雪只负责搜，脚本负责出直链" else "在用 ${entries.firstOrNull { it.active }?.name ?: "没有启用的"}",
        onBack = onBack,
        trailing = { TextButton(onClick = { showImport = true }) { Text("导入") } },
        scrollWholePage = true,
    ) {
        if (entries.isEmpty()) {
            AppStateCard(
                kind = StateKind.Empty,
                title = "还没装脚本",
                hint = "洛雪只能搜，放不了",
                detail = "点右上「导入」：把脚本内容粘进来，或从文件选、贴链接下载。",
            )
        } else {
            entries.forEach { entry ->
                AppListRow(
                    title = entry.name + if (entry.active) "  · 在用" else "",
                    subtitle = listOfNotNull(
                        entry.version,
                        entry.author?.takeIf { it.isNotBlank() },
                        if (entry.sizeBytes >= 1024) "${entry.sizeBytes / 1024}KB" else "${entry.sizeBytes}B",
                    ).joinToString(" · "),
                    badge = if (entry.active) RowBadge.Playing else RowBadge.None,
                    trailing = {
                        Column {
                            if (!entry.active) {
                                TextButton(onClick = {
                                    repo.setActive(entry.fileName)
                                    reload()
                                    note = "已切换到「${entry.name}」"
                                }) { Text("启用") }
                            }
                            TextButton(onClick = {
                                repo.delete(entry.fileName)
                                reload()
                                note = "已删除「${entry.name}」"
                            }) { Text("删除") }
                        }
                    },
                )
                VSpace(Spacing.xs)
            }
        }

        if (broken.isNotEmpty()) {
            AppStateCard(
                kind = StateKind.Hint,
                title = "有 ${broken.size} 个文件不像脚本",
                hint = "它们没有被列在上面",
                detail = broken.joinToString("\n"),
            )
        }

        note?.let {
            AppStateCard(StateKind.Hint, it)
        }
    }

    if (showImport) {
        ImportScriptDialog(
            onDismiss = { showImport = false },
            onImport = { text, origin ->
                val result = repo.import(ScriptCandidate(text = text, origin = origin))
                ScriptSourceStatus.record(result)
                note = ScriptLibrary.explain(result)
                reload()
                showImport = false
            },
        )
    }
}
