package io.github.laozhudu.linkplay.console.ui.skeleton

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction

/**
 * 搜索框 —— 洛雪、电台、曲库、在线曲库共用一套。
 *
 * 统一掉四件以前各页做法不同的事：
 * 1. **进页面光标就在框里**（少点一下）；
 * 2. **键盘上就有「搜索」键**（不必去够右边按钮）；
 * 3. 占位文案风格统一（"歌名，回车就搜"，不是有的页写"请输入…"）；
 * 4. 输入为空时按钮禁用 —— 而不是点了没反应。
 */
@Composable
fun AppSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    onSubmit: () -> Unit,
    enabled: Boolean = true,
    autoFocus: Boolean = true,
    submitLabel: String = "搜索",
    modifier: Modifier = Modifier,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (autoFocus) runCatching { focus.requestFocus() }
    }
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f).focusRequester(focus),
            singleLine = true,
            placeholder = { Text(placeholder) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
        )
        TextButton(onClick = onSubmit, enabled = enabled && value.isNotBlank()) { Text(submitLabel) }
    }
}
