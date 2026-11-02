package io.github.laozhudu.linkplay.console.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Material 3 Expressive 风格的花形/圆角多边形，用纯 Path 数学实现。
 *
 * 为什么自己算而不引 `androidx.graphics:graphics-shapes` + `MaterialShapes`：
 * - `MaterialShapes` 需要 material3 1.5.x（alpha/beta），而本项目固定在 2024.12 的 BOM；
 *   为几个形状去升整个 Compose 版本，风险远大于收益（编译器与 Kotlin 版本也要跟着动）。
 * - 这里只需要「花形」和「胶囊」两种，数学就 30 行，还能精确控制花瓣数与内凹比例。
 *
 * 参考的是 Rhythm 的视觉语言（圆形封面 + 花形侧键 + 大胶囊主键），实现是独立写的。
 */
class ScallopedShape(
    /** 花瓣数。8 接近「饼干」，6 更圆润。 */
    private val petals: Int = 8,
    /** 内凹深度：0 为正圆，越接近 1 越像花瓣。 */
    private val depth: Float = 0.18f,
) : Shape {

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val radius = minOf(cx, cy)
        val inner = radius * (1f - depth)

        val path = Path()
        val steps = petals * 24
        for (i in 0..steps) {
            val t = i.toFloat() / steps
            val angle = 2.0 * PI * t
            // 余弦叠加：r 在 radius 与 inner 之间按花瓣数周期性起伏
            val r = inner + (radius - inner) * ((cos(petals * angle) + 1f) / 2f)
            val x = (cx + r * cos(angle)).toFloat()
            val y = (cy + r * sin(angle)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return Outline.Generic(path)
    }

    override fun equals(other: Any?): Boolean =
        other is ScallopedShape && other.petals == petals && other.depth == depth

    override fun hashCode(): Int = petals * 31 + depth.hashCode()
}

/** 胶囊：高度的一半作圆角，宽度自适应。 */
val PillShape: Shape = RoundedCornerShape(percent = 50)

/** 柔和的圆角方块，用于次级图标按钮（Expressive 的 squircle 观感）。 */
val SquircleShape: Shape = RoundedCornerShape(18.dp)

/** 播放页里几种花形，集中一处避免各页面各写一个数值。 */
object AppShapesExpressive {
    val controlScallop: Shape = ScallopedShape(petals = 8, depth = 0.16f)
    val smallScallop: Shape = ScallopedShape(petals = 7, depth = 0.14f)
    val pill: Shape = PillShape
    val squircle: Shape = SquircleShape
}
