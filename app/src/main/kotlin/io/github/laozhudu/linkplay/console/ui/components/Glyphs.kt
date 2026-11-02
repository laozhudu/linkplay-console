package io.github.laozhudu.linkplay.console.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * 手绘图标集。
 *
 * 为什么不用 `material-icons-extended`：那个包给 APK 增加约 3 MB，而我们只需要十来个图形；
 * `material-icons-core` 又缺 Repeat/Shuffle/Stop 这几个。自己画二十行，顺带能精确控制
 * 线宽与圆角，让按钮大小变化时视觉重量保持一致。
 */
@Composable
fun PlayGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val p = Path().apply {
            moveTo(w * 0.30f, h * 0.18f)
            lineTo(w * 0.80f, h * 0.50f)
            lineTo(w * 0.30f, h * 0.82f)
            close()
        }
        drawPath(p, color)
    }
}

@Composable
fun PauseGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val barW = w * 0.16f
        drawRoundRect(color, Offset(w * 0.28f, h * 0.20f), Size(barW, h * 0.60f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(barW / 2))
        drawRoundRect(color, Offset(w * 0.56f, h * 0.20f), Size(barW, h * 0.60f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(barW / 2))
    }
}

@Composable
fun StopGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val r = size.minDimension * 0.26f
        drawRoundRect(
            color,
            Offset(size.width / 2 - r, size.height / 2 - r),
            Size(r * 2, r * 2),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(r * 0.3f),
        )
    }
}

/** 上一首 / 下一首：两根竖条 + 三角，`forward=false` 时左右镜像。 */
@Composable
fun SkipGlyph(color: Color, forward: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val p = Path()
        if (forward) {
            p.moveTo(w * 0.18f, h * 0.22f); p.lineTo(w * 0.62f, h * 0.50f); p.lineTo(w * 0.18f, h * 0.78f)
        } else {
            p.moveTo(w * 0.82f, h * 0.22f); p.lineTo(w * 0.38f, h * 0.50f); p.lineTo(w * 0.82f, h * 0.78f)
        }
        p.close()
        drawPath(p, color)
        val barX = if (forward) w * 0.70f else w * 0.24f
        drawRoundRect(
            color, Offset(barX, h * 0.22f), Size(w * 0.10f, h * 0.56f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.05f),
        )
    }
}

/**
 * 循环图标。`one=true` 时在中间加「1」，`shuffle=true` 时画交叉箭头。
 * 三种状态用同一个函数，避免三份几乎一样的图在各处漂移。
 */
@Composable
fun LoopGlyph(color: Color, mode: Int, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = w * 0.09f, cap = StrokeCap.Round)
        val inset = w * 0.18f
        if (mode == 2) {
            // 随机：两条交叉斜线 + 箭头
            drawLine(color, Offset(inset, h * 0.30f), Offset(w - inset, h * 0.70f), w * 0.09f, StrokeCap.Round)
            drawLine(color, Offset(inset, h * 0.70f), Offset(w - inset, h * 0.30f), w * 0.09f, StrokeCap.Round)
            val r = w * 0.11f
            drawCircle(color, r, Offset(w - inset, h * 0.70f))
            drawCircle(color, r, Offset(w - inset, h * 0.30f))
        } else {
            drawRoundRect(
                color,
                Offset(inset, inset),
                Size(w - inset * 2, h - inset * 2),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.26f),
                style = stroke,
            )
            // 右上角一个小箭头表示「循环方向」
            val r = w * 0.10f
            drawCircle(color, r, Offset(w - inset, inset))
            if (mode == 1) {
                // 单曲：中间一竖，简化为「1」
                drawLine(color, Offset(w * 0.50f, h * 0.34f), Offset(w * 0.50f, h * 0.66f), w * 0.10f, StrokeCap.Round)
            }
        }
    }
}

@Composable
fun VolumeGlyph(color: Color, muted: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val sp = Path().apply {
            moveTo(w * 0.12f, h * 0.38f)
            lineTo(w * 0.30f, h * 0.38f)
            lineTo(w * 0.52f, h * 0.20f)
            lineTo(w * 0.52f, h * 0.80f)
            lineTo(w * 0.30f, h * 0.62f)
            lineTo(w * 0.12f, h * 0.62f)
            close()
        }
        drawPath(sp, color)
        if (muted) {
            drawLine(color, Offset(w * 0.64f, h * 0.36f), Offset(w * 0.90f, h * 0.64f), w * 0.08f, StrokeCap.Round)
            drawLine(color, Offset(w * 0.90f, h * 0.36f), Offset(w * 0.64f, h * 0.64f), w * 0.08f, StrokeCap.Round)
        } else {
            val stroke = Stroke(width = w * 0.075f, cap = StrokeCap.Round)
            drawArc(color, -50f, 100f, false, Offset(w * 0.46f, h * 0.30f), Size(w * 0.26f, h * 0.40f), style = stroke)
            drawArc(color, -50f, 100f, false, Offset(w * 0.44f, h * 0.20f), Size(w * 0.44f, h * 0.60f), style = stroke)
        }
    }
}

/** 返回箭头（‹）。 */
@Composable
fun BackGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = w * 0.11f, cap = StrokeCap.Round)
        val p = Path().apply {
            moveTo(w * 0.62f, h * 0.22f)
            lineTo(w * 0.34f, h * 0.50f)
            lineTo(w * 0.62f, h * 0.78f)
        }
        drawPath(p, color, style = stroke)
    }
}

/** 折叠/展开的向下箭头。 */
@Composable
fun ChevronGlyph(color: Color, expanded: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = w * 0.10f, cap = StrokeCap.Round)
        val p = Path().apply {
            if (expanded) {
                moveTo(w * 0.22f, h * 0.62f); lineTo(w * 0.50f, h * 0.36f); lineTo(w * 0.78f, h * 0.62f)
            } else {
                moveTo(w * 0.22f, h * 0.38f); lineTo(w * 0.50f, h * 0.64f); lineTo(w * 0.78f, h * 0.38f)
            }
        }
        drawPath(p, color, style = stroke)
    }
}

/** 统一点尺寸，避免每处各写 20.dp / 22.dp。 */
object GlyphSize {
    val small = 16.dp
    val medium = 22.dp
    val large = 30.dp
    val hero = 34.dp
}
