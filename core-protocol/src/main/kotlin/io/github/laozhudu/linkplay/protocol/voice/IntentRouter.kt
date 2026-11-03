package io.github.laozhudu.linkplay.protocol.voice

/**
 * 一句话能表达的全部意图。
 *
 * 为什么用 sealed interface 而不是一个「带 type 字段的 data class」：
 * 分派端（播放页）必须**穷尽**处理所有分支，新增意图时编译器会直接报错，
 * 而不是在运行时静默落到 else 分支里变成「说了没反应」。
 */
sealed interface VoiceIntent {
    data object Next : VoiceIntent
    data object Previous : VoiceIntent
    data object Play : VoiceIntent
    data object Pause : VoiceIntent
    data object Stop : VoiceIntent

    /** 「播放/暂停」取决于当前状态，所以不拆成两种，交给调用方按播放状态决定。 */
    data object TogglePlay : VoiceIntent
    data object VolumeUp : VoiceIntent
    data object VolumeDown : VoiceIntent
    data object Mute : VoiceIntent

    /** 循环模式切下一个（与播放页按钮同一个动作，含单曲/列表/随机）。 */
    data object LoopNext : VoiceIntent

    /** 想找某首/某人的歌：关键词交给搜索，本文不决定从哪个曲库搜。 */
    data class Search(val keyword: String) : VoiceIntent

    /** 没听懂。**带上原文**是为了能在界面上原样回显，让用户知道我们听到了什么。 */
    data class Unknown(val text: String) : VoiceIntent
}

/**
 * 中文口语命令词 → [VoiceIntent]。
 *
 * ## 为什么规则写在这里、而不是放进 app 层
 * 这是纯字符串逻辑，与 Android 语音识别无关：放进 core-protocol 就能被单测锁死，
 * 改词表时不用装机验证。识别引擎（系统/云端）换掉也不影响这一层。
 *
 * ## 为什么「宽松匹配」而不是正则全匹配
 * 系统语音识别的输出是**不稳定的**：同一句话可能带标点、带「请/帮我」、
 * 或把数字识别成中文。真实用户也不会按说明书说话（「不要这首，换一个」）。
 * 所以策略是：先归一化，再按优先级做**包含匹配**，宁可多认一种说法，
 * 也不要让用户对着音箱重复三遍。
 *
 * 优先级是刻意排的，几个必须记住的坑：
 * - 「播放暂停」里含「暂停」，所以 TogglePlay 在 Pause 之前；
 * - 「停一下」里含「停」，所以 Pause 在 Stop 之前；
 * - 「随机播放」里含「播放」，所以 LoopNext 在 Play 之前；
 * - 「播放周杰伦」是搜索而不是播放，所以**搜索只在句首动词后面取内容**，
 *   而 Play 只接受「整句就是播放」这一种形态（见 [playOnly]）。
 */
object IntentRouter {

    /** 口语礼貌前缀。反复剥离（「麻烦你帮我」要剥两次）才算干净。 */
    private val POLITE_PREFIXES = listOf(
        "麻烦你", "麻烦", "请帮我", "请", "帮我", "帮忙", "给我",
        "我想", "我要", "想要", "能不能", "可以", "麻烦您",
    )

    private val TOGGLE_PLAY = listOf("播放暂停", "暂停播放", "切换播放", "toggleplay")

    private val LOOP_NEXT = listOf(
        "循环模式", "换循环", "单曲循环", "列表循环", "随机播放", "切换循环", "循环", "shuffle",
    )

    private val NEXT = listOf(
        "下一首", "下一个", "下一条", "换一首", "换一首歌", "换歌", "跳过", "不要这首", "切歌", "next", "skip",
    )

    private val PREVIOUS = listOf(
        "回到上一首", "上一首", "上一个", "上一条", "换回上一首", "previous", "prev", "back",
    )

    private val VOLUME_UP = listOf(
        "大声点", "大点声", "大声一点", "声音大一点", "声音大点", "音量加", "音量调大",
        "把声音调大", "调大", "volup", "volumeup",
    )

    private val VOLUME_DOWN = listOf(
        "小声点", "小点声", "小声一点", "声音小一点", "声音小点", "音量减", "音量调小",
        "把声音调小", "调小", "voldown", "volumedown",
    )

    private val MUTE = listOf("静音", "别出声", "不要出声", "mute")

    private val PAUSE = listOf("暂停", "停一下", "pause")

    private val STOP = listOf("停止", "停", "别放了", "关掉音乐", "关掉", "stop")

    /**
     * 「整句就是在说播放」。**只做等值比较**，不做包含比较 ——
     * 否则「播放周杰伦」会被当成 Play，用户永远搜不到歌。
     */
    private val PLAY_ONLY = setOf(
        "播放", "开始播", "开始播放", "继续", "继续播放", "继续播", "接着放", "接着播放",
        "放", "播", "播放音乐", "播放歌曲", "听歌", "放歌", "play", "resume",
        "来一首歌", "播放一首歌", "放一首歌", "播放点音乐",
    )

    /** 句首的「搜索动词」：命中后紧跟的内容才是关键词。长词在前，避免被短词截断。 */
    private val SEARCH_VERBS = listOf(
        "搜索一下", "搜一下", "搜索", "搜", "来一首歌", "来一首", "来首", "想听",
        "播放一首", "播放", "放一下", "放一首", "放", "播一首", "播", "听",
    )

    /** 关键词尾缀：「周杰伦的歌」→「周杰伦」。同样从长到短、反复剥离。 */
    private val KEYWORD_SUFFIXES = listOf("的歌曲", "的歌", "歌曲", "的音乐", "音乐", "的", "歌")

    /**
     * 「歌」本身不是关键词。
     *
     * 没有这一层，「听歌」「放歌」「播放音乐」都会被解析成 Search("歌") ——
     * 用户想听音乐，我们却丢进搜索框里找一个叫「歌」的东西。
     */
    private val GENERIC_KEYWORDS = setOf("歌", "歌曲", "音乐", "一首歌", "首歌", "点歌")


    /** 语气词/量词尾巴：「播放吧」「继续一下」。 */
    private val TAIL_FILLERS = listOf("吧", "啊", "呀", "呢", "嘛", "一下", "一点", "一些", "好的", "好吗")

    fun route(text: String): VoiceIntent {
        val spoken = normalize(text)
        if (spoken.isBlank()) return VoiceIntent.Unknown(text)
        // 剥前缀之前先留着「带空格」的版本给关键词用：英文歌名里空格是词边界，不能删
        val body = stripPolitePrefixes(spoken)
        val compact = compact(body)
        if (compact.isEmpty()) return VoiceIntent.Unknown(text)

        // 顺序即优先级，理由见类注释里的「几个必须记住的坑」
        if (TOGGLE_PLAY.any { compact.contains(it) }) return VoiceIntent.TogglePlay
        if (LOOP_NEXT.any { compact.contains(it) }) return VoiceIntent.LoopNext
        if (NEXT.any { compact.contains(it) }) return VoiceIntent.Next
        if (PREVIOUS.any { compact.contains(it) }) return VoiceIntent.Previous
        if (VOLUME_UP.any { compact.contains(it) }) return VoiceIntent.VolumeUp
        if (VOLUME_DOWN.any { compact.contains(it) }) return VoiceIntent.VolumeDown
        if (MUTE.any { compact.contains(it) }) return VoiceIntent.Mute

        // 搜索必须在 Play 之前：「播放周杰伦」是找歌，不是「按播放键」
        searchKeyword(body)?.let { return VoiceIntent.Search(it) }

        if (PAUSE.any { compact.contains(it) }) return VoiceIntent.Pause
        if (STOP.any { compact.contains(it) }) return VoiceIntent.Stop
        if (playOnly(compact)) return VoiceIntent.Play

        return VoiceIntent.Unknown(text)
    }

    /**
     * 把关键词从「放<X>的歌」这类句子里挖出来；挖不到就返回 null（交由其它规则处理）。
     *
     * 先在**保留空格**的串上切动词：英文歌名（「播放 taylor swift」）的空格是词边界，
     * 压缩掉就成了一个不存在的词。识别结果里偶尔会在动词中间插空格，所以退一步再用紧凑串试一次。
     */
    fun searchKeyword(text: String): String? {
        val body = stripPolitePrefixes(normalize(text))
        val spaced = cutVerb(body) ?: cutVerb(compact(body)) ?: return null
        var keyword = spaced.trim()
        // 「放一下周杰伦」：动词后面常挂一个量词，它不是歌名的一部分
        keyword = keyword.removePrefix("一下").removePrefix("一首").removePrefix("首").trim()
        keyword = stripSuffixes(keyword, KEYWORD_SUFFIXES).trim()
        // 语气词允许剥空（「播放吧」里「吧」不是关键词），尾缀不允许
        keyword = stripSuffixes(keyword, TAIL_FILLERS, allowEmpty = true).trim()
        if (keyword in GENERIC_KEYWORDS) return null
        return keyword.ifBlank { null }
    }

    private fun cutVerb(body: String): String? {
        val verb = SEARCH_VERBS.firstOrNull { body.startsWith(it) } ?: return null
        return body.removePrefix(verb)
    }

    /**
     * 归一化：全角转半角、英文小写、标点转空格、压缩空白。
     *
     * 不做的事：**不删空格**。英文/拼音关键词（「taylor swift」「zjl 夜曲」）
     * 靠空格分词，这里删掉就再也拼不回去了；需要紧凑串的地方用 [compact]。
     */
    fun normalize(text: String): String {
        val half = StringBuilder(text.length)
        for (ch in text) {
            half.append(
                when {
                    // 全角 ASCII（！到 ～）整体平移 0xFEE0 就是对应半角
                    ch.code in 0xFF01..0xFF5E -> (ch.code - 0xFEE0).toChar()
                    ch == '\u3000' -> ' '          // 全角空格
                    else -> ch
                },
            )
        }
        val out = StringBuilder(half.length)
        for (ch in half) {
            when {
                ch.isLetterOrDigit() -> out.append(ch.lowercaseChar())
                // 标点统一变成空格而不是直接删：英文里它常常是词边界
                // （「播放，taylor swift」删掉标点会把动词和歌名粘在一起），
                // 中文没空格也无所谓，后面 [compact] 本来就会把空格去掉。
                else -> out.append(' ')
            }
        }
        return out.toString().trim().replace(Regex(" {2,}"), " ")
    }

    /** 去掉空格后的紧凑串，用于「包含」判断（「下 一 首」也要能认）。 */
    fun compact(text: String): String = text.filter { it.isLetterOrDigit() }

    private fun stripPolitePrefixes(text: String): String {
        var s = text
        var changed = true
        while (changed) {
            changed = false
            for (p in POLITE_PREFIXES) {
                if (s.startsWith(p)) {
                    s = s.removePrefix(p).trimStart()
                    changed = true
                }
            }
        }
        return s
    }

    private fun playOnly(compact: String): Boolean {
        var s = compact
        // 「播放吧」「继续一下」：语气词不算内容
        var stripped = true
        while (stripped) {
            stripped = false
            for (t in TAIL_FILLERS) {
                if (s.endsWith(t) && s.length > t.length) {
                    s = s.removeSuffix(t)
                    stripped = true
                }
            }
        }
        return s in PLAY_ONLY
    }

    private fun stripSuffixes(text: String, suffixes: List<String>, allowEmpty: Boolean = false): String {
        var s = text
        var stripped = true
        while (stripped) {
            stripped = false
            for (suffix in suffixes) {
                // 默认不允许把整个词剥空：否则「放首歌」会变成空关键词
                if (s.endsWith(suffix) && (allowEmpty || s.length > suffix.length)) {
                    s = s.removeSuffix(suffix)
                    stripped = true
                }
            }
        }
        return s
    }
}
