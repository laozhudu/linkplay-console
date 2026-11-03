package io.github.laozhudu.linkplay.protocol.floating

/**
 * 悬浮小窗的位置与尺寸（单位与调用方一致，App 里一律是 **px**）。
 *
 * 为什么单独建模而不是直接塞 `WindowManager.LayoutParams`：
 * 拖动、夹取、吸附都是纯算术，真机上验证一轮要装包、开权限、拖来拖去，
 * 抽成纯 JVM 就能把「拖出屏幕」「转屏后一半在屏外」这些边界一次测全。
 */
data class FloatSpec(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
)

/**
 * 悬浮窗的位置策略（纯逻辑，无 Android 依赖）。
 *
 * ## 为什么要三件事分开
 *
 * - [FloatingLayout.clamp]：**任何时刻**都不能让窗口有一截留在屏幕外。
 *   拖到边缘、分屏、折叠屏展开都会触发，必须每帧都能做。
 * - [FloatingLayout.onDrag]：拖动中调用，等同「按位移移动 + 夹回」，但**不吸附** ——
 *   手指还在按着的时候窗口自己跑到边上会让人抓不住。
 * - [FloatingLayout.snap]：松手后才吸附到左右边缘。原厂悬浮窗就是这个手感：
 *   拖到中间松手会自己贴到最近的一侧，不会半截挂在屏幕中间挡住内容。
 *
 * 旋转屏幕是这套逻辑唯一真正救命的场景：竖屏拖到右下角（如 900,1800），
 * 转横屏后屏幕只有 1080 高，不夹取的话整块窗就消失了，用户以为功能坏了。
 */
object FloatingLayout {

    /**
     * 窗口边缘与屏幕边缘之间保留的空白。
     *
     * 默认 8 表示「与坐标同单位」；App 层会把 8dp 换算成 px 再传 [edge]，
     * 这样不同密度下留白看起来一样宽，而纯逻辑层不需要知道 dp 是怎么回事。
     */
    const val EDGE = 8

    /** 把窗口夹回屏幕内（缩不下的尺寸会被压到能放下的最大值）。 */
    fun clamp(spec: FloatSpec, screenW: Int, screenH: Int, edge: Int = EDGE): FloatSpec {
        val w = fitSize(spec.width, screenW, edge)
        val h = fitSize(spec.height, screenH, edge)
        val x = clampAxis(spec.x, w, screenW, edge)
        val y = clampAxis(spec.y, h, screenH, edge)
        return FloatSpec(x = x, y = y, width = w, height = h)
    }

    /**
     * 松手吸附到左右最近的一侧。
     *
     * 先夹回屏幕再判断「中心偏哪边」：不先夹的话，一个已经跑到屏幕外的窗口
     * 中心点可能算在屏幕左边，于是被吸到左边缘 —— 手指明明往右拖，窗口却跳到左边。
     * 竖直方向不吸附：桌面图标与小部件通常在上半部，随手放的位置更有用。
     */
    fun snap(spec: FloatSpec, screenW: Int, screenH: Int, edge: Int = EDGE): FloatSpec {
        val inside = clamp(spec, screenW, screenH, edge)
        val center = inside.x + inside.width / 2
        val x = if (center * 2 < screenW) {
            leftX(screenW, inside.width, edge)
        } else {
            rightX(screenW, inside.width, edge)
        }
        return inside.copy(x = x)
    }

    /** 拖动中的一帧：位移 + 夹回，不吸附（手指还按着，窗口不该自己跑）。 */
    fun onDrag(spec: FloatSpec, dx: Int, dy: Int, screenW: Int, screenH: Int, edge: Int = EDGE): FloatSpec =
        clamp(spec.copy(x = spec.x + dx, y = spec.y + dy), screenW, screenH, edge)

    /** 贴左边缘时的 x。 */
    fun leftX(screenW: Int, width: Int, edge: Int = EDGE): Int = axisBounds(width, screenW, edge).first

    /** 贴右边缘时的 x。 */
    fun rightX(screenW: Int, width: Int, edge: Int = EDGE): Int = axisBounds(width, screenW, edge).second

    /**
     * 某个轴上的合法区间 `[最小, 最大]`。
     *
     * 关键点：窗口比可用空间还大时（屏幕被分成很小的分屏、或尺寸写错），
     * 上界会小于留白 8，这时**以下界为准**把窗口贴到 0 侧，而不是抛
     * `IllegalArgumentException`（`coerceIn` 在 min > max 时会抛，真机上就是崩溃）。
     */
    private fun axisBounds(size: Int, screen: Int, edge: Int): Pair<Int, Int> {
        val hi = (screen - size - edge).coerceAtLeast(0)
        val lo = edge.coerceAtMost(hi)
        return lo to hi
    }

    private fun clampAxis(pos: Int, size: Int, screen: Int, edge: Int): Int {
        val (lo, hi) = axisBounds(size, screen, edge)
        return pos.coerceIn(lo, hi)
    }

    /**
     * 尺寸不能超过「屏幕 - 两侧留白」，否则不管 x 怎么放都有一截在外面。
     *
     * 屏幕小到连留白都放不下（`screen <= 2*edge`）时退回「不超过屏幕」，
     * 保证窗口至少还看得见一部分。
     */
    private fun fitSize(size: Int, screen: Int, edge: Int): Int {
        if (size <= 0 || screen <= 0) return size.coerceAtLeast(0)
        val room = screen - 2 * edge
        return if (room <= 0) size.coerceAtMost(screen) else size.coerceAtMost(room)
    }
}
