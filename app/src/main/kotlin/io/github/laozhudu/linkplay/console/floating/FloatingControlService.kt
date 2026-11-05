package io.github.laozhudu.linkplay.console.floating

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import io.github.laozhudu.linkplay.console.MainActivity
import io.github.laozhudu.linkplay.console.playback.QueueBus
import io.github.laozhudu.linkplay.protocol.DeviceHttpClient
import io.github.laozhudu.linkplay.protocol.playback.PlayIntent
import io.github.laozhudu.linkplay.protocol.floating.FloatSpec
import io.github.laozhudu.linkplay.protocol.floating.FloatingLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 桌面悬浮控制条（原厂 `FloatingWindowService` 的等价实现）。
 *
 * ## 为什么是服务而不是一个 Compose 悬浮层
 *
 * 用户要的是「在桌面/别的 App 上也能控播放」。Compose 只能活在 Activity 的窗口里，
 * 一退到桌面就没了。`WindowManager` + `TYPE_APPLICATION_OVERLAY` 才是能盖在桌面上那一层，
 * 而它必须由**有通知的前台服务**持有 —— 否则系统会在几分钟内把进程回收，悬浮窗随之消失。
 *
 * ## 为什么不依赖 ViewModel
 *
 * 服务与 Activity 是两条生命周期：用户在桌面时 Activity 可能已经销毁。
 * 所以这里只读两处全局状态（[QueueBus.state] 的曲名、[QueueBus.owner] 的设备地址），
 * 按钮直接向设备发 httpapi 命令。命令与播放页用的是同一组
 * （`setPlayerCmd:onepause` / `next` / `prev`），设备侧看到的没有区别。
 *
 * 已知边界（如实写在界面与注释里，不假装能控制）：没有「当前设备」时按钮置灰并显示
 * 「未在播放」—— 悬浮窗最怕的就是点了没反应还看不出为什么。
 */
class FloatingControlService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** 唯一执行点。懒初始化：服务被系统拉起但用户从没点过时不必建它。 */
    private val dispatcher by lazy {
        io.github.laozhudu.linkplay.console.playback.PlaybackDispatcher(application)
    }

    /** 只为发三条播放命令，超时给短一点：网络不通时不能让按钮「看起来点了没反应」。 */
    private val http = DeviceHttpClient(connectTimeoutMs = 3_000, readTimeoutMs = 3_000)

    private var windowManager: WindowManager? = null
    private var root: View? = null
    private var params: WindowManager.LayoutParams? = null

    private var titleView: TextView? = null
    private var prevView: TextView? = null
    private var playView: TextView? = null
    private var nextView: TextView? = null

    /** 窗口当前的几何（px）。拖动时用它做增量位移。 */
    private var spec = FloatSpec(0, 0, 0, 0)

    /** 从设置页带进来的兜底设备地址：播放页没打开过时 [QueueBus.owner] 是 null。 */
    private var fallbackIp: String? = null

    /** 最近一次触摸的原始坐标，用于算拖动增量。 */
    private var downRawX = 0f
    private var downRawY = 0f
    private var downSpecX = 0
    private var downSpecY = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            FloatingSourceStatus.stopped("用户关闭")
            stopSelf()
            return START_NOT_STICKY
        }
        fallbackIp = intent?.getStringExtra(EXTRA_IP) ?: fallbackIp

        // startForeground 必须在 5 秒内调用，否则系统直接 ANR/杀进程 —— 放在最前面
        startForeground(NOTIFICATION_ID, buildNotification())

        FloatingSourceStatus.started(targetIp())
        if (root == null) addOverlay()
        observeQueue()
        return START_STICKY
    }

    /** 目标设备：优先「播放页正在用的那台」，其次设置页带进来的那台。 */
    private fun targetIp(): String? = QueueBus.owner() ?: fallbackIp

    // ---- 悬浮窗 ----

    private fun addOverlay() {
        val wm = getSystemService(WindowManager::class.java)
        windowManager = wm

        // 没有权限时加窗会抛 BadTokenException。这里直接放弃并把自己停掉：
        // 让设置页的开关回到「关」，用户看到的就是「没开」，而不是一个假装开着却什么都没出现的开关。
        if (!canDrawOverlays(this)) {
            FloatingSourceStatus.permission(false)
            FloatingSourceStatus.failed(
                io.github.laozhudu.linkplay.console.diagnostics.SourceFailure.PERMISSION_DENIED,
                "没有 SYSTEM_ALERT_WINDOW 权限，加窗会被系统拒（BadTokenException）",
            )
            stopSelf()
            return
        }
        FloatingSourceStatus.permission(true)

        val metrics = resources.displayMetrics
        val width = dp(200)
        val height = dp(56)
        val edge = dp(FloatingLayout.EDGE)
        // 初始位置：右侧、离顶约四成高 —— 避开状态栏与常见 Dock 区域，也不挡正在看的内容
        spec = FloatingLayout.clamp(
            FloatSpec(
                x = metrics.widthPixels - width - edge,
                y = (metrics.heightPixels * 0.4f).toInt(),
                width = width,
                height = height,
            ),
            metrics.widthPixels,
            metrics.heightPixels,
            edge,
        )

        val bar = buildBar()
        val lp = WindowManager.LayoutParams(
            spec.width,
            spec.height,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            android.graphics.PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = spec.x
            y = spec.y
        }
        params = lp

        runCatching { wm.addView(bar, lp) }.onFailure { error ->
            // 有的 ROM 即使 canDrawOverlays 为 true 也会拒绝，绝不因此崩溃
            FloatingSourceStatus.addFailed(error)
            stopSelf()
            return
        }
        root = bar
        // 记下真实几何：宽高为 0 时窗口加了也看不见，这是"有条但没显示"的唯一证据
        FloatingSourceStatus.added(spec.width, spec.height, spec.x, spec.y, targetIp())
        render()
    }

    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            // API 24/25 没有 APPLICATION_OVERLAY，只能用旧的系统级窗口类型
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

    private fun buildBar(): View {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), 0, dp(6), 0)
            background = GradientDrawable().apply {
                cornerRadius = dp(28).toFloat()
                setColor(Color.parseColor("#E6202124"))
                setStroke(dp(1), Color.parseColor("#33FFFFFF"))
            }
        }

        titleView = TextView(this).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(0, 0, dp(6), 0)
        }
        bar.addView(
            titleView,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
        )

        prevView = button("⏮") { dispatch(PlayIntent.Previous) }
        playView = button("⏯") { dispatch(PlayIntent.TogglePlay) }
        nextView = button("⏭") { dispatch(PlayIntent.Next) }
        // 关闭：服务停了悬浮窗就该消失，免得留下一个点不动的空壳
        val closeView = button("✕") { stopSelf() }

        listOf(prevView, playView, nextView, closeView).forEach { v ->
            bar.addView(v, LinearLayout.LayoutParams(dp(40), dp(40)))
        }

        // 整条与每个按钮都挂同一个手势处理：拖动落在哪都能拖，按钮的「点击」在抬手时判定。
        // 不能用 setOnClickListener —— 触摸监听一旦消费了事件，click 就再也不会触发，
        // 按钮会变成「按下去没反应」。
        bar.setOnTouchListener { _, e -> handleTouch(e, tap = null) }
        return bar
    }

    /**
     * 悬浮窗的全部动作都走唯一执行点（S1.3）。
     *
     * ## 这里原来是个真 bug
     *
     * 三行原来都是直发 LinkPlay 私有 httpapi 命令（`nextTrack` / `previousTrack` / `togglePause`）。
     * 投通用 DLNA 渲染器（电视盒子）时设备不认这条命令，但按钮照样画着：
     * 用户按了，什么都没发生，也没有任何提示。
     *
     * 而「这台设备支不支持切歌」这件事（`DeviceCapabilities.supportsNextPrev`）
     * **从来没被读过一次**。
     */
    private fun dispatch(intent: PlayIntent) {
        if (!dispatcher.can(intent)) {
            // 静默忽略比报错更糟：用户会以为按钮坏了。说清楚是设备做不到。
            android.widget.Toast
                .makeText(this, "这台设备不支持这个操作", android.widget.Toast.LENGTH_SHORT)
                .show()
            return
        }
        scope.launch { dispatcher.dispatch(intent) }
    }

    private fun button(label: String, onClick: () -> Unit): TextView = TextView(this).apply {
        text = label
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        gravity = Gravity.CENTER
        setOnTouchListener { _, e -> handleTouch(e, tap = onClick) }
    }

    /**
     * 拖动 + 按钮点击的统一手势处理。
     *
     * 为什么用增量而不是「当前坐标 + 每次的差」：`rawX` 是绝对坐标，用它 diff 出来的增量
     * 不会因为窗口被夹回边缘而累积漂移 —— 手指从屏幕外滑回来，窗口能立刻跟手，
     * 而不是要先把之前被吃掉的位移补回来。
     *
     * @param tap 抬手时若几乎没移动（< 6dp）就当作点击，触发这个动作；null 表示只拖不点
     */
    private fun handleTouch(event: MotionEvent, tap: (() -> Unit)?): Boolean {
        val wm = windowManager ?: return false
        val lp = params ?: return false
        val metrics = resources.displayMetrics
        val edge = dp(FloatingLayout.EDGE)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downRawX = event.rawX
                downRawY = event.rawY
                downSpecX = spec.x
                downSpecY = spec.y
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = (event.rawX - downRawX).toInt()
                val dy = (event.rawY - downRawY).toInt()
                val moved = FloatingLayout.onDrag(
                    spec.copy(x = downSpecX, y = downSpecY),
                    dx, dy,
                    metrics.widthPixels, metrics.heightPixels,
                    edge,
                )
                applySpec(moved, wm, lp)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                // 松手才吸附：拖动过程中贴边会让人抓不住
                val snapped = FloatingLayout.snap(
                    spec, metrics.widthPixels, metrics.heightPixels, edge,
                )
                applySpec(snapped, wm, lp)
                val slop = dp(6)
                val dragged = kotlin.math.abs(event.rawX - downRawX) > slop ||
                    kotlin.math.abs(event.rawY - downRawY) > slop
                if (!dragged && event.actionMasked == MotionEvent.ACTION_UP) tap?.invoke()
                return true
            }
        }
        return false
    }

    private fun applySpec(next: FloatSpec, wm: WindowManager, lp: WindowManager.LayoutParams) {
        if (next == spec) return
        spec = next
        lp.x = next.x
        lp.y = next.y
        lp.width = next.width
        lp.height = next.height
        runCatching { wm.updateViewLayout(root, lp) }
    }

    // ---- 状态与命令 ----

    /**
     * 订阅全局队列状态：曲名变了、换了设备，悬浮条立刻跟着变。
     *
     * 用 `state` 而不是轮询，是因为 [QueueBus] 本来就是全站唯一真相源 ——
     * 再开一条轮询只会让曲名与播放页对不上。
     */
    private fun observeQueue() {
        if (observing) return
        observing = true
        scope.launch {
            QueueBus.state.collect { render() }
        }
    }

    private var observing = false

    private fun render() {
        val ip = targetIp()
        val title = QueueBus.state.value.nowPlaying
        val ready = ip != null
        titleView?.text = when {
            !ready -> "未在播放"
            title.isNullOrBlank() -> "已连接 · 待播放"
            else -> title
        }
        // 没有目标设备就置灰：点下去只会发到一个不存在的地址，用户却以为是 App 坏了
        listOf(prevView, playView, nextView).forEach { v ->
            v?.isEnabled = ready
            v?.alpha = if (ready) 1f else 0.35f
        }
    }

    private fun send(command: String) {
        val ip = targetIp() ?: return
        scope.launch {
            // 命令是「发了就算」的：设备回 OK 不代表真执行，这里不复读状态，
            // 曲名以播放页/队列总线的回读为准，避免悬浮窗显示一个自己编的答案。
            withContext(Dispatchers.IO) { runCatching { http.send(ip, command) } }
        }
    }

    // ---- 生命周期 ----

    override fun onDestroy() {
        root?.let { v -> runCatching { windowManager?.removeView(v) } }
        root = null
        observing = false
        scope.cancel()
        _running.value = false
        super.onDestroy()
    }

    override fun onCreate() {
        super.onCreate()
        _running.value = true
    }

    // ---- 通知 ----

    private fun buildNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("悬浮控制已开启")
        .setContentText("桌面上可直接播放/暂停/切歌")
        .setSmallIcon(android.R.drawable.ic_media_play)
        .setOngoing(true)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setContentIntent(
            PendingIntent.getActivity(
                this, 0, Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ),
        )
        .addAction(0, "关闭", stopIntent())
        .build()

    private fun stopIntent(): PendingIntent = PendingIntent.getService(
        this, 1, Intent(this, FloatingControlService::class.java).setAction(ACTION_STOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt().coerceAtLeast(1)

    companion object {
        private const val CHANNEL_ID = "floating-control"
        private const val NOTIFICATION_ID = 44
        private const val ACTION_STOP = "io.github.laozhudu.linkplay.console.STOP_FLOATING"
        private const val EXTRA_IP = "ip"

        /**
         * 服务是否在跑。设置页的开关订阅它，而不是自己记一个布尔值 ——
         * 否则用户在通知里点了「关闭」，设置页的开关还会显示「开」。
         */
        private val _running = MutableStateFlow(false)
        val running: StateFlow<Boolean> = _running.asStateFlow()

        /** 有没有「显示在其他应用上层」的权限。API 23+ 才有这个概念，minSdk 24 起都可用。 */
        fun canDrawOverlays(context: Context): Boolean =
            runCatching { Settings.canDrawOverlays(context) }.getOrDefault(false)

        /** 打开系统的悬浮窗权限页。**不要**直接 startActivity 而不加包名 —— 会落到设置首页，用户找不到开关。 */
        fun overlayPermissionIntent(context: Context): Intent =
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                android.net.Uri.parse("package:${context.packageName}"),
            )

        /**
         * 启动。没有权限时返回 false，由调用方去引导授权（这里绝不自己弹窗或崩溃）。
         *
         * @param fallbackIp 播放页没打开过时用它当目标设备（设置页里知道当前设备是谁）
         */
        fun start(context: Context, fallbackIp: String?): Boolean {
            if (!canDrawOverlays(context)) return false
            // 渠道必须先建好，startForeground 里引用不存在的渠道在 API 26+ 会抛异常
            ensureChannel(context)
            val intent = Intent(context, FloatingControlService::class.java).apply {
                putExtra(EXTRA_IP, fallbackIp)
            }
            ContextCompat.startForegroundService(context, intent)
            return true
        }

        fun stop(context: Context) {
            runCatching {
                context.startService(
                    Intent(context, FloatingControlService::class.java).setAction(ACTION_STOP),
                )
            }
        }

        /** 建通知渠道。渠道名就是用户在通知设置里看到的那个名字，所以叫「悬浮控制」。 */
        private fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            if (nm.getNotificationChannel(CHANNEL_ID) != null) return
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "悬浮控制", NotificationManager.IMPORTANCE_LOW),
            )
        }
    }
}
