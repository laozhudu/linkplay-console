package io.github.laozhudu.linkplay.console.playback

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import io.github.laozhudu.linkplay.console.MainActivity
import io.github.laozhudu.linkplay.console.session.SessionStore
import io.github.laozhudu.linkplay.protocol.session.PlaybackSession
import io.github.laozhudu.linkplay.protocol.upnp.UpnpRendererClient
import io.github.laozhudu.linkplay.protocol.queue.ClientSideQueue
import io.github.laozhudu.linkplay.protocol.queue.QueueItem
import io.github.laozhudu.linkplay.protocol.queue.QueueTarget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 队列编排的前台宿主。
 *
 * 为什么必须挪进服务：编排是一个持续几分钟到几小时的循环，原来挂在 `NasViewModel` 上，
 * 用户一退出页面 ViewModel 就被清、循环中断——表现就是「锁屏几分钟后音乐停了」。
 * 前台服务 + 通知是 Android 8 之后唯一能稳定跑后台循环的方式
 * （Android 14 还要 `foregroundServiceType`，这里用 `dataSync`，与既有 `MediaServerService` 一致）。
 *
 * 为什么不在这里做「控制面自愈」的自动重启：重启音箱会中断当前播放约 40 秒，
 * 静默重启比直接告诉用户更糟。这里只把 `controlLost` 报给 UI，由用户决定。
 */
class QueuePlaybackService : Service() {

    private var scope: CoroutineScope? = null
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopQueue()
                return START_NOT_STICKY
            }
        }

        val ip = intent?.getStringExtra(EXTRA_IP)
        val location = intent?.getStringExtra(EXTRA_LOCATION)
        val uuid = intent?.getStringExtra(EXTRA_UUID).orEmpty()
        val loopMode = intent?.getIntExtra(EXTRA_LOOP, 2) ?: 2
        val payload = intent?.getStringExtra(EXTRA_ITEMS)
        val session = PlaybackSession.decode(payload)
        if (ip.isNullOrBlank() || session == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, buildNotification(session.items.firstOrNull()?.title))

        // 重复 start 时先把上一轮停掉，避免两个循环同时往音箱投流
        job?.cancel()
        val sc = CoroutineScope(SupervisorJob() + Dispatchers.IO).also { scope = it }
        job = sc.launch { runQueue(ip, location, uuid, loopMode, session.items) }
        return START_NOT_STICKY
    }

    private suspend fun runQueue(
        ip: String,
        location: String?,
        uuid: String,
        loopMode: Int,
        items: List<QueueItem>,
    ) {
        QueueBus.begin(items)
        val sessions = SessionStore.of(this)
        try {
            val client = describeClient(ip, location)
            val queue = ClientSideQueue(
                target = object : QueueTarget {
                    override fun play(item: QueueItem) {
                        client.setAvTransportUri(item.url, item.title, item.mimeType)
                        client.play()
                        val index = items.indexOfFirst { it.url == item.url }.coerceAtLeast(0)
                        QueueBus.playing(item.title, index)
                        // 每切一首就更新快照：被杀进程时最多丢一首的进度
                        runCatching {
                            sessions.savePlayback(
                                uuid.ifBlank { ip },
                                PlaybackSession(uuid.ifBlank { ip }, ip, items, index, loopMode),
                            )
                        }
                        updateNotification(item.title, index + 1, items.size)
                    }

                    override fun position(): Pair<Long, Long>? = runCatching {
                        val p = client.positionInfo()
                        p.relTimeSec to p.trackDurationSec
                    }.getOrNull()

                    override fun transportState(): String =
                        runCatching { client.transportInfo().state.wire }.getOrDefault("UNKNOWN")
                },
            )
            queue.loopMode = loopMode
            val played = queue.run(items = items, shouldStop = { job?.isCancelled == true })

            val dropped = played < items.size && job?.isCancelled != true
            if (!dropped) {
                // 正常播完清快照，否则下次进页面还会提示「继续上次」
                runCatching { sessions.clearPlayback(uuid.ifBlank { ip }) }
            }
            QueueBus.finish(controlLost = dropped, log = QueueBus.state.value.log)
            QueueBus.appendLog(if (dropped) "提前结束：只播了 $played/${items.size} 首" else "结束，共播 $played 首")
        } catch (t: Throwable) {
            QueueBus.fail(t.message ?: "推送失败")
        } finally {
            stopSelf()
        }
    }

    private fun describeClient(ip: String, location: String?): UpnpRendererClient {
        if (!location.isNullOrBlank()) {
            val desc = runCatching { UpnpRendererClient.describe(location) }.getOrNull()
            if (desc?.avTransportControlUrl != null) return UpnpRendererClient(desc)
        }
        return UpnpRendererClient.ofDevice(ip)
    }

    private fun stopQueue() {
        job?.cancel()
        QueueBus.stopped()
        stopSelf()
    }

    override fun onDestroy() {
        scope?.cancel()
        scope = null
        super.onDestroy()
    }

    // ---- 通知 ----

    private fun buildNotification(title: String?) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("正在播放到音箱")
        .setContentText(title ?: "准备中…")
        .setSmallIcon(android.R.drawable.ic_media_play)
        .setOngoing(true)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setContentIntent(
            PendingIntent.getActivity(
                this, 0, Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ),
        )
        .addAction(0, "停止", stopIntent())
        .build()

    private fun updateNotification(title: String, index: Int, total: Int) {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        nm.notify(
            NOTIFICATION_ID,
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("正在播放 $index/$total")
                .setContentText(title)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .addAction(0, "停止", stopIntent())
                .build(),
        )
    }

    private fun stopIntent(): PendingIntent = PendingIntent.getService(
        this, 1, Intent(this, QueuePlaybackService::class.java).setAction(ACTION_STOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    companion object {
        private const val CHANNEL_ID = "queue-playback"
        private const val NOTIFICATION_ID = 43
        private const val ACTION_STOP = "io.github.laozhudu.linkplay.console.STOP_QUEUE"
        private const val EXTRA_IP = "ip"
        private const val EXTRA_LOCATION = "location"
        private const val EXTRA_UUID = "uuid"
        private const val EXTRA_LOOP = "loop"
        private const val EXTRA_ITEMS = "items"

        /**
         * 开始（或替换）播放队列。
         *
         * @param session 已编好码的快照：服务端直接用它跑，也顺手作为灾备记录
         */
        fun start(context: Context, location: String?, session: PlaybackSession) {
            ensureChannel(context)
            val intent = Intent(context, QueuePlaybackService::class.java).apply {
                putExtra(EXTRA_IP, session.deviceIp)
                putExtra(EXTRA_LOCATION, location)
                putExtra(EXTRA_UUID, session.deviceUuid)
                putExtra(EXTRA_LOOP, session.loopMode)
                putExtra(EXTRA_ITEMS, session.encode())
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, QueuePlaybackService::class.java).setAction(ACTION_STOP),
            )
        }

        private fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            if (nm.getNotificationChannel(CHANNEL_ID) != null) return
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "队列播放", NotificationManager.IMPORTANCE_LOW),
            )
        }
    }
}
