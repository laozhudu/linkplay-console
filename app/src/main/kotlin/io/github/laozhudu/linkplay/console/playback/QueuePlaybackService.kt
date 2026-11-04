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
import io.github.laozhudu.linkplay.protocol.session.PlaybackKey
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

    /** 当前这一轮的参数与队列，skipTo / removeAt 需要用它重启一次编排。 */
    @Volatile private var curIp: String = ""
    @Volatile private var curLocation: String? = null
    @Volatile private var curUuid: String = ""
    @Volatile private var curLoop: Int = 2
    @Volatile private var curItems: List<QueueItem> = emptyList()

    /** 队列操作面：播放页的「播放列表」点某首/删某首都走这里。 */
    private val controller = object : QueueController {
        override fun skipTo(index: Int) {
            val items = curItems
            if (items.isEmpty() || index !in items.indices) return
            // 直接跳到目标位置：取消当前循环，从剩余部分重新编排。
            // 比给 ClientSideQueue 内部加游标更简单，而重投一次流本来就是设备的正常操作。
            restart(items, index)
        }

        override fun removeAt(index: Int) {
            val items = curItems
            if (items.isEmpty() || index !in items.indices) return
            val next = items.toMutableList().apply { removeAt(index) }
            if (next.isEmpty()) {
                stopQueue()
                // 带归属清空：只清这台设备的队列
                QueueBus.clear(curIp)
                return
            }
            // 删掉的是当前曲或更前面的曲：从当前位置（或删后同位）继续；否则只更新列表
            val target = when {
                index < QueueBus.state.value.index -> QueueBus.state.value.index - 1
                index == QueueBus.state.value.index -> index
                else -> -1
            }
            curItems = next
            QueueBus.playing(
                curIp,
                next.getOrNull(QueueBus.state.value.index)?.title ?: "",
                QueueBus.state.value.index.coerceAtLeast(0),
                next,
            )
            if (target >= 0) restart(next, target.coerceIn(0, next.size - 1))
        }
    }

    private fun restart(items: List<QueueItem>, fromIndex: Int) {
        job?.cancel()
        val sc = scope ?: CoroutineScope(SupervisorJob() + Dispatchers.IO).also { scope = it }
        job = sc.launch {
            // 从 fromIndex 开始跑；QueueBus 的 index 以原队列为准，所以这里单独记账
            runQueue(curIp, curLocation, curUuid, curLoop, items.drop(fromIndex), baseIndex = fromIndex)
        }
    }

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
        curIp = ip
        curLocation = location
        curUuid = uuid
        curLoop = loopMode
        curItems = session.items
        QueueBus.bind(controller)
        val sc = CoroutineScope(SupervisorJob() + Dispatchers.IO).also { scope = it }
        // 从快照里的 index 起播：用户点的是第 5 首，就该从第 5 首开始，
        // 而不是笨拙地把前 4 首也「过」一遍（那会白投 4 次流，还会出现前奏闪一下）
        val start = session.index.coerceIn(0, session.items.lastIndex.coerceAtLeast(0))
        job = sc.launch {
            runQueue(ip, location, uuid, loopMode, session.items.drop(start), baseIndex = start)
        }
        return START_NOT_STICKY
    }

    /**
     * @param items 从当前要播的那首开始（重启时是 `drop(fromIndex)`）
     * @param baseIndex 这些 item 在原队列里的起始下标，用于 QueueBus 显示「第几首」
     */
    private suspend fun runQueue(
        ip: String,
        location: String?,
        uuid: String,
        loopMode: Int,
        items: List<QueueItem>,
        baseIndex: Int = 0,
    ) {
        if (baseIndex == 0) {
            QueueBus.begin(ip, items)
        } else {
            QueueBus.playing(ip, items.firstOrNull()?.title ?: "", baseIndex, curItems)
        }
        val sessions = SessionStore.of(this)
        try {
            val client = describeClient(ip, location)
            val queue = ClientSideQueue(
                target = object : QueueTarget {
                    override fun play(item: QueueItem) {
                        client.setAvTransportUri(item.url, item.title, item.mimeType)
                        client.play()
                        val index = baseIndex + items.indexOfFirst { it.url == item.url }.coerceAtLeast(0)
                        QueueBus.playing(ip, item.title, index, curItems)
                        // 每切一首就更新快照：被杀进程时最多丢一首的进度
                        runCatching {
                            sessions.savePlayback(
                                PlaybackKey.of(uuid, ip),
                                PlaybackSession(uuid.ifBlank { ip }, ip, curItems, index, loopMode),
                            )
                        }
                        updateNotification(item.title, index + 1, curItems.size.coerceAtLeast(items.size))
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
        QueueBus.bind(null)
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
