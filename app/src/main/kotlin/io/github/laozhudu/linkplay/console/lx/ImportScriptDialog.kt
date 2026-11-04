package io.github.laozhudu.linkplay.console.lx

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.laozhudu.linkplay.console.ui.components.VSpace
import io.github.laozhudu.linkplay.console.ui.theme.Spacing
import io.github.laozhudu.script.ScriptOrigin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 导入脚本的对话框 —— **两个页面共用**（脚本管理页、洛雪搜索页的"没脚本"引导）。
 *
 * 抽出来的理由很实在：用户点播放时才发现"没脚本"，这时候弹的框必须和脚本页里
 * 那个长得完全一样、行为完全一样。各写一份必然漂移（一个能选文件、一个不能）。
 *
 * @param errorText 导入被拒时的原因（原样显示，不翻译成"失败"）
 */
/** 脚本大小上限。洛雪脚本通常几十 KB，超过这个数基本是下错东西了（比如整个仓库 zip）。 */
private const val MAX_SCRIPT_BYTES = 512 * 1024

/**
 * 按链接把脚本下下来。
 *
 * 三个必须处理的坑（都写在这里，不让 UI 去猜）：
 * 1. **不是 2xx** → 直接把状态码给用户（"服务器返回 404"比"下载失败"有用）；
 * 2. **太大** → 拒绝并说明上限（防止下载整个压缩包把内存打满）；
 * 3. **连不上/超时** → 给原文，不翻译。
 */
private suspend fun fetchScript(rawUrl: String): Result<String> = withContext(Dispatchers.IO) {
    runCatching {
        require(rawUrl.startsWith("http://") || rawUrl.startsWith("https://")) {
            "链接要以 http:// 或 https:// 开头"
        }
        val conn = (java.net.URL(rawUrl).openConnection() as java.net.HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            instanceFollowRedirects = true
            requestMethod = "GET"
            setRequestProperty("User-Agent", "LinkPlayConsole")
        }
        try {
            val code = conn.responseCode
            if (code !in 200..299) throw IllegalStateException("服务器返回 $code")
            val bytes = conn.inputStream.use { input ->
                val buffer = java.io.ByteArrayOutputStream()
                val chunk = ByteArray(8 * 1024)
                while (true) {
                    val read = input.read(chunk)
                    if (read <= 0) break
                    buffer.write(chunk, 0, read)
                    if (buffer.size() > MAX_SCRIPT_BYTES) {
                        throw IllegalStateException("文件超过 ${MAX_SCRIPT_BYTES / 1024}KB，不像是一支脚本")
                    }
                }
                buffer.toByteArray()
            }
            String(bytes, Charsets.UTF_8)
        } finally {
            runCatching { conn.disconnect() }
        }
    }
}

@Composable
internal fun ImportScriptDialog(
    onDismiss: () -> Unit,
    onImport: (String, ScriptOrigin) -> Unit,
    errorText: String? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var picked by remember { mutableStateOf<String?>(null) }
    var url by remember { mutableStateOf("") }
    var fetching by remember { mutableStateOf(false) }
    var fetchNote by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val content = runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        }.getOrNull()
        if (content == null) {
            picked = "选中的文件读不出来（换个文件，或用粘贴）"
        } else {
            text = content
            picked = uri.lastPathSegment
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("导入洛雪脚本") },
        text = {
            Column {
                Text(
                    "粘在下面，或从文件选一份 .js。\n脚本开头要有 /*! … @name … */ 注释。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                VSpace(Spacing.xs)
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 110.dp, max = 200.dp),
                    placeholder = { Text("/*!\n * @name 某音源\n * @version v1.0.0\n */") },
                    textStyle = MaterialTheme.typography.labelSmall,
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                    TextButton(onClick = { picker.launch(arrayOf("text/plain", "application/javascript", "*/*")) }) {
                        Text("从文件选…")
                    }
                }

                // 从网址下载：装脚本从"复制几万字"变成"贴一个链接"
                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = { Text("或贴脚本链接 https://…") },
                        textStyle = MaterialTheme.typography.labelSmall,
                    )
                    TextButton(
                        enabled = url.isNotBlank() && !fetching,
                        onClick = {
                            fetching = true
                            fetchNote = null
                            scope.launch {
                                val result = fetchScript(url.trim())
                                fetching = false
                                result.onSuccess { body ->
                                    // 下载成功只**填进框里**，不直接导入：让用户先看一眼
                                    // 下载到的到底是什么（粘错链接时最常见的后果就是拉了个网页）
                                    text = body
                                    picked = null
                                    fetchNote = "已下载 ${body.toByteArray(Charsets.UTF_8).size / 1024}KB，检查后点「导入」"
                                }.onFailure { error ->
                                    fetchNote = error.message ?: "下载失败"
                                }
                            }
                        },
                    ) { Text(if (fetching) "下载中…" else "下载") }
                }
                fetchNote?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                picked?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                errorText?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onImport(text, if (picked == null) ScriptOrigin.PASTED else ScriptOrigin.FILE) },
                enabled = text.isNotBlank(),
            ) { Text("导入") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
