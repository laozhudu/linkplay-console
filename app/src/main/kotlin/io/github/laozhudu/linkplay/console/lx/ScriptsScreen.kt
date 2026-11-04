package io.github.laozhudu.linkplay.console.lx

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.laozhudu.linkplay.console.ui.components.AppCard
import io.github.laozhudu.linkplay.console.ui.components.SectionTitle
import io.github.laozhudu.linkplay.console.ui.components.VSpace
import io.github.laozhudu.linkplay.console.ui.theme.Spacing
import io.github.laozhudu.script.ScriptCandidate
import io.github.laozhudu.script.ScriptEntry

/**
 * 洛雪音源脚本页：装了什么、哪支在用、导入新的。
 *
 * ## 为什么这一页必须做得像个"清单"而不是一个"导入按钮"
 *
 * 脚本是**耗材**：官方的失效了要换第三方、第三方的挂了要换下一家。
 * 用户会反复来这一页，而且要能一眼看出"现在用的是哪一支、它多旧"。
 * 所以列表优先，导入是列表下方的一个动作。
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.lg),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("← 返回") }
            Text(
                "洛雪音源脚本",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { showImport = true }) { Text("导入") }
        }

        AppCard {
            SectionTitle("这一页是干什么的", "洛雪负责搜，脚本负责出直链")
            Text(
                "搜到歌以后要靠脚本来解析真实的播放地址。没有脚本，搜出来的歌点不动。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        VSpace(Spacing.sm)

        if (entries.isEmpty()) {
            AppCard {
                SectionTitle("还没装脚本", "洛雪只能搜，放不了")
                Text(
                    "点右上「导入」：把脚本内容粘进来，或从文件选一份 .js。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            entries.forEach { entry ->
                AppCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                entry.name + if (entry.active) "  · 在用" else "",
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                listOfNotNull(
                                    entry.version,
                                    entry.author?.takeIf { it.isNotBlank() },
                                    if (entry.sizeBytes >= 1024) "${entry.sizeBytes / 1024}KB" else "${entry.sizeBytes}B",
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
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
                }
                VSpace(Spacing.sm)
            }
        }

        if (broken.isNotEmpty()) {
            AppCard {
                SectionTitle("有 ${broken.size} 个文件不像脚本", "它们没有被列在上面")
                Text(
                    broken.joinToString("\n"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            VSpace(Spacing.sm)
        }

        note?.let {
            AppCard { Text(it, style = MaterialTheme.typography.bodyMedium) }
            VSpace(Spacing.sm)
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
