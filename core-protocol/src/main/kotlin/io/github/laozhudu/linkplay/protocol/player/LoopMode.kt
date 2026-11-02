package io.github.laozhudu.linkplay.protocol.player

/**
 * 循环模式。**枚举值直接就是设备 `setPlayerCmd:loopmode:<n>` 的取值**，
 * 不要另编号再映射 —— 两套编号迟早对不上（docs/01 §11 有官方表）。
 *
 * 注意 `SEQUENCE`（-1）与 `OFF`（0）的区别：`OFF` 是「顺序播完就停」，
 * `SEQUENCE` 是「顺序循环」。设备侧用负数表示循环，很容易写反。
 */
enum class LoopMode(val wire: Int, val label: String) {
    OFF(0, "不循环"),
    ONE(1, "单曲循环"),
    SHUFFLE(2, "随机"),
    SEQUENCE(-1, "列表循环");

    companion object {
        fun from(wire: Int?): LoopMode = entries.firstOrNull { it.wire == wire } ?: OFF

        /** 点一下切下一个模式，顺序按「最常用 → 最少用」。 */
        fun next(current: LoopMode): LoopMode = when (current) {
            OFF -> SEQUENCE
            SEQUENCE -> ONE
            ONE -> SHUFFLE
            SHUFFLE -> OFF
        }
    }
}
