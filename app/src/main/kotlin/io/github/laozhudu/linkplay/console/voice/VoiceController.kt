package io.github.laozhudu.linkplay.console.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * 系统语音识别的最小封装（`SpeechRecognizer` + `ACTION_RECOGNIZE_SPEECH`）。
 *
 * ## 为什么不用云端 SDK
 * 这个 App 的全部通信都在局域网里（音箱 httpapi/UPnP，媒体在自己的 NAS 上），
 * 引一个云端语音 SDK 等于为了「下一首」三个字把麦克风音频送出去，
 * 还要多一份隐私声明和一套账号体系。系统识别由用户在系统设置里自己挑引擎
 * （Google/厂商/离线包都行），我们不绑定任何一家。
 *
 * ## 线程约束（踩坑点）
 * `SpeechRecognizer` 必须在**主线程**创建、`startListening`、`destroy`，
 * 否则回调不会到达（或直接抛异常）。所以这里所有入口都切到主线程，
 * 回调也统一 `Handler(mainLooper).post` 出去 —— 调用方（Compose）可以放心地直接改 UI 状态。
 *
 * ## 为什么要 session 号
 * 用户可能连点两下（开始 → 取消 → 开始）。系统识别在取消后仍可能把上一轮的结果送回来，
 * 若不加区分，界面会「第二次刚点开就被第一轮的结果关掉」。所以每次 start 把 session +1，
 * 回调里 session 不一致就丢弃。
 */
class VoiceController(private val context: Context) {

    private val main = Handler(Looper.getMainLooper())

    private var recognizer: SpeechRecognizer? = null
    private var onResult: ((String) -> Unit)? = null
    private var onError: ((String) -> Unit)? = null
    private var session = 0

    /** 正在听。界面据此把按钮显示成「…」。 */
    var listening: Boolean = false
        private set

    /**
     * 开始听一次。
     *
     * 回调**一定**在主线程触发，且一次 start 只会回调其中一个（成功或失败）。
     */
    fun start(onResult: (String) -> Unit, onError: (String) -> Unit) {
        if (!available(context)) {
            onError(MSG_UNAVAILABLE)
            return
        }
        // 上一轮还没收尾就再 start，系统会回 ERROR_RECOGNIZER_BUSY，表现是「点了没反应」
        stopInternal()
        this.onResult = onResult
        this.onError = onError
        val token = ++session

        val r = runCatching { SpeechRecognizer.createSpeechRecognizer(context) }.getOrNull()
        if (r == null) {
            fail(MSG_UNAVAILABLE, token)
            return
        }
        recognizer = r
        r.setRecognitionListener(Listener(token))
        listening = true
        // 某些 ROM 上 startListening 会同步抛（例如识别服务被冻结），不能让它崩掉界面
        runCatching { r.startListening(buildIntent()) }.onFailure { fail(MSG_FAILED, token) }
    }

    /** 取消本次识别（用户再点一下就是取消）。不回调结果。 */
    fun cancel() {
        session++
        stopInternal()
    }

    /** 页面销毁时调用：释放识别服务，否则会持有 Context 与麦克风。 */
    fun destroy() {
        session++
        stopInternal()
    }

    private fun stopInternal() {
        listening = false
        onResult = null
        onError = null
        val r = recognizer ?: return
        recognizer = null
        runCatching { r.cancel() }
        runCatching { r.destroy() }
    }

    private fun buildIntent(): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        // 强制中文：设备默认语言是英文时，不指定会把「下一首」识别成拼音/英文
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, LANG)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, LANG)
        // 只在部分引擎上生效，但能生效的那些会明显更快给出单句结果
        putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, LANG)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        // 口令都很短（「下一首」一秒说完），默认的静音等待太长，用户会以为没听见
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1_200L)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 800L)
    }

    private fun succeed(text: String, token: Int) {
        if (token != session) return
        val cb = onResult
        listening = false
        // 识别服务已经没用了：留着它会在下一次 create 时抢麦克风
        releaseRecognizer()
        onResult = null
        onError = null
        if (cb != null) main.post { cb(text) }
    }

    private fun fail(message: String, token: Int) {
        if (token != session) return
        val cb = onError
        listening = false
        releaseRecognizer()
        onResult = null
        onError = null
        if (cb != null) main.post { cb(message) }
    }

    private fun releaseRecognizer() {
        val r = recognizer ?: return
        recognizer = null
        runCatching { r.cancel() }
        runCatching { r.destroy() }
    }

    private inner class Listener(private val token: Int) : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit

        override fun onError(error: Int) = fail(messageFor(error), token)

        override fun onPartialResults(partialResults: Bundle?) = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit

        override fun onResults(results: Bundle?) {
            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                ?.trim()
                .orEmpty()
            if (text.isEmpty()) {
                // 「听到了但没解出字」对用户来说和没听清是同一件事，不该分两种说法
                fail(MSG_NOT_HEARD, token)
            } else {
                succeed(text, token)
            }
        }
    }

    companion object {
        private const val LANG = "zh-CN"

        /** 没识别到任何字。对用户就是「再说一次」。 */
        const val MSG_NOT_HEARD = "没听清，再说一次"

        /** 本机没有可用的识别服务（未装语音服务、被禁用、或系统裁剪过）。 */
        const val MSG_UNAVAILABLE = "本机没有语音识别"
        const val MSG_NO_PERMISSION = "没有麦克风权限，语音用不了"
        const val MSG_FAILED = "语音识别没启动，稍后再试"

        /**
         * 是否有可用的识别服务。
         *
         * 不要直接用 `SpeechRecognizer.isRecognitionAvailable` 裸调：部分定制 ROM 上
         * 会在识别服务被禁用时抛异常，那样界面还没画出来就崩了。
         */
        fun available(context: Context): Boolean =
            runCatching { SpeechRecognizer.isRecognitionAvailable(context) }.getOrDefault(false)

        /** 系统错误码 → 一句人话。原始错误码只适合进日志，用户看不懂。 */
        fun messageFor(error: Int): String = when (error) {
            SpeechRecognizer.ERROR_NO_MATCH,
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
            -> MSG_NOT_HEARD

            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> MSG_NO_PERMISSION
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "识别还没结束，等一下再说"
            SpeechRecognizer.ERROR_NETWORK,
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
            -> "语音识别需要联网，检查一下网络"

            SpeechRecognizer.ERROR_CLIENT -> MSG_FAILED
            SpeechRecognizer.ERROR_SERVER,
            SpeechRecognizer.ERROR_SERVER_DISCONNECTED,
            -> "语音服务没响应，稍后再试"

            SpeechRecognizer.ERROR_AUDIO -> "录不到声音，检查一下麦克风"
            else -> MSG_FAILED
        }
    }
}
