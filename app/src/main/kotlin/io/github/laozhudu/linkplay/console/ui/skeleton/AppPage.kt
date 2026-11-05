package io.github.laozhudu.linkplay.console.ui.skeleton

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.laozhudu.linkplay.console.ui.components.PageHeader
import io.github.laozhudu.linkplay.console.ui.theme.Spacing

/**
 * 页面骨架 —— **所有页面从这里开始搭**。
 *
 * ## 为什么需要它
 *
 * 统计过：16 个页面里 6 个用统一的 [PageHeader]，3 个手写「返回 + 标题 + 右侧按钮」。
 * 手写的那几页字号、返回按钮、内边距都和别处不一样 ——
 * 用户从一个页面进另一个会觉得"跳了一下"，却说不出哪不对。
 *
 * ## 两种滚动方式，只能选一种
 *
 * - `scrollWholePage = true`：卡片堆叠的页面（设置、说明、脚本列表）。
 * - `scrollWholePage = false`：列表页。内容区拿到 `weight(1f)`，**里面的 LazyColumn
 *   直接写 `Modifier.fillMaxSize()` 是安全的** —— 不会再把上面的搜索框挤掉。
 *
 * 之前两页在普通 Column 里用 `LazyColumn(fillMaxSize)`，内容一多就抢高度，
 * 这就是"哪里不舒服"里最实的一处布局问题。骨架把这件事一次解决。
 */
@Composable
fun AppPage(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
    scrollWholePage: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scroll = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .let { if (scrollWholePage) it.verticalScroll(scroll) else it }
            .padding(horizontal = Spacing.lg),
    ) {
        PageHeader(title = title, subtitle = subtitle, onBack = onBack, trailing = trailing)
        Column(
            modifier = if (scrollWholePage) {
                Modifier.fillMaxWidth()
            } else {
                Modifier.fillMaxWidth().weight(1f, fill = false)
            },
            content = content,
        )
    }
}
