package io.github.laozhudu.linkplay.console.playback

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import io.github.laozhudu.linkplay.console.ui.components.AppShapesExpressive
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.laozhudu.linkplay.console.ui.components.VolumeGlyph
import io.github.laozhudu.linkplay.console.ui.theme.OnDarkPalette
import io.github.laozhudu.linkplay.console.ui.theme.Spacing

/**
 * 手机音量键的浮层提示。
 *
 * ## 为什么要做
 *
 * 用户的原话：「点音量键要有原手机出现的音量标识可看，要更加跟手」。
 * 音量键被 App 接管之后，Android 自己那根音量条就不再出现，
 * 用户按了键只看音箱有没有响 —— 没有反馈就会怀疑没按上。
 *
 * ## 为什么自己画而不复用系统的
 *
 * 系统音量面板是 `SystemUI` 的窗口，App 拿不到也改不了；
 * 而我们要显示的是**音箱音量**（不是手机音量），所以只能自绘一个同样式的。
 *
 * 样式对齐 Android 原生：圆角卡片、喇叭图标、细长进度条、右侧数字。
 * 位置放在屏幕中上部 —— 与原生一致，且不遮挡底部控制键。
 */
@Composable
fun VolumeOverlay(
    visible: Boolean,
    volume: Int,
    mute: Boolean,
    deviceName: String?,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn() + slideInVertically { -it / 2 },
            exit = fadeOut() + slideOutVertically { -it / 3 },
        ) {
            Column(
                modifier = Modifier
                    .padding(top = 72.dp)
                    .width(240.dp)
                    .clip(AppShapesExpressive.squircle)
                    // 半透明深底 + 高对比正文：压在任意页面之上都能读
                    .background(Color(0xF01A1F2A))
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    VolumeGlyph(
                        color = if (mute) OnDarkPalette.muted else OnDarkPalette.text,
                        muted = mute,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(Spacing.xs))
                    Text(
                        text = if (mute) "已静音" else "音量 $volume",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = OnDarkPalette.text,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "${volume}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = OnDarkPalette.muted,
                    )
                }
                Spacer(Modifier.height(6.dp))
                // 原生音量条是分段的；这里用一条圆角轨道 + 填充，视觉等价且更细
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(AppShapesExpressive.pill)
                        .background(Color(0x33FFFFFF)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth((volume / 100f).coerceIn(0f, 1f))
                            .height(6.dp)
                            .clip(AppShapesExpressive.pill)
                            .background(if (mute) Color(0x66FFFFFF) else Color(0xFF9FD3FF)),
                    )
                }
                if (!deviceName.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = deviceName,
                        style = MaterialTheme.typography.labelSmall,
                        color = OnDarkPalette.faint,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
