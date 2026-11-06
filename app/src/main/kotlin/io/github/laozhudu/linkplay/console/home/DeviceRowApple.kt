package io.github.laozhudu.linkplay.console.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.laozhudu.linkplay.console.ui.apple.rememberAppHaptics

/**
 * 设备行（Apple 风格）。
 *
 * ## 一行只放三件事
 *
 * ```
 * ● doss1   在放《034 诸子百家》     47  ›
 * └在线     └在放什么               └音量
 * ```
 *
 * 电量、固件、能不能组网、声道、预设 —— **全部不在这里**。
 * 用户原话：「用户不需要知道全部设备信息，用户只需要能用，好用，简单状态就够」。
 * 那些进详情页（点右边 chevron）。
 *
 * ## 音量为什么是"横向滑动"而不是一条小滑块
 *
 * 54dp 宽的滑块在 390dp 屏上只有拇指一半宽，拖不准；而且它会把"在放什么"
 * 挤到只能显示四五个字。
 *
 * 苹果在列表里的做法正是"在行上滑"：**整行可左右拖 = 调音量**，
 * 拖动时数字放大，松手才发命令（连续发会让音箱卡）。
 *
 * ## 三种手势共存（这是这个组件唯一复杂的地方）
 *
 * | 手势 | 行为 |
 * |---|---|
 * | 轻点 | 进这台设备的控制面板 |
 * | 长按 | 弹出菜单（重命名 / 取消组网 / 移除 / 详情） |
 * | 左右拖 | 调音量（松手提交） |
 *
 * 用 `combinedClickable` 管前两个，`detectHorizontalDragGestures` 管第三个 ——
 * 两者不会互相吞掉（Compose 的点击判定有位移阈值）。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun DeviceRowApple(
    name: String,
    /** null = 还不知道（正在探），true/false = 在线/离线。 */
    online: Boolean?,
    /** 状态文案，由调用方决定（"在放《…》" / "暂停" / "3 分钟前停的"）。 */
    statusText: String,
    volume: Int,
    onClick: () -> Unit,
    /** 松手才调 —— 拖动过程只更新本地显示。 */
    onVolumeCommit: (Int) -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    showDivider: Boolean = true,
    /**
     * 这台是不是**当前的播放目标**（`docs/96` 模型 A）。
     *
     * 为什么必须有：大厂（Sonos 房间 / Spotify Connect）都让"现在投给谁"永远可见。
     * 我们的目标原来是播放页里一个看不见的开关，用户只能靠听。
     */
    inUse: Boolean = false,
) {
    val haptics = rememberAppHaptics()
    // 拖动中的临时值。null = 没在拖，显示真实音量
    var draft by remember { mutableStateOf<Int?>(null) }
    val shown = draft ?: volume
    val density = LocalDensity.current
    // 从"滑过 100dp 走满 0..100"换算出每像素的步进，屏宽不同也一致
    val stepPerPx = remember(density) { with(density) { 100f / 220.dp.toPx() } }
    // 离线设备：不显示音量数字、也不响应左右拖。
    // 原来离线也画「0」并且能拖 —— 那个 0 是"没读到"不是"音量是 0"，
    // 更糟的是拖了会往一台关着的音箱发命令。
    val interactive = online != false

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = { haptics.tap(); onClick() },
                    onLongClick = { haptics.impact(); onLongClick() },
                )
                .pointerInput(volume, interactive) {
                    if (!interactive) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            draft?.let { onVolumeCommit(it) }
                            draft = null
                        },
                        onDragCancel = { draft = null },
                        onHorizontalDrag = { _, delta ->
                            val current = draft ?: volume
                            val next = (current + (delta * stepPerPx).toInt()).coerceIn(0, 100)
                            if (next != current) {
                                draft = next
                                // 每跨 5 格给一次刻度感，不持续震（会手麻）
                                if (next % 5 == 0) haptics.tick()
                            }
                        },
                    )
                }
                .heightIn(min = 56.dp)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 状态点：在线绿、离线灰、未知半透明
            Box(
                Modifier
                    .size(9.dp)
                    .background(
                        when (online) {
                            true -> io.github.laozhudu.linkplay.console.ui.theme.StatusColors.ok
                            false -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                            null -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                        },
                        CircleShape,
                    ),
            )
            Spacer(Modifier.width(10.dp))
            // 名字**不写死宽度**。原来 76dp 把「DOSS DS-1831」截成「DOSS…」、
            // 「E900V22C-175」截成「E900…」—— 用户自己起的名字被截断是最难认的。
            // 改成按比例分：名字占 45%，状态占剩下的，两边都能伸缩。
            Text(
                name,
                fontSize = 17.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(0.45f),
            )
            if (inUse) {
                Text(
                    "正在用",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(end = 4.dp),
                )
            }
            Text(
                statusText,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                // 离线显示「—」而不是 0：0 会被读成"音量是零"，那是另一回事
                if (!interactive) "—" else shown.toString(),
                fontSize = if (draft != null) 20.sp else 15.sp,
                fontWeight = if (draft != null) FontWeight.SemiBold else FontWeight.Normal,
                color = if (draft != null) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                textAlign = androidx.compose.ui.text.style.TextAlign.End,
                modifier = Modifier.width(34.dp),
            )
            Text(
                "›",
                fontSize = 19.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f),
                modifier = Modifier.padding(start = 6.dp),
            )
        }
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 14.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
            )
        }
    }
}
