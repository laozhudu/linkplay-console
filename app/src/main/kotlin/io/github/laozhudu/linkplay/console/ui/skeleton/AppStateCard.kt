package io.github.laozhudu.linkplay.console.ui.skeleton

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.github.laozhudu.linkplay.console.ui.components.AppCard
import io.github.laozhudu.linkplay.console.ui.components.SectionTitle

/** 状态卡的四种语气 —— **它们不是同一个东西，文案和后续动作都不同**。 */
enum class StateKind {
    /** 真的没有内容（没收藏、没搜到）。 */
    Empty,

    /** 出错了：网络、设备、格式。**原文要给出来**。 */
    Error,

    /** 提示（"正在播 X"、"已切换"）。 */
    Hint,

    /** 正在处理（查目录、取地址）。 */
    Busy,
}

/**
 * 状态卡 —— 空态 / 错误 / 提示 / 处理中，全站一套。
 *
 * ## 为什么错误要能关
 *
 * 之前有两页的错误卡会一直挂在列表上方：搜索结果明明出来了，顶上还顶着红字，
 * 看起来像"搜索也失败了"。所以 [StateKind.Error] 一律带「知道了」。
 *
 * ## 为什么不合并成一句话
 *
 * "没搜到"和"连不上"必须分开：前者该换词，后者该查网络 ——
 * 混成一句，用户只能瞎试（`docs/52` 的「不给假空态」）。
 */
@Composable
fun AppStateCard(
    kind: StateKind,
    title: String,
    detail: String? = null,
    hint: String? = null,
    onDismiss: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    AppCard(modifier = modifier) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                SectionTitle(title = title, subtitle = hint ?: defaultHintOf(kind))
                detail?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (kind == StateKind.Error) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
            if (kind == StateKind.Error && onDismiss != null) {
                TextButton(onClick = onDismiss) { Text("知道了") }
            }
        }
    }
}

private fun defaultHintOf(kind: StateKind): String? = when (kind) {
    StateKind.Empty -> null
    StateKind.Error -> "原因原样给你，不猜"
    StateKind.Hint -> null
    StateKind.Busy -> null
}
