package io.github.laozhudu.linkplay.console.playback

import io.github.laozhudu.linkplay.protocol.playback.PlayIntent
import io.github.laozhudu.linkplay.protocol.voice.VoiceIntent
import io.github.laozhudu.linkplay.protocol.voice.IntentRouter
import io.github.laozhudu.linkplay.console.voice.VoiceController
import io.github.laozhudu.linkplay.console.voice.VoiceSourceStatus
import androidx.compose.runtime.DisposableEffect
import android.graphics.BitmapFactory
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import kotlinx.coroutines.launch
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil.compose.AsyncImage
import io.github.laozhudu.linkplay.console.session.SessionStore
import io.github.laozhudu.linkplay.console.ui.components.AppShapesExpressive
import io.github.laozhudu.linkplay.console.ui.components.BackGlyph
import io.github.laozhudu.linkplay.console.ui.components.CircleIcon
import io.github.laozhudu.linkplay.console.ui.components.GlyphSize
import io.github.laozhudu.linkplay.console.ui.components.LoopGlyph
import io.github.laozhudu.linkplay.console.ui.components.PauseGlyph
import io.github.laozhudu.linkplay.console.ui.components.PlayGlyph
import io.github.laozhudu.linkplay.console.ui.components.SkipGlyph
import io.github.laozhudu.linkplay.console.ui.components.VolumeGlyph
import io.github.laozhudu.linkplay.console.ui.components.WaveProgressSlider
import io.github.laozhudu.linkplay.console.ui.theme.LocalExtraColors
import io.github.laozhudu.linkplay.console.ui.theme.OnDarkPalette
import io.github.laozhudu.linkplay.console.ui.theme.Spacing
import io.github.laozhudu.linkplay.console.ui.theme.StatusColors
import io.github.laozhudu.linkplay.console.ui.theme.seedOf
import io.github.laozhudu.linkplay.protocol.playback.QueueRestore
import io.github.laozhudu.linkplay.protocol.player.LoopMode
import io.github.laozhudu.linkplay.protocol.player.PlayerSource
import io.github.laozhudu.linkplay.protocol.queue.QueueItem
import io.github.laozhudu.linkplay.protocol.upnp.TransportState

/**
 * 播放页 —— **单层、不滚动、无浮层**。
 *
 * ## 为什么又重做一次
 *
 * 上一版把播放列表做成 `ModalBottomSheet`。用户的原话是
 * 「点击是多一层，没在原来的播放器层面播放和暂停，导致正在播放时列表等其他功能点不了」。
 * 也就是说：任何"另开一层"的东西都是错的方向，不管它多像 QQ 音乐。
 *
 * 这一版的硬约束：
 *
 * 1. **整页不滚动**（`Column` 固定，不用 `verticalScroll`）——
 *    滚动会抢手势，而且手指一滑控制键就跑到屏幕外；
 * 2. **没有 Dialog / BottomSheet / 二级页** —— 所有东西都在这一屏里；
 * 3. **只有底部一个可滚动区域**（内容区），靠一个二选一切换「播放列表 / 投流日志」，
 *    所以永远不会有"两个滚动轴打架"或"浮层挡着点不到"；
 * 4. 控制键用 `weight` 分配固定区域，**播放/暂停永远在屏幕内、永远可点**。
 *
 * ## 版式（第二版：列表居中，播放键压底）
 *
 * ```
 * 设备名                                    ⋯
 * 输入源 网络播放 · 播放中        ← 当前输入源常显
 *        ┌────────── 封面（自适应）──────────┐
 *        曲名
 *        歌手
 *   [播放列表 12] [投流与日志]   设备 12 首 · 第 3 首
 *   ┌── 中部列表：全屏唯一的滚动区 ──┐
 *   │ 1 ▶  曲名            ✕        │
 *   │ 2    曲名            ✕        │
 *   └───────────────────────────────┘
 *   1:23 ───────●──────── 4:56
 *   🔊 ──────────●───── 60
 *   输入源 [网络播放✓] 线路输入 蓝牙 U 盘 光纤
 *   循环   ⏮   ▶(68dp)   ⏭   ■      ← 压在最下方
 * ```
 */
@Composable
fun PlayerScreen(
    deviceIp: String,
    deviceLocation: String?,
    /** 设备名（定稿投送状态行要显示 `doss1` 而不是 IP）。 */
    deviceName: String? = null,
    /** 设备 uuid：用于按它恢复上次的播放列表。 */
    deviceUuid: String? = null,
    /** 一级页面时不显示返回箭头（底部 Tab 换页）。 */
    onBack: (() -> Unit)? = null,
    onOpenSettings: () -> Unit = {},
    onOpenNas: () -> Unit = {},
    onOpenLibrary: () -> Unit = {},
    onOpenLocalQueue: () -> Unit = {},
) {
    val appContext = LocalContext.current.applicationContext
    // **刻意不加 key**：整台 App 只有一个 PlaybackViewModel，换设备走 vm.switchTo()。
    // 以前是 key = "player-$deviceIp"，换设备会新建一个 VM，旧的那只留在 ViewModelStore 里
    // 继续每秒轮询、往共享状态里写上一台设备的数据 —— 这才是「切设备后报错/串台、
    // 等一会儿又自己好了」的真因（用户反馈过）。
    val vm: PlaybackViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                PlaybackViewModel(
                    deviceIp = deviceIp,
                    location = deviceLocation,
                    deviceUuid = deviceUuid,
                    sessionStore = SessionStore.of(appContext),
                    appContext = appContext,
                )
            }
        },
    )

    // 登记给 PlaybackDispatcher（S1.2）。dispatcher 是进程级的，拿不到 Composable 作用域，
    // 所以由播放页在此登记；离开时必须注销 —— 否则它会对着一个已销毁的 VM 发命令，
    // 表现是「退出播放页后按悬浮窗没反应，但也没报错」。
    DisposableEffect(vm) {
        PlaybackViewModels.bind(vm)
        onDispose { PlaybackViewModels.unbind(vm) }
    }
    // 设备变了就地切换（首次组合时是同一条设备，switchTo 内部会直接返回，不会重连）
    LaunchedEffect(deviceIp, deviceLocation, deviceUuid) {
        vm.switchTo(deviceIp, deviceLocation, deviceUuid)
    }
    val state by vm.state.collectAsState()
    val queue by QueueBus.state.collectAsState()
    val palette = remember(state.title, state.artist) { seedOf(state.title, state.artist) }
    val extras = LocalExtraColors.current

    var scrubDraft by remember { mutableStateOf<Float?>(null) }
    var volDraft by remember { mutableStateOf<Float?>(null) }
    /** 全屏播放列表是否打开（定稿里它是从一行入口点开的一层）。 */
    var showPlaylist by remember { mutableStateOf(false) }

    // ---- 语音：一条顶栏提示 + 一个麦克风按钮，不引入任何浮层 ----
    var voiceNote by remember { mutableStateOf<String?>(null) }
    var listening by remember { mutableStateOf(false) }
    val voice = remember { VoiceController(appContext) }
    // 语音入口也走唯一执行点（S1.3）：识别出来的意图和界面按钮按下去必须是同一条路，
    // 否则迟早出现"按按钮能切歌、说话切不了"这种不一致。
    // 所有界面动作共用的作用域。语音、按钮、切歌都走它 ——
    // 名字从 voiceScope 改过来，因为它现在不只服务语音。
    val actionScope = rememberCoroutineScope()
    val dispatcher = remember { PlaybackDispatcher(appContext) }
    // 页面销毁时必须取消会话，否则 SpeechRecognizer 会继续占着麦克风
    DisposableEffect(Unit) { onDispose { voice.cancel() } }

    // 麦克风是运行时权限：没授权就先申请，拿到再开始听（不静默失败）
    fun beginListening() {
        listening = true
        voice.start(
            onResult = { text ->
                listening = false
                // 三段分开记：识别到了什么 → 匹配到什么意图 → 执行结果。
                // 只记最后那句提示的话，"没匹配到意图"和"执行失败"就分不开了
                VoiceSourceStatus.heard(text)
                val intent = IntentRouter.route(text)
                actionScope.launch {
                    val note = handleVoiceIntent(intent, dispatcher)
                    VoiceSourceStatus.routed(
                        intent = voiceIntentName(intent),
                        result = note,
                        matched = intent !is PlayIntent.Unknown,
                    )
                    voiceNote = note
                }
            },
            onError = { msg ->
                listening = false
                VoiceSourceStatus.failed(msg)
                voiceNote = msg
            },
        )
    }
    val micLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            VoiceSourceStatus.environment(available = true, micGranted = true)
            beginListening()
        } else {
            VoiceSourceStatus.environment(available = true, micGranted = false)
            voiceNote = "没有麦克风权限，语音用不了"
            VoiceSourceStatus.failed("没有麦克风权限，语音用不了")
        }
    }
    // 3 秒后自动消失：这是"刚才发生了什么"的即时回执，不是需要用户关掉的东西
    LaunchedEffect(voiceNote) {
        if (voiceNote != null) {
            kotlinx.coroutines.delay(3_000)
            voiceNote = null
        }
    }

    val playing = state.state == TransportState.PLAYING || state.state == TransportState.TRANSITIONING
    // ---- 播放状态按输出选源（S2.1）----
    //
    // 本机播放读 ExoPlayer 的实时位置，投音箱读设备快照。界面只用同一组字段，
    // 不关心底下是谁 —— 这就是 PlayerUiState 存在的意义。
    //
    // 之前的问题：切到本机后进度条和音量条是空的，因为界面只读设备状态，
    // 而手机上放的时候没有人往那里填值。
    val routerRef = remember { PlaybackRouter.of(appContext) }
    val localUi by routerRef.localState.collectAsState()
    val isLocalOutput = routerRef.output() is PlaybackOutput.Local
    // 能力位：这台的设备支不支持切歌（电视盒子不支持）。每次重组算一次，很便宜（读缓存）。
    val canPrev = dispatcher.can(PlayIntent.Previous)
    val canNext = dispatcher.can(PlayIntent.Next)

    val duration = if (isLocalOutput) {
        localUi.durationMs / 1000L
    } else {
        state.durationSec.coerceAtLeast(0L)
    }
    val positionSecNow = if (isLocalOutput) localUi.positionMs / 1000.0 else state.positionSec.toDouble()
    val progress = scrubDraft ?: if (isLocalOutput) {
        if (duration <= 0L) 0f else (positionSecNow / duration).toFloat().coerceIn(0f, 1f)
    } else {
        state.progress
    }
    val volumeNow = if (isLocalOutput) localUi.volume else state.volume
    val shownVolume = volDraft ?: (volumeNow.toFloat() / 100f)
    val art = state.albumArtUrl?.takeIf { it.isNotBlank() }
        ?: queue.current?.albumArtUrl?.takeIf { it.isNotBlank() }
    val embeddedCover = remember(state.embeddedCover) {
        state.embeddedCover?.let {
            runCatching { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }.getOrNull()
        }
    }
    val title = state.title ?: queue.current?.title
    val artist = state.artist ?: queue.current?.artist

    // 松手才 seek：逐像素 seek 会让设备进度反复回弹
    LaunchedEffect(scrubDraft) {
        val d = scrubDraft ?: return@LaunchedEffect
        if (duration <= 0) return@LaunchedEffect
        vm.seekTo((d * duration).toLong())
    }
    // 换曲后重新读内嵌封面/歌词
    LaunchedEffect(queue.current?.url) {
        queue.current?.url?.let {
            vm.clearEmbeddedTags()
            vm.loadEmbeddedTags(it)
        }
    }

    val artworkScale by animateFloatAsState(
        targetValue = if (playing) 1f else 0.94f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "artwork-scale",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            // 播放页**永远是深色底**（暂停时也深一档）：
            // 1) 封面/派生色都是按深底设计的；2) 浅色主题下深底更耐看；
            // 3) 换来一组恒定文字色，不必为两种主题各调一遍对比度。
            .background(
                Brush.verticalGradient(
                    listOf(palette.top, palette.bottom),
                ),
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Spacing.lg),
        ) {
            // ---------- 顶栏 ----------
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.xs, bottom = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onBack != null) {
                    CircleIcon(onClick = onBack) {
                        BackGlyph(OnDarkPalette.text, Modifier.size(GlyphSize.medium))
                    }
                    Spacer(Modifier.width(Spacing.xs))
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        state.friendlyName ?: deviceIp,
                        style = MaterialTheme.typography.titleSmall,
                        color = OnDarkPalette.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        buildString {
                            // 输入源以设备回报为准：模式数字 → 人话（PlayerSource.describe）。
                            // 前缀「输入源」是用户明确要求的：一眼能看出当前在哪一路上，
                            // 而不是只看到一句「网络播放 · 本地播放列表」猜不出这是输入源。
                            append("输入源 ")
                            append(
                                if (state.mode != null) PlayerSource.describe(state.mode)
                                else state.sourceLabel ?: "连接中",
                            )
                            if (playing) append(" · 播放中")
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = OnDarkPalette.muted,
                        maxLines = 1,
                    )
                }
                // 麦克风：点一下开始听、再点取消。识别结果走 IntentRouter → 现有 ViewModel 方法，
                // 不新增任何播放逻辑（语音只是"另一个输入方式"）。
                CircleIcon(
                    onClick = {
                        if (listening) {
                            voice.cancel()
                            listening = false
                            voiceNote = "已取消"
                        } else if (!VoiceController.available(appContext)) {
                            VoiceSourceStatus.environment(available = false, micGranted = false)
                            VoiceSourceStatus.failed("本机没有可用的语音识别")
                            voiceNote = "本机没有可用的语音识别"
                        } else if (androidx.core.content.ContextCompat.checkSelfPermission(
                                appContext, android.Manifest.permission.RECORD_AUDIO,
                            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                        ) {
                            // 先要权限；授权成功后由 launcher 回调继续 start
                            VoiceSourceStatus.environment(available = true, micGranted = false)
                            micLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                        } else {
                            VoiceSourceStatus.environment(available = true, micGranted = true)
                            beginListening()
                        }
                    },
                ) {
                    Text(
                        if (listening) "…" else "🎤",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (listening) palette.accent else OnDarkPalette.text,
                    )
                }
                CircleIcon(onClick = onOpenSettings) {
                    Text("⋯", style = MaterialTheme.typography.headlineSmall, color = OnDarkPalette.text)
                }
            }

            voiceNote?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.accent,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = Spacing.xs),
                )
            }

            // ---------- 封面 ----------
            // 版式（第二版）：封面在上 → 播放列表在中 → 播放键压在最下。
            // 两块都用 weight（1.05 : 1）而不是写死高度，矮屏/高屏都能给列表留出位置。
            // 用 BoxWithConstraints 取「宽高较小者」当边长：直接 aspectRatio 在窄屏上会把封面撑出父容器。
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1.05f),
                contentAlignment = Alignment.Center,
            ) {
                val side = if (maxWidth < maxHeight) maxWidth else maxHeight
                Box(
                    modifier = Modifier
                        .size(side)
                        .graphicsLayer {
                            scaleX = artworkScale
                            scaleY = artworkScale
                        }
                        .clip(AppShapesExpressive.squircle)
                        .background(
                            Brush.linearGradient(listOf(palette.accent.copy(alpha = 0.55f), palette.top)),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (art != null) {
                        AsyncImage(
                            model = art,
                            contentDescription = "专辑封面",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else if (embeddedCover != null) {
                        Image(
                            bitmap = embeddedCover,
                            contentDescription = "内嵌封面",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Text(
                            text = (title ?: "?").take(1).uppercase(),
                            fontSize = 64.sp,
                            fontWeight = FontWeight.Black,
                            color = OnDarkPalette.text.copy(alpha = 0.85f),
                        )
                    }
                }
            }

            // ---------- 曲名 / 歌手 ----------
            Spacer(Modifier.height(Spacing.sm))
            Text(
                text = title ?: "未在播放",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = OnDarkPalette.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = artist
                    ?: if (state.state == TransportState.NO_MEDIA_PRESENT) "去媒体库点一首" else deviceIp,
                style = MaterialTheme.typography.bodySmall,
                color = OnDarkPalette.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            // ---------- 投送状态（定稿：`doss1 · 投送中`）----------
            //
            // 这一行回答 **"声音现在往哪走"**，是播放页最该先说清的一件事：
            // 用户最常见的困惑不是"没有播放按钮"，而是"按了播放却没声音"
            // —— 因为声音投到了另一台设备。把这个信息从顶栏的小字提到标题正下方。
            Text(
                text = buildString {
                    append(deviceName ?: deviceIp)
                    append(" · ")
                    append(
                        when {
                            state.error != null -> "出错了"
                            playing -> "投送中"
                            queue.running -> "已暂停"
                            else -> "待播放"
                        },
                    )
                },
                style = MaterialTheme.typography.labelMedium,
                color = OnDarkPalette.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            // ---------- 弹性空隙 ----------
            //
            // 定稿把「播放列表」从**页面中部常驻**收成了下方一行入口
            // （点开才是全屏列表）。中部因此空出来 —— 用 weight 撑开，
            // 让封面/标题靠上、控制区靠下，和定稿的疏密一致。
            Spacer(Modifier.weight(1f))


            // ---------- 底部控制区：进度 → 音量 → 输入源 → 播放整行（压在最下）----------

            // 进度
            WaveProgressSlider(
                value = progress,
                onValueChange = { scrubDraft = it },
                onValueChangeFinished = { scrubDraft = null },
                modifier = Modifier.fillMaxWidth(),
                activeColor = OnDarkPalette.text,
                inactiveColor = OnDarkPalette.muted.copy(alpha = 0.28f),
                thumbColor = palette.accent,
                animated = scrubDraft == null,
                height = 20.dp,
            )
            Row(Modifier.fillMaxWidth()) {
                Text(
                    PlaybackViewModel.format((progress * duration).toLong()),
                    style = MaterialTheme.typography.labelSmall,
                    color = OnDarkPalette.muted,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    // 通用 DLNA 渲染器常常不回时长（GetPositionInfo 的 TrackDuration 为空），
                    // 这时显示 0:00 会让人以为"歌是空的"，用 --:-- 明确表示"设备没告诉"。
                    if (duration > 0) PlaybackViewModel.format(duration) else "--:--",
                    style = MaterialTheme.typography.labelSmall,
                    color = OnDarkPalette.muted,
                )
            }

            // 音量（拖动跟手）
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircleIcon(
                    onClick = { actionScope.launch { dispatcher.dispatch(PlayIntent.Mute) } },
                    size = 32.dp,
                ) {
                    VolumeGlyph(
                        color = if (state.mute) OnDarkPalette.muted else OnDarkPalette.text,
                        muted = state.mute,
                        modifier = Modifier.size(GlyphSize.small),
                    )
                }
                Spacer(Modifier.width(Spacing.xs))
                WaveProgressSlider(
                    value = shownVolume,
                    onValueChange = {
                        volDraft = it
                        vm.setVolumeLive((it * 100).toInt())
                    },
                    onValueChangeFinished = { volDraft = null },
                    modifier = Modifier.weight(1f),
                    activeColor = OnDarkPalette.text,
                    inactiveColor = OnDarkPalette.muted.copy(alpha = 0.28f),
                    thumbColor = palette.accent,
                    animated = false,
                    height = 18.dp,
                )
                Spacer(Modifier.width(Spacing.xs))
                Text(
                    if (!isLocalOutput && state.mute) "静音" else volumeNow.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = OnDarkPalette.muted,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(32.dp),
                )
            }

            // 故障用红字；"该选歌了"这种可执行提示用中性色，别让人以为 App 坏了
            state.error?.let { msg ->
                Text(
                    msg,
                    style = MaterialTheme.typography.labelSmall,
                    color = StatusColors.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            state.notice?.let { msg ->
                Text(
                    msg,
                    style = MaterialTheme.typography.labelSmall,
                    color = StatusColors.warn,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (state.error == null) state.unavailableReason?.let { msg ->
                Text(
                    msg,
                    style = MaterialTheme.typography.labelSmall,
                    color = StatusColors.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // 主控整行：压在屏幕最下方，永远可点。
            //
            // **只留三个键**（上一首 / 播放暂停 / 下一首）—— 定稿就是这三个。
            // 原来还有「循环」和「停止」，都挪进了全屏播放列表页：
            // 一页摆五个平级按钮，每次都要想一下点哪个；
            // 而"停止"和"暂停"在没有外部场景时其实是同一件事。
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.xs),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 能力位决定这个按钮能不能用（docs/86 不变量 I3）。
                //
                // 通用 DLNA 渲染器（电视盒子）没有设备侧队列 → supportsNextPrev = false。
                // 以前照样画出来，按下去毫无反应；现在**灰掉并给一句原因**，
                // 而不是静默失败（悬浮窗踩过那个坑）。
                CircleIcon(
                    onClick = {
                        if (canPrev) {
                            actionScope.launch { dispatcher.dispatch(PlayIntent.Previous) }
                        } else {
                            voiceNote = NOT_SUPPORTED_PREV
                        }
                    },
                    size = 48.dp,
                ) {
                    SkipGlyph(
                        if (canPrev) OnDarkPalette.text else OnDarkPalette.text.copy(alpha = 0.28f),
                        forward = false,
                        modifier = Modifier.size(28.dp),
                    )
                }
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(OnDarkPalette.text)
                        // 走唯一执行点：TogglePlay 内部按设备回报的状态决定播还是停
                        .clickable { actionScope.launch { dispatcher.dispatch(PlayIntent.TogglePlay) } },
                    contentAlignment = Alignment.Center,
                ) {
                    if (playing) PauseGlyph(palette.top, Modifier.size(28.dp))
                    else PlayGlyph(palette.top, Modifier.size(28.dp))
                }
                CircleIcon(
                    onClick = {
                        if (canNext) {
                            actionScope.launch { dispatcher.dispatch(PlayIntent.Next) }
                        } else {
                            voiceNote = NOT_SUPPORTED_NEXT
                        }
                    },
                    size = 48.dp,
                ) {
                    SkipGlyph(
                        if (canNext) OnDarkPalette.text else OnDarkPalette.text.copy(alpha = 0.28f),
                        forward = true,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }

            // ---------- 播放列表入口（定稿：`播放列表 · 100 首 · 第 8 首  ›`）----------
            //
            // 从"页面中部常驻一个列表"改成"一行入口 + 全屏列表"。
            // 理由：这一页是**遥控器**，不是文件管理器 —— 一眼要看封面、进度、三个键；
            // 选第几首是低频动作，值得多一次点击。
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { showPlaylist = true }
                    .padding(horizontal = Spacing.xs, vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = buildString {
                        append("播放列表")
                        if (queue.items.isNotEmpty()) {
                            append(" · ${queue.items.size} 首")
                            if (queue.index >= 0) append(" · 第 ${queue.index + 1} 首")
                        } else {
                            append(" · 空")
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = OnDarkPalette.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "›",
                    style = MaterialTheme.typography.titleMedium,
                    color = OnDarkPalette.muted.copy(alpha = 0.6f),
                )
            }

            // ---------- 「从哪出声」（定稿在底部；大厂同位置）----------
            //
            // Spotify 的 Connect 图标就在底部播放条里，Sonos 的当前房间也在视觉底部。
            // 原来它在顶栏 —— 顶栏是"这页是什么"的地方，目标选择是"东西往哪去"，
            // 语义不同层。挪到最下面之后，和内容页的「投给」是同一种位置关系。
            //
            // **内联一行，不能浮层**（docs/37：播放页不许任何浮层）。
            OutputSwitchRow()
        }
    }

    // ---------- 全屏播放列表（从入口行打开）----------
    if (showPlaylist) {
        PlaylistSheet(
            queue = queue,
            state = state,
            vm = vm,
            onOpenNas = onOpenNas,
            onOpenLocalQueue = onOpenLocalQueue,
            onOpenLibrary = onOpenLibrary,
            onClose = { showPlaylist = false },
            actionScope = actionScope,
            dispatcher = dispatcher,
        )
    }
}

@Composable
private fun SegmentChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(AppShapesExpressive.pill)
            .background(
                if (selected) Color(0x33FFFFFF) else OnDarkPalette.chip,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.md, vertical = 6.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = LocalExtraColors.current.onBackdrop,
        )
    }
}

@Composable
private fun PlaylistRow(
    index: Int,
    item: QueueItem,
    playing: Boolean,
    current: Boolean,
    onClick: () -> Unit,
    onRemove: (() -> Unit)?,
) {
    val extras = LocalExtraColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = Spacing.md, end = Spacing.xs, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(26.dp), contentAlignment = Alignment.Center) {
            if (playing) {
                EqualizerBars(OnDarkPalette.text)
            } else {
                Text(
                    "${index + 1}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (current) OnDarkPalette.text else OnDarkPalette.muted,
                )
            }
        }
        Spacer(Modifier.width(Spacing.xs))
        Column(Modifier.weight(1f)) {
            Text(
                item.title,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal,
                color = if (current) OnDarkPalette.text else OnDarkPalette.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            item.artist?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = OnDarkPalette.muted,
                    maxLines = 1,
                )
            }
        }
        // 占位行（设备有队列但本机没曲目单）不能删：设备侧的列表我们改不了
        onRemove?.let {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .clickable(onClick = it),
                contentAlignment = Alignment.Center,
            ) {
                Text("✕", style = MaterialTheme.typography.labelSmall, color = OnDarkPalette.muted)
            }
        }
    }
}

/** 正在播的小均衡器条。 */
@Composable
private fun EqualizerBars(color: Color) {
    Canvas(Modifier.size(width = 13.dp, height = 14.dp)) {
        val barW = size.width / 5
        listOf(0.55f, 1f, 0.7f).forEachIndexed { i, h ->
            val bh = size.height * h
            val x = barW * (i * 2 + 0.5f)
            drawLine(
                color = color,
                start = Offset(x, (size.height - bh) / 2),
                end = Offset(x, (size.height + bh) / 2),
                strokeWidth = barW,
                cap = StrokeCap.Round,
            )
        }
    }
}


/** 投流与日志。占用底部内容区，不弹出新层。 */
@Composable
private fun CastPanel(
    state: PlaybackUiState,
    vm: PlaybackViewModel,
    onOpenNas: () -> Unit,
    onOpenLocalQueue: () -> Unit,
) {
    val extras = LocalExtraColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.md),
    ) {
        OutlinedTextField(
            value = state.castUrl,
            onValueChange = vm::setCastUrl,
            label = { Text("要投的音频地址") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "这条链接需要 Referer（自动转发）",
                style = MaterialTheme.typography.labelSmall,
                color = OnDarkPalette.muted,
                modifier = Modifier.weight(1f),
            )
            Switch(checked = state.castNeedsHeaders, onCheckedChange = vm::setCastNeedsHeaders)
        }
        if (state.castNeedsHeaders) {
            OutlinedTextField(
                value = state.castReferer,
                onValueChange = vm::setCastReferer,
                label = { Text("Referer") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            TextButton(onClick = vm::cast, enabled = !state.busy) { Text("投流") }
            TextButton(onClick = onOpenNas) { Text("从 NAS 选") }
            TextButton(onClick = onOpenLocalQueue) { Text("手机音乐") }
        }
        if (state.lyrics != null) {
            Text(
                "歌词",
                style = MaterialTheme.typography.labelSmall,
                color = OnDarkPalette.text,
            )
            Text(
                state.lyrics!!,
                style = MaterialTheme.typography.labelSmall,
                color = OnDarkPalette.muted,
                modifier = Modifier.heightIn(max = 200.dp),
            )
        }
        if (state.log.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.xs))
            Text(
                state.log.takeLast(8).joinToString("\n"),
                style = MaterialTheme.typography.labelSmall,
                color = OnDarkPalette.muted,
            )
        }
    }
}

/**
 * 语音意图 → 现有 ViewModel 动作。
 *
 * 这里只做**分派**：所有动作都调已经存在的方法（next/pause/setVolumeLive...），
 * 不在语音这一层另写一套播放逻辑 —— 否则语音和按钮的时序会各走一套，
 * 迟早出现"按按钮能停、说话停不了"这种不一致。
 *
 * @return 给用户的一句回执（3 秒后自动消失）
 */
/** 意图的短名，只给诊断报告看（`Unknown` 这个值本身就能说明"没匹配上"）。 */
private fun voiceIntentName(intent: VoiceIntent): String = when (intent) {
    PlayIntent.Next -> "Next"
    PlayIntent.Previous -> "Previous"
    PlayIntent.Play -> "Play"
    PlayIntent.Pause -> "Pause"
    PlayIntent.Stop -> "Stop"
    PlayIntent.TogglePlay -> "TogglePlay"
    PlayIntent.VolumeUp -> "VolumeUp"
    PlayIntent.VolumeDown -> "VolumeDown"
    PlayIntent.Mute -> "Mute"
    PlayIntent.LoopNext -> "LoopNext"
    is PlayIntent.Search -> "Search(「${intent.keyword}」)"
    is PlayIntent.Unknown -> "Unknown"
    is PlayIntent.SetVolume -> "SetVolume(${intent.value})"
    is PlayIntent.SeekTo -> "SeekTo(${intent.seconds})"
    else -> intent.javaClass.simpleName
}

/**
 * 语音意图的执行。
 *
 * 走 [PlaybackDispatcher] 而不是直接 `vm.xxx()` —— 和界面按钮、悬浮窗同一条路。
 * 回执文案保留原来那套（用户看到的提示不变），只是执行通道换成了唯一入口。
 */
private suspend fun handleVoiceIntent(intent: PlayIntent, dispatcher: PlaybackDispatcher): String = when (intent) {
    PlayIntent.Next -> if (dispatcher.dispatch(intent)) "下一首" else UNSUPPORTED
    PlayIntent.Previous -> if (dispatcher.dispatch(intent)) "上一首" else UNSUPPORTED
    PlayIntent.Play -> if (dispatcher.dispatch(intent)) "播放" else UNSUPPORTED
    PlayIntent.Pause -> if (dispatcher.dispatch(intent)) "暂停" else UNSUPPORTED
    PlayIntent.Stop -> if (dispatcher.dispatch(intent)) "停止" else UNSUPPORTED
    PlayIntent.TogglePlay -> if (dispatcher.dispatch(intent)) "已切换播放状态" else UNSUPPORTED
    PlayIntent.VolumeUp -> if (dispatcher.dispatch(intent)) "音量 +5" else UNSUPPORTED
    PlayIntent.VolumeDown -> if (dispatcher.dispatch(intent)) "音量 -5" else UNSUPPORTED
    PlayIntent.Mute -> if (dispatcher.dispatch(intent)) "静音切换" else UNSUPPORTED
    PlayIntent.LoopNext -> if (dispatcher.dispatch(intent)) "已切循环模式" else UNSUPPORTED
    is PlayIntent.SetVolume -> if (dispatcher.dispatch(intent)) "音量 ${intent.value}" else UNSUPPORTED
    is PlayIntent.SeekTo -> if (dispatcher.dispatch(intent)) "跳到 ${intent.seconds}s" else UNSUPPORTED
    is PlayIntent.Search -> "要找「${intent.keyword}」：去「媒体库 → 在线曲库」搜一下"
    is PlayIntent.Unknown -> "没听清（识别为「${intent.text.take(12)}」）"
}

/** 设备做不到这件事时的统一回执。语音说不出"这台设备没这个能力"，但界面得说清。 */
private const val UNSUPPORTED = "这台设备做不到这个操作"

/**
 * 「从哪出声」——**一个输出口对应一台设备**。
 *
 * ## 为什么不是「本机 / 音箱」两个
 *
 * 家里三台 DOSS + 一台电视盒子。只有两个值时，「投给谁」实际由**上一个页面选中的设备**决定，
 * 用户在这里切了输出口却不知道该投给哪台 —— 显式选择被隐式状态覆盖，这是最难查的那种不一致。
 *
 * 现在：本机 + 每一台已保存的设备，各一个按钮。
 *
 * ⚠️ **内联一行、横向滚动**，不能用浮层（`docs/37`：播放页不许有任何浮层）。
 */
@Composable
private fun OutputSwitchRow() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val router = PlaybackRouter.of(context)
    var output by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(router.output())
    }
    // 每次进页面读一次：设备列表只在增删设备时变，不值得轮询
    val devices = androidx.compose.runtime.remember {
        runCatching { io.github.laozhudu.linkplay.console.session.SessionStore.of(context).savedDevices() }
            .getOrDefault(emptyList())
    }

    androidx.compose.foundation.layout.Row(
        modifier = androidx.compose.ui.Modifier
            .fillMaxWidth()
            .padding(top = Spacing.xs, bottom = Spacing.xs)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "从哪出声",
            style = MaterialTheme.typography.labelSmall,
            color = OnDarkPalette.text.copy(alpha = 0.7f),
        )
        androidx.compose.material3.FilterChip(
            selected = output is PlaybackOutput.Local,
            onClick = {
                router.setOutput(PlaybackOutput.Local)
                output = PlaybackOutput.Local
                // 通知栏要跟着换挂的播放器（MediaSession 不能动态换 player）
                PlaybackService.restart(context)
            },
            label = { Text("本机") },
        )
        devices.forEach { device ->
            val target = PlaybackOutput.Device(device.ip, device.name)
            androidx.compose.material3.FilterChip(
                selected = output is PlaybackOutput.Device &&
                    (output as PlaybackOutput.Device).ip == device.ip,
                onClick = {
                    router.setOutput(target)
                    output = target
                    PlaybackService.restart(context)
                },
                label = { Text(device.name.ifBlank { device.ip }) },
            )
        }
    }
}

/** 设备缺能力时的提示。**说清是设备做不到**，不是"操作失败"。 */
private const val NOT_SUPPORTED_NEXT = "这台设备没有「下一首」（它不持有播放列表）"
private const val NOT_SUPPORTED_PREV = "这台设备没有「上一首」（它不持有播放列表）"


/**
 * 全屏播放列表（定稿里从「播放列表 · N 首 · 第 M 首 ›」这一行点开）。
 *
 * ## 为什么从"页内常驻"改成"一层全屏"
 *
 * 播放页的主角是**遥控**（封面 / 进度 / 三个键），不是文件管理器。
 * 原来列表占掉中部一整块 weight，导致封面被压小、控制区被压到底边，
 * 一屏里三件事（看 / 选 / 控）互相抢空间。
 *
 * ## 循环与停止搬到这里
 *
 * 定稿主控只有三个键。原来那排有五个（多了循环、停止）。
 * 这两个都是**低频且容易误触**的：循环要连点三下才回到"不循环"，
 * 停止和暂停在没有外部输入的场景下几乎同义。放到列表页底部，需要时才找得到。
 */
@Composable
private fun PlaylistSheet(
    queue: QueueRunState,
    state: PlaybackUiState,
    vm: PlaybackViewModel,
    onOpenNas: () -> Unit,
    onOpenLocalQueue: () -> Unit,
    onOpenLibrary: () -> Unit,
    onClose: () -> Unit,
    actionScope: kotlinx.coroutines.CoroutineScope,
    dispatcher: PlaybackDispatcher,
) {
    var showAdvanced by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onClose,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(OnDarkPalette.panel)
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(horizontal = Spacing.lg),
        ) {
            // 顶部：标题 + 关闭
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "播放列表",
                    style = MaterialTheme.typography.titleMedium,
                    color = OnDarkPalette.text,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onClose) { Text("完成") }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = Spacing.xs),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SegmentChip(
                    text = if (queue.items.isEmpty()) "播放列表" else "播放列表 ${queue.items.size}",
                    selected = !showAdvanced,
                    onClick = { showAdvanced = false },
                )
                SegmentChip(text = "投流与日志", selected = showAdvanced, onClick = { showAdvanced = true })
                Spacer(Modifier.weight(1f))
                if (state.deviceQueueCount > 0) {
                    Text(
                        if (state.deviceQueueIndex >= 0) {
                            "设备 ${state.deviceQueueCount} 首 · 第 ${state.deviceQueueIndex + 1} 首"
                        } else {
                            "设备 ${state.deviceQueueCount} 首"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = StatusColors.ok,
                        maxLines = 1,
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(OnDarkPalette.chip),
            ) {
                if (showAdvanced) {
                    CastPanel(
                        state = state,
                        vm = vm,
                        onOpenNas = onOpenNas,
                        onOpenLocalQueue = onOpenLocalQueue,
                    )
                } else if (queue.items.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(Spacing.md),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            "队列是空的",
                            style = MaterialTheme.typography.bodyMedium,
                            color = OnDarkPalette.text,
                        )
                        Spacer(Modifier.height(Spacing.sm))
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            TextButton(onClick = onOpenNas) { Text("NAS 音乐") }
                            TextButton(onClick = onOpenLocalQueue) { Text("手机音乐") }
                            TextButton(onClick = onOpenLibrary) { Text("音乐库") }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = Spacing.xs),
                    ) {
                        itemsIndexed(queue.items) { index, item ->
                            PlaylistRow(
                                index = index,
                                item = item,
                                playing = index == queue.index && queue.running,
                                current = index == queue.index,
                                onClick = { vm.playQueueItem(index) },
                                onRemove = if (QueueRestore.isPlaceholder(item)) null
                                else ({ vm.removeQueueItem(index) }),
                            )
                        }
                    }
                }
            }

            // 底部：循环 + 停止（定稿主控里没有这两个，搬到这里）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Spacing.sm),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = {
                    actionScope.launch { dispatcher.dispatch(PlayIntent.LoopNext) }
                }) {
                    Text(
                        "循环：" + when (state.loopMode) {
                            LoopMode.OFF -> "关"
                            LoopMode.ONE -> "单曲"
                            LoopMode.SHUFFLE -> "随机"
                            LoopMode.SEQUENCE -> "列表"
                        },
                    )
                }
                Spacer(Modifier.width(Spacing.md))
                TextButton(onClick = {
                    actionScope.launch { dispatcher.dispatch(PlayIntent.Stop) }
                }) {
                    Text("停止")
                }
            }
        }
    }
}
