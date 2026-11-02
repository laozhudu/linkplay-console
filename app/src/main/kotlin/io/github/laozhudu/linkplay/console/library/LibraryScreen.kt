package io.github.laozhudu.linkplay.console.library

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import io.github.laozhudu.linkplay.console.ui.components.AppShapesExpressive
import io.github.laozhudu.linkplay.console.ui.theme.Spacing
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.laozhudu.linkplay.protocol.library.TrackRecord

/**
 * 音乐库页：搜索 / 收藏 / 最近 / 电台 + 预设键。
 *
 * 布局取舍：四个分区用顶部一排按钮切换而不是 TabRow——屏幕宽度有限，
 * 搜索框要占满一行，TabRow 会把内容挤到第二屏。
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
    // 不在进入页面时就请求媒体权限：用户可能只是来看收藏/电台。
    // 只有搜索页真正要用本机曲库时才请求（见下面的搜索区）。
    var askedPermission by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        // 分区用可横滚的 pill 而不是一排按钮：4 个按钮在窄屏会换行，且按钮形状让「切换」看起来像「提交」
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            listOf(
                LibraryTab.SEARCH to "搜索",
                LibraryTab.FAVORITES to "收藏 ${state.favorites.size}",
                LibraryTab.RECENT to "最近 ${state.recents.size}",
                LibraryTab.RADIO to "电台 ${state.radios.size}",
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

        state.error?.let {
            Spacer(Modifier.height(6.dp))
            Text("· $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = vm::clearError) { Text("知道了") }
        }

        state.capabilities?.let { caps ->
            Spacer(Modifier.height(4.dp))
            Text(caps.summary(), style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
            if (!caps.canPushQueue) {
                Text(
                    "提示：这台设备不支持整列表推送，只能单曲播放。",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }

        state.nowPlaying?.let {
            Spacer(Modifier.height(4.dp))
            Text("正在播放：$it", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
        }

        Spacer(Modifier.height(8.dp))
        when (state.tab) {
            LibraryTab.SEARCH -> SearchSection(
                state, vm,
                onRequestPermission = { if (!askedPermission) { askedPermission = true; launcher.launch(permission) } },
            )
            LibraryTab.FAVORITES -> TrackList(
                title = "收藏",
                tracks = state.favorites,
                emptyHint = "还没有收藏。搜索结果里点 ☆ 添加。",
                onPlay = vm::play,
                onToggleFav = vm::toggleFavorite,
                favoriteKeys = state.favorites.map { it.dedupeKey }.toSet(),
            )
            LibraryTab.RECENT -> TrackList(
                title = "最近播放",
                tracks = state.recents,
                emptyHint = "还没有播放记录。",
                onPlay = vm::play,
                onToggleFav = vm::toggleFavorite,
                favoriteKeys = state.favorites.map { it.dedupeKey }.toSet(),
            )
            LibraryTab.RADIO -> RadioSection(state, vm)
        }

        Spacer(Modifier.height(10.dp))
        Text("设备预设键（1-6）", style = MaterialTheme.typography.labelSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (1..6).forEach { index ->
                OutlinedButton(onClick = { vm.pressPreset(index) }) { Text("$index") }
            }
        }

        state.log.asReversed().take(5).forEach {
            Text(it, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
        }

        Spacer(Modifier.height(10.dp))
        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("返回") }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SearchSection(
    state: LibraryUiState,
    vm: LibraryViewModel,
    /** 请求媒体权限的入口：进页面不自动弹，用户真正要搜本机曲库时才要。 */
    onRequestPermission: () -> Unit,
) {
    OutlinedTextField(
        value = state.query,
        onValueChange = vm::setQuery,
        label = { Text("搜索：标题 / 艺术家 / 拼音首字母（试试 zjl）") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(8.dp))
    if (state.query.isBlank()) {
        Text(
            "支持拼音首字母：输入 `zj` 找「周杰伦」，输入 `yq` 找「夜曲」。",
            style = MaterialTheme.typography.bodySmall,
        )
        // 用户要搜本机曲库时才要权限，不在进页面时弹框
        Spacer(Modifier.height(Spacing.sm))
        OutlinedButton(onClick = onRequestPermission) { Text("读取本机音乐库") }
        return
    }
    if (state.hits.isEmpty()) {
        Text("没有匹配", style = MaterialTheme.typography.bodySmall)
        return
    }
    val favKeys = state.favorites.map { it.dedupeKey }.toSet()
    // 不再用内层 LazyColumn：外层已是 verticalScroll，两根滚动轴会抢手势。
    // 限 50 条 + 提示缩小范围，比让用户在一个 340dp 的小窗里滑 1900 首更好用。
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(vertical = Spacing.xs)) {
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
                    "只显示前 50 条（共 ${state.hits.size} 条），输入更多关键词缩小范围",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(Spacing.md),
                )
            }
        }
    }
}

@Composable
private fun TrackList(
    title: String,
    tracks: List<TrackRecord>,
    emptyHint: String,
    onPlay: (TrackRecord) -> Unit,
    onToggleFav: (TrackRecord) -> Unit,
    favoriteKeys: Set<String>,
) {
    if (tracks.isEmpty()) {
        Text(emptyHint, style = MaterialTheme.typography.bodySmall)
        return
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(vertical = Spacing.xs)) {
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
                    modifier = Modifier.padding(Spacing.md),
                )
            }
        }
    }
}

/**
 * 一行曲目。**整行点击 = 播放这一首**。
 *
 * 原来是「标题 + ☆ + 播（按钮）」，用户得先看清「播」这个小按钮在哪，还容易点到星标。
 * 改成整行可点之后，收藏做成独立的 40dp 触控区放在行尾，两个动作互不干扰。
 */
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
            .padding(start = Spacing.md, end = Spacing.xs, top = Spacing.xs, bottom = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                track.title,
                style = MaterialTheme.typography.titleMedium,
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

@Composable
private fun RadioSection(state: LibraryUiState, vm: LibraryViewModel) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var pendingDeleteId by remember { mutableStateOf<Long?>(null) }

    OutlinedTextField(
        value = url,
        onValueChange = {
            url = it
            if (name.isBlank()) name = io.github.laozhudu.linkplay.protocol.radio.RadioStation.suggestName(it)
        },
        label = { Text("流地址（http/https，如 http://host/live.m3u8）") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(6.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("电台名称") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Button(onClick = {
            vm.addRadio(name, url)
            name = ""
            url = ""
        }) { Text("添加") }
    }

    Spacer(Modifier.height(8.dp))
    if (state.radios.isEmpty()) {
        Text("还没有电台。粘一条流地址即可（本 App 不内置任何平台接口）。", style = MaterialTheme.typography.bodySmall)
        return
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(vertical = Spacing.xs)) {
            state.radios.forEach { station ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(AppShapesExpressive.squircle)
                        // 整行点击 = 播放，与曲目行保持一致
                        .clickable { vm.playRadio(station) }
                        .padding(start = Spacing.md, end = Spacing.xs, top = Spacing.xs, bottom = Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(station.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                        Text(
                            station.url,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    // 删除要二次确认：误触就得重新找一条流地址
                    if (pendingDeleteId == station.id) {
                        Button(onClick = { vm.removeRadio(station.id); pendingDeleteId = null }) { Text("确认删") }
                        OutlinedButton(onClick = { pendingDeleteId = null }) { Text("取消") }
                    } else {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .clickable { pendingDeleteId = station.id },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "✕",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
