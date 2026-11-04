package io.github.laozhudu.linkplay.console.library

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import io.github.laozhudu.linkplay.protocol.media.LocalMediaServer
import java.io.File

/**
 * 本地媒体 HTTP 服务的前台宿主。
 *
 * 为什么要前台服务：音箱在播放期间会持续向手机拉流，进程一旦被系统回收，音乐就断，
 * 而且 Android 8+ 对后台进程限制很严。前台服务 + 通知是唯一可靠的做法
 * （Android 14 起还要求声明 `foregroundServiceType`，这里用 `dataSync` —— 它正好
 * 表示「在后台传数据」这类用途）。
 */
class MediaServerService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        val tracks = LocalMediaRegistry.tracks
        if (tracks.isEmpty()) {
            // 没有曲目就没必要占着端口
            stopSelf()
            return START_NOT_STICKY
        }
        LocalMediaRegistry.ensureServer(this, tracks)
        return START_STICKY
    }

    override fun onDestroy() {
        LocalMediaRegistry.stopServer()
        super.onDestroy()
    }

    private fun buildNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("本地音乐共享中")
        .setContentText("音箱正在通过本机 HTTP 服务拉取音乐")
        .setSmallIcon(android.R.drawable.ic_media_play)
        .setOngoing(true)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .build()

    companion object {
        private const val CHANNEL_ID = "local-media-server"
        private const val NOTIFICATION_ID = 42

        fun start(context: Context) {
            ensureChannel(context)
            val intent = Intent(context, MediaServerService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MediaServerService::class.java))
        }

        private fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            if (manager.getNotificationChannel(CHANNEL_ID) != null) return
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "本地媒体服务", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "向局域网音箱提供本地音乐文件"
                },
            )
        }
    }
}

/**
 * 进程级单例：持有当前曲目与正在运行的服务实例。
 *
 * 用单例而非绑定服务，是因为 ViewModel 不该持有 Service 引用（易泄漏），
 * 而端口与文件映射又必须跨组件共享。
 */
object LocalMediaRegistry {

    @Volatile
    var tracks: List<LocalTrack> = emptyList()

    private var server: LocalMediaServer? = null

    @Volatile
    var port: Int = -1
        private set

    fun ensureServer(context: Context, tracks: List<LocalTrack>): Int {
        this.tracks = tracks
        val existing = server
        if (existing != null && existing.port > 0) return existing.port
        val created = LocalMediaServer(
            resolver = LocalLibrary.resolver(tracks),
            advertiseHost = localIpAddress(),
        )
        port = created.start()
        server = created
        return port
    }

    fun stopServer() {
        server?.stop()
        server = null
        port = -1
    }

    fun isRunning(): Boolean = server != null && port > 0

    /**
     * 当前服务器实例（没在跑返回 null）。
     *
     * 给诊断页读推流统计用：只告诉用户"服务在跑"没法解释"没声音"，
     * 得知道请求有没有进来、发出去多少、是不是被断了。
     */
    fun mediaServer(): LocalMediaServer? = server

    /** 取本机在当前网络下的 IPv4 地址（音箱要能访问到，所以不能用 127.0.0.1）。 */
    /** 兼容旧调用点；实现已移到 [io.github.laozhudu.linkplay.console.util.Net]。 */
    fun localIpAddress(): String? = io.github.laozhudu.linkplay.console.util.Net.localIpv4()

    fun baseUrl(): String? {
        val ip = localIpAddress() ?: return null
        val p = port
        if (p <= 0) return null
        return "http://$ip:$p"
    }

    fun fileFor(id: Long): File? = tracks.firstOrNull { it.id == id }?.let { File(it.path) }
}
