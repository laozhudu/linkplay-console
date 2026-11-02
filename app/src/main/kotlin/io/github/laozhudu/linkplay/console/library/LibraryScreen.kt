package io.github.laozhudu.linkplay.console.library

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.compose.ui.platform.LocalContext
import io.github.laozhudu.linkplay.console.ui.components.AppCard
import io.github.laozhudu.linkplay.console.ui.components.AppShapesExpressive
import io.github.laozhudu.linkplay.console.ui.components.EmptyState
import io.github.laozhudu.linkplay.console.ui.components.PageHeader
import io.github.laozhudu.linkplay.console.ui.components.PillTag
import io.github.laozhudu.linkplay.console.ui.components.SectionTitle
import io.github.laozhudu.linkplay.console.ui.components.VSpace
import io.github.laozhudu.linkplay.console.ui.theme.Spacing
import io.github.laozhudu.linkplay.console.ui.theme.StatusColors
import io.github.laozhudu.linkplay.protocol.library.TrackRecord
import io.github.laozhudu.linkplay.protocol.radio.RadioStation

/**
 * 音乐库：搜索 / 收藏 / 最近 / 电台 + 预设键。
 *
 * ## 这一版改了什么（对齐首页）
 *
 * 之前这页是「一堆裸 Text + 6dp 间距 + 底部一个『返回』按钮 + 直接把调试日志铺在页尾」，
 * 和首页的卡片风格完全是两个 App。现在：
 *
 * - 页头用统一的 [PageHeader]（与设备设置、NAS、手机音乐同一套尺寸）；
 * - 四个分区的内容都装在 [AppCard] 里，间距只用 [Spacing]；
 * - 删掉页尾那个「返回」按钮 —— 页头已经有返回箭头，两个入口等于承认自己没想清楚；
 * - 调试日志收进「诊断」折叠区，不再是页面正文。
 *
 * ## 电台为什么重做
 *
 * 原先是两个裸文本框 + 「添加」按钮常驻在页面上，用户根本不知道
 * 「流地址」要填什么、格式是什么、填错了会怎样 —— 用户原话「无从下手配置」。
 * 现在改成：默认只有一句说明 + 一个「添加电台」按钮；点开是一个对话框，
 * 里面有**可直接点的示例**、格式说明、实时校验（不是 http/https 直接标红不让你提交）。
 */
@Composable
fun LibraryScreen(
    deviceIp: String,
    location: String?,
    deviceLabel: String?,
    onBack: () -> Unit,
) {
    val app = LocalContext.current.applicationContext as android.app.Application
    val vm: LibraryViewModel = viewModel(
        key = "library-$deviceIp",
        factory = viewModelFactory { initializer { LibraryViewModel(app, deviceIp, location, deviceLabel) } },
    )
    val state by vm.state.collectAsState()

    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (it) vm.refreshAll()
    }
    // 不在进入页面时就请求媒体权限：用户可能只是来看收藏/电台
    var askedPermission by remember { mutableStateOf(false) }
    var showDiagnostics by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.lg),
    ) {
        PageHeader(
            title = "音乐库",
            subtitle = deviceLabel ?: deviceIp,
            onBack = onBack,
        )

        state.error?.let {
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = vm::clearError) { Text("知道了") }
                }
            }
            VSpace(Spacing.sm)
        }

        // ---------- 分区切换 ----------
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            listOf(
                LibraryTab.SEARCH to "搜索",
                LibraryTab.FAVORITES to "收藏 ${state.favorites.size}",
                LibraryTab.RECENT to "最近 ${state.recents.size}",
            ).forEach { (tab, label) ->
                val active = state.tab == tab
                Box(
                    modifier = Modifier
                        .clip(AppShapesExpressive.pill)
                        .background(
                            if (active) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant,
                        )
                        .clickable { vm.setTab(tab) }
                        .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (active) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        VSpace(Spacing.md)

        when (state.tab) {
            LibraryTab.SEARCH -> SearchSection(
                state, vm,
                onRequestPermission = {
                    if (!askedPermission) {
                        askedPermission = true
                        launcher.launch(permission)
                    }
                },
            )
            LibraryTab.FAVORITES -> TrackList(
                title = "收藏",
                subtitle = "${state.favorites.size} 首",
                tracks = state.favorites,
                emptyTitle = "还没有收藏",
                emptyHint = "搜索结果里点 ★ 就收藏了。",
                onPlay = vm::play,
                onToggleFav = vm::toggleFavorite,
                favoriteKeys = state.favorites.map { it.dedupeKey }.toSet(),
            )
            LibraryTab.RECENT -> TrackList(
                title = "最近播放",
                subtitle = "${state.recents.size} 首",
                tracks = state.recents,
                emptyTitle = "还没有播放记录",
                emptyHint = "投一首歌给音箱，这里就会自动记下来。",
                onPlay = vm::play,
                onToggleFav = vm::toggleFavorite,
                favoriteKeys = state.favorites.map { it.dedupeKey }.toSet(),
            )
        }

        VSpace(Spacing.md)
        PresetCard(vm = vm)

        // ---------- 诊断（折叠，不再是页面正文）----------
        VSpace(Spacing.sm)
        AppCard(onClick = { showDiagnostics = !showDiagnostics }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (showDiagnostics) "收起诊断" else "诊断：设备能力与日志",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    if (showDiagnostics) "−" else "+",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (showDiagnostics) {
            VSpace(Spacing.xs)
            AppCard {
                state.capabilities?.let { caps ->
                    Text(caps.summary(), style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
                    if (!caps.canPushQueue) {
                        Text(
                            "这台设备不支持整列表推送，只能单曲播放。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    VSpace(Spacing.xs)
                }
                Text(
                    if (state.log.isEmpty()) "暂无日志" else state.log.takeLast(8).joinToString("\n"),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        state.nowPlaying?.let {
            VSpace(Spacing.sm)
            Text(
                "正在播放：$it",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        VSpace(Spacing.xxl)
    }
}

// ---------------------------------------------------------------- 搜索

@Composable
private fun SearchSection(
    state: LibraryUiState,
    vm: LibraryViewModel,
    /** 请求媒体权限的入口：进页面不自动弹，用户真正要搜本机曲库时才要。 */
    onRequestPermission: () -> Unit,
) {
    val favKeys = state.favorites.map { it.dedupeKey }.toSet()

    // 大胶囊搜索框：与首页搜索同一形状，触控高度 56dp
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(AppShapesExpressive.pill)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = Spacing.md),
        contentAlignment = Alignment.CenterStart,
    ) {
        OutlinedTextField(
            value = state.query,
            onValueChange = vm::setQuery,
            placeholder = { Text("搜标题 / 歌手 / 拼音首字母（试 zjl）") },
            singleLine = true,
            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                focusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }

    VSpace(Spacing.sm)

    if (state.query.isBlank()) {
        AppCard {
            SectionTitle("怎么找歌", "支持拼音首字母：zj → 周杰伦，yq → 夜曲")
            VSpace(Spacing.xs)
            Text(
                "搜索范围是本机曲库 + 收藏 + 最近播放。要从 NAS 上找，请去「NAS 音乐」。",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            VSpace(Spacing.sm)
            PillButton("读取本机音乐库", filled = true, onClick = onRequestPermission)
        }
        return
    }

    if (state.hits.isEmpty()) {
        AppCard {
            Text("没有匹配「${state.query}」", style = MaterialTheme.typography.bodyMedium)
            VSpace(Spacing.xs)
            Text(
                "换个关键词，或只输入拼音首字母。",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    AppCard {
        SectionTitle("搜索结果", "共 ${state.hits.size} 条，最多显示 50 条")
        state.hits.take(50).forEach { hit ->
            TrackRow(
                track = hit.track,
                isFavorite = hit.track.dedupeKey in favKeys,
                onPlay = { vm.play(hit.track) },
                onToggleFav = { vm.toggleFavorite(hit.track) },
            )
        }
        if (state.hits.size > 50) {
            Text(
                "只显示前 50 条，输入更多关键词缩小范围",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ---------------------------------------------------------------- 曲目列表

@Composable
private fun TrackList(
    title: String,
    subtitle: String,
    tracks: List<TrackRecord>,
    emptyTitle: String,
    emptyHint: String,
    onPlay: (TrackRecord) -> Unit,
    onToggleFav: (TrackRecord) -> Unit,
    favoriteKeys: Set<String>,
) {
    if (tracks.isEmpty()) {
        EmptyState(title = emptyTitle, hint = emptyHint)
        return
    }
    AppCard {
        SectionTitle(title, subtitle)
        tracks.take(80).forEach { track ->
            TrackRow(
                track = track,
                isFavorite = track.dedupeKey in favoriteKeys,
                onPlay = { onPlay(track) },
                onToggleFav = { onToggleFav(track) },
            )
        }
        if (tracks.size > 80) {
            Text(
                "只显示前 80 条（共 ${tracks.size} 条）",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 一行曲目：**整行点击 = 播放**；收藏是行尾独立的 40dp 触控区，两者不打架。 */
@Composable
private fun TrackRow(
    track: TrackRecord,
    isFavorite: Boolean,
    onPlay: () -> Unit,
    onToggleFav: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppShapesExpressive.squircle)
            .clickable(onClick = onPlay)
            .padding(start = Spacing.sm, end = Spacing.xs, top = Spacing.xs, bottom = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                track.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                track.displayArtist ?: track.sourceType,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .clickable(onClick = onToggleFav),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (isFavorite) "★" else "☆",
                style = MaterialTheme.typography.titleLarge,
                color = if (isFavorite) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ---------------------------------------------------------------- 电台

// ---------------------------------------------------------------- 预设键

@Composable
private fun PresetCard(vm: LibraryViewModel) {
    AppCard {
        SectionTitle("设备预设键", "相当于按机身或遥控器上的 1-6")
        VSpace(Spacing.xs)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            (1..6).forEach { index ->
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { vm.pressPreset(index) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("$index", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
private fun PillButton(text: String, filled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(AppShapesExpressive.pill)
            .background(
                if (filled) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.lg, vertical = 10.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = if (filled) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
