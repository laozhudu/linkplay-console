package io.github.laozhudu.linkplay.console.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/**
 * 波浪进度条（Material 3 Expressive 的 wavy progress 观感）。
 *
 * 为什么不用 `LinearWavyProgressIndicator`：那是 material3 1.4+ 才有的组件，
 * 本项目固定在 2024.12 的 BOM。自己画一条正弦轨道只要 40 行，还能顺便把
 * 「拖动时才显示波形」这种细节做进去。
 *
 * 交互与 [io.github.laozhudu.linkplay.console.playback.PlayerScreen] 一致：
 * 拖动过程中只回调 [onValueChange]，松手才回调 [onValueChangeFinished]
 * —— 设备侧 seek 会打断缓冲，连续下发会让曲子一直卡。
 */
@Composable
fun WaveProgressSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
    activeColor: Color,
    inactiveColor: Color,
    thumbColor: Color,
    /** 播放中才让波形动起来；暂停时是直线，省一次无限动画的开销。 */
    animated: Boolean = true,
    enabled: Boolean = true,
    height: androidx.compose.ui.unit.Dp = 36.dp,
) {
    var dragging by remember { mutableStateOf(false) }
    val latestValue by rememberUpdatedState(value)
    val latestOnChange by rememberUpdatedState(onValueChange)
    val latestOnFinished by rememberUpdatedState(onValueChangeFinished)
    val currentEnabled by rememberUpdatedState(enabled)

    // 波形相位：只有播放中才推进，避免静止时也在重组
    val phase = if (animated) {
        val t = rememberInfiniteTransition(label = "wave")
        t.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart),
            label = "wave-phase",
        ).value
    } else {
        0f
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .pointerInput(currentEnabled) {
                if (!currentEnabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown()
                    dragging = true
                    fun frac(x: Float) = (x / size.width).coerceIn(0f, 1f)
                    latestOnChange(frac(down.position.x))
                    var pointer = down
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == pointer.id } ?: break
                        if (!change.pressed) break
                        latestOnChange(frac(change.position.x))
                        change.consume()
                        pointer = change
                    }
                    dragging = false
                    latestOnFinished()
                }
            },
    ) {
        Canvas(Modifier.fillMaxWidth().height(height)) {
            val w = size.width
            val h = size.height
            val cy = h / 2f
            val stroke = Stroke(width = h * 0.11f, cap = StrokeCap.Round)
            val amp = h * if (dragging) 0.20f else 0.13f
            val splitX = (w * value.coerceIn(0f, 1f))

            fun wavePath(fromX: Float, toX: Float): Path {
                val p = Path()
                if (toX <= fromX) return p
                val wavelength = w / 6f
                var x = fromX
                p.moveTo(x, cy)
                while (x < toX) {
                    x += 1.5f
                    // 只在未播放的一侧取正半周，形成连续的波浪
                    val t = (x - fromX) / wavelength + phase * 2f
                    p.lineTo(x, cy + amp * sin(t * 2f * PI.toFloat()))
                }
                return p
            }

            // 未播放部分：直线，低对比
            drawLine(
                color = inactiveColor,
                start = Offset(0f, cy),
                end = Offset(w, cy),
                strokeWidth = stroke.width,
                cap = StrokeCap.Round,
            )
            // 已播放部分：波浪
            drawPath(wavePath(0f, splitX), color = activeColor, style = stroke)

            // 拖柄
            drawCircle(color = thumbColor, radius = h * 0.16f, center = Offset(splitX, cy))
            drawCircle(color = activeColor, radius = h * 0.09f, center = Offset(splitX, cy))
        }
    }
}
