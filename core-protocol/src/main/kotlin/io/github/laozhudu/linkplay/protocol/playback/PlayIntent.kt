package io.github.laozhudu.linkplay.protocol.playback

/**
 * 用户想干的全部播放动作。
 *
 * ## 从 VoiceIntent 演进而来
 *
 * 原来叫 `VoiceIntent`，只服务语音（`PlayerScreen:865` 唯一使用点）。
 * 但「下一首」在界面 / 悬浮窗 / 通知栏各走各的，一共 4 条路径 ——
 * 因为意图层只给语音开了口。
 *
 * 改名的理由：**意图不关心它是说出来的还是点出来的。**
 *
 * ## 为什么仍是 sealed interface
 *
 * 分派端必须**穷尽**处理所有分支，新增意图时编译器直接报错，
 * 而不是运行时静默落到 else 变成「点了没反应」。
 */
sealed interface PlayIntent {

    data object Next : PlayIntent
    data object Previous : PlayIntent
    data object Play : PlayIntent
    data object Pause : PlayIntent
    data object Stop : PlayIntent

    /** 「播放/暂停」取决于当前状态，交给执行点按状态决定。 */
    data object TogglePlay : PlayIntent

    data object VolumeUp : PlayIntent
    data object VolumeDown : PlayIntent
    data object Mute : PlayIntent

    /** 循环模式切下一个（单曲/列表/随机）。 */
    data object LoopNext : PlayIntent

    /** 拖动音量条：定值而不是增量。 */
    data class SetVolume(val value: Int) : PlayIntent

    /** 拖动进度条：秒。 */
    data class SeekTo(val seconds: Long) : PlayIntent

    /** 想找某首/某人的歌：关键词交给搜索，不决定从哪个曲库搜。 */
    data class Search(val keyword: String) : PlayIntent

    /** 没听懂。**带上原文**是为了原样回显，让用户知道我们听到了什么。 */
    data class Unknown(val text: String) : PlayIntent
}
