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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.laozhudu.linkplay.console.ui.components.VSpace
import io.github.laozhudu.linkplay.console.ui.theme.Spacing
import io.github.laozhudu.script.ScriptOrigin

/**
 * 导入脚本的对话框 —— **两个页面共用**（脚本管理页、洛雪搜索页的"没脚本"引导）。
 *
 * 抽出来的理由很实在：用户点播放时才发现"没脚本"，这时候弹的框必须和脚本页里
 * 那个长得完全一样、行为完全一样。各写一份必然漂移（一个能选文件、一个不能）。
 *
 * @param errorText 导入被拒时的原因（原样显示，不翻译成"失败"）
 */
@Composable
internal fun ImportScriptDialog(
    onDismiss: () -> Unit,
    onImport: (String, ScriptOrigin) -> Unit,
    errorText: String? = null,
) {
    val context = LocalContext.current
    var text by remember { mutableStateOf("") }
    var picked by remember { mutableStateOf<String?>(null) }

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
