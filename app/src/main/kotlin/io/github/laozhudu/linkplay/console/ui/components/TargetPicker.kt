package io.github.laozhudu.linkplay.console.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.laozhudu.linkplay.console.playback.PlaybackOutput
import io.github.laozhudu.linkplay.console.playback.PlaybackOutputStore
import io.github.laozhudu.linkplay.console.session.SessionStore
import io.github.laozhudu.linkplay.console.ui.theme.Spacing

/**
 * 「从哪出声 / 投给」—— **一个组件，两处共用**。
 *
 * ## 为什么必须共用
 *
 * 用户原话：「内容页投给我说过与播放页的投屏保持样式和功能一致，你没做到」。
 *
 * 原来播放页是 cast 图标 + 弹层，内容页是一排横向 chips —— 同一个概念两种形态，
 * 而且内容页那排还会被设备长名字撑出屏幕。
 *
 * 现在两边都用这一套：[CastButton] 贴在界面上，点开 [TargetSheet] 选。
 *
 * ## 为什么是 cast 图标而不是文字
 *
 * Spotify 底部、Apple Music 的 AirPlay、YouTube 顶栏 —— 大厂都是
 * 「一个图标 + 点开一层列表」，不占常驻空间，用户也不用学。
 */
/**
 * 左下角的「从哪出声」按钮 —— 大厂的 cast / AirPlay 位置。
 *
 * 为什么不写文字：设备一多，一行 chips 会横滚（之前就是这样），
 * 而且顶部已经有一句状态了，再铺一行看着像重复。一个大厂形状的图标
 * 用户不用学就知道是"这声音往哪去"。
 */
@Composable
fun CastButton(
    label: String,
    isLocal: Boolean,
    onClick: () -> Unit,
    /**
     * 图标和文字的颜色。
     *
     * ## 为什么必须能由调用方指定（真机 bug）
     *
     * 播放页的背景是**固定深色**（那页不看主题，就是要沉浸）。
     * 而内容页跟随主题。如果这个组件写死用 `MaterialTheme.colorScheme.onSurface`：
     *
     * - 浅色主题 + 播放页深色背景 → onSurface 是**黑的** → **黑图标落在深色底上，等于没有**
     * - 真机截图就是这个现象：左下角只剩一行几乎看不见的 "doss1"
     *
     * 所以颜色必须是参数：播放页传 `OnDarkPalette.text`，内容页传主题色。
     * **组件不认识背景，背景由调用方知道。**
     */
    contentColor: androidx.compose.ui.graphics.Color,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.xs, vertical = 2.dp),
    ) {
        // 颜色在 Canvas **外面**取：DrawScope 不是 @Composable 上下文
        val iconColor = contentColor
        androidx.compose.foundation.Canvas(Modifier.size(22.dp)) {
            val w = size.width
            val h = size.height
            val stroke = w * 0.085f
            val c = iconColor
            // 右下角矩形（屏幕）
            drawRoundRect(
                color = c,
                topLeft = androidx.compose.ui.geometry.Offset(w * 0.30f, h * 0.16f),
                size = androidx.compose.ui.geometry.Size(w * 0.62f, h * 0.46f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.06f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke),
            )
            // 左下角的同心弧（信号）
            drawArc(
                color = c,
                startAngle = 270f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(w * 0.06f, h * 0.32f),
                size = androidx.compose.ui.geometry.Size(w * 0.72f, h * 0.72f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke),
            )
            drawArc(
                color = c,
                startAngle = 270f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(w * 0.10f, h * 0.52f),
                size = androidx.compose.ui.geometry.Size(w * 0.44f, h * 0.44f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke),
            )
            drawCircle(
                color = c,
                radius = stroke * 0.95f,
                center = androidx.compose.ui.geometry.Offset(w * 0.16f, h * 0.78f),
            )
        }
        Text(
            // label 已经是最终名字（由 targetLabel 算好），这里只截长度防溢出
            text = label.take(8),
            style = MaterialTheme.typography.labelSmall,
            // 文字比图标再淡一点，层次分明；但同样必须跟随调用方给的颜色
            color = contentColor.copy(alpha = 0.75f),
            maxLines = 1,
            modifier = Modifier.padding(top = 1.dp),
        )
    }
}

/**
 * 选目标的一层（临时任务层，`docs/97`）。
 *
 * 用 `ModalBottomSheet` 而不是 Dialog：大厂（Spotify / Apple Music / YouTube）
 * 选输出设备的都是可下拉关闭的 sheet，因为这是"看一眼选一个就走"的动作。
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun TargetSheet(
    /** 从外面传进来 —— 内部再读一次的话，读不到就只剩「本机」，表现是"换不出别的设备"。 */
    devices: List<io.github.laozhudu.linkplay.protocol.session.SavedDevice>,
    onDismiss: () -> Unit,
    onPick: (PlaybackOutput) -> Unit,
) {
    val target = io.github.laozhudu.linkplay.console.playback.PlaybackOutputStore.current
        .collectAsState().value

    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(bottom = Spacing.lg)) {
            Text(
                "从哪出声",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            )
            // 本机
            TargetSheetRow(
                title = "本机",
                subtitle = "这台手机自己放",
                selected = target is PlaybackOutput.Local,
                onClick = { onPick(PlaybackOutput.Local) },
            )
            devices.forEach { dev ->
                TargetSheetRow(
                    title = dev.name.ifBlank { dev.ip },
                    subtitle = dev.ip,
                    selected = (target as? PlaybackOutput.Device)?.ip == dev.ip,
                    onClick = { onPick(PlaybackOutput.Device(dev.ip, dev.name)) },
                )
            }
        }
    }
}

@Composable
private fun TargetSheetRow(
    title: String,
    subtitle: String?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            subtitle?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (selected) {
            Text("✓", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}
