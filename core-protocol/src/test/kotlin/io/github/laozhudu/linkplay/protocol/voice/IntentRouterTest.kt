package io.github.laozhudu.linkplay.protocol.voice

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 语音命令词解析的回归测试。
 *
 * 为什么要这么多条：这一层的输入是**系统语音识别的输出**，不是用户手打的规范文本。
 * 同一种意图在真实场景里会有十几种说法（带语气词、带标点、全角半角混用），
 * 任何一个说法漏掉，用户的感受就是「这 App 听不懂人话」。
 * 每条用例都对应一种真实口语形态，改词表时必须全绿。
 */
class IntentRouterTest {

    private fun assertRoute(text: String, expected: VoiceIntent) {
        assertEquals("「$text」应解析为 $expected", expected, IntentRouter.route(text))
    }

    // ---------- Next ----------

    @Test
    fun `next 直白说法`() = assertRoute("下一首", VoiceIntent.Next)

    @Test
    fun `next 带语气词与标点`() = assertRoute("换一首吧！", VoiceIntent.Next)

    @Test
    fun `next 跳过这首`() = assertRoute("跳过这首歌", VoiceIntent.Next)

    @Test
    fun `next 英文`() = assertRoute("next", VoiceIntent.Next)

    @Test
    fun `next 口语否定式`() = assertRoute("不要这首", VoiceIntent.Next)

    @Test
    fun `播放下一首 也算 next`() = assertRoute("播放下一首", VoiceIntent.Next)

    // ---------- Previous ----------

    @Test
    fun `previous 直白说法`() = assertRoute("上一首", VoiceIntent.Previous)

    @Test
    fun `previous 长句`() = assertRoute("回到上一首", VoiceIntent.Previous)

    @Test
    fun `previous 英文`() = assertRoute("previous", VoiceIntent.Previous)

    // ---------- Play ----------

    @Test
    fun `play 直白`() = assertRoute("播放", VoiceIntent.Play)

    @Test
    fun `play 继续`() = assertRoute("继续", VoiceIntent.Play)

    @Test
    fun `play 接着放`() = assertRoute("接着放", VoiceIntent.Play)

    @Test
    fun `play 英文 resume`() = assertRoute("resume", VoiceIntent.Play)

    @Test
    fun `play 带语气词`() = assertRoute("播放吧", VoiceIntent.Play)

    // ---------- Pause ----------

    @Test
    fun `pause 直白`() = assertRoute("暂停", VoiceIntent.Pause)

    // 「停一下」里含「停」，必须仍然算暂停而不是停止
    @Test
    fun `pause 停一下 不能被当成停止`() = assertRoute("停一下", VoiceIntent.Pause)

    @Test
    fun `pause 英文`() = assertRoute("pause", VoiceIntent.Pause)

    // ---------- Stop ----------

    @Test
    fun `stop 直白`() = assertRoute("停止", VoiceIntent.Stop)

    @Test
    fun `stop 口语`() = assertRoute("别放了", VoiceIntent.Stop)

    @Test
    fun `stop 关掉音乐`() = assertRoute("关掉音乐", VoiceIntent.Stop)

    @Test
    fun `stop 英文`() = assertRoute("stop", VoiceIntent.Stop)

    // ---------- TogglePlay ----------

    // 「播放暂停」含「暂停」，顺序错了就会变成 Pause
    @Test
    fun `toggle 播放暂停`() = assertRoute("播放暂停", VoiceIntent.TogglePlay)

    @Test
    fun `toggle 切换播放`() = assertRoute("切换播放", VoiceIntent.TogglePlay)

    // ---------- 音量 ----------

    @Test
    fun `volume up 大声点`() = assertRoute("大声点", VoiceIntent.VolumeUp)

    @Test
    fun `volume up 音量加`() = assertRoute("音量加一点", VoiceIntent.VolumeUp)

    @Test
    fun `volume up 把声音调大`() = assertRoute("帮我把声音调大", VoiceIntent.VolumeUp)

    @Test
    fun `volume down 小声点`() = assertRoute("小声点", VoiceIntent.VolumeDown)

    @Test
    fun `volume down 音量减`() = assertRoute("音量减", VoiceIntent.VolumeDown)

    @Test
    fun `volume 英文简写`() {
        assertRoute("volup", VoiceIntent.VolumeUp)
        assertRoute("voldown", VoiceIntent.VolumeDown)
    }

    // ---------- 静音 ----------

    @Test
    fun `mute 静音`() = assertRoute("静音", VoiceIntent.Mute)

    @Test
    fun `mute 别出声`() = assertRoute("别出声", VoiceIntent.Mute)

    @Test
    fun `mute 英文`() = assertRoute("mute", VoiceIntent.Mute)

    // ---------- 循环 ----------

    @Test
    fun `loop 循环模式`() = assertRoute("循环模式", VoiceIntent.LoopNext)

    @Test
    fun `loop 单曲循环`() = assertRoute("单曲循环", VoiceIntent.LoopNext)

    // 「随机播放」含「播放」，必须仍然算循环
    @Test
    fun `loop 随机播放 不能被当成播放`() = assertRoute("随机播放", VoiceIntent.LoopNext)

    @Test
    fun `loop 英文 shuffle`() = assertRoute("shuffle", VoiceIntent.LoopNext)

    // ---------- 搜索 ----------

    @Test
    fun `search 放某某的歌`() = assertRoute("放周杰伦的歌", VoiceIntent.Search("周杰伦"))

    @Test
    fun `search 播放某某的歌`() = assertRoute("播放周杰伦的歌", VoiceIntent.Search("周杰伦"))

    @Test
    fun `search 来一首`() = assertRoute("来一首 晴天", VoiceIntent.Search("晴天"))

    @Test
    fun `search 搜一下`() = assertRoute("搜一下晴天", VoiceIntent.Search("晴天"))

    @Test
    fun `search 听某某`() = assertRoute("我想听夜曲", VoiceIntent.Search("夜曲"))

    @Test
    fun `search 英文歌名保留词边界`() =
        assertRoute("播放 Taylor Swift 的歌", VoiceIntent.Search("taylor swift"))

    // 「听歌」是动作，不是关键词（否则会去搜一个叫「歌」的东西）
    @Test
    fun `听歌 不算搜索`() = assertRoute("听歌", VoiceIntent.Play)

    // ---------- 归一化 ----------

    @Test
    fun `口语礼貌前缀被剥离`() {
        assertRoute("请帮我下一首", VoiceIntent.Next)
        assertRoute("麻烦你帮我切歌", VoiceIntent.Next)
        assertRoute("请播放", VoiceIntent.Play)
    }

    @Test
    fun `全角字母与标点`() = assertRoute("ＮＥＸＴ！", VoiceIntent.Next)

    @Test
    fun `大小写与中英标点混用`() = assertRoute("Back, please.", VoiceIntent.Previous)

    @Test
    fun `中间插入空格也能认`() = assertRoute("下 一 首", VoiceIntent.Next)

    // ---------- 兜底 ----------

    @Test
    fun `空串归 Unknown 并保留原文`() = assertRoute("", VoiceIntent.Unknown(""))

    @Test
    fun `只有标点归 Unknown`() = assertRoute("！！！", VoiceIntent.Unknown("！！！"))

    @Test
    fun `乱码归 Unknown 并保留原文`() =
        assertRoute("asdfghjkl 这是啥", VoiceIntent.Unknown("asdfghjkl 这是啥"))

    // ---------- 归一化函数本身 ----------

    @Test
    fun `normalize 保留空格但不保留标点`() {
        assertEquals("播放 taylor swift", IntentRouter.normalize("播放，Taylor Swift！"))
    }

    @Test
    fun `compact 去掉所有非字母数字`() {
        assertEquals("下一首", IntentRouter.compact("下 一 首。"))
    }
}
