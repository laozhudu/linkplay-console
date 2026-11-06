package io.github.laozhudu.linkplay.console.bili

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.transformer.Composition
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume

/**
 * B 站音频的 fMP4 → 可播 MP4 重封装。
 *
 * ## 为什么非做不可（`docs/85` §2.3，真机实测不是推测）
 *
 * | 容器 | DOSS 结果 |
 * |---|---|
 * | MP3 | ✅ play |
 * | 普通 M4A（无 `moof`） | ✅ play |
 * | **fMP4 分片（有 `moof`）** | ❌ **stop，totlen=0** |
 *
 * B 站的 DASH 音轨全是 fMP4。直投必然失败 —— 这就是"B 站接不进来"的真正门槛。
 *
 * ## 为什么用 Media3 Transformer 而不是 ffmpeg-kit
 *
 * 官方文档原文：音轨格式不变时 **Transformer 自动走 transmux**
 * （"copying the compressed samples without re-encoding"），正是我们要的。
 *
 * 而 ffmpeg-kit 会让包大 50MB 且是 GPL 变体 —— 项目是 MIT，不能用。
 * Media3 是 Apache-2.0，版本也和现有 media3 1.5.1 对齐。
 *
 * ## 为什么先下载再转，不直接喂 URL
 *
 * B 站直链**有效期 120 分钟**且是分片流。Transformer 直接吃网络 URI 时，
 * 任何一次读超时都会让整个转换失败，而且失败点很难归因。
 * 先落地成本地 cache 文件（几 MB），转换就是纯本地操作，稳定得多。
 */
object Fmp4Remuxer {

    /** remux 结果在缓存目录里的子目录。 */
    private const val DIR = "bili_remux"

    /**
     * 下载 + 重封装。
     *
     * @param audioUrl B 站给的 fMP4 直链（带时效，必须现取现用）
     * @return 可直接投给音箱的 MP4 文件
     */
    suspend fun fetchAndRemux(
        context: Context,
        audioUrl: String,
        cacheKey: String,
    ): File {
        val dir = File(context.cacheDir, DIR).apply { mkdirs() }
        val raw = File(dir, "$cacheKey.fmp4")
        val out = File(dir, "$cacheKey.mp4")

        // 已经有了就直接用（同一首反复点不会重复下载；直链过期也不影响已转好的文件）
        if (out.exists() && out.length() > 0) return out

        download(audioUrl, raw)
        return try {
            transmux(context, raw, out)
            out
        } finally {
            // 中间文件没用了；留着只会占空间（每首 5MB 上下）
            raw.delete()
        }
    }

    /** 下载到本地。B 站直链支持 Range，但这里一次性拿完（几 MB，不做断点）。 */
    private suspend fun download(url: String, target: File) = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                // 实测：B 站直链不需要 Referer、不需要特殊 UA
                instanceFollowRedirects = true
            }
            val code = conn.responseCode
            if (code !in 200..299) error("下载失败：HTTP $code")
            conn.inputStream.use { input ->
                target.outputStream().use { output -> input.copyTo(output, 64 * 1024) }
            }
            if (target.length() == 0L) error("下载下来是空文件")
        } finally {
            conn?.disconnect()
        }
    }

    /**
     * 真正的重封装。Transformer 必须在主线程驱动（它内部要 Looper）。
     *
     * 用 `suspendCancellableCoroutine` 把回调式 API 包成挂起函数，
     * 这样调用方可以顺序写：取流 → 下载 → 转换 → 投送。
     */
    private suspend fun transmux(context: Context, input: File, output: File): Unit =
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                val transformer = Transformer.Builder(context)
                    // 只处理音频：B 站的音轨单独一条，没有视频轨需要丢弃
                    .setAudioMimeType(androidx.media3.common.MimeTypes.AUDIO_AAC)
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, result: ExportResult) {
                            if (cont.isActive) cont.resume(Unit)
                        }

                        override fun onError(
                            composition: Composition,
                            result: ExportResult,
                            exception: ExportException,
                        ) {
                            if (cont.isActive) {
                                cont.resumeWith(
                                    Result.failure(
                                        IllegalStateException(
                                            "重封装失败：${exception.message ?: exception.javaClass.simpleName}",
                                        ),
                                    ),
                                )
                            }
                        }
                    })
                    .build()

                cont.invokeOnCancellation { runCatching { transformer.cancel() } }
                runCatching {
                    transformer.start(MediaItem.fromUri(Uri.fromFile(input)), output.absolutePath)
                }.onFailure {
                    if (cont.isActive) cont.resumeWith(Result.failure(it))
                }
            }
        }

    /** 清掉转好的缓存（设置页可以给一个"清空 B 站缓存"）。 */
    fun clear(context: Context): Int {
        val dir = File(context.cacheDir, DIR)
        val files = dir.listFiles().orEmpty()
        var n = 0
        files.forEach { if (it.delete()) n++ }
        return n
    }
}
